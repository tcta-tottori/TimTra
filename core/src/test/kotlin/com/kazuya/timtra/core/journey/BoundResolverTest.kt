package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoundResolverTest {
    private val minamiYoshinari = GeoPoint(35.4801735, 134.2185381)
    private val tottoriStation = GeoPoint(35.4949634, 134.2244051)

    @Test
    fun `distance between home stop and Tottori station is about 1_7 km`() {
        val d = minamiYoshinari.distanceMetersTo(tottoriStation)
        assertTrue(d in 1_500.0..2_000.0, "$d")
        assertTrue(minamiYoshinari.distanceMetersTo(Places.HOUGI_STATION) > 15_000.0)
    }

    @Test
    fun `near home stop is outbound`() {
        val home = GeoPoint(35.4790, 134.2200)
        assertEquals(BoundDecision(Bound.OUTBOUND, BoundBasis.NEAR_HOME), BoundResolver.resolve(home, minamiYoshinari, Bound.INBOUND))
    }

    @Test
    fun `near Hougi station is inbound regardless of time`() {
        val work = GeoPoint(Places.HOUGI_STATION.lat + 0.01, Places.HOUGI_STATION.lon)
        assertEquals(BoundDecision(Bound.INBOUND, BoundBasis.NEAR_WORK), BoundResolver.resolve(work, minamiYoshinari, Bound.OUTBOUND))
    }

    @Test
    fun `Tottori station and unknown locations fall back to time of day`() {
        assertEquals(
            BoundDecision(Bound.INBOUND, BoundBasis.TIME_OF_DAY),
            BoundResolver.resolve(tottoriStation, minamiYoshinari, Bound.INBOUND),
        )
        assertEquals(BoundDecision(Bound.OUTBOUND, BoundBasis.TIME_OF_DAY), BoundResolver.resolve(null, minamiYoshinari, Bound.OUTBOUND))
        // 出張先など
        val osaka = GeoPoint(34.7024, 135.4959)
        assertEquals(BoundDecision(Bound.OUTBOUND, BoundBasis.TIME_OF_DAY), BoundResolver.resolve(osaka, minamiYoshinari, Bound.OUTBOUND))
    }

    @Test
    fun `without home stop coordinates only the work side is detected`() {
        assertEquals(BoundDecision(Bound.INBOUND, BoundBasis.TIME_OF_DAY), BoundResolver.resolve(minamiYoshinari, null, Bound.INBOUND))
        assertEquals(BoundDecision(Bound.INBOUND, BoundBasis.NEAR_WORK), BoundResolver.resolve(Places.HOUGI_STATION, null, Bound.OUTBOUND))
    }
}
