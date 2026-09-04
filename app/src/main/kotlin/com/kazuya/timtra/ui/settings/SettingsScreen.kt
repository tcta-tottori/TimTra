package com.kazuya.timtra.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kazuya.timtra.R
import com.kazuya.timtra.data.repository.AppSettings
import com.kazuya.timtra.ui.common.hhmm
import java.time.Duration
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        val current = settings
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            SettingsContent(
                settings = current,
                dayOffToday = viewModel.isDayOffToday,
                viewModel = viewModel,
                onOpenAbout = onOpenAbout,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    dayOffToday: Boolean,
    viewModel: SettingsViewModel,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = settings.commute
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SectionTitle(stringResource(R.string.settings_section_times))
        DurationRow(R.string.settings_walk_home_to_stop, c.walkHomeToStop) { viewModel.adjust(DurationField.WALK_HOME_TO_STOP, it) }
        DurationRow(R.string.settings_transfer_bus_to_jr, c.transferBusToJr) { viewModel.adjust(DurationField.TRANSFER_BUS_TO_JR, it) }
        DurationRow(R.string.settings_prep_buffer, c.prepBuffer) { viewModel.adjust(DurationField.PREP_BUFFER, it) }
        DurationRow(
            R.string.settings_walk_station_to_work,
            c.walkStationToWork,
        ) { viewModel.adjust(DurationField.WALK_STATION_TO_WORK, it) }
        DurationRow(R.string.settings_min_transfer, c.minTransfer) { viewModel.adjust(DurationField.MIN_TRANSFER, it) }
        DurationRow(
            R.string.settings_comfortable_transfer,
            c.comfortableTransfer,
        ) { viewModel.adjust(DurationField.COMFORTABLE_TRANSFER, it) }

        SectionTitle(stringResource(R.string.settings_section_windows))
        TimeRow(R.string.settings_outbound_window_start, c.outboundWindowStart) { viewModel.adjust(TimeField.OUTBOUND_WINDOW_START, it) }
        TimeRow(R.string.settings_inbound_window_start, c.inboundWindowStart) { viewModel.adjust(TimeField.INBOUND_WINDOW_START, it) }
        TimeRow(R.string.settings_earliest_leave_home, c.earliestLeaveHome) { viewModel.adjust(TimeField.EARLIEST_LEAVE_HOME, it) }
        TimeRow(R.string.settings_work_ends_at, c.workEndsAt) { viewModel.adjust(TimeField.WORK_ENDS_AT, it) }

        SectionTitle(stringResource(R.string.settings_section_notifications))
        SwitchRow(
            title = stringResource(R.string.settings_notifications_enabled),
            subtitle = stringResource(R.string.settings_notifications_note),
            checked = settings.notificationsEnabled,
            onCheckedChange = viewModel::setNotificationsEnabled,
        )
        SwitchRow(
            title = stringResource(R.string.settings_day_off),
            subtitle = stringResource(R.string.settings_day_off_note),
            checked = dayOffToday,
            onCheckedChange = viewModel::setDayOffToday,
        )

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = viewModel::resetToDefaults) { Text(stringResource(R.string.settings_reset)) }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAbout).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.nav_about), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun StepperRow(
    labelRes: Int,
    value: String,
    onStep: (Long) -> Unit,
    step: Long,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(labelRes), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { onStep(-step) }) { Text("−") }
        Text(
            text = value,
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(onClick = { onStep(step) }) { Text("+") }
    }
}

@Composable
private fun DurationRow(
    labelRes: Int,
    value: Duration,
    onStep: (Long) -> Unit,
) {
    StepperRow(labelRes, stringResource(R.string.settings_minutes_value, value.toMinutes()), onStep, step = 1)
}

@Composable
private fun TimeRow(
    labelRes: Int,
    value: LocalTime,
    onStep: (Long) -> Unit,
) {
    StepperRow(labelRes, value.hhmm(), onStep, step = TIME_STEP_MINUTES)
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private const val TIME_STEP_MINUTES = 15L
