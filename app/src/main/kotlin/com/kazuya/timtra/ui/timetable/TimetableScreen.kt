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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kazuya.timtra.R
import com.kazuya.timtra.ui.common.hhmm
import java.time.format.DateTimeFormatter

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日(E)")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(viewModel: TimetableViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.timetable_title))
                        state.date?.let {
                            Text(
                                text = stringResource(R.string.timetable_date, it.format(dateFormat)),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                TimetableTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(stringResource(tab.labelRes())) },
                    )
                }
            }
            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.timetable_empty))
                }
            } else {
                TimetableList(state)
            }
        }
    }
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
    // 現在時刻の位置に自動スクロール（CLAUDE.md 7-2）
    LaunchedEffect(state.tab, state.entries) {
        listState.scrollToItem((upcoming - 1).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(state.entries) { index, entry ->
            val highlight = index == upcoming
            val past = upcoming == -1 || index < upcoming
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        ).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.time.hhmm(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
                    color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(72.dp),
                )
                Text(
                    text = stringResource(if (entry.kind == EntryKind.BUS) R.string.timetable_kind_bus else R.string.timetable_kind_jr),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(40.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.destination, style = MaterialTheme.typography.bodyLarge)
                    Text(entry.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                entry.platform?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_platform, it), style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
        }
    }
}
