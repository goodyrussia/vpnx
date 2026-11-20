package com.vpnx.dope.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vpnx.dope.data.AppDatabase
import com.vpnx.dope.data.repository.ProfileRepository
import com.vpnx.dope.data.repository.SettingsRepository
import com.vpnx.dope.ui.screens.config.ConfigViewModel
import com.vpnx.dope.ui.screens.home.HomeViewModel
import com.vpnx.dope.ui.screens.logs.LogsViewModel
import com.vpnx.dope.ui.screens.settings.SettingsViewModel
import com.vpnx.dope.ui.theme.VpnxTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val db = AppDatabase.getDatabase(this)
        val profileRepository = ProfileRepository(db)
        val settingsRepository = SettingsRepository.getInstance(this)

        setContent {
            val settings by settingsRepository.settings.collectAsState()

            VpnxTheme(themePreference = settings.theme) {
                val homeViewModel: HomeViewModel = viewModel {
                    HomeViewModel(profileRepository, settingsRepository)
                }
                val configViewModel: ConfigViewModel = viewModel {
                    ConfigViewModel(profileRepository)
                }
                val logsViewModel: LogsViewModel = viewModel {
                    LogsViewModel()
                }
                val settingsViewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(settingsRepository)
                }

                AppContent(
                    homeViewModel = homeViewModel,
                    configViewModel = configViewModel,
                    logsViewModel = logsViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    companion object {
        // Matched by CI version bump script
        const val CURRENT_BUILD_CODE = 1
        const val CURRENT_VERSION_NAME = "1.0.0"
    }
}
