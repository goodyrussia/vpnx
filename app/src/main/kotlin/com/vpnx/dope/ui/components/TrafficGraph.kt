package com.vpnx.dope.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.vpnx.dope.ui.theme.BrandBlue
import com.vpnx.dope.ui.theme.StatusGreen

@Composable
fun TrafficGraph(
    uploadSpeedBps: Long,
    downloadSpeedBps: Long,
    modifier: Modifier = Modifier
) {
    val history = remember { ArrayDeque<Pair<Float, Float>>() }
    val maxPoints = 30

    val maxSpeed = 10_000_000f // 10 MB/s scaling reference
    val upRatio = (uploadSpeedBps.toFloat() / maxSpeed).coerceIn(0.05f, 1f)
    val downRatio = (downloadSpeedBps.toFloat() / maxSpeed).coerceIn(0.05f, 1f)

    if (history.size >= maxPoints) {
        history.removeFirst()
    }
    history.addLast(upRatio to downRatio)

    Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
        val w = size.width
        val h = size.height
        val step = if (history.size > 1) w / (maxPoints - 1) else w

        val downPath = Path()
        val upPath = Path()

        history.forEachIndexed { i, (up, down) ->
            val x = i * step
            val yDown = h - (down * h * 0.85f)
            val yUp = h - (up * h * 0.85f)

            if (i == 0) {
                downPath.moveTo(x, yDown)
                upPath.moveTo(x, yUp)
            } else {
                downPath.lineTo(x, yDown)
                upPath.lineTo(x, yUp)
            }
        }

        // Draw download line (Green)
        drawPath(
            path = downPath,
            color = StatusGreen.copy(alpha = 0.8f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw upload line (Blue)
        drawPath(
            path = upPath,
            color = BrandBlue.copy(alpha = 0.8f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
