package com.kazuya.timtra.core.model

import com.kazuya.timtra.core.calendar.HolidayCalendar
import com.kazuya.timtra.core.calendar.JapaneseHolidays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 日種別。JR の時刻表はこの 3 種で切り替わる。 */
enum class DayType {
    WEEKDAY,
    SATURDAY,

    /** 日曜・祝日 */
    HOLIDAY,
}

/** jr_timetable.json の calendar 値。 */
enum class JrCalendar(
    val jsonValue: String,
) {
    WEEKDAY("weekday"),
    SATURDAY("saturday"),
    HOLIDAY("holiday"),
    EVERYDAY("everyday"),
    ;

    fun runsOn(dayType: DayType): Boolean =
        when (this) {
            EVERYDAY -> true
            WEEKDAY -> dayType == DayType.WEEKDAY
            SATURDAY -> dayType == DayType.SATURDAY
            HOLIDAY -> dayType == DayType.HOLIDAY
        }

    /** overrides で日種別として使う場合の変換。everyday は日種別にならない。 */
    fun toDayType(): DayType =
        when (this) {
            WEEKDAY -> DayType.WEEKDAY
            SATURDAY -> DayType.SATURDAY
            HOLIDAY -> DayType.HOLIDAY
            EVERYDAY -> throw IllegalArgumentException("overrides の calendar に everyday は指定できません")
        }

    companion object {
        fun fromJsonValue(value: String): JrCalendar =
            entries.firstOrNull { it.jsonValue == value }
                ?: throw IllegalArgumentException("未知の calendar: $value")
    }
}

/** JR の 1 本。 */
data class JrService(
    val trainId: String,
    val calendar: JrCalendar,
    val departure: LocalTime,
    val arrival: LocalTime,
    val platform: String = "",
    val note: String = "",
) {
    /** 到着が出発より早い時刻なら日付をまたいでいる（例: 23:50 発 00:12 着）。 */
    val arrivesNextDay: Boolean get() = arrival.isBefore(departure)
}

/** 駅間 1 区間の時刻表。 */
data class JrLeg(
    val id: String,
    val from: String,
    val to: String,
    val line: String,
    val services: List<JrService>,
)

/** jr_timetable.json 全体。 */
data class JrTimetable(
    val version: String,
    val note: String,
    val legs: List<JrLeg>,
    /** 特定日の日種別の上書き（年末年始など）。 */
    val overrides: Map<LocalDate, DayType>,
) {
    fun leg(id: String): JrLeg = legs.firstOrNull { it.id == id } ?: throw IllegalArgumentException("JR 区間がありません: $id")

    /**
     * その日の日種別。overrides > 日曜・祝日 > 土曜 > 平日 の順で決める。
     * 祝日判定は [holidays] に委ねる（既定は [JapaneseHolidays]）。
     */
    fun dayTypeOf(
        date: LocalDate,
        holidays: HolidayCalendar = JapaneseHolidays,
    ): DayType =
        overrides[date] ?: when {
            date.dayOfWeek == DayOfWeek.SUNDAY || holidays.isHoliday(date) -> DayType.HOLIDAY
            date.dayOfWeek == DayOfWeek.SATURDAY -> DayType.SATURDAY
            else -> DayType.WEEKDAY
        }

    /** その日にその区間で運行する便を発時刻順で返す。 */
    fun servicesOn(
        date: LocalDate,
        legId: String,
        holidays: HolidayCalendar = JapaneseHolidays,
    ): List<JrService> {
        val dayType = dayTypeOf(date, holidays)
        return leg(legId).services.filter { it.calendar.runsOn(dayType) }.sortedBy { it.departure }
    }
}
