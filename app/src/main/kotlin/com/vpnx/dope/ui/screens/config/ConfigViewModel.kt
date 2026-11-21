package com.vpnx.dope.ui.screens.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vpnx.dope.data.entity.ProfileEntity
import com.vpnx.dope.data.repository.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.Socket

class ConfigViewModel(
    private val repository: ProfileRepository
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedProfile: StateFlow<ProfileEntity?> = repository.selectedProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectProfile(id: String) {
        viewModelScope.launch {
            repository.setSelected(id)
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun saveProfile(profile: ProfileEntity, autoSelect: Boolean = false) {
        viewModelScope.launch {
            repository.insertProfile(profile)
            if (autoSelect || repository.getSelectedProfileSync() == null) {
                repository.setSelected(profile.id)
            }
        }
    }

    fun importUri(rawUri: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = repository.parseAndImportUri(rawUri)
            result.onSuccess {
                repository.setSelected(it.id)
                onResult(true, "Imported: ${it.name}")
            }.onFailure {
                onResult(false, it.message ?: "Failed to import URI")
            }
        }
    }

    fun pingProfile(profile: ProfileEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            val success = runCatching {
                Socket().use { sock ->
                    sock.connect(InetSocketAddress(profile.server, profile.port), 3000)
                    true
                }
            }.getOrDefault(false)
            val ping = if (success) System.currentTimeMillis() - start else -1L
            repository.updatePing(profile.id, ping)
        }
    }
}