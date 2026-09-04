package com.kazuya.timtra.core.calendar

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JapaneseHolidaysTest {
    private fun name(s: String) = JapaneseHolidays.holidayName(LocalDate.parse(s))

    @Test
    fun `fixed and happy monday holidays in 2026`() {
        assertEquals("元日", name("2026-01-01"))
        assertEquals("成人の日", name("2026-01-12"))
        assertEquals("建国記念の日", name("2026-02-11"))
        assertEquals("天皇誕生日", name("2026-02-23"))
        assertEquals("春分の日", name("2026-03-20"))
        assertEquals("昭和の日", name("2026-04-29"))
        assertEquals("憲法記念日", name("2026-05-03"))
        assertEquals("みどりの日", name("2026-05-04"))
        assertEquals("こどもの日", name("2026-05-05"))
        assertEquals("海の日", name("2026-07-20"))
        assertEquals("山の日", name("2026-08-11"))
        assertEquals("敬老の日", name("2026-09-21"))
        assertEquals("秋分の日", name("2026-09-23"))
        assertEquals("スポーツの日", name("2026-10-12"))
        assertEquals("文化の日", name("2026-11-03"))
        assertEquals("勤労感謝の日", name("2026-11-23"))
    }

    @Test
    fun `substitute holiday and citizens holiday`() {
        // 2026-05-03 が日曜 → 5/4, 5/5 は祝日なので 5/6 が振替休日
        assertEquals("振替休日", name("2026-05-06"))
        // 2026 年のシルバーウィーク: 敬老の日(9/21) と秋分の日(9/23) に挟まれた 9/22
        assertEquals("国民の休日", name("2026-09-22"))
        // 2026-08-11 は火曜なので振替なし
        assertNull(name("2026-08-12"))
    }

    @Test
    fun `ordinary days and year end are not statutory holidays`() {
        assertNull(name("2026-09-07"))
        assertNull(name("2026-12-29"))
        assertNull(name("2026-12-31"))
        assertFalse(JapaneseHolidays.isHoliday(LocalDate.parse("2027-01-02")))
        assertTrue(JapaneseHolidays.isHoliday(LocalDate.parse("2027-01-01")))
    }

    @Test
    fun `equinox approximation for nearby years`() {
        assertEquals(20, JapaneseHolidays.vernalEquinoxDay(2026))
        assertEquals(21, JapaneseHolidays.vernalEquinoxDay(2027))
        assertEquals(23, JapaneseHolidays.autumnalEquinoxDay(2026))
        assertEquals(23, JapaneseHolidays.autumnalEquinoxDay(2027))
        assertEquals(22, JapaneseHolidays.autumnalEquinoxDay(2028))
    }
}
