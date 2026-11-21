package com.vpnx.dope.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpnx.dope.data.entity.AppSettings
import com.vpnx.dope.data.repository.SettingsRepository
import com.vpnx.dope.engine.root.RootShellManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = repository.settings

    private val _rootAvailable = MutableStateFlow<Boolean?>(null)
    val rootAvailable: StateFlow<Boolean?> = _rootAvailable.asStateFlow()

    init {
        checkRoot()
    }

    fun checkRoot() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = RootShellManager.checkRootAccess()
                _rootAvailable.value = result
            } catch (t: Throwable) {
                Log.e("SettingsViewModel", "Failed to check root access", t)
                _rootAvailable.value = false
            }
        }
    }

    fun updateTunnelMode(mode: String) {
        viewModelScope.launch {
            repository.setTunnelMode(mode)
        }
    }

    fun updateMtu(mtu: Int) {
        val clamped = mtu.coerceIn(SettingsRepository.MIN_MTU, SettingsRepository.MAX_MTU)
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(tunnelMtu = clamped))
        }
    }

    fun updateDns(primary: String, secondary: String) {
        val trimmedPrimary = primary.trim()
        val trimmedSecondary = secondary.trim()
        if (!SettingsRepository.isValidDnsServer(trimmedPrimary)) return
        if (trimmedSecondary.isNotEmpty() && !SettingsRepository.isValidDnsServer(trimmedSecondary)) return

        viewModelScope.launch {
            repository.updateSettings(
                settings.value.copy(
                    primaryDns = trimmedPrimary,
                    secondaryDns = trimmedSecondary
                )
            )
        }
    }

    fun updateHotspotSharing(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(hotspotSharing = enabled))
        }
    }

    fun updateTcpNoDelay(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(tcpNoDelay = enabled))
        }
    }

    fun updateWakeLock(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(wakeLock = enabled))
        }
    }

    fun updateTheme(theme: String) {
        viewModelScope.launch {
            repository.updateSettings(settings.value.copy(theme = theme))
        }
    }
}