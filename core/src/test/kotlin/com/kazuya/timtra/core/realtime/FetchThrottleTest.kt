package com.kazuya.timtra.core.realtime

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FetchThrottleTest {
    private val t0: Instant = Instant.parse("2026-09-07T07:00:00Z")

    @Test
    fun `allows one attempt per 30 seconds`() {
        val throttle = FetchThrottle()
        assertTrue(throttle.tryAcquire(t0))
        assertFalse(throttle.tryAcquire(t0.plusSeconds(1)))
        assertEquals(Duration.ofSeconds(29), throttle.timeUntilAllowed(t0.plusSeconds(1)))
        assertFalse(throttle.tryAcquire(t0.plusSeconds(29)))
        assertTrue(throttle.tryAcquire(t0.plusSeconds(30)), "ちょうど 30 秒で可")
        assertEquals(t0.plusSeconds(30), throttle.lastAttempt())
    }

    @Test
    fun `denied attempts do not reset the window`() {
        val throttle = FetchThrottle()
        assertTrue(throttle.tryAcquire(t0))
        assertFalse(throttle.tryAcquire(t0.plusSeconds(20)))
        assertTrue(throttle.tryAcquire(t0.plusSeconds(30)))
    }

    @Test
    fun `restored last attempt is honoured across restarts`() {
        val throttle = FetchThrottle()
        throttle.restoreLastAttempt(t0)
        assertFalse(throttle.tryAcquire(t0.plusSeconds(10)))
        assertTrue(throttle.tryAcquire(t0.plusSeconds(31)))
    }

    @Test
    fun `clock going backwards does not block forever`() {
        val throttle = FetchThrottle()
        assertTrue(throttle.tryAcquire(t0))
        assertTrue(throttle.tryAcquire(t0.minusSeconds(60)))
    }
}
