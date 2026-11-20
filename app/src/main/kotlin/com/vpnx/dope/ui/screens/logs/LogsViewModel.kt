package com.vpnx.dope.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpnx.dope.engine.xray.XrayLogTailer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogsViewModel : ViewModel() {

    private val allLogs = MutableStateFlow<List<String>>(emptyList())
    val selectedFilter = MutableStateFlow("All") // "All", "INFO", "WARN", "ERROR"

    val filteredLogs: StateFlow<List<String>> = combine(allLogs, selectedFilter) { logs, filter ->
        if (filter == "All") {
            logs
        } else {
            logs.filter { it.contains("[$filter]") }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            XrayLogTailer.logFlow.collect { line ->
                val current = allLogs.value
                val next = if (current.size > 500) current.drop(1) + line else current + line
                allLogs.value = next
            }
        }
    }

    fun setFilter(filter: String) {
        selectedFilter.value = filter
    }

    fun clearLogs() {
        allLogs.value = emptyList()
        XrayLogTailer.clear()
    }
}
