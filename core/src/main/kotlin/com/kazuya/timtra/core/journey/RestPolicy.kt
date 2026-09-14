package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.GeoPoint
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 「今日の通勤は終わり」の判定。夜、鳥取駅を離れて帰路についたら（または自宅側にいたら）残り時間の表示をやめ、
 * 翌朝の表示開始時刻（leaveHomeDisplayStart、既定 05:30）から再び出す。
 *
 * 判定は「朝の時間帯の外」かつ「自宅側にいる」。
 * - 朝の時間帯: leaveHomeDisplayStart 以上 inboundWindowStart 未満
 * - 自宅側: 南吉成から [NEAR_HOME_METERS] 以内、
 *   または復路で鳥取駅から [LEFT_STATION_METERS] 超離れ、南吉成までが鳥取駅〜南吉成の距離より近い（バスで帰宅中）
 */
object RestPolicy {
    const val NEAR_HOME_METERS = BoundResolver.NEAR_HOME_METERS
    const val LEFT_STATION_METERS = 1_000.0

    fun isResting(
        now: LocalDateTime,
        bound: Bound,
        here: GeoPoint?,
        homeStop: GeoPoint?,
        station: GeoPoint,
        settings: CommuteSettings,
    ): Boolean {
        if (here == null || homeStop == null) return false
        if (isMorningWindow(now.toLocalTime(), settings)) return false
        val toHome = here.distanceMetersTo(homeStop)
        val toStation = here.distanceMetersTo(station)
        if (toHome <= NEAR_HOME_METERS) return true
        // 鳥取駅〜南吉成の間（バスで帰宅中）。勤務先など遠くでは両方とも遠いので該当しない
        val corridor = homeStop.distanceMetersTo(station)
        return bound == Bound.INBOUND && toStation > LEFT_STATION_METERS && toHome < corridor
    }

    /** 朝の通勤の時間帯（この間は必ず表示する）。 */
    fun isMorningWindow(
        time: LocalTime,
        settings: CommuteSettings,
    ): Boolean = !time.isBefore(settings.leaveHomeDisplayStart) && time.isBefore(settings.inboundWindowStart)
}
