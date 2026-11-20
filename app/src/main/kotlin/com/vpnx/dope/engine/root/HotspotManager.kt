package com.vpnx.dope.engine.root

import android.util.Log

object HotspotManager {
    private const val TAG = "HotspotManager"

    /**
     * Applies iptables PREROUTING mark 100 on tethering interfaces (ap+, wlan+, rndis+)
     * and clears hardware TC offload filters so tethered packets route through the tunnel.
     */
    suspend fun enableHotspotSharing(): Boolean {
        val commands = listOf(
            "iptables -t mangle -A PREROUTING -i ap+ -j MARK --set-mark 100",
            "iptables -t mangle -A PREROUTING -i wlan+ -j MARK --set-mark 100",
            "iptables -t mangle -A PREROUTING -i rndis+ -j MARK --set-mark 100",
            "tc qdisc del dev ap0 clsact 2>/dev/null",
            "tc qdisc del dev wlan0 clsact 2>/dev/null"
        )
        for (cmd in commands) {
            val res = RootShellManager.exec(cmd)
            if (!res.isSuccess) {
                Log.d(TAG, "Hotspot command notice: $cmd -> ${res.output}")
            }
        }
        return true
    }

    suspend fun disableHotspotSharing() {
        val cleanupCommands = listOf(
            "iptables -t mangle -D PREROUTING -i ap+ -j MARK --set-mark 100 2>/dev/null",
            "iptables -t mangle -D PREROUTING -i wlan+ -j MARK --set-mark 100 2>/dev/null",
            "iptables -t mangle -D PREROUTING -i rndis+ -j MARK --set-mark 100 2>/dev/null"
        )
        for (cmd in cleanupCommands) {
            RootShellManager.exec(cmd)
        }
    }
}
