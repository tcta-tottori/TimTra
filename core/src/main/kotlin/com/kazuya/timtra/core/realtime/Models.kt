package com.kazuya.timtra.core.realtime

import com.kazuya.timtra.core.model.GtfsTime
import java.time.Duration
import java.time.Instant

/** GTFS-RT VehiclePosition の 1 台分（必要な項目だけ）。 */
data class VehiclePosition(
    val vehicleId: String?,
    val tripId: String?,
    val routeId: String?,
    val latitude: Double,
    val longitude: Double,
    val stopId: String?,
    val currentStopSequence: Int?,
    val currentStatus: VehicleStopStatus?,
    /** 車両側のタイムスタンプ。無ければ null（取得時刻で代用する）。 */
    val observedAt: Instant?,
)

enum class VehicleStopStatus { INCOMING_AT, STOPPED_AT, IN_TRANSIT_TO }

/** 1 回の取得結果。 */
data class VehiclePositionFeed(
    val fetchedAt: Instant,
    val feedTimestamp: Instant?,
    val vehicles: List<VehiclePosition>,
) {
    fun forTrip(tripId: String): VehiclePosition? = vehicles.firstOrNull { it.tripId == tripId }
}

/** 1 便の停車時刻と停留所座標（stop_times + stops）。遅延推定に使う。 */
data class TripStopTime(
    val stopId: String,
    val stopSequence: Int,
    val arrival: GtfsTime,
    val departure: GtfsTime,
    val latitude: Double?,
    val longitude: Double?,
) {
    val hasCoordinates: Boolean get() = latitude != null && longitude != null
}

enum class EstimateMethod {
    /** current_stop_sequence と停車状態から求めた */
    STOP_SEQUENCE,

    /** 車両位置を停留所間の線分に投影して求めた */
    NEAREST_SEGMENT,
}

/**
 * 遅延の推定値。TripUpdate が提供されないため、車両位置と stop_times 上の予定位置の比較から求める（CLAUDE.md 4-2）。
 * 推定であることを UI で必ず明示する。
 */
data class DelayEstimate(
    val tripId: String,
    /** 正なら遅れ、負なら早発。 */
    val delay: Duration,
    val method: EstimateMethod,
    val observedAt: Instant,
    /** 車両が今いる（または直前に通過した）停留所の stop_sequence。 */
    val stopSequence: Int,
    /** 投影した線分までの距離（m）。STOP_SEQUENCE の場合は null。 */
    val distanceMeters: Double?,
) {
    /** 乗り継ぎ計算に足す値。早発はあてにしないので 0 に丸める。 */
    val delayForPlanning: Duration get() = if (delay.isNegative) Duration.ZERO else delay
}
