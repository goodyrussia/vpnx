package com.vpnx.dope.ui.screens.config.editor

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpnx.dope.data.entity.ProfileEntity
import com.vpnx.dope.ui.theme.BrandBlue
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun ProfileEditorSheet(
    initialProfile: ProfileEntity?,
    onSave: (ProfileEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var state by remember {
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

    val typeOptions = listOf("Xray", "SSH")
    val selectedTypeIdx = if (state.protocolType == "SSH") 1 else 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (initialProfile == null) "Add Profile" else "Edit Profile",
                actions = {
                    Button(
                        onClick = {
                            if (state.server.isNotBlank()) {
                                onSave(state)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(color = BrandBlue),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text("Save", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold)
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    TextField(
                        value = state.name,
                        onValueChange = { state = state.copy(name = it) },
                        label = "Profile Name (Remarks)",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OverlayDropdownPreference(
                        items = typeOptions,
                        selectedIndex = selectedTypeIdx,
                        title = "Core Type",
                        onSelectedIndexChange = { idx ->
                            state = state.copy(protocolType = if (idx == 1) "SSH" else "XRAY")
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = state.server,
                        onValueChange = { state = state.copy(server = it) },
                        label = "Server Host / IP",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    TextField(
                        value = state.port.toString(),
                        onValueChange = { state = state.copy(port = it.toIntOrNull() ?: 443) },
                        label = "Server Port",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    if (state.protocolType == "SSH") {
                        SshEditorSection(state = state, onStateChange = { state = it })
                    } else {
                        XrayEditorSection(state = state, onStateChange = { state = it })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
