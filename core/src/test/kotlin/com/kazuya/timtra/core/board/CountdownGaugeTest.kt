package com.kazuya.timtra.core.board

import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CountdownGaugeTest {
    private val departure = LocalDateTime.of(2026, 9, 15, 7, 5)

    /** 発車 [minutesBefore] 分前のリングの残量。 */
    private fun at(minutesBefore: Long): Float = CountdownGauge.level(departure.minusMinutes(minutesBefore), departure)

    @Test
    fun `it stays full until the window opens`() {
        assertEquals(1f, at(60))
        assertEquals(1f, at(16))
        assertEquals(1f, at(15), "15 分前がちょうど減りはじめる点")
    }

    @Test
    fun `it drains over the window`() {
        assertEquals(0.8f, at(12), TOLERANCE)
        assertEquals(7f / 15f, at(7), TOLERANCE)
        assertEquals(0.2f, at(3), TOLERANCE)
        assertTrue(at(3) < at(12), "発車が近いほど減る")
    }

    @Test
    fun `it is empty at the departure`() {
        assertEquals(0f, at(0))
        assertEquals(0f, CountdownGauge.level(departure.plusMinutes(1), departure), "過ぎても 0 のまま")
    }

    @Test
    fun `the window can be given explicitly`() {
        assertEquals(0.5f, CountdownGauge.level(departure.minusMinutes(15), departure, Duration.ofMinutes(30)), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
