package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places

/** 往路/復路をどう決めたか。ホーム画面の脚注に出す。 */
enum class BoundBasis {
    /** ユーザーが手動で切り替えた */
    MANUAL,

    /** 現在地が南吉成（自宅側）に近い → 往路 */
    NEAR_HOME,

    /** 現在地が宝木駅（勤務先側）に近い → 復路 */
    NEAR_WORK,

    /** 位置が取れない、または途中（鳥取駅など）にいる → 設定の時刻帯で判定 */
    TIME_OF_DAY,
}

data class BoundDecision(
    val bound: Bound,
    val basis: BoundBasis,
)

/**
 * 現在地から「いま出発すべき場所」を決める。
 * 自宅側のバス停の近くなら往路（家を出る時刻）、宝木駅の近くなら復路（職場を出る時刻）。
 * 鳥取駅のような途中や、位置が取れないときは時刻帯の判定に戻す。
 */
object BoundResolver {
    /** 南吉成からこの距離以内なら自宅側。鳥取駅までは約 1.7 km あるので、それより小さくする。 */
    const val NEAR_HOME_METERS = 1_200.0

    /** 宝木駅からこの距離以内なら勤務先側（勤務先は駅から徒歩・自転車 10 分）。 */
    const val NEAR_WORK_METERS = 5_000.0

    fun resolve(
        location: GeoPoint?,
        homeStop: GeoPoint?,
        byTime: Bound,
        workStation: GeoPoint = Places.HOUGI_STATION,
    ): BoundDecision {
        if (location == null) return BoundDecision(byTime, BoundBasis.TIME_OF_DAY)
        val toHome = homeStop?.let(location::distanceMetersTo)
        val toWork = location.distanceMetersTo(workStation)
        return when {
            toHome != null && toHome <= NEAR_HOME_METERS && toHome <= toWork -> BoundDecision(Bound.OUTBOUND, BoundBasis.NEAR_HOME)
            toWork <= NEAR_WORK_METERS -> BoundDecision(Bound.INBOUND, BoundBasis.NEAR_WORK)
            else -> BoundDecision(byTime, BoundBasis.TIME_OF_DAY)
        }
    }
}
