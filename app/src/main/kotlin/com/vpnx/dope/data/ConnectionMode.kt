package com.vpnx.dope.data

/**
 * How VPNX carries phone traffic into the engine's local SOCKS5 point.
 * Everything else (configs, engines, screens) is identical in both modes.
 */
enum class ConnectionMode {
    /** hev-socks5-tunnel run as root creates the TUN. Default mode. */
    ROOT_TUN2SOCKS,

    /** Android VpnService creates the TUN. Works without root. */
    NORMAL_VPN_SERVICE,
}
