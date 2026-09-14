package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.Fixtures
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.BusDirection
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class InboundPhaseTest {
    @Test
    fun `train is the hero until the user leaves the Hougi area`() {
        assertEquals(InboundPhase.TO_TRAIN, InboundPhaseResolver.resolve(Bound.INBOUND, null))
        assertEquals(InboundPhase.TO_TRAIN, InboundPhaseResolver.resolve(Bound.INBOUND, Places.WORKPLACE_DEFAULT))
        assertEquals(InboundPhase.TO_TRAIN, InboundPhaseResolver.resolve(Bound.INBOUND, Places.HOUGI_STATION))
        // 浜村駅付近（宝木から東へ約 2.5 km）: もう電車に乗っている
        assertEquals(InboundPhase.TO_BUS, InboundPhaseResolver.resolve(Bound.INBOUND, GeoPoint(35.5233, 134.1080)))
        assertEquals(InboundPhase.TO_BUS, InboundPhaseResolver.resolve(Bound.INBOUND, Places.TOTTORI_STATION))
        // 往路では関係ない
        assertEquals(InboundPhase.TO_TRAIN, InboundPhaseResolver.resolve(Bound.OUTBOUND, Places.TOTTORI_STATION))
    }

    @Test
    fun `next buses from the station are listed by departure and roll over to the next day`() {
        val planner = JourneyPlanner(Fixtures.busTimetable(), Fixtures.jr, CommuteSettings())
        val evening = Fixtures.monday.atTime(LocalTime.of(18, 0))
        val buses = planner.nextBuses(evening, BusDirection.FROM_STATION, 3)
        assertEquals(listOf("R1830", "R2015", "R2350"), buses.map { it.trip.tripId })
        assertEquals(Fixtures.monday.atTime(LocalTime.of(18, 30)), buses.first().departureAt)
        // 深夜便（月曜ダイヤの 24:30 = 火曜 0:30）のあとは次の運行日の便。フィクスチャでは火曜が全面運休なので水曜
        val late = Fixtures.monday.plusDays(1).atTime(LocalTime.of(1, 0))
        val next = planner.nextBuses(late, BusDirection.FROM_STATION, 1).single()
        assertEquals(Fixtures.monday.plusDays(2), next.serviceDate)
        assertEquals("R1722", next.trip.tripId)
        // 火曜 0:00 なら月曜ダイヤの深夜便 24:30 が先
        val midnight = Fixtures.monday.plusDays(1).atTime(LocalTime.of(0, 0))
        assertEquals(
            "R2430",
            planner
                .nextBuses(midnight, BusDirection.FROM_STATION, 1)
                .single()
                .trip.tripId,
        )
    }
}
