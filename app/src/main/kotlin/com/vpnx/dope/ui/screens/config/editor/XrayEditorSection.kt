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
fun XrayEditorSection(
    state: ProfileEntity,
    onStateChange: (ProfileEntity) -> Unit
) {
    val protocols = listOf("VLESS", "VMESS", "TROJAN", "SHADOWSOCKS", "HYSTERIA2")
    val selectedProtoIdx = protocols.indexOf(state.xrayProtocol).coerceAtLeast(0)

    val transports = listOf("tcp", "ws", "grpc", "httpupgrade")
    val selectedTransportIdx = transports.indexOf(state.transport.lowercase()).coerceAtLeast(0)

    val securities = listOf("none", "tls", "reality")
    val selectedSecurityIdx = securities.indexOf(state.security.lowercase()).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxWidth()) {
        OverlayDropdownPreference(
            items = protocols,
            selectedIndex = selectedProtoIdx,
            title = "Protocol",
            onSelectedIndexChange = { idx ->
                onStateChange(state.copy(xrayProtocol = protocols[idx]))
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (state.xrayProtocol) {
            "VLESS", "VMESS" -> {
                TextField(
                    value = state.uuid,
                    onValueChange = { onStateChange(state.copy(uuid = it)) },
                    label = "UUID",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (state.xrayProtocol == "VLESS") {
                    TextField(
                        value = state.flow,
                        onValueChange = { onStateChange(state.copy(flow = it)) },
                        label = "Flow (e.g. xtls-rprx-vision or blank)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            "TROJAN" -> {
                TextField(
                    value = state.trojanPassword,
                    onValueChange = { onStateChange(state.copy(trojanPassword = it)) },
                    label = "Password",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            "SHADOWSOCKS" -> {
                TextField(
                    value = state.ssPassword,
                    onValueChange = { onStateChange(state.copy(ssPassword = it)) },
                    label = "Password",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = state.ssMethod,
                    onValueChange = { onStateChange(state.copy(ssMethod = it)) },
                    label = "Cipher (e.g. 2022-blake3-aes-128-gcm)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            "HYSTERIA2" -> {
                TextField(
                    value = state.hy2Auth,
                    onValueChange = { onStateChange(state.copy(hy2Auth = it)) },
                    label = "Auth / Password",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                TextField(
                    value = state.hy2Ports,
                    onValueChange = { onStateChange(state.copy(hy2Ports = it)) },
                    label = "Port Hopping (mport)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        OverlayDropdownPreference(
            items = listOf("None", "TLS", "REALITY"),
            selectedIndex = selectedSecurityIdx,
            title = "Security",
            onSelectedIndexChange = { idx ->
                onStateChange(state.copy(security = securities[idx]))
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (state.security != "none") {
            TextField(
                value = state.sni,
                onValueChange = { onStateChange(state.copy(sni = it)) },
                label = "SNI (Server Name)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            SwitchPreference(
                checked = state.allowInsecure,
                onCheckedChange = { onStateChange(state.copy(allowInsecure = it)) },
                title = "Allow Insecure Certificates",
                summary = "Skip TLS cert validation (required for SNI bug hosts)"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OverlayDropdownPreference(
            items = listOf("TCP", "WebSocket (ws)", "gRPC", "HTTPUpgrade"),
            selectedIndex = selectedTransportIdx,
            title = "Transport Network",
            onSelectedIndexChange = { idx ->
                onStateChange(state.copy(transport = transports[idx]))
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (state.transport.equals("ws", ignoreCase = true)) {
            TextField(
                value = state.wsPath,
                onValueChange = { onStateChange(state.copy(wsPath = it)) },
                label = "WebSocket Path",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            TextField(
                value = state.wsHost,
                onValueChange = { onStateChange(state.copy(wsHost = it)) },
                label = "WebSocket Host Header",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
