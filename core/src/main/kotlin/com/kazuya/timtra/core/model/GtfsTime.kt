package com.kazuya.timtra.core.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * GTFS の時刻。サービス日の 0 時起点の秒数で、深夜便は 86400 以上になる（例: 24:15:00 → 87300）。
 * プリパッケージ DB の *_secs 列と同じ表現。
 */
@JvmInline
value class GtfsTime(
    val seconds: Int,
) : Comparable<GtfsTime> {
    init {
        require(seconds >= 0) { "GtfsTime は負にできません: $seconds" }
    }

    val hour: Int get() = seconds / 3600
    val minute: Int get() = seconds % 3600 / 60
    val second: Int get() = seconds % 60

    /** 深夜 0 時を越えているか。 */
    val isPastMidnight: Boolean get() = seconds >= SECONDS_PER_DAY

    /** サービス日を与えて絶対時刻にする。深夜便は翌日の時刻になる。 */
    fun at(serviceDate: LocalDate): LocalDateTime = serviceDate.atStartOfDay().plusSeconds(seconds.toLong())

    /** 表示用。24 時以降は 0 時からに折り返す（24:15 → 00:15）。 */
    fun toLocalTime(): LocalTime = LocalTime.ofSecondOfDay((seconds % SECONDS_PER_DAY).toLong())

    operator fun plus(duration: Duration): GtfsTime = GtfsTime(seconds + duration.seconds.toInt())

    operator fun minus(duration: Duration): GtfsTime = GtfsTime(seconds - duration.seconds.toInt())

    override fun compareTo(other: GtfsTime): Int = seconds.compareTo(other.seconds)

    /** GTFS 表記 (HH:MM:SS)。24 時以降はそのまま "24:15:00" となる。 */
    override fun toString(): String = "%02d:%02d:%02d".format(hour, minute, second)

    companion object {
        const val SECONDS_PER_DAY = 24 * 3600

        /** "7:05:00" / "07:05:00" / "24:15:00" を解釈する。 */
        fun parse(text: String): GtfsTime {
            val parts = text.trim().split(':')
            require(parts.size == 3) { "GTFS の時刻として解釈できません: '$text'" }
            val (h, m, s) = parts.map { it.toIntOrNull() ?: throw IllegalArgumentException("GTFS の時刻として解釈できません: '$text'") }
            require(m in 0..59 && s in 0..59 && h >= 0) { "GTFS の時刻として解釈できません: '$text'" }
            return GtfsTime(h * 3600 + m * 60 + s)
        }

        fun of(
            hour: Int,
            minute: Int,
            second: Int = 0,
        ): GtfsTime = GtfsTime(hour * 3600 + minute * 60 + second)
    }
}
