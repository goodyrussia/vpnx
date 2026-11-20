package com.vpnx.dope

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.vpnx.dope.data.AppDatabase
import com.vpnx.dope.engine.root.RootShellManager
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

class VpnApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 1. Explicitly register BouncyCastle at Priority 1 for X25519/Ed25519 support on Android 7-16
        try {
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        } catch (t: Throwable) {
            Log.w("VpnApplication", "Failed to register BouncyCastle provider", t)
        }

        // 2. Initialize libsu 6.0.0 RootShellManager once from Application.onCreate() (PLAN.md 6.1)
        RootShellManager.init()

        // 3. Initialize Room database instance
        AppDatabase.getDatabase(this)

        // 4. Create Notification Channels
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "vpnx_service_channel"
        lateinit var instance: VpnApplication
            private set
    }
}
