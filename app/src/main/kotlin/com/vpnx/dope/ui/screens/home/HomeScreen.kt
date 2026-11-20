package com.vpnx.dope.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpnx.dope.data.model.ConnectionState
import com.vpnx.dope.ui.components.LatencyBadge
import com.vpnx.dope.ui.components.StatusBadge
import com.vpnx.dope.ui.components.TrafficGraph
import com.vpnx.dope.ui.theme.BrandBlue
import com.vpnx.dope.ui.theme.StatusAmber
import com.vpnx.dope.ui.theme.StatusGreen
import com.vpnx.dope.ui.theme.StatusRed
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToConfig: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.connectionState.collectAsState()
    val profile by viewModel.selectedProfile.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val stats by viewModel.trafficStats.collectAsState()
    val duration by viewModel.durationText.collectAsState()

    val modeTitle = if (settings.tunnelMode == "ROOT") "Root (hev)" else "VPN Service"

    Scaffold(
        topBar = {
            TopAppBar(
                title = "vpnx",
                actions = {
                    Text(
                        text = "[$modeTitle]",
                        color = BrandBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // 1. Hero Connection Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusBadge(state = state)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = duration,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    val (btnColor, btnText) = when (state) {
                        ConnectionState.CONNECTED -> Pair(StatusRed, "DISCONNECT")
                        ConnectionState.CONNECTING -> Pair(StatusAmber, "CONNECTING...")
                        ConnectionState.DISCONNECTING -> Pair(StatusAmber, "STOPPING...")
                        ConnectionState.DISCONNECTED -> Pair(BrandBlue, "CONNECT")
                    }

                    Button(
                        onClick = { viewModel.toggleConnection(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            color = btnColor
                        )
                    ) {
                        Text(
                            text = btnText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Active Profile Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ACTIVE PROFILE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        if (profile != null) {
                            LatencyBadge(pingMs = profile!!.lastPingMs)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (profile != null) {
                        val p = profile!!
                        val protoBadge = if (p.protocolType == "SSH") "SSH (${p.sshMode})" else "XRAY (${p.xrayProtocol})"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p.name,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${p.server}:${p.port} • $protoBadge",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            Button(
                                onClick = { viewModel.pingActiveServer() },
                                colors = ButtonDefaults.buttonColors(color = BrandBlue.copy(alpha = 0.15f))
                            ) {
                                Text("Ping", color = BrandBlue, fontSize = 12.sp)
                            }
                        }
                    } else {
                        Text(
                            text = "No profile selected. Tap Config to select one.",
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Real-Time Traffic Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "LIVE TRAFFIC",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Download", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Text(
                                formatSpeed(stats.rxSpeedBps),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusGreen
                            )
                            Text(
                                "Total: ${formatBytes(stats.rxBytes)}",
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Upload", fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            Text(
                                formatSpeed(stats.txSpeedBps),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandBlue
                            )
                            Text(
                                "Total: ${formatBytes(stats.txBytes)}",
                                fontSize = 11.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    TrafficGraph(
                        uploadSpeedBps = stats.txSpeedBps,
                        downloadSpeedBps = stats.rxSpeedBps
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec < 1024 -> "$bytesPerSec B/s"
        bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024} KB/s"
        else -> "%.1f MB/s".format(bytesPerSec.toFloat() / (1024 * 1024))
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.2f GB".format(bytes.toFloat() / (1024 * 1024 * 1024))
    }
}
