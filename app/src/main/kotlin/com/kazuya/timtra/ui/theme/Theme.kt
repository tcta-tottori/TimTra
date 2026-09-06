package com.kazuya.timtra.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazuya.timtra.core.journey.JourneyStatus

/**
 * 配色。アプリアイコンに合わせ、ヘッダーとメニューは青のグラデーション、本文は白地。
 */
object TimTraColors {
    /** グラデーションの明側（アイコン左上）。 */
    val gradientStart = Color(0xFF2E8BF5)

    /** グラデーションの暗側（アイコン右下）。 */
    val gradientEnd = Color(0xFF14307F)

    /** ボタン・リンク・強調の青。 */
    val primary = Color(0xFF1F5FD6)

    /** 「Tra」の水色。 */
    val accentLight = Color(0xFF78C5FA)

    val background = Color.White
    val surface = Color(0xFFF5F8FF)
    val outline = Color(0xFFD8E1F5)
    val onSurface = Color(0xFF12204A)
    val onSurfaceVariant = Color(0xFF5B6A93)

    /** 青地の上に置く白文字・ピル。 */
    val onGradient = Color.White
    val pillFill = Color.White.copy(alpha = 0.18f)
    val pillBorder = Color.White.copy(alpha = 0.45f)

    /** ヘッダー・メニュー・主要カードに使うグラデーション。 */
    val headerGradient: Brush = Brush.linearGradient(listOf(gradientStart, gradientEnd))
}

/** 交通手段ごとの色。バスは日ノ丸バスを思わせる橙、JR は JR 西日本の青。地図・時刻表・ホームで共通。 */
object TransitColors {
    val bus = Color(0xFFE8590C)
    val jr = Color(0xFF0B72B9)
    val walk = Color(0xFF6B7A99)

    /** 勤務先などの一般の地点。 */
    val place = Color(0xFF6B5B95)

    /** 現在地の青い点。 */
    val here = Color(0xFF1A73E8)

    /** 地図の下地と罫線。 */
    val mapGround = Color(0xFFF2F6FD)
    val mapGrid = Color(0xFFDCE5F5)
}

/** ステータスの色分け（CLAUDE.md 6: 緑 / オレンジ / 赤）。白地で読める濃さ。 */
object StatusColors {
    val ok = Color(0xFF1E9E5A)
    val tight = Color(0xFFE8900B)
    val risk = Color(0xFFE0433C)
    val missed = Color(0xFF8A94AD)

    fun of(status: JourneyStatus): Color =
        when (status) {
            JourneyStatus.OK -> ok
            JourneyStatus.TIGHT -> tight
            JourneyStatus.RISK -> risk
            JourneyStatus.MISSED -> missed
        }
}

private val scheme =
    lightColorScheme(
        primary = TimTraColors.primary,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE3ECFF),
        onPrimaryContainer = Color(0xFF0E2A6E),
        secondary = Color(0xFF4F6BA8),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE8EEFB),
        onSecondaryContainer = TimTraColors.onSurface,
        tertiary = Color(0xFF6B5B95),
        tertiaryContainer = Color(0xFFEDE7FF),
        onTertiaryContainer = Color(0xFF2A1F52),
        background = TimTraColors.background,
        onBackground = TimTraColors.onSurface,
        surface = TimTraColors.background,
        onSurface = TimTraColors.onSurface,
        surfaceVariant = TimTraColors.surface,
        onSurfaceVariant = TimTraColors.onSurfaceVariant,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = TimTraColors.surface,
        surfaceContainer = TimTraColors.surface,
        surfaceContainerHigh = Color(0xFFEDF2FF),
        surfaceContainerHighest = Color(0xFFE3EAFB),
        outline = TimTraColors.outline,
        outlineVariant = Color(0xFFE6ECF8),
        error = Color(0xFFC62828),
        onError = Color.White,
        errorContainer = Color(0xFFFFE9E7),
        onErrorContainer = Color(0xFF5C1210),
    )

/**
 * 文字サイズ。Material3 の既定より一回り小さくし、通勤情報を 1 画面に収める。
 * 各画面は必ずこの typography を経由し、直接 sp を書かない。
 */
private val defaultTypography = Typography()
private val compactTypography =
    Typography(
        displayLarge = defaultTypography.displayLarge.copy(fontSize = 44.sp, lineHeight = 50.sp),
        displayMedium = defaultTypography.displayMedium.copy(fontSize = 36.sp, lineHeight = 42.sp),
        displaySmall = defaultTypography.displaySmall.copy(fontSize = 30.sp, lineHeight = 36.sp),
        headlineLarge = defaultTypography.headlineLarge.copy(fontSize = 26.sp, lineHeight = 32.sp),
        headlineMedium = defaultTypography.headlineMedium.copy(fontSize = 22.sp, lineHeight = 28.sp),
        headlineSmall = defaultTypography.headlineSmall.copy(fontSize = 19.sp, lineHeight = 24.sp),
        titleLarge = defaultTypography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
        titleMedium = defaultTypography.titleMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
        titleSmall = defaultTypography.titleSmall.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
        bodyLarge = defaultTypography.bodyLarge.copy(fontSize = 14.5.sp, lineHeight = 20.sp),
        bodyMedium = defaultTypography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        bodySmall = defaultTypography.bodySmall.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
        labelLarge = defaultTypography.labelLarge.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
        labelMedium = defaultTypography.labelMedium.copy(fontSize = 11.sp, lineHeight = 14.sp),
        labelSmall = defaultTypography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
    )

@Composable
fun TimTraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = compactTypography) {
        Box(modifier = Modifier.fillMaxSize().background(TimTraColors.background)) {
            content()
        }
    }
}

/** 白地のカード。角丸 20dp と薄い縁。 */
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

/** 青グラデーションのカード（出発時刻など主役の情報）。中の文字は白。 */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .background(TimTraColors.headerGradient),
    ) {
        content()
    }
}

/** 青グラデーションのトップバー。ステータスバーの裏まで塗る。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimTraTopBar(
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxWidth().background(TimTraColors.headerGradient)) {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = TimTraColors.onGradient,
                    navigationIconContentColor = TimTraColors.onGradient,
                    actionIconContentColor = TimTraColors.onGradient,
                ),
        )
    }
}

/** トップバーの 1 行タイトル。 */
@Composable
fun TopBarTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}
