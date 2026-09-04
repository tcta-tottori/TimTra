package com.kazuya.timtra.core.model

/** 通勤の向き。 */
enum class Bound {
    /** 往路: 自宅 → 南吉成 →(バス)→ 鳥取駅 →(JR)→ 宝木 → 勤務先 */
    OUTBOUND,

    /** 復路: 勤務先 → 宝木 →(JR)→ 鳥取駅 →(バス)→ 南吉成 → 自宅 */
    INBOUND,
    ;

    /** この向きで乗るバス区間の方向。 */
    val busDirection: BusDirection
        get() =
            when (this) {
                OUTBOUND -> BusDirection.TO_STATION
                INBOUND -> BusDirection.FROM_STATION
            }

    /** この向きで乗る JR 区間の ID（jr_timetable.json の legs[].id）。 */
    val jrLegId: String
        get() =
            when (this) {
                OUTBOUND -> JrLegIds.TOTTORI_TO_HOUGI
                INBOUND -> JrLegIds.HOUGI_TO_TOTTORI
            }
}

/**
 * バス区間の方向。プリパッケージ DB の commute_legs.direction と同じ文字列。
 * direction_id ではなく stop_sequence の前後関係から決めている（docs/db_schema.md）。
 */
enum class BusDirection(
    val dbValue: String,
) {
    /** 南吉成 → 鳥取駅 */
    TO_STATION("TO_STATION"),

    /** 鳥取駅 → 南吉成 */
    FROM_STATION("FROM_STATION"),
    ;

    companion object {
        fun fromDbValue(value: String): BusDirection =
            entries.firstOrNull { it.dbValue == value }
                ?: throw IllegalArgumentException("未知の方向: $value")
    }
}

/** jr_timetable.json の legs[].id。 */
object JrLegIds {
    const val TOTTORI_TO_HOUGI = "tottori_to_hougi"
    const val HOUGI_TO_TOTTORI = "hougi_to_tottori"
}
