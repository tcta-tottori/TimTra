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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kazuya.timtra.R
import com.kazuya.timtra.core.notify.SuppressReason
import com.kazuya.timtra.data.repository.AppSettings
import com.kazuya.timtra.data.repository.NotificationPlanSummary
import com.kazuya.timtra.ui.common.hhmm
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val planSummary by viewModel.planSummary.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        val current = settings
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            SettingsContent(
                settings = current,
                planSummary = planSummary,
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
    planSummary: NotificationPlanSummary,
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

        SectionTitle(stringResource(R.string.settings_section_notify_timing))
        val t = settings.notificationTiming
        DurationRow(R.string.settings_notify_before_leave, t.beforeLeave) { viewModel.adjust(NotifyField.BEFORE_LEAVE, it) }
        DurationRow(
            R.string.settings_notify_before_first_leg,
            t.beforeFirstLegDeparture,
        ) { viewModel.adjust(NotifyField.BEFORE_FIRST_LEG, it) }
        DurationRow(R.string.settings_notify_before_transfer, t.beforeTransferArrival) { viewModel.adjust(NotifyField.BEFORE_TRANSFER, it) }

        SectionTitle(stringResource(R.string.settings_plan_status))
        PlanStatus(planSummary)
        Row {
            TextButton(onClick = viewModel::replanNow) { Text(stringResource(R.string.settings_replan)) }
            TextButton(onClick = viewModel::sendTestNotification) { Text(stringResource(R.string.settings_test_notification)) }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = viewModel::resetToDefaults) { Text(stringResource(R.string.settings_reset)) }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAbout).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.nav_about), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(16.dp))
    }
}

private val planDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d(E)")

@Composable
private fun PlanStatus(summary: NotificationPlanSummary) {
    val computedAt = summary.computedAt
    if (computedAt == null) {
        Text(stringResource(R.string.settings_plan_not_computed), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column {
        Text(
            text = stringResource(R.string.settings_plan_today, summary.today.label(), planLine(summary.todayCount, summary.todayReason)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text =
                stringResource(
                    R.string.settings_plan_tomorrow,
                    summary.tomorrow.label(),
                    planLine(summary.tomorrowCount, summary.tomorrowReason),
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.settings_plan_computed_at, computedAt.hhmm()),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!summary.exactAlarms) {
            Text(
                text = stringResource(R.string.settings_plan_inexact),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun LocalDate?.label(): String = this?.format(planDateFormat) ?: "-"

@Composable
private fun planLine(
    count: Int,
    reason: String?,
): String {
    val suppress = reason?.let { name -> SuppressReason.entries.firstOrNull { it.name == name } }
    return when {
        suppress != null -> stringResource(suppress.labelRes())
        count > 0 -> stringResource(R.string.settings_plan_count, count)
        else -> stringResource(R.string.settings_plan_none)
    }
}

private fun SuppressReason.labelRes(): Int =
    when (this) {
        SuppressReason.DISABLED -> R.string.suppress_disabled
        SuppressReason.DAY_OFF -> R.string.suppress_day_off
        SuppressReason.NOT_WORKDAY -> R.string.suppress_not_workday
        SuppressReason.NO_BUS_SERVICE -> R.string.suppress_no_bus_service
        SuppressReason.NO_JOURNEY -> R.string.suppress_no_journey
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
