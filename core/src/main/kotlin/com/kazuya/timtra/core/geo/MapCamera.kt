package com.kazuya.timtra.core.geo

import com.kazuya.timtra.core.model.GeoPoint

/**
 * 地図の見え方（中心と縮尺）。全画面地図のドラッグ・ピンチで動かす。
 * 画面の大きさとは独立なので、カードと全画面で同じ値から投影を作れる。
 */
data class MapCamera(
    /** 画面中心のメルカトル座標（メートル）。 */
    val centerX: Double,
    val centerY: Double,
    /** 1 ピクセルあたりのメルカトル上のメートル。小さいほど拡大。 */
    val metersPerPixel: Double,
) {
    val center: GeoPoint get() = WebMercator.toGeo(centerX, centerY)

    /** 指でドラッグした分（画面ピクセル）だけ地図をずらす。右にドラッグすれば中心は西へ動く。 */
    fun panned(
        dxPx: Double,
        dyPx: Double,
    ): MapCamera = copy(centerX = centerX - dxPx * metersPerPixel, centerY = centerY + dyPx * metersPerPixel)

    /**
     * [focus]（画面ピクセル）の下にある地点を動かさずに [factor] 倍に拡大する（1 未満で縮小）。
     * ピンチの中心を固定するための計算。縮尺は [MIN_METERS_PER_PIXEL]〜[MAX_METERS_PER_PIXEL] に収める。
     */
    fun zoomed(
        factor: Double,
        focus: MapPoint,
        width: Double,
        height: Double,
    ): MapCamera {
        if (factor <= 0.0 || factor.isNaN()) return this
        val newMpp = (metersPerPixel / factor).coerceIn(MIN_METERS_PER_PIXEL, MAX_METERS_PER_PIXEL)
        if (newMpp == metersPerPixel) return this
        val fx = focus.x - width / 2
        val fy = focus.y - height / 2
        // focus の下の地点 W = center + f * mpp を保つ: center' = W - f * mpp'
        return MapCamera(
            centerX = centerX + fx * (metersPerPixel - newMpp),
            centerY = centerY - fy * (metersPerPixel - newMpp),
            metersPerPixel = newMpp,
        )
    }

    /** 中心を [point] に移す（縮尺はそのまま）。「現在地へ」ボタン用。 */
    fun centeredOn(point: GeoPoint): MapCamera {
        val (x, y) = WebMercator.toMeters(point)
        return copy(centerX = x, centerY = y)
    }

    companion object {
        /** これ以上は拡大しない（ズーム 19 相当。建物が見える程度）。 */
        const val MIN_METERS_PER_PIXEL = 0.25

        /** これ以上は縮小しない（日本全体が入る程度）。 */
        const val MAX_METERS_PER_PIXEL = 20_000.0
    }
}
