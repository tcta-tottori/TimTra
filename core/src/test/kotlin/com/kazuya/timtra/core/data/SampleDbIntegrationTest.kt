package com.kazuya.timtra.core.data

import com.kazuya.timtra.core.Fixtures
import com.kazuya.timtra.core.journey.CommuteSettings
import com.kazuya.timtra.core.journey.JourneyPlanner
import com.kazuya.timtra.core.journey.JourneyStatus
import com.kazuya.timtra.core.journey.PlanRequest
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.BusDirection
import com.kazuya.timtra.core.model.GtfsTime
import com.kazuya.timtra.core.model.StopRole
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 手順 1 のツールが合成フィクスチャから生成した DB を core が正しく解釈できることを確認する。
 * DB は Gradle の generateSampleGtfsDb タスクが build/sample に作り、system property で場所を渡す。
 */
class SampleDbIntegrationTest {
    private val db: File =
        System.getProperty("timtra.sampleDb")?.let(::File)?.takeIf { it.isFile }
            ?: error("timtra.sampleDb が指定されていないか、DB がありません。./gradlew :core:test で実行してください。")

    private val timetable by lazy { JdbcBusTimetableLoader.load(db.absolutePath) }

    @Test
    fun `loads legs, stops and calendar from the generated db`() {
        assertEquals("sample-2026-04", timetable.feedVersion)
        assertEquals(9, timetable.trips.size)
        val toStation = timetable.trips.filter { it.direction == BusDirection.TO_STATION }
        assertEquals(listOf("T_91_WD_0705", "T_91_WD_0735", "T_91_WD_0800", "T_93_SA_0800", "T_93_SH_0830"), toStation.map { it.tripId })
        val first = toStation.first()
        assertEquals("91", first.routeShortName)
        assertEquals("鳥取駅", first.headsign)
        assertEquals(StopRole.HOME, first.boardStop.role)
        assertEquals("南吉成", first.boardStop.name)
        assertEquals(StopRole.STATION, first.alightStop.role)
        assertEquals("1", first.alightStop.platformCode)
        assertEquals(GtfsTime.of(7, 5), first.departure)
        assertEquals(GtfsTime.of(7, 24), first.arrival)

        val midnight = timetable.trips.first { it.tripId == "T_91_WD_2350" }
        assertEquals(GtfsTime.parse("24:15:00"), midnight.arrival)
        assertTrue(midnight.arrival.isPastMidnight)

        // calendar / calendar_dates
        assertEquals(setOf("WD"), timetable.calendar.activeServiceIds(LocalDate.of(2026, 9, 7)))
        assertEquals(setOf("SH"), timetable.calendar.activeServiceIds(LocalDate.of(2026, 5, 4)), "calendar_dates の祝日振替")
        assertEquals(setOf("SH"), timetable.calendar.activeServiceIds(LocalDate.of(2026, 12, 31)))
        assertFalse(timetable.hasServiceOn(LocalDate.of(2027, 4, 1)), "feed 期間外")
    }

    @Test
    fun `plans a weekday morning from the generated db and bundled JR sample`() {
        val planner = JourneyPlanner(timetable, Fixtures.jr, CommuteSettings())
        val j = assertNotNull(planner.plan(PlanRequest(Fixtures.monday.atTime(LocalTime.of(6, 0)), Bound.OUTBOUND)))
        assertEquals("S103D", j.train.service.trainId)
        assertEquals("T_91_WD_0705", j.bus.trip.tripId)
        assertEquals(Duration.ofMinutes(13), j.transferMargin)
        assertEquals(JourneyStatus.OK, j.status)
        assertEquals(Fixtures.monday.atTime(LocalTime.of(6, 55)), j.leaveAt)

        val back = assertNotNull(planner.plan(PlanRequest(Fixtures.monday.atTime(LocalTime.of(17, 0)), Bound.INBOUND)))
        assertEquals("S110D", back.train.service.trainId)
        assertEquals("T_91_WD_1830", back.bus.trip.tripId)
        assertEquals("5", back.bus.trip.boardStop.platformCode)
    }
}
