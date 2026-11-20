package com.vpnx.dope.ui.screens.settings

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
            val result = RootShellManager.checkRootAccess()
            _rootAvailable.value = result
        }
    }

    fun updateTunnelMode(mode: String) {
        repository.setTunnelMode(mode)
    }

    fun updateMtu(mtu: Int) {
        repository.updateSettings(settings.value.copy(tunnelMtu = mtu))
    }

    fun updateDns(primary: String, secondary: String) {
        repository.updateSettings(settings.value.copy(primaryDns = primary, secondaryDns = secondary))
    }

    fun updateHotspotSharing(enabled: Boolean) {
        repository.updateSettings(settings.value.copy(hotspotSharing = enabled))
    }

    fun updateTcpNoDelay(enabled: Boolean) {
        repository.updateSettings(settings.value.copy(tcpNoDelay = enabled))
    }

    fun updateWakeLock(enabled: Boolean) {
        repository.updateSettings(settings.value.copy(wakeLock = enabled))
    }

    fun updateTheme(theme: String) {
        repository.updateSettings(settings.value.copy(theme = theme))
    }
}
