package com.vpnx.dope.engine.ssh

import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.SocketFactory
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SshTunnelSocketFactory(
    private val mode: String, // "DIRECT", "PROXY", "TLS", "PROXY_TLS"
    private val targetHost: String,
    private val targetPort: Int,
    private val payloadEnabled: Boolean,
    private val payloadTemplate: String,
    private val sni: String,
    private val proxyHost: String,
    private val proxyPort: Int,
    private val proxyAuth: Boolean,
    private val proxyUser: String,
    private val proxyPass: String,
    private val tlsVersion: String,
    private val socketProtectCallback: ((Socket) -> Unit)? = null
) : SocketFactory() {

    override fun createSocket(): Socket = openTunneledSocket()
    override fun createSocket(host: String?, port: Int): Socket = openTunneledSocket()
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = openTunneledSocket()
    override fun createSocket(host: InetAddress?, port: Int): Socket = openTunneledSocket()
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = openTunneledSocket()

    private fun openRawTcpSocket(tcpHost: String, tcpPort: Int): Socket {
        return Socket().apply {
            socketProtectCallback?.invoke(this)
            tcpNoDelay = true
            keepAlive = true
            sendBufferSize = 1048576
            receiveBufferSize = 1048576
            connect(InetSocketAddress(tcpHost, tcpPort.coerceIn(1, 65535)), 12000)
        }
    }

    private fun createTrustAllSslContext(tlsVer: String): SSLContext {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        val proto = when (tlsVer.uppercase().replace("V", "")) {
            "TLS1.3" -> "TLSv1.3"
            "TLS1.2" -> "TLSv1.2"
            else -> "TLS"
        }
        return try {
            SSLContext.getInstance(proto).apply { init(null, trustAll, SecureRandom()) }
        } catch (_: Throwable) {
            SSLContext.getInstance("TLS").apply { init(null, trustAll, SecureRandom()) }
        }
    }

    private fun openSslSocket(rawSocket: Socket, sniHost: String, tcpPort: Int): SSLSocket {
        val sslContext = createTrustAllSslContext(tlsVersion)
        return (sslContext.socketFactory.createSocket(rawSocket, sniHost, tcpPort, true) as SSLSocket).apply {
            tcpNoDelay = true
            val params = sslParameters
            params.serverNames = listOf(SNIHostName(sniHost))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                params.applicationProtocols = arrayOf("http/1.1")
            }
            sslParameters = params
            startHandshake()
        }
    }

    private fun sendPayloadChunks(out: OutputStream, formattedPayload: String) {
        val chunks = formattedPayload.split(Regex("\\[(instant_|delay_)?split\\]", RegexOption.IGNORE_CASE))
        for ((idx, chunk) in chunks.withIndex()) {
            if (chunk.isNotEmpty()) {
                out.write(chunk.toByteArray(Charsets.UTF_8))
                out.flush()
                if (idx < chunks.size - 1) Thread.sleep(15)
            }
        }
    }

    private fun consumeHttpUpgrade(fastIn: FastPrefixInputStream) {
        val buf = ByteArray(1024)
        var totalRead = 0
        while (totalRead < 4096) {
            val b = fastIn.read()
            if (b == -1) break
            buf[totalRead % 1024] = b.toByte()
            totalRead++
            if (totalRead >= 4) {
                val p3 = buf[(totalRead - 1) % 1024]
                val p2 = buf[(totalRead - 2) % 1024]
                val p1 = buf[(totalRead - 3) % 1024]
                val p0 = buf[(totalRead - 4) % 1024]
                if (p0 == '\r'.code.toByte() && p1 == '\n'.code.toByte() &&
                    p2 == '\r'.code.toByte() && p3 == '\n'.code.toByte()
                ) {
                    break
                }
            }
        }
    }

    private fun openTunneledSocket(): Socket {
        val hasProxy = (mode == "PROXY" || mode == "PROXY_TLS") && proxyHost.isNotBlank()
        val isTls = (mode == "TLS" || mode == "PROXY_TLS")

        val connectHost = if (hasProxy) proxyHost else targetHost
        val connectPort = if (hasProxy) proxyPort else targetPort
        val effectiveSni = sni.ifBlank { targetHost }

        // 1. Establish initial TCP socket
        var activeSocket: Socket = openRawTcpSocket(connectHost, connectPort)

        // 2. If Proxy mode without TLS, send proxy CONNECT if payload is empty
        if (hasProxy && !isTls && !payloadEnabled && payloadTemplate.isBlank()) {
            val connectReq = "CONNECT $targetHost:$targetPort HTTP/1.1\r\nHost: $targetHost:$targetPort\r\n\r\n"
            activeSocket.getOutputStream().write(connectReq.toByteArray(Charsets.UTF_8))
            activeSocket.getOutputStream().flush()
        }

        // 3. If TLS mode, wrap socket
        if (isTls) {
            activeSocket = openSslSocket(activeSocket, effectiveSni, connectPort)
        }

        val fastIn = FastPrefixInputStream(BufferedInputStream(activeSocket.getInputStream(), 65536))

        // 4. Send payload if enabled
        val effectivePayload = if (payloadEnabled && payloadTemplate.isNotBlank()) {
            PayloadGenerator.generatePayload(payloadTemplate, targetHost, targetPort, effectiveSni)
        } else if (isTls && payloadEnabled) {
            "GET / HTTP/1.1\r\nHost: $effectiveSni\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nUser-Agent: Mozilla/5.0\r\n\r\n"
        } else {
            ""
        }

        if (effectivePayload.isNotBlank()) {
            var finalPayload = effectivePayload
            if (hasProxy && proxyAuth && proxyUser.isNotBlank()) {
                val b64 = Base64.encodeToString("$proxyUser:$proxyPass".toByteArray(), Base64.NO_WRAP)
                finalPayload = finalPayload.replaceFirst("\r\n\r\n", "\r\nProxy-Authorization: Basic $b64\r\n\r\n")
            }
            sendPayloadChunks(activeSocket.getOutputStream(), finalPayload)
            consumeHttpUpgrade(fastIn)
        } else {
            // Peek for SSH banner without consuming it
            val peek = ByteArray(256)
            val prevTimeout = activeSocket.soTimeout
            activeSocket.soTimeout = 1500
            try {
                val n = fastIn.read(peek)
                if (n > 0) {
                    val str = String(peek, 0, n, Charsets.ISO_8859_1)
                    val sshPos = str.indexOf("SSH-")
                    if (sshPos >= 0) {
                        fastIn.unread(peek, sshPos, n - sshPos)
                    } else {
                        fastIn.unread(peek, 0, n)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                activeSocket.soTimeout = prevTimeout
            }
        }

        return WrappedTunnelSocket(activeSocket, fastIn)
    }

    private class WrappedTunnelSocket(
        private val delegate: Socket,
        private val customIn: InputStream
    ) : Socket() {
        override fun connect(endpoint: SocketAddress?) {}
        override fun connect(endpoint: SocketAddress?, timeout: Int) {}
        override fun bind(bindpoint: SocketAddress?) {}
        override fun getInputStream(): InputStream = customIn
        override fun getOutputStream(): OutputStream = delegate.getOutputStream()
        override fun isConnected(): Boolean = delegate.isConnected
        override fun isClosed(): Boolean = delegate.isClosed
        override fun isBound(): Boolean = true
        override fun close() = delegate.close()
        override fun setSoTimeout(timeout: Int) { delegate.soTimeout = timeout }
        override fun getSoTimeout(): Int = delegate.soTimeout
        override fun setTcpNoDelay(on: Boolean) { delegate.tcpNoDelay = on }
        override fun setKeepAlive(on: Boolean) { delegate.keepAlive = on }
    }
}
