package com.vpnx.dope.services

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.vpnx.dope.vpn.SmartVpnService

@RequiresApi(Build.VERSION_CODES.N)
class QuickSettingsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState(false)
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return

        if (tile.state == Tile.STATE_ACTIVE) {
            val stopIntent = Intent(this, SmartVpnService::class.java).apply {
                action = SmartVpnService.ACTION_STOP
            }
            startService(stopIntent)
            updateTileState(false)
        } else {
            val prefs = getSharedPreferences("vpnx_v12_settings_prefs", MODE_PRIVATE)
            val rootMode = prefs.getString("tunnel_mode", "VPN") == "ROOT"
            if (rootMode) {
                // Root mode needs no VpnService permission.
                val startIntent = Intent(this, SmartVpnService::class.java).apply {
                    action = SmartVpnService.ACTION_START
                    putExtra(SmartVpnService.EXTRA_ROOT_MODE, true)
                }
                startService(startIntent)
                updateTileState(true)
            } else {
                val prepareIntent = VpnService.prepare(this)
                if (prepareIntent == null) {
                    val startIntent = Intent(this, SmartVpnService::class.java).apply {
                        action = SmartVpnService.ACTION_START
                    }
                    startService(startIntent)
                    updateTileState(true)
                }
            }
        }
    }

    private fun updateTileState(isActive: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isActive) "vpnx (Active)" else "vpnx"
        tile.updateTile()
    }
}
