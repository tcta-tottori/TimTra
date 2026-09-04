package com.kazuya.timtra.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.journey.JourneyStatus
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.wear.R
import com.kazuya.timtra.wear.WearSnapshot
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** タイルをタップしたときの詳細画面: 出発時刻、バス/JR の発着、余裕、到着予測。 */
@Composable
fun WearHomeScreen(viewModel: WearHomeViewModel = hiltViewModel()) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    Scaffold(timeText = { TimeText() }) {
        val s = snapshot
        if (s == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Content(s, viewModel.isManualBound, onToggleBound = { viewModel.toggleBound(s.bound) }, onResetBound = viewModel::resetBound)
        }
    }
}

@Composable
private fun Content(
    s: WearSnapshot,
    isManualBound: Boolean,
    onToggleBound: () -> Unit,
    onResetBound: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current
    ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        val j = s.journey
        if (j == null) {
            item { Line(stringResource(if (s.dayOff) R.string.day_off else R.string.no_journey)) }
        } else {
            item {
                Line(stringResource(if (j.bound == Bound.OUTBOUND) R.string.leave_home else R.string.leave_work), color = Color.LightGray)
            }
            item { Text(j.leaveAt.hhmm(), style = MaterialTheme.typography.display1, fontWeight = FontWeight.Bold) }
            item { Line(context.countdownLabel(s.now, j.leaveAt)) }
            item { StatusBadge(j) }
            if (j.bound == Bound.OUTBOUND) {
                item { LegLine(R.string.bus_line, j.bus.departureAt, j.bus.arrivalAt, j.bus.trip.alightStop.platformCode) }
                item {
                    LegLine(
                        R.string.jr_line,
                        j.train.departureAt,
                        j.train.arrivalAt,
                        j.train.service.platform
                            .ifBlank { null },
                    )
                }
                item { Line(stringResource(R.string.arrival_work, j.arriveAt.hhmm())) }
            } else {
                item { LegLine(R.string.jr_line, j.train.departureAt, j.train.arrivalAt, null) }
                item { LegLine(R.string.bus_line, j.bus.departureAt, j.bus.arrivalAt, j.bus.trip.boardStop.platformCode) }
                item { Line(stringResource(R.string.arrival_home, j.arriveAt.hhmm())) }
            }
            if (s.dayOff) item { Line(stringResource(R.string.day_off), color = MaterialTheme.colors.primary) }
        }
        item {
            Chip(
                onClick = onToggleBound,
                label = { Text(stringResource(if (s.bound == Bound.OUTBOUND) R.string.bound_outbound else R.string.bound_inbound)) },
                colors = ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isManualBound) {
            item {
                Chip(
                    onClick = onResetBound,
                    label = { Text(stringResource(R.string.bound_auto)) },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { Line(syncLabel(s), color = Color.Gray) }
        if (s.sampleData) item { Line(stringResource(R.string.sample_data), color = MaterialTheme.colors.primary) }
    }
}

@Composable
private fun syncLabel(s: WearSnapshot): String {
    val at = s.lastSyncedAt ?: return stringResource(R.string.not_synced)
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault())
    return stringResource(R.string.synced_at, time.hhmm())
}

@Composable
private fun Line(
    text: String,
    color: Color = MaterialTheme.colors.onBackground,
) {
    Text(
        text = text,
        color = color,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.caption1,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
private fun LegLine(
    labelRes: Int,
    from: LocalDateTime,
    to: LocalDateTime,
    platform: String?,
) {
    val base = stringResource(labelRes, from.hhmm(), to.hhmm())
    Line(if (platform.isNullOrBlank()) base else base + " " + stringResource(R.string.platform, platform))
}

@Composable
private fun StatusBadge(journey: Journey) {
    val label =
        stringResource(
            when (journey.status) {
                JourneyStatus.OK -> R.string.status_ok
                JourneyStatus.TIGHT -> R.string.status_tight
                JourneyStatus.RISK -> R.string.status_risk
                JourneyStatus.MISSED -> R.string.status_missed
            },
        )
    val margin = journey.transferMargin.toMinutes()
    val marginText =
        if (margin >= 0) stringResource(R.string.transfer_margin, margin) else stringResource(R.string.transfer_margin_negative, -margin)
    Box(
        modifier =
            Modifier
                .background(Color(StatusColors.argb(journey.status)), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text("$label・$marginText", color = Color.White, style = MaterialTheme.typography.caption1)
    }
}
