package com.kazuya.timtra.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kazuya.timtra.core.journey.CommuteSettings
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.notify.NotificationTiming
import com.kazuya.timtra.core.notify.TrainReminderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
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
    /** 通知タイミング（CLAUDE.md 8）。 */
    val notificationTiming: NotificationTiming = NotificationTiming(),
    /** 勤務先にいるときの「次の電車まで」リマインダー。 */
    val trainReminder: TrainReminderSettings = TrainReminderSettings(),
    /** 「現在地を勤務先に登録」で保存した位置。未登録なら null（core の Places.WORKPLACE_DEFAULT で代用）。 */
    val workplace: GeoPoint? = null,
    /** 「現在地を自宅に登録」で保存した位置。未登録なら null（core の Places.HOME_DEFAULT で代用）。 */
    val home: GeoPoint? = null,
    /** 勤務先を離れたのでリマインダーを止めた日。翌日になれば自動的に無効。 */
    val trainReminderOffDate: LocalDate? = null,
) {
    fun isDayOff(today: LocalDate): Boolean = dayOff == today

    fun isTrainReminderOff(today: LocalDate): Boolean = trainReminderOffDate == today
}

/** 直近の通知予約計算の要約。設定画面で「予約状況」として見せる。 */
data class NotificationPlanSummary(
    val computedAt: LocalDateTime? = null,
    val today: LocalDate? = null,
    val todayCount: Int = 0,
    /** core の SuppressReason.name。null なら抑止なし。 */
    val todayReason: String? = null,
    val tomorrow: LocalDate? = null,
    val tomorrowCount: Int = 0,
    val tomorrowReason: String? = null,
    /** 正確なアラームで予約できたか。 */
    val exactAlarms: Boolean = true,
)

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
                prefs[Keys.LEAVE_HOME_DISPLAY_START] = next.leaveHomeDisplayStart.toSecondOfDay()
                prefs[Keys.LEAVE_HOME_DISPLAY_END] = next.leaveHomeDisplayEnd.toSecondOfDay()
                prefs[Keys.LEAVE_WORK_DISPLAY_START] = next.leaveWorkDisplayStart.toSecondOfDay()
            }
        }

        suspend fun setNotificationsEnabled(enabled: Boolean) {
            store.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
        }

        suspend fun updateTrainReminder(transform: (TrainReminderSettings) -> TrainReminderSettings) {
            store.edit { prefs ->
                val next = transform(prefs.toSettings().trainReminder)
                prefs[Keys.TRAIN_REMINDER_ENABLED] = next.enabled
                prefs[Keys.TRAIN_REMINDER_WINDOW_START] = next.windowStart.toSecondOfDay()
            }
        }

        /** 勤務先の位置。null で登録解除。 */
        suspend fun setWorkplace(point: GeoPoint?) {
            store.edit { prefs ->
                if (point == null) {
                    prefs.remove(Keys.WORKPLACE_LAT)
                    prefs.remove(Keys.WORKPLACE_LON)
                } else {
                    prefs[Keys.WORKPLACE_LAT] = point.lat
                    prefs[Keys.WORKPLACE_LON] = point.lon
                }
            }
        }

        /** 自宅の位置。null で登録解除。 */
        suspend fun setHome(point: GeoPoint?) {
            store.edit { prefs ->
                if (point == null) {
                    prefs.remove(Keys.HOME_LAT)
                    prefs.remove(Keys.HOME_LON)
                } else {
                    prefs[Keys.HOME_LAT] = point.lat
                    prefs[Keys.HOME_LON] = point.lon
                }
            }
        }

        /** 勤務先を離れた日を記録する（その日のリマインダーを止める）。null で解除。 */
        suspend fun setTrainReminderOffDate(date: LocalDate?) {
            store.edit { prefs ->
                if (date == null) prefs.remove(Keys.TRAIN_REMINDER_OFF_DATE) else prefs[Keys.TRAIN_REMINDER_OFF_DATE] = date.toString()
            }
        }

        /** スマホから同期された設定で丸ごと置き換える（Wear 側）。予約状況の要約は触らない。 */
        suspend fun replaceAll(settings: AppSettings) {
            updateCommute { settings.commute }
            updateNotificationTiming { settings.notificationTiming }
            setNotificationsEnabled(settings.notificationsEnabled)
            setDayOff(settings.dayOff)
            store.edit { it[Keys.LAST_SYNCED_AT] = System.currentTimeMillis() }
        }

        /** スマホから最後に設定を受け取った時刻（epoch ミリ秒）。Wear の UI 用。 */
        val lastSyncedAtEpochMillis: Flow<Long?> = store.data.map { it[Keys.LAST_SYNCED_AT] }

        suspend fun updateNotificationTiming(transform: (NotificationTiming) -> NotificationTiming) {
            store.edit { prefs ->
                val next = transform(prefs.toSettings().notificationTiming)
                prefs[Keys.NOTIFY_BEFORE_LEAVE] = next.beforeLeave.toMinutes().toInt()
                prefs[Keys.NOTIFY_BEFORE_FIRST_LEG] = next.beforeFirstLegDeparture.toMinutes().toInt()
                prefs[Keys.NOTIFY_BEFORE_TRANSFER] = next.beforeTransferArrival.toMinutes().toInt()
            }
        }

        val planSummary: Flow<NotificationPlanSummary> =
            store.data.map { p ->
                NotificationPlanSummary(
                    computedAt = p[Keys.PLAN_COMPUTED_AT]?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
                    today = p[Keys.PLAN_TODAY]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    todayCount = p[Keys.PLAN_TODAY_COUNT] ?: 0,
                    todayReason = p[Keys.PLAN_TODAY_REASON],
                    tomorrow = p[Keys.PLAN_TOMORROW]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                    tomorrowCount = p[Keys.PLAN_TOMORROW_COUNT] ?: 0,
                    tomorrowReason = p[Keys.PLAN_TOMORROW_REASON],
                    exactAlarms = p[Keys.PLAN_EXACT] ?: true,
                )
            }

        suspend fun savePlanSummary(summary: NotificationPlanSummary) {
            store.edit { p ->
                fun put(
                    key: Preferences.Key<String>,
                    value: String?,
                ) {
                    if (value == null) p.remove(key) else p[key] = value
                }
                put(Keys.PLAN_COMPUTED_AT, summary.computedAt?.toString())
                put(Keys.PLAN_TODAY, summary.today?.toString())
                p[Keys.PLAN_TODAY_COUNT] = summary.todayCount
                put(Keys.PLAN_TODAY_REASON, summary.todayReason)
                put(Keys.PLAN_TOMORROW, summary.tomorrow?.toString())
                p[Keys.PLAN_TOMORROW_COUNT] = summary.tomorrowCount
                put(Keys.PLAN_TOMORROW_REASON, summary.tomorrowReason)
                p[Keys.PLAN_EXACT] = summary.exactAlarms
            }
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
                    leaveHomeDisplayStart = time(Keys.LEAVE_HOME_DISPLAY_START, d.leaveHomeDisplayStart),
                    leaveHomeDisplayEnd = time(Keys.LEAVE_HOME_DISPLAY_END, d.leaveHomeDisplayEnd),
                    leaveWorkDisplayStart = time(Keys.LEAVE_WORK_DISPLAY_START, d.leaveWorkDisplayStart),
                )
            val t = NotificationTiming()
            val r = TrainReminderSettings()
            val workplaceLat = this[Keys.WORKPLACE_LAT]
            val workplaceLon = this[Keys.WORKPLACE_LON]
            val homeLat = this[Keys.HOME_LAT]
            val homeLon = this[Keys.HOME_LON]
            return AppSettings(
                commute = commute,
                notificationsEnabled = this[Keys.NOTIFICATIONS_ENABLED] ?: true,
                dayOff = this[Keys.DAY_OFF]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                notificationTiming =
                    NotificationTiming(
                        beforeLeave = minutes(Keys.NOTIFY_BEFORE_LEAVE, t.beforeLeave),
                        beforeFirstLegDeparture = minutes(Keys.NOTIFY_BEFORE_FIRST_LEG, t.beforeFirstLegDeparture),
                        beforeTransferArrival = minutes(Keys.NOTIFY_BEFORE_TRANSFER, t.beforeTransferArrival),
                    ),
                trainReminder =
                    TrainReminderSettings(
                        enabled = this[Keys.TRAIN_REMINDER_ENABLED] ?: r.enabled,
                        windowStart = time(Keys.TRAIN_REMINDER_WINDOW_START, r.windowStart),
                    ),
                workplace = if (workplaceLat != null && workplaceLon != null) GeoPoint(workplaceLat, workplaceLon) else null,
                home = if (homeLat != null && homeLon != null) GeoPoint(homeLat, homeLon) else null,
                trainReminderOffDate = this[Keys.TRAIN_REMINDER_OFF_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
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
            val LEAVE_HOME_DISPLAY_START = intPreferencesKey("leave_home_display_start_sec")
            val LEAVE_HOME_DISPLAY_END = intPreferencesKey("leave_home_display_end_sec")
            val LEAVE_WORK_DISPLAY_START = intPreferencesKey("leave_work_display_start_sec")
            val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
            val DAY_OFF = stringPreferencesKey("day_off_date")
            val NOTIFY_BEFORE_LEAVE = intPreferencesKey("notify_before_leave_min")
            val NOTIFY_BEFORE_FIRST_LEG = intPreferencesKey("notify_before_first_leg_min")
            val NOTIFY_BEFORE_TRANSFER = intPreferencesKey("notify_before_transfer_min")
            val PLAN_COMPUTED_AT = stringPreferencesKey("plan_computed_at")
            val PLAN_TODAY = stringPreferencesKey("plan_today")
            val PLAN_TODAY_COUNT = intPreferencesKey("plan_today_count")
            val PLAN_TODAY_REASON = stringPreferencesKey("plan_today_reason")
            val PLAN_TOMORROW = stringPreferencesKey("plan_tomorrow")
            val PLAN_TOMORROW_COUNT = intPreferencesKey("plan_tomorrow_count")
            val PLAN_TOMORROW_REASON = stringPreferencesKey("plan_tomorrow_reason")
            val PLAN_EXACT = booleanPreferencesKey("plan_exact_alarms")
            val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
            val TRAIN_REMINDER_ENABLED = booleanPreferencesKey("train_reminder_enabled")
            val TRAIN_REMINDER_WINDOW_START = intPreferencesKey("train_reminder_window_start_sec")
            val WORKPLACE_LAT = doublePreferencesKey("workplace_lat")
            val WORKPLACE_LON = doublePreferencesKey("workplace_lon")
            val HOME_LAT = doublePreferencesKey("home_lat")
            val HOME_LON = doublePreferencesKey("home_lon")
            val TRAIN_REMINDER_OFF_DATE = stringPreferencesKey("train_reminder_off_date")
        }
    }
