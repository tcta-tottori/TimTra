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
     * JR 宝木駅（鳥取市気高町宝木）。山陰本線が県道 182 号と交わる少し東、OSM の駅記号の位置。
     * 2026-09-07 に Google マップの徒歩経路（勤務先 → 宝木駅、約 1.0 km 東・0.4 km 南）と
     * OpenStreetMap タイル上の駅記号の両方から読み取って確定。それ以前の値は国道 9 号との交差点付近（約 400 m 北西）や
     * 青谷寄り（約 5 km 西）にずれていた。南吉成からは約 14 km 西。
     */
    val HOUGI_STATION = GeoPoint(35.5147, 134.0819)

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

    /**
     * 勤務先 → 宝木駅 の徒歩経路（Google マップの徒歩ルート、約 1.4 km・19〜20 分を 2026-09-07 に手でなぞった概略）。
     * 勤務先から南へ下りて国道 9 号に出て、河内川を渡って東へ進み、県道 182 号沿いに南東へ折れて駅に至る。
     * 地図の描画にだけ使う（所要時間は設定の walkStationToWork）。勤務先を別の場所に登録したときは使わない。
     */
    val WORKPLACE_TO_HOUGI_WALK: List<GeoPoint> =
        listOf(
            WORKPLACE_DEFAULT,
            GeoPoint(35.5177, 134.0709),
            GeoPoint(35.5169, 134.0713),
            GeoPoint(35.5164, 134.0719),
            GeoPoint(35.5168, 134.0734),
            GeoPoint(35.5165, 134.0745),
            GeoPoint(35.5161, 134.0761),
            GeoPoint(35.5157, 134.0777),
            GeoPoint(35.5153, 134.0785),
            GeoPoint(35.5155, 134.0797),
            GeoPoint(35.5151, 134.0809),
            HOUGI_STATION,
        )

    /** 登録した勤務先（自宅）が既定位置からこの距離以内なら、上の徒歩経路をそのまま使う。 */
    const val WALK_PATH_MATCH_METERS = 200.0

    /**
     * 自宅の既定位置（南吉成バス停の南南東 約 160 m）。2026-09-07 20:17 の現在地から。
     * 設定画面の「現在地を自宅に登録」で保存した値があればそちらを優先する。
     */
    val HOME_DEFAULT = GeoPoint(35.47885, 134.21917)

    /**
     * 自宅 → 南吉成バス停（鳥取駅方面のりば）の徒歩経路。2026-09-07 に本人が地図上に描いたルートをなぞった値
     * （家を出て北へ、路地を西へ折れ、住宅地を抜けて国道 53 号沿いのバス停へ。約 240 m）。
     */
    val HOME_TO_MINAMIYOSHINARI_WALK: List<GeoPoint> =
        listOf(
            HOME_DEFAULT,
            GeoPoint(35.47907, 134.21934),
            GeoPoint(35.47940, 134.21942),
            GeoPoint(35.47939, 134.21913),
            GeoPoint(35.47923, 134.21898),
            GeoPoint(35.47936, 134.21884),
            GeoPoint(35.47952, 134.21867),
            GeoPoint(35.47967, 134.21867),
            GeoPoint(35.47976, 134.21881),
            GeoPoint(35.47989, 134.21857),
            GeoPoint(35.48017, 134.21854),
        )

    /**
     * JR 山陰本線 鳥取 → 宝木 の線路の概略（地図の描画用）。
     * 鳥取駅から千代川を渡って湖山・鳥取大学前までは OSM タイル上の線路からなぞった値、
     * 末恒〜宝木は駅位置と地形からの推定。ずれがあれば点を足して直す。
     */
    val TOTTORI_TO_HOUGI_RAIL: List<GeoPoint> =
        listOf(
            TOTTORI_STATION,
            GeoPoint(35.4960, 134.2153),
            GeoPoint(35.4998, 134.2078),
            GeoPoint(35.5039, 134.2002),
            GeoPoint(35.5088, 134.1907),
            GeoPoint(35.5122, 134.1825),
            GeoPoint(35.5153, 134.1718),
            GeoPoint(35.5172, 134.1600),
            GeoPoint(35.5175, 134.1490),
            GeoPoint(35.5155, 134.1375),
            GeoPoint(35.5150, 134.1230),
            GeoPoint(35.5140, 134.1080),
            GeoPoint(35.5159, 134.0899),
            HOUGI_STATION,
        )
}
