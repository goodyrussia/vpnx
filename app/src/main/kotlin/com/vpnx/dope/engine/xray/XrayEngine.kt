package com.vpnx.dope.engine.xray

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
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
import java.util.concurrent.atomic.AtomicReference

object XrayEngine {
    private const val TAG = "XrayEngine"
    private const val XRAY_CORE_BASE_KEY_LENGTH = 32

    private val lock = Any()
    private val coreEnvLock = Any()
    private val initializedAssetsDir = AtomicReference<String?>(null)

    @Volatile
    private var controller: CoreController? = null
    private val isRunning = AtomicBoolean(false)

    fun initCoreEnv(context: Context) {
        val appContext = context.applicationContext
        val assetsDir = File(appContext.filesDir, "xray-assets").apply { mkdirs() }
        val assetsPath = assetsDir.absolutePath

        if (initializedAssetsDir.get() == assetsPath) {
            return
        }

        synchronized(coreEnvLock) {
            if (initializedAssetsDir.get() == assetsPath) {
                return
            }
            try {
                Seq.setContext(appContext)
                Libv2ray.initCoreEnv(assetsPath, xrayCoreBaseKey(appContext))
                initializedAssetsDir.set(assetsPath)
            } catch (t: Throwable) {
                XrayLogTailer.error("Failed to initialize Xray core environment: ${t.message}")
                Log.e(TAG, "Failed to initialize Xray core environment", t)
            }
        }
    }

    @SuppressLint("HardwareIds")
    private fun xrayCoreBaseKey(context: Context): String {
        val deviceId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
            .orEmpty()
            .ifBlank { context.packageName }

        return deviceId
            .toByteArray(Charsets.UTF_8)
            .copyOf(XRAY_CORE_BASE_KEY_LENGTH)
            .encodeUrlSafeBase64NoPadding()
    }

    private fun ByteArray.encodeUrlSafeBase64NoPadding(): String {
        return Base64.encodeToString(
            this,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
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
        val wasRunning = isRunning.getAndSet(false)
        synchronized(lock) {
            val ctrl = controller
            controller = null
            if (ctrl != null) {
                runCatching { ctrl.stopLoop() }.onFailure { t ->
                    XrayLogTailer.error(
                        "Xray stopLoop failed (${t::class.simpleName}): ${t.message ?: "no detail"} (listener may still be held)"
                    )
                    Log.e(TAG, "Failed to stop Xray loop", t)
                }
            }
        }
        if (wasRunning) {
            XrayLogTailer.info("Xray engine stopped")
        }
    }

    fun isRunning(): Boolean = isRunning.get()
}