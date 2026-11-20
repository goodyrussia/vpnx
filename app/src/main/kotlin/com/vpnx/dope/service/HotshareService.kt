package com.vpnx.dope.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vpnx.dope.R
import com.vpnx.dope.VpnApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class HotshareService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat(createNotification("Hotspot proxy active on port 10809"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(createNotification("Hotspot proxy active on port 10809"))
        if (intent?.action == ACTION_STOP) {
            stopServer()
            stopSelf()
            return START_NOT_STICKY
        }
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        if (isRunning.compareAndSet(false, true)) {
            serviceScope.launch {
                try {
                    val server = ServerSocket()
                    server.reuseAddress = true
                    server.bind(InetSocketAddress("0.0.0.0", 10809), 128)
                    serverSocket = server
                    while (isRunning.get()) {
                        val client = server.accept()
                        serviceScope.launch { forwardClient(client) }
                    }
                } catch (t: Throwable) {
                    Log.d("HotshareService", "Hotshare server loop notice: ${t.message}")
                }
            }
        }
    }

    private fun forwardClient(client: Socket) {
        try {
            val upstream = Socket()
            upstream.connect(InetSocketAddress("127.0.0.1", 10808), 5000)

            val cin = client.getInputStream()
            val cout = client.getOutputStream()
            val uin = upstream.getInputStream()
            val uout = upstream.getOutputStream()

            val t1 = Thread {
                val buf = ByteArray(16384)
                try {
                    while (true) {
                        val n = cin.read(buf)
                        if (n <= 0) break
                        uout.write(buf, 0, n)
                        uout.flush()
                    }
                } catch (_: Throwable) {}
            }
            val t2 = Thread {
                val buf = ByteArray(16384)
                try {
                    while (true) {
                        val n = uin.read(buf)
                        if (n <= 0) break
                        cout.write(buf, 0, n)
                        cout.flush()
                    }
                } catch (_: Throwable) {}
            }
            t1.isDaemon = true
            t2.isDaemon = true
            t1.start()
            t2.start()
            t1.join()
            t2.join()
        } catch (_: Throwable) {
        } finally {
            runCatching { client.close() }
        }
    }

    private fun stopServer() {
        isRunning.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e("HotshareService", "Failed to start foreground", t)
        }
    }

    private fun createNotification(statusText: String): Notification {
        return NotificationCompat.Builder(this, VpnApplication.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("vpnx Hotshare")
            .setContentText(statusText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopServer()
    }

    companion object {
        const val NOTIFICATION_ID = 2003
        const val ACTION_START = "com.vpnx.dope.ACTION_HOTSHARE_START"
        const val ACTION_STOP = "com.vpnx.dope.ACTION_HOTSHARE_STOP"
    }
}
