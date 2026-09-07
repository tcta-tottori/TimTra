package com.kazuya.timtra.core.geo

import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places

/** 経路のどちら側か。地図の拡大表示の単位。 */
enum class RouteSide {
    /** 自宅側: 南吉成・鳥取駅 */
    HOME,

    /** 勤務先側: 宝木駅・勤務先 */
    WORK,
}

/** 地図に出す地点の種類。アイコンと色は UI 側で決める。 */
enum class LandmarkKind(
    val side: RouteSide,
) {
    /** 南吉成 バス停（自宅側） */
    HOME_STOP(RouteSide.HOME),

    /** 鳥取駅（バスターミナル。JR 駅舎とは約 150 m しか離れていないので地図では 1 点にまとめる） */
    STATION(RouteSide.HOME),

    /** JR 宝木駅 */
    HOUGI_STATION(RouteSide.WORK),

    /** 勤務先（設定で登録したとき） */
    WORKPLACE(RouteSide.WORK),
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
     * 地図の初期表示で収める地点。
     *
     * 現在地が分かるときはそれを優先する: 自宅側（南吉成・鳥取駅）か勤務先側（宝木・勤務先）のうち
     * 近いほうの側だけを拡大し、どちらからも [NEAR_SIDE_METERS] より離れている（移動中）ときは経路全体を出す。
     * 現在地が無い、または遠く（[FAR_METERS] 超。出張先など）にいるときは向きで決める
     * （往路 → 自宅側、復路 → 勤務先側）。経路全体（約 14 km）を常に出すと自宅側の 2 点が重なるため。
     */
    fun focusFor(
        bound: Bound,
        here: GeoPoint? = null,
    ): List<Landmark> {
        val side = sideFor(bound, here)
        return all.filter { it.kind.side == side }.ifEmpty { all }
    }

    /** [focusFor] で選ぶ側。null は経路全体。 */
    fun sideFor(
        bound: Bound,
        here: GeoPoint?,
    ): RouteSide? {
        val byBound = if (bound == Bound.OUTBOUND) RouteSide.HOME else RouteSide.WORK
        if (here == null) return byBound
        val nearest = distancesFrom(here).firstOrNull() ?: return byBound
        return when {
            nearest.second > FAR_METERS -> byBound
            nearest.second > NEAR_SIDE_METERS -> null
            else -> nearest.first.kind.side
        }
    }

    /** 現在地から各地点までの距離（メートル）。近い順。 */
    fun distancesFrom(here: GeoPoint): List<Pair<Landmark, Double>> =
        all.map { it to here.distanceMetersTo(it.location) }.sortedBy { it.second }

    companion object {
        /** 最寄りの地点がこの距離以内なら、その側だけを拡大して出す。 */
        const val NEAR_SIDE_METERS = 4_000.0

        /** 最寄りの地点がこの距離より遠ければ通勤圏外とみなし、向きで側を決める。 */
        const val FAR_METERS = 40_000.0

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
