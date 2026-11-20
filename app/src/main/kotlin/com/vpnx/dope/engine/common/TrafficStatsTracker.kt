package com.vpnx.dope.engine.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class TrafficStats(
    val txBytes: Long = 0L,
    val rxBytes: Long = 0L,
    val txSpeedBps: Long = 0L,
    val rxSpeedBps: Long = 0L
)

object TrafficStatsTracker {
    private val totalTx = AtomicLong(0L)
    private val totalRx = AtomicLong(0L)

    private var lastTx = 0L
    private var lastRx = 0L
    private var lastTickMs = System.currentTimeMillis()

    private val _stats = MutableStateFlow(TrafficStats())
    val stats: StateFlow<TrafficStats> = _stats.asStateFlow()

    fun addTx(bytes: Long) {
        totalTx.addAndGet(bytes)
    }

    fun addRx(bytes: Long) {
        totalRx.addAndGet(bytes)
    }

    fun tick() {
        val now = System.currentTimeMillis()
        val dt = (now - lastTickMs).coerceAtLeast(1)
        val curTx = totalTx.get()
        val curRx = totalRx.get()

        val txSpeed = ((curTx - lastTx) * 1000) / dt
        val rxSpeed = ((curRx - lastRx) * 1000) / dt

        lastTx = curTx
        lastRx = curRx
        lastTickMs = now

        _stats.value = TrafficStats(
            txBytes = curTx,
            rxBytes = curRx,
            txSpeedBps = txSpeed.coerceAtLeast(0),
            rxSpeedBps = rxSpeed.coerceAtLeast(0)
        )
    }

    fun reset() {
        totalTx.set(0L)
        totalRx.set(0L)
        lastTx = 0L
        lastRx = 0L
        lastTickMs = System.currentTimeMillis()
        _stats.value = TrafficStats()
    }
}
