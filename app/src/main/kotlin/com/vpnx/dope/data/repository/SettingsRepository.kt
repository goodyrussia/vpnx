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
        val rawMtu = prefs.getInt(KEY_TUNNEL_MTU, DEFAULT_MTU)
        val rawPrimaryDns = prefs.getString(KEY_PRIMARY_DNS, DEFAULT_PRIMARY_DNS) ?: DEFAULT_PRIMARY_DNS
        val rawSecondaryDns = prefs.getString(KEY_SECONDARY_DNS, DEFAULT_SECONDARY_DNS) ?: DEFAULT_SECONDARY_DNS

        return AppSettings(
            tunnelMode = prefs.getString(KEY_TUNNEL_MODE, "VPN") ?: "VPN",
            tunnelMtu = rawMtu.coerceIn(MIN_MTU, MAX_MTU),
            primaryDns = sanitizePrimaryDns(rawPrimaryDns),
            secondaryDns = sanitizeSecondaryDns(rawSecondaryDns),
            remoteDns = prefs.getBoolean(KEY_REMOTE_DNS, true),
            hotspotSharing = prefs.getBoolean(KEY_HOTSPOT_SHARING, false),
            tcpNoDelay = prefs.getBoolean(KEY_TCP_NODELAY, true),
            wakeLock = prefs.getBoolean(KEY_WAKE_LOCK, false),
            theme = prefs.getString(KEY_THEME, "SYSTEM") ?: "SYSTEM"
        )
    }

    @Synchronized
    fun updateSettings(newSettings: AppSettings) {
        val sanitized = newSettings.copy(
            tunnelMtu = newSettings.tunnelMtu.coerceIn(MIN_MTU, MAX_MTU),
            primaryDns = sanitizePrimaryDns(newSettings.primaryDns),
            secondaryDns = sanitizeSecondaryDns(newSettings.secondaryDns)
        )
        prefs.edit()
            .putString(KEY_TUNNEL_MODE, sanitized.tunnelMode)
            .putInt(KEY_TUNNEL_MTU, sanitized.tunnelMtu)
            .putString(KEY_PRIMARY_DNS, sanitized.primaryDns)
            .putString(KEY_SECONDARY_DNS, sanitized.secondaryDns)
            .putBoolean(KEY_REMOTE_DNS, sanitized.remoteDns)
            .putBoolean(KEY_HOTSPOT_SHARING, sanitized.hotspotSharing)
            .putBoolean(KEY_TCP_NODELAY, sanitized.tcpNoDelay)
            .putBoolean(KEY_WAKE_LOCK, sanitized.wakeLock)
            .putString(KEY_THEME, sanitized.theme)
            .apply()
        _settings.value = sanitized
        if (_themePreference.value != sanitized.theme) {
            _themePreference.value = sanitized.theme
        }
    }

    fun setTunnelMode(mode: String) {
        updateSettings(_settings.value.copy(tunnelMode = mode))
    }

    companion object {
        const val MIN_MTU = 576
        const val MAX_MTU = 9000
        const val DEFAULT_MTU = 1400
        const val DEFAULT_PRIMARY_DNS = "1.1.1.1"
        const val DEFAULT_SECONDARY_DNS = "8.8.8.8"

        private val IPV4_REGEX = Regex(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$"
        )
        private val IPV6_CHARS_REGEX = Regex("^[0-9a-fA-F:]+$")
        private val HOSTNAME_REGEX = Regex(
            "^(?=.{1,253}$)(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))*$"
        )

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

        fun isValidMtu(mtu: Int): Boolean = mtu in MIN_MTU..MAX_MTU

        fun isNumericIp(value: String): Boolean {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return false
            if (IPV4_REGEX.matches(trimmed)) return true
            if (trimmed.contains(":") && IPV6_CHARS_REGEX.matches(trimmed)) {
                val doubleColonCount = trimmed.split("::").size - 1
                if (doubleColonCount > 1) return false
                val groups = trimmed.split(":").filter { it.isNotEmpty() }
                if (groups.size > 8 || groups.any { it.length > 4 }) return false
                return doubleColonCount == 1 || trimmed.count { it == ':' } == 7
            }
            return false
        }

        fun isValidDnsServer(value: String): Boolean {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return false
            return isNumericIp(trimmed) || HOSTNAME_REGEX.matches(trimmed)
        }

        private fun sanitizePrimaryDns(raw: String): String {
            val trimmed = raw.trim()
            return if (isValidDnsServer(trimmed)) trimmed else DEFAULT_PRIMARY_DNS
        }

        private fun sanitizeSecondaryDns(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            return if (isValidDnsServer(trimmed)) trimmed else ""
        }

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}