package com.kazuya.timtra.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.kazuya.timtra.location.LocationProvider
import com.kazuya.timtra.ui.common.hhmm
import com.kazuya.timtra.ui.theme.StatusColors
import com.kazuya.timtra.ui.theme.TimTraTopBar
import com.kazuya.timtra.ui.theme.TopBarTitle
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenDrawer: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val planSummary by viewModel.planSummary.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, context.getString(it), Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TimTraTopBar(
                title = { TopBarTitle(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.action_menu))
                    }
                },
            )
        },
    ) { padding ->
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

        SectionTitle(stringResource(R.string.settings_section_leave_display))
        Text(
            text = stringResource(R.string.settings_leave_display_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        TimeRow(R.string.settings_leave_home_display_start, c.leaveHomeDisplayStart, step = FINE_TIME_STEP_MINUTES) {
            viewModel.adjust(TimeField.LEAVE_HOME_DISPLAY_START, it)
        }
        TimeRow(R.string.settings_leave_home_display_end, c.leaveHomeDisplayEnd, step = FINE_TIME_STEP_MINUTES) {
            viewModel.adjust(TimeField.LEAVE_HOME_DISPLAY_END, it)
        }
        TimeRow(R.string.settings_leave_work_display_start, c.leaveWorkDisplayStart, step = FINE_TIME_STEP_MINUTES) {
            viewModel.adjust(TimeField.LEAVE_WORK_DISPLAY_START, it)
        }

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

        SectionTitle(stringResource(R.string.settings_section_reminder))
        SwitchRow(
            title = stringResource(R.string.settings_reminder_enabled),
            subtitle = stringResource(R.string.settings_reminder_note),
            checked = settings.trainReminder.enabled,
            onCheckedChange = viewModel::setTrainReminderEnabled,
        )
        TimeRow(R.string.settings_reminder_window_start, settings.trainReminder.windowStart) { viewModel.adjustReminderWindowStart(it) }
        WorkplaceRow(settings, viewModel)
        BackgroundLocationRow(viewModel)
        if (viewModel.isTrainReminderOffToday) {
            Text(
                text = stringResource(R.string.settings_reminder_off_today),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

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

/** 勤務先の位置。登録は現在地を 1 回取るだけ（前景の位置情報の許可が必要）。 */
@Composable
private fun WorkplaceRow(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            viewModel.permissionsChanged()
            if (result.values.any { it }) viewModel.registerWorkplaceHere()
        }
    val workplace = settings.workplace
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(stringResource(R.string.settings_workplace), style = MaterialTheme.typography.bodyLarge)
        Text(
            text =
                if (workplace != null) {
                    stringResource(R.string.settings_workplace_registered, workplace.lat, workplace.lon)
                } else {
                    stringResource(R.string.settings_workplace_default)
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            TextButton(
                onClick = {
                    if (viewModel.hasLocationPermission) {
                        viewModel.registerWorkplaceHere()
                    } else {
                        launcher.launch(
                            LocationProvider.PERMISSIONS,
                        )
                    }
                },
            ) { Text(stringResource(R.string.settings_workplace_register)) }
            if (workplace != null) {
                TextButton(onClick = viewModel::clearWorkplace) { Text(stringResource(R.string.settings_workplace_clear)) }
            }
        }
    }
}

/** 「常に許可」の状態と導線。Android 11 以降は要求すると設定画面が開く。 */
@Composable
private fun BackgroundLocationRow(viewModel: SettingsViewModel) {
    val version by viewModel.permissionVersion.collectAsStateWithLifecycle()
    val foreground =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.permissionsChanged() }
    val background =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.permissionsChanged() }
    // version が変わるたびに権限を読み直す
    val granted = remember(version) { viewModel.hasBackgroundLocationPermission }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(if (granted) R.drawable.ic_check_circle else R.drawable.ic_warning),
            contentDescription = null,
            tint = if (granted) StatusColors.ok else StatusColors.risk,
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_background_location), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_background_location_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted) {
            Text(stringResource(R.string.perm_granted), style = MaterialTheme.typography.labelMedium)
        } else {
            TextButton(
                onClick = {
                    if (viewModel.hasLocationPermission) {
                        background.launch(LocationProvider.BACKGROUND_PERMISSION)
                    } else {
                        foreground.launch(LocationProvider.PERMISSIONS)
                    }
                },
            ) { Text(stringResource(R.string.perm_action)) }
        }
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
    step: Long = TIME_STEP_MINUTES,
    onStep: (Long) -> Unit,
) {
    StepperRow(labelRes, value.hhmm(), onStep, step = step)
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

/** 出発時刻の表示時間帯は 6:50 のような半端な時刻を使うので 5 分刻み。 */
private const val FINE_TIME_STEP_MINUTES = 5L
