package com.kazuya.timtra.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.kazuya.timtra.R

object NotificationChannels {
    const val COMMUTE = "commute"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel =
            NotificationChannel(
                COMMUTE,
                context.getString(R.string.notify_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notify_channel_description)
                enableVibration(true)
            }
        manager.createNotificationChannel(channel)
    }
}
