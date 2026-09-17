package com.kazuya.timtra.core.board

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CountdownTest {
    private val target = LocalDateTime.of(2026, 9, 15, 7, 5)

    @Test
    fun `it shows minutes and seconds in four digits`() {
        assertEquals("09:30", Countdown.clock(target.minusSeconds(570), target))
        assertEquals("00:05", Countdown.clock(target.minusSeconds(5), target))
        assertEquals("15:00", Countdown.clock(target.minusMinutes(15), target))
    }

    @Test
    fun `it stops at zero once the departure has passed`() {
        assertEquals("00:00", Countdown.clock(target, target))
        assertEquals("00:00", Countdown.clock(target.plusMinutes(3), target))
    }

    @Test
    fun `far ahead it stays inside four digits`() {
        assertEquals("99:59", Countdown.clock(target.minusHours(5), target))
        assertEquals("99:59", Countdown.clock(target.minusMinutes(100), target))
        assertEquals("99:00", Countdown.clock(target.minusMinutes(99), target))
    }
}
