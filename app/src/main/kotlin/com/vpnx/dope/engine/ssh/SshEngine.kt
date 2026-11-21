package com.vpnx.dope.engine.ssh

import android.net.VpnService
import android.util.Base64
import android.util.Log
import com.vpnx.dope.data.model.SshProfile
import com.vpnx.dope.engine.common.LocalSocksServer
import com.vpnx.dope.engine.common.TrafficStatsTracker
import com.vpnx.dope.engine.xray.XrayLogTailer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.connection.channel.direct.DirectConnection
import net.schmizz.sshj.connection.channel.direct.DirectTCPIPChannel
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.ChallengeResponseProvider
import net.schmizz.sshj.userauth.password.Resource
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.BindException
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object SshEngine {
    private const val TAG = "SshEngine"
    private const val SSH_CONNECT_TIMEOUT_MS = 15_000
    private const val SSH_OPERATION_TIMEOUT_MS = 30_000
    private const val SOCKS_BIND_RETRY_DELAY_MS = 1_000L
    private const val DNS_TCP_TIMEOUT_MS = 2_500L
    private const val DNS_CACHE_TTL_MS = 600_000L
    private const val MAX_DNS_CACHE_ENTRIES = 512

    private data class DnsCacheRecord(val response: ByteArray, val expireAt: Long)

    @Volatile
    var vpnService: VpnService? = null

    private val startMutex = Mutex()
    private val activeClientRef = AtomicReference<SSHClient?>(null)
    private val localSocksRef = AtomicReference<LocalSocksServer?>(null)
    private val isRunning = AtomicBoolean(false)
    private val dnsCache = ConcurrentHashMap<String, DnsCacheRecord>()
    private val dnsExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "SshDnsResolver").apply { isDaemon = true }
    }

    private fun isX25519Supported(): Boolean {
        return try {
            SecurityUtils.getKeyPairGenerator("X25519")
            true
        } catch (_: Throwable) {
            try {
                KeyPairGenerator.getInstance("X25519")
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    private fun buildAndroidSafeSshConfig(): DefaultConfig {
        val config = DefaultConfig()
        val hasX25519 = isX25519Supported()

        // Filter out strict KEX and remove curve25519/x25519 if the ROM lacks X25519 KeyPairGenerator
        config.keyExchangeFactories = config.keyExchangeFactories.filterNot { factory ->
            val n = factory.name.lowercase()
            n.contains("strict") || (!hasX25519 && (n.contains("25519") || n.contains("curve25519")))
        }

        // Prioritize Ed25519 and RSA host keys before ECDSA
        config.keyAlgorithms = config.keyAlgorithms.sortedBy { factory ->
            val n = factory.name.lowercase()
            when {
                n.contains("ed25519") -> 0
                n.contains("rsa") -> 1
                else -> 2
            }
        }
        return config
    }

    private fun computeSha256Fingerprint(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return "SHA256:unknown"
        return try {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            val b64 = Base64.encodeToString(digest, Base64.NO_WRAP).trimEnd('=')
            "SHA256:$b64"
        } catch (_: Throwable) {
            "SHA256:verified"
        }
    }

    private fun authenticateSshClient(client: SSHClient, user: String, pass: String): Pair<Boolean, Throwable?> {
        var passwordError: Throwable? = null
        try {
            client.authPassword(user, pass)
            if (client.isAuthenticated) {
                XrayLogTailer.info("SSH auth succeeded (password) for '$user'")
                return true to null
            }
        } catch (t: Throwable) {
            passwordError = t
            Log.w(TAG, "SSH password auth failed, falling back to keyboard-interactive", t)
            XrayLogTailer.warn(
                "SSH password auth failed (${t::class.simpleName}): ${t.message ?: "rejected"}; trying keyboard-interactive..."
            )
        }

        var kbiError: Throwable? = null
        try {
            val kbiProvider = object : ChallengeResponseProvider {
                override fun getSubmethods(): MutableList<String> = Collections.emptyList()
                override fun init(resource: Resource<*>?, name: String?, instruction: String?) {}
                override fun getResponse(prompt: String?, echo: Boolean): CharArray = pass.toCharArray()
                override fun shouldRetry(): Boolean = false
            }
            client.auth(user, AuthKeyboardInteractive(kbiProvider))
            if (client.isAuthenticated) {
                XrayLogTailer.info("SSH auth succeeded (keyboard-interactive) for '$user'")
                return true to null
            }
        } catch (t: Throwable) {
            kbiError = t
            Log.w(TAG, "SSH keyboard-interactive auth failed", t)
            XrayLogTailer.warn(
                "SSH keyboard-interactive auth failed (${t::class.simpleName}): ${t.message ?: "rejected"}"
            )
        }

        return client.isAuthenticated to (kbiError ?: passwordError)
    }

    suspend fun start(profile: SshProfile, socksPort: Int = 10808): Boolean = withContext(Dispatchers.IO) {
        startMutex.withLock {
            stopInternal()
            var stage = "init"

            try {
                stage = "config"
                val config = buildAndroidSafeSshConfig()
                val client = SSHClient(config).apply {
                    addHostKeyVerifier(object : HostKeyVerifier {
                        override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean {
                            if (key != null) {
                                val fp = computeSha256Fingerprint(key.encoded)
                                val algo = key.algorithm ?: "SSH-Key"
                                XrayLogTailer.info("SSH host key accepted ($algo): $fp")
                            }
                            return true
                        }

                        override fun findExistingAlgorithms(hostname: String?, port: Int): List<String> = emptyList()
                    })
                    connectTimeout = SSH_CONNECT_TIMEOUT_MS
                    timeout = SSH_OPERATION_TIMEOUT_MS
                    socketFactory = SshTunnelSocketFactory(
                        mode = profile.mode,
                        targetHost = profile.server,
                        targetPort = profile.port,
                        payloadEnabled = profile.payloadEnabled,
                        payloadTemplate = profile.payload,
                        sni = profile.sni,
                        proxyHost = profile.proxyHost,
                        proxyPort = profile.proxyPort,
                        proxyAuth = profile.proxyAuth,
                        proxyUser = profile.proxyUser,
                        proxyPass = profile.proxyPass,
                        tlsVersion = profile.tlsVersion,
                        socketProtectCallback = { sock -> runCatching { vpnService?.protect(sock) } }
                    )
                }

                activeClientRef.set(client)

                stage = "connect"
                val payloadLabel = if (profile.payloadEnabled) ", payload" else ""
                XrayLogTailer.info(
                    "SSH connecting to ${profile.server}:${profile.port} (mode=${profile.mode}$payloadLabel)..."
                )
                Log.i(TAG, "Connecting SSH to ${profile.server}:${profile.port} (${profile.mode})...")
                client.connect(profile.server, profile.port)
                currentCoroutineContext().ensureActive()

                stage = "auth"
                XrayLogTailer.info("SSH transport established; authenticating user '${profile.user}'...")
                Log.i(TAG, "Authenticating SSH user '${profile.user}'...")
                val (authenticated, authError) = authenticateSshClient(client, profile.user, profile.pass)
                currentCoroutineContext().ensureActive()

                if (!authenticated) {
                    val detail = if (authError != null) {
                        " (${authError::class.simpleName}: ${authError.message})"
                    } else {
                        " (server accepted the connection but rejected the credentials)"
                    }
                    XrayLogTailer.error("SSH authentication failed for user '${profile.user}'$detail")
                    Log.e(TAG, "SSH authentication failed for user '${profile.user}'$detail", authError)
                    stopInternal()
                    return@withLock false
                }

                stage = "socks-bind"
                XrayLogTailer.info("SSH binding local SOCKS5 on 127.0.0.1:$socksPort...")
                val socksServer = startLocalSocksWithRetry(client, socksPort)
                if (socksServer == null) {
                    stopInternal()
                    return@withLock false
                }

                localSocksRef.set(socksServer)
                isRunning.set(true)
                XrayLogTailer.info("SSH tunnel active; SOCKS5 listening on 127.0.0.1:$socksPort")
                Log.i(TAG, "SSH tunnel active; SOCKS5 listening on 127.0.0.1:$socksPort")
                true
            } catch (ce: CancellationException) {
                withContext(NonCancellable) {
                    stopInternal()
                }
                throw ce
            } catch (t: Throwable) {
                XrayLogTailer.error(
                    "SSH start failed at $stage (${t::class.simpleName}): ${t.message ?: "no detail"}"
                )
                Log.e(TAG, "Failed to start SSH tunnel at $stage", t)
                withContext(NonCancellable) {
                    stopInternal()
                }
                false
            }
        }
    }

    /**
     * Resolves a raw DNS wire query strictly inside the SSH tunnel via DNS-over-TCP (port 53).
     * Never sends direct/bypassed UDP queries outside the tunnel.
     */
    fun resolveDnsViaSsh(preferredDns: String, dnsQuery: ByteArray): ByteArray? {
        if (dnsQuery.size < 12) return null

        val now = System.currentTimeMillis()
        val keyLen = minOf(dnsQuery.size, 128) - 2
        val cacheKey = if (keyLen > 0) {
            Base64.encodeToString(dnsQuery, 2, keyLen, Base64.NO_WRAP)
        } else {
            null
        }

        if (cacheKey != null) {
            val cached = dnsCache[cacheKey]
            if (cached != null) {
                if (now < cached.expireAt && cached.response.size >= 2) {
                    val reply = cached.response.copyOf()
                    reply[0] = dnsQuery[0]
                    reply[1] = dnsQuery[1]
                    return reply
                } else {
                    dnsCache.remove(cacheKey, cached)
                }
            }
        }

        val client = activeClientRef.get()
        if (client == null || !client.isConnected || !client.isAuthenticated) {
            XrayLogTailer.error("SSH DNS-over-TCP failed: SSH session is not connected/authenticated")
            return null
        }

        val dnsServers = listOf("1.1.1.1", "8.8.8.8", preferredDns.trim())
            .filter { it.isNotBlank() && !it.contains(":") }
            .distinct()

        var lastError: Throwable? = null
        for (dnsIp in dnsServers) {
            val (reply, err) = queryDnsOverSshChannelWithTimeout(client, dnsIp, dnsQuery, DNS_TCP_TIMEOUT_MS)
            if (reply != null && reply.size >= 2) {
                if (cacheKey != null) {
                    if (dnsCache.size >= MAX_DNS_CACHE_ENTRIES) {
                        dnsCache.entries.removeIf { it.value.expireAt <= now }
                        if (dnsCache.size >= MAX_DNS_CACHE_ENTRIES) {
                            dnsCache.clear()
                        }
                    }
                    dnsCache[cacheKey] = DnsCacheRecord(reply.copyOf(), now + DNS_CACHE_TTL_MS)
                }
                return reply
            }
            if (err != null) {
                lastError = err
            }
        }

        val detail = lastError?.let { "${it::class.simpleName}: ${it.message ?: "no detail"}" } ?: "no valid response"
        Log.w(TAG, "SSH DNS-over-TCP failed across $dnsServers: $detail", lastError)
        XrayLogTailer.error("SSH DNS-over-TCP failed (${dnsServers.joinToString(",")}): $detail")
        return null
    }

    private fun queryDnsOverSshChannelWithTimeout(
        client: SSHClient,
        dnsIp: String,
        dnsQuery: ByteArray,
        timeoutMs: Long
    ): Pair<ByteArray?, Throwable?> {
        val channelRef = AtomicReference<DirectTCPIPChannel?>(null)
        val future = dnsExecutor.submit(Callable<Pair<ByteArray?, Throwable?>> {
            try {
                val channel = DirectConnection(client.connection, dnsIp, 53)
                channelRef.set(channel)
                channel.open()

                val dos = DataOutputStream(BufferedOutputStream(channel.outputStream, 1024))
                val dis = DataInputStream(BufferedInputStream(channel.inputStream, 2048))
                dos.writeShort(dnsQuery.size)
                dos.write(dnsQuery)
                dos.flush()
                TrafficStatsTracker.addTx((dnsQuery.size + 2).toLong())

                val replyLen = dis.readUnsignedShort()
                if (replyLen in 1..4096) {
                    val replyPayload = ByteArray(replyLen)
                    dis.readFully(replyPayload)
                    TrafficStatsTracker.addRx((replyLen + 2).toLong())
                    replyPayload to null
                } else {
                    null to IllegalStateException("Invalid DNS TCP response length: $replyLen")
                }
            } catch (t: Throwable) {
                null to t
            } finally {
                runCatching { channelRef.getAndSet(null)?.close() }
            }
        })

        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
            future.cancel(true)
            runCatching { channelRef.getAndSet(null)?.close() }
            null to t
        }
    }

    private suspend fun startLocalSocksWithRetry(client: SSHClient, socksPort: Int): LocalSocksServer? {
        fun buildServer(): LocalSocksServer = LocalSocksServer(
            bindPort = socksPort,
            dnsResolver = ::resolveDnsViaSsh
        ) { targetHost, targetPort, clientIn, clientOut ->
            forwardSocksConnection(client, targetHost, targetPort, clientIn, clientOut)
        }

        return try {
            buildServer().apply { start() }
        } catch (firstErr: BindException) {
            Log.w(TAG, "SOCKS bind failed on 127.0.0.1:$socksPort (attempt 1); retrying in 1s", firstErr)
            XrayLogTailer.warn(
                "SSH SOCKS bind failed on 127.0.0.1:$socksPort (${firstErr::class.simpleName}: ${firstErr.message}); retrying in 1s..."
            )
            delay(SOCKS_BIND_RETRY_DELAY_MS)
            currentCoroutineContext().ensureActive()
            try {
                buildServer().apply { start() }
            } catch (secondErr: BindException) {
                Log.e(TAG, "SOCKS bind failed on 127.0.0.1:$socksPort (attempt 2)", secondErr)
                XrayLogTailer.error(
                    "SSH SOCKS bind failed after retry: port $socksPort already in use (${secondErr::class.simpleName}: ${secondErr.message}) — previous engine may not have released it"
                )
                null
            }
        }
    }

    private fun forwardSocksConnection(
        client: SSHClient,
        targetHost: String,
        targetPort: Int,
        clientIn: InputStream,
        clientOut: OutputStream
    ) {
        var channel: DirectTCPIPChannel? = null
        try {
            // SSHJ 0.38: Connection has no openChannel(), and
            // DirectTCPIPChannel's constructor is protected. The public
            // way to open a direct-tcpip channel is DirectConnection
            // (a DirectTCPIPChannel subclass with a public constructor).
            channel = DirectConnection(client.connection, targetHost, targetPort)
            channel.open()

            val remoteIn = channel.inputStream
            val remoteOut = channel.outputStream

            val t1 = Thread {
                val buf = ByteArray(16384)
                try {
                    while (true) {
                        val n = clientIn.read(buf)
                        if (n <= 0) break
                        remoteOut.write(buf, 0, n)
                        remoteOut.flush()
                        TrafficStatsTracker.addTx(n.toLong())
                    }
                } catch (_: Throwable) {}
            }

            val t2 = Thread {
                val buf = ByteArray(16384)
                try {
                    while (true) {
                        val n = remoteIn.read(buf)
                        if (n <= 0) break
                        clientOut.write(buf, 0, n)
                        clientOut.flush()
                        TrafficStatsTracker.addRx(n.toLong())
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
            runCatching { channel?.close() }
        }
    }

    private fun stopInternal() {
        val wasRunning = isRunning.getAndSet(false)
        dnsCache.clear()
        val socks = localSocksRef.getAndSet(null)
        runCatching { socks?.stop() }
        val client = activeClientRef.getAndSet(null)
        runCatching { client?.disconnect() }
        runCatching { client?.close() }
        if (wasRunning) {
            Log.i(TAG, "SSH tunnel stopped")
            XrayLogTailer.info("SSH engine stopped")
        }
    }

    suspend fun stop() = withContext(Dispatchers.IO) {
        stopInternal()
    }

    fun isRunning(): Boolean = isRunning.get()
}