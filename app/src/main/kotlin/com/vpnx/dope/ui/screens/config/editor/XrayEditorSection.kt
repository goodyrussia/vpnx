package com.vpnx.dope.ui.screens.config.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vpnx.dope.data.entity.ProfileEntity
import com.vpnx.dope.ui.components.VpnxDropdownPreference
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun XrayEditorSection(
    state: ProfileEntity,
    onStateChange: (ProfileEntity) -> Unit
) {
    val protocols = listOf("VLESS", "VMESS", "TROJAN", "SHADOWSOCKS", "HYSTERIA2")
    val selectedProtoIdx = protocols.indexOf(state.xrayProtocol).let { if (it >= 0) it else 0 }.coerceIn(protocols.indices)

    val transports = listOf("tcp", "ws", "grpc", "httpupgrade")
    val selectedTransportIdx = transports.indexOf(state.transport.lowercase()).let { if (it >= 0) it else 0 }.coerceIn(transports.indices)

    val securities = listOf("none", "tls", "reality")
    val selectedSecurityIdx = securities.indexOf(state.security.lowercase()).let { if (it >= 0) it else 0 }.coerceIn(securities.indices)

    Column(modifier = Modifier.fillMaxWidth()) {
        VpnxDropdownPreference(
            items = protocols,
            selectedIndex = selectedProtoIdx,
            title = "Protocol",
            onSelectedIndexChange = { idx ->
                val safeIdx = idx.coerceIn(protocols.indices)
                onStateChange(state.copy(xrayProtocol = protocols[safeIdx]))
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
                    value = state.hy2ObfsType,
                    onValueChange = { onStateChange(state.copy(hy2ObfsType = it)) },
                    label = "Obfs Type (e.g. salamander or blank)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (state.hy2ObfsType.isNotBlank()) {
                    TextField(
                        value = state.hy2ObfsPassword,
                        onValueChange = { onStateChange(state.copy(hy2ObfsPassword = it)) },
                        label = "Obfs Password",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                TextField(
                    value = state.hy2Ports,
                    onValueChange = { onStateChange(state.copy(hy2Ports = it)) },
                    label = "Port Hopping (mport, e.g. 20000-50000)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (state.hy2Ports.isNotBlank()) {
                    TextField(
                        value = state.hy2HopInterval,
                        onValueChange = { onStateChange(state.copy(hy2HopInterval = it)) },
                        label = "Hop Interval Seconds (default 30)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                TextField(
                    value = state.hy2PinSha256,
                    onValueChange = { onStateChange(state.copy(hy2PinSha256 = it)) },
                    label = "Pinned Cert SHA-256 (optional)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        VpnxDropdownPreference(
            items = listOf("None", "TLS", "REALITY"),
            selectedIndex = selectedSecurityIdx,
            title = "Security",
            onSelectedIndexChange = { idx ->
                val safeIdx = idx.coerceIn(securities.indices)
                onStateChange(state.copy(security = securities[safeIdx]))
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (state.security.equals("tls", ignoreCase = true)) {
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
        } else if (state.security.equals("reality", ignoreCase = true)) {
            TextField(
                value = state.sni,
                onValueChange = { onStateChange(state.copy(sni = it)) },
                label = "SNI (Server Name)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            TextField(
                value = state.fingerprint,
                onValueChange = { onStateChange(state.copy(fingerprint = it)) },
                label = "Fingerprint (e.g. chrome)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            TextField(
                value = state.realityPublicKey,
                onValueChange = { onStateChange(state.copy(realityPublicKey = it)) },
                label = "Reality Public Key (pbk)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            TextField(
                value = state.realityShortId,
                onValueChange = { onStateChange(state.copy(realityShortId = it)) },
                label = "Reality Short ID (sid)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))

            TextField(
                value = state.realitySpiderX,
                onValueChange = { onStateChange(state.copy(realitySpiderX = it)) },
                label = "Reality SpiderX (spx, optional)",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        VpnxDropdownPreference(
            items = listOf("TCP", "WebSocket (ws)", "gRPC", "HTTPUpgrade"),
            selectedIndex = selectedTransportIdx,
            title = "Transport Network",
            onSelectedIndexChange = { idx ->
                val safeIdx = idx.coerceIn(transports.indices)
                onStateChange(state.copy(transport = transports[safeIdx]))
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        when (state.transport.lowercase()) {
            "ws" -> {
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
            "grpc" -> {
                TextField(
                    value = state.grpcServiceName,
                    onValueChange = { onStateChange(state.copy(grpcServiceName = it)) },
                    label = "gRPC Service Name",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            "httpupgrade" -> {
                TextField(
                    value = state.httpUpgradePath,
                    onValueChange = { onStateChange(state.copy(httpUpgradePath = it)) },
                    label = "HTTPUpgrade Path (default /)",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                TextField(
                    value = state.httpUpgradeHost,
                    onValueChange = { onStateChange(state.copy(httpUpgradeHost = it)) },
                    label = "HTTPUpgrade Host Header",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}