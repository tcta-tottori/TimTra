package com.kazuya.timtra.core.journey

import java.time.Duration
import kotlin.math.ceil

/** 出発地点（駅・バス停）までどのくらい急ぐ必要があるか。 */
enum class Pace {
    /** 普通に歩いて間に合う */
    WALK,

    /** 早歩きなら間に合う */
    FAST_WALK,

    /** 走れば間に合う */
    RUN,

    /** 走っても間に合わない */
    TOO_LATE,
}

data class PaceAdvice(
    val pace: Pace,
    /** 道なりの想定距離（メートル）。 */
    val routeMeters: Double,
    /** 普通に歩いたときの所要（分、切り上げ）。 */
    val walkMinutes: Long,
    /** 早歩きの所要（分、切り上げ）。 */
    val fastWalkMinutes: Long,
    /** すでに出発地点にいる（[PaceAdvisor.AT_PLACE_METERS] 以内）。 */
    val atPlace: Boolean,
)

/**
 * 現在地から出発地点までの距離と発車までの残り時間から、歩き / 早歩き / 走る / 間に合わない を判定する。
 *
 * 速度は 2026-09-07 の実測に合わせる: 勤務先 → 宝木駅 の道なり約 1.4 km（直線約 1.13 km）を
 * 「ちょっと早歩き」で 15 分ちょうど → 約 95 m/分。普通の歩きは 80 m/分、走りは 140 m/分とする。
 */
object PaceAdvisor {
    /** 直線距離 → 道なり距離 の係数（勤務先〜宝木駅で 1.4 / 1.13 ≒ 1.25）。 */
    const val ROUTE_FACTOR = 1.25
    const val WALK_METERS_PER_MINUTE = 80.0
    const val FAST_WALK_METERS_PER_MINUTE = 95.0
    const val RUN_METERS_PER_MINUTE = 140.0

    /** この距離以内なら「もう着いている」。基地局測位の誤差も見込む。 */
    const val AT_PLACE_METERS = 120.0

    /** 改札・ホームまでの余裕。到着してから乗るまでにかかる分。 */
    val BOARDING_BUFFER: Duration = Duration.ofMinutes(1)

    fun advise(
        straightMeters: Double,
        remaining: Duration,
    ): PaceAdvice {
        val route = if (straightMeters <= AT_PLACE_METERS) 0.0 else straightMeters * ROUTE_FACTOR
        val walkMinutes = ceil(route / WALK_METERS_PER_MINUTE).toLong()
        val fastMinutes = ceil(route / FAST_WALK_METERS_PER_MINUTE).toLong()
        val available = remaining.minus(BOARDING_BUFFER).seconds.toDouble() / 60.0
        val pace =
            when {
                available >= route / WALK_METERS_PER_MINUTE -> Pace.WALK
                available >= route / FAST_WALK_METERS_PER_MINUTE -> Pace.FAST_WALK
                available >= route / RUN_METERS_PER_MINUTE -> Pace.RUN
                else -> Pace.TOO_LATE
            }
        return PaceAdvice(pace, route, walkMinutes, fastMinutes, atPlace = straightMeters <= AT_PLACE_METERS)
    }
}
