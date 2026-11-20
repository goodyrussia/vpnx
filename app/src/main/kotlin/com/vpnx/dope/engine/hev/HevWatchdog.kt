package com.vpnx.dope.engine.hev

import android.util.Log
import com.vpnx.dope.engine.root.RootShellManager
import com.vpnx.dope.engine.root.RoutingTableManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

class HevWatchdog(
    private val onTunnelDied: () -> Unit
) {
    private var watchdogJob: Job? = null

    fun start(scope: CoroutineScope, socksPort: Int = 10808) {
        stop()
        watchdogJob = scope.launch(Dispatchers.IO) {
            var consecutiveFailures = 0
            while (isActive) {
                delay(3000)
                val isAlive = probeTunnel(socksPort)
                if (!isAlive) {
                    consecutiveFailures++
                    Log.w("HevWatchdog", "Watchdog probe failed ($consecutiveFailures/3)")
                    if (consecutiveFailures >= 3) {
                        Log.e("HevWatchdog", "Hev tunnel died; alerting controller")
                        onTunnelDied()
                        break
                    }
                } else {
                    consecutiveFailures = 0
                }
            }
        }
    }

    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    private suspend fun probeTunnel(port: Int): Boolean {
        // 1. Verify vpnx0 interface exists
        val res = RootShellManager.exec("ip link show ${RoutingTableManager.TUN_DEV}")
        if (!res.isSuccess) return false

        // 2. Verify local SOCKS5 loopback listener is accepting connections
        return runCatching {
            Socket().use { sock ->
                sock.connect(InetSocketAddress("127.0.0.1", port), 1000)
                true
            }
        }.getOrDefault(false)
    }
}
