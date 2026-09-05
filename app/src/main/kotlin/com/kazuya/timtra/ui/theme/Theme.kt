package com.kazuya.timtra.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kazuya.timtra.core.journey.JourneyStatus

/**
 * 配色。濃紺のグラデーションを地にした固定ダークテーマ（端末のダイナミックカラーは使わない）。
 * アプリアイコンの紺とオレンジに揃える。
 */
object TimTraColors {
    val backgroundTop = Color(0xFF171C38)
    val backgroundBottom = Color(0xFF080A15)
    val surface = Color(0xFF1B2041)
    val surfaceElevated = Color(0xFF242B52)
    val outline = Color(0xFF2F3763)
    val primary = Color(0xFFFFA028)
    val onSurface = Color(0xFFF3F5FF)
    val onSurfaceVariant = Color(0xFF9AA3C9)
    val pillFill = Color.White.copy(alpha = 0.10f)
    val pillBorder = Color.White.copy(alpha = 0.28f)
}

/** ステータスの色分け（CLAUDE.md 6: 緑 / オレンジ / 赤）。 */
object StatusColors {
    val ok = Color(0xFF34C759)
    val tight = Color(0xFFFF9F0A)
    val risk = Color(0xFFFF453A)
    val missed = Color(0xFF8E8E93)

    fun of(status: JourneyStatus): Color =
        when (status) {
            JourneyStatus.OK -> ok
            JourneyStatus.TIGHT -> tight
            JourneyStatus.RISK -> risk
            JourneyStatus.MISSED -> missed
        }
}

private val scheme =
    darkColorScheme(
        primary = TimTraColors.primary,
        onPrimary = Color(0xFF1B1200),
        primaryContainer = Color(0xFF3A2A0C),
        onPrimaryContainer = Color(0xFFFFDDB0),
        secondary = Color(0xFFA9C7FF),
        onSecondary = Color(0xFF0B1F4A),
        secondaryContainer = Color(0xFF26305A),
        onSecondaryContainer = TimTraColors.onSurface,
        tertiary = Color(0xFFD0BCFF),
        tertiaryContainer = Color(0xFF2E2A55),
        onTertiaryContainer = TimTraColors.onSurface,
        background = TimTraColors.backgroundBottom,
        onBackground = TimTraColors.onSurface,
        surface = TimTraColors.surface,
        onSurface = TimTraColors.onSurface,
        surfaceVariant = TimTraColors.surfaceElevated,
        onSurfaceVariant = TimTraColors.onSurfaceVariant,
        surfaceContainerLowest = TimTraColors.backgroundBottom,
        surfaceContainerLow = TimTraColors.surface,
        surfaceContainer = TimTraColors.surface,
        surfaceContainerHigh = TimTraColors.surfaceElevated,
        surfaceContainerHighest = TimTraColors.surfaceElevated,
        outline = TimTraColors.outline,
        outlineVariant = Color(0xFF242B4D),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF3B0A10),
        errorContainer = Color(0xFF3B1E2C),
        onErrorContainer = Color(0xFFFFD9DE),
    )

@Composable
fun TimTraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(TimTraColors.backgroundTop, TimTraColors.backgroundBottom))),
        ) {
            content()
        }
    }
}

/** 丸みの大きい半透明のカード。ホーム・設定・About で共通に使う。 */
@Composable
fun TimTraCard(
    modifier: Modifier = Modifier,
    containerColor: Color = TimTraColors.surface,
    borderColor: Color = TimTraColors.outline,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        content = content,
    )
}
