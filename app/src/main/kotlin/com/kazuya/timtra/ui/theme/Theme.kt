package com.kazuya.timtra.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.kazuya.timtra.core.journey.JourneyStatus

/** ステータスの色分け（CLAUDE.md 6: 緑 / オレンジ / 赤）。 */
object StatusColors {
    val ok = Color(0xFF2E7D32)
    val tight = Color(0xFFEF6C00)
    val risk = Color(0xFFC62828)
    val missed = Color(0xFF616161)

    fun of(status: JourneyStatus): Color =
        when (status) {
            JourneyStatus.OK -> ok
            JourneyStatus.TIGHT -> tight
            JourneyStatus.RISK -> risk
            JourneyStatus.MISSED -> missed
        }
}

private val lightScheme =
    lightColorScheme(
        primary = Color(0xFF1E5AA8),
        secondary = Color(0xFF4F6079),
        tertiary = Color(0xFF6B5B95),
    )

private val darkScheme =
    darkColorScheme(
        primary = Color(0xFFA9C7FF),
        secondary = Color(0xFFB7C8E3),
        tertiary = Color(0xFFD0BCFF),
    )

@Composable
fun TimTraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkTheme -> darkScheme
            else -> lightScheme
        }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
