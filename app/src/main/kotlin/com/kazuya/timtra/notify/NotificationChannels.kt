package com.kazuya.timtra.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.kazuya.timtra.R

object NotificationChannels {
    const val COMMUTE = "commute"

    /** 勤務先にいるときの「次の電車まで」リマインダー。通勤通知とは別に音・表示を調整できるようにする。 */
    const val TRAIN_REMINDER = "train_reminder"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                COMMUTE,
                context.getString(R.string.notify_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notify_channel_description)
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                TRAIN_REMINDER,
                context.getString(R.string.notify_channel_reminder_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notify_channel_reminder_description)
            },
        )
    }
}
