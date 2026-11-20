package com.vpnx.dope.engine.common

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalSocksServer(
    private val bindHost: String = "127.0.0.1",
    private val bindPort: Int = 10808,
    private val forwarder: (targetHost: String, targetPort: Int, clientIn: InputStream, clientOut: OutputStream) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private val isRunning = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool { r ->
        Thread(r, "LocalSocksWorker").apply { isDaemon = true }
    }

    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            serverSocket = ServerSocket().apply {
                reuseAddress = true
                receiveBufferSize = 1048576
                bind(InetSocketAddress(bindHost, bindPort), 512)
            }
            executor.execute {
                while (isRunning.get()) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        executor.execute { handleClient(client) }
                    } catch (_: Throwable) {
                        break
                    }
                }
            }
            Log.i("LocalSocksServer", "Local SOCKS5 server listening on $bindHost:$bindPort")
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            runCatching { serverSocket?.close() }
            serverSocket = null
            executor.shutdownNow()
            Log.i("LocalSocksServer", "Local SOCKS5 server stopped")
        }
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
                client.close()
                return
            }
            val nMethods = dis.read()
            if (nMethods > 0) {
                val methods = ByteArray(nMethods)
                dis.readFully(methods)
            }
            dos.write(byteArrayOf(0x05, 0x00)) // NO AUTH
            dos.flush()

            // 2. Request Details
            val reqVer = dis.read()
            val cmd = dis.read()
            dis.read() // RSV
            val atyp = dis.read()
            if (reqVer != 5) {
                client.close()
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
                    client.close()
                    return
                }
            }
            val targetPort = dis.readUnsignedShort()

            if (cmd == 1) { // CONNECT
                // Reply SUCCESS (0x00)
                dos.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0x7F, 0x00, 0x00, 0x01, 0x00, 0x00))
                dos.flush()
                forwarder(targetHost, targetPort, dis, dos)
            } else {
                // Command not supported
                dos.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
                dos.flush()
                client.close()
            }
        } catch (_: Throwable) {
            runCatching { client.close() }
        }
    }
}
