package com.vpnx.dope.service

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe single-flight permission requester bridging ActivityResultLauncher with coroutines.
 * Avoids holding a Mutex across the user-prompt await and guards against Activity-destroyed launcher races.
 */
class VpnPermissionRequester {

    private val launcherRef = AtomicReference<((Intent) -> Unit)?>(null)
    private val inFlightRef = AtomicReference<CompletableDeferred<Boolean>?>(null)

    fun registerLauncher(launcher: ((Intent) -> Unit)?) {
        launcherRef.set(launcher)
    }

    fun complete(granted: Boolean) {
        inFlightRef.getAndSet(null)?.complete(granted)
    }

    suspend fun request(intent: Intent): Boolean {
        val newDeferred = CompletableDeferred<Boolean>()
        if (!inFlightRef.compareAndSet(null, newDeferred)) {
            // Single-flight: piggyback on the existing in-flight prompt without blocking on a lock
            return inFlightRef.get()?.await() ?: false
        }

        val currentLauncher = launcherRef.get()
        if (currentLauncher == null) {
            inFlightRef.compareAndSet(newDeferred, null)
            newDeferred.complete(false)
            return false
        }

        try {
            withContext(Dispatchers.Main.immediate) {
                currentLauncher(intent)
            }
            return newDeferred.await()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch OS VPN permission prompt", t)
            inFlightRef.compareAndSet(newDeferred, null)
            newDeferred.complete(false)
            return false
        } finally {
            inFlightRef.compareAndSet(newDeferred, null)
        }
    }

    companion object {
        private const val TAG = "VpnPermissionRequester"
    }
}