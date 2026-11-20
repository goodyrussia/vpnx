package com.vpnx.dope.engine.ssh

import java.io.InputStream
import kotlin.math.min

class FastPrefixInputStream(private val delegate: InputStream) : InputStream() {
    private var prefixBuf: ByteArray? = null
    private var prefixPos = 0
    private var prefixLimit = 0

    fun unread(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        val rem = prefixLimit - prefixPos
        val next = ByteArray(len + rem)
        System.arraycopy(b, off, next, 0, len)
        val existing = prefixBuf
        if (rem > 0 && existing != null) {
            System.arraycopy(existing, prefixPos, next, len, rem)
        }
        prefixBuf = next
        prefixPos = 0
        prefixLimit = next.size
    }

    override fun read(): Int {
        val buf = prefixBuf
        if (buf != null) {
            val v = buf[prefixPos++].toInt() and 0xFF
            if (prefixPos >= prefixLimit) {
                prefixBuf = null
                prefixPos = 0
                prefixLimit = 0
            }
            return v
        }
        return delegate.read()
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val buf = prefixBuf
        if (buf != null) {
            val avail = prefixLimit - prefixPos
            val toCopy = min(len, avail)
            System.arraycopy(buf, prefixPos, b, off, toCopy)
            prefixPos += toCopy
            if (prefixPos >= prefixLimit) {
                prefixBuf = null
                prefixPos = 0
                prefixLimit = 0
            }
            return toCopy
        }
        return delegate.read(b, off, len)
    }

    override fun available(): Int {
        val rem = if (prefixBuf != null) (prefixLimit - prefixPos) else 0
        return rem + delegate.available()
    }

    override fun close() = delegate.close()
}
