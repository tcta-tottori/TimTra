package com.kazuya.timtra.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kazuya.timtra.R
import kotlin.math.cos
import kotlin.math.sin

/** 右下のボタンから開く行き先。 */
data class FabAction(
    val labelRes: Int,
    val iconRes: Int,
    val onClick: () -> Unit,
)

/**
 * 右下の 1 つのボタンから、時刻表・更新・設定を展開して出す。左メニューの代わり。
 *
 * 並びは渡した順に 上・左・斜め上。上のものから少しずつ遅らせて、
 * 「+」の周りを弧を描きながら外へ出てくる。開いているあいだ「+」はグレーに変わる。
 * 展開する項目も「+」と同じ大きさ・同じ見た目（[GlowFabFace]）。後ろの画面は呼び出し側でぼかす。
 */
@Composable
fun ActionMenuFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<FabAction>,
    modifier: Modifier = Modifier,
) {
    val open by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = SWEEP_MILLIS, easing = FastOutSlowInEasing),
        label = "fab",
    )
    // 上にあるものから順に出す（弧の上から下へ）
    val order = remember(actions.size) { actions.indices.sortedBy { angleOf(it, actions.size) } }
    Box(modifier = modifier.fillMaxSize()) {
        if (expanded || open > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = open }
                        .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onExpandedChange(false) },
            )
        }
        actions.forEachIndexed { index, action ->
            ArcItem(
                action = action,
                expanded = expanded,
                angle = angleOf(index, actions.size),
                rank = order.indexOf(index),
                onSelect = {
                    onExpandedChange(false)
                    action.onClick()
                },
            )
        }
        GlowFab(
            iconRes = R.drawable.ic_add,
            contentDescription = stringResource(if (expanded) R.string.action_close_menu else R.string.action_menu),
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.align(Alignment.BottomEnd).fabInset(),
            iconRotation = OPEN_ROTATION * open,
            muted = open,
        )
    }
}

/** 弧の上の 1 項目。開くと半径と角度がいっしょに伸びるので、円を描いて出てくる。 */
@Composable
private fun BoxScope.ArcItem(
    action: FabAction,
    expanded: Boolean,
    angle: Float,
    rank: Int,
    onSelect: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec =
            tween(
                durationMillis = SWEEP_MILLIS,
                delayMillis = if (expanded) rank * STAGGER_MILLIS else 0,
                easing = FastOutSlowInEasing,
            ),
        label = "arc",
    )
    if (!expanded && progress <= 0f) return
    val swept = (angle - SWEEP_DEGREES) + SWEEP_DEGREES * progress
    val radians = Math.toRadians(swept.toDouble())
    val radius = RADIUS_DP * progress
    Box(
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .fabInset()
                .offset(
                    x = (CENTER_X_DP - radius * sin(radians)).toFloat().dp,
                    y = (CENTER_Y_DP - radius * cos(radians)).toFloat().dp,
                ).graphicsLayer {
                    alpha = progress
                    scaleX = ITEM_MIN_SCALE + (1f - ITEM_MIN_SCALE) * progress
                    scaleY = scaleX
                },
    ) {
        ActionItem(action, onSelect)
    }
}

/** 「+」と同じ丸ボタンと、その下の名前。 */
@Composable
private fun ActionItem(
    action: FabAction,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .size(width = ITEM_WIDTH_DP.dp, height = ITEM_HEIGHT_DP.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        GlowFabFace(iconRes = action.iconRes, contentDescription = null)
        Box(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(action.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** 渡した順に 上（0°）・左（90°）・斜め上（45°）。4 つ以上のときは弧に均等割りする。 */
private fun angleOf(
    index: Int,
    count: Int,
): Float =
    when {
        index < arcAngles.size -> arcAngles[index]
        count <= 1 -> 0f
        else -> ARC_DEGREES * index / (count - 1)
    }

private val arcAngles = listOf(0f, 90f, 45f)

/** 開いたときに「+」を「×」に見せる角度。 */
private const val OPEN_ROTATION = 135f

/** 後ろの画面にかける暗幕の濃さ。ぼかしと合わせて使う。 */
private const val SCRIM_ALPHA = 0.55f

/** 出てくるときの時間と、1 つずつずらす間隔。 */
private const val SWEEP_MILLIS = 280
private const val STAGGER_MILLIS = 70

/** 出てくる前に戻しておく角度。この分だけ弧をなぞって現れる。 */
private const val SWEEP_DEGREES = 42f

/** 出はじめの大きさ。 */
private const val ITEM_MIN_SCALE = 0.55f

/** 1 項目ぶんの囲み（丸ボタン + 名前）。弧の上に中心を合わせるので、大きさを決め打ちにする。 */
private const val ITEM_WIDTH_DP = 100f
private const val ITEM_HEIGHT_DP = FAB_OUTER_DP + 30f

/** 弧の半径と、4 つ以上になったときの広がり（真上から左へ）。 */
private const val RADIUS_DP = 136.0
private const val ARC_DEGREES = 90f

/**
 * 右下ぞろえの囲みの「丸ボタンの中心」を、「+」の中心に合わせるための補正。
 * 囲みは「+」と同じ余白（[fabInset]）で右下にそろえてあるので、大きさの差だけを見ればよい。
 */
private const val CENTER_X_DP = (ITEM_WIDTH_DP - FAB_OUTER_DP) / 2
private const val CENTER_Y_DP = ITEM_HEIGHT_DP - FAB_OUTER_DP
