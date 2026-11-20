package com.vpnx.dope.ui.screens.logs

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun LogsScreen(
    viewModel: LogsViewModel
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val logs by viewModel.filteredLogs.collectAsState()
    val filter by viewModel.selectedFilter.collectAsState()

    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }

    LaunchedEffect(logs.size) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "Live Logs",
                actions = {
                    Button(
                        onClick = {
                            val text = logs.joinToString("\n")
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(color = BrandBlue.copy(alpha = 0.15f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Copy", color = BrandBlue, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { viewModel.clearLogs() },
                        colors = ButtonDefaults.buttonColors(color = StatusRed.copy(alpha = 0.15f)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text("Clear", color = StatusRed, fontSize = 12.sp)
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
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "INFO", "WARN", "ERROR").forEach { opt ->
                    val isSelected = filter == opt
                    Button(
                        onClick = { viewModel.setFilter(opt) },
                        colors = ButtonDefaults.buttonColors(
                            color = if (isSelected) BrandBlue else BrandBlue.copy(alpha = 0.12f)
                        )
                    ) {
                        Text(
                            opt,
                            color = if (isSelected) Color.White else BrandBlue,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Console Box
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141414))
                        .padding(10.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(logs) { line ->
                            val color = when {
                                line.contains("[ERROR]") -> StatusRed
                                line.contains("[WARN]") -> StatusAmber
                                line.contains("[INFO]") -> Color(0xFF64D2FF)
                                else -> Color(0xFFAAAAAA)
                            }
                            Text(
                                text = line,
                                color = color,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
