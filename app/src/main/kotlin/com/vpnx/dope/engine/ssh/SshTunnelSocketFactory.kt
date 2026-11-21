package com.vpnx.dope.engine.ssh

import android.os.Build
import android.util.Base64
import android.util.Log
import com.vpnx.dope.engine.xray.XrayLogTailer
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketTimeoutException
import java.security.MessageDigest
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

    companion object {
        private const val TAG = "SshTunnelSocketFactory"
        private const val TCP_CONNECT_TIMEOUT_MS = 12_000
        private const val HANDSHAKE_TIMEOUT_MS = 15_000
        private const val TLS_BANNER_PEEK_TIMEOUT_MS = 1_200
        private const val DIRECT_BANNER_PEEK_TIMEOUT_MS = 1_500
        private const val CHAINED_HTTP_PEEK_TIMEOUT_MS = 500
    }

    override fun createSocket(): Socket = openTunneledSocket()
    override fun createSocket(host: String?, port: Int): Socket = openTunneledSocket()
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = openTunneledSocket()
    override fun createSocket(host: InetAddress?, port: Int): Socket = openTunneledSocket()
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = openTunneledSocket()

    private fun openRawTcpSocket(tcpHost: String, tcpPort: Int): Socket {
        val safePort = tcpPort.coerceIn(1, 65535)
        XrayLogTailer.info("SSH TCP connecting to $tcpHost:$safePort...")
        return Socket().apply {
            runCatching { socketProtectCallback?.invoke(this) }
                .onFailure { t -> Log.w(TAG, "Socket protect callback failed", t) }
            tcpNoDelay = true
            keepAlive = true
            sendBufferSize = 1048576
            receiveBufferSize = 1048576
            connect(InetSocketAddress(tcpHost, safePort), TCP_CONNECT_TIMEOUT_MS)
        }.also {
            XrayLogTailer.info("SSH TCP connected to $tcpHost:$safePort")
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

    private fun computeSha256Fingerprint(bytes: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val b64 = Base64.encodeToString(digest, Base64.NO_WRAP).trimEnd('=')
            "SHA256:$b64"
        } catch (_: Throwable) {
            "SHA256:verified"
        }
    }

    private fun openSslSocket(
        rawSocket: Socket,
        sniHost: String,
        tcpPort: Int,
        forceHttp1: Boolean
    ): SSLSocket {
        val alpnLabel = if (forceHttp1) "http/1.1" else "none"
        XrayLogTailer.info("SSH TLS handshake (SNI=$sniHost, ALPN=$alpnLabel)...")
        val sslContext = createTrustAllSslContext(tlsVersion)
        val sslSocket = sslContext.socketFactory.createSocket(rawSocket, sniHost, tcpPort, true) as SSLSocket
        val prevTimeout = sslSocket.soTimeout
        sslSocket.tcpNoDelay = true
        val params = sslSocket.sslParameters
        params.serverNames = listOf(SNIHostName(sniHost))
        if (forceHttp1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            params.applicationProtocols = arrayOf("http/1.1")
        }
        sslSocket.sslParameters = params
        sslSocket.soTimeout = HANDSHAKE_TIMEOUT_MS
        try {
            sslSocket.startHandshake()
            runCatching {
                val sess = sslSocket.session
                val cert = sess.peerCertificates.firstOrNull() as? X509Certificate
                val certInfo = if (cert != null) " [${computeSha256Fingerprint(cert.encoded)}]" else ""
                XrayLogTailer.info("SSH TLS established (${sess.protocol} / ${sess.cipherSuite})$certInfo")
            }
        } finally {
            runCatching { sslSocket.soTimeout = prevTimeout }
        }
        return sslSocket
    }

    /**
     * Sends HTTP CONNECT to the proxy and reads/validates the response headers through \r\n\r\n
     * directly from the raw socket InputStream so zero subsequent bytes (SSH banner or TLS records)
     * are over-read into a buffer.
     */
    private fun sendAndValidateProxyConnect(socket: Socket) {
        XrayLogTailer.info("SSH proxy CONNECT $targetHost:$targetPort...")
        val prevTimeout = socket.soTimeout
        socket.soTimeout = HANDSHAKE_TIMEOUT_MS
        try {
            val connectReq = buildString {
                append("CONNECT ").append(targetHost).append(":").append(targetPort).append(" HTTP/1.1\r\n")
                append("Host: ").append(targetHost).append(":").append(targetPort).append("\r\n")
                if (proxyAuth && proxyUser.isNotBlank()) {
                    val b64 = Base64.encodeToString("$proxyUser:$proxyPass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    append("Proxy-Authorization: Basic ").append(b64).append("\r\n")
                }
                append("Proxy-Connection: Keep-Alive\r\n\r\n")
            }

            val out = socket.getOutputStream()
            out.write(connectReq.toByteArray(Charsets.UTF_8))
            out.flush()

            val rawIn = socket.getInputStream()
            val headerBytes = ByteArrayOutputStream(256)
            var state = 0
            var totalRead = 0
            var terminated = false

            while (totalRead < 8192) {
                val b = rawIn.read()
                if (b == -1) break
                headerBytes.write(b)
                totalRead++

                state = when (state) {
                    0 -> if (b == '\r'.code) 1 else 0
                    1 -> if (b == '\n'.code) 2 else if (b == '\r'.code) 1 else 0
                    2 -> if (b == '\r'.code) 3 else 0
                    3 -> if (b == '\n'.code) 4 else if (b == '\r'.code) 1 else 0
                    else -> 0
                }
                if (state == 4) {
                    terminated = true
                    break
                }
            }

            val response = headerBytes.toString(Charsets.ISO_8859_1.name())
            val statusLine = response.lineSequence().firstOrNull().orEmpty().trim()
            val isHttp200 = Regex("^HTTP/1\\.[01]\\s+200(\\s|$)", RegexOption.IGNORE_CASE)
                .containsMatchIn(statusLine)

            if (!terminated || !isHttp200) {
                runCatching { socket.close() }
                throw IOException(
                    "HTTP proxy CONNECT rejected (${if (statusLine.isBlank()) "empty/EOF response" else statusLine})"
                )
            }
            XrayLogTailer.info("SSH proxy CONNECT established ($statusLine)")
        } finally {
            runCatching { socket.soTimeout = prevTimeout }
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

    private enum class UpgradeConsumeOutcome {
        HTTP_UPGRADED,
        SSH_BANNER_DETECTED,
        EMPTY_EOF
    }

    /**
     * Reads an HTTP upgrade/payload response until \r\n\r\n, while detecting any SSH banner
     * prefix (`SSH-` at offset 0 or `SSH-2.0-`/`SSH-1.99-` in the stream) and pushing its
     * exact bytes back into [fastIn] instead of discarding them.
     */
    private fun consumeHttpUpgrade(socket: Socket, fastIn: FastPrefixInputStream): UpgradeConsumeOutcome {
        val headerBuf = ByteArray(8192)
        var len = 0
        try {
            while (len < headerBuf.size) {
                val b = fastIn.read()
                if (b == -1) {
                    return if (len == 0) UpgradeConsumeOutcome.EMPTY_EOF else UpgradeConsumeOutcome.HTTP_UPGRADED
                }
                headerBuf[len++] = b.toByte()

                val currentStr = String(headerBuf, 0, len, Charsets.ISO_8859_1)
                val sshIdx = when {
                    len >= 4 && currentStr.startsWith("SSH-") -> 0
                    else -> {
                        val v2 = currentStr.indexOf("SSH-2.0-")
                        if (v2 >= 0) v2 else currentStr.indexOf("SSH-1.99-")
                    }
                }
                if (sshIdx >= 0) {
                    fastIn.unread(headerBuf, sshIdx, len - sshIdx)
                    XrayLogTailer.info("SSH banner detected directly during HTTP upgrade")
                    return UpgradeConsumeOutcome.SSH_BANNER_DETECTED
                }

                if (len >= 4 &&
                    headerBuf[len - 4] == '\r'.code.toByte() &&
                    headerBuf[len - 3] == '\n'.code.toByte() &&
                    headerBuf[len - 2] == '\r'.code.toByte() &&
                    headerBuf[len - 1] == '\n'.code.toByte()
                ) {
                    val firstLine = currentStr.lineSequence().firstOrNull()?.trim().orEmpty()
                    if (firstLine.isNotEmpty()) {
                        XrayLogTailer.info("SSH HTTP response: $firstLine")
                    }
                    // Handle chained proxy 200 OK followed immediately by 101 Switching Protocols
                    if (firstLine.contains("200") && !firstLine.contains("101")) {
                        val savedTimeout = socket.soTimeout
                        socket.soTimeout = CHAINED_HTTP_PEEK_TIMEOUT_MS
                        try {
                            val peekBuf = ByteArray(8)
                            val r = fastIn.read(peekBuf, 0, 8)
                            if (r > 0) {
                                val peekStr = String(peekBuf, 0, r, Charsets.ISO_8859_1)
                                fastIn.unread(peekBuf, 0, r)
                                if (peekStr.startsWith("HTTP", ignoreCase = true)) {
                                    len = 0
                                    continue
                                }
                            }
                        } catch (_: SocketTimeoutException) {
                            // No second HTTP header block; server is ready for SSH
                        } finally {
                            runCatching { socket.soTimeout = savedTimeout }
                        }
                    }
                    return UpgradeConsumeOutcome.HTTP_UPGRADED
                }
            }
            return UpgradeConsumeOutcome.HTTP_UPGRADED
        } catch (ste: SocketTimeoutException) {
            XrayLogTailer.error(
                "SSH HTTP upgrade timed out after ${HANDSHAKE_TIMEOUT_MS}ms (received $len bytes without HTTP terminator or SSH banner)"
            )
            throw SocketTimeoutException("HTTP upgrade timed out after ${HANDSHAKE_TIMEOUT_MS}ms (read $len bytes)")
                .apply { initCause(ste) }
        }
    }

    private fun buildDefaultWebSocketUpgrade(wsHost: String): String {
        return "GET / HTTP/1.1\r\n" +
            "Host: $wsHost\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "User-Agent: Mozilla/5.0\r\n\r\n"
    }

    private fun openBaseSocket(
        connectHost: String,
        connectPort: Int,
        sendProxyConnect: Boolean
    ): Socket {
        val raw = openRawTcpSocket(connectHost, connectPort)
        if (sendProxyConnect) {
            sendAndValidateProxyConnect(raw)
        }
        return raw
    }

    private fun openTunneledSocket(): Socket {
        val hasProxy = (mode == "PROXY" || mode == "PROXY_TLS") && proxyHost.isNotBlank()
        val isTls = (mode == "TLS" || mode == "PROXY_TLS")
        val hasExplicitPayload = payloadEnabled

        val connectHost = if (hasProxy) proxyHost else targetHost
        val connectPort = if (hasProxy) proxyPort else targetPort
        val effectiveSni = sni.ifBlank { targetHost }

        // Establish HTTP CONNECT tunnel through proxy:
        // - PROXY_TLS always tunnels via CONNECT first before TLS handshake with target (B4)
        // - PROXY without payload tunnels via CONNECT and consumes the 200 response (B3)
        val shouldSendConnect = hasProxy && (isTls || (!payloadEnabled && payloadTemplate.isBlank()))

        // 1. Initial TCP (+ proxy CONNECT if needed)
        var activeSocket: Socket = openBaseSocket(connectHost, connectPort, shouldSendConnect)
        var tlsEstablished = false

        // 2. TLS wrap if requested:
        //    - Direct TLS (no explicit payload): NO ALPN (forceHttp1 = false) so SSH-over-TLS servers do not route to HTTP.
        //    - Explicit payload over TLS: forceHttp1 = true.
        //    - On TLS handshake failure: log distinctly and fall back to raw TCP (+ proxy CONNECT if PROXY_TLS).
        if (isTls) {
            val useHttp1Alpn = hasExplicitPayload
            try {
                activeSocket = openSslSocket(activeSocket, effectiveSni, targetPort, forceHttp1 = useHttp1Alpn)
                tlsEstablished = true
            } catch (tlsErr: Throwable) {
                Log.w(TAG, "TLS handshake failed; falling back to raw TCP", tlsErr)
                XrayLogTailer.warn(
                    "SSH TLS handshake failed (${tlsErr::class.simpleName}): ${tlsErr.message ?: "handshake error"}; falling back to raw TCP..."
                )
                runCatching { activeSocket.close() }
                activeSocket = openBaseSocket(connectHost, connectPort, shouldSendConnect)
                tlsEstablished = false
            }
        }

        var fastIn = FastPrefixInputStream(BufferedInputStream(activeSocket.getInputStream(), 65536))

        // 3. Payload or banner peek (+ WS fallback on TLS path if no SSH banner arrives)
        val effectivePayload = if (payloadEnabled && payloadTemplate.isNotBlank()) {
            PayloadGenerator.generatePayload(payloadTemplate, targetHost, targetPort, effectiveSni)
        } else if (isTls && payloadEnabled) {
            buildDefaultWebSocketUpgrade(effectiveSni)
        } else {
            ""
        }

        if (effectivePayload.isNotBlank()) {
            var finalPayload = effectivePayload
            if (hasProxy && !shouldSendConnect && proxyAuth && proxyUser.isNotBlank()) {
                val b64 = Base64.encodeToString("$proxyUser:$proxyPass".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                finalPayload = finalPayload.replaceFirst("\r\n\r\n", "\r\nProxy-Authorization: Basic $b64\r\n\r\n")
            }
            val prevTimeout = activeSocket.soTimeout
            activeSocket.soTimeout = HANDSHAKE_TIMEOUT_MS
            try {
                XrayLogTailer.info("SSH sending HTTP payload...")
                sendPayloadChunks(activeSocket.getOutputStream(), finalPayload)
                consumeHttpUpgrade(activeSocket, fastIn)
            } finally {
                runCatching { activeSocket.soTimeout = prevTimeout }
            }
        } else {
            // Peek for SSH banner without consuming it
            val peek = ByteArray(256)
            val prevTimeout = activeSocket.soTimeout
            activeSocket.soTimeout = if (isTls) TLS_BANNER_PEEK_TIMEOUT_MS else DIRECT_BANNER_PEEK_TIMEOUT_MS
            var sawSshBanner = false
            try {
                val n = fastIn.read(peek)
                if (n > 0) {
                    val str = String(peek, 0, n, Charsets.ISO_8859_1)
                    val sshPos = str.indexOf("SSH-")
                    if (sshPos >= 0) {
                        fastIn.unread(peek, sshPos, n - sshPos)
                        sawSshBanner = true
                        XrayLogTailer.info("SSH banner received")
                    } else {
                        fastIn.unread(peek, 0, n)
                    }
                }
            } catch (_: SocketTimeoutException) {
                sawSshBanner = false
            } catch (_: Throwable) {
                sawSshBanner = false
            } finally {
                runCatching { activeSocket.soTimeout = prevTimeout }
            }

            // On TLS/PROXY_TLS path: if no SSH banner arrived after handshake, send WebSocket upgrade
            if (isTls && !sawSshBanner) {
                XrayLogTailer.info("No direct SSH banner received; sending WebSocket upgrade...")
                val wsReq = buildDefaultWebSocketUpgrade(effectiveSni)
                val savedTimeout = activeSocket.soTimeout
                activeSocket.soTimeout = HANDSHAKE_TIMEOUT_MS
                val outcome = try {
                    sendPayloadChunks(activeSocket.getOutputStream(), wsReq)
                    consumeHttpUpgrade(activeSocket, fastIn)
                } finally {
                    runCatching { activeSocket.soTimeout = savedTimeout }
                }

                // If the TLS server closed cleanly without replying (e.g. terminator requiring http/1.1 ALPN for WS),
                // reconnect once with forceHttp1 = true and retry the WebSocket upgrade.
                if (outcome == UpgradeConsumeOutcome.EMPTY_EOF && tlsEstablished) {
                    XrayLogTailer.warn("TLS WebSocket upgrade returned EOF without ALPN; retrying with ALPN http/1.1...")
                    runCatching { activeSocket.close() }
                    val retryBase = openBaseSocket(connectHost, connectPort, shouldSendConnect)
                    activeSocket = openSslSocket(retryBase, effectiveSni, targetPort, forceHttp1 = true)
                    fastIn = FastPrefixInputStream(BufferedInputStream(activeSocket.getInputStream(), 65536))
                    val retryPrevTimeout = activeSocket.soTimeout
                    activeSocket.soTimeout = HANDSHAKE_TIMEOUT_MS
                    try {
                        sendPayloadChunks(activeSocket.getOutputStream(), wsReq)
                        consumeHttpUpgrade(activeSocket, fastIn)
                    } finally {
                        runCatching { activeSocket.soTimeout = retryPrevTimeout }
                    }
                }
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
        override fun shutdownInput() = delegate.shutdownInput()
        override fun shutdownOutput() = delegate.shutdownOutput()
        override fun setSoTimeout(timeout: Int) { delegate.soTimeout = timeout }
        override fun getSoTimeout(): Int = delegate.soTimeout
        override fun setTcpNoDelay(on: Boolean) { runCatching { delegate.tcpNoDelay = on } }
        override fun setKeepAlive(on: Boolean) { runCatching { delegate.keepAlive = on } }
        override fun setSendBufferSize(size: Int) { runCatching { delegate.sendBufferSize = size } }
        override fun setReceiveBufferSize(size: Int) { runCatching { delegate.receiveBufferSize = size } }
        override fun getRemoteSocketAddress(): SocketAddress? = delegate.remoteSocketAddress
        override fun getInetAddress(): InetAddress? = delegate.inetAddress
        override fun getLocalAddress(): InetAddress = delegate.localAddress
        override fun getPort(): Int = delegate.port
        override fun getLocalPort(): Int = delegate.localPort
    }
}