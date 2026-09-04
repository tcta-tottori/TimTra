package com.kazuya.timtra.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kazuya.timtra.MainActivity
import com.kazuya.timtra.R

/** AlarmManager から起こされ、予約時に確定した文面をそのまま表示する。 */
class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_SHOW) return
        val content = readContent(intent) ?: return
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
                .Builder(context, NotificationChannels.COMMUTE)
                .setSmallIcon(R.drawable.ic_notification)
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

        fun putContent(
            intent: Intent,
            content: NotificationContent,
        ) {
            intent.putExtra(EXTRA_ID, content.id)
            intent.putExtra(EXTRA_TITLE, content.title)
            intent.putExtra(EXTRA_TEXT, content.text)
            intent.putExtra(EXTRA_BIG_TEXT, content.bigText)
        }

        fun readContent(intent: Intent): NotificationContent? {
            val title = intent.getStringExtra(EXTRA_TITLE) ?: return null
            val text = intent.getStringExtra(EXTRA_TEXT) ?: return null
            return NotificationContent(
                id = intent.getIntExtra(EXTRA_ID, 0),
                title = title,
                text = text,
                bigText = intent.getStringExtra(EXTRA_BIG_TEXT) ?: text,
            )
        }
    }
}
