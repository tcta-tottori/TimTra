package com.kazuya.timtra.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kazuya.timtra.R
import com.kazuya.timtra.ui.common.ModeBadge
import com.kazuya.timtra.ui.common.TransitMode
import com.kazuya.timtra.ui.common.departsInText
import com.kazuya.timtra.ui.theme.TimTraColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日(E)")

/**
 * ホームのバス / JR カードをタップしたときのポップアップ。
 * 現在時刻から一番近い便を先頭（ハイライト + 残り時間）に、それ以降の便を並べる。
 * 今日の残りが少なければ翌日の始発から数本を続けて出す。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetablePeekSheet(
    peek: TimetablePeek,
    now: LocalTime,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mode = if (peek.kind == PeekKind.BUS_HOME || peek.kind == PeekKind.BUS_STATION) TransitMode.BUS else TransitMode.JR
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.White) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeBadge(mode = mode, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(peek.kind.titleRes()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = mode.color,
                    )
                    Text(
                        text = stringResource(R.string.timetable_date_today, peek.today.format(dateFormat)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.peek_close)) }
            }
            HorizontalDivider(color = TimTraColors.outline)
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                if (peek.upcomingToday.isEmpty()) {
                    item(key = "none") {
                        Text(
                            text = stringResource(R.string.peek_no_more_today),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
                itemsIndexed(peek.upcomingToday, key = { i, _ -> "t$i" }) { index, entry ->
                    com.kazuya.timtra.ui.timetable.TimetableRow(
                        entry = entry,
                        isNext = index == 0,
                        isPast = false,
                        departsIn = if (index == 0) departsInText(now, entry.seconds) else null,
                    )
                }
                if (peek.tomorrowHead.isNotEmpty()) {
                    item(key = "tomorrow") { DayHeader(peek.tomorrow) }
                    itemsIndexed(peek.tomorrowHead, key = { i, _ -> "n$i" }) { _, entry ->
                        com.kazuya.timtra.ui.timetable
                            .TimetableRow(entry = entry, isNext = false, isPast = false, departsIn = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.peek_next_day, date.format(dateFormat)),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = TimTraColors.primary,
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), color = TimTraColors.outline)
    }
}

private fun PeekKind.titleRes(): Int =
    when (this) {
        PeekKind.BUS_HOME -> R.string.peek_title_bus_home
        PeekKind.JR_OUTBOUND -> R.string.peek_title_jr_outbound
        PeekKind.JR_INBOUND -> R.string.peek_title_jr_inbound
        PeekKind.BUS_STATION -> R.string.peek_title_bus_station
    }
