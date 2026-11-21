package com.vpnx.dope.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import androidx.core.content.ContextCompat
import com.vpnx.dope.data.model.ConnectionState
import com.vpnx.dope.data.repository.SettingsRepository
import com.vpnx.dope.engine.hev.HevRootEngine
import com.vpnx.dope.engine.xray.XrayLogTailer
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ServiceController {

    private const val TAG = "ServiceController"

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val vpnPermissionRequester = VpnPermissionRequester()
    @Volatile
    private var pendingProfileId: String? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught error in ServiceController", throwable)
        XrayLogTailer.error("Tunnel controller error: ${throwable.message}")
        updateState(ConnectionState.DISCONNECTED)
        pendingProfileId = null
    }

    private val controllerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main + exceptionHandler)

    fun updateState(newState: ConnectionState) {
        _connectionState.value = newState
    }

    fun onVpnPermissionResult(granted: Boolean, context: Context) {
        vpnPermissionRequester.complete(granted)
        if (!granted) {
            XrayLogTailer.warn("OS VPN permission denied or cancelled by user.")
            updateState(ConnectionState.DISCONNECTED)
            pendingProfileId = null
        }
    }

    fun startTunnel(context: Context, profileId: String? = null) {
        val settings = SettingsRepository.getInstance(context).settings.value

        if (settings.tunnelMode == "ROOT") {
            controllerScope.launch(Dispatchers.IO) {
                // Non-blocking root availability preflight check (Hard Rule #2)
                XrayLogTailer.info("Checking KernelSU root access...")
                val rootOk = HevRootEngine.isRootAvailable()
                if (!rootOk) {
                    XrayLogTailer.error("Root access denied or KernelSU not detected. Cannot start Root mode.")
                    updateState(ConnectionState.DISCONNECTED)
                    return@launch
                }

                XrayLogTailer.info("Root access confirmed; starting RootModeService...")
                val intent = Intent(context, RootModeService::class.java).apply {
                    action = RootModeService.ACTION_START
                    putExtra(RootModeService.EXTRA_PROFILE_ID, profileId)
                }
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to start RootModeService", t)
                    XrayLogTailer.error("Failed to start Root service: ${t.message}")
                    updateState(ConnectionState.DISCONNECTED)
                }
            }
        } else {
            // VPN Service Mode (Zero root operations)
            controllerScope.launch {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    pendingProfileId = profileId
                    updateState(ConnectionState.CONNECTING)
                    XrayLogTailer.warn("OS VPN permission required. Launching system prompt...")
                    val granted = try {
                        vpnPermissionRequester.request(prepareIntent)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Failed to request OS VPN permission", t)
                        XrayLogTailer.error("Failed to request OS VPN permission: ${t.message}")
                        updateState(ConnectionState.DISCONNECTED)
                        pendingProfileId = null
                        return@launch
                    }
                    if (!granted) {
                        XrayLogTailer.warn("OS VPN permission denied or cancelled by user.")
                        updateState(ConnectionState.DISCONNECTED)
                        pendingProfileId = null
                        return@launch
                    }
                    XrayLogTailer.info("OS VPN permission granted. Resuming connection...")
                }

                val activeProfileId = profileId ?: pendingProfileId
                pendingProfileId = null

                val intent = Intent(context, VpnModeService::class.java).apply {
                    action = VpnModeService.ACTION_START
                    putExtra(VpnModeService.EXTRA_PROFILE_ID, activeProfileId)
                }
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to start VpnModeService", t)
                    XrayLogTailer.error("Failed to start VPN service: ${t.message}")
                    updateState(ConnectionState.DISCONNECTED)
                    pendingProfileId = null
                }
            }
        }
    }

    fun stopTunnel(context: Context) {
        pendingProfileId = null
        val settings = SettingsRepository.getInstance(context).settings.value
        if (settings.tunnelMode == "ROOT") {
            val intent = Intent(context, RootModeService::class.java).apply {
                action = RootModeService.ACTION_STOP
            }
            context.startService(intent)
        } else {
            val intent = Intent(context, VpnModeService::class.java).apply {
                action = VpnModeService.ACTION_STOP
            }
            context.startService(intent)
        }
    }
}