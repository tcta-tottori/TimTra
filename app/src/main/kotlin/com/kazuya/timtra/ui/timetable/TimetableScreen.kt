package com.kazuya.timtra.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kazuya.timtra.R
import com.kazuya.timtra.ui.common.hhmm
import com.kazuya.timtra.ui.theme.TimTraColors
import com.kazuya.timtra.ui.theme.TimTraTopBar
import java.time.format.DateTimeFormatter

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日(E)")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    onOpenDrawer: () -> Unit,
    viewModel: TimetableViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TimTraTopBar(
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.action_menu))
                    }
                },
                title = {
                    Column {
                        Text(stringResource(R.string.timetable_title), style = MaterialTheme.typography.titleLarge)
                        state.date?.let {
                            Text(
                                text = dateLabel(state, it.format(dateFormat)),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = state.tab.ordinal,
                containerColor = Color.White,
                contentColor = TimTraColors.primary,
                indicator = { positions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[state.tab.ordinal]),
                        color = TimTraColors.primary,
                    )
                },
            ) {
                TimetableTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(stringResource(tab.labelRes())) },
                    )
                }
            }
            DaySelector(selected = state.day, onSelect = viewModel::selectDay)
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (state.isToday) R.string.timetable_empty else R.string.timetable_empty_day_type))
                }
            } else {
                TimetableList(state)
            }
        }
    }
}

/** ヘッダー 2 行目。今日はその旨を、日種別指定はどの日の例かを示す。 */
@Composable
private fun dateLabel(
    state: TimetableUiState,
    formattedDate: String,
): String =
    when (state.day) {
        DaySelection.TODAY -> stringResource(R.string.timetable_date_today, formattedDate)
        DaySelection.WEEKDAY ->
            stringResource(
                R.string.timetable_date_day_type,
                stringResource(R.string.timetable_day_weekday),
                formattedDate,
            )
        DaySelection.SATURDAY ->
            stringResource(
                R.string.timetable_date_day_type,
                stringResource(R.string.timetable_day_saturday),
                formattedDate,
            )
        DaySelection.HOLIDAY ->
            stringResource(R.string.timetable_date_day_type, stringResource(R.string.timetable_day_holiday_long), formattedDate)
    }

/** 今日 / 平日 / 土曜 / 日祝 の切り替え。初期表示は今日（CLAUDE.md 7-2）。 */
@Composable
private fun DaySelector(
    selected: DaySelection,
    onSelect: (DaySelection) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        DaySelection.entries.forEachIndexed { index, day ->
            SegmentedButton(
                selected = day == selected,
                onClick = { onSelect(day) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = DaySelection.entries.size),
                colors =
                    SegmentedButtonDefaults.colors(
                        activeContainerColor = TimTraColors.primary,
                        activeContentColor = Color.White,
                        activeBorderColor = TimTraColors.primary,
                        inactiveContainerColor = Color.White,
                        inactiveContentColor = TimTraColors.primary,
                        inactiveBorderColor = TimTraColors.outline,
                    ),
                icon = {},
            ) {
                Text(stringResource(day.labelRes()), style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

private fun DaySelection.labelRes(): Int =
    when (this) {
        DaySelection.TODAY -> R.string.timetable_day_today
        DaySelection.WEEKDAY -> R.string.timetable_day_weekday
        DaySelection.SATURDAY -> R.string.timetable_day_saturday
        DaySelection.HOLIDAY -> R.string.timetable_day_holiday
    }

private fun TimetableTab.labelRes(): Int =
    when (this) {
        TimetableTab.HOME_STOP -> R.string.timetable_tab_home_stop
        TimetableTab.STATION -> R.string.timetable_tab_station
        TimetableTab.HOUGI -> R.string.timetable_tab_hougi
    }

@Composable
private fun TimetableList(state: TimetableUiState) {
    val listState = rememberLazyListState()
    val upcoming = state.upcomingIndex
    // 今日の表示では現在時刻の位置に自動スクロール（CLAUDE.md 7-2）。他の日種別は先頭から。
    LaunchedEffect(state.tab, state.day, state.entries) {
        listState.scrollToItem((upcoming - 1).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(state.entries) { index, entry ->
            val highlight = index == upcoming
            val past = state.isToday && (upcoming == -1 || index < upcoming)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (highlight) TimTraColors.primary.copy(alpha = 0.10f) else Color.Transparent,
                        ).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.time.hhmm(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                    color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(60.dp),
                )
                Text(
                    text = stringResource(if (entry.kind == EntryKind.BUS) R.string.timetable_kind_bus else R.string.timetable_kind_jr),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(40.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.destination, style = MaterialTheme.typography.bodyMedium)
                    Text(entry.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                entry.platform?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_platform, it), style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(color = TimTraColors.outline)
        }
    }
}
