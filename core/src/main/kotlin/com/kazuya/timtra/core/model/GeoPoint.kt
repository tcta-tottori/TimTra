package com.kazuya.timtra.core.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 緯度経度（度）。 */
data class GeoPoint(
    val lat: Double,
    val lon: Double,
) {
    /** 大円距離（メートル）。数 km の近さ判定に使うので球面近似で十分。 */
    fun distanceMetersTo(other: GeoPoint): Double {
        val dLat = Math.toRadians(other.lat - lat)
        val dLon = Math.toRadians(other.lon - lon)
        val a =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat)) * cos(Math.toRadians(other.lat)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

/** 固定経路上の地点（CLAUDE.md 2）。バス停は GTFS から取るので、ここは JR 側だけ。 */
object Places {
    /**
     * JR 宝木駅（鳥取市気高町宝木）。北緯 35°31′06″ 東経 134°04′28″。
     * 以前の値（134.0196）は約 5 km 西（青谷寄り）にずれており、宝木にいても「宝木駅まで 5 km」と出ていた。
     * 南吉成からは約 14 km 西。
     */
    val HOUGI_STATION = GeoPoint(35.5183, 134.0746)

    /**
     * JR 鳥取駅（駅舎）。バスターミナル（GTFS の停留所）から南へ約 150 m。
     * ホーム画面の地図の目印と、「職場を出る時刻」を隠す距離判定の基準に使う。
     */
    val TOTTORI_STATION = GeoPoint(35.4934, 134.2226)

    /**
     * 勤務先（気高電機）の仮の位置。正確な座標はネットで確認できなかったので、
     * 設定画面の「現在地を勤務先に登録」で端末に保存した値を優先し、未登録の間は宝木駅の位置で代用する。
     */
    val WORKPLACE_DEFAULT: GeoPoint = HOUGI_STATION
}
