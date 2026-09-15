package com.kazuya.timtra.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/**
 * 時計の配色。文字盤に馴染むよう黒地を基調にし、上に向かって濃紺へ持ち上げる。
 * 青はスマホ版（`app/ui/theme/Theme.kt` の TimTraColors）と同じ値を使う。
 */
object WearColors {
    /** グラデーションの明側（アイコン左上）。 */
    val gradientStart = Color(0xFF2E8BF5)

    /** グラデーションの暗側（アイコン右下）。 */
    val gradientEnd = Color(0xFF14307F)

    /** 「Tra」の水色。強調した数字に使う。 */
    val accentLight = Color(0xFF78C5FA)

    /** カード・ピルの地。黒地から少しだけ浮かせる。 */
    val surface = Color(0xFF0F1B30)

    /** 選択中・強調のカードの地。 */
    val surfaceSelected = Color(0xFF17427F)

    val outline = Color(0xFF1E3355)

    /** カウントダウンのリングの地（残りの部分）。 */
    val track = Color(0xFF39445A)
    val onSurface = Color.White
    val onSurfaceVariant = Color(0xFF9FB4D6)

    /** 地点の下に置く行き先など、白よりわずかに落とした文字。 */
    val onSurfaceSubtle = Color(0xFFCBDAF2)

    /** 時刻表で、現在時刻より前の便に使う灰色。 */
    val onSurfaceDisabled = Color(0xFF61708C)

    /** 画面の地。黒（有機 EL で消灯する）から上だけ濃紺に持ち上げる。 */
    val background: Brush =
        Brush.linearGradient(
            colors = listOf(Color(0xFF0A1730), Color.Black),
            start = Offset.Zero,
            end = Offset(0f, Float.POSITIVE_INFINITY),
        )

    /** 主ボタン・次の便の行に使う青のグラデーション。 */
    val primaryGradient: Brush = Brush.horizontalGradient(listOf(gradientStart, gradientEnd))

    /** カウントダウンのリング（明るい水色 → 青）。 */
    val ringGradient: Brush = Brush.linearGradient(listOf(accentLight, gradientStart))

    /** ホーム下部の発時刻カードの地。 */
    val footerGradient: Brush = Brush.verticalGradient(listOf(Color(0xFF16305C), Color(0xFF0C1B36)))

    /** 大きな数字のグラデーション（白 → 水色）。 */
    val countdownGradient: Brush = Brush.verticalGradient(listOf(Color.White, accentLight))
}

private val colors =
    Colors(
        primary = WearColors.accentLight,
        primaryVariant = WearColors.gradientStart,
        secondary = Color(0xFFA9C7FF),
        background = Color.Black,
        surface = WearColors.surface,
        onPrimary = Color.Black,
        onSurface = WearColors.onSurface,
        onBackground = WearColors.onSurface,
    )

@Composable
fun WearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = colors, content = content)
}
