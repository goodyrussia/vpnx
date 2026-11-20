package com.vpnx.dope.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Home : Route

    @Serializable
    data object Config : Route

    @Serializable
    data object Logs : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data class ProfileEditor(val profileId: String? = null) : Route
}
