package com.kazuya.timtra.core.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters

/** 祝日判定の差し替え口。テストや将来の祝日法改正に備える。 */
fun interface HolidayCalendar {
    fun isHoliday(date: LocalDate): Boolean
}

/**
 * 日本の国民の祝日（祝日法）。2022 年以降の規定を前提にしている
 * （2020・2021 年の五輪特例は扱わない）。春分・秋分は近似式で求める（1980〜2099 年で有効）。
 *
 * 年末年始（12/29〜1/3）は祝日ではないので、JR の休日ダイヤ適用は
 * jr_timetable.json の overrides で指定する。バスは calendar_dates.txt に従う。
 */
object JapaneseHolidays : HolidayCalendar {
    override fun isHoliday(date: LocalDate): Boolean = holidayName(date) != null

    /** 祝日名。祝日でなければ null。振替休日・国民の休日も含む。 */
    fun holidayName(date: LocalDate): String? {
        statutoryName(date)?.let { return it }

        // 振替休日: 祝日が日曜と重なったとき、その日後の最初の「祝日でない日」が休日になる。
        var cursor = date.minusDays(1)
        while (statutoryName(cursor) != null) {
            if (cursor.dayOfWeek == DayOfWeek.SUNDAY) return "振替休日"
            cursor = cursor.minusDays(1)
        }

        // 国民の休日: 前日と翌日が祝日に挟まれた平日（日曜は除く）。
        if (date.dayOfWeek != DayOfWeek.SUNDAY &&
            statutoryName(date.minusDays(1)) != null &&
            statutoryName(date.plusDays(1)) != null
        ) {
            return "国民の休日"
        }
        return null
    }

    /** 祝日法に列挙された祝日（振替・国民の休日を含まない）。 */
    private fun statutoryName(date: LocalDate): String? {
        val d = date.dayOfMonth
        return when (date.month) {
            Month.JANUARY ->
                when {
                    d == 1 -> "元日"
                    date == nthMonday(date, 2) -> "成人の日"
                    else -> null
                }
            Month.FEBRUARY ->
                when (d) {
                    11 -> "建国記念の日"
                    23 -> "天皇誕生日"
                    else -> null
                }
            Month.MARCH -> if (d == vernalEquinoxDay(date.year)) "春分の日" else null
            Month.APRIL -> if (d == 29) "昭和の日" else null
            Month.MAY ->
                when (d) {
                    3 -> "憲法記念日"
                    4 -> "みどりの日"
                    5 -> "こどもの日"
                    else -> null
                }
            Month.JULY -> if (date == nthMonday(date, 3)) "海の日" else null
            Month.AUGUST -> if (d == 11) "山の日" else null
            Month.SEPTEMBER ->
                when {
                    date == nthMonday(date, 3) -> "敬老の日"
                    d == autumnalEquinoxDay(date.year) -> "秋分の日"
                    else -> null
                }
            Month.OCTOBER -> if (date == nthMonday(date, 2)) "スポーツの日" else null
            Month.NOVEMBER ->
                when (d) {
                    3 -> "文化の日"
                    23 -> "勤労感謝の日"
                    else -> null
                }
            else -> null
        }
    }

    private fun nthMonday(
        date: LocalDate,
        n: Int,
    ): LocalDate = date.withDayOfMonth(1).with(TemporalAdjusters.dayOfWeekInMonth(n, DayOfWeek.MONDAY))

    /** 春分日の近似（1980〜2099 年）。 */
    internal fun vernalEquinoxDay(year: Int): Int = equinox(year, 20.8431)

    /** 秋分日の近似（1980〜2099 年）。 */
    internal fun autumnalEquinoxDay(year: Int): Int = equinox(year, 23.2488)

    private fun equinox(
        year: Int,
        base: Double,
    ): Int {
        val y = year - 1980
        return (base + 0.242194 * y - y / 4).toInt()
    }
}
