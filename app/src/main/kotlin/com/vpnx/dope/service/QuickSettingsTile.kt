package com.vpnx.dope.service

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vpnx.dope.data.model.ConnectionState
import com.vpnx.dope.data.repository.SettingsRepository
import com.vpnx.dope.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class QuickSettingsTile : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var stateCollectJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        stateCollectJob?.cancel()
        stateCollectJob = scope.launch {
            ServiceController.connectionState.collectLatest { state ->
                updateTileState(state)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        stateCollectJob?.cancel()
        stateCollectJob = null
    }

    override fun onClick() {
        super.onClick()
        val cur = ServiceController.connectionState.value
        if (cur == ConnectionState.CONNECTED || cur == ConnectionState.CONNECTING) {
            ServiceController.stopTunnel(this)
        } else {
            val settings = SettingsRepository.getInstance(this).settings.value
            if (settings.tunnelMode != "ROOT" && VpnService.prepare(this) != null) {
                // VPN consent cannot be requested from a TileService when MainActivity is not active.
                launchAppForConsent()
                return
            }
            ServiceController.startTunnel(this)
        }
    }

    private fun launchAppForConsent() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(launchIntent)
        }
    }

    private fun updateTileState(state: ConnectionState) {
        val tile = qsTile ?: return
        val subtitleText = when (state) {
            ConnectionState.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                "Connected"
            }
            ConnectionState.CONNECTING -> {
                tile.state = Tile.STATE_ACTIVE
                "Connecting..."
            }
            ConnectionState.DISCONNECTING -> {
                tile.state = Tile.STATE_UNAVAILABLE
                "Disconnecting..."
            }
            ConnectionState.DISCONNECTED -> {
                tile.state = Tile.STATE_INACTIVE
                "Disconnected"
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitleText
        }
        tile.updateTile()
    }
}