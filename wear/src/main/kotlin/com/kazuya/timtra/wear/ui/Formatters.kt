package com.kazuya.timtra.wear.ui

import android.content.Context
import com.kazuya.timtra.core.journey.JourneyStatus
import com.kazuya.timtra.wear.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun LocalDateTime.hhmm(): String = format(hhmm)

/** ステータス色（スマホ版と同じ値）。 */
object StatusColors {
    private const val OK = 0xFF2E7D32
    private const val TIGHT = 0xFFEF6C00
    private const val RISK = 0xFFC62828
    private const val MISSED = 0xFF616161

    fun argb(status: JourneyStatus): Int =
        when (status) {
            JourneyStatus.OK -> OK
            JourneyStatus.TIGHT -> TIGHT
            JourneyStatus.RISK -> RISK
            JourneyStatus.MISSED -> MISSED
        }.toInt()
}

/** 残り時間の静的表示（タイルのフォールバックと UI 用）。 */
fun Context.countdownLabel(
    now: LocalDateTime,
    target: LocalDateTime,
): String {
    val remaining = Duration.between(now, target)
    val minutes = remaining.toMinutes()
    return when {
        remaining.isNegative && minutes <= -1 -> getString(R.string.countdown_passed)
        minutes < 1 -> getString(R.string.countdown_now)
        minutes < MINUTES_PER_HOUR -> getString(R.string.countdown_minutes, minutes)
        else -> getString(R.string.countdown_hours_minutes, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }
}

/**
 * 「次の発車まで」の大きな数字だけ。1 時間未満は分、それ以上は時間（端数は [countdownUnit] に出す）。
 * デザインの「12」+「分」のように、数字と単位を別の大きさで並べるために分けている。
 */
fun Context.countdownValue(
    now: LocalDateTime,
    target: LocalDateTime,
): String {
    val minutes = Duration.between(now, target).toMinutes()
    return when {
        minutes < 0 -> "-"
        minutes < MINUTES_PER_HOUR -> minutes.toString()
        else -> (minutes / MINUTES_PER_HOUR).toString()
    }
}

/** [countdownValue] に添える単位。1 時間以上は「時間 M 分」。 */
fun Context.countdownUnit(
    now: LocalDateTime,
    target: LocalDateTime,
): String {
    val minutes = Duration.between(now, target).toMinutes()
    return when {
        minutes < 0 -> getString(R.string.countdown_passed)
        minutes < MINUTES_PER_HOUR -> getString(R.string.unit_minutes)
        else -> getString(R.string.unit_hours_minutes, minutes % MINUTES_PER_HOUR)
    }
}

/** 一覧の右端に出す残り時間（「12分」「1時間8分」）。 */
fun Context.compactCountdown(
    now: LocalDateTime,
    target: LocalDateTime,
): String {
    val minutes = Duration.between(now, target).toMinutes()
    return when {
        minutes < 0 -> getString(R.string.countdown_passed)
        minutes < MINUTES_PER_HOUR -> getString(R.string.compact_minutes, minutes)
        else -> getString(R.string.compact_hours_minutes, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }
}

private const val MINUTES_PER_HOUR = 60L
