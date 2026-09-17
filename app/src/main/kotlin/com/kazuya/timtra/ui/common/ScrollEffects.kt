package com.kazuya.timtra.ui.common

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * スクロールの見え方を整える部品。
 * 入ってきたものは下から浮かび上がらせ、画面の下端に近いものは薄くして奥行きを出す。
 */

/**
 * 画面に入ってきたときに、何も無い状態から下から浮かび上がらせる。
 *
 * 画面の外にあるあいだは窓の中での高さが 0 になるので、それを合図にして一度だけ動かす。
 * 一覧（LazyColumn）でも、まとめて組み立てる縦スクロールでも同じように効く。
 */
@Composable
fun Modifier.riseIn(rise: Dp = RISE_DP.dp): Modifier {
    var shown by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = RISE_MILLIS, easing = LinearOutSlowInEasing),
        label = "riseIn",
    )
    val risePx = with(LocalDensity.current) { rise.toPx() }
    return this
        .onGloballyPositioned { coordinates ->
            if (!shown && coordinates.boundsInWindow().height > VISIBLE_PX) shown = true
        }.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * risePx
        }
}

/** 画面の下端に向かって薄くする。スクロールの続きがあることも、これで分かる。 */
fun Modifier.fadeBottomEdge(height: Dp = FADE_DP.dp): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val fade = minOf(height.toPx(), size.height)
            if (fade <= 0f) return@drawWithContent
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Black.copy(alpha = FADE_FLOOR)),
                        startY = size.height - fade,
                        endY = size.height,
                    ),
                topLeft = Offset(0f, size.height - fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn,
            )
        }

/** 浮かび上がる距離と時間。 */
private const val RISE_DP = 18f
private const val RISE_MILLIS = 320

/** これだけ見えたら「入ってきた」とみなす高さ（px）。 */
private const val VISIBLE_PX = 1f

/** 下端をぼかす高さと、いちばん下の残りの濃さ。 */
private const val FADE_DP = 96f
private const val FADE_FLOOR = 0.18f
