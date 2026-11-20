package com.vpnx.dope.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vpnx.dope.data.ConnectionMode
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun HomeScreen(mode: ConnectionMode) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Not connected")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when (mode) {
                        ConnectionMode.ROOT_TUN2SOCKS -> "Mode: TUN2SOCKS (ROOT)"
                        ConnectionMode.NORMAL_VPN_SERVICE -> "Mode: Normal (VpnService)"
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "No config selected yet. Import one in Configs.")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = { /* Engine wiring lands in Phase 1. */ },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Connect")
        }
    }
}
