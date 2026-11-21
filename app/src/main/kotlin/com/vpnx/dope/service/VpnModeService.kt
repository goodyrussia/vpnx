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
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetAddress

/**
 * VPN Service Mode (Non-Root capture path).
 * HARD RULE: Never probes, requests, or executes root commands.
 * Calls startForeground() as the FIRST statement.
 */
class VpnModeService : VpnService() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        XrayLogTailer.error("Uncaught error in VPN service: ${throwable.message}")
        Log.e(TAG, "Uncaught coroutine exception in VpnModeService", throwable)
        ServiceController.updateState(ConnectionState.DISCONNECTED)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
    private val lifecycleMutex = Mutex()
    private val jobLock = Any()
    private var startJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var statsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        // Promote immediately as the very first statement (Hard Rule #3)
        startForegroundCompat(createNotification("Initializing VPN mode..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val statusText = if (intent?.action == ACTION_STOP) {
            "Disconnecting VPN..."
        } else {
            "Connecting VPN..."
        }
        startForegroundCompat(createNotification(statusText))

        when (intent?.action) {
            ACTION_STOP -> {
                enqueueStop()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                enqueueStart(profileId)
            }
        }
        return START_STICKY
    }

    private fun enqueueStart(profileId: String?) {
        synchronized(jobLock) {
            val prev = startJob
            prev?.cancel()
            startJob = serviceScope.launch {
                prev?.join()
                lifecycleMutex.withLock {
                    if (!isActive) return@withLock
                    startVpnLocked(profileId)
                }
            }
        }
    }

    private fun enqueueStop() {
        val prev = synchronized(jobLock) {
            val p = startJob
            p?.cancel()
            startJob = null
            p
        }
        serviceScope.launch {
            prev?.join()
            lifecycleMutex.withLock {
                stopVpnLocked()
            }
        }
    }

    private suspend fun startVpnLocked(profileId: String?) {
        try {
            statsJob?.cancel()
            statsJob = null
            if (vpnInterface != null) {
                teardownResourcesLocked()
            }

            TrafficStatsTracker.reset()
            ServiceController.updateState(ConnectionState.CONNECTING)
            XrayLogTailer.info("Starting VPN Service mode...")

            val db = AppDatabase.getDatabase(this)
            val profile = if (!profileId.isNullOrBlank()) {
                db.profileDao().getProfileById(profileId)
            } else {
                db.profileDao().getSelectedProfileSync()
            }
            currentCoroutineContext().ensureActive()

            if (profile == null) {
                XrayLogTailer.error("No profile configured for connection")
                ServiceController.updateState(ConnectionState.DISCONNECTED)
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
                return
            }

            if (profile.port !in 1..65535) {
                XrayLogTailer.error("Invalid profile server port (${profile.port}); must be between 1 and 65535")
                ServiceController.updateState(ConnectionState.DISCONNECTED)
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
                return
            }

            val settings = SettingsRepository.getInstance(this).settings.value

            if (!SettingsRepository.isValidMtu(settings.tunnelMtu)) {
                XrayLogTailer.error(
                    "Invalid tunnel MTU (${settings.tunnelMtu}); must be between ${SettingsRepository.MIN_MTU} and ${SettingsRepository.MAX_MTU}"
                )
                ServiceController.updateState(ConnectionState.DISCONNECTED)
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
                return
            }

            val resolvedPrimaryDns = resolveDnsServer(settings.primaryDns)
            if (resolvedPrimaryDns == null) {
                XrayLogTailer.error("Invalid Primary DNS server: '${settings.primaryDns}'")
                ServiceController.updateState(ConnectionState.DISCONNECTED)
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
                return
            }

            val resolvedSecondaryDns = if (settings.secondaryDns.isNotBlank()) {
                val resolved = resolveDnsServer(settings.secondaryDns)
                if (resolved == null) {
                    XrayLogTailer.error("Invalid Secondary DNS server: '${settings.secondaryDns}'")
                    ServiceController.updateState(ConnectionState.DISCONNECTED)
                    runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                    stopSelf()
                    return
                }
                resolved
            } else {
                null
            }

            // 1. Establish proxy engine (SSH or Xray)
            val engineSuccess = startProxyEngine(profile)
            currentCoroutineContext().ensureActive()
            if (!engineSuccess) {
                XrayLogTailer.error("Failed to start proxy engine")
                teardownResourcesLocked()
                ServiceController.updateState(ConnectionState.DISCONNECTED)
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
                stopSelf()
                return
            }

            // 2. Build Android OS VpnService TUN interface (IPv4 + IPv6 to prevent IPv6 leak)
            val builder = Builder().apply {
                setMtu(settings.tunnelMtu)
                addAddress("10.0.0.2", 24)
                addAddress("fc00::2", 126)
                addRoute("0.0.0.0", 0)
                addRoute("::", 0)
                addDnsServer(resolvedPrimaryDns)
                if (resolvedSecondaryDns != null) {
                    addDnsServer(resolvedSecondaryDns)
                }
                addDisallowedApplication(packageName)
                setSession("vpnx - ${profile.name}")
            }

            vpnInterface = builder.establish()
            val pfd = vpnInterface
            if (pfd == null) {
                XrayLogTailer.error("Failed to establish VpnService TUN file descriptor")
                stopVpnLocked()
                return
            }
            currentCoroutineContext().ensureActive()

            // 3. Write hev config and start JNI HevTunNative
            val hevConfigFile = File(filesDir, "hev-vpn.yml")
            HevConfigWriter.writeVpnModeConfig(hevConfigFile, settings.tunnelMtu, 10808)
            val hevStarted = HevTunNative.startService(hevConfigFile.absolutePath, pfd.fd)
            currentCoroutineContext().ensureActive()

            if (!hevStarted) {
                XrayLogTailer.error("Failed to start HevTunNative JNI service")
                stopVpnLocked()
                return
            }

            // 4. Optional hotspot proxy share on port 10809
            if (settings.hotspotSharing) {
                startHotshare()
            }

            ServiceController.updateState(ConnectionState.CONNECTED)
            XrayLogTailer.info("VPN Service connected successfully")
            startForegroundCompat(createNotification("Connected: ${profile.name}"))
            startTrafficMonitor()
        } catch (ce: CancellationException) {
            withContext(NonCancellable) {
                teardownResourcesLocked()
            }
            throw ce
        } catch (t: Throwable) {
            XrayLogTailer.error("Error during VPN startup: ${t.message}")
            Log.e(TAG, "VPN startup failed", t)
            withContext(NonCancellable) {
                teardownResourcesLocked()
            }
            ServiceController.updateState(ConnectionState.DISCONNECTED)
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
        }
    }

    private fun resolveDnsServer(raw: String): String? {
        val trimmed = raw.trim()
        if (!SettingsRepository.isValidDnsServer(trimmed)) return null
        if (SettingsRepository.isNumericIp(trimmed)) return trimmed
        return runCatching { InetAddress.getByName(trimmed).hostAddress }.getOrNull()
    }

    private fun startHotshare() {
        try {
            val intent = Intent(this, HotshareService::class.java).apply {
                action = HotshareService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
        } catch (t: Throwable) {
            XrayLogTailer.warn("Failed to start HotshareService: ${t.message}")
            Log.w(TAG, "Failed to start HotshareService", t)
        }
    }

    private fun stopHotshare() {
        runCatching {
            stopService(Intent(this, HotshareService::class.java))
        }
    }

    private suspend fun startProxyEngine(profile: ProfileEntity): Boolean {
        stopProxyEngine()
        return if (profile.protocolType == "SSH") {
            SshEngine.vpnService = this
            SshEngine.start(SshProfile.fromEntity(profile), 10808)
        } else {
            XrayEngine.start(this, XrayProfile.fromEntity(profile), 10808)
        }
    }

    private suspend fun stopProxyEngine() {
        runCatching { SshEngine.stop() }
        SshEngine.vpnService = null
        runCatching { XrayEngine.stop() }
    }

    private suspend fun teardownResourcesLocked() {
        statsJob?.cancel()
        statsJob = null

        stopHotshare()
        runCatching { HevTunNative.stopService() }
        runCatching { vpnInterface?.close() }
        vpnInterface = null

        stopProxyEngine()
    }

    private suspend fun stopVpnLocked() {
        withContext(NonCancellable) {
            ServiceController.updateState(ConnectionState.DISCONNECTING)
            teardownResourcesLocked()
            ServiceController.updateState(ConnectionState.DISCONNECTED)
            XrayLogTailer.info("VPN Service disconnected")
            runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            stopSelf()
        }
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
        synchronized(jobLock) {
            startJob?.cancel()
            startJob = null
        }
        statsJob?.cancel()
        statsJob = null
        serviceScope.cancel()

        stopHotshare()
        runCatching { HevTunNative.stopService() }
        runCatching { vpnInterface?.close() }
        vpnInterface = null

        CoroutineScope(Dispatchers.IO).launch {
            stopProxyEngine()
        }
    }

    companion object {
        private const val TAG = "VpnModeService"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.vpnx.dope.ACTION_VPN_START"
        const val ACTION_STOP = "com.vpnx.dope.ACTION_VPN_STOP"
        const val EXTRA_PROFILE_ID = "extra_profile_id"
    }
}