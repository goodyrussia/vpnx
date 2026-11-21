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
    private const val TUN_WAIT_DEADLINE_MS = 10_000L
    private const val TUN_POLL_INTERVAL_MS = 250L

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
        val target = File(context.filesDir, "hev-socks5-tunnel-cli")
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
        if (running) stop(context)

        val cliPath = resolveCliExecutable(context)
        if (cliPath == null) {
            val msg = "hev CLI executable ($CLI_NAME) not found in nativeLibraryDir or filesDir"
            Log.e(TAG, msg)
            XrayLogTailer.error(msg)
            return@withContext false
        }

        val configFile = File(context.filesDir, "hev.yml")
        val logFile = File(context.filesDir, "hev.log")
        val pidFile = File(context.filesDir, "hev.pid")

        HevConfigWriter.writeRootModeConfig(
            outFile = configFile,
            tunName = RoutingTableManager.TUN_DEV,
            mtu = mtu,
            socksPort = socksPort,
            fwmark = RoutingTableManager.FWMARK,
            logFile = logFile,
            pidFile = pidFile
        )

        // 1. Launch standalone hev daemon using setsid + nohup (PLAN.md 6.3)
        val startCmd = "nohup setsid $cliPath ${configFile.absolutePath} >> ${logFile.absolutePath} 2>&1 < /dev/null &"
        Log.i(TAG, "Launching hev root daemon: $startCmd")
        val execRes = RootShellManager.exec(startCmd)
        if (!execRes.isSuccess) {
            Log.e(TAG, "Failed to launch hev daemon: ${execRes.output}")
            XrayLogTailer.error("Failed to launch hev daemon: ${execRes.output}")
            return@withContext false
        }

        // Wait up to 10 seconds wall-clock for vpnx0 interface to appear
        var interfaceUp = false
        val deadline = System.currentTimeMillis() + TUN_WAIT_DEADLINE_MS
        while (System.currentTimeMillis() < deadline) {
            delay(TUN_POLL_INTERVAL_MS)
            val checkRes = RootShellManager.exec("ip link show ${RoutingTableManager.TUN_DEV}")
            if (checkRes.isSuccess && checkRes.stdout.any { it.contains(RoutingTableManager.TUN_DEV) }) {
                interfaceUp = true
                break
            }
        }

        if (!interfaceUp) {
            val msg = "hev daemon started but ${RoutingTableManager.TUN_DEV} never materialized within ${TUN_WAIT_DEADLINE_MS / 1000}s"
            Log.e(TAG, msg)
            XrayLogTailer.error(msg)
            stop(context)
            return@withContext false
        }

        // 2. Apply kernel routing rules (pref 5, 10, 15, 20) — fail start if any rule fails
        val rulesApplied = RoutingTableManager.applyRules(Process.myUid())
        if (!rulesApplied) {
            val msg = "Failed to apply kernel routing rules for ${RoutingTableManager.TUN_DEV}"
            Log.e(TAG, msg)
            XrayLogTailer.error(msg)
            stop(context)
            return@withContext false
        }

        running = true
        Log.i(TAG, "Hev root mode successfully started and routed via ${RoutingTableManager.TUN_DEV}")
        true
    }

    suspend fun stop(context: Context) = withContext(Dispatchers.IO) {
        running = false
        // 1. Teardown routing rules and tables
        RoutingTableManager.teardownRules(Process.myUid())

        // 2. Kill hev daemon
        val pidFile = File(context.filesDir, "hev.pid")
        if (pidFile.exists()) {
            val pid = runCatching { pidFile.readText().trim() }.getOrDefault("")
            if (pid.isNotEmpty()) {
                RootShellManager.exec("kill -9 $pid 2>/dev/null")
            }
            pidFile.delete()
        }
        RootShellManager.exec("killall -9 hev-socks5-tunnel-cli 2>/dev/null || true")
        Log.i(TAG, "Hev root daemon stopped and routing tables restored")
    }

    fun isRunning(): Boolean = running
}