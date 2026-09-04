package com.kazuya.timtra.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.core.journey.CommuteSettings
import com.kazuya.timtra.data.repository.AppSettings
import com.kazuya.timtra.data.repository.SettingsRepository
import com.kazuya.timtra.di.AppClock
import dagger.hilt.android.lifecycle.HiltViewModel
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
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: SettingsRepository,
        private val clock: AppClock,
    ) : ViewModel() {
        val settings: StateFlow<AppSettings?> =
            repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        val isDayOffToday: Boolean
            get() = settings.value?.isDayOff(clock.now().toLocalDate()) == true

        fun adjust(
            field: DurationField,
            deltaMinutes: Long,
        ) {
            viewModelScope.launch {
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
            viewModelScope.launch {
                repository.updateCommute { s ->
                    fun step(current: LocalTime) = current.plusMinutes(deltaMinutes)
                    when (field) {
                        TimeField.OUTBOUND_WINDOW_START -> s.copy(outboundWindowStart = step(s.outboundWindowStart))
                        TimeField.INBOUND_WINDOW_START -> s.copy(inboundWindowStart = step(s.inboundWindowStart))
                        TimeField.EARLIEST_LEAVE_HOME -> s.copy(earliestLeaveHome = step(s.earliestLeaveHome))
                        TimeField.WORK_ENDS_AT -> s.copy(workEndsAt = step(s.workEndsAt))
                    }
                }
            }
        }

        fun setNotificationsEnabled(enabled: Boolean) {
            viewModelScope.launch { repository.setNotificationsEnabled(enabled) }
        }

        fun setDayOffToday(dayOff: Boolean) {
            viewModelScope.launch { repository.setDayOff(if (dayOff) clock.now().toLocalDate() else null) }
        }

        fun resetToDefaults() {
            viewModelScope.launch { repository.updateCommute { CommuteSettings() } }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            val MAX_DURATION: Duration = Duration.ofMinutes(120)
        }
    }
