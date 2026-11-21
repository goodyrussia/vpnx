package com.vpnx.dope.data.repository

import android.content.Context
import com.vpnx.dope.data.entity.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("vpnx_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _themePreference = MutableStateFlow(_settings.value.theme)
    val themePreference: StateFlow<String> = _themePreference.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            tunnelMode = prefs.getString(KEY_TUNNEL_MODE, "VPN") ?: "VPN",
            tunnelMtu = prefs.getInt(KEY_TUNNEL_MTU, 1400),
            primaryDns = prefs.getString(KEY_PRIMARY_DNS, "1.1.1.1") ?: "1.1.1.1",
            secondaryDns = prefs.getString(KEY_SECONDARY_DNS, "8.8.8.8") ?: "8.8.8.8",
            remoteDns = prefs.getBoolean(KEY_REMOTE_DNS, true),
            hotspotSharing = prefs.getBoolean(KEY_HOTSPOT_SHARING, false),
            tcpNoDelay = prefs.getBoolean(KEY_TCP_NODELAY, true),
            wakeLock = prefs.getBoolean(KEY_WAKE_LOCK, false),
            theme = prefs.getString(KEY_THEME, "SYSTEM") ?: "SYSTEM"
        )
    }

    @Synchronized
    fun updateSettings(newSettings: AppSettings) {
        prefs.edit()
            .putString(KEY_TUNNEL_MODE, newSettings.tunnelMode)
            .putInt(KEY_TUNNEL_MTU, newSettings.tunnelMtu)
            .putString(KEY_PRIMARY_DNS, newSettings.primaryDns)
            .putString(KEY_SECONDARY_DNS, newSettings.secondaryDns)
            .putBoolean(KEY_REMOTE_DNS, newSettings.remoteDns)
            .putBoolean(KEY_HOTSPOT_SHARING, newSettings.hotspotSharing)
            .putBoolean(KEY_TCP_NODELAY, newSettings.tcpNoDelay)
            .putBoolean(KEY_WAKE_LOCK, newSettings.wakeLock)
            .putString(KEY_THEME, newSettings.theme)
            .apply()
        _settings.value = newSettings
        if (_themePreference.value != newSettings.theme) {
            _themePreference.value = newSettings.theme
        }
    }

    fun setTunnelMode(mode: String) {
        updateSettings(_settings.value.copy(tunnelMode = mode))
    }

    companion object {
        private const val KEY_TUNNEL_MODE = "tunnel_mode"
        private const val KEY_TUNNEL_MTU = "tunnel_mtu"
        private const val KEY_PRIMARY_DNS = "primary_dns"
        private const val KEY_SECONDARY_DNS = "secondary_dns"
        private const val KEY_REMOTE_DNS = "remote_dns"
        private const val KEY_HOTSPOT_SHARING = "hotspot_sharing"
        private const val KEY_TCP_NODELAY = "tcp_nodelay"
        private const val KEY_WAKE_LOCK = "wake_lock"
        private const val KEY_THEME = "theme"

        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
