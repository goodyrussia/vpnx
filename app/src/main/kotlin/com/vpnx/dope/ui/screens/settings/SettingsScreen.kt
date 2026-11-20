package com.vpnx.dope.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpnx.dope.ui.theme.BrandBlue
import com.vpnx.dope.ui.theme.StatusAmber
import com.vpnx.dope.ui.theme.StatusGreen
import com.vpnx.dope.ui.theme.StatusRed
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val settings by viewModel.settings.collectAsState()
    val rootAvailable by viewModel.rootAvailable.collectAsState()

    val modeOptions = listOf("VPN Service", "Root (hev)")
    val selectedModeIdx = if (settings.tunnelMode == "ROOT") 1 else 0

    val themeOptions = listOf("System", "Light", "Dark")
    val selectedThemeIdx = when (settings.theme.uppercase()) {
        "LIGHT" -> 1
        "DARK" -> 2
        else -> 0
    }

    Scaffold(
        topBar = {
            TopAppBar(title = "Settings")
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Group 1: Capture & Engine Mode
            Text(
                text = "CAPTURE & ENGINE MODE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    OverlayDropdownPreference(
                        items = modeOptions,
                        selectedIndex = selectedModeIdx,
                        title = "Tunnel Mode",
                        summary = if (settings.tunnelMode == "ROOT") "High-speed kernel TUN routing" else "Standard non-root Android VpnService",
                        onSelectedIndexChange = { idx ->
                            viewModel.updateTunnelMode(if (idx == 1) "ROOT" else "VPN")
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val (rootText, rootColor) = when (rootAvailable) {
                        true -> Pair("Granted via KernelSU / SU", StatusGreen)
                        false -> Pair("Denied or not available", StatusRed)
                        null -> Pair("Checking root access...", StatusAmber)
                    }

                    ArrowPreference(
                        title = "Root Status",
                        summary = rootText,
                        onClick = { viewModel.checkRoot() }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = settings.tunnelMtu.toString(),
                        onValueChange = { viewModel.updateMtu(it.toIntOrNull() ?: 1400) },
                        label = "Tunnel MTU (default 1400)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Group 2: Hotspot & Tethering Sharing
            Text(
                text = "TETHERING & HOTSPOT SHARING",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    SwitchPreference(
                        checked = settings.hotspotSharing,
                        onCheckedChange = { viewModel.updateHotspotSharing(it) },
                        title = "Share Tunnel via Hotspot",
                        summary = if (settings.tunnelMode == "ROOT") {
                            "Direct kernel routing: marks ap+/wlan+ packets & removes TC offload"
                        } else {
                            "Proxy share: runs Hotshare proxy server on port 10809"
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Group 3: DNS Settings
            Text(
                text = "DNS RESOLUTION",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    TextField(
                        value = settings.primaryDns,
                        onValueChange = { viewModel.updateDns(it, settings.secondaryDns) },
                        label = "Primary DNS Server",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    TextField(
                        value = settings.secondaryDns,
                        onValueChange = { viewModel.updateDns(settings.primaryDns, it) },
                        label = "Secondary DNS Server",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Group 4: Network & System
            Text(
                text = "NETWORK & SYSTEM",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    SwitchPreference(
                        checked = settings.tcpNoDelay,
                        onCheckedChange = { viewModel.updateTcpNoDelay(it) },
                        title = "TCP NoDelay",
                        summary = "Disable Nagle algorithm for reduced connection latency"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SwitchPreference(
                        checked = settings.wakeLock,
                        onCheckedChange = { viewModel.updateWakeLock(it) },
                        title = "CPU WakeLock",
                        summary = "Keep CPU active during high-bandwidth background streaming"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OverlayDropdownPreference(
                        items = themeOptions,
                        selectedIndex = selectedThemeIdx,
                        title = "App Theme",
                        onSelectedIndexChange = { idx ->
                            viewModel.updateTheme(when (idx) {
                                1 -> "LIGHT"
                                2 -> "DARK"
                                else -> "SYSTEM"
                            })
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
