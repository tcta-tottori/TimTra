package com.kazuya.timtra.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kazuya.timtra.R
import com.kazuya.timtra.ui.common.InfoPill
import com.kazuya.timtra.ui.common.ModeBadge
import com.kazuya.timtra.ui.common.TransitMode
import com.kazuya.timtra.ui.common.departsInText
import com.kazuya.timtra.ui.common.hhmm
import com.kazuya.timtra.ui.theme.StatusColors
import com.kazuya.timtra.ui.theme.TimTraColors
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日(E)")

/** LazyColumn の 1 行。時間帯の見出し・現在時刻の線・便のいずれか。 */
private sealed interface RowItem {
    val key: String

    data class Header(
        val hour: Int,
        val count: Int,
    ) : RowItem {
        override val key: String get() = "h$hour"
    }

    data object Now : RowItem {
        override val key: String get() = "now"
    }

    data class Entry(
        val index: Int,
        val entry: TimetableEntry,
    ) : RowItem {
        override val key: String get() = "e$index"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    onOpenDrawer: () -> Unit,
    viewModel: TimetableViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rows = remember(state.entries, state.upcomingIndex, state.isToday) { buildRows(state) }
    val nowRowIndex = rows.indexOfFirst { it is RowItem.Now }

    // 今日の表示では現在時刻の位置に自動スクロール（CLAUDE.md 7-2）。他の日種別は先頭から。
    LaunchedEffect(state.tab, state.day, state.stationFilter, state.loading) {
        if (!state.loading) listState.scrollToItem((nowRowIndex - 1).coerceAtLeast(0))
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(TimTraColors.headerGradient)) {
                TopAppBar(
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
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = TimTraColors.onGradient,
                            navigationIconContentColor = TimTraColors.onGradient,
                        ),
                )
                HeaderTabs(selected = state.tab, onSelect = viewModel::selectTab)
            }
        },
        floatingActionButton = {
            if (state.isToday && nowRowIndex >= 0) {
                ExtendedFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem((nowRowIndex - 1).coerceAtLeast(0)) } },
                    containerColor = TimTraColors.primary,
                    contentColor = Color.White,
                    icon = { Icon(painterResource(R.drawable.ic_timer), contentDescription = null) },
                    text = { Text(stringResource(R.string.timetable_scroll_to_now)) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DaySelector(selected = state.day, onSelect = viewModel::selectDay)
            if (state.tab == TimetableTab.STATION) {
                StationFilterRow(selected = state.stationFilter, onSelect = viewModel::selectStationFilter)
            }
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.entries.isEmpty()) {
                EmptyState(state)
            } else {
                SummaryStrip(state)
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(rows, key = { _, row -> row.key }) { _, row ->
                        when (row) {
                            is RowItem.Header -> HourHeader(row.hour, row.count)
                            RowItem.Now -> NowMarker(state.now.hhmm())
                            is RowItem.Entry -> {
                                val upcoming = state.upcomingIndex
                                TimetableRow(
                                    entry = row.entry,
                                    isNext = row.index == upcoming,
                                    isPast = state.isToday && (upcoming == -1 || row.index < upcoming),
                                    departsIn =
                                        if (row.index == upcoming) {
                                            departsInText(state.now, row.entry.seconds)
                                        } else {
                                            null
                                        },
                                )
                            }
                        }
                    }
                    item(key = "bottom") { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

/** 時間帯ごとの見出しを挟み、今日の表示では次の便の直前に「現在」の線を入れる。 */
private fun buildRows(state: TimetableUiState): List<RowItem> {
    val upcoming = state.upcomingIndex
    val rows = mutableListOf<RowItem>()
    var nowInserted = !state.isToday
    for (group in state.hourGroups) {
        rows += RowItem.Header(group.hour, group.items.size)
        for ((index, entry) in group.items) {
            if (!nowInserted && index == upcoming) {
                rows += RowItem.Now
                nowInserted = true
            }
            rows += RowItem.Entry(index, entry)
        }
    }
    // すべて過ぎている（最終便のあと）ときは末尾に
    if (!nowInserted) rows += RowItem.Now
    return rows
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

/** 青い帯の中のタブ。選んだものは白いピルになり、アイコンで種別（バス / JR）が分かる。 */
@Composable
private fun HeaderTabs(
    selected: TimetableTab,
    onSelect: (TimetableTab) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                .background(TimTraColors.pillFill, RoundedCornerShape(50))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TimetableTab.entries.forEach { tab ->
            val active = tab == selected
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Color.White else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tab.modes().forEach { mode ->
                    Icon(
                        painter = painterResource(mode.iconRes),
                        contentDescription = null,
                        tint = if (active) mode.color else Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(tab.labelRes()),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) TimTraColors.primary else Color.White,
                    maxLines = 1,
                )
            }
        }
    }
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

/** 鳥取駅タブだけに出す バス / JR の絞り込み。 */
@Composable
private fun StationFilterRow(
    selected: StationFilter,
    onSelect: (StationFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StationFilter.entries.forEach { filter ->
            val mode = filter.mode()
            val accent = mode?.color ?: TimTraColors.primary
            val leadingIcon: (@Composable () -> Unit)? =
                if (mode == null) {
                    null
                } else {
                    {
                        Icon(
                            painter = painterResource(mode.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                }
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes()), style = MaterialTheme.typography.labelLarge) },
                leadingIcon = leadingIcon,
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.12f),
                        selectedLabelColor = accent,
                        selectedLeadingIconColor = accent,
                        iconColor = accent,
                    ),
                border =
                    FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filter == selected,
                        borderColor = TimTraColors.outline,
                        selectedBorderColor = accent,
                        selectedBorderWidth = 1.dp,
                    ),
            )
        }
    }
}

/** 一覧の上の要約: 方面、本数、始発と最終。 */
@Composable
private fun SummaryStrip(state: TimetableUiState) {
    val first = state.entries.first()
    val last = state.entries.last()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(state.directionLabelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = TimTraColors.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
                stringResource(R.string.timetable_summary_count, state.entries.size) + " ・ " +
                    stringResource(R.string.timetable_summary_first_last, first.time.hhmm(), last.time.hhmm()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(state: TimetableUiState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_schedule),
                contentDescription = null,
                tint = TimTraColors.outline,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(if (state.isToday) R.string.timetable_empty else R.string.timetable_empty_day_type),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 「7 時 ──── 3 本」の見出し。 */
@Composable
private fun HourHeader(
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
private fun NowMarker(nowText: String) {
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
private fun TimetableRow(
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

private fun EntryKind.mode(): TransitMode =
    when (this) {
        EntryKind.BUS -> TransitMode.BUS
        EntryKind.JR -> TransitMode.JR
    }

private fun TimetableTab.modes(): List<TransitMode> =
    when (this) {
        TimetableTab.HOME_STOP -> listOf(TransitMode.BUS)
        TimetableTab.STATION -> listOf(TransitMode.BUS, TransitMode.JR)
        TimetableTab.HOUGI -> listOf(TransitMode.JR)
    }

private fun StationFilter.mode(): TransitMode? =
    when (this) {
        StationFilter.ALL -> null
        StationFilter.BUS -> TransitMode.BUS
        StationFilter.JR -> TransitMode.JR
    }

private fun TimetableUiState.directionLabelRes(): Int =
    when (tab) {
        TimetableTab.HOME_STOP -> R.string.timetable_dir_home_stop
        TimetableTab.STATION ->
            when (stationFilter) {
                StationFilter.ALL -> R.string.timetable_dir_station
                StationFilter.BUS -> R.string.timetable_dir_station_bus
                StationFilter.JR -> R.string.timetable_dir_station_jr
            }
        TimetableTab.HOUGI -> R.string.timetable_dir_hougi
    }

private fun StationFilter.labelRes(): Int =
    when (this) {
        StationFilter.ALL -> R.string.timetable_filter_all
        StationFilter.BUS -> R.string.timetable_filter_bus
        StationFilter.JR -> R.string.timetable_filter_jr
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

private const val PAST_ALPHA = 0.45f
