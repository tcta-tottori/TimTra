package com.kazuya.timtra.ui.settings

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.R
import com.kazuya.timtra.core.journey.CommuteSettings
import com.kazuya.timtra.core.notify.NotificationTiming
import com.kazuya.timtra.core.notify.TrainReminderSettings
import com.kazuya.timtra.data.di.AppClock
import com.kazuya.timtra.data.repository.AppSettings
import com.kazuya.timtra.data.repository.NotificationPlanSummary
import com.kazuya.timtra.data.repository.SettingsRepository
import com.kazuya.timtra.location.LocationProvider
import com.kazuya.timtra.notify.NotificationChannels
import com.kazuya.timtra.notify.NotificationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

/** 分単位で調整する設定項目。 */
enum class DurationField {
    WALK_HOME_TO_STOP,
    TRANSFER_BUS_TO_JR,
    PREP_BUFFER,
    WALK_STATION_TO_WORK,
    MIN_TRANSFER,
    COMFORTABLE_TRANSFER,
}

/** 時刻で調整する設定項目。 */
enum class TimeField {
    OUTBOUND_WINDOW_START,
    INBOUND_WINDOW_START,
    EARLIEST_LEAVE_HOME,
    WORK_ENDS_AT,
    LEAVE_HOME_DISPLAY_START,
    LEAVE_HOME_DISPLAY_END,
    LEAVE_WORK_DISPLAY_START,
}

/** 通知タイミング（分）。 */
enum class NotifyField {
    BEFORE_LEAVE,
    BEFORE_FIRST_LEG,
    BEFORE_TRANSFER,
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: SettingsRepository,
        private val scheduler: NotificationScheduler,
        private val location: LocationProvider,
        private val clock: AppClock,
    ) : ViewModel() {
        /** 「現在地を勤務先に登録」の結果メッセージ（表示したら [consumeMessage] で消す）。 */
        val message = MutableStateFlow<Int?>(null)

        val hasLocationPermission: Boolean get() = location.hasPermission
        val hasBackgroundLocationPermission: Boolean get() = location.hasBackgroundPermission

        /** 権限画面から戻ったときに再評価させるためのカウンタ。 */
        val permissionVersion = MutableStateFlow(0)

        val settings: StateFlow<AppSettings?> =
            repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        val planSummary: StateFlow<NotificationPlanSummary> =
            repository.planSummary.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NotificationPlanSummary())

        val isDayOffToday: Boolean
            get() = settings.value?.isDayOff(clock.now().toLocalDate()) == true

        val isTrainReminderOffToday: Boolean
            get() = settings.value?.isTrainReminderOff(clock.now().toLocalDate()) == true

        fun setTrainReminderEnabled(enabled: Boolean) {
            update { repository.updateTrainReminder { it.copy(enabled = enabled) } }
        }

        fun adjustReminderWindowStart(deltaMinutes: Long) {
            update { repository.updateTrainReminder { it.copy(windowStart = it.windowStart.plusMinutes(deltaMinutes)) } }
        }

        /** 現在地を 1 回取り、勤務先として保存する。 */
        fun registerWorkplaceHere() {
            viewModelScope.launch {
                val here = location.current(maxCacheMillis = 0)
                if (here == null) {
                    message.value = R.string.settings_workplace_failed
                } else {
                    repository.setWorkplace(here)
                    message.value = R.string.settings_workplace_registered_toast
                    scheduler.requestReplan()
                }
            }
        }

        fun clearWorkplace() {
            update { repository.setWorkplace(null) }
        }

        fun consumeMessage() {
            message.value = null
        }

        fun permissionsChanged() {
            permissionVersion.value += 1
        }

        fun adjust(
            field: DurationField,
            deltaMinutes: Long,
        ) {
            update {
                repository.updateCommute { s ->
                    fun step(current: Duration) = current.plusMinutes(deltaMinutes).coerceIn(Duration.ZERO, MAX_DURATION)
                    when (field) {
                        DurationField.WALK_HOME_TO_STOP -> s.copy(walkHomeToStop = step(s.walkHomeToStop))
                        DurationField.TRANSFER_BUS_TO_JR -> s.copy(transferBusToJr = step(s.transferBusToJr))
                        DurationField.PREP_BUFFER -> s.copy(prepBuffer = step(s.prepBuffer))
                        DurationField.WALK_STATION_TO_WORK -> s.copy(walkStationToWork = step(s.walkStationToWork))
                        DurationField.MIN_TRANSFER -> {
                            val min = step(s.minTransfer)
                            // comfortableTransfer は minTransfer 以上でなければならない
                            s.copy(minTransfer = min, comfortableTransfer = maxOf(min, s.comfortableTransfer))
                        }
                        DurationField.COMFORTABLE_TRANSFER ->
                            s.copy(comfortableTransfer = maxOf(s.minTransfer, step(s.comfortableTransfer)))
                    }
                }
            }
        }

        fun adjust(
            field: TimeField,
            deltaMinutes: Long,
        ) {
            update {
                repository.updateCommute { s ->
                    fun step(current: LocalTime) = current.plusMinutes(deltaMinutes)
                    when (field) {
                        TimeField.OUTBOUND_WINDOW_START -> s.copy(outboundWindowStart = step(s.outboundWindowStart))
                        TimeField.INBOUND_WINDOW_START -> s.copy(inboundWindowStart = step(s.inboundWindowStart))
                        TimeField.EARLIEST_LEAVE_HOME -> s.copy(earliestLeaveHome = step(s.earliestLeaveHome))
                        TimeField.WORK_ENDS_AT -> s.copy(workEndsAt = step(s.workEndsAt))
                        TimeField.LEAVE_HOME_DISPLAY_START -> s.copy(leaveHomeDisplayStart = step(s.leaveHomeDisplayStart))
                        TimeField.LEAVE_HOME_DISPLAY_END -> s.copy(leaveHomeDisplayEnd = step(s.leaveHomeDisplayEnd))
                        TimeField.LEAVE_WORK_DISPLAY_START -> s.copy(leaveWorkDisplayStart = step(s.leaveWorkDisplayStart))
                    }
                }
            }
        }

        fun adjust(
            field: NotifyField,
            deltaMinutes: Long,
        ) {
            update {
                repository.updateNotificationTiming { t ->
                    fun step(current: Duration) = current.plusMinutes(deltaMinutes).coerceIn(Duration.ZERO, MAX_NOTIFY_LEAD)
                    when (field) {
                        NotifyField.BEFORE_LEAVE -> t.copy(beforeLeave = step(t.beforeLeave))
                        NotifyField.BEFORE_FIRST_LEG -> t.copy(beforeFirstLegDeparture = step(t.beforeFirstLegDeparture))
                        NotifyField.BEFORE_TRANSFER -> t.copy(beforeTransferArrival = step(t.beforeTransferArrival))
                    }
                }
            }
        }

        fun setNotificationsEnabled(enabled: Boolean) {
            update { repository.setNotificationsEnabled(enabled) }
        }

        fun setDayOffToday(dayOff: Boolean) {
            update { repository.setDayOff(if (dayOff) clock.now().toLocalDate() else null) }
        }

        fun resetToDefaults() {
            update {
                repository.updateCommute { CommuteSettings() }
                repository.updateNotificationTiming { NotificationTiming() }
                repository.updateTrainReminder { TrainReminderSettings() }
            }
        }

        fun replanNow() {
            scheduler.requestReplan()
        }

        /** 通知チャンネルと権限の確認用。 */
        fun sendTestNotification() {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            val notification =
                NotificationCompat
                    .Builder(context, NotificationChannels.COMMUTE)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.notify_test_title))
                    .setContentText(context.getString(R.string.notify_test_text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
            @Suppress("MissingPermission")
            manager.notify(TEST_NOTIFICATION_ID, notification)
        }

        /** 設定を書き換えたら通知の予約も作り直す。 */
        private fun update(block: suspend () -> Unit) {
            viewModelScope.launch {
                block()
                scheduler.requestReplan()
            }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val TEST_NOTIFICATION_ID = 9_999
            val MAX_DURATION: Duration = Duration.ofMinutes(120)
            val MAX_NOTIFY_LEAD: Duration = Duration.ofMinutes(60)
        }
    }
