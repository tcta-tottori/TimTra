package com.kazuya.timtra.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kazuya.timtra.MainActivity
import com.kazuya.timtra.R
import com.kazuya.timtra.widget.CommuteWidgetUpdater
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * AlarmManager から起こされ、予約時に確定した文面をそのまま表示する。
 * 勤務先リマインダーだけは、鳴る瞬間に位置を 1 回確認してから出す（離れていればその日は止める）。
 */
class NotificationAlarmReceiver : BroadcastReceiver() {
    /** BroadcastReceiver は Hilt の @AndroidEntryPoint が使いにくい（super.onReceive が抽象）ので EntryPoint で取る。 */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun trainReminders(): TrainReminderScheduler
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_SHOW) return
        val content = readContent(intent) ?: return
        val trainReminders =
            EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java).trainReminders()
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val show =
                    if (content.requiresWorkplace) {
                        val date = content.reminderDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        date != null && trainReminders.shouldShowNow(date)
                    } else {
                        true
                    }
                if (show) show(context, content)
                // 通知の時刻は状況が変わる節目なので、ウィジェットもここで描き直す（常駐せずに更新する機会）
                CommuteWidgetUpdater.updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    private fun show(
        context: Context,
        content: NotificationContent,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val tap =
            PendingIntent.getActivity(
                context,
                content.id,
                Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, content.channelId)
                .setSmallIcon(content.iconRes)
                .setContentTitle(content.title)
                .setContentText(content.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content.bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(tap)
                .build()
        // POST_NOTIFICATIONS 未許可なら areNotificationsEnabled() が false になり上で抜けている
        @Suppress("MissingPermission")
        manager.notify(content.id, notification)
    }

    companion object {
        const val ACTION_SHOW = "com.kazuya.timtra.action.SHOW_NOTIFICATION"
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_BIG_TEXT = "bigText"
        private const val EXTRA_ICON = "icon"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_REQUIRES_WORKPLACE = "requiresWorkplace"
        private const val EXTRA_REMINDER_DATE = "reminderDate"

        fun putContent(
            intent: Intent,
            content: NotificationContent,
        ) {
            intent.putExtra(EXTRA_ID, content.id)
            intent.putExtra(EXTRA_TITLE, content.title)
            intent.putExtra(EXTRA_TEXT, content.text)
            intent.putExtra(EXTRA_BIG_TEXT, content.bigText)
            intent.putExtra(EXTRA_ICON, content.iconRes)
            intent.putExtra(EXTRA_CHANNEL, content.channelId)
            intent.putExtra(EXTRA_REQUIRES_WORKPLACE, content.requiresWorkplace)
            content.reminderDate?.let { intent.putExtra(EXTRA_REMINDER_DATE, it) }
        }

        fun readContent(intent: Intent): NotificationContent? {
            val title = intent.getStringExtra(EXTRA_TITLE) ?: return null
            val text = intent.getStringExtra(EXTRA_TEXT) ?: return null
            return NotificationContent(
                id = intent.getIntExtra(EXTRA_ID, 0),
                title = title,
                text = text,
                bigText = intent.getStringExtra(EXTRA_BIG_TEXT) ?: text,
                iconRes = intent.getIntExtra(EXTRA_ICON, R.drawable.ic_notification),
                channelId = intent.getStringExtra(EXTRA_CHANNEL) ?: NotificationChannels.COMMUTE,
                requiresWorkplace = intent.getBooleanExtra(EXTRA_REQUIRES_WORKPLACE, false),
                reminderDate = intent.getStringExtra(EXTRA_REMINDER_DATE),
            )
        }
    }
}
