package com.kazuya.timtra.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kazuya.timtra.R
import com.kazuya.timtra.core.geo.Landmark
import com.kazuya.timtra.core.geo.LandmarkKind
import com.kazuya.timtra.core.geo.MapPoint
import com.kazuya.timtra.core.geo.MapProjection
import com.kazuya.timtra.core.geo.RouteLandmarks
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.realtime.VehiclePosition
import com.kazuya.timtra.ui.common.CircleIcon
import com.kazuya.timtra.ui.common.color
import com.kazuya.timtra.ui.common.distanceText
import com.kazuya.timtra.ui.common.hhmm
import com.kazuya.timtra.ui.common.iconRes
import com.kazuya.timtra.ui.common.labelRes
import com.kazuya.timtra.ui.theme.TimTraCard
import com.kazuya.timtra.ui.theme.TimTraColors
import com.kazuya.timtra.ui.theme.TransitColors
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** 地図に出すバスの車両位置（GTFS-RT）。 */
data class MapVehicles(
    val vehicles: List<VehiclePosition> = emptyList(),
    /** 乗る予定の便の trip_id。この車両だけ大きく描き、ラベルを付ける。 */
    val targetTripId: String? = null,
    /** 乗る予定の便の推定遅延（分）。0 以下なら出さない。 */
    val targetDelayMinutes: Long = 0,
    /** 車両位置を取得した時刻。 */
    val fetchedAt: Instant? = null,
) {
    val target: VehiclePosition? get() = targetTripId?.let { id -> vehicles.firstOrNull { it.tripId == id } }
}

/**
 * ホーム中央の地図。サーバーやタイル画像は使わず（CLAUDE.md 3-5）、経路上の地点と現在地を
 * 端末内の座標だけで描く簡易地図。現在地から各地点までの距離を線と数字で示す。
 *
 * 初期表示は現在地に近い側（南吉成〜鳥取駅、または宝木駅〜勤務先。現在地が無ければ向きで決める）を拡大し、
 * 右上のボタンで経路全体（約 14 km）に切り替えられる。
 * GTFS-RT の車両位置が取れていれば、対象区間を走るバスも描く（乗る予定の便は大きく、遅延も添える）。
 */
@Composable
fun RouteMapCard(
    landmarks: RouteLandmarks,
    here: GeoPoint?,
    bound: Bound,
    locationPermitted: Boolean,
    modifier: Modifier = Modifier,
    buses: MapVehicles = MapVehicles(),
) {
    var full by rememberSaveable { mutableStateOf(false) }
    TimTraCard(modifier = modifier.fillMaxWidth(), containerColor = Color.White) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIcon(iconRes = R.drawable.ic_map, color = TimTraColors.primary, size = 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.map_title), style = MaterialTheme.typography.labelLarge, color = TimTraColors.primary)
                    Text(
                        text =
                            when {
                                here != null -> nearestSummary(landmarks, here)
                                locationPermitted -> stringResource(R.string.map_locating)
                                else -> stringResource(R.string.map_here)
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = { full = !full }) {
                    Icon(
                        painter = painterResource(if (full) R.drawable.ic_my_location else R.drawable.ic_zoom_out_map),
                        contentDescription = stringResource(if (full) R.string.map_toggle_focus else R.string.map_toggle_full),
                        tint = TimTraColors.primary,
                    )
                }
            }
            RouteMap(
                landmarks = landmarks,
                here = here,
                bound = bound,
                full = full,
                buses = buses,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .height(MAP_HEIGHT)
                        .clip(RoundedCornerShape(16.dp))
                        .background(TransitColors.mapGround)
                        .border(1.dp, TimTraColors.outline, RoundedCornerShape(16.dp)),
            )
            if (here != null) {
                DistanceChips(landmarks, here)
            } else {
                Text(
                    text = stringResource(R.string.map_no_location),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
    }
}

/** 見出しの 2 行目: 最寄りの地点までの距離。 */
@Composable
private fun nearestSummary(
    landmarks: RouteLandmarks,
    here: GeoPoint,
): String {
    val nearest = landmarks.distancesFrom(here).firstOrNull() ?: return stringResource(R.string.map_here)
    return stringResource(R.string.map_distance_to, stringResource(nearest.first.kind.labelRes), distanceText(nearest.second))
}

/** 現在地から各地点までの距離。近い順に横並び。 */
@Composable
private fun DistanceChips(
    landmarks: RouteLandmarks,
    here: GeoPoint,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        landmarks.distancesFrom(here).forEach { (landmark, meters) ->
            Row(
                modifier =
                    Modifier
                        .background(landmark.kind.color.copy(alpha = 0.10f), RoundedCornerShape(50))
                        .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIcon(iconRes = landmark.kind.iconRes, color = landmark.kind.color, size = 20.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(landmark.kind.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = distanceText(meters),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = landmark.kind.color,
                )
            }
        }
    }
}

@Composable
private fun RouteMap(
    landmarks: RouteLandmarks,
    here: GeoPoint?,
    bound: Bound,
    full: Boolean,
    buses: MapVehicles,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val paddingPx = with(density) { MAP_PADDING.toPx() }
        val edgeInsetPx = with(density) { HERE_DOT_SIZE.toPx() }

        // 現在地に近い側を拡大する。宝木にいれば宝木側、南吉成にいれば自宅側（向きは現在地が無いときの予備）
        val focus = if (full) landmarks.all else landmarks.focusFor(bound, here)
        val targetBus = buses.target?.let { GeoPoint(it.latitude, it.longitude) }
        val fitPoints =
            remember(focus, here, full, targetBus) {
                buildList {
                    addAll(focus.map { it.location })
                    // 現在地が近ければ画面に収める。遠い（出張中など）ときは縁に矢印代わりの点を出すだけにする。
                    if (here != null && focus.any { it.location.distanceMetersTo(here) <= includeHereWithin(full) }) add(here)
                    // 乗る予定のバスが近づいていれば、それも収める（「バスはいまどこか」が地図の主目的のひとつ）
                    if (targetBus != null && focus.any { it.location.distanceMetersTo(targetBus) <= INCLUDE_BUS_METERS }) add(targetBus)
                }
            }
        val projection =
            remember(fitPoints, widthPx, heightPx) {
                MapProjection.fit(fitPoints, widthPx.toDouble(), heightPx.toDouble(), paddingPx.toDouble(), MIN_SPAN_METERS)
            }
        val points = landmarks.all.map { it to projection.project(it.location) }
        val hereRaw = here?.let { projection.project(it) }
        val hereInside = hereRaw?.isInside(widthPx.toDouble(), heightPx.toDouble()) == true
        val hereDrawn = hereRaw?.let { if (hereInside) it else projection.clampToEdge(it, edgeInsetPx.toDouble()) }
        val nearest = here?.let { h -> landmarks.distancesFrom(h).firstOrNull() }
        val nearestPoint = nearest?.let { (lm, _) -> points.first { it.first == lm }.second }
        val (scaleMeters, scaleBarPx) = remember(projection) { projection.scaleBar(widthPx / 3.0) }
        val busPoints = buses.vehicles.map { it to projection.project(GeoPoint(it.latitude, it.longitude)) }
        val targetBusRaw = targetBus?.let { projection.project(it) }
        val targetBusInside = targetBusRaw?.isInside(widthPx.toDouble(), heightPx.toDouble()) == true

        val pulse by
            rememberInfiniteTransition(label = "here").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = LinearEasing), RepeatMode.Restart),
                label = "pulse",
            )

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 方眼。地図らしさと縮尺感のため
            val grid = GRID_STEP.toPx()
            var x = grid
            while (x < size.width) {
                drawLine(TransitColors.mapGrid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += grid
            }
            var y = grid
            while (y < size.height) {
                drawLine(TransitColors.mapGrid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += grid
            }

            // 経路。地点の並び（南吉成 → 鳥取駅 → 宝木駅 → 勤務先）を結ぶ
            val routeWidth = ROUTE_WIDTH.toPx()
            for (i in 0 until points.size - 1) {
                val (a, pa) = points[i]
                val (b, pb) = points[i + 1]
                val start = Offset(pa.x.toFloat(), pa.y.toFloat())
                val end = Offset(pb.x.toFloat(), pb.y.toFloat())
                val segment = segmentStyle(a.kind, b.kind)
                drawLine(segment.color.copy(alpha = 0.18f), start, end, strokeWidth = routeWidth * 2.2f, cap = StrokeCap.Round)
                drawLine(
                    color = segment.color,
                    start = start,
                    end = end,
                    strokeWidth = routeWidth,
                    cap = StrokeCap.Round,
                    pathEffect = if (segment.dashed) PathEffect.dashPathEffect(floatArrayOf(routeWidth * 2, routeWidth * 2), 0f) else null,
                )
            }

            // 現在地 → 最寄り地点 の距離線
            if (hereDrawn != null && hereInside && nearestPoint != null) {
                drawLine(
                    color = TransitColors.here,
                    start = Offset(hereDrawn.x.toFloat(), hereDrawn.y.toFloat()),
                    end = Offset(nearestPoint.x.toFloat(), nearestPoint.y.toFloat()),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()), 0f),
                )
            }

            // 現在地の波紋
            if (hereDrawn != null) {
                val center = Offset(hereDrawn.x.toFloat(), hereDrawn.y.toFloat())
                val maxRadius = PULSE_RADIUS.toPx()
                drawCircle(TransitColors.here, radius = maxRadius * pulse, center = center, alpha = (1f - pulse) * 0.35f)
                drawCircle(TransitColors.here, radius = HERE_DOT_SIZE.toPx() / 2 + 3.dp.toPx(), center = center, alpha = 0.18f)
            }

            // 縮尺バー（左下）
            val barPx = scaleBarPx
            val barY = size.height - SCALE_MARGIN.toPx()
            val barX = SCALE_MARGIN.toPx()
            val barColor = TimTraColors.onSurfaceVariant
            drawLine(barColor, Offset(barX, barY), Offset(barX + barPx.toFloat(), barY), strokeWidth = 2.dp.toPx())
            drawLine(barColor, Offset(barX, barY - 4.dp.toPx()), Offset(barX, barY + 1.dp.toPx()), strokeWidth = 2.dp.toPx())
            drawLine(
                barColor,
                Offset(barX + barPx.toFloat(), barY - 4.dp.toPx()),
                Offset(barX + barPx.toFloat(), barY + 1.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
            )
        }

        // 縮尺の数字
        Text(
            text = scaleLabel(scaleMeters),
            style = MaterialTheme.typography.labelSmall,
            color = TimTraColors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = SCALE_MARGIN, bottom = SCALE_MARGIN + 4.dp),
        )

        // 地点のマーカー（画面内のものだけ。少しはみ出す程度なら描く）
        val marginPx = with(density) { MARKER_SIZE.toPx() }
        points.forEach { (landmark, p) ->
            if (p.x in -marginPx..(widthPx + marginPx) && p.y in -marginPx..(heightPx + marginPx)) {
                LandmarkMarker(landmark, p)
            }
        }

        // バスの車両位置（GTFS-RT）。乗る予定の便は大きく、他の便は小さく
        val busMarginPx = with(density) { BUS_MARKER_SIZE.toPx() }
        busPoints.forEach { (vehicle, p) ->
            val isTarget = vehicle.tripId == buses.targetTripId
            if (p.x in -busMarginPx..(widthPx + busMarginPx) && p.y in -busMarginPx..(heightPx + busMarginPx)) {
                BusMarker(point = p, isTarget = isTarget, delayMinutes = if (isTarget) buses.targetDelayMinutes else 0)
            }
        }
        if (targetBus != null && targetBusRaw != null && !targetBusInside) {
            val edge = projection.clampToEdge(targetBusRaw, edgeInsetPx.toDouble())
            BusMarker(point = edge, isTarget = true, delayMinutes = 0, label = false)
            // 距離は現在地から。現在地が無ければ拡大している側の最初の地点（南吉成 / 宝木駅）から
            val from = here ?: focus.first().location
            FloatingLabel(
                text = stringResource(R.string.map_bus_off_screen, distanceText(from.distanceMetersTo(targetBus))),
                point = MapPoint(widthPx / 2.0, heightPx - paddingPx / 2.0),
                color = TransitColors.bus,
                bold = false,
                width = 240.dp,
            )
        }
        buses.fetchedAt?.let { fetched ->
            Text(
                text = stringResource(R.string.map_bus_fetched, LocalDateTime.ofInstant(fetched, ZoneId.systemDefault()).hhmm()),
                style = MaterialTheme.typography.labelSmall,
                color = TransitColors.bus,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        // 距離のラベル（現在地と最寄り地点の中間）
        if (hereDrawn != null && hereInside && nearestPoint != null && nearest != null) {
            val mid = MapPoint((hereDrawn.x + nearestPoint.x) / 2, (hereDrawn.y + nearestPoint.y) / 2)
            FloatingLabel(
                text = distanceText(nearest.second),
                point = mid,
                color = TransitColors.here,
                bold = true,
            )
        }

        // 現在地
        if (hereDrawn != null) {
            HereMarker(hereDrawn, inside = hereInside)
            if (!hereInside && nearest != null) {
                FloatingLabel(
                    text = stringResource(R.string.map_here_off_screen, distanceText(nearest.second)),
                    point = MapPoint(widthPx / 2.0, paddingPx / 2.0),
                    color = TransitColors.here,
                    bold = false,
                    width = 240.dp,
                )
            }
        }
    }
}

private data class SegmentStyle(
    val color: Color,
    val dashed: Boolean,
)

/** 地点の種類の組み合わせから、その区間の交通手段（色）を決める。 */
private fun segmentStyle(
    from: LandmarkKind,
    to: LandmarkKind,
): SegmentStyle =
    when {
        from == LandmarkKind.HOME_STOP || to == LandmarkKind.HOME_STOP -> SegmentStyle(TransitColors.bus, dashed = false)
        from == LandmarkKind.WORKPLACE || to == LandmarkKind.WORKPLACE -> SegmentStyle(TransitColors.walk, dashed = true)
        else -> SegmentStyle(TransitColors.jr, dashed = false)
    }

@Composable
private fun LandmarkMarker(
    landmark: Landmark,
    point: MapPoint,
) {
    val density = LocalDensity.current
    val halfWidth = with(density) { (MARKER_LABEL_WIDTH / 2).roundToPx() }
    val halfMarker = with(density) { (MARKER_SIZE / 2).roundToPx() }
    Column(
        modifier =
            Modifier
                .offset { IntOffset(point.x.roundToInt() - halfWidth, point.y.roundToInt() - halfMarker) }
                .width(MARKER_LABEL_WIDTH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(MARKER_SIZE).background(Color.White, CircleShape).padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircleIcon(iconRes = landmark.kind.iconRes, color = landmark.kind.color, size = MARKER_SIZE - 4.dp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(landmark.kind.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier =
                Modifier
                    .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun HereMarker(
    point: MapPoint,
    inside: Boolean,
) {
    val density = LocalDensity.current
    val half = with(density) { (HERE_DOT_SIZE / 2).roundToPx() }
    Box(
        modifier =
            Modifier
                .offset { IntOffset(point.x.roundToInt() - half, point.y.roundToInt() - half) }
                .size(HERE_DOT_SIZE)
                .background(Color.White, CircleShape)
                .padding(2.5.dp)
                .background(TransitColors.here, CircleShape),
    )
    if (inside) {
        val labelHalf = with(density) { (HERE_LABEL_WIDTH / 2).roundToPx() }
        val above = with(density) { (HERE_DOT_SIZE / 2 + 18.dp).roundToPx() }
        Text(
            text = stringResource(R.string.map_here),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier =
                Modifier
                    .offset { IntOffset(point.x.roundToInt() - labelHalf, point.y.roundToInt() - above) }
                    .width(HERE_LABEL_WIDTH)
                    .background(TransitColors.here, RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}

/** バスの車両位置。乗る予定の便は大きな橙の丸に「乗るバス」と遅延、他の便は小さな丸だけ。 */
@Composable
private fun BusMarker(
    point: MapPoint,
    isTarget: Boolean,
    delayMinutes: Long,
    label: Boolean = isTarget,
) {
    val density = LocalDensity.current
    val size = if (isTarget) BUS_MARKER_SIZE else BUS_MARKER_SIZE_OTHER
    val half = with(density) { (size / 2).roundToPx() }
    Box(
        modifier =
            Modifier
                .offset { IntOffset(point.x.roundToInt() - half, point.y.roundToInt() - half) }
                .size(size)
                .background(Color.White, CircleShape)
                .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircleIcon(
            iconRes = R.drawable.ic_bus,
            color = if (isTarget) TransitColors.bus else TransitColors.bus.copy(alpha = 0.7f),
            size = size - 4.dp,
            contentDescription = stringResource(if (isTarget) R.string.map_bus_target else R.string.map_bus_other),
        )
    }
    if (label) {
        val labelHalf = with(density) { (BUS_LABEL_WIDTH / 2).roundToPx() }
        val below = with(density) { (size / 2 + 3.dp).roundToPx() }
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(point.x.roundToInt() - labelHalf, point.y.roundToInt() + below) }
                    .width(BUS_LABEL_WIDTH),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                    if (delayMinutes > 0) {
                        stringResource(R.string.map_bus_target) + " " + stringResource(R.string.map_bus_delay, delayMinutes)
                    } else {
                        stringResource(R.string.map_bus_target)
                    },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier =
                    Modifier
                        .background(TransitColors.bus, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

/** 地図の上に浮かせる小さなラベル（距離など）。[point] を中心に置く。 */
@Composable
private fun FloatingLabel(
    text: String,
    point: MapPoint,
    color: Color,
    bold: Boolean,
    width: Dp = FLOATING_LABEL_WIDTH,
) {
    val density = LocalDensity.current
    val halfWidth = with(density) { (width / 2).roundToPx() }
    val halfHeight = with(density) { 9.dp.roundToPx() }
    Box(
        modifier = Modifier.offset { IntOffset(point.x.roundToInt() - halfWidth, point.y.roundToInt() - halfHeight) }.width(width),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = color,
            maxLines = 1,
            modifier =
                Modifier
                    .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(50))
                    .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** 縮尺バーの数字。1 km 以上は km で。 */
private fun scaleLabel(meters: Int): String = if (meters >= 1_000) "${meters / 1_000} km" else "$meters m"

/** 拡大表示では近くにいるときだけ現在地を画面に収める。経路全体表示ではもう少し広く取る。 */
private fun includeHereWithin(full: Boolean): Double = if (full) INCLUDE_HERE_FULL_METERS else INCLUDE_HERE_FOCUS_METERS

private val MAP_HEIGHT = 210.dp
private val MAP_PADDING = 40.dp
private val GRID_STEP = 28.dp
private val ROUTE_WIDTH = 5.dp
private val MARKER_SIZE = 30.dp
private val MARKER_LABEL_WIDTH = 96.dp
private val HERE_DOT_SIZE = 16.dp
private val BUS_MARKER_SIZE = 30.dp
private val BUS_MARKER_SIZE_OTHER = 20.dp
private val BUS_LABEL_WIDTH = 150.dp
private val HERE_LABEL_WIDTH = 56.dp
private val FLOATING_LABEL_WIDTH = 88.dp
private val PULSE_RADIUS = 22.dp
private val SCALE_MARGIN = 10.dp
private const val PULSE_MILLIS = 1_800
private const val MIN_SPAN_METERS = 1_200.0
private const val INCLUDE_HERE_FOCUS_METERS = 6_000.0
private const val INCLUDE_HERE_FULL_METERS = 40_000.0
private const val INCLUDE_BUS_METERS = 12_000.0
