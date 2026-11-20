package com.vpnx.dope.engine.xray

import android.content.Context
import android.util.Log
import com.vpnx.dope.data.model.XrayProfile
import go.Seq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

object XrayEngine {
    private const val TAG = "XrayEngine"
    private val lock = Any()
    @Volatile
    private var controller: CoreController? = null
    private val isRunning = AtomicBoolean(false)

    private fun initCoreEnv(context: Context) {
        runCatching {
            Seq.setContext(context.applicationContext)
            val assetsDir = File(context.filesDir, "xray-assets").apply { mkdirs() }
            Libv2ray.initCoreEnv(assetsDir.absolutePath, "vpnx-core-key-16b")
        }
    }

    suspend fun start(
        context: Context,
        profile: XrayProfile,
        socksPort: Int = 10808
    ): Boolean = withContext(Dispatchers.IO) {
        if (isRunning.get()) stop()

        initCoreEnv(context)
        val configJson = XrayConfigBuilder.buildRoutingFreeConfig(profile, socksPort)
        XrayLogTailer.info("Starting Xray-core for ${profile.protocol} (${profile.server}:${profile.port})...")

        val callbackHandler = object : CoreCallbackHandler {
            override fun startup(): Long {
                XrayLogTailer.info("Xray-core loop initialized successfully")
                return 0
            }

            override fun shutdown(): Long {
                XrayLogTailer.info("Xray-core loop stopped")
                return 0
            }

            override fun onEmitStatus(code: Long, msg: String?): Long {
                val text = msg.orEmpty().ifBlank { "Status code: $code" }
                XrayLogTailer.debug(text)
                return 0
            }
        }

        try {
            val ctrl = Libv2ray.newCoreController(callbackHandler)
            // SOCKS5 inbound only; tunFd is -1 because hev handles TUN capture
            ctrl.startLoop(configJson, -1)
            synchronized(lock) {
                controller = ctrl
            }
            isRunning.set(true)
            XrayLogTailer.info("Xray-core running on SOCKS5 127.0.0.1:$socksPort")
            true
        } catch (t: Throwable) {
            XrayLogTailer.error("Failed to start Xray: ${t.message}")
            Log.e(TAG, "Failed to start Xray loop", t)
            stop()
            false
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        if (isRunning.compareAndSet(true, false)) {
            synchronized(lock) {
                runCatching { controller?.stopLoop() }
                controller = null
            }
            XrayLogTailer.info("Xray engine stopped")
        }
    }

    fun isRunning(): Boolean = isRunning.get()
}
