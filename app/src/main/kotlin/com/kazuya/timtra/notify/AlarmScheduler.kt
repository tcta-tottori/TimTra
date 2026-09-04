package com.kazuya.timtra.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kazuya.timtra.core.TimTraConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** 予約する 1 件のアラーム。requestCode は日をまたいで衝突しないように呼び出し側が決める。 */
data class PlannedAlarm(
    val requestCode: Int,
    val fireAt: LocalDateTime,
    val content: NotificationContent,
)

/**
 * AlarmManager.setExactAndAllowWhileIdle() で通知を予約する（CLAUDE.md 8）。
 * 正確なアラームの許可が無い場合は不正確な予約に落とす（UI で許可を促す）。
 */
@Singleton
class AlarmScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

        val canScheduleExact: Boolean
            get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

        fun schedule(alarms: Collection<PlannedAlarm>) {
            alarms.forEach { alarm ->
                val millis =
                    alarm.fireAt
                        .atZone(TimTraConstants.ZONE)
                        .toInstant()
                        .toEpochMilli()
                val pending = pendingIntent(alarm.requestCode, alarm.content)
                if (canScheduleExact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
                }
            }
        }

        fun cancel(requestCodes: Collection<Int>) {
            requestCodes.forEach { code ->
                alarmManager.cancel(pendingIntent(code, null))
            }
        }

        private fun pendingIntent(
            requestCode: Int,
            content: NotificationContent?,
        ): PendingIntent {
            val intent =
                Intent(context, NotificationAlarmReceiver::class.java).apply {
                    action = NotificationAlarmReceiver.ACTION_SHOW
                    // requestCode ごとに別の PendingIntent にするため data も変える
                    data = android.net.Uri.parse("timtra://notify/$requestCode")
                    content?.let { NotificationAlarmReceiver.putContent(this, it) }
                }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
