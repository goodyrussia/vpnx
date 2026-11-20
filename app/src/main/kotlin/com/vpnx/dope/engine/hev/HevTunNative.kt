package com.vpnx.dope.engine.hev

import engine.vpn.hevtun.HevTunNative as NativeBridge

object HevTunNative {
    fun startService(configPath: String, fd: Int): Boolean = NativeBridge.startService(configPath, fd)
    fun stopService(): Boolean = NativeBridge.stopService()
    fun isRunning(): Boolean = NativeBridge.isRunning()
    fun getStats(): LongArray = NativeBridge.getStats()
}
