package com.kazuya.timtra.core.geo

import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places

/** 地図に出す地点の種類。アイコンと色は UI 側で決める。 */
enum class LandmarkKind {
    /** 南吉成 バス停（自宅側） */
    HOME_STOP,

    /** 鳥取駅（バスターミナル。JR 駅舎とは約 150 m しか離れていないので地図では 1 点にまとめる） */
    STATION,

    /** JR 宝木駅 */
    HOUGI_STATION,

    /** 勤務先（設定で登録したとき） */
    WORKPLACE,
}

data class Landmark(
    val kind: LandmarkKind,
    val name: String,
    val location: GeoPoint,
)

/**
 * 固定経路（CLAUDE.md 2）上の地点。往路の順に並ぶ。
 * バス停は GTFS の stops から、JR 駅は [Places]、勤務先は設定値。
 */
data class RouteLandmarks(
    val all: List<Landmark>,
) {
    fun find(kind: LandmarkKind): Landmark? = all.firstOrNull { it.kind == kind }

    /**
     * 地図の初期表示で収める地点。往路は自宅側（南吉成・鳥取駅）、復路は勤務先側（宝木・勤務先）。
     * 経路全体（約 18 km）を常に出すと自宅側の 2 点が重なるため、近い側に寄せる。
     */
    fun focusFor(bound: Bound): List<Landmark> {
        val kinds =
            when (bound) {
                Bound.OUTBOUND -> setOf(LandmarkKind.HOME_STOP, LandmarkKind.STATION)
                Bound.INBOUND -> setOf(LandmarkKind.HOUGI_STATION, LandmarkKind.WORKPLACE)
            }
        return all.filter { it.kind in kinds }.ifEmpty { all }
    }

    /** 現在地から各地点までの距離（メートル）。近い順。 */
    fun distancesFrom(here: GeoPoint): List<Pair<Landmark, Double>> =
        all.map { it to here.distanceMetersTo(it.location) }.sortedBy { it.second }

    companion object {
        /**
         * @param homeStop 南吉成の位置（GTFS）。無ければ地点から外す。
         * @param stationBusStop 鳥取駅バスターミナルの位置（GTFS）。無ければ JR 駅舎の位置で代用する。
         * @param workplace 設定で登録した勤務先。未登録なら null（宝木駅で代用せず、地点から外す）。
         */
        fun build(
            homeStop: GeoPoint?,
            stationBusStop: GeoPoint?,
            workplace: GeoPoint?,
            homeStopName: String = "南吉成",
            stationName: String = "鳥取駅",
            hougiName: String = "宝木駅",
            workplaceName: String = "勤務先",
        ): RouteLandmarks =
            RouteLandmarks(
                listOfNotNull(
                    homeStop?.let { Landmark(LandmarkKind.HOME_STOP, homeStopName, it) },
                    Landmark(LandmarkKind.STATION, stationName, stationBusStop ?: Places.TOTTORI_STATION),
                    Landmark(LandmarkKind.HOUGI_STATION, hougiName, Places.HOUGI_STATION),
                    workplace?.let { Landmark(LandmarkKind.WORKPLACE, workplaceName, it) },
                ),
            )
    }
}
