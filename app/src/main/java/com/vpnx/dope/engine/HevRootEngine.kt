package com.vpnx.dope.engine

import android.content.Context
import android.os.Process
import com.vpnx.dope.ui.viewmodel.VpnStateBridge
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile

/**
 * Root (hev) tunnel mode for vpnx.
 *
 * Runs the hev-socks5-tunnel CLI executable (libhev-socks5-tunnel-cli.so,
 * built by CI from heiher/hev-socks5-tunnel and shipped inside the APK) as
 * root. The CLI creates the TUN interface itself (vpnx0) and forwards its
 * packets to the local SOCKS5 server that the proxy engines already run on
 * 127.0.0.1:<localSocksPort> — the same SOCKS server the VpnService mode
 * feeds through Tun2Socks. No VpnService, no Builder.establish(), no
 * protect(): the proxy's own outbound sockets are kept out of the tunnel by
 * a policy-routing rule (uidrange for this app's UID, with an iptables
 * owner-mark rule as fallback), and hev marks its own sockets with the
 * fwmark from its config.
 *
 * Strictly separate from the VpnService path: this object is only ever
 * called when the persisted tunnel mode is ROOT, and the VpnService path
 * never executes a root command.
 *
 * Routing (all via a root shell script, verified after applying):
 *   ip rule add uidrange <uid>-<uid> lookup main pref 5   (app traffic direct)
 *   ip rule add fwmark 438 lookup main pref 10            (hev's own sockets)
 *   ip rule add from all lookup 100 pref 20               (everything else)
 *   ip route add default dev vpnx0 table 100              (tunnel default)
 * DNS needs no special case: client queries are routed into vpnx0, hev
 * forwards them as SOCKS UDP to the local server, which resolves them
 * through the proxy — exactly like the VpnService path.
 *
 * Kill behaviour: if hev dies, the vpnx0 default route stays but the
 * interface is gone, so traffic is blackholed until teardown (fail-closed).
 * The watchdog stops the pipeline cleanly (routes removed, interface
 * deleted) after 3 consecutive failed liveness checks.
 */
object HevRootEngine {

    const val TUN_NAME = "vpnx0"
    const val TUN_IPV4 = "10.0.0.1"
    const val MARK = 438
    const val TABLE = 100
    private const val RULE_PREF_UID = 5
    private const val RULE_PREF_MARK = 10
    private const val RULE_PREF_TUN = 20
    private const val MTU = 1400
    private const val CLI_LIB_NAME = "libhev-socks5-tunnel-cli.so"
    private const val CONFIG_NAME = "vpnx-hev.yml"
    private const val LOG_NAME = "vpnx-hev.log"
    private const val PID_NAME = "vpnx-hev.pid"
    private const val START_SCRIPT_NAME = "vpnx-root-start.sh"
    private const val STOP_SCRIPT_NAME = "vpnx-root-stop.sh"

    @Volatile
    var isRunning: Boolean = false
        private set

    private val watchdogStop = AtomicBoolean(false)
    private var watchdogThread: Thread? = null

    // ------------------------------------------------------------------ root

    /** True only when `su -c id` reports uid=0 within the timeout. */
    fun isRootAvailable(): Boolean {
        val (code, out) = runCmd(arrayOf("su", "-c", "id"), 10)
        return code == 0 && out.contains("uid=0")
    }

    // --------------------------------------------------------------- lifecycle

    /**
     * Starts the hev root tunnel. The local SOCKS server must already be
     * listening on [socksPort] (the proxy engine is started first by the
     * caller, same as in VpnService mode). Returns true only after the
     * interface exists, the routing is verified, and the SOCKS port answers.
     */
    fun start(context: Context, socksPort: Int, onTunnelDead: () -> Unit): Boolean {
        if (isRunning) stop(context)
        val filesDir = context.filesDir
        val cli = resolveCliBinary(context)
        if (cli == null) {
            VpnStateBridge.addLog("Root mode: hev CLI binary missing from APK (libhev-socks5-tunnel-cli.so).")
            return false
        }
        if (!looksLikeElf(cli)) {
            VpnStateBridge.addLog("Root mode: hev CLI binary is not a valid executable (${cli.length()} bytes).")
            return false
        }
        val configFile = File(filesDir, CONFIG_NAME)
        val logFile = File(filesDir, LOG_NAME)
        val pidFile = File(filesDir, PID_NAME)
        try {
            logFile.delete()
            pidFile.delete()
            writeConfig(configFile, logFile, pidFile, socksPort)
        } catch (t: Throwable) {
            VpnStateBridge.addLog("Root mode: could not write hev config: ${t.message}")
            return false
        }
        val startScript = File(filesDir, START_SCRIPT_NAME)
        val stopScript = File(filesDir, STOP_SCRIPT_NAME)
        try {
            writeScripts(startScript, stopScript)
        } catch (t: Throwable) {
            VpnStateBridge.addLog("Root mode: could not write root scripts: ${t.message}")
            return false
        }
        if (!isRootAvailable()) {
            VpnStateBridge.addLog("Root mode: no root access (su -c id did not return uid=0).")
            return false
        }
        VpnStateBridge.addLog("Root mode: starting hev tunnel (vpnx0, fwmark $MARK)...")
        val uid = Process.myUid()
        val (code, out) = runCmd(
            arrayOf(
                "su", "-c",
                "sh ${startScript.absolutePath} ${cli.absolutePath} ${configFile.absolutePath} " +
                    "${pidFile.absolutePath} ${logFile.absolutePath} $uid $TUN_NAME $MARK $TABLE"
            ),
            30
        )
        if (code != 0) {
            VpnStateBridge.addLog("Root mode: setup script failed (exit $code). ${out.trim().take(300)}")
            logHevTail(logFile)
            runStopScript(context)
            return false
        }
        if (!waitForTunnelUp(context, socksPort, 6000)) {
            VpnStateBridge.addLog("Root mode: vpnx0 did not come up or SOCKS port did not answer.")
            logHevTail(logFile)
            runStopScript(context)
            return false
        }
        if (!verifyBypass()) {
            VpnStateBridge.addLog("Root mode: bypass routing missing after setup, tearing down.")
            runStopScript(context)
            return false
        }
        isRunning = true
        startWatchdog(context, socksPort, onTunnelDead)
        VpnStateBridge.addLog("Root mode: hev tunnel up (vpnx0 -> 127.0.0.1:$socksPort, bypass verified).")
        return true
    }

    /** Stops hev, removes all routing/rules, deletes the interface. */
    fun stop(context: Context) {
        val wasRunning = isRunning
        isRunning = false
        stopWatchdog()
        if (!wasRunning) return
        runStopScript(context)
        VpnStateBridge.addLog("Root mode: hev tunnel stopped, routing restored.")
    }

    // -------------------------------------------------------------- liveness

    /** TUN interface exists and the local SOCKS port accepts a connection. */
    fun isTunnelUp(context: Context, socksPort: Int): Boolean {
        return tunExists() && socksPortOpen(socksPort)
    }

    private fun waitForTunnelUp(context: Context, socksPort: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isTunnelUp(context, socksPort)) return true
            try { Thread.sleep(300) } catch (_: InterruptedException) { return false }
        }
        return isTunnelUp(context, socksPort)
    }

    private fun tunExists(): Boolean {
        val (code, _) = runCmd(arrayOf("/system/bin/ip", "link", "show", TUN_NAME), 5)
        if (code == 0) return true
        val (code2, _) = runCmd(arrayOf("ip", "link", "show", TUN_NAME), 5)
        return code2 == 0
    }

    private fun socksPortOpen(port: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", port), 1500)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * The bypass (app traffic -> main table) must be present, otherwise the
     * proxy's own outbound connection would loop through the tunnel forever.
     */
    private fun verifyBypass(): Boolean {
        val uid = Process.myUid()
        // The setup script prefers /system/bin/ip but falls back to PATH,
        // so check both the same way.
        for (ipBin in listOf("ip", "/system/bin/ip")) {
            val (uidCode, _) = runCmd(
                arrayOf("su", "-c", "$ipBin rule show | grep -q 'uidrange $uid-$uid'"),
                10
            )
            if (uidCode == 0) return true
        }
        for (iptBin in listOf("iptables", "/system/bin/iptables")) {
            val (iptCode, _) = runCmd(
                arrayOf("su", "-c", "$iptBin -t mangle -C OUTPUT -m owner --uid-owner $uid -j MARK --set-mark $MARK"),
                10
            )
            if (iptCode == 0) return true
        }
        return false
    }

    private fun startWatchdog(context: Context, socksPort: Int, onTunnelDead: () -> Unit) {
        stopWatchdog()
        watchdogStop.set(false)
        val t = Thread({
            var failures = 0
            while (!watchdogStop.get()) {
                try { Thread.sleep(5000) } catch (_: InterruptedException) { break }
                if (watchdogStop.get()) break
                if (!isRunning) break
                if (isTunnelUp(context, socksPort)) {
                    failures = 0
                } else {
                    failures++
                    if (failures >= 3) {
                        VpnStateBridge.addLog("Root mode: tunnel dead (3 failed checks), stopping.")
                        try { onTunnelDead() } catch (_: Throwable) {}
                        break
                    }
                }
            }
        }, "vpnx-hev-watchdog")
        t.isDaemon = true
        watchdogThread = t
        t.start()
    }

    private fun stopWatchdog() {
        watchdogStop.set(true)
        watchdogThread?.interrupt()
        watchdogThread = null
    }

    // ------------------------------------------------------------- hev files

    private fun writeConfig(configFile: File, logFile: File, pidFile: File, socksPort: Int) {
        val yaml = buildString {
            appendLine("tunnel:")
            appendLine("  name: '$TUN_NAME'")
            appendLine("  mtu: $MTU")
            appendLine("  ipv4: '$TUN_IPV4'")
            appendLine("socks5:")
            appendLine("  port: $socksPort")
            appendLine("  address: '127.0.0.1'")
            appendLine("  udp: 'udp'")
            appendLine("  mark: $MARK")
            appendLine("misc:")
            appendLine("  tcp-read-write-timeout: 10000")
            appendLine("  udp-read-write-timeout: 60000")
            appendLine("  limit-nofile: 65535")
            appendLine("  log-file: '${logFile.absolutePath}'")
            appendLine("  log-level: 'warn'")
            appendLine("  pid-file: '${pidFile.absolutePath}'")
        }
        configFile.writeText(yaml)
    }

    /**
     * The CLI binary ships as libhev-socks5-tunnel-cli.so inside the APK so
     * it is extracted at install with execute permission. Prefer the copy in
     * the native library dir; fall back to extracting it from the APK into
     * filesDir (same pattern as XrayCore.resolveXrayBinary).
     */
    private fun resolveCliBinary(context: Context): File? {
        val inLib = File(context.applicationInfo.nativeLibraryDir, CLI_LIB_NAME)
        if (inLib.exists() && inLib.length() > 100_000) return inLib
        return try {
            val out = File(context.filesDir, "hev-socks5-tunnel-cli")
            val apk = File(context.applicationInfo.sourceDir)
            ZipFile(apk).use { zip ->
                val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
                val entry = zip.getEntry("lib/$abi/$CLI_LIB_NAME") ?: return null
                zip.getInputStream(entry).use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
            }
            out.setExecutable(true, true)
            if (out.exists() && out.length() > 100_000) out else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun looksLikeElf(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 &&
                    magic[0] == 0x7F.toByte() &&
                    magic[1] == 'E'.code.toByte() &&
                    magic[2] == 'L'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        } catch (_: Throwable) {
            false
        }
    }

    // ------------------------------------------------------------ root shell

    /**
     * Runs a command and returns (exitCode, combined stdout+stderr).
     * Streams are drained on a reader thread so a chatty command cannot
     * deadlock on a full pipe.
     */
    private fun runCmd(cmd: Array<String>, timeoutSec: Long): Pair<Int, String> {
        return try {
            val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val output = StringBuilder()
            val reader = Thread({
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        synchronized(output) { output.appendLine(line) }
                    }
                } catch (_: Throwable) {}
            }, "vpnx-cmd-reader")
            reader.isDaemon = true
            reader.start()
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                try { process.destroyForcibly() } catch (_: Throwable) {}
                reader.join(1000)
                return -1 to "timeout after ${timeoutSec}s"
            }
            reader.join(2000)
            process.exitValue() to output.toString()
        } catch (t: Throwable) {
            -1 to (t.message ?: "exec failed")
        }
    }

    private fun runStopScript(context: Context) {
        val stopScript = File(context.filesDir, STOP_SCRIPT_NAME)
        if (!stopScript.exists()) return
        val uid = Process.myUid()
        runCmd(
            arrayOf(
                "su", "-c",
                "sh ${stopScript.absolutePath} ${File(context.filesDir, PID_NAME).absolutePath} " +
                    "$TUN_NAME $uid $MARK $TABLE"
            ),
            20
        )
    }

    private fun logHevTail(logFile: File) {
        try {
            if (!logFile.exists()) return
            val lines = logFile.readLines().takeLast(12)
            if (lines.isEmpty()) return
            VpnStateBridge.addLog("hev log tail:")
            lines.forEach { line -> VpnStateBridge.addLog("  ${line.take(200)}") }
        } catch (_: Throwable) {}
    }

    // ------------------------------------------------------------- scripts

    /**
     * Both scripts are written as files and executed by `su -c 'sh <file> …'`.
     * The kill pattern (the hev config filename) lives only inside the stop
     * script's content, never in a process command line, so pkill cannot
     * match the shell running the script itself.
     *
     * Start script args:  $1 CLI  $2 config  $3 pid-file  $4 log  $5 app uid
     *                     $6 tun name  $7 fwmark  $8 table
     * Stop script args:   $1 pid-file  $2 tun name  $3 app uid  $4 fwmark
     *                     $5 table
     */
    private fun writeScripts(startScript: File, stopScript: File) {
        val start = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("CLI=\"\$1\"; CFG=\"\$2\"; PIDF=\"\$3\"; LOGF=\"\$4\"; APPUID=\"\$5\"; TUN=\"\$6\"; MARK=\"\$7\"; TAB=\"\$8\"")
            appendLine("IP=/system/bin/ip")
            appendLine("[ -x \"\$IP\" ] || IP=ip")
            appendLine("IPT=/system/bin/iptables")
            appendLine("[ -x \"\$IPT\" ] || IPT=iptables")
            appendLine("# stale cleanup (best effort, ignore errors)")
            appendLine("\"\$IP\" link del \"\$TUN\" 2>/dev/null")
            appendLine("\"\$IP\" rule del fwmark \"\$MARK\" lookup main 2>/dev/null")
            appendLine("\"\$IP\" rule del uidrange \"\$APPUID-\$APPUID\" lookup main 2>/dev/null")
            appendLine("\"\$IP\" rule del lookup \"\$TAB\" 2>/dev/null")
            appendLine("\"\$IP\" route del default dev \"\$TUN\" table \"\$TAB\" 2>/dev/null")
            appendLine("\"\$IPT\" -t mangle -D OUTPUT -m owner --uid-owner \"\$APPUID\" -j MARK --set-mark \"\$MARK\" 2>/dev/null")
            appendLine("rm -f \"\$PIDF\"")
            appendLine("# hev creates the TUN interface itself and writes its pid file")
            appendLine("if command -v setsid >/dev/null 2>&1; then")
            appendLine("  setsid \"\$CLI\" \"\$CFG\" >>\"\$LOGF\" 2>&1 < /dev/null &")
            appendLine("else")
            appendLine("  \"\$CLI\" \"\$CFG\" >>\"\$LOGF\" 2>&1 < /dev/null &")
            appendLine("fi")
            appendLine("i=0")
            appendLine("while [ \"\$i\" -lt 50 ]; do")
            appendLine("  \"\$IP\" link show \"\$TUN\" >/dev/null 2>&1 && break")
            appendLine("  sleep 0.1")
            appendLine("  i=\$((i + 1))")
            appendLine("done")
            appendLine("\"\$IP\" link show \"\$TUN\" >/dev/null 2>&1 || exit 3")
            appendLine("# routing: marked + app traffic via main table, rest via table -> tun")
            appendLine("\"\$IP\" rule add fwmark \"\$MARK\" lookup main pref $RULE_PREF_MARK || exit 4")
            appendLine("\"\$IP\" route add default dev \"\$TUN\" table \"\$TAB\" 2>/dev/null")
            appendLine("\"\$IP\" rule add from all lookup \"\$TAB\" pref $RULE_PREF_TUN || exit 5")
            appendLine("if ! \"\$IP\" rule add uidrange \"\$APPUID-\$APPUID\" lookup main pref $RULE_PREF_UID 2>/dev/null; then")
            appendLine("  \"\$IPT\" -t mangle -A OUTPUT -m owner --uid-owner \"\$APPUID\" -j MARK --set-mark \"\$MARK\" || exit 6")
            appendLine("fi")
            appendLine("sysctl -w net.ipv4.conf.\"\$TUN\".rp_filter=0 2>/dev/null")
            appendLine("exit 0")
        }
        val stop = buildString {
            appendLine("#!/system/bin/sh")
            appendLine("PIDF=\"\$1\"; TUN=\"\$2\"; APPUID=\"\$3\"; MARK=\"\$4\"; TAB=\"\$5\"")
            appendLine("IP=/system/bin/ip")
            appendLine("[ -x \"\$IP\" ] || IP=ip")
            appendLine("IPT=/system/bin/iptables")
            appendLine("[ -x \"\$IPT\" ] || IPT=iptables")
            appendLine("if [ -f \"\$PIDF\" ]; then")
            appendLine("  P=\$(cat \"\$PIDF\")")
            appendLine("  if grep -q hev \"/proc/\$P/cmdline\" 2>/dev/null; then kill \"\$P\" 2>/dev/null; fi")
            appendLine("  sleep 1")
            appendLine("fi")
            appendLine("pkill -f $CONFIG_NAME 2>/dev/null")
            appendLine("sleep 1")
            appendLine("\"\$IP\" rule del fwmark \"\$MARK\" lookup main 2>/dev/null")
            appendLine("\"\$IP\" rule del uidrange \"\$APPUID-\$APPUID\" lookup main 2>/dev/null")
            appendLine("\"\$IP\" rule del lookup \"\$TAB\" 2>/dev/null")
            appendLine("\"\$IP\" route del default dev \"\$TUN\" table \"\$TAB\" 2>/dev/null")
            appendLine("\"\$IPT\" -t mangle -D OUTPUT -m owner --uid-owner \"\$APPUID\" -j MARK --set-mark \"\$MARK\" 2>/dev/null")
            appendLine("\"\$IP\" link del \"\$TUN\" 2>/dev/null")
            appendLine("rm -f \"\$PIDF\"")
            appendLine("exit 0")
        }
        startScript.writeText(start)
        stopScript.writeText(stop)
        startScript.setExecutable(true, true)
        stopScript.setExecutable(true, true)
    }
}
