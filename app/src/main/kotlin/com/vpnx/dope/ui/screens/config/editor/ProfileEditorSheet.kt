package com.vpnx.dope.ui.screens.config.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpnx.dope.data.entity.ProfileEntity
import com.vpnx.dope.ui.components.VpnxDropdownPreference
import com.vpnx.dope.ui.theme.BrandBlue
import com.vpnx.dope.ui.theme.StatusRed
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar

private val ProfileEntitySaver = listSaver<ProfileEntity, Any>(
    save = { p ->
        listOf(
            p.id,
            p.name,
            p.protocolType,
            p.server,
            p.port,
            p.isSelected,
            p.sshUser,
            p.sshPass,
            p.sshMode,
            p.payloadEnabled,
            p.payload,
            p.sni,
            p.proxyHost,
            p.proxyPort,
            p.proxyAuth,
            p.proxyUser,
            p.proxyPass,
            p.tlsVersion,
            p.xrayProtocol,
            p.uuid,
            p.flow,
            p.security,
            p.fingerprint,
            p.alpn,
            p.allowInsecure,
            p.realityPublicKey,
            p.realityShortId,
            p.realitySpiderX,
            p.transport,
            p.wsPath,
            p.wsHost,
            p.grpcServiceName,
            p.httpUpgradePath,
            p.httpUpgradeHost,
            p.ssMethod,
            p.ssPassword,
            p.trojanPassword,
            p.hy2Auth,
            p.hy2UpBandwidth,
            p.hy2DownBandwidth,
            p.hy2Ports,
            p.hy2ObfsType,
            p.hy2ObfsPassword,
            p.hy2PinSha256,
            p.hy2HopInterval,
            p.lastPingMs,
            p.createdAt
        )
    },
    restore = { list ->
        ProfileEntity(
            id = list[0] as String,
            name = list[1] as String,
            protocolType = list[2] as String,
            server = list[3] as String,
            port = list[4] as Int,
            isSelected = list[5] as Boolean,
            sshUser = list[6] as String,
            sshPass = list[7] as String,
            sshMode = list[8] as String,
            payloadEnabled = list[9] as Boolean,
            payload = list[10] as String,
            sni = list[11] as String,
            proxyHost = list[12] as String,
            proxyPort = list[13] as Int,
            proxyAuth = list[14] as Boolean,
            proxyUser = list[15] as String,
            proxyPass = list[16] as String,
            tlsVersion = list[17] as String,
            xrayProtocol = list[18] as String,
            uuid = list[19] as String,
            flow = list[20] as String,
            security = list[21] as String,
            fingerprint = list[22] as String,
            alpn = list[23] as String,
            allowInsecure = list[24] as Boolean,
            realityPublicKey = list[25] as String,
            realityShortId = list[26] as String,
            realitySpiderX = list[27] as String,
            transport = list[28] as String,
            wsPath = list[29] as String,
            wsHost = list[30] as String,
            grpcServiceName = list[31] as String,
            httpUpgradePath = list[32] as String,
            httpUpgradeHost = list[33] as String,
            ssMethod = list[34] as String,
            ssPassword = list[35] as String,
            trojanPassword = list[36] as String,
            hy2Auth = list[37] as String,
            hy2UpBandwidth = list[38] as Int,
            hy2DownBandwidth = list[39] as Int,
            hy2Ports = list[40] as String,
            hy2ObfsType = list[41] as String,
            hy2ObfsPassword = list[42] as String,
            hy2PinSha256 = list[43] as String,
            hy2HopInterval = list[44] as String,
            lastPingMs = list[45] as Long,
            createdAt = list[46] as Long
        )
    }
)

@Composable
fun ProfileEditorSheet(
    initialProfile: ProfileEntity?,
    onSave: (ProfileEntity) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    var state by rememberSaveable(initialProfile?.id, stateSaver = ProfileEntitySaver) {
        mutableStateOf(
            initialProfile ?: ProfileEntity(
                name = "New Profile",
                protocolType = "XRAY",
                xrayProtocol = "VLESS",
                server = "",
                port = 443
            )
        )
    }
    var portText by rememberSaveable(initialProfile?.id) {
        mutableStateOf(if (state.port in 1..65535) state.port.toString() else "443")
    }
    var validationError by rememberSaveable(initialProfile?.id) {
        mutableStateOf<String?>(null)
    }

    val parsedPort = portText.trim().toIntOrNull()
    val portInlineError = if (parsedPort == null || parsedPort !in 1..65535) {
        "Port must be between 1 and 65535"
    } else {
        null
    }

    val typeOptions = listOf("Xray", "SSH")
    val selectedTypeIdx = (if (state.protocolType == "SSH") 1 else 0).coerceIn(typeOptions.indices)

    fun validateProfile(p: ProfileEntity): String? {
        if (p.server.isBlank()) return "Server host is required."
        if (p.port !in 1..65535) return "Server port must be between 1 and 65535."

        if (p.protocolType == "SSH") {
            if (p.sshUser.isBlank()) return "SSH username is required."
            if ((p.sshMode == "PROXY" || p.sshMode == "PROXY_TLS") && p.proxyHost.isBlank()) {
                return "Proxy host is required for ${p.sshMode} mode."
            }
            if ((p.sshMode == "PROXY" || p.sshMode == "PROXY_TLS") && p.proxyPort !in 1..65535) {
                return "Proxy port must be between 1 and 65535."
            }
        } else {
            when (p.xrayProtocol.uppercase()) {
                "VLESS", "VMESS" -> if (p.uuid.isBlank()) return "UUID is required for ${p.xrayProtocol}."
                "TROJAN" -> if (p.trojanPassword.isBlank()) return "Password is required for Trojan."
                "SHADOWSOCKS" -> if (p.ssPassword.isBlank()) return "Password is required for Shadowsocks."
                "HYSTERIA2" -> if (p.hy2Auth.isBlank()) return "Auth / password is required for Hysteria2."
            }
            if (
                p.xrayProtocol.uppercase() !in setOf("HYSTERIA2", "SHADOWSOCKS") &&
                p.security.equals("reality", ignoreCase = true) &&
                p.realityPublicKey.isBlank()
            ) {
                return "Reality public key (pbk) is required when security is REALITY."
            }
        }
        return null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (initialProfile == null) "Add Profile" else "Edit Profile",
                actions = {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(color = StatusRed.copy(alpha = 0.14f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel", color = StatusRed, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            val candidate = state.copy(port = parsedPort ?: -1)
                            val err = validateProfile(candidate)
                            if (err != null) {
                                validationError = err
                            } else {
                                validationError = null
                                onSave(candidate.copy(server = candidate.server.trim()))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(color = BrandBlue),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
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

            if (validationError != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = validationError!!,
                        color = StatusRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    TextField(
                        value = state.name,
                        onValueChange = {
                            state = state.copy(name = it)
                            validationError = null
                        },
                        label = "Profile Name (Remarks)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    VpnxDropdownPreference(
                        items = typeOptions,
                        selectedIndex = selectedTypeIdx,
                        title = "Core Type",
                        onSelectedIndexChange = { idx ->
                            val safeIdx = idx.coerceIn(typeOptions.indices)
                            state = state.copy(protocolType = if (safeIdx == 1) "SSH" else "XRAY")
                            validationError = null
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = state.server,
                        onValueChange = {
                            state = state.copy(server = it)
                            validationError = null
                        },
                        label = "Server Host / IP",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    TextField(
                        value = portText,
                        onValueChange = { raw ->
                            portText = raw
                            state = state.copy(port = raw.trim().toIntOrNull() ?: -1)
                            validationError = null
                        },
                        label = "Server Port (1–65535)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (portInlineError != null) {
                        Text(
                            text = portInlineError,
                            color = StatusRed,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    if (state.protocolType == "SSH") {
                        SshEditorSection(
                            state = state,
                            onStateChange = {
                                state = it
                                validationError = null
                            }
                        )
                    } else {
                        XrayEditorSection(
                            state = state,
                            onStateChange = {
                                state = it
                                validationError = null
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}