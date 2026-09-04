package com.kazuya.timtra.notify

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kazuya.timtra.core.notify.DailyNotificationPlan
import com.kazuya.timtra.core.notify.DailyNotificationPlanner
import com.kazuya.timtra.core.notify.PlannedNotification
import com.kazuya.timtra.data.di.AppClock
import com.kazuya.timtra.data.repository.BusTimetableRepository
import com.kazuya.timtra.data.repository.JourneyRepository
import com.kazuya.timtra.data.repository.JrTimetableRepository
import com.kazuya.timtra.data.repository.NotificationPlanSummary
import com.kazuya.timtra.data.repository.SettingsRepository
import com.kazuya.timtra.sync.WearSyncPublisher
import com.kazuya.timtra.widget.CommuteWidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知予約の入口。
 * - 毎晩 23:00 頃の WorkManager ジョブ（[NightlyPlanWorker]）が翌日分を計算して AlarmManager に予約する。
 * - 設定変更・アプリ起動・再起動時にも同じ計算をやり直す（今日の残り + 明日）。
 * 常駐サービスやポーリングは使わない（CLAUDE.md 3-4, 8）。
 */
@Singleton
class NotificationScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val journeys: JourneyRepository,
        private val bus: BusTimetableRepository,
        private val jr: JrTimetableRepository,
        private val settings: SettingsRepository,
        private val alarms: AlarmScheduler,
        private val wearSync: WearSyncPublisher,
        private val clock: AppClock,
    ) {
        /** 今日の残りと明日の通知を計算し直して予約する。計算結果の要約を保存して UI に見せる。 */
        suspend fun replan(now: LocalDateTime = clock.now()): List<DailyNotificationPlan> {
            val appSettings = settings.current()
            val planner = DailyNotificationPlanner(journeys.planner(), bus.timetable(), jr.timetable())
            val today = now.toLocalDate()
            val plans =
                listOf(
                    planner.plan(today, appSettings.notificationTiming, appSettings.notificationsEnabled, appSettings.dayOff, now = now),
                    planner.plan(today.plusDays(1), appSettings.notificationTiming, appSettings.notificationsEnabled, appSettings.dayOff),
                )

            alarms.cancel(allRequestCodes())
            val scheduled =
                plans.flatMapIndexed { dayIndex, plan ->
                    plan.notifications.map { n ->
                        PlannedAlarm(
                            requestCode = requestCode(n, dayIndex),
                            fireAt = n.fireAt,
                            content = context.notificationContent(n, appSettings.notificationTiming),
                        )
                    }
                }
            alarms.schedule(scheduled)

            settings.savePlanSummary(
                NotificationPlanSummary(
                    computedAt = now,
                    today = plans[0].date,
                    todayCount = plans[0].notifications.size,
                    todayReason = plans[0].suppressReason?.name,
                    tomorrow = plans[1].date,
                    tomorrowCount = plans[1].notifications.size,
                    tomorrowReason = plans[1].suppressReason?.name,
                    exactAlarms = alarms.canScheduleExact,
                ),
            )
            // 設定変更・起動・夜間ジョブのたびに時計へも設定を配り、ウィジェットも描き直す
            wearSync.publishSettings(appSettings)
            CommuteWidgetUpdater.updateAll(context)
            return plans
        }

        /** 予約をすべて取り消す（通知 OFF 時など）。 */
        fun cancelAll() {
            alarms.cancel(allRequestCodes())
        }

        /** 夜間ジョブの登録（何度呼んでも 1 つ）と、今すぐの再計算。アプリ起動時に呼ぶ。 */
        fun ensureScheduled() {
            enqueueNightly(context, clock.now())
            enqueueReplan(context)
        }

        /** 設定変更後などに、バックグラウンドで再計算する。 */
        fun requestReplan() = enqueueReplan(context)

        private fun requestCode(
            n: PlannedNotification,
            dayIndex: Int,
        ): Int = n.id + dayIndex * DAY_BLOCK

        private fun allRequestCodes(): List<Int> = (0 until DAYS).flatMap { d -> PlannedNotification.ALL_IDS.map { it + d * DAY_BLOCK } }

        companion object {
            private const val DAYS = 2
            private const val DAY_BLOCK = 1000
            private const val NIGHTLY_WORK = "nightly_plan"
            private const val REPLAN_WORK = "replan_now"
            private val NIGHTLY_AT: LocalTime = LocalTime.of(23, 0)

            /** 毎晩 23:00 頃に翌日分を計算する周期ジョブ。 */
            fun enqueueNightly(
                context: Context,
                now: LocalDateTime,
            ) {
                var next = now.toLocalDate().atTime(NIGHTLY_AT)
                if (!next.isAfter(now)) next = next.plusDays(1)
                val delay = Duration.between(now, next)
                val request =
                    PeriodicWorkRequestBuilder<NightlyPlanWorker>(1, TimeUnit.DAYS)
                        .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                        .build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(NIGHTLY_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
            }

            /** 今すぐ 1 回だけ再計算する（BroadcastReceiver からも呼べるよう static）。 */
            fun enqueueReplan(context: Context) {
                val request = OneTimeWorkRequestBuilder<NightlyPlanWorker>().build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork(REPLAN_WORK, ExistingWorkPolicy.REPLACE, request)
            }
        }
    }
