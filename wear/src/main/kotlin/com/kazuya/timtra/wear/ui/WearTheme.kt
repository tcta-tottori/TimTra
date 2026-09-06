package com.kazuya.timtra.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

private val colors =
    Colors(
        primary = Color(0xFF78C5FA),
        primaryVariant = Color(0xFF2E8BF5),
        secondary = Color(0xFFA9C7FF),
        background = Color.Black,
        surface = Color(0xFF14307F),
        onPrimary = Color.Black,
        onSurface = Color.White,
        onBackground = Color.White,
    )

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = colors, content = content)
}
