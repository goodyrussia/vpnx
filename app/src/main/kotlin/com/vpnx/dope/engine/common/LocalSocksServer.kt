package com.vpnx.dope.engine.common

import android.util.Log
import com.vpnx.dope.engine.xray.XrayLogTailer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class LocalSocksServer(
    private val bindHost: String = "127.0.0.1",
    private val bindPort: Int = 10808,
    private val dnsResolver: ((preferredDns: String, dnsQuery: ByteArray) -> ByteArray?)? = null,
    private val forwarder: (targetHost: String, targetPort: Int, clientIn: InputStream, clientOut: OutputStream) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val activeClients = ConcurrentHashMap.newKeySet<Socket>()
    private val activeUdpSockets = ConcurrentHashMap.newKeySet<DatagramSocket>()
    private val loggedFirstUdpAssociate = AtomicBoolean(false)
    private val loggedFirstDnsReply = AtomicBoolean(false)
    private val droppedNonDnsUdpCount = AtomicLong(0L)
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "LocalSocksWorker").apply { isDaemon = true }
    }

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            loggedFirstUdpAssociate.set(false)
            loggedFirstDnsReply.set(false)
            droppedNonDnsUdpCount.set(0L)

            val server = try {
                ServerSocket().apply {
                    reuseAddress = true
                    receiveBufferSize = 1048576
                    bind(InetSocketAddress(bindHost, bindPort), 512)
                }
            } catch (t: Throwable) {
                isRunning.set(false)
                runCatching { serverSocket?.close() }
                serverSocket = null
                throw t
            }

            serverSocket = server
            executor.execute {
                try {
                    while (isRunning.get()) {
                        try {
                            val client = server.accept()
                            activeClients.add(client)
                            executor.execute { handleClient(client) }
                        } catch (_: Throwable) {
                            break
                        }
                    }
                } finally {
                    isRunning.set(false)
                }
            }
            Log.i(TAG, "Local SOCKS5 server listening on $bindHost:$bindPort")
        }
    }

    fun stop() {
        isRunning.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null

        val udpSnapshot = activeUdpSockets.toList()
        activeUdpSockets.clear()
        for (ds in udpSnapshot) {
            runCatching { ds.close() }
        }

        val clientSnapshot = activeClients.toList()
        activeClients.clear()
        for (client in clientSnapshot) {
            runCatching { client.close() }
        }

        executor.shutdownNow()
        Log.i(TAG, "Local SOCKS5 server stopped")
    }

    private fun handleClient(client: Socket) {
        try {
            client.tcpNoDelay = true
            val rawIn = client.getInputStream()
            val rawOut = client.getOutputStream()
            val dis = DataInputStream(BufferedInputStream(rawIn, 32768))
            val dos = BufferedOutputStream(rawOut, 32768)

            // 1. SOCKS5 Handshake
            val ver = dis.read()
            if (ver != 5) {
                return
            }
            val nMethods = dis.read()
            if (nMethods < 0) {
                return
            }
            if (nMethods > 0) {
                val methods = ByteArray(nMethods)
                dis.readFully(methods)
            }
            dos.write(byteArrayOf(0x05, 0x00)) // NO AUTH
            dos.flush()

            // 2. Request Details
            val reqVer = dis.read()
            val cmd = dis.read()
            val rsv = dis.read()
            val atyp = dis.read()
            if (reqVer != 5 || cmd < 0 || rsv < 0 || atyp < 0) {
                return
            }

            val targetHost = when (atyp) {
                1 -> {
                    val addr = ByteArray(4)
                    dis.readFully(addr)
                    InetAddress.getByAddress(addr).hostAddress ?: ""
                }
                3 -> {
                    val len = dis.read()
                    if (len < 0) return
                    val domain = ByteArray(len)
                    dis.readFully(domain)
                    String(domain, Charsets.UTF_8)
                }
                4 -> {
                    val addr = ByteArray(16)
                    dis.readFully(addr)
                    InetAddress.getByAddress(addr).hostAddress ?: ""
                }
                else -> {
                    return
                }
            }
            val targetPort = dis.readUnsignedShort()

            when (cmd) {
                1 -> { // CONNECT (TCP)
                    dos.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0x7F, 0x00, 0x00, 0x01, 0x00, 0x00))
                    dos.flush()
                    forwarder(targetHost, targetPort, dis, dos)
                }
                3 -> { // UDP ASSOCIATE (DNS-only relay)
                    val resolver = dnsResolver
                    if (resolver != null) {
                        handleSocks5UdpAssociate(client, dis, dos, resolver)
                    } else {
                        dos.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                        dos.flush()
                    }
                }
                else -> {
                    // Command not supported
                    dos.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                    dos.flush()
                }
            }
        } catch (_: Throwable) {
        } finally {
            activeClients.remove(client)
            runCatching { client.close() }
        }
    }

    private fun handleSocks5UdpAssociate(
        client: Socket,
        ins: InputStream,
        outs: OutputStream,
        resolver: (preferredDns: String, dnsQuery: ByteArray) -> ByteArray?
    ) {
        var udpRelay: DatagramSocket? = null
        try {
            udpRelay = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
            activeUdpSockets.add(udpRelay)
            val relayPort = udpRelay.localPort

            outs.write(
                byteArrayOf(
                    0x05, 0x00, 0x00, 0x01,
                    127, 0, 0, 1,
                    ((relayPort shr 8) and 0xFF).toByte(),
                    (relayPort and 0xFF).toByte()
                )
            )
            outs.flush()

            if (loggedFirstUdpAssociate.compareAndSet(false, true)) {
                XrayLogTailer.info("SOCKS5 UDP ASSOCIATE relay opened on 127.0.0.1:$relayPort (DNS-only)")
            } else {
                XrayLogTailer.debug("SOCKS5 UDP ASSOCIATE relay opened on 127.0.0.1:$relayPort")
            }

            val relayRef = udpRelay
            executor.execute {
                val buf = ByteArray(4096)
                while (isRunning.get() && !relayRef.isClosed) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        relayRef.receive(pkt)
                        val length = pkt.length
                        if (length < 10) continue

                        val data = pkt.data.copyOfRange(0, length)
                        val clientAddr = pkt.socketAddress

                        // RFC 1928 §7: RSV (2), FRAG (1), ATYP (1), DST.ADDR, DST.PORT (2), DATA
                        val frag = data[2].toInt() and 0xFF
                        if (frag != 0) continue

                        val atyp = data[3].toInt() and 0xFF
                        val portOffset: Int
                        val dstHost = when (atyp) {
                            1 -> {
                                portOffset = 8
                                if (portOffset + 2 > data.size) continue
                                InetAddress.getByAddress(data.copyOfRange(4, 8)).hostAddress ?: "1.1.1.1"
                            }
                            3 -> {
                                if (data.size < 7) continue
                                val domainLen = data[4].toInt() and 0xFF
                                portOffset = 5 + domainLen
                                if (portOffset + 2 > data.size) continue
                                String(data, 5, domainLen, Charsets.UTF_8)
                            }
                            4 -> {
                                portOffset = 20
                                if (portOffset + 2 > data.size) continue
                                InetAddress.getByAddress(data.copyOfRange(4, 20)).hostAddress ?: "1.1.1.1"
                            }
                            else -> continue
                        }

                        val dstPort = ((data[portOffset].toInt() and 0xFF) shl 8) or
                            (data[portOffset + 1].toInt() and 0xFF)
                        if (dstPort != 53 && dstPort != 5353) {
                            val dropped = droppedNonDnsUdpCount.incrementAndGet()
                            if (dropped == 1L || dropped % 50L == 0L) {
                                XrayLogTailer.debug(
                                    "SOCKS5 UDP dropped non-DNS datagram to $dstHost:$dstPort (total dropped: $dropped)"
                                )
                            }
                            continue
                        }

                        val payloadStart = portOffset + 2
                        if (payloadStart >= data.size) continue

                        val header = data.copyOfRange(0, payloadStart)
                        val dnsQuery = data.copyOfRange(payloadStart, data.size)

                        executor.execute {
                            XrayLogTailer.debug("SOCKS5 UDP DNS query -> $dstHost:$dstPort (${dnsQuery.size}B)")
                            val dnsReply = runCatching { resolver(dstHost, dnsQuery) }.getOrNull()
                            if (dnsReply != null && dnsReply.isNotEmpty()) {
                                val response = ByteArray(header.size + dnsReply.size)
                                System.arraycopy(header, 0, response, 0, header.size)
                                System.arraycopy(dnsReply, 0, response, header.size, dnsReply.size)
                                runCatching {
                                    relayRef.send(DatagramPacket(response, response.size, clientAddr))
                                }.onSuccess {
                                    if (loggedFirstDnsReply.compareAndSet(false, true)) {
                                        XrayLogTailer.info(
                                            "SOCKS5 UDP DNS resolved via tunnel ($dstHost:$dstPort, ${dnsReply.size}B)"
                                        )
                                    } else {
                                        XrayLogTailer.debug(
                                            "SOCKS5 UDP DNS reply <- $dstHost:$dstPort (${dnsReply.size}B)"
                                        )
                                    }
                                }.onFailure { t ->
                                    Log.w(TAG, "Failed to send UDP DNS reply to client", t)
                                }
                            } else {
                                XrayLogTailer.warn(
                                    "SOCKS5 UDP DNS resolution via tunnel failed for $dstHost:$dstPort"
                                )
                            }
                        }
                    } catch (_: Throwable) {
                        break
                    }
                }
            }

            // RFC 1928 §7: UDP association terminates when the controlling TCP connection closes.
            while (isRunning.get() && !client.isClosed && ins.read() != -1) {
                // Wait for EOF on TCP control channel
            }
        } catch (t: Throwable) {
            Log.d(TAG, "UDP ASSOCIATE ended: ${t.message}")
        } finally {
            udpRelay?.let {
                activeUdpSockets.remove(it)
                runCatching { it.close() }
            }
            runCatching { client.close() }
        }
    }

    companion object {
        private const val TAG = "LocalSocksServer"
    }
}