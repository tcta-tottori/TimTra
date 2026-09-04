package com.kazuya.timtra.core.model

/**
 * 南吉成⇔鳥取駅を通るバス 1 便。プリパッケージ DB の commute_legs に
 * routes / trips / stops を結合したもの。時刻はサービス日基準の [GtfsTime]。
 */
data class BusTrip(
    val tripId: String,
    val routeId: String,
    /** 系統番号（例: "91"）。 */
    val routeShortName: String,
    /** 行先表示（例: "鳥取駅", "智頭"）。 */
    val headsign: String,
    val serviceId: String,
    val direction: BusDirection,
    val boardStop: BusStop,
    val alightStop: BusStop,
    /** 乗車停留所の発時刻。 */
    val departure: GtfsTime,
    /** 降車停留所の着時刻。 */
    val arrival: GtfsTime,
) {
    init {
        require(arrival >= departure) { "到着が出発より前です: $tripId" }
    }
}

/** 停留所。鳥取駅は乗り場ごとに別の stop_id を持つ。 */
data class BusStop(
    val stopId: String,
    val name: String,
    /** 乗り場番号（例: "5"）。無ければ null。 */
    val platformCode: String? = null,
    val role: StopRole? = null,
)

/** プリパッケージ DB の stops.role。 */
enum class StopRole(
    val dbValue: String,
) {
    /** 南吉成（自宅側） */
    HOME("HOME"),

    /** 鳥取駅（駅側） */
    STATION("STATION"),
    ;

    companion object {
        fun fromDbValue(value: String?): StopRole? = entries.firstOrNull { it.dbValue == value }
    }
}
