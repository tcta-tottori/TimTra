package com.kazuya.timtra.core.geo

import com.kazuya.timtra.core.model.GeoPoint
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
 * ホーム画面の地図の投影。Web メルカトル（[WebMercator]）なので、OpenStreetMap のタイルをそのまま下に敷ける。
 * 純 Kotlin で、描画は UI 側。
 *
 * [metersPerPixel] はメルカトル上のメートルで、地上の距離は [groundMetersPerPixel]（cos(緯度) 倍）を使う。
 */
class MapProjection private constructor(
    /** 画面中心のメルカトル座標。 */
    private val centerX: Double,
    private val centerY: Double,
    /** 1 ピクセルあたりのメルカトル上のメートル。 */
    val metersPerPixel: Double,
    val width: Double,
    val height: Double,
) {
    /** 画面中心の緯度経度。 */
    val center: GeoPoint = WebMercator.toGeo(centerX, centerY)

    /** 1 ピクセルあたりの地上の距離（メートル）。縮尺バーと距離の見た目に使う。 */
    val groundMetersPerPixel: Double = metersPerPixel * cos(Math.toRadians(center.lat))

    /** 緯度経度 → ピクセル。y は下向き。 */
    fun project(p: GeoPoint): MapPoint {
        val (mx, my) = WebMercator.toMeters(p)
        return MapPoint(width / 2 + (mx - centerX) / metersPerPixel, height / 2 - (my - centerY) / metersPerPixel)
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

    /** 縮尺バーの長さ。[maxPixels] に収まる最大のきりのよい地上距離（メートル）と、そのピクセル長。 */
    fun scaleBar(maxPixels: Double): Pair<Int, Double> {
        val maxMeters = maxPixels * groundMetersPerPixel
        val meters = SCALE_STEPS.lastOrNull { it <= maxMeters } ?: SCALE_STEPS.first()
        return meters to meters / groundMetersPerPixel
    }

    /**
     * タイルのズームを選ぶ。1 タイル（256px）が画面上でおよそ [targetTilePixels] ピクセルになる値。
     * 高密度画面では density 倍を渡すと文字が読める大きさになる。
     */
    fun tileZoom(
        targetTilePixels: Double,
        maxZoom: Int = MAX_TILE_ZOOM,
        minZoom: Int = MIN_TILE_ZOOM,
    ): Int {
        // タイルの画面上の大きさ = tileSizeMeters(z) / metersPerPixel を targetTilePixels に近づける
        val ideal = ln(2 * WebMercator.HALF_WORLD / (metersPerPixel * targetTilePixels)) / ln(2.0)
        return ideal.roundToInt().coerceIn(minZoom, maxZoom)
    }

    /** 画面を覆うタイルと、それぞれの描画位置。 */
    fun tiles(zoom: Int): List<TileSpec> {
        val size = WebMercator.tileSizeMeters(zoom)
        val sizePx = size / metersPerPixel
        val minX = centerX - width / 2 * metersPerPixel
        val maxX = centerX + width / 2 * metersPerPixel
        val minY = centerY - height / 2 * metersPerPixel
        val maxY = centerY + height / 2 * metersPerPixel
        val n = 1 shl zoom
        val (txStart, tyStart) = WebMercator.tileIndex(minX, maxY, zoom)
        val txEnd = floor((maxX + WebMercator.HALF_WORLD) / size).toInt().coerceIn(0, n - 1)
        val tyEnd = ceil((WebMercator.HALF_WORLD - minY) / size).toInt().coerceIn(0, n) - 1
        val result = ArrayList<TileSpec>()
        for (ty in tyStart..max(tyStart, tyEnd)) {
            for (tx in txStart..max(txStart, txEnd)) {
                val (ox, oy) = WebMercator.tileOrigin(tx, ty, zoom)
                val left = width / 2 + (ox - centerX) / metersPerPixel
                val top = height / 2 - (oy - centerY) / metersPerPixel
                result += TileSpec(zoom, tx, ty, left, top, sizePx)
            }
        }
        return result
    }

    companion object {
        private val SCALE_STEPS = listOf(50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000, 20_000, 50_000)
        const val MIN_TILE_ZOOM = 3
        const val MAX_TILE_ZOOM = 18

        /**
         * [points] がすべて収まる投影を作る。
         * @param paddingPx 縁の余白。マーカーやラベルがはみ出さないようにする。
         * @param minSpanMeters 点が 1 つ、または密集しているときの最小表示幅（地上のメートル）。
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
            var minX = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            for (p in points) {
                val (x, y) = WebMercator.toMeters(p)
                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
            }
            val centerX = (minX + maxX) / 2
            val centerY = (minY + maxY) / 2
            // 最小表示幅は地上のメートルで指定されるので、メルカトル上の長さに直す
            val cosLat = cos(Math.toRadians(WebMercator.toGeo(centerX, centerY).lat))
            val minSpan = minSpanMeters / cosLat
            val spanX = max(maxX - minX, minSpan)
            val spanY = max(maxY - minY, minSpan)
            val mpp = max(spanX / (width - paddingPx * 2), spanY / (height - paddingPx * 2))
            return MapProjection(centerX, centerY, mpp, width, height)
        }
    }
}
