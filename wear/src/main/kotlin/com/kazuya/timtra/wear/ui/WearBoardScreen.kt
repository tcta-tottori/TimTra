package com.kazuya.timtra.wear.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.kazuya.timtra.core.board.BoardPlace
import com.kazuya.timtra.core.board.Departure
import com.kazuya.timtra.wear.R
import com.kazuya.timtra.wear.board.BoardSnapshot
import com.kazuya.timtra.wear.board.PlaceDistance
import com.kazuya.timtra.wear.location.WearLocationProvider
import java.time.LocalDateTime

/**
 * 発車標（CLAUDE.md 7-4 の拡張）。添付デザインの流れをそのまま実装している。
 *
 * 現在地取得中 → 最寄りの確認 → 発車標。位置が取れなければ 駅・バス停の選択に落ちる。
 * 発車標は「次の発車まで N 分」を主役にし、その下に同じ地点の以降の便を並べる。
 */
@Composable
fun WearBoardScreen(
    onOpenJourney: () -> Unit,
    viewModel: WearBoardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(timeText = { TimeText() }) {
        Box(modifier = Modifier.fillMaxSize().background(WearColors.background)) {
            when (state.step) {
                BoardStep.LOCATING -> LocatingScreen(onCancel = viewModel::openPicker)
                BoardStep.CONFIRM ->
                    ConfirmScreen(state = state, onConfirm = viewModel::confirm, onPick = viewModel::openPicker)
                BoardStep.PICKER ->
                    PickerScreen(
                        state = state,
                        onPick = viewModel::pick,
                        onUseLocation = viewModel::useLocation,
                        onBack = viewModel::closePicker,
                    )
                BoardStep.BOARD ->
                    BoardScreen(
                        state = state,
                        onPick = viewModel::openPicker,
                        onOpenJourney = onOpenJourney,
                        onLocationGranted = viewModel::start,
                    )
            }
        }
    }
}

// ---------------------------------------------------------------- 現在地を取得中

@Composable
private fun LocatingScreen(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                indicatorColor = WearColors.gradientStart,
                trackColor = WearColors.outline,
                strokeWidth = 3.dp,
            )
            Icon(
                painter = painterResource(R.drawable.ic_place),
                contentDescription = null,
                tint = WearColors.accentLight,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.locating_title),
            style = MaterialTheme.typography.title3,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.locating_sub),
            style = MaterialTheme.typography.caption3,
            color = WearColors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        PillButton(text = stringResource(R.string.action_cancel), iconRes = R.drawable.ic_close, onClick = onCancel)
    }
}

// ---------------------------------------------------------------- 最寄りの確認

@Composable
private fun ConfirmScreen(
    state: BoardUiState,
    onConfirm: () -> Unit,
    onPick: () -> Unit,
) {
    val resolution = state.resolution ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_place),
                contentDescription = null,
                tint = WearColors.accentLight,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.nearest_title),
                style = MaterialTheme.typography.caption2,
                color = WearColors.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(resolution.place.nameRes()),
            style = MaterialTheme.typography.title2,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        resolution.distanceMeters?.let {
            Text(text = distanceLabel(it), style = MaterialTheme.typography.caption1, color = WearColors.accentLight)
        }
        Spacer(Modifier.height(10.dp))
        GradientButton(text = stringResource(R.string.action_use_this), onClick = onConfirm)
        Spacer(Modifier.height(6.dp))
        PillButton(
            text = stringResource(R.string.action_pick_place),
            iconRes = R.drawable.ic_my_location,
            onClick = onPick,
        )
    }
}

// ---------------------------------------------------------------- 発車標

@Composable
private fun BoardScreen(
    state: BoardUiState,
    onPick: () -> Unit,
    onOpenJourney: () -> Unit,
    onLocationGranted: () -> Unit,
) {
    val s = state.snapshot ?: return
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item { PlacePill(s.place) }
        item {
            Text(
                text = stringResource(s.place.directionRes()),
                style = MaterialTheme.typography.caption3,
                color = WearColors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val next = s.next
        if (next == null) {
            item { Caption(stringResource(R.string.board_empty)) }
        } else {
            item { NextDeparture(s, next) }
            item { NextPills(s.later) }
            item { Caption(stringResource(R.string.board_later_label)) }
            s.departures.forEachIndexed { index, departure ->
                item { DepartureRow(departure = departure, now = s.now, highlighted = index == 0) }
            }
        }
        item { Caption(stringResource(state.resolution?.basis?.labelRes() ?: R.string.basis_manual)) }
        if (!state.locationPermitted) item { LocationPrompt(onLocationGranted) }
        item {
            PillButton(
                text = stringResource(R.string.action_pick_place),
                iconRes = R.drawable.ic_my_location,
                onClick = onPick,
            )
        }
        item {
            PillButton(
                text = stringResource(R.string.nav_journey),
                iconRes = R.drawable.ic_schedule,
                onClick = onOpenJourney,
            )
        }
    }
}

/** デザイン 1 枚目の主役: 丸いアイコン + 「次の発車まで NN 分」 + 発時刻と行き先。 */
@Composable
private fun NextDeparture(
    s: BoardSnapshot,
    next: Departure,
) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(WearColors.surface)
                        .border(2.dp, WearColors.gradientStart, CircleShape),
            ) {
                Icon(
                    painter = painterResource(next.mode.iconRes()),
                    contentDescription = null,
                    tint = WearColors.accentLight,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.board_next_in),
                    style = MaterialTheme.typography.caption3,
                    color = WearColors.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = context.countdownValue(s.now, next.at),
                        fontSize = COUNTDOWN_SP.sp,
                        fontWeight = FontWeight.Bold,
                        color = WearColors.accentLight,
                    )
                    Text(
                        text = context.countdownUnit(s.now, next.at),
                        style = MaterialTheme.typography.caption2,
                        color = WearColors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp, start = 2.dp),
                    )
                }
            }
        }
        Text(
            text = stringResource(R.string.board_depart_at, next.at.hhmm()),
            style = MaterialTheme.typography.title3,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.board_headsign_line, next.headsign, next.line),
            style = MaterialTheme.typography.caption3,
            color = WearColors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/** 「つぎ 8:32 / そのつぎ 8:56」の小さなピル 2 つ。 */
@Composable
private fun NextPills(later: List<Departure>) {
    if (later.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        SmallPill(stringResource(R.string.board_next_short), later[0].at.hhmm(), Modifier.weight(1f))
        later.getOrNull(1)?.let {
            SmallPill(stringResource(R.string.board_next_next_short), it.at.hhmm(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun SmallPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(WearColors.surface)
                .border(1.dp, WearColors.outline, RoundedCornerShape(12.dp))
                .padding(vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.caption3, color = WearColors.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.caption1, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** 一覧の 1 行: 時刻 / 行き先 / 残り時間。次の便は青く塗る（デザイン 2 枚目）。 */
@Composable
private fun DepartureRow(
    departure: Departure,
    now: LocalDateTime,
    highlighted: Boolean,
) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .then(if (highlighted) Modifier.background(WearColors.primaryGradient) else Modifier.background(WearColors.surface))
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = departure.at.hhmm(),
            style = MaterialTheme.typography.caption1,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(42.dp),
        )
        Text(
            text = stringResource(R.string.board_headsign, departure.headsign),
            style = MaterialTheme.typography.caption2,
            color = if (highlighted) Color.White else WearColors.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = context.compactCountdown(now, departure.at),
            style = MaterialTheme.typography.caption2,
            color = if (highlighted) Color.White else WearColors.accentLight,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- 駅・バス停の選択

@Composable
private fun PickerScreen(
    state: BoardUiState,
    onPick: (BoardPlace) -> Unit,
    onUseLocation: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.nav_back),
                    tint = WearColors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable(onClick = onBack),
                )
                Text(
                    text = stringResource(R.string.picker_title),
                    style = MaterialTheme.typography.caption1,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
            }
        }
        item { Caption(stringResource(R.string.picker_nearby)) }
        state.nearby.forEach { entry ->
            item { PlaceRow(entry = entry, selected = state.manual == entry.place, onClick = { onPick(entry.place) }) }
        }
        item {
            PillButton(
                text = stringResource(R.string.board_use_location),
                iconRes = R.drawable.ic_my_location,
                onClick = onUseLocation,
            )
        }
    }
}

@Composable
private fun PlaceRow(
    entry: PlaceDistance,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .then(if (selected) Modifier.background(WearColors.primaryGradient) else Modifier.background(WearColors.surface))
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(entry.place.iconRes()),
            contentDescription = null,
            tint = if (selected) Color.White else WearColors.accentLight,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(entry.place.nameRes()),
                style = MaterialTheme.typography.caption1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = stringResource(entry.place.directionRes()),
                style = MaterialTheme.typography.caption3,
                color = if (selected) Color.White else WearColors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        entry.distanceMeters?.let {
            Text(
                text = distanceLabel(it),
                style = MaterialTheme.typography.caption3,
                color = if (selected) Color.White else WearColors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (selected) {
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- 共通部品

/** 画面上部の地点ピル（📍 鳥取駅）。 */
@Composable
private fun PlacePill(place: BoardPlace) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(WearColors.surface)
                .border(1.dp, WearColors.outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_place),
            contentDescription = null,
            tint = WearColors.accentLight,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(place.nameRes()),
            style = MaterialTheme.typography.caption1,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun GradientButton(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(WearColors.primaryGradient)
                .clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.button, color = Color.White, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.caption1, maxLines = 1) },
        icon = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = WearColors.accentLight,
                modifier = Modifier.size(18.dp),
            )
        },
        colors = ChipDefaults.chipColors(backgroundColor = WearColors.surface, contentColor = WearColors.onSurface),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LocationPrompt(onGranted: () -> Unit) {
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.any { it }) onGranted()
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        Caption(stringResource(R.string.board_location_prompt))
        PillButton(
            text = stringResource(R.string.board_allow_location),
            iconRes = R.drawable.ic_my_location,
            onClick = { launcher.launch(WearLocationProvider.PERMISSIONS) },
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        color = WearColors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.caption3,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

/** 距離の表示（約 350m / 約 1.2km）。 */
@Composable
private fun distanceLabel(meters: Double): String =
    if (meters < METERS_IN_KM) {
        stringResource(R.string.distance_meters, meters.toInt())
    } else {
        stringResource(R.string.distance_kilometers, meters / METERS_IN_KM)
    }

private const val METERS_IN_KM = 1_000.0
private const val COUNTDOWN_SP = 40f
