package com.vpnx.dope.engine.root

import android.os.Process
import android.util.Log
import com.vpnx.dope.engine.xray.XrayLogTailer

object RoutingTableManager {
    private const val TAG = "RoutingTableManager"
    const val FWMARK = 438
    const val HOTSPOT_FWMARK = 100
    const val TABLE_ID = 100
    const val TUN_DEV = "vpnx0"

    /**
     * Applies IPv4 and IPv6 policy routing rules (pref 5, 10, 15, 20) in persistent root shell:
     * 1. App UID bypass (pref 5): Outbound traffic from this app routes via table main (DIRECT)
     * 2. Hev mark bypass (pref 10): Outbound sockets marked 438 route via table main (DIRECT)
     * 3. Hotspot mark routing (pref 15): Tethered packets marked 100 route into table 100 dev vpnx0
     * 4. Capture default route (pref 20): All remaining device traffic goes into table 100 dev vpnx0
     */
    suspend fun applyRules(appUid: Int = Process.myUid()): Boolean {
        teardownRules(appUid)

        val commands = listOf(
            // --- IPv4 ---
            // 1. App UID bypass (pref 5)
            "ip rule add uidrange $appUid-$appUid lookup main pref 5",
            "iptables -t mangle -A OUTPUT -m owner --uid-owner $appUid -j MARK --set-mark $FWMARK",

            // 2. Hev daemon fwmark bypass (pref 10)
            "ip rule add fwmark $FWMARK lookup main pref 10",

            // 3. Hotspot marked packets into table 100 (pref 15)
            "ip rule add fwmark $HOTSPOT_FWMARK lookup $TABLE_ID pref 15",

            // 4. TUN default route into table 100 (pref 20)
            "ip route add default dev $TUN_DEV table $TABLE_ID",
            "ip rule add from all lookup $TABLE_ID pref 20",

            // 5. Disable reverse-path filtering
            "sysctl -w net.ipv4.conf.$TUN_DEV.rp_filter=0",

            // --- IPv6 ---
            "ip -6 rule add uidrange $appUid-$appUid lookup main pref 5",
            "ip6tables -t mangle -A OUTPUT -m owner --uid-owner $appUid -j MARK --set-mark $FWMARK",
            "ip -6 rule add fwmark $FWMARK lookup main pref 10",
            "ip -6 rule add fwmark $HOTSPOT_FWMARK lookup $TABLE_ID pref 15",
            "ip -6 route add default dev $TUN_DEV table $TABLE_ID",
            "ip -6 rule add from all lookup $TABLE_ID pref 20"
        )

        for (cmd in commands) {
            val res = RootShellManager.exec(cmd)
            if (!res.isSuccess) {
                Log.e(TAG, "Routing command failed: $cmd -> ${res.output}")
                XrayLogTailer.error("Root routing command failed: $cmd (${res.output.ifBlank { "exit ${res.code}" }})")
                teardownRules(appUid)
                return false
            }
        }
        return true
    }

    /**
     * Cleans up all applied IPv4 and IPv6 routing rules, routes, and iptables/ip6tables marks.
     */
    suspend fun teardownRules(appUid: Int = Process.myUid()) {
        val cleanupCommands = listOf(
            "ip rule del fwmark $FWMARK lookup main 2>/dev/null",
            "ip rule del fwmark $HOTSPOT_FWMARK lookup $TABLE_ID 2>/dev/null",
            "ip rule del uidrange $appUid-$appUid lookup main 2>/dev/null",
            "ip rule del lookup $TABLE_ID 2>/dev/null",
            "ip route del default dev $TUN_DEV table $TABLE_ID 2>/dev/null",
            "iptables -t mangle -D OUTPUT -m owner --uid-owner $appUid -j MARK --set-mark $FWMARK 2>/dev/null",
            "ip -6 rule del fwmark $FWMARK lookup main 2>/dev/null",
            "ip -6 rule del fwmark $HOTSPOT_FWMARK lookup $TABLE_ID 2>/dev/null",
            "ip -6 rule del uidrange $appUid-$appUid lookup main 2>/dev/null",
            "ip -6 rule del lookup $TABLE_ID 2>/dev/null",
            "ip -6 route del default dev $TUN_DEV table $TABLE_ID 2>/dev/null",
            "ip6tables -t mangle -D OUTPUT -m owner --uid-owner $appUid -j MARK --set-mark $FWMARK 2>/dev/null",
            "ip link set $TUN_DEV down 2>/dev/null",
            "ip link del $TUN_DEV 2>/dev/null"
        )
        for (cmd in cleanupCommands) {
            RootShellManager.exec(cmd)
        }
    }
}