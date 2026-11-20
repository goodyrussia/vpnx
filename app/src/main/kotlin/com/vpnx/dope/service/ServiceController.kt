package com.vpnx.dope.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.vpnx.dope.data.model.ConnectionState
import com.vpnx.dope.data.repository.SettingsRepository
import com.vpnx.dope.engine.hev.HevRootEngine
import com.vpnx.dope.engine.xray.XrayLogTailer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ServiceController {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val controllerScope = CoroutineScope(Dispatchers.Main)

    fun updateState(newState: ConnectionState) {
        _connectionState.value = newState
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
                ContextCompat.startForegroundService(context, intent)
            }
        } else {
            // VPN Service Mode (Zero root operations)
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                // User must approve OS VPN dialog first
                XrayLogTailer.warn("OS VPN permission required. Launching system prompt...")
                prepareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(prepareIntent)
                return
            }

            val intent = Intent(context, VpnModeService::class.java).apply {
                action = VpnModeService.ACTION_START
                putExtra(VpnModeService.EXTRA_PROFILE_ID, profileId)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun stopTunnel(context: Context) {
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
