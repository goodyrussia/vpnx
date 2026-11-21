package com.vpnx.dope.ui.screens.config.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vpnx.dope.data.entity.ProfileEntity
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun SshEditorSection(
    state: ProfileEntity,
    onStateChange: (ProfileEntity) -> Unit
) {
    val modes = listOf("DIRECT", "PROXY", "TLS", "PROXY_TLS")
    val selectedModeIdx = modes.indexOf(state.sshMode).let { if (it >= 0) it else 0 }.coerceIn(modes.indices)

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

        OverlayDropdownPreference(
            items = listOf("Direct TCP", "HTTP Proxy", "TLS (SNI)", "HTTP Proxy + TLS"),
            selectedIndex = selectedModeIdx,
            title = "SSH Mode",
            renderInRootScaffold = false,
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
                value = state.proxyPort.toString(),
                onValueChange = { onStateChange(state.copy(proxyPort = it.toIntOrNull() ?: 8080)) },
                label = "Proxy Port",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
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
