package com.kazuya.timtra.core.board

import com.kazuya.timtra.core.journey.CommuteSettings
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CountdownGaugeTest {
    private val departure = LocalDateTime.of(2026, 9, 15, 7, 5)
    private val travel = Duration.ofMinutes(5)
    private val prep = Duration.ofMinutes(5)

    /** 発車 [minutesBefore] 分前のリングの進み具合。 */
    private fun at(minutesBefore: Long): Float = CountdownGauge.progress(departure.minusMinutes(minutesBefore), departure, travel, prep)

    @Test
    fun `far from the departure the ring stays empty`() {
        // 動き出しは 移動 5 + 準備 5 + 移動 5 = 15 分前
        assertEquals(0f, at(60))
        assertEquals(0f, at(16))
        assertEquals(0f, at(15))
    }

    @Test
    fun `it fills over the configured travel time`() {
        assertEquals(0.4f, at(13), TOLERANCE)
        assertEquals(0.6f, at(12), TOLERANCE)
        assertEquals(0.8f, at(11), TOLERANCE)
        assertTrue(at(11) > at(13), "発車が近いほど満ちる")
    }

    @Test
    fun `inside the leave window it is full`() {
        // 出発目安時刻 = 発車 10 分前（移動 5 + 準備 5）。ここから先はずっとフル
        assertEquals(1f, at(10))
        assertEquals(1f, at(3))
        assertEquals(1f, at(0))
    }

    @Test
    fun `past the departure it stays full`() {
        assertEquals(1f, CountdownGauge.progress(departure.plusMinutes(1), departure, travel, prep))
    }

    @Test
    fun `travel time comes from the settings of that place`() {
        val settings = CommuteSettings()
        assertEquals(settings.walkHomeToStop, CountdownGauge.travelTo(BoardPlace.HOME_STOP, settings))
        assertEquals(settings.walkStationToWork, CountdownGauge.travelTo(BoardPlace.HOUGI_JR, settings))
        assertEquals(settings.transferBusToJr, CountdownGauge.travelTo(BoardPlace.TOTTORI_JR, settings))
        assertEquals(settings.transferBusToJr, CountdownGauge.travelTo(BoardPlace.STATION_BUS, settings))
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
