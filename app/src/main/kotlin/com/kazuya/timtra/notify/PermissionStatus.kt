package com.kazuya.timtra.notify

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 通知が時間どおりに届くために必要な 3 つの状態（CLAUDE.md 8 注意点）。
 * 欠けているものがあればホーム画面で案内する。
 */
data class PermissionStatus(
    val notificationsAllowed: Boolean,
    val exactAlarmAllowed: Boolean,
    val batteryOptimizationIgnored: Boolean,
) {
    val allGranted: Boolean get() = notificationsAllowed && exactAlarmAllowed && batteryOptimizationIgnored

    companion object {
        fun check(context: Context): PermissionStatus {
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val powerManager = context.getSystemService(PowerManager::class.java)
            return PermissionStatus(
                notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
                exactAlarmAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms(),
                batteryOptimizationIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName),
            )
        }

        /** Android 13 以降で POST_NOTIFICATIONS の実行時許可が必要か。 */
        val needsRuntimeNotificationPermission: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        /** 正確なアラームの許可画面（Android 12+）。 */
        fun exactAlarmSettingsIntent(context: Context): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))
            } else {
                null
            }

        /** バッテリー最適化の除外を依頼するダイアログ。 */
        fun batteryOptimizationIntent(context: Context): Intent =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))

        /** アプリの通知設定画面（実行時許可を拒否したあとの導線）。 */
        fun notificationSettingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")
    }
}
