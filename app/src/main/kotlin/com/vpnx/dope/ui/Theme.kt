package com.vpnx.dope.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * VPNX has exactly one theme: Miuix dark (black). No light theme,
 * no theme options anywhere in the app.
 */
@Composable
fun VpnxTheme(content: @Composable () -> Unit) {
    val controller = remember { ThemeController(ColorSchemeMode.Dark) }
    MiuixTheme(controller) {
        content()
    }
}
