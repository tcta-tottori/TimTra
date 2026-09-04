package com.kazuya.timtra.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kazuya.timtra.core.journey.CommuteSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "timtra_settings")

/** アプリ全体の設定（CLAUDE.md 7-3）。core の [CommuteSettings] と通知・休み設定をまとめて持つ。 */
data class AppSettings(
    val commute: CommuteSettings = CommuteSettings(),
    /** 通知の ON/OFF（手順 4 で使用）。 */
    val notificationsEnabled: Boolean = true,
    /** 「今日は休み」を押した日。翌日になれば自動的に解除される。 */
    val dayOff: LocalDate? = null,
) {
    fun isDayOff(today: LocalDate): Boolean = dayOff == today
}

@Singleton
class SettingsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val store get() = context.settingsStore

        val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

        suspend fun current(): AppSettings = settings.first()

        suspend fun updateCommute(transform: (CommuteSettings) -> CommuteSettings) {
            store.edit { prefs ->
                val next = transform(prefs.toSettings().commute)
                prefs[Keys.WALK_HOME_TO_STOP] = next.walkHomeToStop.toMinutes().toInt()
                prefs[Keys.TRANSFER_BUS_TO_JR] = next.transferBusToJr.toMinutes().toInt()
                prefs[Keys.PREP_BUFFER] = next.prepBuffer.toMinutes().toInt()
                prefs[Keys.WALK_STATION_TO_WORK] = next.walkStationToWork.toMinutes().toInt()
                prefs[Keys.MIN_TRANSFER] = next.minTransfer.toMinutes().toInt()
                prefs[Keys.COMFORTABLE_TRANSFER] = next.comfortableTransfer.toMinutes().toInt()
                prefs[Keys.OUTBOUND_WINDOW_START] = next.outboundWindowStart.toSecondOfDay()
                prefs[Keys.INBOUND_WINDOW_START] = next.inboundWindowStart.toSecondOfDay()
                prefs[Keys.EARLIEST_LEAVE_HOME] = next.earliestLeaveHome.toSecondOfDay()
                prefs[Keys.WORK_ENDS_AT] = next.workEndsAt.toSecondOfDay()
            }
        }

        suspend fun setNotificationsEnabled(enabled: Boolean) {
            store.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
        }

        /** 「今日は休み」。[date] を渡すとその日を休みに、null で解除。 */
        suspend fun setDayOff(date: LocalDate?) {
            store.edit { prefs ->
                if (date == null) prefs.remove(Keys.DAY_OFF) else prefs[Keys.DAY_OFF] = date.toString()
            }
        }

        private fun Preferences.toSettings(): AppSettings {
            val d = CommuteSettings()

            fun minutes(
                key: Preferences.Key<Int>,
                default: Duration,
            ) = this[key]?.let { Duration.ofMinutes(it.toLong()) } ?: default

            fun time(
                key: Preferences.Key<Int>,
                default: LocalTime,
            ) = this[key]?.let { LocalTime.ofSecondOfDay(it.toLong()) } ?: default
            val commute =
                CommuteSettings(
                    walkHomeToStop = minutes(Keys.WALK_HOME_TO_STOP, d.walkHomeToStop),
                    transferBusToJr = minutes(Keys.TRANSFER_BUS_TO_JR, d.transferBusToJr),
                    prepBuffer = minutes(Keys.PREP_BUFFER, d.prepBuffer),
                    walkStationToWork = minutes(Keys.WALK_STATION_TO_WORK, d.walkStationToWork),
                    minTransfer = minutes(Keys.MIN_TRANSFER, d.minTransfer),
                    comfortableTransfer = minutes(Keys.COMFORTABLE_TRANSFER, d.comfortableTransfer),
                    outboundWindowStart = time(Keys.OUTBOUND_WINDOW_START, d.outboundWindowStart),
                    inboundWindowStart = time(Keys.INBOUND_WINDOW_START, d.inboundWindowStart),
                    earliestLeaveHome = time(Keys.EARLIEST_LEAVE_HOME, d.earliestLeaveHome),
                    workEndsAt = time(Keys.WORK_ENDS_AT, d.workEndsAt),
                )
            return AppSettings(
                commute = commute,
                notificationsEnabled = this[Keys.NOTIFICATIONS_ENABLED] ?: true,
                dayOff = this[Keys.DAY_OFF]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            )
        }

        private object Keys {
            val WALK_HOME_TO_STOP = intPreferencesKey("walk_home_to_stop_min")
            val TRANSFER_BUS_TO_JR = intPreferencesKey("transfer_bus_to_jr_min")
            val PREP_BUFFER = intPreferencesKey("prep_buffer_min")
            val WALK_STATION_TO_WORK = intPreferencesKey("walk_station_to_work_min")
            val MIN_TRANSFER = intPreferencesKey("min_transfer_min")
            val COMFORTABLE_TRANSFER = intPreferencesKey("comfortable_transfer_min")
            val OUTBOUND_WINDOW_START = intPreferencesKey("outbound_window_start_sec")
            val INBOUND_WINDOW_START = intPreferencesKey("inbound_window_start_sec")
            val EARLIEST_LEAVE_HOME = intPreferencesKey("earliest_leave_home_sec")
            val WORK_ENDS_AT = intPreferencesKey("work_ends_at_sec")
            val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
            val DAY_OFF = stringPreferencesKey("day_off_date")
        }
    }
