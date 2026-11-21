package com.vpnx.dope.engine.hev

import android.content.Context
import android.os.Process
import android.util.Log
import com.vpnx.dope.engine.root.RootShellManager
import com.vpnx.dope.engine.root.RoutingTableManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object HevRootEngine {
    private const val TAG = "HevRootEngine"
    private const val CLI_NAME = "libhev-socks5-tunnel-cli.so"

    @Volatile
    private var running = false

    suspend fun isRootAvailable(): Boolean = RootShellManager.checkRootAccess()

    /**
     * Resolves the executable path of hev-socks5-tunnel-cli.
     * Searches nativeLibraryDir first; falls back to extracting from assets/filesDir.
     */
    private fun resolveCliExecutable(context: Context): String {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val cliInLib = File(libDir, CLI_NAME)
        if (cliInLib.exists() && cliInLib.canExecute()) {
            return cliInLib.absolutePath
        }

        // Extract to internal storage if not executable directly from libDir
        val target = File(context.filesDir, "hev-socks5-tunnel-cli")
        if (!target.exists() || target.length() == 0L || (cliInLib.exists() && target.length() != cliInLib.length())) {
            if (cliInLib.exists()) {
                cliInLib.inputStream().use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                }
            }
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
            return@withContext false
        }

        // Wait up to 3 seconds for vpnx0 interface to appear
        var interfaceUp = false
        for (i in 0 until 15) {
            kotlinx.coroutines.delay(200)
            val checkRes = RootShellManager.exec("ip link show ${RoutingTableManager.TUN_DEV}")
            if (checkRes.isSuccess && checkRes.stdout.any { it.contains(RoutingTableManager.TUN_DEV) }) {
                interfaceUp = true
                break
            }
        }

        if (!interfaceUp) {
            Log.e(TAG, "hev daemon started but ${RoutingTableManager.TUN_DEV} never materialized")
            stop(context)
            return@withContext false
        }

        // 2. Apply kernel routing rules (pref 5, 10, 20)
        RoutingTableManager.applyRules(Process.myUid())
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
            val pid = pidFile.readText().trim()
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
