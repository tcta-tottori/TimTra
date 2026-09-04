package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.BusTrip
import com.kazuya.timtra.core.model.JrService
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** 乗り継ぎの状態。色分けは UI 側で行う。 */
enum class JourneyStatus {
    /** 余裕 10 分以上（緑） */
    OK,

    /** 余裕 5〜10 分（オレンジ） */
    TIGHT,

    /** 余裕 5 分未満（赤）。fallback に代替案が入る。 */
    RISK,

    /** 乗車済みで間に合わない。fallback に次の JR 便での案が入る。 */
    MISSED,
}

/** サービス日を与えて絶対時刻にしたバス便。 */
data class ScheduledBus(
    val trip: BusTrip,
    val serviceDate: LocalDate,
) {
    val departureAt: LocalDateTime get() = trip.departure.at(serviceDate)
    val arrivalAt: LocalDateTime get() = trip.arrival.at(serviceDate)
}

/** 日付を与えて絶対時刻にした JR 便。 */
data class ScheduledTrain(
    val service: JrService,
    val date: LocalDate,
) {
    val departureAt: LocalDateTime get() = date.atTime(service.departure)
    val arrivalAt: LocalDateTime get() = (if (service.arrivesNextDay) date.plusDays(1) else date).atTime(service.arrival)
}

/**
 * 乗り継ぎ計算の結果（CLAUDE.md 6）。
 *
 * 往路: bus（南吉成→鳥取駅）に乗って train（鳥取→宝木）に乗り継ぐ。
 * 復路: train（宝木→鳥取）に乗って bus（鳥取駅→南吉成）に乗り継ぐ。
 */
data class Journey(
    val bound: Bound,
    /** 乗りたい JR 便（アンカー）。 */
    val train: ScheduledTrain,
    /** 往路: 間に合う中で最も遅いバス便。復路: JR 到着後に乗れる最初のバス便。 */
    val bus: ScheduledBus,
    /** バス便に適用した推定遅延。定刻なら ZERO。 */
    val busDelay: Duration,
    /** 家（往路）または職場（復路）を出る時刻。 */
    val leaveAt: LocalDateTime,
    /** 鳥取駅での余裕。乗換所要時間（transferBusToJr）を差し引いた後の値。負なら間に合わない。 */
    val transferMargin: Duration,
    val status: JourneyStatus,
    /** 勤務先（往路）または自宅（復路）への到着予測。 */
    val arriveAt: LocalDateTime,
    /** RISK / MISSED のときの代替案。 */
    val fallback: Journey? = null,
) {
    /** 遅延を加味したバスの鳥取駅（往路）/ 南吉成（復路）到着予測。 */
    val busArrivalEstimatedAt: LocalDateTime get() = bus.arrivalAt.plus(busDelay)

    /** 遅延を加味したバスの発車予測。 */
    val busDepartureEstimatedAt: LocalDateTime get() = bus.departureAt.plus(busDelay)

    /** 遅延情報を反映しているか。UI で「（推定）」の表示に使う。 */
    val hasDelay: Boolean get() = !busDelay.isZero
}
