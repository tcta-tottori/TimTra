package com.kazuya.timtra.core.geo

import com.kazuya.timtra.core.model.GeoPoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/** 画面上の位置（ピクセル）。 */
data class MapPoint(
    val x: Double,
    val y: Double,
) {
    /** 描画範囲（0..width, 0..height）に入っているか。 */
    fun isInside(
        width: Double,
        height: Double,
    ): Boolean = x in 0.0..width && y in 0.0..height
}

/**
 * ホーム画面の地図に使う、数十 km 四方向けの簡易投影（正距円筒 + 緯度補正）。
 * 通勤範囲（鳥取市内〜気高町）なら誤差は無視できる。純 Kotlin で、描画は UI 側。
 */
class MapProjection private constructor(
    private val originLat: Double,
    private val originLon: Double,
    /** 1 ピクセルあたりのメートル。 */
    val metersPerPixel: Double,
    val width: Double,
    val height: Double,
) {
    private val cosLat = cos(Math.toRadians(originLat))

    /** 中心（原点）から東西・南北にどれだけ離れているか（メートル）。東と北が正。 */
    private fun offsetMeters(p: GeoPoint): Pair<Double, Double> {
        val dx = Math.toRadians(p.lon - originLon) * cosLat * GeoPoint.EARTH_RADIUS_METERS
        val dy = Math.toRadians(p.lat - originLat) * GeoPoint.EARTH_RADIUS_METERS
        return dx to dy
    }

    /** 緯度経度 → ピクセル。y は下向き。 */
    fun project(p: GeoPoint): MapPoint {
        val (dx, dy) = offsetMeters(p)
        return MapPoint(width / 2 + dx / metersPerPixel, height / 2 - dy / metersPerPixel)
    }

    /**
     * 範囲外の点を、中心からその点へ向かう方向の縁（[inset] だけ内側）に寄せる。
     * 出張先など遠くにいるときの「現在地はこちら」の矢印位置に使う。
     */
    fun clampToEdge(
        point: MapPoint,
        inset: Double,
    ): MapPoint {
        val cx = width / 2
        val cy = height / 2
        val dx = point.x - cx
        val dy = point.y - cy
        val halfW = cx - inset
        val halfH = cy - inset
        if (dx == 0.0 && dy == 0.0) return point
        val scaleX = if (dx == 0.0) Double.MAX_VALUE else halfW / abs(dx)
        val scaleY = if (dy == 0.0) Double.MAX_VALUE else halfH / abs(dy)
        val scale = min(scaleX, scaleY)
        if (scale >= 1.0) return point
        return MapPoint(cx + dx * scale, cy + dy * scale)
    }

    /** 縮尺バーの長さ。[maxPixels] に収まる最大のきりのよい距離（メートル）と、そのピクセル長。 */
    fun scaleBar(maxPixels: Double): Pair<Int, Double> {
        val maxMeters = maxPixels * metersPerPixel
        val meters = SCALE_STEPS.lastOrNull { it <= maxMeters } ?: SCALE_STEPS.first()
        return meters to meters / metersPerPixel
    }

    companion object {
        private val SCALE_STEPS = listOf(50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000)

        /**
         * [points] がすべて収まる投影を作る。
         * @param paddingPx 縁の余白。マーカーやラベルがはみ出さないようにする。
         * @param minSpanMeters 点が 1 つ、または密集しているときの最小表示幅。
         */
        fun fit(
            points: Collection<GeoPoint>,
            width: Double,
            height: Double,
            paddingPx: Double,
            minSpanMeters: Double = 1_000.0,
        ): MapProjection {
            require(points.isNotEmpty()) { "投影する点がありません" }
            require(width > paddingPx * 2 && height > paddingPx * 2) { "描画範囲が余白より小さい" }
            val centerLat = points.sumOf { it.lat } / points.size
            val centerLon = points.sumOf { it.lon } / points.size
            val cosLat = cos(Math.toRadians(centerLat))
            var minX = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            for (p in points) {
                val dx = Math.toRadians(p.lon - centerLon) * cosLat * GeoPoint.EARTH_RADIUS_METERS
                val dy = Math.toRadians(p.lat - centerLat) * GeoPoint.EARTH_RADIUS_METERS
                minX = min(minX, dx)
                maxX = max(maxX, dx)
                minY = min(minY, dy)
                maxY = max(maxY, dy)
            }
            val spanX = max(maxX - minX, minSpanMeters)
            val spanY = max(maxY - minY, minSpanMeters)
            val mpp = max(spanX / (width - paddingPx * 2), spanY / (height - paddingPx * 2))
            // 原点は点群の外接矩形の中心（平均ではなく）にして、左右上下の余白を揃える
            val midX = (minX + maxX) / 2
            val midY = (minY + maxY) / 2
            val originLon = centerLon + Math.toDegrees(midX / (cosLat * GeoPoint.EARTH_RADIUS_METERS))
            val originLat = centerLat + Math.toDegrees(midY / GeoPoint.EARTH_RADIUS_METERS)
            return MapProjection(originLat, originLon, mpp, width, height)
        }
    }
}
