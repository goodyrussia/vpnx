package com.vpnx.dope.engine.ssh

import android.net.VpnService
import android.util.Log
import com.vpnx.dope.data.model.SshProfile
import com.vpnx.dope.engine.common.LocalSocksServer
import com.vpnx.dope.engine.common.TrafficStatsTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.DirectConnection
import net.schmizz.sshj.connection.channel.direct.DirectTCPIPChannel
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.util.concurrent.atomic.AtomicBoolean

object SshEngine {
    private const val TAG = "SshEngine"
    private const val SSH_CONNECT_TIMEOUT_MS = 15_000
    private const val SSH_OPERATION_TIMEOUT_MS = 30_000

    @Volatile
    var vpnService: VpnService? = null

    private var sshClient: SSHClient? = null
    private var localSocksServer: LocalSocksServer? = null
    private val isRunning = AtomicBoolean(false)

    suspend fun start(profile: SshProfile, socksPort: Int = 10808): Boolean = withContext(Dispatchers.IO) {
        if (isRunning.get()) stop()

        try {
            val config = DefaultConfig()
            val client = SSHClient(config).apply {
                addHostKeyVerifier(object : HostKeyVerifier {
                    override fun verify(hostname: String?, port: Int, key: PublicKey?): Boolean = true
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
                    socketProtectCallback = { sock -> vpnService?.protect(sock) }
                )
            }

            Log.i(TAG, "Connecting SSH to ${profile.server}:${profile.port}...")
            client.connect(profile.server, profile.port)

            Log.i(TAG, "Authenticating SSH user '${profile.user}'...")
            client.authPassword(profile.user, profile.pass)

            if (!client.isAuthenticated) {
                Log.e(TAG, "SSH authentication failed for user '${profile.user}'")
                client.disconnect()
                return@withContext false
            }

            sshClient = client
            isRunning.set(true)

            // Start loopback SOCKS5 server on 127.0.0.1:10808
            localSocksServer = LocalSocksServer(bindPort = socksPort) { targetHost, targetPort, clientIn, clientOut ->
                forwardSocksConnection(client, targetHost, targetPort, clientIn, clientOut)
            }.apply { start() }

            Log.i(TAG, "SSH tunnel active; SOCKS5 listening on 127.0.0.1:$socksPort")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start SSH tunnel", t)
            stop()
            false
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

    suspend fun stop() = withContext(Dispatchers.IO) {
        val wasRunning = isRunning.getAndSet(false)
        runCatching { localSocksServer?.stop() }
        localSocksServer = null
        runCatching { sshClient?.disconnect() }
        sshClient = null
        if (wasRunning) {
            Log.i(TAG, "SSH tunnel stopped")
        }
    }

    fun isRunning(): Boolean = isRunning.get()
}