package com.kazuya.timtra.core.notify

import com.kazuya.timtra.core.model.JrLegIds
import com.kazuya.timtra.core.model.JrService
import com.kazuya.timtra.core.model.JrTimetable
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 勤務先にいるときの「次の電車まであと N 分」リマインダーの段階。
 * 30 分前は歩き、20 分前は早歩き、15 分前は走る、の目安。アイコンはこの順に切り替える。
 */
enum class ReminderStage(
    val before: Duration,
) {
    WALK(Duration.ofMinutes(30)),
    FAST_WALK(Duration.ofMinutes(20)),
    DASH(Duration.ofMinutes(15)),
}

/** 勤務先リマインダーの設定。 */
data class TrainReminderSettings(
    val enabled: Boolean = true,
    /** この時刻以降に宝木を出る電車が対象（「17 時以降」）。 */
    val windowStart: LocalTime = LocalTime.of(17, 0),
)

/** 予約する 1 件。 */
data class TrainReminder(
    /** 同じ電車の段階は同じ通知 ID にして、30 分前 → 20 分前 → 15 分前で通知を差し替える。 */
    val notificationId: Int,
    /** AlarmManager の requestCode。段階ごとに別。 */
    val requestCode: Int,
    val stage: ReminderStage,
    val train: JrService,
    val departureAt: LocalDateTime,
    val fireAt: LocalDateTime,
)

/**
 * 宝木 → 鳥取 の電車について、発車 30 / 20 / 15 分前のリマインダーを列挙する（純粋関数）。
 * 「勤務先にいるか」は予約時には分からないので、鳴る瞬間に app 側が位置で判定して出す・出さないを決める。
 */
object TrainReminderPlanner {
    /** 通知 ID / requestCode の起点。通勤通知（100 番台〜）と重ならないようにする。 */
    const val ID_BASE = 5_000

    /** 1 日に対象にする電車の上限。宝木発は 1 日 20 本程度。 */
    const val MAX_TRAINS = 30

    const val STAGE_STRIDE = 10

    /** 取り得るすべての requestCode（1 日分）。予約の全解除に使う。 */
    val ALL_REQUEST_CODES: List<Int> =
        (0 until MAX_TRAINS).flatMap { i -> ReminderStage.entries.map { s -> requestCodeOf(i, s) } }

    fun notificationIdOf(trainIndex: Int): Int = ID_BASE + trainIndex

    fun requestCodeOf(
        trainIndex: Int,
        stage: ReminderStage,
    ): Int = ID_BASE + trainIndex * STAGE_STRIDE + stage.ordinal

    /**
     * @param notBefore この時刻より前に鳴るはずだったものは捨てる（当日の再計算用）。
     */
    fun plan(
        date: LocalDate,
        jr: JrTimetable,
        settings: TrainReminderSettings,
        notBefore: LocalDateTime? = null,
        legId: String = JrLegIds.HOUGI_TO_TOTTORI,
    ): List<TrainReminder> {
        if (!settings.enabled) return emptyList()
        val trains =
            jr
                .servicesOn(date, legId)
                .filter { !it.departure.isBefore(settings.windowStart) }
                .take(MAX_TRAINS)
        return trains.flatMapIndexed { index, train ->
            val departureAt = date.atTime(train.departure)
            ReminderStage.entries.mapNotNull { stage ->
                val fireAt = departureAt.minus(stage.before)
                if (notBefore != null && fireAt.isBefore(notBefore)) return@mapNotNull null
                TrainReminder(
                    notificationId = notificationIdOf(index),
                    requestCode = requestCodeOf(index, stage),
                    stage = stage,
                    train = train,
                    departureAt = departureAt,
                    fireAt = fireAt,
                )
            }
        }
    }
}
