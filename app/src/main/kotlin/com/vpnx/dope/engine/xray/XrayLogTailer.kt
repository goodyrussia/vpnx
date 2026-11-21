package com.vpnx.dope.engine.xray

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object XrayLogTailer {
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.US)
    private val _logFlow = MutableSharedFlow<String>(replay = 100, extraBufferCapacity = 500)
    val logFlow: SharedFlow<String> = _logFlow.asSharedFlow()

    fun log(level: String, message: String) {
        val timestamp = LocalTime.now().format(timeFormatter)
        val formatted = "[$timestamp] [$level] $message"
        _logFlow.tryEmit(formatted)
    }

    fun info(message: String) = log("INFO", message)
    fun warn(message: String) = log("WARN", message)
    fun error(message: String) = log("ERROR", message)
    fun debug(message: String) = log("DEBUG", message)

    fun clear() {
        _logFlow.resetReplayCache()
    }
}