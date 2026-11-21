package com.vpnx.dope.ui.screens.config.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpnx.dope.data.entity.ProfileEntity
import com.vpnx.dope.ui.components.VpnxDropdownPreference
import com.vpnx.dope.ui.theme.StatusRed
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SshEditorSection(
    state: ProfileEntity,
    onStateChange: (ProfileEntity) -> Unit
) {
    val modes = listOf("DIRECT", "PROXY", "TLS", "PROXY_TLS")
    val selectedModeIdx = modes.indexOf(state.sshMode).let { if (it >= 0) it else 0 }.coerceIn(modes.indices)

    var proxyPortText by rememberSaveable(state.id) {
        mutableStateOf(if (state.proxyPort in 1..65535) state.proxyPort.toString() else "8080")
    }
    val parsedProxyPort = proxyPortText.trim().toIntOrNull()
    val proxyPortError = if (parsedProxyPort == null || parsedProxyPort !in 1..65535) {
        "Proxy port must be between 1 and 65535"
    } else {
        null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = state.sshUser,
            onValueChange = { onStateChange(state.copy(sshUser = it)) },
            label = "SSH Username",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        TextField(
            value = state.sshPass,
            onValueChange = { onStateChange(state.copy(sshPass = it)) },
            label = "SSH Password",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        VpnxDropdownPreference(
            items = listOf("Direct TCP", "HTTP Proxy", "TLS (SNI)", "HTTP Proxy + TLS"),
            selectedIndex = selectedModeIdx,
            title = "SSH Mode",
            onSelectedIndexChange = { idx ->
                val safeIdx = idx.coerceIn(modes.indices)
                onStateChange(state.copy(sshMode = modes[safeIdx]))
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (state.sshMode == "PROXY" || state.sshMode == "PROXY_TLS") {
            TextField(
                value = state.proxyHost,
                onValueChange = { onStateChange(state.copy(proxyHost = it)) },
                label = "Proxy Host",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            TextField(
                value = proxyPortText,
                onValueChange = { raw ->
                    proxyPortText = raw
                    onStateChange(state.copy(proxyPort = raw.trim().toIntOrNull() ?: -1))
                },
                label = "Proxy Port (1–65535)",
                modifier = Modifier.fillMaxWidth()
            )
            if (proxyPortError != null) {
                Text(
                    text = proxyPortError,
                    color = StatusRed,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            SwitchPreference(
                checked = state.proxyAuth,
                onCheckedChange = { onStateChange(state.copy(proxyAuth = it)) },
                title = "Proxy Authentication",
                summary = "Send Proxy-Authorization Basic credentials"
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (state.proxyAuth) {
                TextField(
                    value = state.proxyUser,
                    onValueChange = { onStateChange(state.copy(proxyUser = it)) },
                    label = "Proxy Username",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = state.proxyPass,
                    onValueChange = { onStateChange(state.copy(proxyPass = it)) },
                    label = "Proxy Password",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        if (state.sshMode == "TLS" || state.sshMode == "PROXY_TLS") {
            TextField(
                value = state.sni,
                onValueChange = { onStateChange(state.copy(sni = it)) },
                label = "TLS SNI Host",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        SwitchPreference(
            checked = state.payloadEnabled,
            onCheckedChange = { onStateChange(state.copy(payloadEnabled = it)) },
            title = "HTTP Payload Injection",
            summary = "Enable HTTP headers / WebSocket upgrade before SSH handshake"
        )

        if (state.payloadEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            TextField(
                value = state.payload,
                onValueChange = { onStateChange(state.copy(payload = it)) },
                label = "Payload Template ([host], [port], [sni], [crlf])",
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}