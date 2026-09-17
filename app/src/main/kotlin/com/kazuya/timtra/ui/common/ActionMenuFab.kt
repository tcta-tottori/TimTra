package com.kazuya.timtra.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kazuya.timtra.R
import com.kazuya.timtra.ui.theme.TimTraColors

/** 右下のボタンから開く行き先。 */
data class FabAction(
    val labelRes: Int,
    val iconRes: Int,
    val onClick: () -> Unit,
)

/**
 * 右下の 1 つのボタンから、時刻表・設定などを展開して出す。
 * 左メニューの代わりなので、画面の行き来はここに集約する。
 */
@Composable
fun ActionMenuFab(
    actions: List<FabAction>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) OPEN_ROTATION else 0f, label = "fab")
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.forEach { action ->
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(action.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = TimTraColors.onSurface,
                        modifier =
                            Modifier
                                .background(TimTraColors.surfaceHigh, RoundedCornerShape(50))
                                .border(1.dp, TimTraColors.outline, RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    SmallFloatingActionButton(
                        onClick = {
                            expanded = false
                            action.onClick()
                        },
                        containerColor = TimTraColors.surfaceHigh,
                        contentColor = TimTraColors.accentLight,
                        shape = CircleShape,
                    ) {
                        Icon(painterResource(action.iconRes), contentDescription = null)
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { expanded = !expanded },
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

/** 開いたときに「+」を「×」に見せる角度。 */
private const val OPEN_ROTATION = 135f
