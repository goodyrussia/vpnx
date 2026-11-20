package com.vpnx.dope.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Placeholder navigation icons drawn in code (simple solid shapes).
 * They get replaced by the real Miuix icon set in the polish phase;
 * drawing them here keeps Phase 0 free of icon-library guesswork.
 */
private fun navIcon(name: String, draw: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(draw).build()

val IconHome: ImageVector = navIcon("Home") {
    path(fill = SolidColor(Color.White)) {
        moveTo(12f, 3f)
        lineTo(22f, 12f)
        lineTo(19f, 12f)
        lineTo(19f, 21f)
        lineTo(14f, 21f)
        lineTo(14f, 15f)
        lineTo(10f, 15f)
        lineTo(10f, 21f)
        lineTo(5f, 21f)
        lineTo(5f, 12f)
        lineTo(2f, 12f)
        close()
    }
}

val IconConfigs: ImageVector = navIcon("Configs") {
    path(fill = SolidColor(Color.White)) {
        moveTo(4f, 5f)
        lineTo(20f, 5f)
        lineTo(20f, 7.5f)
        lineTo(4f, 7.5f)
        close()
        moveTo(4f, 10.75f)
        lineTo(20f, 10.75f)
        lineTo(20f, 13.25f)
        lineTo(4f, 13.25f)
        close()
        moveTo(4f, 16.5f)
        lineTo(20f, 16.5f)
        lineTo(20f, 19f)
        lineTo(4f, 19f)
        close()
    }
}

val IconLogs: ImageVector = navIcon("Logs") {
    path(fill = SolidColor(Color.White)) {
        moveTo(6f, 2f)
        lineTo(18f, 2f)
        lineTo(18f, 22f)
        lineTo(6f, 22f)
        close()
    }
}

val IconSettings: ImageVector = navIcon("Settings") {
    path(fill = SolidColor(Color.White)) {
        moveTo(3f, 7f)
        lineTo(21f, 7f)
        lineTo(21f, 9f)
        lineTo(3f, 9f)
        close()
        moveTo(3f, 15f)
        lineTo(21f, 15f)
        lineTo(21f, 17f)
        lineTo(3f, 17f)
        close()
        moveTo(15f, 5f)
        lineTo(18f, 5f)
        lineTo(18f, 11f)
        lineTo(15f, 11f)
        close()
        moveTo(6f, 13f)
        lineTo(9f, 13f)
        lineTo(9f, 19f)
        lineTo(6f, 19f)
        close()
    }
}
