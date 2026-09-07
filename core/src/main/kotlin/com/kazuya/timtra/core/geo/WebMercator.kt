package com.kazuya.timtra.core.geo

import com.kazuya.timtra.core.model.GeoPoint
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * Web メルカトル（EPSG:3857）。OpenStreetMap などのタイル地図と同じ投影。
 * 座標の単位は「メルカトル上のメートル」で、地上の距離とは cos(緯度) 倍だけ違う（鳥取付近で約 0.81）。
 */
object WebMercator {
    /** 地球半径（球体近似。タイル地図の規約値）。 */
    const val EARTH_RADIUS = 6_378_137.0

    /** 世界の端（±）。 */
    const val HALF_WORLD = PI * EARTH_RADIUS

    /** 1 タイルのピクセル数（標準のラスタタイル）。 */
    const val TILE_PIXELS = 256

    /** 緯度経度 → メルカトル座標（メートル）。x は東が正、y は北が正。 */
    fun toMeters(p: GeoPoint): Pair<Double, Double> {
        val lat = p.lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val x = Math.toRadians(p.lon) * EARTH_RADIUS
        val y = ln(tan(PI / 4 + Math.toRadians(lat) / 2)) * EARTH_RADIUS
        return x to y
    }

    /** メルカトル座標（メートル）→ 緯度経度。 */
    fun toGeo(
        x: Double,
        y: Double,
    ): GeoPoint {
        val lon = Math.toDegrees(x / EARTH_RADIUS)
        val lat = Math.toDegrees(2 * atan(exp(y / EARTH_RADIUS)) - PI / 2)
        return GeoPoint(lat, lon)
    }

    /** ズーム [zoom] の 1 タイルが覆うメルカトル上の長さ（メートル）。 */
    fun tileSizeMeters(zoom: Int): Double = 2 * HALF_WORLD / 2.0.pow(zoom)

    /** メルカトル座標が属するタイル番号（x, y）。y は北から数える。 */
    fun tileIndex(
        x: Double,
        y: Double,
        zoom: Int,
    ): Pair<Int, Int> {
        val size = tileSizeMeters(zoom)
        val n = 1 shl zoom
        val tx = floor((x + HALF_WORLD) / size).toInt().coerceIn(0, n - 1)
        val ty = floor((HALF_WORLD - y) / size).toInt().coerceIn(0, n - 1)
        return tx to ty
    }

    /** タイル (tx, ty) の左上（北西）のメルカトル座標。 */
    fun tileOrigin(
        tx: Int,
        ty: Int,
        zoom: Int,
    ): Pair<Double, Double> {
        val size = tileSizeMeters(zoom)
        return (tx * size - HALF_WORLD) to (HALF_WORLD - ty * size)
    }

    /** メルカトルが定義される緯度の上限（タイル地図の慣例）。 */
    const val MAX_LATITUDE = 85.05112878
}

/** 画面に貼るタイル 1 枚: どのタイルを、画面上のどこに、何ピクセル角で描くか。 */
data class TileSpec(
    val zoom: Int,
    val x: Int,
    val y: Int,
    /** 画面上の左上（ピクセル）。 */
    val left: Double,
    val top: Double,
    /** 画面上の一辺（ピクセル）。 */
    val size: Double,
) {
    /** キャッシュや URL のキー。 */
    val key: String get() = "$zoom/$x/$y"
}
