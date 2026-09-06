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
import androidx.compose.foundation.layout.Row
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
import com.kazuya.timtra.ui.common.countdownText
import com.kazuya.timtra.ui.common.hhmm
import com.kazuya.timtra.ui.common.statusLabel
import com.kazuya.timtra.ui.theme.GradientCard
import com.kazuya.timtra.ui.theme.StatusColors
import com.kazuya.timtra.ui.theme.TimTraCard
import com.kazuya.timtra.ui.theme.TimTraColors
import com.kazuya.timtra.ui.theme.TimTraTopBar
import com.kazuya.timtra.ui.theme.TopBarTitle
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

        val journey = state.journey
        if (journey == null) {
            Text(stringResource(R.string.home_empty), style = MaterialTheme.typography.bodyLarge)
        } else {
            // 1. 家を出る時刻と残り時間
            LeaveCard(state.now, journey)
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
            ArrivalRow(journey)
            journey.fallback?.let { FallbackCard(journey, it) }
            // 6. 次の候補
            state.next?.let { NextCandidateCard(it) }
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

@Composable
private fun Banner(
    text: String,
    color: Color,
) {
    Surface(color = color, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

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
            Text(
                text =
                    stringResource(
                        if (journey.bound == Bound.OUTBOUND) R.string.home_leave_home else R.string.home_leave_work,
                    ),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
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
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    TimTraCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun BusCard(
    journey: Journey,
    realtime: RealtimeState,
) {
    val trip = journey.bus.trip
    SectionCard(
        title = stringResource(R.string.home_section_bus),
        trailing = {
            Text(
                text =
                    if (trip.hasRouteNumber) {
                        stringResource(R.string.home_route_line, trip.routeShortName, trip.headsign)
                    } else {
                        stringResource(R.string.home_route_line_named, trip.routeDisplayName, trip.headsign)
                    },
                style = MaterialTheme.typography.labelMedium,
            )
        },
    ) {
        Text(
            text =
                stringResource(
                    R.string.home_leg_times,
                    trip.boardStop.name,
                    journey.bus.departureAt.hhmm(),
                    trip.alightStop.name,
                    journey.bus.arrivalAt.hhmm(),
                ),
            style = MaterialTheme.typography.titleMedium,
        )
        val platform = if (journey.bound == Bound.INBOUND) trip.boardStop.platformCode else trip.alightStop.platformCode
        if (!platform.isNullOrBlank()) {
            Text(stringResource(R.string.home_platform, platform), style = MaterialTheme.typography.bodyMedium)
        }
        if (journey.hasDelay) {
            Text(
                text = stringResource(R.string.home_delay_estimated, journey.busDelay.toMinutes()),
                color = StatusColors.tight,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.home_delay_arrival_estimated, journey.busArrivalEstimatedAt.hhmm()),
                color = StatusColors.tight,
                style = MaterialTheme.typography.bodyMedium,
            )
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
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.home_section_transfer),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            if (minutes >= 0) {
                                stringResource(R.string.home_transfer_margin, minutes)
                            } else {
                                stringResource(R.string.home_transfer_margin_negative, -minutes)
                            },
                        style = MaterialTheme.typography.titleLarge,
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
}

@Composable
private fun JrCard(journey: Journey) {
    val service = journey.train.service
    val (from, to) =
        when (journey.bound) {
            Bound.OUTBOUND -> "鳥取" to "宝木"
            Bound.INBOUND -> "宝木" to "鳥取"
        }
    SectionCard(
        title = stringResource(R.string.home_section_jr),
        trailing = { Text(service.trainId, style = MaterialTheme.typography.labelMedium) },
    ) {
        Text(
            text = stringResource(R.string.home_leg_times, from, journey.train.departureAt.hhmm(), to, journey.train.arrivalAt.hhmm()),
            style = MaterialTheme.typography.titleMedium,
        )
        if (service.platform.isNotBlank()) {
            Text(stringResource(R.string.home_jr_platform, service.platform), style = MaterialTheme.typography.bodyMedium)
        }
        if (service.note.isNotBlank()) {
            Text(service.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ArrivalRow(journey: Journey) {
    Text(
        text =
            stringResource(
                if (journey.bound == Bound.OUTBOUND) R.string.home_arrival_work else R.string.home_arrival_home,
                journey.arriveAt.hhmm(),
            ),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun FallbackCard(
    journey: Journey,
    fallback: Journey,
) {
    val title =
        when {
            journey.status == JourneyStatus.MISSED -> R.string.home_fallback_missed
            journey.bound == Bound.OUTBOUND -> R.string.home_fallback_outbound
            else -> R.string.home_fallback_inbound
        }
    TimTraCard(modifier = Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = TimTraColors.primary)
            JourneySummary(fallback)
        }
    }
}

@Composable
private fun NextCandidateCard(next: Journey) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TimTraCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_next_candidate), style = MaterialTheme.typography.labelLarge)
                    Text(
                        text =
                            stringResource(
                                R.string.home_next_candidate_summary,
                                next.leaveAt.hhmm(),
                                next.bus.departureAt.hhmm(),
                                next.train.departureAt.hhmm(),
                            ),
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
                    JourneySummary(next)
                }
            }
        }
    }
}

/** 代替案・次の候補で使う短い要約。 */
@Composable
private fun JourneySummary(journey: Journey) {
    val trip = journey.bus.trip
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    stringResource(
                        if (journey.bound == Bound.OUTBOUND) R.string.home_leave_home else R.string.home_leave_work,
                    ) + " " + journey.leaveAt.hhmm(),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.weight(1f))
            StatusBadge(journey.status)
        }
        Text(
            text =
                stringResource(R.string.home_section_bus) + " " +
                    stringResource(
                        R.string.home_leg_times,
                        trip.boardStop.name,
                        journey.bus.departureAt.hhmm(),
                        trip.alightStop.name,
                        journey.bus.arrivalAt.hhmm(),
                    ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text =
                stringResource(R.string.home_section_jr) + " " +
                    journey.train.departureAt.hhmm() + " → " + journey.train.arrivalAt.hhmm() +
                    "（" + journey.train.service.trainId + "）",
            style = MaterialTheme.typography.bodyMedium,
        )
        val minutes = journey.transferMargin.toMinutes()
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

private val WARNING_CONTAINER = Color(0xFFFFF4E0)

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
