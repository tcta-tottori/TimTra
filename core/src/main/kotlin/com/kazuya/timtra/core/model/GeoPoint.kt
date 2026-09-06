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
     * JR 宝木駅（鳥取市気高町宝木）。概算値。
     * 往路/復路の判定は南吉成（約 18 km 東）との近さ比較なので、数百 m の誤差は影響しない。
     */
    val HOUGI_STATION = GeoPoint(35.5214, 134.0196)
}
