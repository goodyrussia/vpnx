package engine.vpn.hevtun

import androidx.annotation.Keep

@Keep
object HevTunNative {
    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
        } catch (t: Throwable) {
            android.util.Log.e("HevTunNative", "Failed to load libhev-socks5-tunnel.so", t)
        }
    }

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyStartService(configPath: String, fd: Int): Boolean

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyStopService(): Boolean

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyIsRunning(): Boolean

    @JvmStatic
    @Keep
    @Suppress("FunctionName")
    private external fun TProxyGetStats(): LongArray

    fun startService(configPath: String, fd: Int): Boolean {
        return runCatching { TProxyStartService(configPath, fd) }.getOrDefault(false)
    }

    fun stopService(): Boolean {
        return runCatching { TProxyStopService() }.getOrDefault(false)
    }

    fun isRunning(): Boolean {
        return runCatching { TProxyIsRunning() }.getOrDefault(false)
    }

    fun getStats(): LongArray {
        return runCatching { TProxyGetStats() }.getOrDefault(LongArray(2))
    }
}
