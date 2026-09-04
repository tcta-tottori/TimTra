package com.kazuya.timtra.core.data

import com.kazuya.timtra.core.Fixtures
import com.kazuya.timtra.core.model.DayType
import com.kazuya.timtra.core.model.JrCalendar
import com.kazuya.timtra.core.model.JrLegIds
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JrTimetableParserTest {
    @Test
    fun `parses bundled sample`() {
        val jr = Fixtures.jr
        assertEquals("sample-2026-09", jr.version)
        assertEquals(listOf(JrLegIds.TOTTORI_TO_HOUGI, JrLegIds.HOUGI_TO_TOTTORI), jr.legs.map { it.id })
        val leg = jr.leg(JrLegIds.TOTTORI_TO_HOUGI)
        assertEquals("鳥取", leg.from)
        assertEquals("宝木", leg.to)
        val s103 = leg.services.first { it.trainId == "S103D" }
        assertEquals(JrCalendar.WEEKDAY, s103.calendar)
        assertEquals(LocalTime.of(7, 45), s103.departure)
        assertEquals(LocalTime.of(8, 7), s103.arrival)
        assertEquals("1", s103.platform)
        assertEquals(DayType.HOLIDAY, jr.overrides[LocalDate.of(2026, 12, 31)])
    }

    @Test
    fun `day type resolution`() {
        val jr = Fixtures.jr
        assertEquals(DayType.WEEKDAY, jr.dayTypeOf(Fixtures.monday))
        assertEquals(DayType.SATURDAY, jr.dayTypeOf(Fixtures.saturday))
        assertEquals(DayType.HOLIDAY, jr.dayTypeOf(Fixtures.sunday))
        assertEquals(DayType.HOLIDAY, jr.dayTypeOf(Fixtures.respectForAgedDay), "祝日")
        assertEquals(DayType.HOLIDAY, jr.dayTypeOf(Fixtures.newYearsEve), "overrides")
        assertEquals(DayType.WEEKDAY, jr.dayTypeOf(LocalDate.of(2026, 12, 29)), "overrides に無い年末の平日")
        // 祝日判定を差し替えられる
        assertEquals(DayType.WEEKDAY, jr.dayTypeOf(Fixtures.respectForAgedDay, holidays = { false }))
    }

    @Test
    fun `servicesOn filters by calendar and sorts`() {
        val jr = Fixtures.jr
        val weekday = jr.servicesOn(Fixtures.monday, JrLegIds.TOTTORI_TO_HOUGI)
        assertTrue(weekday.all { it.calendar == JrCalendar.WEEKDAY })
        assertEquals(weekday.sortedBy { it.departure }, weekday)
        val holiday = jr.servicesOn(Fixtures.sunday, JrLegIds.HOUGI_TO_TOTTORI)
        assertTrue(holiday.any { it.calendar == JrCalendar.EVERYDAY }, "everyday は全日種別に含まれる")
        assertTrue(holiday.none { it.calendar == JrCalendar.WEEKDAY })
    }

    @Test
    fun `next day arrival is detected`() {
        val s900 =
            Fixtures.jr
                .leg(JrLegIds.HOUGI_TO_TOTTORI)
                .services
                .first { it.trainId == "S900D" }
        assertTrue(s900.arrivesNextDay)
    }

    @Test
    fun `parses minimal schema with empty services and unknown keys`() {
        val jr =
            JrTimetableParser.parse(
                """
                {"version":"x","legs":[{"id":"a","from":"A","to":"B","line":"L","services":[]}],"extra":1}
                """.trimIndent(),
            )
        assertEquals("x", jr.version)
        assertTrue(jr.leg("a").services.isEmpty())
        assertTrue(jr.overrides.isEmpty())
    }

    @Test
    fun `rejects everyday in overrides and unknown calendar`() {
        assertFailsWith<IllegalArgumentException> {
            JrTimetableParser.parse("""{"version":"x","legs":[],"overrides":[{"date":"2026-01-01","calendar":"everyday"}]}""")
        }
        assertFailsWith<IllegalArgumentException> {
            JrTimetableParser.parse(
                """{"version":"x","legs":[{"id":"a","from":"A","to":"B","line":"L","services":[{"trainId":"1","calendar":"sunday","departure":"07:00","arrival":"07:20"}]}]}""",
            )
        }
    }
}
