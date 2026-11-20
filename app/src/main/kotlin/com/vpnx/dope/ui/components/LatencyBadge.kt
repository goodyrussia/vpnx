package com.vpnx.dope.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpnx.dope.ui.theme.StatusAmber
import com.vpnx.dope.ui.theme.StatusGreen
import com.vpnx.dope.ui.theme.StatusRed
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun LatencyBadge(
    pingMs: Long,
    modifier: Modifier = Modifier
) {
    val (badgeColor, text) = when {
        pingMs < 0L -> Pair(Color.Gray, "-- ms")
        pingMs < 120L -> Pair(StatusGreen, "${pingMs}ms")
        pingMs < 300L -> Pair(StatusAmber, "${pingMs}ms")
        else -> Pair(StatusRed, "${pingMs}ms")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = badgeColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
