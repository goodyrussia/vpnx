package com.vpnx.dope.service

import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thread-safe permission requester bridging ActivityResultLauncher with coroutines.
 * Matches AsteriskNG AndroidVpnPermissionRequester pattern.
 */
class VpnPermissionRequester {
    private val mutex = Mutex()
    private var launcher: ((Intent) -> Unit)? = null
    private var pendingResult: CompletableDeferred<Boolean>? = null

    fun registerLauncher(launcher: ((Intent) -> Unit)?) {
        this.launcher = launcher
    }

    fun complete(granted: Boolean) {
        pendingResult?.complete(granted)
        pendingResult = null
    }

    suspend fun request(intent: Intent): Boolean {
        return mutex.withLock {
            val currentLauncher = launcher ?: return false
            val result = CompletableDeferred<Boolean>()
            pendingResult = result
            try {
                withContext(Dispatchers.Main.immediate) {
                    currentLauncher(intent)
                }
                result.await()
            } finally {
                if (pendingResult === result) {
                    pendingResult = null
                }
            }
        }
    }
}
