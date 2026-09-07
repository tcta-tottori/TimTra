package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.model.Places
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaceAdvisorTest {
    /** 勤務先 → 宝木駅 の直線距離（約 1.13 km）。道なりは約 1.4 km。 */
    private val workToStation = Places.WORKPLACE_DEFAULT.distanceMetersTo(Places.HOUGI_STATION)

    @Test
    fun `route distance from the workplace matches the measured 1_4 km`() {
        val advice = PaceAdvisor.advise(workToStation, Duration.ofMinutes(30))
        assertTrue(advice.routeMeters in 1_300.0..1_500.0, "${advice.routeMeters}")
        // 早歩き 15 分（実測）、普通に歩けば 18 分前後
        assertEquals(15, advice.fastWalkMinutes)
        assertTrue(advice.walkMinutes in 17..19, "${advice.walkMinutes}")
        assertFalse(advice.atPlace)
    }

    @Test
    fun `pace gets more urgent as the departure approaches`() {
        assertEquals(Pace.WALK, PaceAdvisor.advise(workToStation, Duration.ofMinutes(25)).pace)
        assertEquals(Pace.WALK, PaceAdvisor.advise(workToStation, Duration.ofMinutes(19)).pace)
        // 実測: 早歩きで 15 分 + 余裕 1 分
        assertEquals(Pace.FAST_WALK, PaceAdvisor.advise(workToStation, Duration.ofMinutes(16)).pace)
        assertEquals(Pace.RUN, PaceAdvisor.advise(workToStation, Duration.ofMinutes(13)).pace)
        assertEquals(Pace.TOO_LATE, PaceAdvisor.advise(workToStation, Duration.ofMinutes(8)).pace)
        assertEquals(Pace.TOO_LATE, PaceAdvisor.advise(workToStation, Duration.ZERO).pace)
    }

    @Test
    fun `at the station only the boarding buffer matters`() {
        val onPlatform = PaceAdvisor.advise(50.0, Duration.ofMinutes(2))
        assertTrue(onPlatform.atPlace)
        assertEquals(Pace.WALK, onPlatform.pace)
        assertEquals(0, onPlatform.walkMinutes)
        assertEquals(Pace.TOO_LATE, PaceAdvisor.advise(50.0, Duration.ofSeconds(30)).pace)
    }
}
