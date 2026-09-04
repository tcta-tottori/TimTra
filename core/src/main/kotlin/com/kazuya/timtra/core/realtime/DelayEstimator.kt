package com.kazuya.timtra.core.realtime

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 車両位置と stop_times 上の予定位置を比較して遅延を推定する（CLAUDE.md 4-2）。
 *
 * 例: 予定ではすでに南吉成を出ているはずだが、車両はまだ 2 停留所手前 → その地点の予定時刻と
 * 現在時刻の差が遅延。
 *
 * 手順:
 * 1. current_stop_sequence があればそれを使う（停車中なら発時刻、走行中なら直前区間に投影）。
 * 2. 無ければ、停留所を結ぶ線分のうち車両に最も近いものへ投影し、区間内の予定時刻を線形補間する。
 * 3. 遅延 = 観測時刻 − その地点の予定時刻。
 */
class DelayEstimator(
    /** これより古い車両タイムスタンプは使わない。 */
    private val maxAge: Duration = Duration.ofMinutes(3),
    /** 線分からこれ以上離れていれば、その便の車両とはみなさない（m）。 */
    private val maxDistanceMeters: Double = 800.0,
) {
    fun estimate(
        tripId: String,
        serviceDate: LocalDate,
        stopTimes: List<TripStopTime>,
        vehicle: VehiclePosition,
        now: Instant,
        zone: ZoneId,
    ): DelayEstimate? {
        val observedAt = vehicle.observedAt ?: now
        if (Duration.between(observedAt, now) > maxAge) return null
        val stops = stopTimes.sortedBy { it.stopSequence }
        if (stops.size < 2) return null
        val dayStart = serviceDate.atStartOfDay(zone).toInstant()

        bySequence(stops, vehicle, dayStart, observedAt)?.let { return it.copy(tripId = tripId) }
        return byNearestSegment(stops, vehicle, dayStart, observedAt)?.copy(tripId = tripId)
    }

    private fun bySequence(
        stops: List<TripStopTime>,
        vehicle: VehiclePosition,
        dayStart: Instant,
        observedAt: Instant,
    ): DelayEstimate? {
        val seq = vehicle.currentStopSequence ?: return null
        val index = stops.indexOfFirst { it.stopSequence == seq }
        if (index < 0) return null
        val stop = stops[index]
        val status = vehicle.currentStatus ?: VehicleStopStatus.IN_TRANSIT_TO
        if (status == VehicleStopStatus.STOPPED_AT || index == 0) {
            val scheduled = dayStart.plusSeconds(stop.departure.seconds.toLong())
            return DelayEstimate("", Duration.between(scheduled, observedAt), EstimateMethod.STOP_SEQUENCE, observedAt, seq, null)
        }
        // 走行中: 直前の停留所との間に投影して補間する（座標が無ければ次停留所の着時刻で近似）
        val prev = stops[index - 1]
        val projected = project(prev, stop, vehicle)
        val fraction = projected?.fraction ?: 1.0
        val scheduledSeconds = prev.departure.seconds + fraction * (stop.arrival.seconds - prev.departure.seconds)
        val scheduled = dayStart.plusSeconds(scheduledSeconds.toLong())
        return DelayEstimate(
            "",
            Duration.between(scheduled, observedAt),
            EstimateMethod.STOP_SEQUENCE,
            observedAt,
            prev.stopSequence,
            projected?.distanceMeters,
        )
    }

    private fun byNearestSegment(
        stops: List<TripStopTime>,
        vehicle: VehiclePosition,
        dayStart: Instant,
        observedAt: Instant,
    ): DelayEstimate? {
        var best: Projection? = null
        var bestIndex = -1
        for (i in 0 until stops.size - 1) {
            val p = project(stops[i], stops[i + 1], vehicle) ?: continue
            // 停留所上で前後の線分が同距離になったときは、後ろの線分（その停留所を始点とする区間）を採る
            if (best == null || p.distanceMeters <= best.distanceMeters + TIE_METERS) {
                best = p
                bestIndex = i
            }
        }
        val projection = best ?: return null
        if (projection.distanceMeters > maxDistanceMeters) return null
        val from = stops[bestIndex]
        val to = stops[bestIndex + 1]
        val scheduledSeconds = from.departure.seconds + projection.fraction * (to.arrival.seconds - from.departure.seconds)
        val scheduled = dayStart.plusSeconds(scheduledSeconds.toLong())
        return DelayEstimate(
            "",
            Duration.between(scheduled, observedAt),
            EstimateMethod.NEAREST_SEGMENT,
            observedAt,
            from.stopSequence,
            projection.distanceMeters,
        )
    }

    private data class Projection(
        /** 区間の始点を 0、終点を 1 とした位置。 */
        val fraction: Double,
        val distanceMeters: Double,
    )

    /** 車両位置を停留所 a→b の線分に投影する。座標が無ければ null。 */
    private fun project(
        a: TripStopTime,
        b: TripStopTime,
        vehicle: VehiclePosition,
    ): Projection? {
        if (!a.hasCoordinates || !b.hasCoordinates) return null
        // 数 km 程度なので正距円筒図法で平面近似する
        val lat0 = Math.toRadians(vehicle.latitude)
        val kx = EARTH_RADIUS_METERS * cos(lat0)
        val ky = EARTH_RADIUS_METERS
        val ax = Math.toRadians(a.longitude!! - vehicle.longitude) * kx
        val ay = Math.toRadians(a.latitude!! - vehicle.latitude) * ky
        val bx = Math.toRadians(b.longitude!! - vehicle.longitude) * kx
        val by = Math.toRadians(b.latitude!! - vehicle.latitude) * ky
        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / lengthSq
        val fraction = t.coerceIn(0.0, 1.0)
        val px = ax + fraction * dx
        val py = ay + fraction * dy
        return Projection(fraction, sqrt(px * px + py * py))
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val TIE_METERS = 0.5
    }
}
