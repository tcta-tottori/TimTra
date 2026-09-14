package com.kazuya.timtra.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kazuya.timtra.R
import com.kazuya.timtra.core.journey.JourneyStatus
import com.kazuya.timtra.core.model.Bound
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateWithDow: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日(E)")

fun LocalDateTime.hhmm(): String = toLocalTime().format(hhmm)

fun LocalTime.hhmm(): String = format(hhmm)

fun LocalDateTime.dateLabel(): String = toLocalDate().format(dateWithDow)

/** 残り時間の表示。30 秒刻みで更新される前提なので分単位でよい。 */
@Composable
fun countdownText(
    now: LocalDateTime,
    target: LocalDateTime,
): String {
    val remaining = Duration.between(now, target)
    val minutes = remaining.toMinutes()
    return when {
        remaining.isNegative && minutes <= -1 -> stringResource(R.string.home_countdown_passed)
        minutes < 1 -> stringResource(R.string.home_countdown_now)
        minutes < 60 -> stringResource(R.string.home_countdown_minutes, minutes)
        else -> stringResource(R.string.home_countdown_hours_minutes, minutes / 60, minutes % 60)
    }
}

/** 距離の表示。1 km 未満は 10 m 単位、それ以上は 0.1 km 単位（10 km 以上は整数）。 */
@Composable
fun distanceText(meters: Double): String =
    when {
        meters < 1_000 -> stringResource(R.string.map_distance_meters, (meters / 10).toInt() * 10)
        meters < 10_000 -> stringResource(R.string.map_distance_km, String.format(java.util.Locale.JAPAN, "%.1f", meters / 1_000))
        else -> stringResource(R.string.map_distance_km, (meters / 1_000).toInt().toString())
    }

/** 時刻表の行で使う短い残り時間。 */
@Composable
fun departsInText(
    now: LocalTime,
    targetSeconds: Int,
): String {
    val minutes = (targetSeconds - now.toSecondOfDay()) / 60
    return when {
        minutes < 1 -> stringResource(R.string.timetable_departs_now)
        minutes < 60 -> stringResource(R.string.timetable_departs_in, minutes)
        else -> stringResource(R.string.timetable_departs_in_hours, minutes / 60, minutes % 60)
    }
}

@Composable
fun statusLabel(status: JourneyStatus): String =
    stringResource(
        when (status) {
            JourneyStatus.OK -> R.string.status_ok
            JourneyStatus.TIGHT -> R.string.status_tight
            JourneyStatus.RISK -> R.string.status_risk
            JourneyStatus.MISSED -> R.string.status_missed
        },
    )

@Composable
fun boundLabel(bound: Bound): String =
    stringResource(
        when (bound) {
            Bound.OUTBOUND -> R.string.bound_outbound
            Bound.INBOUND -> R.string.bound_inbound
        },
    )
