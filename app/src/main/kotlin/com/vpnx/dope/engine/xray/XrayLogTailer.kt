package com.vpnx.dope.engine.xray

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object XrayLogTailer {
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val _logFlow = MutableSharedFlow<String>(replay = 100, extraBufferCapacity = 500)
    val logFlow: SharedFlow<String> = _logFlow.asSharedFlow()

    fun log(level: String, message: String) {
        val timestamp = timeFormat.format(Date())
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
