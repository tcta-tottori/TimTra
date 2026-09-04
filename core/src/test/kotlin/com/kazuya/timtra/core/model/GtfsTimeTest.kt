package com.kazuya.timtra.core.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GtfsTimeTest {
    @Test
    fun `parses zero padded and unpadded`() {
        assertEquals(7 * 3600 + 5 * 60, GtfsTime.parse("07:05:00").seconds)
        assertEquals(7 * 3600 + 5 * 60, GtfsTime.parse("7:05:00").seconds)
    }

    @Test
    fun `keeps times past midnight`() {
        val t = GtfsTime.parse("24:15:00")
        assertEquals(24 * 3600 + 15 * 60, t.seconds)
        assertTrue(t.isPastMidnight)
        assertEquals("24:15:00", t.toString())
        assertEquals(LocalTime.of(0, 15), t.toLocalTime())
        assertEquals(LocalDateTime.of(2026, 9, 8, 0, 15), t.at(LocalDate.of(2026, 9, 7)))
        assertFalse(GtfsTime.parse("23:59:59").isPastMidnight)
    }

    @Test
    fun `rejects malformed input`() {
        listOf("7:05", "07:60:00", "abc", "", "-1:00:00").forEach {
            assertFailsWith<IllegalArgumentException>(it) { GtfsTime.parse(it) }
        }
    }

    @Test
    fun `arithmetic and ordering`() {
        val t = GtfsTime.of(23, 50)
        assertEquals(GtfsTime.parse("24:05:00"), t + Duration.ofMinutes(15))
        assertEquals(GtfsTime.parse("23:40:00"), t - Duration.ofMinutes(10))
        assertTrue(GtfsTime.parse("24:00:00") > GtfsTime.parse("23:59:59"))
    }
}
