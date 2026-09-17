package com.kazuya.timtra.wear.ui

import android.content.Context
import com.kazuya.timtra.wear.R
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun LocalDateTime.hhmm(): String = format(hhmm)

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
