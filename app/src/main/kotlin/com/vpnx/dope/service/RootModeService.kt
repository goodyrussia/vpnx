package com.vpnx.dope.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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
import com.vpnx.dope.engine.hev.HevRootEngine
import com.vpnx.dope.engine.hev.HevWatchdog
import com.vpnx.dope.engine.root.HotspotManager
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

/**
 * Root Mode Service (KernelSU-Next root path).
 * Separate Service class (NOT a VpnService).
 * Calls startForeground() as the FIRST statement.
 */
class RootModeService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var watchdog: HevWatchdog? = null
    private var statsJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Promote immediately as the very first statement (Hard Rule #3)
        startForegroundCompat(createNotification("Initializing Root mode..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(createNotification("Connecting Root hev tunnel..."))

        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch { stopRoot() }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                serviceScope.launch { startRoot(profileId) }
            }
        }
        return START_STICKY
    }

    private suspend fun startRoot(profileId: String?) {
        TrafficStatsTracker.reset()
        ServiceController.updateState(ConnectionState.CONNECTING)
        XrayLogTailer.info("Starting Root (hev) mode...")

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

        // 2. Start standalone hev daemon & apply kernel routing rules
        val hevStarted = HevRootEngine.start(this, settings.tunnelMtu, 10808)
        if (!hevStarted) {
            XrayLogTailer.error("Failed to start root hev daemon")
            stopRoot()
            return
        }

        // 3. Optional hotspot sharing
        if (settings.hotspotSharing) {
            HotspotManager.enableHotspotSharing()
        }

        // 4. Start Watchdog
        watchdog = HevWatchdog {
            XrayLogTailer.warn("Root hev watchdog detected tunnel loss")
            serviceScope.launch { stopRoot() }
        }.apply { start(serviceScope, 10808) }

        ServiceController.updateState(ConnectionState.CONNECTED)
        XrayLogTailer.info("Root hev mode connected and routing active")
        startForegroundCompat(createNotification("Connected (Root): ${profile.name}"))
        startTrafficMonitor()
    }

    private suspend fun startProxyEngine(profile: ProfileEntity): Boolean {
        return if (profile.protocolType == "SSH") {
            SshEngine.start(SshProfile.fromEntity(profile), 10808)
        } else {
            XrayEngine.start(this, XrayProfile.fromEntity(profile), 10808)
        }
    }

    private suspend fun stopProxyEngine() {
        SshEngine.stop()
        XrayEngine.stop()
    }

    private suspend fun stopRoot() {
        ServiceController.updateState(ConnectionState.DISCONNECTING)
        statsJob?.cancel()
        statsJob = null
        watchdog?.stop()
        watchdog = null

        HotspotManager.disableHotspotSharing()
        HevRootEngine.stop(this)
        stopProxyEngine()

        ServiceController.updateState(ConnectionState.DISCONNECTED)
        XrayLogTailer.info("Root hev tunnel disconnected")
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
            .setContentTitle("vpnx (Root)")
            .setContentText(statusText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        statsJob?.cancel()
        watchdog?.stop()
    }

    companion object {
        private const val TAG = "RootModeService"
        const val NOTIFICATION_ID = 2002
        const val ACTION_START = "com.vpnx.dope.ACTION_ROOT_START"
        const val ACTION_STOP = "com.vpnx.dope.ACTION_ROOT_STOP"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
    }
}
