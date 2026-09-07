package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places

/** 復路のどの段階にいるか。ホームの主役を電車にするかバスにするかを決める。 */
enum class InboundPhase {
    /** 勤務先〜宝木駅の側にいる（または位置が無い）: 宝木発の電車が主役 */
    TO_TRAIN,

    /** 宝木駅エリアを離れて鳥取方面へ向かっている（乗車中・鳥取駅到着後）: 鳥取駅発のバスが主役 */
    TO_BUS,
}

object InboundPhaseResolver {
    /** 宝木駅からこの距離を超えて離れたら、電車には乗った（乗る）ものとしてバスの案内に切り替える。 */
    const val LEFT_HOUGI_METERS = 2_000.0

    fun resolve(
        bound: Bound,
        here: GeoPoint?,
        hougi: GeoPoint = Places.HOUGI_STATION,
    ): InboundPhase {
        if (bound != Bound.INBOUND || here == null) return InboundPhase.TO_TRAIN
        return if (here.distanceMetersTo(hougi) > LEFT_HOUGI_METERS) InboundPhase.TO_BUS else InboundPhase.TO_TRAIN
    }
}
