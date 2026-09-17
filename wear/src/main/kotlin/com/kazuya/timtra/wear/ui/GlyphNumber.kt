package com.kazuya.timtra.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.kazuya.timtra.data.glyph.GlyphText

/**
 * 切り出した字（`assets/date`）で数字を書く。0〜9 と「:」だけ。
 *
 * [capHeight] は数字「1」の高さ。字画像にはグローが焼き込んであるので、
 * それが切れないよう囲みを [GLOW_PAD] ぶん広く取り、中に寄せて描く。
 */
@Composable
fun GlyphNumber(
    text: String,
    capHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cap = with(density) { capHeight.toPx() }
    val pad = cap * GLOW_PAD
    val width = GlyphText.width(text, cap) + pad * 2
    val height = GlyphText.height(cap) + pad * 2
    Canvas(modifier.size(with(density) { width.toDp() }, with(density) { height.toDp() })) {
        drawIntoCanvas { canvas ->
            GlyphText.draw(context, canvas.nativeCanvas, text, cap, pad, pad + cap)
        }
    }
}

/** グローのぶんの余白（数字の高さに対する割合）。切り出しの余白と同じ。 */
private const val GLOW_PAD = 0.22f
