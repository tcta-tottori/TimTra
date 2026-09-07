package com.kazuya.timtra.core.geo

import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapProjectionTest {
    private val minamiYoshinari = GeoPoint(35.4801735, 134.2185381)
    private val busTerminal = GeoPoint(35.4949634, 134.2244051)

    @Test
    fun `all fitted points land inside the padded area`() {
        val points = listOf(minamiYoshinari, busTerminal, Places.TOTTORI_STATION, Places.HOUGI_STATION)
        val proj = MapProjection.fit(points, width = 360.0, height = 220.0, paddingPx = 24.0)
        for (p in points) {
            val m = proj.project(p)
            assertTrue(m.x in 24.0..336.0 && m.y in 24.0..196.0, "$p -> $m")
        }
        // 東西に約 14 km あるので横幅で決まる: 14 km / (360 − 48) px
        val span = minamiYoshinari.distanceMetersTo(Places.HOUGI_STATION)
        assertTrue(abs(proj.metersPerPixel - span / 312.0) < 5.0, "${proj.metersPerPixel}")
    }

    @Test
    fun `east is right and north is up`() {
        val proj = MapProjection.fit(listOf(minamiYoshinari, busTerminal), 300.0, 300.0, 20.0)
        val home = proj.project(minamiYoshinari)
        val station = proj.project(busTerminal)
        assertTrue(station.x > home.x, "駅は東")
        assertTrue(station.y < home.y, "駅は北（画面では上）")
    }

    @Test
    fun `pixel distance matches great circle distance`() {
        val proj = MapProjection.fit(listOf(minamiYoshinari, busTerminal), 400.0, 400.0, 10.0)
        val a = proj.project(minamiYoshinari)
        val b = proj.project(busTerminal)
        val px = kotlin.math.hypot(a.x - b.x, a.y - b.y)
        val meters = minamiYoshinari.distanceMetersTo(busTerminal)
        assertTrue(abs(px * proj.metersPerPixel - meters) < meters * 0.01, "${px * proj.metersPerPixel} vs $meters")
    }

    @Test
    fun `single point uses the minimum span instead of dividing by zero`() {
        val proj = MapProjection.fit(listOf(minamiYoshinari), 200.0, 100.0, 10.0, minSpanMeters = 800.0)
        val m = proj.project(minamiYoshinari)
        assertEquals(100.0, m.x, 1e-6)
        assertEquals(50.0, m.y, 1e-6)
        assertEquals(800.0 / 80.0, proj.metersPerPixel, 1e-6)
    }

    @Test
    fun `far away points are clamped to the edge in the right direction`() {
        val proj = MapProjection.fit(listOf(minamiYoshinari, busTerminal), 300.0, 200.0, 20.0)
        val osaka = proj.project(GeoPoint(34.7024, 135.4959))
        assertFalse(osaka.isInside(300.0, 200.0))
        val edge = proj.clampToEdge(osaka, inset = 12.0)
        assertTrue(edge.isInside(300.0, 200.0), "$edge")
        assertTrue(edge.x > 150.0 && edge.y > 100.0, "大阪は南東: $edge")
        assertTrue(abs(edge.x - 288.0) < 1e-6 || abs(edge.y - 188.0) < 1e-6, "縁に接する: $edge")
        // 範囲内の点はそのまま
        val inside = proj.project(busTerminal)
        assertEquals(inside, proj.clampToEdge(inside, 12.0))
    }

    @Test
    fun `scale bar picks a round number that fits`() {
        val proj = MapProjection.fit(listOf(minamiYoshinari, Places.HOUGI_STATION), 360.0, 220.0, 24.0)
        val (meters, px) = proj.scaleBar(maxPixels = 120.0)
        assertEquals(5_000, meters)
        assertTrue(px <= 120.0 && px > 40.0, "$px")
    }

    private val workplace = GeoPoint(35.5183, 134.0636)

    @Test
    fun `without a location the map focuses on the side implied by the bound`() {
        val landmarks = RouteLandmarks.build(minamiYoshinari, busTerminal, workplace)
        assertEquals(4, landmarks.all.size)
        assertEquals(listOf(LandmarkKind.HOME_STOP, LandmarkKind.STATION), landmarks.focusFor(Bound.OUTBOUND).map { it.kind })
        assertEquals(listOf(LandmarkKind.HOUGI_STATION, LandmarkKind.WORKPLACE), landmarks.focusFor(Bound.INBOUND).map { it.kind })
        val nearest = landmarks.distancesFrom(GeoPoint(35.4790, 134.2200)).first()
        assertEquals(LandmarkKind.HOME_STOP, nearest.first.kind)
        assertTrue(nearest.second < 300.0)
    }

    @Test
    fun `with a location the map focuses on the side the user is actually on`() {
        val landmarks = RouteLandmarks.build(minamiYoshinari, busTerminal, workplace)
        // 宝木駅のすぐそば（朝で向きが往路でも勤務先側を出す）
        val atHougi = GeoPoint(35.5170, 134.0750)
        assertEquals(RouteSide.WORK, landmarks.sideFor(Bound.OUTBOUND, atHougi))
        assertEquals(
            listOf(LandmarkKind.HOUGI_STATION, LandmarkKind.WORKPLACE),
            landmarks.focusFor(Bound.OUTBOUND, atHougi).map { it.kind },
        )
        // 南吉成の近く（夕方で向きが復路でも自宅側）
        assertEquals(RouteSide.HOME, landmarks.sideFor(Bound.INBOUND, GeoPoint(35.4790, 134.2200)))
        // 鳥取駅も自宅側
        assertEquals(RouteSide.HOME, landmarks.sideFor(Bound.INBOUND, Places.TOTTORI_STATION))
        // 途中（浜村付近、どちらからも 4 km 超）は経路全体
        assertNull(landmarks.sideFor(Bound.INBOUND, GeoPoint(35.5230, 134.1300)))
        assertEquals(4, landmarks.focusFor(Bound.INBOUND, GeoPoint(35.5230, 134.1300)).size)
        // 通勤圏外（大阪）は向きで決める
        assertEquals(RouteSide.HOME, landmarks.sideFor(Bound.OUTBOUND, GeoPoint(34.7024, 135.4959)))
        assertEquals(RouteSide.WORK, landmarks.sideFor(Bound.INBOUND, GeoPoint(34.7024, 135.4959)))
    }

    @Test
    fun `workplace marker is omitted when not registered`() {
        val landmarks = RouteLandmarks.build(minamiYoshinari, busTerminal, workplace = null)
        assertNull(landmarks.find(LandmarkKind.WORKPLACE))
        assertNotNull(landmarks.find(LandmarkKind.HOUGI_STATION))
        // バスターミナルの位置が無ければ JR 駅舎で代用
        assertEquals(Places.TOTTORI_STATION, RouteLandmarks.build(null, null, null).find(LandmarkKind.STATION)?.location)
        assertEquals(listOf(LandmarkKind.HOUGI_STATION), landmarks.focusFor(Bound.INBOUND).map { it.kind })
    }
}
