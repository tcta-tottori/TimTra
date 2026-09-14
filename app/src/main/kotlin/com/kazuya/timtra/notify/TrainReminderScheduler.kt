package com.kazuya.timtra.notify

import android.content.Context
import com.kazuya.timtra.R
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places
import com.kazuya.timtra.core.notify.ReminderStage
import com.kazuya.timtra.core.notify.TrainReminder
import com.kazuya.timtra.core.notify.TrainReminderPlanner
import com.kazuya.timtra.data.di.AppClock
import com.kazuya.timtra.data.repository.AppSettings
import com.kazuya.timtra.data.repository.JrTimetableRepository
import com.kazuya.timtra.data.repository.SettingsRepository
import com.kazuya.timtra.location.LocationFix
import com.kazuya.timtra.location.LocationProvider
import com.kazuya.timtra.ui.common.hhmm
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 勤務先（気高電機）にいるときの「次の電車まであと 30 / 20 / 10 分」リマインダー。
 *
 * - 予約は通勤通知と同じく前夜（と再計算時）に AlarmManager へ入れる。常駐はしない。
 * - 鳴る瞬間に [shouldShowNow] で位置を 1 回だけ確認し、**確かに** 勤務先から離れていれば出さず、
 *   その日の残りを取り消す（「気高電機から離れた時点でその日の通知機能はオフ」）。
 * - 位置が取れない・古い位置しか無いときは **通知を出す**。バックグラウンドの測位は
 *   「常に許可」やドーズの影響で失敗しやすく、黙って鳴らさないと勤務先にいても通知が来なくなるため。
 * - 勤務先の位置は設定で登録した値、未登録なら [Places.WORKPLACE_DEFAULT]。
 */
@Singleton
class TrainReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val jr: JrTimetableRepository,
        private val settings: SettingsRepository,
        private val alarms: AlarmScheduler,
        private val location: LocationProvider,
        private val clock: AppClock,
    ) {
        /** 今日の残りと明日の分を予約し直す。[NotificationScheduler.replan] から呼ばれる。 */
        suspend fun replan(now: LocalDateTime = clock.now()): List<TrainReminder> {
            cancelAll()
            val appSettings = settings.current()
            if (!appSettings.notificationsEnabled || !appSettings.trainReminder.enabled) return emptyList()
            val today = now.toLocalDate()
            val days =
                listOf(
                    today to if (appSettings.isTrainReminderOff(today)) emptyList() else plannerFor(today, appSettings, now),
                    today.plusDays(1) to plannerFor(today.plusDays(1), appSettings, null),
                )
            val planned =
                days.flatMapIndexed { dayIndex, (date, reminders) ->
                    reminders.map { r ->
                        PlannedAlarm(
                            requestCode = r.requestCode + dayIndex * DAY_BLOCK,
                            fireAt = r.fireAt,
                            content = content(r, date, dayIndex),
                        )
                    }
                }
            alarms.schedule(planned)
            return days.flatMap { it.second }
        }

        /** 今日・明日の予約をすべて取り消す。 */
        fun cancelAll() {
            alarms.cancel(allRequestCodes())
        }

        private suspend fun plannerFor(
            date: LocalDate,
            appSettings: AppSettings,
            notBefore: LocalDateTime?,
        ): List<TrainReminder> = TrainReminderPlanner.plan(date, jr.timetable(), appSettings.trainReminder, notBefore = notBefore)

        /**
         * 鳴る瞬間の判定。出すなら true。
         *
         * 「勤務先から離れた」と断定できたときだけ止める。バックグラウンドの測位は
         * 「常に許可」が無い・ドーズ中・屋内などで簡単に失敗し、古い位置（今朝の自宅など）が
         * 返ってくることもある。それを「離れた」と解釈すると、勤務先にいるのに通知が来ないうえ
         * その日の残りまで取り消されてしまうので、確信が持てないときは出す側に倒す。
         */
        suspend fun shouldShowNow(date: LocalDate): Boolean {
            val appSettings = settings.current()
            if (!appSettings.notificationsEnabled || !appSettings.trainReminder.enabled) return false
            if (appSettings.isTrainReminderOff(date)) return false
            val fix = location.currentFix(maxCacheMillis = RECEIVER_CACHE_MILLIS, timeoutMillis = RECEIVER_TIMEOUT_MILLIS)
            // 位置が取れない / 古い位置しか無い → 判断できないので出す
            if (fix == null || fix.isStale) return true
            if (isAtWorkplace(fix, appSettings)) return true
            markLeft(date)
            return false
        }

        /**
         * ホーム画面が前景で位置を見たときの副作用。対象時間帯に勤務先から離れていれば、その日を止める。
         * 鳴る瞬間を待たずに止められるので、帰宅途中に鳴ることが減る。
         */
        suspend fun onLocationObserved(
            fix: LocationFix?,
            now: LocalDateTime,
        ) {
            // 古い位置で「離れた」と誤判定するとその日の通知が全部消えるので、新しい位置のときだけ見る
            if (fix == null || fix.isStale) return
            val appSettings = settings.current()
            if (!appSettings.trainReminder.enabled || appSettings.isTrainReminderOff(now.toLocalDate())) return
            if (now.toLocalTime().isBefore(appSettings.trainReminder.windowStart)) return
            if (!isAtWorkplace(fix, appSettings)) markLeft(now.toLocalDate())
        }

        /** その日のリマインダーを止める（勤務先を離れた）。今日分の予約も取り消す。 */
        suspend fun markLeft(date: LocalDate) {
            settings.setTrainReminderOffDate(date)
            if (date == clock.now().toLocalDate()) alarms.cancel(TrainReminderPlanner.ALL_REQUEST_CODES)
        }

        /** 「再開」。止めた記録を消して予約し直す。 */
        suspend fun resume() {
            settings.setTrainReminderOffDate(null)
            replan()
        }

        /**
         * 勤務先にいるとみなせるか。測位の誤差半径を差し引いてから判定するので、
         * 基地局測位のような粗い位置で「離れた」と誤判定しにくい。
         */
        fun isAtWorkplace(
            fix: LocationFix,
            appSettings: AppSettings,
        ): Boolean {
            val distance = fix.point.distanceMetersTo(workplaceOf(appSettings))
            return distance - fix.accuracyMeters <= WORKPLACE_RADIUS_METERS
        }

        private fun content(
            r: TrainReminder,
            date: LocalDate,
            dayIndex: Int,
        ): NotificationContent {
            val minutes = r.stage.before.toMinutes()
            val text = context.getString(R.string.reminder_text, r.train.departure.hhmm(), minutes)
            val hint =
                context.getString(
                    when (r.stage) {
                        ReminderStage.WALK -> R.string.reminder_hint_walk
                        ReminderStage.FAST_WALK -> R.string.reminder_hint_fast_walk
                        ReminderStage.DASH -> R.string.reminder_hint_dash
                    },
                )
            return NotificationContent(
                id = r.notificationId + dayIndex * DAY_BLOCK,
                title = context.getString(R.string.reminder_title),
                text = text,
                bigText = "$text\n$hint",
                iconRes = iconOf(r.stage),
                channelId = NotificationChannels.TRAIN_REMINDER,
                requiresWorkplace = true,
                reminderDate = date.toString(),
            )
        }

        private fun allRequestCodes(): List<Int> =
            (0 until DAYS).flatMap { d -> TrainReminderPlanner.ALL_REQUEST_CODES.map { it + d * DAY_BLOCK } }

        companion object {
            /**
             * この距離以内なら「勤務先にいる」。測位の誤差半径は別途差し引くので、
             * ここは勤務先の敷地と最寄りの行動範囲をゆるく覆う値でよい。
             * 自宅（南吉成）も鳥取駅も 15 km 以上離れているので、広めに取っても取り違えない。
             */
            const val WORKPLACE_RADIUS_METERS = 1_500.0
            private const val DAYS = 2
            private const val DAY_BLOCK = 1_000
            private const val RECEIVER_CACHE_MILLIS = 2 * 60_000L
            private const val RECEIVER_TIMEOUT_MILLIS = 6_000L

            fun workplaceOf(settings: AppSettings): GeoPoint = settings.workplace ?: Places.WORKPLACE_DEFAULT

            fun iconOf(stage: ReminderStage): Int =
                when (stage) {
                    ReminderStage.WALK -> R.drawable.ic_walk
                    ReminderStage.FAST_WALK -> R.drawable.ic_walk_fast
                    ReminderStage.DASH -> R.drawable.ic_run
                }
        }
    }
