package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestPolicyTest {
    private val settings = CommuteSettings()
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)
    private val homeStop = GeoPoint(35.4801735, 134.2185381)
    private val station = GeoPoint(35.4949634, 134.2244051)
    private val home = Places.HOME_DEFAULT

    private fun at(
        hour: Int,
        minute: Int,
    ): LocalDateTime = monday.atTime(hour, minute)

    @Test
    fun `at home in the evening and at night the countdown rests, from 05_30 it shows again`() {
        assertTrue(RestPolicy.isResting(at(20, 30), Bound.OUTBOUND, home, homeStop, station, settings))
        assertTrue(RestPolicy.isResting(at(23, 50), Bound.INBOUND, home, homeStop, station, settings))
        assertTrue(RestPolicy.isResting(at(5, 29), Bound.OUTBOUND, home, homeStop, station, settings))
        assertFalse(RestPolicy.isResting(at(5, 30), Bound.OUTBOUND, home, homeStop, station, settings))
        assertFalse(RestPolicy.isResting(at(6, 40), Bound.OUTBOUND, home, homeStop, station, settings))
        // 昼に自宅にいる（休みなど）も朝の時間帯の外なので休止
        assertTrue(RestPolicy.isResting(at(13, 0), Bound.INBOUND, home, homeStop, station, settings))
    }

    @Test
    fun `on the bus home after leaving Tottori station the countdown rests`() {
        // 吉成付近（鳥取駅から約 1.3 km、南吉成から約 0.9 km）
        val onTheBus = GeoPoint(35.4888, 134.2215)
        assertTrue(RestPolicy.isResting(at(21, 0), Bound.INBOUND, onTheBus, homeStop, station, settings))
        // 鳥取駅にいる間は出す
        assertFalse(RestPolicy.isResting(at(21, 0), Bound.INBOUND, station, homeStop, station, settings))
        // 鳥取駅を出た直後（約 330 m）はまだ出す
        assertFalse(RestPolicy.isResting(at(21, 0), Bound.INBOUND, GeoPoint(35.4920, 134.2242), homeStop, station, settings))
    }

    @Test
    fun `away from home or without a location never rests`() {
        assertFalse(RestPolicy.isResting(at(21, 0), Bound.INBOUND, Places.WORKPLACE_DEFAULT, homeStop, station, settings))
        assertFalse(RestPolicy.isResting(at(21, 0), Bound.INBOUND, Places.HOUGI_STATION, homeStop, station, settings))
        assertFalse(RestPolicy.isResting(at(21, 0), Bound.INBOUND, null, homeStop, station, settings))
        assertFalse(RestPolicy.isResting(at(21, 0), Bound.OUTBOUND, home, null, station, settings))
    }
}
