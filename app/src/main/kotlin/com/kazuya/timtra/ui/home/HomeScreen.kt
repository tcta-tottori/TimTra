package com.kazuya.timtra.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kazuya.timtra.R
import com.kazuya.timtra.core.journey.BoundBasis
import com.kazuya.timtra.core.journey.CommuteSettings
import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.journey.JourneyStatus
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.data.realtime.RealtimeState
import com.kazuya.timtra.location.LocationProvider
import com.kazuya.timtra.ui.common.CircleIcon
import com.kazuya.timtra.ui.common.InfoPill
import com.kazuya.timtra.ui.common.ModeBadge
import com.kazuya.timtra.ui.common.ModeChip
import com.kazuya.timtra.ui.common.TransitMode
import com.kazuya.timtra.ui.common.countdownText
import com.kazuya.timtra.ui.common.destinationIconRes
import com.kazuya.timtra.ui.common.hhmm
import com.kazuya.timtra.ui.common.originIconRes
import com.kazuya.timtra.ui.common.statusLabel
import com.kazuya.timtra.ui.theme.GradientCard
import com.kazuya.timtra.ui.theme.StatusColors
import com.kazuya.timtra.ui.theme.TimTraCard
import com.kazuya.timtra.ui.theme.TimTraColors
import com.kazuya.timtra.ui.theme.TimTraTopBar
import com.kazuya.timtra.ui.theme.TopBarTitle
import com.kazuya.timtra.ui.theme.TransitColors
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDrawer: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 権限画面から戻ったときに状態を取り直す
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TimTraTopBar(
                title = { TopBarTitle(stringResource(R.string.nav_home)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.action_menu))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
        floatingActionButton = {
            val ready = state as? HomeUiState.Ready
            if (ready != null) {
                FloatingActionButton(
                    onClick = { viewModel.setBound(if (ready.bound == Bound.OUTBOUND) Bound.INBOUND else Bound.OUTBOUND) },
                    containerColor = TimTraColors.primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                ) {
                    Icon(painterResource(R.drawable.ic_swap_horiz), contentDescription = stringResource(R.string.home_toggle_bound))
                }
            }
        },
    ) { padding ->
        when (val s = state) {
            HomeUiState.Loading ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            is HomeUiState.Ready ->
                HomeContent(
                    state = s,
                    onBoundChange = viewModel::setBound,
                    onPermissionsChanged = viewModel::refresh,
                    onResumeReminders = viewModel::resumeTrainReminders,
                    modifier = Modifier.padding(padding),
                )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Ready,
    onBoundChange: (Bound?) -> Unit,
    onPermissionsChanged: () -> Unit,
    onResumeReminders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!state.permissions.allGranted) PermissionsCard(state.permissions, onChanged = onPermissionsChanged)
        if (!state.locationPermitted) LocationPrompt(onChanged = onPermissionsChanged)
        if (state.dayOff) Banner(stringResource(R.string.home_day_off), MaterialTheme.colorScheme.primaryContainer)
        if (state.sampleBus) Banner(stringResource(R.string.home_sample_bus_warning), WARNING_CONTAINER)
        if (state.sampleJr) Banner(stringResource(R.string.home_sample_jr_warning), WARNING_CONTAINER)
        if (state.reminderOffToday) ReminderOffBanner(onResume = onResumeReminders)

        val journey = state.journey
        if (journey == null) {
            Text(stringResource(R.string.home_empty), style = MaterialTheme.typography.bodyLarge)
            RouteMapCard(state.landmarks, state.location, state.bound, state.locationPermitted)
        } else {
            // 1. 家 / 職場を出る時刻と残り時間。決まった時間帯の外では最初の便の発車を主役にする
            if (state.showLeaveTime) {
                LeaveCard(state.now, journey)
            } else {
                NextDepartureCard(state.now, journey, state.settings)
            }
            // 地図: 現在地と、駅・バス停までの距離
            RouteMapCard(state.landmarks, state.location, state.bound, state.locationPermitted)
            when (journey.bound) {
                Bound.OUTBOUND -> {
                    // 2. バス 3. 乗り継ぎ 4. JR
                    BusCard(journey, state.realtime)
                    TransferCard(journey, state.settings)
                    JrCard(journey)
                }
                Bound.INBOUND -> {
                    JrCard(journey)
                    TransferCard(journey, state.settings)
                    BusCard(journey, state.realtime)
                }
            }
            // 5. 到着予測
            ArrivalCard(journey)
            journey.fallback?.let { FallbackCard(journey, it, state.showLeaveTime) }
            // 6. 次の候補
            state.next?.let { NextCandidateCard(it, state.showLeaveTime) }
        }
        BasisFooter(state, onBoundChange)
        Spacer(Modifier.height(8.dp))
    }
}

/** 脚注: 更新時刻と、往路/復路をどう決めたか。手動中はここから自動に戻す。 */
@Composable
private fun BasisFooter(
    state: HomeUiState.Ready,
    onBoundChange: (Bound?) -> Unit,
) {
    val basis =
        stringResource(
            when (state.boundBasis) {
                BoundBasis.MANUAL -> R.string.home_basis_manual
                BoundBasis.NEAR_HOME -> R.string.home_basis_near_home
                BoundBasis.NEAR_WORK -> R.string.home_basis_near_work
                BoundBasis.TIME_OF_DAY -> R.string.home_basis_time
            },
        )
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.home_updated_at, state.now.hhmm()) + " ・ " + basis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (state.boundBasis == BoundBasis.MANUAL) {
            TextButton(onClick = { onBoundChange(null) }) {
                Text(stringResource(R.string.home_basis_reset), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 位置情報が未許可のときだけ出す小さな案内。閉じればこの画面の間は出さない。 */
@Composable
private fun LocationPrompt(onChanged: () -> Unit) {
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (dismissed) return
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onChanged() }
    TimTraCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.home_location_prompt), style = MaterialTheme.typography.bodySmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.home_location_dismiss)) }
                TextButton(onClick = { launcher.launch(LocationProvider.PERMISSIONS) }) {
                    Text(stringResource(R.string.home_location_allow))
                }
            }
        }
    }
}

/** 勤務先を離れて「次の電車」リマインダーが止まったことを示す。戻ったときは再開できる。 */
@Composable
private fun ReminderOffBanner(onResume: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_reminder_off_today),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )
            TextButton(onClick = onResume) { Text(stringResource(R.string.home_reminder_resume)) }
        }
    }
}

@Composable
private fun Banner(
    text: String,
    color: Color,
) {
    Surface(color = color, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

/** 主役: 家 / 職場を出る時刻。決まった時間帯（LeaveDisplayPolicy）のときだけ出る。 */
@Composable
private fun LeaveCard(
    now: LocalDateTime,
    journey: Journey,
) {
    GradientCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIcon(iconRes = journey.bound.originIconRes(), color = TimTraColors.pillFill, size = 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(if (journey.bound == Bound.OUTBOUND) R.string.home_leave_home else R.string.home_leave_work),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Text(
                text = journey.leaveAt.hhmm(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = countdownText(now, journey.leaveAt),
                style = MaterialTheme.typography.titleLarge,
                color = TimTraColors.accentLight,
            )
            Spacer(Modifier.height(12.dp))
            HeroLegStrip(journey)
        }
    }
}

/**
 * 出発時刻を出さない時間帯の主役: 最初の便（往路はバス、復路は JR）の発車時刻と残り時間。
 * 「家を出る」「職場を出る」の文字はここでは出さない。
 */
@Composable
private fun NextDepartureCard(
    now: LocalDateTime,
    journey: Journey,
    settings: CommuteSettings,
) {
    val outbound = journey.bound == Bound.OUTBOUND
    val mode = if (outbound) TransitMode.BUS else TransitMode.JR
    val departAt = if (outbound) journey.busDepartureEstimatedAt else journey.train.departureAt
    val from = if (outbound) journey.bus.trip.boardStop.name else HOUGI
    GradientCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeBadge(mode = mode, size = 28.dp, color = TimTraColors.pillFill)
                Spacer(Modifier.width(8.dp))
                Text(
                    text =
                        stringResource(if (outbound) R.string.home_next_departure_bus else R.string.home_next_departure_jr) +
                            "  " + stringResource(R.string.home_departs_from, from),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Text(
                text = departAt.hhmm(),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = countdownText(now, departAt),
                style = MaterialTheme.typography.titleLarge,
                color = TimTraColors.accentLight,
            )
            if (outbound && journey.hasDelay) {
                Text(
                    text = stringResource(R.string.home_delay_estimated, journey.busDelay.toMinutes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                )
            }
            Spacer(Modifier.height(12.dp))
            HeroLegStrip(journey)
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    if (outbound) {
                        stringResource(
                            R.string.home_leave_hidden_home,
                            settings.leaveHomeDisplayStart.hhmm(),
                            settings.leaveHomeDisplayEnd.hhmm(),
                        )
                    } else {
                        stringResource(R.string.home_leave_hidden_work, settings.leaveWorkDisplayStart.hhmm())
                    },
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 主役カードの下段: バスと JR の発車時刻をアイコン付きで 1 行に。 */
@Composable
private fun HeroLegStrip(journey: Journey) {
    val legs =
        when (journey.bound) {
            Bound.OUTBOUND -> listOf(TransitMode.BUS to journey.busDepartureEstimatedAt, TransitMode.JR to journey.train.departureAt)
            Bound.INBOUND -> listOf(TransitMode.JR to journey.train.departureAt, TransitMode.BUS to journey.busDepartureEstimatedAt)
        }
    Row(
        modifier =
            Modifier
                .background(TimTraColors.pillFill, RoundedCornerShape(50))
                .border(1.dp, TimTraColors.pillBorder, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        legs.forEachIndexed { index, (mode, at) ->
            if (index > 0) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 10.dp).size(16.dp),
                )
            }
            Icon(
                painter = painterResource(mode.iconRes),
                contentDescription = stringResource(mode.labelRes),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = at.hhmm(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.width(10.dp))
        StatusDot(journey.status)
    }
}

@Composable
private fun StatusDot(status: JourneyStatus) {
    Box(
        modifier =
            Modifier
                .size(12.dp)
                .background(Color.White, CircleShape)
                .padding(2.dp)
                .background(StatusColors.of(status), CircleShape),
    )
}

/**
 * 区間カードの共通レイアウト。左にアイコンの丸、右上に系統などのピル、
 * 中央に「発 → 着」の大きな時刻。
 */
@Composable
private fun LegCard(
    mode: TransitMode,
    title: String,
    from: String,
    departure: String,
    to: String,
    arrival: String,
    chips: @Composable RowScope.() -> Unit = {},
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    TimTraCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ModeBadge(mode = mode, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = mode.color, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = chips)
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TimeColumn(time = departure, place = from, label = stringResource(R.string.home_dep_label), modifier = Modifier.weight(1f))
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_forward),
                    contentDescription = null,
                    tint = mode.color.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 6.dp).size(20.dp),
                )
                TimeColumn(
                    time = arrival,
                    place = to,
                    label = stringResource(R.string.home_arr_label),
                    modifier = Modifier.weight(1f),
                    alignEnd = true,
                )
            }
            extra()
        }
    }
}

@Composable
private fun TimeColumn(
    time: String,
    place: String,
    label: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Column(modifier = modifier, horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
        Text(time, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "$place $label",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun BusCard(
    journey: Journey,
    realtime: RealtimeState,
) {
    val trip = journey.bus.trip
    val platform = if (journey.bound == Bound.INBOUND) trip.boardStop.platformCode else trip.alightStop.platformCode
    LegCard(
        mode = TransitMode.BUS,
        title = stringResource(R.string.mode_bus_full),
        from = trip.boardStop.name,
        departure = journey.bus.departureAt.hhmm(),
        to = trip.alightStop.name,
        arrival = journey.bus.arrivalAt.hhmm(),
        chips = {
            InfoPill(
                text =
                    if (trip.hasRouteNumber) {
                        stringResource(R.string.home_route_line, trip.routeShortName, trip.headsign)
                    } else {
                        stringResource(R.string.home_route_line_named, trip.routeDisplayName, trip.headsign)
                    },
            )
            if (!platform.isNullOrBlank()) {
                InfoPill(
                    text = stringResource(R.string.home_platform, platform),
                    color = TransitColors.bus,
                    container = TransitColors.bus.copy(alpha = 0.12f),
                )
            }
        },
    ) {
        if (journey.hasDelay) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_warning),
                    contentDescription = null,
                    tint = StatusColors.tight,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text =
                        stringResource(R.string.home_delay_estimated, journey.busDelay.toMinutes()) + " ・ " +
                            stringResource(R.string.home_delay_arrival_estimated, journey.busArrivalEstimatedAt.hhmm()),
                    color = StatusColors.tight,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        RealtimeStatusLine(journey, realtime)
    }
}

/** GTFS-RT の取得状態。遅延が出ているときは上に「約 N 分遅れ（推定）」が出るので、ここは補足だけ。 */
@Composable
private fun RealtimeStatusLine(
    journey: Journey,
    realtime: RealtimeState,
) {
    val fetched = realtime.fetchedAt?.let { LocalDateTime.ofInstant(it, ZoneId.systemDefault()).hhmm() }
    val hasEstimate = realtime.estimates.any { it.tripId == journey.bus.trip.tripId }
    val text =
        when (realtime.status) {
            RealtimeState.Status.NOT_CONFIGURED -> stringResource(R.string.home_rt_not_configured)
            RealtimeState.Status.IDLE -> stringResource(R.string.home_rt_idle)
            RealtimeState.Status.OK, RealtimeState.Status.THROTTLED ->
                when {
                    fetched == null -> stringResource(R.string.home_rt_loading)
                    journey.hasDelay -> null
                    hasEstimate -> stringResource(R.string.home_rt_on_time, fetched)
                    else -> stringResource(R.string.home_rt_no_vehicle, fetched)
                }
            RealtimeState.Status.ERROR ->
                if (fetched == null) stringResource(R.string.home_rt_error) else stringResource(R.string.home_rt_error_with_time, fetched)
        }
    if (text != null) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (realtime.status == RealtimeState.Status.ERROR) StatusColors.risk else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransferCard(
    journey: Journey,
    settings: CommuteSettings,
) {
    val minutes = journey.transferMargin.toMinutes()
    val color = StatusColors.of(journey.status)
    TimTraCard(modifier = Modifier.fillMaxWidth(), borderColor = color.copy(alpha = 0.45f)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIcon(iconRes = R.drawable.ic_transfer, color = TransitColors.walk, size = 34.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_transfer_at),
                    style = MaterialTheme.typography.titleSmall,
                    color = TransitColors.walk,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text =
                        if (minutes >= 0) {
                            stringResource(R.string.home_transfer_margin_short, minutes)
                        } else {
                            stringResource(R.string.home_transfer_margin_short_negative, -minutes)
                        },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(
                    text = stringResource(R.string.home_transfer_note, settings.transferBusToJr.toMinutes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            StatusBadge(journey.status, large = true)
        }
    }
}

@Composable
private fun JrCard(journey: Journey) {
    val service = journey.train.service
    val (from, to) =
        when (journey.bound) {
            Bound.OUTBOUND -> TOTTORI to HOUGI
            Bound.INBOUND -> HOUGI to TOTTORI
        }
    LegCard(
        mode = TransitMode.JR,
        title = stringResource(R.string.mode_jr_full),
        from = from,
        departure = journey.train.departureAt.hhmm(),
        to = to,
        arrival = journey.train.arrivalAt.hhmm(),
        chips = {
            InfoPill(text = service.trainId)
            if (service.platform.isNotBlank()) {
                InfoPill(
                    text = stringResource(R.string.home_jr_platform, service.platform),
                    color = TransitColors.jr,
                    container = TransitColors.jr.copy(alpha = 0.12f),
                )
            }
        },
    ) {
        if (service.note.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(service.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ArrivalCard(journey: Journey) {
    TimTraCard(modifier = Modifier.fillMaxWidth(), containerColor = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIcon(iconRes = journey.bound.destinationIconRes(), color = TransitColors.place, size = 30.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                text =
                    stringResource(
                        if (journey.bound ==
                            Bound.OUTBOUND
                        ) {
                            R.string.home_arrival_label_work
                        } else {
                            R.string.home_arrival_label_home
                        },
                    ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(journey.arriveAt.hhmm(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FallbackCard(
    journey: Journey,
    fallback: Journey,
    showLeaveTime: Boolean,
) {
    val title =
        when {
            journey.status == JourneyStatus.MISSED -> R.string.home_fallback_missed
            journey.bound == Bound.OUTBOUND -> R.string.home_fallback_outbound
            else -> R.string.home_fallback_inbound
        }
    TimTraCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = TimTraColors.primary)
            JourneySummary(fallback, showLeaveTime)
        }
    }
}

@Composable
private fun NextCandidateCard(
    next: Journey,
    showLeaveTime: Boolean,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TimTraCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_next_candidate), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text =
                            when {
                                showLeaveTime ->
                                    stringResource(
                                        R.string.home_next_candidate_summary,
                                        next.leaveAt.hhmm(),
                                        next.bus.departureAt.hhmm(),
                                        next.train.departureAt.hhmm(),
                                    )
                                next.bound == Bound.OUTBOUND ->
                                    stringResource(
                                        R.string.home_next_candidate_summary_no_leave,
                                        next.bus.departureAt.hhmm(),
                                        next.train.departureAt.hhmm(),
                                    )
                                else ->
                                    stringResource(
                                        R.string.home_next_candidate_summary_no_leave_inbound,
                                        next.train.departureAt.hhmm(),
                                        next.bus.departureAt.hhmm(),
                                    )
                            },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Icon(
                    painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                    contentDescription = stringResource(if (expanded) R.string.home_collapse else R.string.home_expand),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    JourneySummary(next, showLeaveTime)
                }
            }
        }
    }
}

/** 代替案・次の候補で使う短い要約。出発時刻は表示時間帯のときだけ。 */
@Composable
private fun JourneySummary(
    journey: Journey,
    showLeaveTime: Boolean,
) {
    val trip = journey.bus.trip
    val minutes = journey.transferMargin.toMinutes()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showLeaveTime) {
                Icon(
                    painter = painterResource(journey.bound.originIconRes()),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text =
                        stringResource(if (journey.bound == Bound.OUTBOUND) R.string.home_leave_home else R.string.home_leave_work) +
                            " " + journey.leaveAt.hhmm(),
                    style = MaterialTheme.typography.titleSmall,
                )
            } else {
                Text(
                    text =
                        if (minutes >= 0) {
                            stringResource(R.string.home_transfer_margin, minutes)
                        } else {
                            stringResource(R.string.home_transfer_margin_negative, -minutes)
                        },
                    style = MaterialTheme.typography.titleSmall,
                    color = StatusColors.of(journey.status),
                )
            }
            Spacer(Modifier.weight(1f))
            StatusBadge(journey.status)
        }
        SummaryLeg(
            mode = TransitMode.BUS,
            text =
                stringResource(
                    R.string.home_leg_times,
                    trip.boardStop.name,
                    journey.bus.departureAt.hhmm(),
                    trip.alightStop.name,
                    journey.bus.arrivalAt.hhmm(),
                ),
        )
        SummaryLeg(
            mode = TransitMode.JR,
            text = journey.train.departureAt.hhmm() + " → " + journey.train.arrivalAt.hhmm() + "（" + journey.train.service.trainId + "）",
        )
        if (showLeaveTime) {
            Text(
                text =
                    if (minutes >= 0) {
                        stringResource(R.string.home_transfer_margin, minutes)
                    } else {
                        stringResource(R.string.home_transfer_margin_negative, -minutes)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = StatusColors.of(journey.status),
            )
        }
    }
}

@Composable
private fun SummaryLeg(
    mode: TransitMode,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ModeChip(mode)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private val WARNING_CONTAINER = Color(0xFFFFF4E0)
private const val TOTTORI = "鳥取"
private const val HOUGI = "宝木"

@Composable
fun StatusBadge(
    status: JourneyStatus,
    large: Boolean = false,
) {
    val color = StatusColors.of(status)
    val shape = RoundedCornerShape(50)
    Row(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.12f), shape)
                .border(1.5.dp, color, shape)
                .padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 8.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(if (large) 10.dp else 7.dp).background(color, CircleShape))
        Spacer(Modifier.width(if (large) 8.dp else 6.dp))
        Text(
            text = statusLabel(status),
            color = color,
            style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
