package com.vpnx.dope

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.vpnx.dope.data.AppDatabase
import com.vpnx.dope.engine.root.RootShellManager
import com.vpnx.dope.engine.xray.XrayEngine
import com.vpnx.dope.engine.xray.XrayLogTailer
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.security.Security
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VpnApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 0. Install global uncaught exception handler for on-device crash diagnostics
        setupCrashDiagnostics()

        // 1. Explicitly register BouncyCastle at Priority 1 for X25519/Ed25519 support on Android 7-16
        try {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        } catch (t: Throwable) {
            Log.w("VpnApplication", "Failed to register BouncyCastle provider", t)
        }

        // 2. Initialize libsu 6.0.0 RootShellManager once from Application.onCreate() (PLAN.md 6.1)
        RootShellManager.init()

        // 3. Initialize Room database instance
        AppDatabase.getDatabase(this)

        // 4. Create Notification Channels
        createNotificationChannels()

        // 5. Initialize Xray-core environment once at startup (idempotent)
        try {
            XrayEngine.initCoreEnv(this)
        } catch (t: Throwable) {
            Log.e("VpnApplication", "Failed to pre-initialize Xray core environment", t)
        }
    }

    private fun setupCrashDiagnostics() {
        // Replay any previous crash report into XrayLogTailer so user can inspect it in Logs tab
        try {
            val crashFile = File(filesDir, CRASH_LOG_FILE)
            if (crashFile.exists() && crashFile.length() > 0) {
                val lastCrash = crashFile.readText()
                XrayLogTailer.error("=== PREVIOUS CRASH REPORT DETECTED ===\n$lastCrash")
            }
        } catch (t: Throwable) {
            Log.e("VpnApplication", "Failed to replay previous crash log", t)
        }

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                val timestamp = timeFormat.format(Date())
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()

                val appVersion = runCatching {
                    @Suppress("DEPRECATION")
                    val pInfo = packageManager.getPackageInfo(packageName, 0)
                    val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode
                    "${pInfo.versionName} (Build $vCode)"
                }.getOrDefault("Unknown")

                val report = buildString {
                    appendLine("Timestamp: $timestamp")
                    appendLine("App Version: $appVersion")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                    appendLine("Thread: ${thread.name} (id: ${thread.id})")
                    appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                    appendLine("Stack Trace:")
                    appendLine(stackTrace)
                }

                Log.e("VpnApplication", "FATAL UNCAUGHT EXCEPTION:\n$report")
                XrayLogTailer.error("FATAL UNCAUGHT EXCEPTION:\n$report")

                // 1. Write to internal storage (guaranteed writable)
                val internalFile = File(filesDir, CRASH_LOG_FILE)
                internalFile.writeText(report)

                // 2. Also write to external filesDir if available (user-accessible)
                try {
                    getExternalFilesDir(null)?.let { extDir ->
                        File(extDir, CRASH_LOG_FILE).writeText(report)
                    }
                } catch (_: Throwable) {}
            } catch (e: Throwable) {
                Log.e("VpnApplication", "Failed to record uncaught exception", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "vpnx_service_channel"
        const val CRASH_LOG_FILE = "vpnx-crash.log"
        lateinit var instance: VpnApplication
            private set
    }
}