package com.vpnx.dope.vpn

import android.net.VpnService

/**
 * Normal (no-root) mode service.
 *
 * Phase 0: declared and compiling only. Phase 1 wires the real path here:
 * engines start and expose local SOCKS5, this service establishes the TUN,
 * and traffic is forwarded TUN -> SOCKS5 (Albert's proven flow).
 */
class NormalVpnService : VpnService() {
    override fun onRevoke() {
        stopSelf()
        super.onRevoke()
    }
}
