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

private const val MINUTES_PER_HOUR = 60L
