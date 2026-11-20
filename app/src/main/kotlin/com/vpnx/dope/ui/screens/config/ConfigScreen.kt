package com.vpnx.dope.ui.screens.config

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpnx.dope.data.entity.ProfileEntity
import com.vpnx.dope.ui.components.LatencyBadge
import com.vpnx.dope.ui.screens.config.editor.ProfileEditorSheet
import com.vpnx.dope.ui.theme.BrandBlue
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
fun ConfigScreen(
    viewModel: ConfigViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val profiles by viewModel.profiles.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()

    var editingProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    if (isAddingNew || editingProfile != null) {
        ProfileEditorSheet(
            initialProfile = editingProfile,
            onSave = { profile ->
                viewModel.saveProfile(profile)
                isAddingNew = false
                editingProfile = null
            },
            onDismiss = {
                isAddingNew = false
                editingProfile = null
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "Profiles",
                actions = {
                    Button(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text.orEmpty()
                            if (clipText.isNotBlank()) {
                                viewModel.importUri(clipText) { success, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(color = BrandBlue.copy(alpha = 0.15f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Import", color = BrandBlue, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { isAddingNew = true },
                        colors = ButtonDefaults.buttonColors(color = BrandBlue),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text("+ Add", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        if (profiles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No profiles found.",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { isAddingNew = true }) {
                    Text("Add Your First Profile")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { Spacer(modifier = Modifier.height(6.dp)) }

                items(profiles, key = { it.id }) { p ->
                    val isSelected = selectedProfile?.id == p.id
                    val protoTag = if (p.protocolType == "SSH") "SSH (${p.sshMode})" else "XRAY (${p.xrayProtocol})"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectProfile(p.id) }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelected) {
                                        Text(
                                            text = "●",
                                            color = StatusGreen,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                    }
                                    Text(
                                        text = p.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) BrandBlue else MiuixTheme.colorScheme.onSurface
                                    )
                                }
                                LatencyBadge(pingMs = p.lastPingMs)
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${p.server}:${p.port} • $protoTag",
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { viewModel.pingProfile(p) },
                                    colors = ButtonDefaults.buttonColors(color = BrandBlue.copy(alpha = 0.12f))
                                ) {
                                    Text("Ping", fontSize = 11.sp, color = BrandBlue)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { editingProfile = p },
                                    colors = ButtonDefaults.buttonColors(color = BrandBlue.copy(alpha = 0.12f))
                                ) {
                                    Text("Edit", fontSize = 11.sp, color = BrandBlue)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { viewModel.deleteProfile(p.id) },
                                    colors = ButtonDefaults.buttonColors(color = StatusRed.copy(alpha = 0.12f))
                                ) {
                                    Text("Delete", fontSize = 11.sp, color = StatusRed)
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}
