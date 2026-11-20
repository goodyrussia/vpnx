package com.vpnx.dope.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpnx.dope.data.model.ConnectionState
import com.vpnx.dope.ui.theme.StatusAmber
import com.vpnx.dope.ui.theme.StatusGreen
import com.vpnx.dope.ui.theme.StatusRed
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun StatusBadge(
    state: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (dotColor, label, bgAlpha) = when (state) {
        ConnectionState.CONNECTED -> Triple(StatusGreen, "CONNECTED", 0.15f)
        ConnectionState.CONNECTING -> Triple(StatusAmber, "CONNECTING", 0.15f)
        ConnectionState.DISCONNECTING -> Triple(StatusAmber, "DISCONNECTING", 0.15f)
        ConnectionState.DISCONNECTED -> Triple(Color.Gray, "DISCONNECTED", 0.12f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(dotColor.copy(alpha = bgAlpha))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = dotColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
