package com.kazuya.timtra.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kazuya.timtra.core.journey.JourneyStatus

/**
 * 配色。時計版（`wear/ui/WearTheme.kt`）に合わせた黒地・濃紺。
 * ヘッダーと主役のカードは青のグラデーション、地は黒から濃紺へ持ち上げる。
 */
object TimTraColors {
    /** グラデーションの明側（アイコン左上）。 */
    val gradientStart = Color(0xFF2E8BF5)

    /** グラデーションの暗側（アイコン右下）。 */
    val gradientEnd = Color(0xFF14307F)

    /** ボタン・リンク・強調の青。黒地で読める明るさにする。 */
    val primary = Color(0xFF78C5FA)

    /** 「Tra」の水色。 */
    val accentLight = Color(0xFF78C5FA)

    /** 画面の地（有機 EL で消える黒に寄せる）。 */
    val background = Color(0xFF050A14)

    /** カードの地。黒地から少しだけ浮かせる。 */
    val surface = Color(0xFF0F1B30)
    val surfaceHigh = Color(0xFF16253F)
    val outline = Color(0xFF1E3355)
    val onSurface = Color.White
    val onSurfaceVariant = Color(0xFF9FB4D6)

    /** 地点名の下などに置く、白よりわずかに落とした文字。 */
    val onSurfaceSubtle = Color(0xFFCBDAF2)

    /** カウントダウンのリングの地（残りの部分）。 */
    val track = Color(0xFF39445A)

    /** 青地の上に置く白文字・ピル。 */
    val onGradient = Color.White
    val pillFill = Color.White.copy(alpha = 0.18f)
    val pillBorder = Color.White.copy(alpha = 0.45f)

    /** ヘッダー・メニュー・主要カードに使うグラデーション。 */
    val headerGradient: Brush = Brush.linearGradient(listOf(gradientStart, gradientEnd))

    /**
     * 画面の地。時計版と同じ考えで、右上へ向かって濃紺に持ち上げる。
     * 左下は黒に落として、有機 EL でも締まって見えるようにする。
     */
    val backgroundGradient: Brush =
        Brush.linearGradient(
            colors = listOf(background, Color(0xFF0A1730), Color(0xFF123061)),
            start = Offset.Zero,
            end = Offset(Float.POSITIVE_INFINITY, 0f),
        )

    /** カウントダウンのリング（明るい水色 → 青）。 */
    val ringGradient: Brush = Brush.linearGradient(listOf(accentLight, gradientStart))

    /** 左メニューの地。画面の地より少しだけ青を強くして、手前にあることを示す。 */
    val drawerGradient: Brush = Brush.verticalGradient(listOf(Color(0xFF12203C), Color(0xFF070D1A)))
}

/** 交通手段ごとの色。バスは日ノ丸バスを思わせる橙、JR は JR 西日本の青。地図・時刻表・ホームで共通。 */
object TransitColors {
    val bus = Color(0xFFFF8A3D)
    val jr = Color(0xFF4FB0FF)
    val walk = Color(0xFF8FA0C0)

    /** 勤務先などの一般の地点。 */
    val place = Color(0xFFB8A6E8)

    /** 現在地の青い点。 */
    val here = Color(0xFF4FA4FF)

    /** 地図の下地と罫線。 */
    val mapGround = Color(0xFF0F1B30)
    val mapGrid = Color(0xFF24334E)

    /** 地図に浮かせるラベルの地。タイルの上でも読めるよう濃く敷く。 */
    val labelFill = Color(0xE6081120)

    /**
     * 地図のタイル（OSM は白地）を黒地へ寄せる色変換。
     * いったん明るさだけにしてから反転し、濃紺（白かった所）→ 青灰（黒かった所）の幅に写す。
     */
    val mapTileMatrix: FloatArray = tileRow(98f, 120f) + tileRow(105f, 140f) + tileRow(117f, 175f) + floatArrayOf(0f, 0f, 0f, 1f, 0f)

    /** 明るさ 0 で [offset]、明るさ 1 で `offset - span` になる 1 行ぶん。 */
    private fun tileRow(
        span: Float,
        offset: Float,
    ): FloatArray = floatArrayOf(-0.299f * span / 255f, -0.587f * span / 255f, -0.114f * span / 255f, 0f, offset)
}

/** ステータスの色分け（CLAUDE.md 6: 緑 / オレンジ / 赤）。黒地で読める明るさ。 */
object StatusColors {
    val ok = Color(0xFF35D07F)
    val tight = Color(0xFFFFB74D)
    val risk = Color(0xFFFF6B66)
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
    darkColorScheme(
        primary = TimTraColors.primary,
        onPrimary = Color(0xFF05122B),
        primaryContainer = Color(0xFF17427F),
        onPrimaryContainer = Color(0xFFDCE9FF),
        secondary = Color(0xFFA9C7FF),
        onSecondary = Color(0xFF07152F),
        secondaryContainer = Color(0xFF16305C),
        onSecondaryContainer = Color(0xFFDCE9FF),
        tertiary = Color(0xFFC3B2F5),
        tertiaryContainer = Color(0xFF2E2557),
        onTertiaryContainer = Color(0xFFEDE7FF),
        background = TimTraColors.background,
        onBackground = TimTraColors.onSurface,
        surface = TimTraColors.background,
        onSurface = TimTraColors.onSurface,
        // surfaceVariant はカードの地（surface）と別の値にする。
        // 同じにすると contentColorFor がカードの文字色を onSurfaceVariant（薄い青灰）に寄せてしまう
        surfaceVariant = TimTraColors.surfaceHigh,
        onSurfaceVariant = TimTraColors.onSurfaceVariant,
        surfaceContainerLowest = Color(0xFF080F1E),
        surfaceContainerLow = TimTraColors.surface,
        surfaceContainer = TimTraColors.surface,
        surfaceContainerHigh = TimTraColors.surfaceHigh,
        surfaceContainerHighest = Color(0xFF1B2C4B),
        outline = TimTraColors.outline,
        outlineVariant = Color(0xFF17253E),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF3B0907),
        errorContainer = Color(0xFF5C1210),
        onErrorContainer = Color(0xFFFFE9E7),
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
        // Surface に包まれていない文字は既定で黒になる。黒地なので明示的に白へ寄せる
        CompositionLocalProvider(LocalContentColor provides TimTraColors.onSurface) {
            Box(modifier = Modifier.fillMaxSize().background(TimTraColors.backgroundGradient)) {
                content()
            }
        }
    }
}

/** カード。角丸 20dp と薄い縁。黒地から少しだけ浮かせる。 */
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
