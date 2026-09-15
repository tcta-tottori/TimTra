package com.kazuya.timtra.core.board

import com.kazuya.timtra.core.journey.CommuteSettings
import java.time.Duration
import java.time.LocalDateTime

/**
 * 時計ホームの、乗り物アイコンを囲むリングの進み具合（0〜1）。
 *
 * 表すのは「発車まであと何割か」ではなく **「そろそろ出ないと間に合わない」** の度合い。
 * 満タンの位置はユーザー設定から決まる出発目安時刻
 * （= 発車時刻 − その地点までの移動時間 − 準備時間）で、そこから先はずっとフルのままにする。
 *
 * - 残り時間 > 移動時間 ×2 + 準備時間 → 0（空。リングは動かない）
 * - その間                            → 0 → 1 へ線形に満ちる（設定した移動時間ぶんかけて満ちる）
 * - 残り時間 ≤ 移動時間 + 準備時間     → 1（出発目安時刻を過ぎている＝もう出る時間）
 */
object CountdownGauge {
    /**
     * @param travel 自宅・勤務先からその地点までの移動時間（設定値）。
     * @param prep 準備時間（バッファ、設定値）。
     */
    fun progress(
        now: LocalDateTime,
        departure: LocalDateTime,
        travel: Duration,
        prep: Duration,
    ): Float {
        val remaining = Duration.between(now, departure)
        val full = travel + prep
        val start = full + travel
        return when {
            remaining <= full -> 1f
            remaining >= start -> 0f
            // start → full の区間を 0 → 1 に写す。travel が 0 なら区間が無いので上の 2 つで片が付く
            else -> (start - remaining).toMillis().toFloat() / travel.toMillis().toFloat()
        }
    }

    /** [place] へ向かうのにかかる、設定上の移動時間。 */
    fun travelTo(
        place: BoardPlace,
        settings: CommuteSettings,
    ): Duration =
        when (place) {
            // 往路の出発地。自宅から歩く
            BoardPlace.HOME_STOP -> settings.walkHomeToStop
            // 復路の出発地。勤務先から歩く
            BoardPlace.HOUGI_JR -> settings.walkStationToWork
            // 鳥取駅は乗換。1 本目を降りてから歩く時間
            BoardPlace.TOTTORI_JR, BoardPlace.STATION_BUS -> settings.transferBusToJr
        }
}
