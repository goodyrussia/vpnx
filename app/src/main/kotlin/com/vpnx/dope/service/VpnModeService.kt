package com.vpnx.dope.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vpnx.dope.R
import com.vpnx.dope.VpnApplication
import com.vpnx.dope.data.AppDatabase
import com.vpnx.dope.data.entity.ProfileEntity
import com.vpnx.dope.data.model.ConnectionState
import com.vpnx.dope.data.model.SshProfile
import com.vpnx.dope.data.model.XrayProfile
import com.vpnx.dope.data.repository.SettingsRepository
import com.vpnx.dope.engine.common.TrafficStatsTracker
import com.vpnx.dope.engine.hev.HevConfigWriter
import com.vpnx.dope.engine.hev.HevTunNative
import com.vpnx.dope.engine.ssh.SshEngine
import com.vpnx.dope.engine.xray.XrayEngine
import com.vpnx.dope.engine.xray.XrayLogTailer
import com.vpnx.dope.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * VPN Service Mode (Non-Root capture path).
 * HARD RULE: Never probes, requests, or executes root commands.
 * Calls startForeground() as the FIRST statement.
 */
class VpnModeService : VpnService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var vpnInterface: ParcelFileDescriptor? = null
    private var statsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Promote immediately as the very first statement (Hard Rule #3)
        startForegroundCompat(createNotification("Initializing VPN mode..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(createNotification("Connecting VPN..."))

        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch { stopVpn() }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                serviceScope.launch { startVpn(profileId) }
            }
        }
        return START_STICKY
    }

    private suspend fun startVpn(profileId: String?) {
        TrafficStatsTracker.reset()
        ServiceController.updateState(ConnectionState.CONNECTING)
        XrayLogTailer.info("Starting VPN Service mode...")

        val db = AppDatabase.getDatabase(this)
        val profile = if (!profileId.isNullOrBlank()) {
            db.profileDao().getProfileById(profileId)
        } else {
            db.profileDao().getSelectedProfileSync()
        }

        if (profile == null) {
            XrayLogTailer.error("No profile configured for connection")
            ServiceController.updateState(ConnectionState.DISCONNECTED)
            stopSelf()
            return
        }

        val settings = SettingsRepository.getInstance(this).settings.value

        // 1. Establish proxy engine (SSH or Xray)
        val engineSuccess = startProxyEngine(profile)
        if (!engineSuccess) {
            XrayLogTailer.error("Failed to start proxy engine")
            ServiceController.updateState(ConnectionState.DISCONNECTED)
            stopSelf()
            return
        }

        // 2. Build Android OS VpnService TUN interface
        try {
            val builder = Builder().apply {
                setMtu(settings.tunnelMtu)
                addAddress("10.0.0.2", 24)
                addRoute("0.0.0.0", 0)
                addDnsServer(settings.primaryDns)
                if (settings.secondaryDns.isNotBlank()) {
                    addDnsServer(settings.secondaryDns)
                }
                addDisallowedApplication(packageName)
                setSession("vpnx - ${profile.name}")
            }

            vpnInterface = builder.establish()
            val pfd = vpnInterface
            if (pfd == null) {
                XrayLogTailer.error("Failed to establish VpnService TUN file descriptor")
                stopVpn()
                return
            }

            // 3. Write hev config and start JNI HevTunNative
            val hevConfigFile = File(filesDir, "hev-vpn.yml")
            HevConfigWriter.writeVpnModeConfig(hevConfigFile, settings.tunnelMtu, 10808)
            val hevStarted = HevTunNative.startService(hevConfigFile.absolutePath, pfd.fd)

            if (!hevStarted) {
                XrayLogTailer.error("Failed to start HevTunNative JNI service")
                stopVpn()
                return
            }

            ServiceController.updateState(ConnectionState.CONNECTED)
            XrayLogTailer.info("VPN Service connected successfully")
            startForegroundCompat(createNotification("Connected: ${profile.name}"))
            startTrafficMonitor()
        } catch (t: Throwable) {
            XrayLogTailer.error("Error during VPN establishment: ${t.message}")
            Log.e(TAG, "VPN startup failed", t)
            stopVpn()
        }
    }

    private suspend fun startProxyEngine(profile: ProfileEntity): Boolean {
        return if (profile.protocolType == "SSH") {
            SshEngine.vpnService = this
            SshEngine.start(SshProfile.fromEntity(profile), 10808)
        } else {
            XrayEngine.start(this, XrayProfile.fromEntity(profile), 10808)
        }
    }

    private suspend fun stopProxyEngine() {
        SshEngine.stop()
        SshEngine.vpnService = null
        XrayEngine.stop()
    }

    private suspend fun stopVpn() {
        ServiceController.updateState(ConnectionState.DISCONNECTING)
        statsJob?.cancel()
        statsJob = null

        HevTunNative.stopService()
        runCatching { vpnInterface?.close() }
        vpnInterface = null

        stopProxyEngine()
        ServiceController.updateState(ConnectionState.DISCONNECTED)
        XrayLogTailer.info("VPN Service disconnected")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startTrafficMonitor() {
        statsJob?.cancel()
        statsJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                TrafficStatsTracker.tick()
            }
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Critical: startForegroundCompat failed", t)
        }
    }

    private fun createNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VpnApplication.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("vpnx")
            .setContentText(statusText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        statsJob?.cancel()
    }

    companion object {
        private const val TAG = "VpnModeService"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.vpnx.dope.ACTION_VPN_START"
        const val ACTION_STOP = "com.vpnx.dope.ACTION_VPN_STOP"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
    }
}
