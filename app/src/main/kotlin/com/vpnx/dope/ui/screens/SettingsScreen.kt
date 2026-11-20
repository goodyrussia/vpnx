package com.vpnx.dope.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vpnx.dope.data.ConnectionMode
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun SettingsScreen(mode: ConnectionMode, onModeChange: (ConnectionMode) -> Unit) {
    var startOnBoot by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(text = "Connection mode")
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "TUN2SOCKS (ROOT)")
                        Text(text = "Default. Needs a rooted phone.")
                    }
                    Switch(
                        checked = mode == ConnectionMode.ROOT_TUN2SOCKS,
                        onCheckedChange = { root ->
                            onModeChange(
                                if (root) {
                                    ConnectionMode.ROOT_TUN2SOCKS
                                } else {
                                    ConnectionMode.NORMAL_VPN_SERVICE
                                },
                            )
                        },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = when (mode) {
                        ConnectionMode.ROOT_TUN2SOCKS ->
                            "Traffic: apps -> TUN (hev, as root) -> engine SOCKS5 -> server."
                        ConnectionMode.NORMAL_VPN_SERVICE ->
                            "Traffic: apps -> TUN (VpnService) -> engine SOCKS5 -> server."
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "General")
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Start on boot")
                    Text(text = "Reconnect the last used config after restart.")
                }
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = { startOnBoot = it },
                )
            }
        }
    }
}
