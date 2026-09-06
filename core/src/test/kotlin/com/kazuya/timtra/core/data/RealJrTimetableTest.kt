package com.kazuya.timtra.core.data

import com.kazuya.timtra.core.Fixtures
import com.kazuya.timtra.core.model.DayType
import com.kazuya.timtra.core.model.JrLegIds
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 同梱の実 JR 時刻表（手動転記）が壊れていないことを確認する。 */
class RealJrTimetableTest {
    private val jr = JrTimetableParser.parse(Fixtures.realJrTimetableFile.readText())

    @Test
    fun `is real data, not the sample`() {
        assertFalse(jr.version.startsWith("sample"))
        assertEquals(listOf(JrLegIds.TOTTORI_TO_HOUGI, JrLegIds.HOUGI_TO_TOTTORI), jr.legs.map { it.id })
        jr.legs.forEach { leg -> assertTrue(leg.services.size >= 15, "${leg.id} の便数が少なすぎる") }
    }

    @Test
    fun `weekday and holiday differences transcribed from the screenshots`() {
        val monday = LocalDate.of(2026, 9, 7)
        val sunday = LocalDate.of(2026, 9, 6)
        val saturday = LocalDate.of(2026, 9, 12)

        fun deps(
            date: LocalDate,
            leg: String,
        ) = jr.servicesOn(date, leg).map { it.departure }

        val downWeekday = deps(monday, JrLegIds.TOTTORI_TO_HOUGI)
        val downHoliday = deps(sunday, JrLegIds.TOTTORI_TO_HOUGI)
        assertTrue(LocalTime.of(6, 33) in downWeekday && LocalTime.of(8, 49) in downWeekday)
        assertFalse(LocalTime.of(6, 33) in downHoliday)
        assertTrue(LocalTime.of(5, 40) in downHoliday && LocalTime.of(5, 40) !in downWeekday)
        assertEquals(downHoliday, deps(saturday, JrLegIds.TOTTORI_TO_HOUGI), "土曜は日祝と同じと仮定")
        // 特急は含めない（宝木に停車しない）
        assertFalse(LocalTime.of(7, 3) in downWeekday)
        assertFalse(LocalTime.of(8, 25) in downWeekday)

        val upWeekday = deps(monday, JrLegIds.HOUGI_TO_TOTTORI)
        val upHoliday = deps(sunday, JrLegIds.HOUGI_TO_TOTTORI)
        assertTrue(LocalTime.of(8, 25) in upWeekday && LocalTime.of(8, 25) !in upHoliday)
        assertEquals(upWeekday.size - 1, upHoliday.size)
        assertFalse(LocalTime.of(23, 14) in upWeekday, "◆ 特定日のみ運転は除外")
        assertTrue(upWeekday == upWeekday.sorted())
    }

    @Test
    fun `day types and arrivals are consistent`() {
        assertEquals(DayType.WEEKDAY, jr.dayTypeOf(LocalDate.of(2026, 9, 7)))
        assertEquals(DayType.HOLIDAY, jr.dayTypeOf(LocalDate.of(2026, 12, 31)))
        jr.legs.flatMap { it.services }.forEach { s ->
            assertTrue(s.arrival.isAfter(s.departure), "${s.trainId} の着時刻")
        }
    }
}
