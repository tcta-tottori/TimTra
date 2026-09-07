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
     * JR 宝木駅（鳥取市気高町宝木）。北緯 35°30′57″ 東経 134°04′40″。
     * 2026-09-07 に勤務先からの実測（Google マップ上で勤務先の東 680 m・南 290 m）と突き合わせて確定。
     * 最初の値（134.0196）は約 5 km 西（青谷寄り）にずれていた。南吉成からは約 14 km 西。
     */
    val HOUGI_STATION = GeoPoint(35.5158, 134.0778)

    /**
     * JR 鳥取駅（駅舎）。バスターミナル（GTFS の停留所）から南へ約 150 m。
     * ホーム画面の地図の目印と、「職場を出る時刻」を隠す距離判定の基準に使う。
     */
    val TOTTORI_STATION = GeoPoint(35.4934, 134.2226)

    /**
     * 勤務先（気高電機、気高町下坂本）の既定位置。宝木駅の北西約 700 m。
     * 2026-09-07 のスクリーンショット（Google マップの「職場」）から読み取った概算値。
     * 設定画面の「現在地を勤務先に登録」で端末に保存した値があればそちらを優先する。
     */
    val WORKPLACE_DEFAULT: GeoPoint = GeoPoint(35.5184, 134.0703)
}
