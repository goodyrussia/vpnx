package com.vpnx.dope.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vpnx.dope.ui.screens.config.ConfigScreen
import com.vpnx.dope.ui.screens.config.ConfigViewModel
import com.vpnx.dope.ui.screens.home.HomeScreen
import com.vpnx.dope.ui.screens.home.HomeViewModel
import com.vpnx.dope.ui.screens.logs.LogsScreen
import com.vpnx.dope.ui.screens.logs.LogsViewModel
import com.vpnx.dope.ui.screens.settings.SettingsScreen
import com.vpnx.dope.ui.screens.settings.SettingsViewModel
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Settings

enum class AppTab {
    HOME,
    CONFIG,
    LOGS,
    SETTINGS
}

@Composable
fun AppContent(
    homeViewModel: HomeViewModel,
    configViewModel: ConfigViewModel,
    logsViewModel: LogsViewModel,
    settingsViewModel: SettingsViewModel
) {
    var currentTab by rememberSaveable { mutableStateOf(AppTab.HOME) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == AppTab.HOME,
                    onClick = { currentTab = AppTab.HOME },
                    icon = MiuixIcons.Home,
                    label = "Home"
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.CONFIG,
                    onClick = { currentTab = AppTab.CONFIG },
                    icon = MiuixIcons.Layers,
                    label = "Config"
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.LOGS,
                    onClick = { currentTab = AppTab.LOGS },
                    icon = MiuixIcons.File,
                    label = "Logs"
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.SETTINGS,
                    onClick = { currentTab = AppTab.SETTINGS },
                    icon = MiuixIcons.Settings,
                    label = "Settings"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (currentTab) {
                AppTab.HOME -> HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToConfig = { currentTab = AppTab.CONFIG }
                )
                AppTab.CONFIG -> ConfigScreen(viewModel = configViewModel)
                AppTab.LOGS -> LogsScreen(viewModel = logsViewModel)
                AppTab.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
            }
        }
    }
}
