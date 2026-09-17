package com.kazuya.timtra.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kazuya.timtra.R
import com.kazuya.timtra.ui.theme.TimTraColors
import kotlin.math.cos
import kotlin.math.sin

/** 右下のボタンから開く行き先。 */
data class FabAction(
    val labelRes: Int,
    val iconRes: Int,
    val onClick: () -> Unit,
)

/**
 * 右下の 1 つのボタンから、時刻表・設定などを展開して出す。左メニューの代わり。
 *
 * 開くと丸いアイコンバッジが「+」を囲うように弧を描いて並び、それぞれの下に名前が付く。
 * 画面いっぱいに広がるので、この上に置く（後ろの画面は呼び出し側でぼかす）。
 */
@Composable
fun ActionMenuFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    actions: List<FabAction>,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (expanded) OPEN_ROTATION else 0f, label = "fab")
    Box(modifier = modifier.fillMaxSize()) {
        if (expanded) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onExpandedChange(false) },
            )
        }
        actions.forEachIndexed { index, action ->
            // 真上から左へ、等間隔の弧に置く
            val angle = if (actions.size <= 1) 0f else ARC_DEGREES * index / (actions.size - 1)
            val radians = Math.toRadians(angle.toDouble())
            AnimatedVisibility(
                visible = expanded,
                enter = scaleIn(initialScale = 0.6f) + fadeIn(),
                exit = scaleOut(targetScale = 0.6f) + fadeOut(),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = EDGE_DP.dp, bottom = EDGE_DP.dp)
                        .offset(
                            x = (CENTER_X_DP - RADIUS_DP * sin(radians)).toFloat().dp,
                            y = (CENTER_Y_DP - RADIUS_DP * cos(radians)).toFloat().dp,
                        ),
            ) {
                ActionItem(action) {
                    onExpandedChange(false)
                    action.onClick()
                }
            }
        }
        FloatingActionButton(
            onClick = { onExpandedChange(!expanded) },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(EDGE_DP.dp)
                    .size(FAB_DP.dp),
            containerColor = TimTraColors.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(if (expanded) R.string.action_close_menu else R.string.action_menu),
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

/** 大きな丸いアイコンバッジと、その下の名前。 */
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
        Box(
            modifier = Modifier.size(BADGE_DP.dp).background(TimTraColors.headerGradient, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(action.iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(BADGE_ICON_DP.dp),
            )
        }
        Box(modifier = Modifier.height(4.dp))
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

/** 開いたときに「+」を「×」に見せる角度。 */
private const val OPEN_ROTATION = 135f

/** 後ろの画面にかける暗幕の濃さ。ぼかしと合わせて使う。 */
private const val SCRIM_ALPHA = 0.55f

/** 画面の隅からの余白と、押しボタンの大きさ。 */
private const val EDGE_DP = 20f
private const val FAB_DP = 56f

/** バッジの大きさと、その中のアイコン。 */
private const val BADGE_DP = 54f
private const val BADGE_ICON_DP = 26f

/** 1 項目ぶんの囲み（バッジ + 名前）。弧の上に中心を合わせるので、大きさを決め打ちにする。 */
private const val ITEM_WIDTH_DP = 96f
private const val ITEM_HEIGHT_DP = 88f

/** 弧の半径と広がり（真上から左へ）。 */
private const val RADIUS_DP = 124.0
private const val ARC_DEGREES = 78f

/** 右下ぞろえの囲みを、押しボタンの中心に合わせるための補正。 */
private const val CENTER_X_DP = (ITEM_WIDTH_DP - FAB_DP) / 2
private const val CENTER_Y_DP = (ITEM_HEIGHT_DP - FAB_DP) / 2
