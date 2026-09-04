package com.kazuya.timtra.core.model

import com.kazuya.timtra.core.Fixtures
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceCalendarTest {
    private val cal = Fixtures.calendar

    @Test
    fun `fixture dates have the expected weekdays`() {
        assertEquals(DayOfWeek.MONDAY, Fixtures.monday.dayOfWeek)
        assertEquals(DayOfWeek.TUESDAY, Fixtures.tuesdayNoService.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, Fixtures.saturday.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, Fixtures.sunday.dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, Fixtures.respectForAgedDay.dayOfWeek)
        assertEquals(DayOfWeek.THURSDAY, Fixtures.newYearsEve.dayOfWeek)
    }

    @Test
    fun `weekday saturday sunday rules`() {
        assertEquals(setOf("WD"), cal.activeServiceIds(Fixtures.monday))
        assertEquals(setOf("SA"), cal.activeServiceIds(Fixtures.saturday))
        assertEquals(setOf("SH"), cal.activeServiceIds(Fixtures.sunday))
    }

    @Test
    fun `calendar_dates override rules`() {
        // 祝日: 平日ダイヤを外して休日ダイヤを追加
        assertEquals(setOf("SH"), cal.activeServiceIds(Fixtures.respectForAgedDay))
        // 年末年始
        assertEquals(setOf("SH"), cal.activeServiceIds(Fixtures.newYearsEve))
        assertEquals(setOf("SH"), cal.activeServiceIds(LocalDate.of(2027, 1, 1)))
        // 全面運休日
        assertTrue(cal.activeServiceIds(Fixtures.tuesdayNoService).isEmpty())
        assertFalse(cal.isActive("WD", Fixtures.tuesdayNoService))
    }

    @Test
    fun `outside date range is inactive`() {
        assertFalse(cal.isActive("WD", LocalDate.of(2026, 3, 31)))
        assertFalse(cal.isActive("WD", LocalDate.of(2027, 4, 1)))
        assertTrue(cal.isActive("WD", LocalDate.of(2027, 3, 31)))
    }

    @Test
    fun `service defined only by calendar_dates`() {
        val onlyDates =
            ServiceCalendar(
                rules = emptyList(),
                exceptions = listOf(CalendarException("SPECIAL", Fixtures.monday, ExceptionType.ADDED)),
            )
        assertEquals(setOf("SPECIAL"), onlyDates.activeServiceIds(Fixtures.monday))
        assertTrue(onlyDates.activeServiceIds(Fixtures.monday.plusDays(1)).isEmpty())
    }
}
