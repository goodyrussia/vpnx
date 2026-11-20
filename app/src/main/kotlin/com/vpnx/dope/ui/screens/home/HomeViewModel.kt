package com.vpnx.dope.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpnx.dope.data.AppDatabase
import com.vpnx.dope.data.entity.AppSettings
import com.vpnx.dope.data.entity.ProfileEntity
import com.vpnx.dope.data.model.ConnectionState
import com.vpnx.dope.data.repository.ProfileRepository
import com.vpnx.dope.data.repository.SettingsRepository
import com.vpnx.dope.engine.common.TrafficStats
import com.vpnx.dope.engine.common.TrafficStatsTracker
import com.vpnx.dope.service.ServiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class HomeViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = ServiceController.connectionState
    val selectedProfile: StateFlow<ProfileEntity?> = profileRepository.selectedProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val settings: StateFlow<AppSettings> = settingsRepository.settings
    val trafficStats: StateFlow<TrafficStats> = TrafficStatsTracker.stats

    private val _durationText = MutableStateFlow("00:00:00")
    val durationText: StateFlow<String> = _durationText.asStateFlow()

    private var connectedSinceMs = 0L

    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                if (state == ConnectionState.CONNECTED) {
                    if (connectedSinceMs == 0L) {
                        connectedSinceMs = System.currentTimeMillis()
                    }
                } else if (state == ConnectionState.DISCONNECTED) {
                    connectedSinceMs = 0L
                    _durationText.value = "00:00:00"
                }
            }
        }

        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (connectedSinceMs > 0L) {
                    val sec = (System.currentTimeMillis() - connectedSinceMs) / 1000
                    val h = sec / 3600
                    val m = (sec % 3600) / 60
                    val s = sec % 60
                    _durationText.value = "%02d:%02d:%02d".format(h, m, s)
                }
            }
        }
    }

    fun toggleConnection(context: Context) {
        val cur = connectionState.value
        if (cur == ConnectionState.CONNECTED || cur == ConnectionState.CONNECTING) {
            ServiceController.stopTunnel(context)
        } else {
            ServiceController.startTunnel(context)
        }
    }

    fun pingActiveServer() {
        val profile = selectedProfile.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val success = runCatching {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(profile.server, profile.port), 3000)
                    true
                }
            }.getOrDefault(false)
            val ping = if (success) System.currentTimeMillis() - start else -1L
            profileRepository.updatePing(profile.id, ping)
        }
    }
}
