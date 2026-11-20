package com.vpnx.dope.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vpnx.dope.data.model.ConnectionState
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
            ServiceController.startTunnel(this)
        }
    }

    private fun updateTileState(state: ConnectionState) {
        val tile = qsTile ?: return
        when (state) {
            ConnectionState.CONNECTED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = "Connected"
            }
            ConnectionState.CONNECTING -> {
                tile.state = Tile.STATE_ACTIVE
                tile.subtitle = "Connecting..."
            }
            ConnectionState.DISCONNECTING -> {
                tile.state = Tile.STATE_UNAVAILABLE
                tile.subtitle = "Disconnecting..."
            }
            ConnectionState.DISCONNECTED -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = "Disconnected"
            }
        }
        tile.updateTile()
    }
}
