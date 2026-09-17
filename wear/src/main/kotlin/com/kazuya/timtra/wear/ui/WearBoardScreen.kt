package com.kazuya.timtra.wear.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
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
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.kazuya.timtra.core.board.BoardPlace
import com.kazuya.timtra.core.board.Departure
import com.kazuya.timtra.wear.R
import com.kazuya.timtra.wear.board.BoardSnapshot
import com.kazuya.timtra.wear.board.DayBoard
import com.kazuya.timtra.wear.board.DaySelection
import com.kazuya.timtra.wear.board.PlaceDistance
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val dateLabelFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d(E)")

/**
 * 時計の画面。ホームは 1 画面に収め、そこから一覧へ広げる（CLAUDE.md 7-4 の拡張）。
 *
 * - ホーム: 現在時刻のすぐ下に地点バッジ → 行き先 → 大きなアイコンと「次の発車まで」→ 下部に発時刻。
 * - 発時刻をタップ、またはリューズ時計回り → この先の発車（現在時刻〜当日終電）。
 * - その一覧の最下部の「時刻表を表示する」 → 今日 / 平日 / 土日祝 を切り替えられる時刻表。
 * - 上から下へスワイプ、またはリューズ反時計回り → メニュー（駅・バス停の一覧）。
 * - 一覧からホームへは、上端または下端でさらに送る（リューズ / スワイプ）。右へスワイプでも戻る。
 */
@Composable
fun WearBoardScreen(viewModel: WearBoardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state.step) {
        BoardStep.LOADING -> LoadingScreen()
        BoardStep.HOME ->
            HomeScreen(
                state = state,
                onOpenUpcoming = { viewModel.openUpcoming() },
                onOpenMenu = viewModel::openMenu,
            )
        BoardStep.UPCOMING, BoardStep.TIMETABLE -> {
            BackHandler(onBack = viewModel::backHome)
            BoardListScreen(
                board = state.dayBoard,
                pinned = state.pinned,
                onSelectDay = viewModel::selectDay,
                onTogglePinned = viewModel::togglePinned,
                onOpenTimetable = { viewModel.openTimetable(it) },
                onBack = viewModel::backHome,
            )
        }
        BoardStep.MENU -> {
            BackHandler(onBack = viewModel::backHome)
            MenuScreen(
                places = state.places,
                pinned = state.pinned,
                onPick = { viewModel.openTimetable(it) },
                onBack = viewModel::backHome,
            )
        }
    }
}

@Composable
private fun LoadingScreen() {
    Scaffold(timeText = { TimeText() }) {
        Box(
            modifier = Modifier.fillMaxSize().background(WearColors.background),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(indicatorColor = WearColors.gradientStart, trackColor = WearColors.outline)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.locating_title),
                    style = MaterialTheme.typography.caption2,
                    color = WearColors.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- ホーム（1 画面）

/**
 * 提供されたモックの写し。上から
 * 📍地点名 → 行き先 → リングに包まれたバス / 電車アイコンと「次の便まで NN 分」→ 発時刻。
 * スクロールはさせず、1 画面に収める。
 *
 * 発時刻は角丸カードに乗せない。丸い文字盤では隅が切れて見栄えが悪いので、
 * 画面の横幅いっぱいに敷いた下からのグロー（[WearColors.departureGlow]）の上に置く。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HomeScreen(
    state: BoardUiState,
    onOpenUpcoming: () -> Unit,
    onOpenMenu: () -> Unit,
) {
    val s = state.snapshot ?: return
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Scaffold(timeText = { TimeText() }) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(WearColors.background)
                    // リューズ: 時計回りでこの先の発車、反時計回りでメニュー
                    .onRotaryScrollEvent { event ->
                        if (event.verticalScrollPixels > 0) onOpenUpcoming() else onOpenMenu()
                        true
                    }.focusRequester(focusRequester)
                    .focusable()
                    // 上から下へのスワイプでメニュー（指を離したときに判定する）
                    .pointerInput(Unit) {
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = { if (dragged > SWIPE_DOWN_THRESHOLD_PX) onOpenMenu() },
                        ) { _, dragAmount -> dragged += dragAmount }
                    },
        ) {
            val next = s.next
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = CENTER_LIFT_DP.dp),
            ) {
                PlaceHeader(s.place)
                Spacer(Modifier.height(8.dp))
                if (next == null) {
                    Text(
                        text = stringResource(R.string.board_empty),
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CountdownRing(s, next)
                }
            }
            if (next != null) {
                DepartureGlow(next, onClick = onOpenUpcoming, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** 📍 + 地点名（大きく）と、その下の行き先。 */
@Composable
private fun PlaceHeader(place: BoardPlace) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_place),
                contentDescription = null,
                tint = WearColors.gradientStart,
                modifier = Modifier.size(PLACE_ICON_DP.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(place.nameRes()),
                fontSize = PLACE_SP.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
        }
        Text(
            text = stringResource(place.directionRes()),
            fontSize = DIRECTION_SP.sp,
            color = WearColors.onSurfaceSubtle,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * 中央。左に「次の便まで」と大きな残り時間、右にリングで囲んだバス / 電車アイコンを置く。
 * リングは砂時計と同じで、発車 15 分前から減りはじめ発車時刻で 0 になる
 * （core の `CountdownGauge`。[BoardSnapshot.gauge]）。
 */
@Composable
private fun CountdownRing(
    s: BoardSnapshot,
    next: Departure,
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.Start) {
            Text(
                text = stringResource(R.string.board_next_in),
                fontSize = CAPTION_SP.sp,
                color = WearColors.onSurfaceSubtle,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = context.countdownValue(s.now, next.at),
                    fontSize = COUNTDOWN_SP.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
                Text(
                    text = context.countdownUnit(s.now, next.at),
                    fontSize = UNIT_SP.sp,
                    fontWeight = FontWeight.Bold,
                    color = WearColors.accentLight,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 4.dp, start = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(RING_DP.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = RING_STROKE_DP.dp.toPx()
                val topLeft = Offset(stroke / 2f, stroke / 2f)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val style = Stroke(width = stroke, cap = StrokeCap.Round)
                drawArc(
                    color = WearColors.track,
                    startAngle = RING_START_ANGLE,
                    sweepAngle = FULL_TURN,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = style,
                )
                if (s.gauge > 0f) {
                    drawArc(
                        brush = WearColors.ringGradient,
                        startAngle = RING_START_ANGLE,
                        sweepAngle = FULL_TURN * s.gauge,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = style,
                    )
                }
            }
            Icon(
                painter = painterResource(next.mode.iconRes()),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(RING_ICON_DP.dp),
            )
        }
    }
}

/** 画面下部いっぱいのグローに乗せた発時刻（行き先 + H:MM）。タップでこの先の発車が開く。 */
@Composable
private fun DepartureGlow(
    next: Departure,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(GLOW_HEIGHT_FRACTION)
                .background(WearColors.departureGlow)
                .clickable(onClick = onClick)
                .padding(start = 24.dp, end = 24.dp, bottom = GLOW_BOTTOM_DP.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = next.headsign,
                fontSize = HEADSIGN_SP.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = stringResource(R.string.board_bound_for),
                fontSize = CAPTION_SP.sp,
                color = WearColors.onSurfaceSubtle,
                maxLines = 1,
            )
        }
        Text(
            text = next.at.hhmm(),
            fontSize = DEPART_SP.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- 一覧（この先の発車 / 時刻表）

/**
 * 1 枚の一覧。[DayBoard.onlyUpcoming] で 2 つの顔を持つ。
 *
 * - この先の発車（ホームから開く）: 現在時刻〜当日終電。最下部に「時刻表を表示する」。
 * - 時刻表: その日の始発〜終電。上部に 今日 / 平日 / 土日祝 のバッジ。
 *
 * どちらも現在時刻より前はグレー、次の便は青、それ以降は通常。
 * 右にスクロールバー（[PositionIndicator]）を出し、端でさらに送るとホームへ戻る。
 */
@Composable
private fun BoardListScreen(
    board: DayBoard?,
    pinned: BoardPlace?,
    onSelectDay: (DaySelection) -> Unit,
    onTogglePinned: (BoardPlace) -> Unit,
    onOpenTimetable: (BoardPlace) -> Unit,
    onBack: () -> Unit,
) {
    if (board == null) return
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current
    // 開いたら「次の便」が真ん中に来るようにする
    LaunchedEffect(board.place, board.selection, board.onlyUpcoming, board.departures.size) {
        val index = board.nextIndex
        if (index >= 0) runCatching { listState.scrollToItem(index + HEADER_ITEMS) }
    }
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(WearColors.background).listNavigation(listState, onBack),
        ) {
            item { PlaceBadge(board.place) }
            item {
                if (board.onlyUpcoming) {
                    Text(
                        text = stringResource(R.string.upcoming_title),
                        style = MaterialTheme.typography.caption1,
                        color = WearColors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    DaySelector(board.selection, board.date.format(dateLabelFormat), onSelectDay)
                }
            }
            if (board.departures.isEmpty()) {
                item {
                    Text(
                        text = stringResource(if (board.onlyUpcoming) R.string.upcoming_empty else R.string.board_empty),
                        style = MaterialTheme.typography.body1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            board.departures.forEachIndexed { index, departure ->
                item {
                    TimetableRow(
                        departure = departure,
                        state = board.stateOf(index),
                        remaining = if (board.isToday) context.compactCountdown(board.now, departure.at) else null,
                    )
                }
            }
            item {
                if (board.onlyUpcoming) {
                    PillButton(
                        text = stringResource(R.string.action_open_timetable),
                        iconRes = R.drawable.ic_list,
                        onClick = { onOpenTimetable(board.place) },
                    )
                } else {
                    PillButton(
                        text = stringResource(if (pinned == board.place) R.string.board_unpin else R.string.board_pin),
                        iconRes = R.drawable.ic_place,
                        onClick = { onTogglePinned(board.place) },
                    )
                }
            }
        }
    }
}

/** 1 行の見え方。 */
private enum class RowState {
    /** 現在時刻より前 */
    PAST,

    /** 次の便 */
    NEXT,

    /** それ以降（と、今日以外の表示） */
    UPCOMING,
}

private fun DayBoard.stateOf(index: Int): RowState =
    when {
        !isToday -> RowState.UPCOMING
        nextIndex < 0 -> RowState.PAST
        index < nextIndex -> RowState.PAST
        index == nextIndex -> RowState.NEXT
        else -> RowState.UPCOMING
    }

@Composable
private fun TimetableRow(
    departure: Departure,
    state: RowState,
    remaining: String?,
) {
    val background = if (state == RowState.NEXT) WearColors.primaryGradient else null
    val timeColor =
        when (state) {
            RowState.PAST -> WearColors.onSurfaceDisabled
            RowState.NEXT -> Color.White
            RowState.UPCOMING -> WearColors.onSurface
        }
    val subColor = if (state == RowState.PAST) WearColors.onSurfaceDisabled else WearColors.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .then(if (background != null) Modifier.background(background) else Modifier.background(WearColors.surface))
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = departure.at.hhmm(),
            style = MaterialTheme.typography.title2,
            fontWeight = FontWeight.Bold,
            color = timeColor,
            modifier = Modifier.width(62.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.board_headsign, departure.headsign),
            style = MaterialTheme.typography.body1,
            color = if (state == RowState.NEXT) Color.White else subColor,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (state != RowState.PAST && remaining != null) {
            Text(
                text = remaining,
                style = MaterialTheme.typography.body2,
                color = if (state == RowState.NEXT) Color.White else WearColors.accentLight,
                maxLines = 1,
            )
        }
    }
}

/** 今日 / 平日 / 土日祝 の切り替えバッジ。下に実際に引いた日付を出す。 */
@Composable
private fun DaySelector(
    selected: DaySelection,
    dateLabel: String,
    onSelect: (DaySelection) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            DaySelection.entries.forEach { day ->
                val active = day == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .then(if (active) Modifier.background(WearColors.primaryGradient) else Modifier.background(WearColors.surface))
                            .clickable { onSelect(day) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = stringResource(day.labelRes()),
                        style = MaterialTheme.typography.caption1,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        color = if (active) Color.White else WearColors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(
            text = dateLabel,
            style = MaterialTheme.typography.caption3,
            color = WearColors.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------- メニュー

/** 駅・バス停の一覧。タップでその地点の時刻表を開く。 */
@Composable
private fun MenuScreen(
    places: List<PlaceDistance>,
    pinned: BoardPlace?,
    onPick: (BoardPlace) -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(WearColors.background).listNavigation(listState, onBack),
        ) {
            item {
                Text(
                    text = stringResource(R.string.menu_title),
                    style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            places.forEach { entry ->
                item { MenuRow(entry = entry, pinned = pinned == entry.place, onClick = { onPick(entry.place) }) }
            }
        }
    }
}

@Composable
private fun MenuRow(
    entry: PlaceDistance,
    pinned: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WearColors.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Icon(
            painter = painterResource(entry.place.iconRes()),
            contentDescription = null,
            tint = WearColors.accentLight,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(entry.place.nameRes()),
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = stringResource(entry.place.directionRes()),
                style = MaterialTheme.typography.caption3,
                color = WearColors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        entry.distanceMeters?.let {
            Text(
                text = distanceLabel(it),
                style = MaterialTheme.typography.caption3,
                color = WearColors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (pinned) {
            Spacer(Modifier.width(4.dp))
            Icon(
                painter = painterResource(R.drawable.ic_check_circle),
                contentDescription = null,
                tint = WearColors.accentLight,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- 共通部品

/**
 * 端でさらに送ろうとした分をためる。
 *
 * 一気に端まで送った勢いでそのままホームへ飛ばないよう、**その操作が端から始まったときだけ** 数える。
 * 操作の切れ目は入力が [GESTURE_GAP_MILLIS] 途切れたことで見る（リューズを止める / 指を離す）。
 * つまり「端まで行って一度手を止め、そこからもう一度送る」とホームへ戻る。
 */
private class EdgeTravel {
    private var travel = 0f
    private var lastInputAt = 0L
    private var startedAtEdge = false

    /**
     * @param delta 端で押し込まれた量（送れなかったぶん）。
     * @param atEdge 一覧がもう動かない（端に着いている）か。
     * @return ホームへ戻すなら true。
     */
    fun push(
        delta: Float,
        atEdge: Boolean,
    ): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastInputAt > GESTURE_GAP_MILLIS) {
            // 新しい操作。端から始まったものだけが「その先へ行こうとした」操作
            travel = 0f
            startedAtEdge = atEdge
        }
        lastInputAt = now
        if (!startedAtEdge || !atEdge) return false
        travel += delta
        if (abs(travel) < OVERSCROLL_BACK_PX) return false
        travel = 0f
        startedAtEdge = false
        return true
    }
}

/**
 * 一覧の操作。リューズでスクロールし、端に着いて一度止めてからもう一度送るとホームへ戻る。
 * 指のスワイプも同じ（端まで引き切った勢いでは戻らない）。判定は [EdgeTravel]。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.listNavigation(
    listState: ScalingLazyListState,
    onBack: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val edge = remember { EdgeTravel() }
    val currentBack by rememberUpdatedState(onBack)
    val nested =
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // 慣性スクロールで端に当たっただけでは戻さない。指で引いているときだけ数える
                    if (source != NestedScrollSource.UserInput) return Offset.Zero
                    if (edge.push(available.y, atEdge = consumed.y == 0f && available.y != 0f)) currentBack()
                    return Offset.Zero
                }
            }
        }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    return this
        .nestedScroll(nested)
        .onRotaryScrollEvent { event ->
            scope.launch {
                val delta = event.verticalScrollPixels
                val left = delta - listState.scrollBy(delta)
                if (edge.push(left, atEdge = abs(left) >= ROTARY_EPSILON_PX)) currentBack()
            }
            true
        }.focusRequester(focusRequester)
        .focusable()
}

/** 画面上部の地点バッジ（📍 鳥取駅）。 */
@Composable
private fun PlaceBadge(place: BoardPlace) {
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
private fun PillButton(
    text: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WearColors.surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = WearColors.accentLight,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.caption1, maxLines = 1)
    }
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

// ホームの寸法。提供されたモックの比率に合わせてある
private const val PLACE_ICON_DP = 20f
private const val PLACE_SP = 20f
private const val DIRECTION_SP = 11f
private const val CAPTION_SP = 12f
private const val RING_DP = 74f
private const val RING_STROKE_DP = 6f
private const val RING_ICON_DP = 34f
private const val RING_START_ANGLE = -90f
private const val FULL_TURN = 360f
private const val COUNTDOWN_SP = 44f
private const val UNIT_SP = 22f
private const val HEADSIGN_SP = 15f
private const val DEPART_SP = 26f

/** 発時刻のグローが覆う高さ（画面の割合）。 */
private const val GLOW_HEIGHT_FRACTION = 0.42f

/** グローの中の文字を画面の下端からどれだけ上げるか。丸い文字盤で切れないぶん。 */
private const val GLOW_BOTTOM_DP = 18f

/** 中央の塊をグローに被らないよう少し持ち上げる。 */
private const val CENTER_LIFT_DP = 26f

/** 一覧の先頭に置く見出し（地点バッジ・日種別）の数。「次の便」へ送るときの補正に使う。 */
private const val HEADER_ITEMS = 2

/** 下方向にこれだけ動かして離したらメニューを開く。 */
private const val SWIPE_DOWN_THRESHOLD_PX = 60f

/** 端に着いてから、これだけ先へ送ろうとしたらホームへ戻る。 */
private const val OVERSCROLL_BACK_PX = 96f

/** リューズの送り残りがこれ未満なら「まだ端ではない」とみなす。 */
private const val ROTARY_EPSILON_PX = 1f

/** 入力がこれだけ途切れたら、次は別の操作とみなす（リューズを止める / 指を離す）。 */
private const val GESTURE_GAP_MILLIS = 350L
