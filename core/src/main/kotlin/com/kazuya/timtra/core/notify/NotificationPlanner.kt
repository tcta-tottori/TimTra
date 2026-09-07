package com.kazuya.timtra.core.notify

import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.model.Bound
import java.time.Duration
import java.time.LocalDateTime

/** 通知タイミング（CLAUDE.md 8）。設定画面から変更できる。 */
data class NotificationTiming(
    /** 家（職場）を出る時刻の何分前に「あと N 分で出発」を出すか。 */
    val beforeLeave: Duration = Duration.ofMinutes(10),
    /** 最初の乗り物（往路: バス、復路: JR）の発車の何分前に「まもなく発車」を出すか。 */
    val beforeFirstLegDeparture: Duration = Duration.ofMinutes(3),
    /** 鳥取駅到着予定の何分前に「次は鳥取駅」を出すか。 */
    val beforeTransferArrival: Duration = Duration.ofMinutes(2),
) {
    init {
        listOf(beforeLeave, beforeFirstLegDeparture, beforeTransferArrival).forEach {
            require(!it.isNegative) { "通知タイミングに負の時間は指定できません" }
        }
    }
}

/** 通知の種類。往路・復路それぞれで 4 通り。 */
enum class NotificationKind {
    /** 家（職場）を出る N 分前: 「あと10分で出発。7:05のバスです」 */
    LEAVE_SOON,

    /** 出発時刻: 「出発時刻です」 */
    LEAVE_NOW,

    /** 最初の乗り物の発車 N 分前: 「まもなく南吉成発。乗り遅れ注意」 */
    FIRST_LEG_DEPARTING,

    /** 鳥取駅到着の N 分前: 「次は鳥取駅。JRは07:33発」 */
    APPROACHING_TRANSFER,
}

/** 予約する 1 件の通知。 */
data class PlannedNotification(
    /** 往路/復路 × 種類 で一意。AlarmManager の requestCode と通知 ID に使う。 */
    val id: Int,
    val kind: NotificationKind,
    val fireAt: LocalDateTime,
    val journey: Journey,
) {
    val bound: Bound get() = journey.bound

    companion object {
        fun idOf(
            bound: Bound,
            kind: NotificationKind,
        ): Int = (bound.ordinal + 1) * ID_BLOCK + kind.ordinal

        /** 取り得るすべての ID。予約の全解除に使う。 */
        val ALL_IDS: List<Int> = Bound.entries.flatMap { b -> NotificationKind.entries.map { k -> idOf(b, k) } }

        private const val ID_BLOCK = 100
    }
}

/**
 * 1 つの Journey から、出すべき通知とその時刻を求める。純粋関数。
 *
 * 往路は CLAUDE.md 8 の 4 件。復路は宝木駅の発車 N 分前の 1 件だけ
 * （職場を出る時刻は勤務先リマインダーが担い、鳥取駅でのバス発車の通知は不要）。
 */
object NotificationPlanner {
    /**
     * @param notBefore この時刻以前に鳴るはずだった通知は捨てる（当日の再計算用）。null なら全件。
     */
    fun plan(
        journey: Journey,
        timing: NotificationTiming = NotificationTiming(),
        notBefore: LocalDateTime? = null,
    ): List<PlannedNotification> {
        val firstLegDeparture =
            when (journey.bound) {
                Bound.OUTBOUND -> journey.bus.departureAt
                Bound.INBOUND -> journey.train.departureAt
            }
        val transferArrival =
            when (journey.bound) {
                Bound.OUTBOUND -> journey.bus.arrivalAt
                Bound.INBOUND -> journey.train.arrivalAt
            }
        val times =
            when (journey.bound) {
                Bound.OUTBOUND ->
                    listOf(
                        NotificationKind.LEAVE_SOON to journey.leaveAt.minus(timing.beforeLeave),
                        NotificationKind.LEAVE_NOW to journey.leaveAt,
                        NotificationKind.FIRST_LEG_DEPARTING to firstLegDeparture.minus(timing.beforeFirstLegDeparture),
                        NotificationKind.APPROACHING_TRANSFER to transferArrival.minus(timing.beforeTransferArrival),
                    )
                // 復路は「まもなく宝木発」だけ。バス（鳥取駅発）の通知は出さない
                Bound.INBOUND -> listOf(NotificationKind.FIRST_LEG_DEPARTING to firstLegDeparture.minus(timing.beforeFirstLegDeparture))
            }
        return times
            .filter { (_, at) -> notBefore == null || at.isAfter(notBefore) }
            .map { (kind, at) -> PlannedNotification(PlannedNotification.idOf(journey.bound, kind), kind, at, journey) }
            .sortedWith(compareBy({ it.fireAt }, { it.id }))
    }
}
