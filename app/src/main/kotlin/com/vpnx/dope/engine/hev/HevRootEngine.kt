package com.vpnx.dope.engine.hev

import android.content.Context
import android.os.Process
import android.util.Log
import com.vpnx.dope.engine.root.RootShellManager
import com.vpnx.dope.engine.root.RoutingTableManager
import com.vpnx.dope.engine.xray.XrayLogTailer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object HevRootEngine {
    private const val TAG = "HevRootEngine"
    private const val CLI_NAME = "libhev-socks5-tunnel-cli.so"
    private const val FALLBACK_CLI_NAME = "hev-socks5-tunnel-cli"
    private const val TUN_WAIT_DEADLINE_MS = 10_000L
    private const val TUN_POLL_INTERVAL_MS = 250L
    private const val HEV_LOG_TAIL_LINES = 30

    @Volatile
    private var running = false

    suspend fun isRootAvailable(): Boolean = RootShellManager.checkRootAccess()

    /**
     * Resolves the executable path of hev-socks5-tunnel-cli.
     * Returns null if the native library binary is missing or cannot be materialized.
     */
    private fun resolveCliExecutable(context: Context): String? {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val cliInLib = File(libDir, CLI_NAME)
        if (cliInLib.exists() && cliInLib.length() > 0L && cliInLib.canExecute()) {
            return cliInLib.absolutePath
        }

        // Extract to internal storage if not executable directly from libDir
        val target = File(context.filesDir, FALLBACK_CLI_NAME)
        if (cliInLib.exists() && cliInLib.length() > 0L) {
            if (!target.exists() || target.length() != cliInLib.length()) {
                runCatching {
                    cliInLib.inputStream().use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                }.onFailure { t ->
                    Log.e(TAG, "Failed to copy hev CLI binary to internal storage", t)
                }
            }
        }

        if (!target.exists() || target.length() == 0L) {
            return null
        }
        target.setExecutable(true, false)
        return target.absolutePath
    }

    suspend fun start(
        context: Context,
        mtu: Int = 1400,
        socksPort: Int = 10808
    ): Boolean = withContext(Dispatchers.IO) {
        // Always clean up any stale rules, daemon, or TUN device BEFORE launching hev
        stop(context)

        val cliPath = resolveCliExecutable(context)
        if (cliPath == null) {
            val msg = "hev CLI executable ($CLI_NAME) not found in nativeLibraryDir or filesDir"
            Log.e(TAG, msg)
            XrayLogTailer.error(msg)
            return@withContext false
        }

        // Ensure /dev/net/tun device node exists before launching hev
        val tunNodeRes = RootShellManager.exec(
            "if [ ! -e /dev/net/tun ]; then mkdir -p /dev/net && mknod /dev/net/tun c 10 200 && chmod 666 /dev/net/tun; fi"
        )
        if (!tunNodeRes.isSuccess) {
            val msg = "Failed to ensure /dev/net/tun: ${tunNodeRes.output.ifBlank { "exit ${tunNodeRes.code}" }}"
            Log.e(TAG, msg)
            XrayLogTailer.error(msg)
            return@withContext false
        }

        val configFile = File(context.filesDir, "hev.yml")
        val logFile = File(context.filesDir, "hev.log")
        val launchLogFile = File(context.filesDir, "hev-launch.log")
        val pidFile = File(context.filesDir, "hev.pid")

        // Clear previous run's logs and PID file (root shell handles root-owned files)
        RootShellManager.exec(
            "rm -f ${shSingleQuote(logFile.absolutePath)} ${shSingleQuote(launchLogFile.absolutePath)} ${shSingleQuote(pidFile.absolutePath)}"
        )

        HevConfigWriter.writeRootModeConfig(
            outFile = configFile,
            tunName = RoutingTableManager.TUN_DEV,
            mtu = mtu,
            socksPort = socksPort,
            fwmark = RoutingTableManager.FWMARK,
            logFile = logFile,
            pidFile = pidFile
        )

        // 1. Launch standalone hev daemon (hev owns hev.log via config; shell writes startup stderr to hev-launch.log)
        val startCmd =
            "nohup ${shSingleQuote(cliPath)} ${shSingleQuote(configFile.absolutePath)} " +
                "> ${shSingleQuote(launchLogFile.absolutePath)} 2>&1 < /dev/null &"
        Log.i(TAG, "Launching hev root daemon: $startCmd")
        val execRes = RootShellManager.exec(startCmd)
        if (!execRes.isSuccess) {
            val msg = "Failed to launch hev daemon: ${execRes.output.ifBlank { "exit ${execRes.code}" }}"
            Log.e(TAG, msg)
            XrayLogTailer.error(msg)
            surfaceHevDiagnostics(logFile, launchLogFile, pidFile)
            stop(context)
            return@withContext false
        }

        // Wait up to 10 seconds wall-clock for vpnx0 interface to appear
        var interfaceUp = false
        val deadline = System.currentTimeMillis() + TUN_WAIT_DEADLINE_MS
        while (System.currentTimeMillis() < deadline) {
            delay(TUN_POLL_INTERVAL_MS)
            if (RoutingTableManager.isTunInterfaceUp()) {
                interfaceUp = true
                break
            }
        }

        if (!interfaceUp) {
            val msg = "hev daemon started but ${RoutingTableManager.TUN_DEV} never materialized within ${TUN_WAIT_DEADLINE_MS / 1000}s"
            Log.e(TAG, msg)
            XrayLogTailer.error(msg)
            surfaceHevDiagnostics(logFile, launchLogFile, pidFile)
            stop(context)
            return@withContext false
        }

        // 2. Apply kernel routing rules (pref 5, 10, 15, 20) — never deletes vpnx0
        val rulesApplied = RoutingTableManager.applyRules(Process.myUid())
        if (!rulesApplied) {
            val msg = "Failed to apply kernel routing rules for ${RoutingTableManager.TUN_DEV}"
            Log.e(TAG, msg)
            XrayLogTailer.error(msg)
            surfaceHevDiagnostics(logFile, launchLogFile, pidFile)
            stop(context)
            return@withContext false
        }

        // 3. Re-verify TUN device is still present after routing rules are installed
        if (!RoutingTableManager.isTunInterfaceUp()) {
            val msg = "${RoutingTableManager.TUN_DEV} disappeared after applying kernel routing rules"
            Log.e(TAG, msg)
            XrayLogTailer.error(msg)
            surfaceHevDiagnostics(logFile, launchLogFile, pidFile)
            stop(context)
            return@withContext false
        }

        running = true
        Log.i(TAG, "Hev root mode successfully started and routed via ${RoutingTableManager.TUN_DEV}")
        true
    }

    suspend fun stop(context: Context) = withContext(Dispatchers.IO) {
        running = false
        val appUid = Process.myUid()

        // 1. Quiesce traffic and remove routing rules/marks first (without touching TUN yet)
        RoutingTableManager.teardownRulesOnly(appUid)

        // 2. Stop hev daemon cleanly (SIGTERM -> brief wait -> SIGKILL + killall backstop)
        val pidFile = File(context.filesDir, "hev.pid")
        val pid = readPid(pidFile)
        if (pid != null) {
            RootShellManager.exec("kill $pid 2>/dev/null || true")
            delay(150)
            RootShellManager.exec("kill -9 $pid 2>/dev/null || true")
        }
        RootShellManager.exec("rm -f ${shSingleQuote(pidFile.absolutePath)}")
        RootShellManager.exec("killall -9 $CLI_NAME 2>/dev/null || true")
        RootShellManager.exec("killall -9 $FALLBACK_CLI_NAME 2>/dev/null || true")

        // 3. Final teardown (rules + best-effort TUN cleanup only AFTER daemon has exited)
        RoutingTableManager.teardownAll(appUid)
        Log.i(TAG, "Hev root daemon stopped and routing tables restored")
    }

    fun isRunning(): Boolean = running

    private suspend fun readPid(pidFile: File): String? {
        val raw = runCatching {
            if (pidFile.exists() && pidFile.canRead()) pidFile.readText().trim() else ""
        }.getOrDefault("").ifBlank {
            val res = RootShellManager.exec("cat ${shSingleQuote(pidFile.absolutePath)} 2>/dev/null")
            if (res.isSuccess) res.stdout.joinToString("").trim() else ""
        }
        return raw.takeIf { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }
    }

    private suspend fun surfaceHevDiagnostics(
        logFile: File,
        launchLogFile: File,
        pidFile: File
    ) {
        val pid = readPid(pidFile)
        val liveness = if (pid == null) {
            "not running (no hev.pid)"
        } else {
            val check = RootShellManager.exec("kill -0 $pid 2>/dev/null")
            if (check.isSuccess) "alive (pid=$pid)" else "exited (stale pid=$pid)"
        }
        val statusMsg = "hev daemon state: $liveness"
        Log.e(TAG, statusMsg)
        XrayLogTailer.error(statusMsg)

        val hevTail = readTail(logFile, HEV_LOG_TAIL_LINES)
        if (hevTail.isNotBlank()) {
            val tailMsg = "hev.log (last $HEV_LOG_TAIL_LINES lines):\n$hevTail"
            Log.e(TAG, tailMsg)
            XrayLogTailer.error(tailMsg)
        } else {
            XrayLogTailer.error("hev.log is empty or missing")
        }

        val launchTail = readTail(launchLogFile, HEV_LOG_TAIL_LINES)
        if (launchTail.isNotBlank()) {
            val launchMsg = "hev-launch.log:\n$launchTail"
            Log.e(TAG, launchMsg)
            XrayLogTailer.error(launchMsg)
        }
    }

    private suspend fun readTail(file: File, maxLines: Int): String {
        val direct = runCatching {
            if (file.exists() && file.canRead()) {
                file.readLines().takeLast(maxLines).joinToString("\n").trim()
            } else {
                ""
            }
        }.getOrDefault("")
        if (direct.isNotBlank()) return direct

        val res = RootShellManager.exec("tail -n $maxLines ${shSingleQuote(file.absolutePath)} 2>/dev/null")
        return if (res.isSuccess) res.stdout.joinToString("\n").trim() else ""
    }

    private fun shSingleQuote(value: String): String = "'${value.replace("'", "'\\''")}'"
}