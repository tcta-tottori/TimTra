package com.kazuya.timtra.core

import com.kazuya.timtra.core.data.BusTimetable
import com.kazuya.timtra.core.data.JrTimetableParser
import com.kazuya.timtra.core.model.BusDirection
import com.kazuya.timtra.core.model.BusStop
import com.kazuya.timtra.core.model.BusTrip
import com.kazuya.timtra.core.model.CalendarException
import com.kazuya.timtra.core.model.CalendarRule
import com.kazuya.timtra.core.model.ExceptionType
import com.kazuya.timtra.core.model.GtfsTime
import com.kazuya.timtra.core.model.JrTimetable
import com.kazuya.timtra.core.model.ServiceCalendar
import com.kazuya.timtra.core.model.StopRole
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/** テスト用のインメモリ時刻表。合成データであり実ダイヤとは無関係。 */
object Fixtures {
    val minamiYoshinari = BusStop("S_MINAMIYOSHINARI", "南吉成", role = StopRole.HOME)
    val tottoriEki1 = BusStop("S_TOTTORI_EKI_1", "鳥取駅", platformCode = "1", role = StopRole.STATION)
    val tottoriEki5 = BusStop("S_TOTTORI_EKI_5", "鳥取駅", platformCode = "5", role = StopRole.STATION)

    val weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
    val start: LocalDate = LocalDate.of(2026, 4, 1)
    val end: LocalDate = LocalDate.of(2027, 3, 31)

    val rules =
        listOf(
            CalendarRule("WD", weekdays, start, end),
            CalendarRule("SA", setOf(DayOfWeek.SATURDAY), start, end),
            CalendarRule("SH", setOf(DayOfWeek.SUNDAY), start, end),
        )

    /** 祝日（敬老の日・国民の休日・秋分）と年末年始は休日ダイヤ、2026-09-08 は全面運休。 */
    val exceptions =
        listOf("2026-09-21", "2026-09-22", "2026-09-23", "2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02", "2027-01-03")
            .map(LocalDate::parse)
            .flatMap { d ->
                listOf(CalendarException("WD", d, ExceptionType.REMOVED), CalendarException("SH", d, ExceptionType.ADDED))
            } + listOf(CalendarException("WD", LocalDate.parse("2026-09-08"), ExceptionType.REMOVED))

    fun toStation(
        id: String,
        service: String,
        dep: String,
        arr: String,
        alight: BusStop = tottoriEki1,
    ) = BusTrip(
        id,
        "R_91",
        "91",
        "鳥取駅",
        service,
        BusDirection.TO_STATION,
        minamiYoshinari,
        alight,
        GtfsTime.parse(dep),
        GtfsTime.parse(arr),
    )

    fun fromStation(
        id: String,
        service: String,
        dep: String,
        arr: String,
        board: BusStop = tottoriEki5,
    ) = BusTrip(
        id,
        "R_91",
        "91",
        "智頭",
        service,
        BusDirection.FROM_STATION,
        board,
        minamiYoshinari,
        GtfsTime.parse(dep),
        GtfsTime.parse(arr),
    )

    val trips =
        listOf(
            toStation("B0705", "WD", "07:05:00", "07:24:00"),
            toStation("B0720", "WD", "07:20:00", "07:40:00"),
            toStation("B0735", "WD", "07:35:00", "07:56:00"),
            toStation("B0800", "WD", "08:00:00", "08:20:00", alight = tottoriEki5),
            toStation("B_SA_0800", "SA", "08:00:00", "08:19:00"),
            toStation("B_SH_0830", "SH", "08:30:00", "08:49:00"),
            fromStation("R1722", "WD", "17:22:00", "17:39:00"),
            fromStation("R1745", "WD", "17:45:00", "18:02:00"),
            fromStation("R1830", "WD", "18:30:00", "18:47:00"),
            fromStation("R2015", "WD", "20:15:00", "20:32:00"),
            fromStation("R2350", "WD", "23:50:00", "24:15:00"),
            fromStation("R2430", "WD", "24:30:00", "24:55:00"),
            fromStation("R_SA_1800", "SA", "18:00:00", "18:17:00"),
            fromStation("R_SH_1800", "SH", "18:00:00", "18:17:00"),
        )

    val calendar = ServiceCalendar(rules, exceptions)

    fun busTimetable(trips: List<BusTrip> = this.trips): BusTimetable = BusTimetable(trips, calendar)

    /** data/src/main/assets/jr_timetable.json（サンプル）。Gradle からは system property で場所を渡す。 */
    val jrTimetableFile: File by lazy {
        val fromProperty = System.getProperty("timtra.jrTimetableJson")?.let(::File)
        val candidates =
            listOfNotNull(fromProperty, File("../data/src/main/assets/jr_timetable.json"), File("data/src/main/assets/jr_timetable.json"))
        candidates.firstOrNull { it.isFile } ?: error("jr_timetable.json が見つかりません: $candidates")
    }

    val jr: JrTimetable by lazy { JrTimetableParser.parse(jrTimetableFile.readText()) }

    // よく使う日付（曜日はテストで検証している）
    val monday: LocalDate = LocalDate.of(2026, 9, 7)
    val tuesdayNoService: LocalDate = LocalDate.of(2026, 9, 8)
    val saturday: LocalDate = LocalDate.of(2026, 9, 12)
    val sunday: LocalDate = LocalDate.of(2026, 9, 13)
    val respectForAgedDay: LocalDate = LocalDate.of(2026, 9, 21)
    val newYearsEve: LocalDate = LocalDate.of(2026, 12, 31)
}
