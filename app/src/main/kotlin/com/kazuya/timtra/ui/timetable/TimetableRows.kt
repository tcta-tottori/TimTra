package com.kazuya.timtra.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kazuya.timtra.R
import com.kazuya.timtra.ui.common.InfoPill
import com.kazuya.timtra.ui.common.ModeBadge
import com.kazuya.timtra.ui.common.TransitMode
import com.kazuya.timtra.ui.common.hhmm
import com.kazuya.timtra.ui.theme.StatusColors
import com.kazuya.timtra.ui.theme.TimTraColors

/*
 * 時刻表の行部品。時刻表画面と、ホームから開く「次の便」のポップアップで共有する。
 */

/** 「7 時 ──── 3 本」の見出し。 */
@Composable
internal fun HourHeader(
    hour: Int,
    count: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.timetable_hour, hour),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TimTraColors.primary,
        )
        Text(
            text = stringResource(R.string.timetable_hour_suffix),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 2.dp, top = 6.dp),
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), color = TimTraColors.outline)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.timetable_hour_count, count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 過ぎた便と次の便の境目に引く「現在 HH:mm」の線。 */
@Composable
internal fun NowMarker(nowText: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f).height(2.dp).background(StatusColors.risk))
        Text(
            text = stringResource(R.string.timetable_now_marker, nowText),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier =
                Modifier
                    .background(StatusColors.risk, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Box(modifier = Modifier.weight(1f).height(2.dp).background(StatusColors.risk))
    }
}

/** 便 1 行。時刻を大きく、種別を色付きアイコンで、行先と系統・のりばをピルで。 */
@Composable
internal fun TimetableRow(
    entry: TimetableEntry,
    isNext: Boolean,
    isPast: Boolean,
    departsIn: String?,
) {
    val mode = entry.kind.mode()
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp)
                .then(
                    if (isNext) {
                        Modifier
                            .background(TimTraColors.primary.copy(alpha = 0.07f), shape)
                            .border(1.dp, TimTraColors.primary.copy(alpha = 0.35f), shape)
                    } else {
                        Modifier
                    },
                ).alpha(if (isPast) PAST_ALPHA else 1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.time.hhmm(),
            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
            fontWeight = if (isNext) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isNext) TimTraColors.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(66.dp),
        )
        ModeBadge(mode = mode, size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.timetable_bound_for, entry.destination),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                InfoPill(text = entry.line)
                entry.code?.let { InfoPill(text = it) }
                entry.platform?.let {
                    InfoPill(
                        text = stringResource(R.string.home_platform, it),
                        color = mode.color,
                        container = mode.color.copy(alpha = 0.12f),
                    )
                }
            }
            entry.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (isNext && departsIn != null) {
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.timetable_next_badge),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier =
                        Modifier
                            .background(TimTraColors.primary, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 1.dp),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = departsIn,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TimTraColors.primary,
                    maxLines = 1,
                )
            }
        }
    }
}

internal fun EntryKind.mode(): TransitMode =
    when (this) {
        EntryKind.BUS -> TransitMode.BUS
        EntryKind.JR -> TransitMode.JR
    }

internal const val PAST_ALPHA = 0.45f
