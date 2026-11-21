package com.vpnx.dope.engine.root

import android.os.Process
import android.util.Log
import com.vpnx.dope.engine.ssh.SshEngine
import com.vpnx.dope.engine.xray.XrayLogTailer

object RoutingTableManager {
    private const val TAG = "RoutingTableManager"
    const val FWMARK = 438
    const val HOTSPOT_FWMARK = 100
    const val TABLE_ID = 100
    const val TUN_DEV = "vpnx0"

    /**
     * Checks whether the TUN interface created by hev currently exists.
     */
    suspend fun isTunInterfaceUp(): Boolean {
        val res = RootShellManager.exec("ip link show $TUN_DEV")
        return res.isSuccess && res.stdout.any { it.contains(TUN_DEV) }
    }

    /**
     * Applies IPv4 (and optionally IPv6) policy routing rules (pref 5, 10, 15, 20) in persistent root shell:
     * 1. App UID bypass (pref 5): Outbound traffic from this app routes via table main (DIRECT)
     * 2. Hev mark bypass (pref 10): Outbound sockets marked 438 route via table main (DIRECT)
     * 3. Hotspot mark routing (pref 15): Tethered packets marked 100 route into table 100 dev vpnx0
     * 4. Capture default route (pref 20): All remaining device traffic goes into table 100 dev vpnx0
     *
     * Skips IPv6 capture when [enableIpv6] is false (defaulting to false when [SshEngine] is active
     * so IPv6 SYNs do not hang in SSH mode while preserving Xray-mode IPv6 routing unchanged).
     * Pre-cleans only stale rules/routes/marks via [teardownRulesOnly] — never deletes [TUN_DEV].
     */
    suspend fun applyRules(
        appUid: Int = Process.myUid(),
        enableIpv6: Boolean = !SshEngine.isRunning()
    ): Boolean {
        teardownRulesOnly(appUid)

        if (!isTunInterfaceUp()) {
            val msg = "TUN device $TUN_DEV is not present before applying routing rules"
            Log.e(TAG, msg)
            XrayLogTailer.error(msg)
            return false
        }

        val commands = buildList {
            // Ensure TUN link is administratively up before installing routes
            add("ip link set dev $TUN_DEV up")

            // --- IPv4 ---
            // 1. App UID bypass (pref 5)
            add("ip rule add uidrange $appUid-$appUid lookup main pref 5")
            add("iptables -t mangle -A OUTPUT -m owner --uid-owner $appUid -j MARK --set-mark $FWMARK")

            // 2. Hev daemon fwmark bypass (pref 10)
            add("ip rule add fwmark $FWMARK lookup main pref 10")

            // 3. Hotspot marked packets into table 100 (pref 15)
            add("ip rule add fwmark $HOTSPOT_FWMARK lookup $TABLE_ID pref 15")

            // 4. TUN default route into table 100 (pref 20) — replace is idempotent
            add("ip route replace default dev $TUN_DEV table $TABLE_ID")
            add("ip rule add from all lookup $TABLE_ID pref 20")

            // 5. Relax reverse-path filtering (best-effort if sysctl binary is absent)
            add("echo 0 > /proc/sys/net/ipv4/conf/$TUN_DEV/rp_filter 2>/dev/null || sysctl -w net.ipv4.conf.$TUN_DEV.rp_filter=0 2>/dev/null || true")

            // --- IPv6 (enabled for Xray; skipped in SSH mode where the tunnel is IPv4-only) ---
            if (enableIpv6) {
                add("ip -6 rule add uidrange $appUid-$appUid lookup main pref 5")
                add("ip6tables -t mangle -A OUTPUT -m owner --uid-owner $appUid -j MARK --set-mark $FWMARK")
                add("ip -6 rule add fwmark $FWMARK lookup main pref 10")
                add("ip -6 rule add fwmark $HOTSPOT_FWMARK lookup $TABLE_ID pref 15")
                add("ip -6 route replace default dev $TUN_DEV table $TABLE_ID")
                add("ip -6 rule add from all lookup $TABLE_ID pref 20")
            }
        }

        if (!enableIpv6) {
            XrayLogTailer.info("Root routing: skipping IPv6 capture rules in SSH mode (IPv4 full tunnel active)")
        }

        for (cmd in commands) {
            val res = RootShellManager.exec(cmd)
            if (!res.isSuccess) {
                Log.e(TAG, "Routing command failed: $cmd -> ${res.output}")
                XrayLogTailer.error("Root routing command failed: $cmd (${res.output.ifBlank { "exit ${res.code}" }})")
                teardownRulesOnly(appUid)
                return false
            }
        }
        return true
    }

    /**
     * Removes ONLY ip rules, table routes, and iptables/ip6tables mangle marks.
     * NEVER touches or deletes [TUN_DEV] — safe to call before or during start/rollback.
     */
    suspend fun teardownRulesOnly(appUid: Int = Process.myUid()) {
        val cleanupCommands = listOf(
            "ip rule del fwmark $FWMARK lookup main 2>/dev/null",
            "ip rule del fwmark $HOTSPOT_FWMARK lookup $TABLE_ID 2>/dev/null",
            "ip rule del uidrange $appUid-$appUid lookup main 2>/dev/null",
            "ip rule del lookup $TABLE_ID 2>/dev/null",
            "ip route del default dev $TUN_DEV table $TABLE_ID 2>/dev/null",
            "ip route flush table $TABLE_ID 2>/dev/null",
            "iptables -t mangle -D OUTPUT -m owner --uid-owner $appUid -j MARK --set-mark $FWMARK 2>/dev/null",
            "ip -6 rule del fwmark $FWMARK lookup main 2>/dev/null",
            "ip -6 rule del fwmark $HOTSPOT_FWMARK lookup $TABLE_ID 2>/dev/null",
            "ip -6 rule del uidrange $appUid-$appUid lookup main 2>/dev/null",
            "ip -6 rule del lookup $TABLE_ID 2>/dev/null",
            "ip -6 route del default dev $TUN_DEV table $TABLE_ID 2>/dev/null",
            "ip -6 route flush table $TABLE_ID 2>/dev/null",
            "ip6tables -t mangle -D OUTPUT -m owner --uid-owner $appUid -j MARK --set-mark $FWMARK 2>/dev/null"
        )
        for (cmd in cleanupCommands) {
            RootShellManager.exec(cmd)
        }
    }

    /**
     * Full teardown for stop paths ONLY: removes all routing rules/marks and cleans up [TUN_DEV]
     * if it was left behind after the hev daemon exited.
     */
    suspend fun teardownAll(appUid: Int = Process.myUid()) {
        teardownRulesOnly(appUid)
        val linkCleanupCommands = listOf(
            "ip link set $TUN_DEV down 2>/dev/null",
            "ip link del $TUN_DEV 2>/dev/null"
        )
        for (cmd in linkCleanupCommands) {
            RootShellManager.exec(cmd)
        }
    }

    /**
     * Safe compatibility alias — removes only rules/routes/marks without deleting [TUN_DEV].
     */
    suspend fun teardownRules(appUid: Int = Process.myUid()) {
        teardownRulesOnly(appUid)
    }
}