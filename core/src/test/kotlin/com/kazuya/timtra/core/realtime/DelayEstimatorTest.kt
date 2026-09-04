package com.kazuya.timtra.core.realtime

import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.core.model.GtfsTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DelayEstimatorTest {
    private val zone = TimTraConstants.ZONE
    private val date: LocalDate = LocalDate.of(2026, 9, 7)
    private val estimator = DelayEstimator()

    // 南北に一直線に並ぶ 4 停留所。緯度 0.009 度 ≒ 1 km。
    private val stops =
        listOf(
            TripStopTime("S1", 1, GtfsTime.parse("07:00:00"), GtfsTime.parse("07:00:00"), 35.400, 134.200),
            TripStopTime("S2", 2, GtfsTime.parse("07:05:00"), GtfsTime.parse("07:05:00"), 35.409, 134.200),
            TripStopTime("S3", 3, GtfsTime.parse("07:08:00"), GtfsTime.parse("07:08:00"), 35.418, 134.200),
            TripStopTime("S4", 4, GtfsTime.parse("07:24:00"), GtfsTime.parse("07:24:00"), 35.427, 134.200),
        )

    private fun at(time: String): Instant = LocalDateTime.of(date, java.time.LocalTime.parse(time)).atZone(zone).toInstant()

    private fun vehicle(
        lat: Double,
        lon: Double = 134.200,
        observed: Instant?,
        seq: Int? = null,
        status: VehicleStopStatus? = null,
    ) = VehiclePosition("bus", "T", "R", lat, lon, null, seq, status, observed)

    @Test
    fun `on time at a stop gives zero delay`() {
        val e = assertNotNull(estimator.estimate("T", date, stops, vehicle(35.409, observed = at("07:05:00")), at("07:05:10"), zone))
        assertEquals(Duration.ZERO, e.delay)
        assertEquals(EstimateMethod.NEAREST_SEGMENT, e.method)
        assertEquals("T", e.tripId)
    }

    @Test
    fun `vehicle still two stops behind schedule is late by the schedule gap`() {
        // 予定では 07:10 に S3 を出て S4 へ向かっているはずだが、車両はまだ S2（07:05 発予定）→ 5 分遅れ
        val e = assertNotNull(estimator.estimate("T", date, stops, vehicle(35.409, observed = at("07:10:00")), at("07:10:00"), zone))
        assertEquals(Duration.ofMinutes(5), e.delay)
        assertEquals(2, e.stopSequence)
        assertEquals(Duration.ofMinutes(5), e.delayForPlanning)
    }

    @Test
    fun `position between stops is interpolated`() {
        // S3(07:08) と S4(07:24) の中間 → 予定 07:16。観測 07:20 なら 4 分遅れ
        val e = assertNotNull(estimator.estimate("T", date, stops, vehicle(35.4225, observed = at("07:20:00")), at("07:20:00"), zone))
        assertEquals(Duration.ofMinutes(4), e.delay)
        assertEquals(3, e.stopSequence)
        assertNotNull(e.distanceMeters)
    }

    @Test
    fun `running early gives negative delay but zero for planning`() {
        val e = assertNotNull(estimator.estimate("T", date, stops, vehicle(35.418, observed = at("07:06:00")), at("07:06:00"), zone))
        assertEquals(Duration.ofMinutes(-2), e.delay)
        assertEquals(Duration.ZERO, e.delayForPlanning)
    }

    @Test
    fun `stop sequence with STOPPED_AT uses the stop's departure time`() {
        val e =
            assertNotNull(
                estimator.estimate(
                    "T",
                    date,
                    stops,
                    vehicle(35.409, observed = at("07:08:00"), seq = 2, status = VehicleStopStatus.STOPPED_AT),
                    at("07:08:00"),
                    zone,
                ),
            )
        assertEquals(Duration.ofMinutes(3), e.delay)
        assertEquals(EstimateMethod.STOP_SEQUENCE, e.method)
        assertNull(e.distanceMeters)
    }

    @Test
    fun `stop sequence in transit projects onto the previous segment`() {
        // S2→S3 の中間、予定 07:06:30。観測 07:09:30 → 3 分遅れ
        val e =
            assertNotNull(
                estimator.estimate(
                    "T",
                    date,
                    stops,
                    vehicle(35.4135, observed = at("07:09:30"), seq = 3, status = VehicleStopStatus.IN_TRANSIT_TO),
                    at("07:09:30"),
                    zone,
                ),
            )
        assertEquals(Duration.ofMinutes(3), e.delay)
        assertEquals(EstimateMethod.STOP_SEQUENCE, e.method)
        assertEquals(2, e.stopSequence)
    }

    @Test
    fun `stale observation and far away vehicles are ignored`() {
        assertNull(estimator.estimate("T", date, stops, vehicle(35.409, observed = at("07:00:00")), at("07:04:00"), zone), "3 分より古い")
        assertNull(
            estimator.estimate("T", date, stops, vehicle(35.409, lon = 134.250, observed = at("07:05:00")), at("07:05:00"), zone),
            "4.5 km 離れている",
        )
    }

    @Test
    fun `uses fetch time when the vehicle has no timestamp`() {
        val e = assertNotNull(estimator.estimate("T", date, stops, vehicle(35.409, observed = null), at("07:07:00"), zone))
        assertEquals(Duration.ofMinutes(2), e.delay)
        assertEquals(at("07:07:00"), e.observedAt)
    }

    @Test
    fun `service date after midnight is handled through GtfsTime`() {
        val late =
            listOf(
                TripStopTime("A", 1, GtfsTime.parse("23:50:00"), GtfsTime.parse("23:50:00"), 35.400, 134.200),
                TripStopTime("B", 2, GtfsTime.parse("24:10:00"), GtfsTime.parse("24:10:00"), 35.409, 134.200),
            )
        val observed = LocalDateTime.of(date.plusDays(1), java.time.LocalTime.of(0, 12)).atZone(zone).toInstant()
        val e = assertNotNull(estimator.estimate("T", date, late, vehicle(35.409, observed = observed), observed, zone))
        assertEquals(Duration.ofMinutes(2), e.delay)
    }
}
