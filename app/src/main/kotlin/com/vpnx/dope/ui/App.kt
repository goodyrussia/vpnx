package com.vpnx.dope.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vpnx.dope.data.ConnectionMode
import com.vpnx.dope.ui.screens.ConfigsScreen
import com.vpnx.dope.ui.screens.HomeScreen
import com.vpnx.dope.ui.screens.LogsScreen
import com.vpnx.dope.ui.screens.SettingsScreen
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar

@Composable
fun VpnxApp() {
    VpnxTheme {
        var tab by remember { mutableIntStateOf(0) }
        var mode by remember { mutableStateOf(ConnectionMode.ROOT_TUN2SOCKS) }

        Scaffold(
            topBar = { TopAppBar(title = "VPNX") },
            bottomBar = {
                NavigationBar {
                    NavigationItem(
                        icon = IconHome,
                        label = "Home",
                        selected = tab == 0,
                        onClick = { tab = 0 },
                    )
                    NavigationItem(
                        icon = IconConfigs,
                        label = "Configs",
                        selected = tab == 1,
                        onClick = { tab = 1 },
                    )
                    NavigationItem(
                        icon = IconLogs,
                        label = "Logs",
                        selected = tab == 2,
                        onClick = { tab = 2 },
                    )
                    NavigationItem(
                        icon = IconSettings,
                        label = "Settings",
                        selected = tab == 3,
                        onClick = { tab = 3 },
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(mode = mode)
                    1 -> ConfigsScreen()
                    2 -> LogsScreen()
                    else -> SettingsScreen(mode = mode, onModeChange = { mode = it })
                }
            }
        }
    }
}
