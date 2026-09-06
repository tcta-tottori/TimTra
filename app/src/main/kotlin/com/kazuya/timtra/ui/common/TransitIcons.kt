package com.kazuya.timtra.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kazuya.timtra.R
import com.kazuya.timtra.core.geo.LandmarkKind
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.ui.theme.TransitColors

/** 交通手段。ホーム・時刻表・地図で同じアイコンと色を使う。 */
enum class TransitMode {
    BUS,
    JR,
    WALK,
    ;

    val iconRes: Int
        get() =
            when (this) {
                BUS -> R.drawable.ic_bus
                JR -> R.drawable.ic_train
                WALK -> R.drawable.ic_walk
            }

    val color: Color
        get() =
            when (this) {
                BUS -> TransitColors.bus
                JR -> TransitColors.jr
                WALK -> TransitColors.walk
            }

    val labelRes: Int
        get() =
            when (this) {
                BUS -> R.string.mode_bus
                JR -> R.string.mode_jr
                WALK -> R.string.mode_walk
            }
}

/** 出発地点のアイコン（往路は家、復路は勤務先）。 */
fun Bound.originIconRes(): Int =
    when (this) {
        Bound.OUTBOUND -> R.drawable.ic_home
        Bound.INBOUND -> R.drawable.ic_work
    }

/** 到着地点のアイコン（往路は勤務先、復路は家）。 */
fun Bound.destinationIconRes(): Int =
    when (this) {
        Bound.OUTBOUND -> R.drawable.ic_work
        Bound.INBOUND -> R.drawable.ic_home
    }

/** 地図の地点のアイコンと色。 */
val LandmarkKind.iconRes: Int
    get() =
        when (this) {
            LandmarkKind.HOME_STOP -> R.drawable.ic_bus
            LandmarkKind.STATION -> R.drawable.ic_train
            LandmarkKind.HOUGI_STATION -> R.drawable.ic_train
            LandmarkKind.WORKPLACE -> R.drawable.ic_work
        }

val LandmarkKind.color: Color
    get() =
        when (this) {
            LandmarkKind.HOME_STOP -> TransitColors.bus
            LandmarkKind.STATION -> TransitColors.jr
            LandmarkKind.HOUGI_STATION -> TransitColors.jr
            LandmarkKind.WORKPLACE -> TransitColors.place
        }

val LandmarkKind.labelRes: Int
    get() =
        when (this) {
            LandmarkKind.HOME_STOP -> R.string.map_landmark_home_stop
            LandmarkKind.STATION -> R.string.map_landmark_station
            LandmarkKind.HOUGI_STATION -> R.string.map_landmark_hougi
            LandmarkKind.WORKPLACE -> R.string.map_landmark_workplace
        }

/** 丸い色地に白いアイコン。バス = オレンジ、JR = 青。 */
@Composable
fun ModeBadge(
    mode: TransitMode,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    color: Color = mode.color,
    contentDescription: String? = stringResource(mode.labelRes),
) {
    Box(
        modifier = modifier.size(size).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(mode.iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size * ICON_RATIO),
        )
    }
}

/** 任意のアイコンを丸い色地に載せる（地図の地点、出発/到着など）。 */
@Composable
fun CircleIcon(
    iconRes: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier.size(size).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(size * ICON_RATIO),
        )
    }
}

/** 「バス」「JR」の小さなピル（アイコン + 文字）。時刻表の種別表示や見出しに使う。 */
@Composable
fun ModeChip(
    mode: TransitMode,
    modifier: Modifier = Modifier,
    text: String = stringResource(mode.labelRes),
) {
    Row(
        modifier =
            modifier
                .background(mode.color.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(start = 6.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(mode.iconRes),
            contentDescription = null,
            tint = mode.color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = mode.color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** 系統名・番線などの控えめなピル。 */
@Composable
fun InfoPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier =
            modifier
                .background(container, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private const val ICON_RATIO = 0.58f
