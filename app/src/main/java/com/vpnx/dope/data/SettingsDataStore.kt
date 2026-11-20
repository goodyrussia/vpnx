package com.vpnx.dope.data

import android.content.Context
import com.vpnx.dope.ui.viewmodel.VpnStateBridge
import kotlinx.coroutines.flow.Flow

class SettingsDataStore(private val context: Context) {

    init {
        VpnStateBridge.initPrefs(context)
    }

    val themeMode: Flow<String> = VpnStateBridge.themeMode
    val roundRobin: Flow<Boolean> = VpnStateBridge.loadBalancingEnabled

    suspend fun saveTheme(theme: String) {
        VpnStateBridge.themeMode.value = theme
        VpnStateBridge.persistAllSettings(context)
    }

    suspend fun saveRoundRobin(enabled: Boolean) {
        VpnStateBridge.saveRoundRobinPref(context, enabled)
    }
}
