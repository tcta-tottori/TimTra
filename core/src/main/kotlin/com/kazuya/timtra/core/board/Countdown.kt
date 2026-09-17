package com.kazuya.timtra.core.board

import java.time.Duration
import java.time.LocalDateTime

/** 次の便までの残り時間を、時計と同じ「分:秒」の 4 桁で出す。 */
object Countdown {
    /** 4 桁に収まる上限。これを超える先の便は 99:59 で頭打ちにする。 */
    private const val MAX_MINUTES = 99L
    private const val SECONDS_PER_MINUTE = 60L

    fun clock(
        now: LocalDateTime,
        target: LocalDateTime,
    ): String {
        val seconds = Duration.between(now, target).seconds.coerceAtLeast(0L)
        val minutes = seconds / SECONDS_PER_MINUTE
        return if (minutes > MAX_MINUTES) {
            format(MAX_MINUTES, SECONDS_PER_MINUTE - 1)
        } else {
            format(minutes, seconds % SECONDS_PER_MINUTE)
        }
    }

    private fun format(
        minutes: Long,
        seconds: Long,
    ): String = "%02d:%02d".format(minutes, seconds)
}
