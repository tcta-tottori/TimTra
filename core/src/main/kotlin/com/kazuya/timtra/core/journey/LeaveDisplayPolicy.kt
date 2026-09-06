package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 「家を出る時刻」「職場を出る時刻」をホームに出すかどうか。
 *
 * - 家を出る: 朝の決まった時間帯（既定 05:30〜06:50、6:48 発のバスに乗る前提）だけ出す。
 *   それを過ぎれば、もう家にはいないので出さない。
 * - 職場を出る: 既定 17:00 から、その日の終電（アンカーの JR 便が今日のうち）まで出す。
 *   ただし現在地が勤務先を離れて鳥取駅周辺まで来た（または同じだけ離れた）ときは出さない。
 */
object LeaveDisplayPolicy {
    /** 鳥取駅からこの距離以内なら「鳥取駅周辺」。 */
    const val NEAR_STATION_METERS = 1_500.0

    /**
     * 勤務先からこれ以上離れていれば「離れた」と判定する下限。
     * 勤務先と鳥取駅が近い設定（未登録で宝木駅代用など）でも、基地局測位の誤差で誤って隠さないようにする。
     */
    const val MIN_AWAY_METERS = 3_000.0

    /** 家を出る時刻を出す時間帯か（[start] 以上 [end] 未満）。 */
    fun showLeaveHome(
        now: LocalDateTime,
        settings: CommuteSettings,
    ): Boolean = isWithin(now.toLocalTime(), settings.leaveHomeDisplayStart, settings.leaveHomeDisplayEnd)

    /**
     * 職場を出る時刻を出すか。
     * @param trainDate 案のアンカー（宝木発の JR 便）の日付。今日でなければ終電後なので出さない。
     * @param location 現在地。取れなければ時刻だけで判定する。
     */
    fun showLeaveWork(
        now: LocalDateTime,
        trainDate: LocalDate,
        location: GeoPoint?,
        workplace: GeoPoint,
        settings: CommuteSettings,
        station: GeoPoint = Places.TOTTORI_STATION,
    ): Boolean {
        if (now.toLocalTime().isBefore(settings.leaveWorkDisplayStart)) return false
        if (trainDate != now.toLocalDate()) return false
        if (location != null && location.distanceMetersTo(workplace) >= awayThresholdMeters(workplace, station)) return false
        return true
    }

    /** 勤務先からこの距離以上離れたら隠す。鳥取駅の手前 [NEAR_STATION_METERS] で隠し始める。 */
    fun awayThresholdMeters(
        workplace: GeoPoint,
        station: GeoPoint = Places.TOTTORI_STATION,
    ): Double = maxOf(workplace.distanceMetersTo(station) - NEAR_STATION_METERS, MIN_AWAY_METERS)

    private fun isWithin(
        time: LocalTime,
        start: LocalTime,
        end: LocalTime,
    ): Boolean =
        if (!start.isAfter(end)) {
            !time.isBefore(start) && time.isBefore(end)
        } else {
            // 深夜をまたぐ指定（例: 23:00〜01:00）
            !time.isBefore(start) || time.isBefore(end)
        }
}
