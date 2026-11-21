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

    private val tickLock = Any()
    private val lastTx = AtomicLong(0L)
    private val lastRx = AtomicLong(0L)
    private val lastTickMs = AtomicLong(System.nanoTime() / 1_000_000L)

    private val _stats = MutableStateFlow(TrafficStats())
    val stats: StateFlow<TrafficStats> = _stats.asStateFlow()

    fun addTx(bytes: Long) {
        totalTx.addAndGet(bytes)
    }

    fun addRx(bytes: Long) {
        totalRx.addAndGet(bytes)
    }

    fun tick() {
        synchronized(tickLock) {
            val now = System.nanoTime() / 1_000_000L
            val dt = (now - lastTickMs.get()).coerceAtLeast(1L)
            val curTx = totalTx.get()
            val curRx = totalRx.get()

            val txSpeed = ((curTx - lastTx.get()) * 1000L) / dt
            val rxSpeed = ((curRx - lastRx.get()) * 1000L) / dt

            lastTx.set(curTx)
            lastRx.set(curRx)
            lastTickMs.set(now)

            _stats.value = TrafficStats(
                txBytes = curTx,
                rxBytes = curRx,
                txSpeedBps = txSpeed.coerceAtLeast(0L),
                rxSpeedBps = rxSpeed.coerceAtLeast(0L)
            )
        }
    }

    fun reset() {
        synchronized(tickLock) {
            totalTx.set(0L)
            totalRx.set(0L)
            lastTx.set(0L)
            lastRx.set(0L)
            lastTickMs.set(System.nanoTime() / 1_000_000L)
            _stats.value = TrafficStats()
        }
    }
}