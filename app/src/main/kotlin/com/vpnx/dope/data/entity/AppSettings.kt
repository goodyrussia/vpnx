package com.vpnx.dope.data.entity

data class AppSettings(
    val tunnelMode: String = "VPN", // "VPN" (non-root) or "ROOT" (hev TUN2SOCKS)
    val tunnelMtu: Int = 1400,
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "8.8.8.8",
    val remoteDns: Boolean = true,
    val hotspotSharing: Boolean = false,
    val tcpNoDelay: Boolean = true,
    val wakeLock: Boolean = false,
    val theme: String = "SYSTEM" // "SYSTEM", "LIGHT", "DARK"
)
