package com.vpnx.dope.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vpnx.dope.data.AppDatabase
import com.vpnx.dope.data.repository.ProfileRepository
import com.vpnx.dope.data.repository.SettingsRepository
import com.vpnx.dope.service.ServiceController
import com.vpnx.dope.ui.screens.config.ConfigViewModel
import com.vpnx.dope.ui.screens.home.HomeViewModel
import com.vpnx.dope.ui.screens.logs.LogsViewModel
import com.vpnx.dope.ui.screens.settings.SettingsViewModel
import com.vpnx.dope.ui.theme.VpnxTheme

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        ServiceController.onVpnPermissionResult(result.resultCode == RESULT_OK, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        ServiceController.vpnPermissionRequester.registerLauncher { intent ->
            vpnPermissionLauncher.launch(intent)
        }

        val db = AppDatabase.getDatabase(this)
        val profileRepository = ProfileRepository(db)
        val settingsRepository = SettingsRepository.getInstance(this)

        setContent {
            // Decouple theme preference collection so changing tunnelMode does NOT recompose MainActivity
            val themePreference by settingsRepository.themePreference.collectAsState()

            VpnxTheme(themePreference = themePreference) {
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

    override fun onDestroy() {
        ServiceController.vpnPermissionRequester.complete(false)
        ServiceController.vpnPermissionRequester.registerLauncher(null)
        super.onDestroy()
    }

    companion object {
        // Matched by CI version bump script
        const val CURRENT_BUILD_CODE = 11
        const val CURRENT_VERSION_NAME = "1.0.10"
    }
}
