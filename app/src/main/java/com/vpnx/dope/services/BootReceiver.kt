package com.vpnx.dope.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.vpnx.dope.vpn.SmartVpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("vpnx_v12_settings_prefs", Context.MODE_PRIVATE)
            val rootMode = prefs.getString("tunnel_mode", "VPN") == "ROOT"
            if (rootMode) {
                // Root mode needs no VpnService permission; the service itself
                // fails cleanly (and logs) if root is not ready yet at boot.
                val startIntent = Intent(context, SmartVpnService::class.java).apply {
                    action = SmartVpnService.ACTION_START
                    putExtra(SmartVpnService.EXTRA_ROOT_MODE, true)
                }
                context.startService(startIntent)
            } else if (VpnService.prepare(context) == null) {
                // Verify VPN permission is already granted by Android
                val startIntent = Intent(context, SmartVpnService::class.java).apply {
                    action = SmartVpnService.ACTION_START
                }
                context.startService(startIntent)
            }
        }
    }
}
