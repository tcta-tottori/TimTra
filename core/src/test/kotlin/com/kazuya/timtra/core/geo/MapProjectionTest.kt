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
            assertTrue(m.x in 23.99..336.01 && m.y in 23.99..196.01, "$p -> $m")
        }
        // 東西に約 14 km あるので横幅で決まる: おおむね 14 km / (360 − 48) px（大円距離には南北成分も入るので 5% 見る）
        val span = minamiYoshinari.distanceMetersTo(Places.HOUGI_STATION)
        assertTrue(abs(proj.groundMetersPerPixel - span / 312.0) < span / 312.0 * 0.05, "${proj.groundMetersPerPixel}")
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
        assertTrue(abs(px * proj.groundMetersPerPixel - meters) < meters * 0.01, "${px * proj.groundMetersPerPixel} vs $meters")
    }

    @Test
    fun `single point uses the minimum span instead of dividing by zero`() {
        val proj = MapProjection.fit(listOf(minamiYoshinari), 200.0, 100.0, 10.0, minSpanMeters = 800.0)
        val m = proj.project(minamiYoshinari)
        assertEquals(100.0, m.x, 1e-6)
        assertEquals(50.0, m.y, 1e-6)
        assertEquals(800.0 / 80.0, proj.groundMetersPerPixel, 1e-3)
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
    fun `tiles cover the whole viewport and align with the projection`() {
        val proj = MapProjection.fit(listOf(minamiYoshinari, busTerminal), 360.0, 220.0, 24.0)
        val zoom = proj.tileZoom(targetTilePixels = 256.0)
        assertTrue(zoom in 13..16, "$zoom")
        val tiles = proj.tiles(zoom)
        assertTrue(tiles.isNotEmpty())
        // 1 枚の大きさはおよそ 256px（ズームは 2 倍刻みなので 181〜362px）
        val size = tiles.first().size
        assertTrue(size in 180.0..363.0, "$size")
        // 左上のタイルは画面の左上を覆い、右下のタイルは右下を覆う
        assertTrue(tiles.minOf { it.left } <= 0.0 && tiles.minOf { it.top } <= 0.0)
        assertTrue(tiles.maxOf { it.left + it.size } >= 360.0 && tiles.maxOf { it.top + it.size } >= 220.0)
        // タイルの原点を投影し直すと、その描画位置に一致する
        val t = tiles.first()
        val (ox, oy) = WebMercator.tileOrigin(t.x, t.y, t.zoom)
        val p = proj.project(WebMercator.toGeo(ox, oy))
        assertEquals(t.left, p.x, 1e-6)
        assertEquals(t.top, p.y, 1e-6)
        // 高密度画面では 1 タイルを大きく描く = 1 枚が広い範囲を覆う = 低いズームを選ぶ
        assertTrue(proj.tileZoom(256.0 * 2.75) <= zoom - 1, "${proj.tileZoom(256.0 * 2.75)} vs $zoom")
    }

    @Test
    fun `web mercator round trips and tile indices follow the slippy map convention`() {
        val p = GeoPoint(35.5158, 134.0778)
        val (x, y) = WebMercator.toMeters(p)
        val back = WebMercator.toGeo(x, y)
        assertEquals(p.lat, back.lat, 1e-9)
        assertEquals(p.lon, back.lon, 1e-9)
        // 赤道・本初子午線は zoom 1 で (1, 1)
        val (ex, ey) = WebMercator.toMeters(GeoPoint(0.0, 0.0))
        assertEquals(1 to 1, WebMercator.tileIndex(ex, ey, 1))
        // 鳥取付近（北緯 35.5°、東経 134°）は zoom 10 で x=893, y=403
        val (tx, ty) = WebMercator.tileIndex(x, y, 10)
        assertEquals(893, tx)
        assertEquals(403, ty)
        // タイルの原点はそのタイルに属し、1 枚分ずらすと隣のタイル
        val (ox, oy) = WebMercator.tileOrigin(tx, ty, 10)
        assertEquals(tx to ty, WebMercator.tileIndex(ox + 1.0, oy - 1.0, 10))
        val tileSize = WebMercator.tileSizeMeters(10)
        assertEquals(tx + 1 to ty + 1, WebMercator.tileIndex(ox + tileSize + 1.0, oy - tileSize - 1.0, 10))
    }

    @Test
    fun `camera pan moves the map with the finger and zoom keeps the focus point fixed`() {
        val fit = MapProjection.fit(listOf(minamiYoshinari, busTerminal), 360.0, 640.0, 24.0)
        val camera = fit.camera
        assertEquals(fit.project(busTerminal), MapProjection.of(camera, 360.0, 640.0).project(busTerminal))

        // 右へ 100px・下へ 50px ドラッグ → 地点も右へ 100px・下へ 50px 動く
        val panned = MapProjection.of(camera.panned(100.0, 50.0), 360.0, 640.0)
        val before = fit.project(busTerminal)
        val after = panned.project(busTerminal)
        assertEquals(before.x + 100.0, after.x, 1e-6)
        assertEquals(before.y + 50.0, after.y, 1e-6)

        // 南吉成を指の中心にして 2 倍に拡大 → 南吉成は動かず、駅までの距離（px）は 2 倍
        val focus = fit.project(minamiYoshinari)
        val zoomed = MapProjection.of(camera.zoomed(2.0, focus, 360.0, 640.0), 360.0, 640.0)
        val home = zoomed.project(minamiYoshinari)
        assertEquals(focus.x, home.x, 1e-6)
        assertEquals(focus.y, home.y, 1e-6)
        val d0 = kotlin.math.hypot(before.x - focus.x, before.y - focus.y)
        val st = zoomed.project(busTerminal)
        val d1 = kotlin.math.hypot(st.x - home.x, st.y - home.y)
        assertEquals(d0 * 2, d1, 1e-6)
        assertEquals(camera.metersPerPixel / 2, zoomed.metersPerPixel, 1e-9)

        // 縮尺の上限・下限で止まる
        val tooFar = camera.zoomed(1e-9, focus, 360.0, 640.0)
        assertEquals(MapCamera.MAX_METERS_PER_PIXEL, tooFar.metersPerPixel, 1e-9)
        val tooClose = camera.zoomed(1e9, focus, 360.0, 640.0)
        assertEquals(MapCamera.MIN_METERS_PER_PIXEL, tooClose.metersPerPixel, 1e-9)
        // 「現在地へ」: 中心が移り、縮尺は変わらない
        val centered = camera.centeredOn(Places.HOUGI_STATION)
        assertEquals(Places.HOUGI_STATION.lat, centered.center.lat, 1e-9)
        assertEquals(camera.metersPerPixel, centered.metersPerPixel)
    }

    @Test
    fun `scale bar picks a round number that fits`() {
        val proj = MapProjection.fit(listOf(minamiYoshinari, Places.HOUGI_STATION), 360.0, 220.0, 24.0)
        val (meters, px) = proj.scaleBar(maxPixels = 130.0)
        assertEquals(5_000, meters)
        assertTrue(px <= 130.0 && px > 40.0, "$px")
    }

    private val workplace = GeoPoint(35.5183, 134.0636)

    @Test
    fun `without a location the map focuses on the side implied by the bound`() {
        val landmarks = RouteLandmarks.build(minamiYoshinari, busTerminal, workplace, home = Places.HOME_DEFAULT)
        assertEquals(5, landmarks.all.size)
        assertEquals(
            listOf(LandmarkKind.HOME, LandmarkKind.HOME_STOP, LandmarkKind.STATION),
            landmarks.focusFor(Bound.OUTBOUND).map { it.kind },
        )
        assertEquals(listOf(LandmarkKind.HOUGI_STATION, LandmarkKind.WORKPLACE), landmarks.focusFor(Bound.INBOUND).map { it.kind })
        val nearest = landmarks.distancesFrom(GeoPoint(35.4790, 134.2200)).first()
        assertEquals(LandmarkKind.HOME, nearest.first.kind)
        assertTrue(nearest.second < 300.0)
        assertEquals(
            LandmarkKind.HOME_STOP,
            landmarks
                .distancesFrom(GeoPoint(35.4803, 134.2186))
                .first()
                .first.kind,
        )
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
        // バスターミナルの位置が無ければ JR 駅舎で代用。自宅も未指定なら出さない
        val bare = RouteLandmarks.build(null, null, null)
        assertEquals(Places.TOTTORI_STATION, bare.find(LandmarkKind.STATION)?.location)
        assertNull(bare.find(LandmarkKind.HOME))
        assertEquals(listOf(LandmarkKind.HOUGI_STATION), landmarks.focusFor(Bound.INBOUND).map { it.kind })
    }
}
