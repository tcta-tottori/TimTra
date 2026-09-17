package com.kazuya.timtra.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kazuya.timtra.ui.theme.TimTraColors

/**
 * 乗り物アイコンを囲むリング（時計版と同じ）。
 * 砂時計と同じ向きで、[level] が 1 なら満タン、0 で空。
 */
@Composable
fun CountdownRing(
    level: Float,
    iconRes: Int,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = STROKE_DP.dp.toPx()
            val topLeft = Offset(stroke / 2f, stroke / 2f)
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val style = Stroke(width = stroke, cap = StrokeCap.Round)
            drawArc(
                color = TimTraColors.track,
                startAngle = START_ANGLE,
                sweepAngle = FULL_TURN,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style,
            )
            if (level > 0f) {
                drawArc(
                    brush = TimTraColors.ringGradient,
                    startAngle = START_ANGLE,
                    sweepAngle = FULL_TURN * level,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = style,
                )
            }
        }
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * ICON_RATIO),
        )
    }
}

private const val STROKE_DP = 6f
private const val START_ANGLE = -90f
private const val FULL_TURN = 360f
private const val ICON_RATIO = 0.46f
