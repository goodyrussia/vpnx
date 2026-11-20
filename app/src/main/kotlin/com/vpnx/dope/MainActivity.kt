package com.vpnx.dope

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.vpnx.dope.ui.VpnxTheme
import top.yukonga.miuix.kmp.basic.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VpnxTheme {
                Text(text = "VPNX")
            }
        }
    }
}
