package com.kazuya.timtra.data.sync

import com.kazuya.timtra.core.journey.CommuteSettings
import com.kazuya.timtra.core.notify.NotificationTiming
import com.kazuya.timtra.data.repository.AppSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** Wearable Data Layer のパスとキー。スマホ → Wear の一方向。 */
object WearSyncPaths {
    /** 設定（[SyncedSettings] の JSON）。 */
    const val SETTINGS = "/timtra/settings"
    const val KEY_JSON = "json"
    const val KEY_SENT_AT = "sentAt"
}

/**
 * スマホの設定を Wear に配るための形。計算そのものは同梱データで Wear 側が行うので、
 * 設定と「今日は休み」が一致していれば両者の結果は同じになる（CLAUDE.md 3-3, 7-4）。
 */
@Serializable
data class SyncedSettings(
    val walkHomeToStopMin: Int,
    val transferBusToJrMin: Int,
    val prepBufferMin: Int,
    val walkStationToWorkMin: Int,
    val minTransferMin: Int,
    val comfortableTransferMin: Int,
    val outboundWindowStartSec: Int,
    val inboundWindowStartSec: Int,
    val earliestLeaveHomeSec: Int,
    val workEndsAtSec: Int,
    /** 出発時刻の表示時間帯。古いスマホ版からの JSON には無いので既定値を持つ。 */
    val leaveHomeDisplayStartSec: Int = CommuteSettings().leaveHomeDisplayStart.toSecondOfDay(),
    val leaveHomeDisplayEndSec: Int = CommuteSettings().leaveHomeDisplayEnd.toSecondOfDay(),
    val leaveWorkDisplayStartSec: Int = CommuteSettings().leaveWorkDisplayStart.toSecondOfDay(),
    val notificationsEnabled: Boolean,
    /** ISO 日付。無ければ null。 */
    val dayOff: String? = null,
    val notifyBeforeLeaveMin: Int,
    val notifyBeforeFirstLegMin: Int,
    val notifyBeforeTransferMin: Int,
    /** 送信時刻（epoch ミリ秒）。古い更新で新しい設定を上書きしないために使う。 */
    val sentAtEpochMillis: Long,
) {
    fun toAppSettings(): AppSettings =
        AppSettings(
            commute =
                CommuteSettings(
                    walkHomeToStop = Duration.ofMinutes(walkHomeToStopMin.toLong()),
                    transferBusToJr = Duration.ofMinutes(transferBusToJrMin.toLong()),
                    prepBuffer = Duration.ofMinutes(prepBufferMin.toLong()),
                    walkStationToWork = Duration.ofMinutes(walkStationToWorkMin.toLong()),
                    minTransfer = Duration.ofMinutes(minTransferMin.toLong()),
                    comfortableTransfer = Duration.ofMinutes(comfortableTransferMin.toLong()),
                    outboundWindowStart = LocalTime.ofSecondOfDay(outboundWindowStartSec.toLong()),
                    inboundWindowStart = LocalTime.ofSecondOfDay(inboundWindowStartSec.toLong()),
                    earliestLeaveHome = LocalTime.ofSecondOfDay(earliestLeaveHomeSec.toLong()),
                    workEndsAt = LocalTime.ofSecondOfDay(workEndsAtSec.toLong()),
                    leaveHomeDisplayStart = LocalTime.ofSecondOfDay(leaveHomeDisplayStartSec.toLong()),
                    leaveHomeDisplayEnd = LocalTime.ofSecondOfDay(leaveHomeDisplayEndSec.toLong()),
                    leaveWorkDisplayStart = LocalTime.ofSecondOfDay(leaveWorkDisplayStartSec.toLong()),
                ),
            notificationsEnabled = notificationsEnabled,
            dayOff = dayOff?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            notificationTiming =
                NotificationTiming(
                    beforeLeave = Duration.ofMinutes(notifyBeforeLeaveMin.toLong()),
                    beforeFirstLegDeparture = Duration.ofMinutes(notifyBeforeFirstLegMin.toLong()),
                    beforeTransferArrival = Duration.ofMinutes(notifyBeforeTransferMin.toLong()),
                ),
        )

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        fun from(
            settings: AppSettings,
            sentAtEpochMillis: Long,
        ): SyncedSettings {
            val c = settings.commute
            val t = settings.notificationTiming
            return SyncedSettings(
                walkHomeToStopMin = c.walkHomeToStop.toMinutes().toInt(),
                transferBusToJrMin = c.transferBusToJr.toMinutes().toInt(),
                prepBufferMin = c.prepBuffer.toMinutes().toInt(),
                walkStationToWorkMin = c.walkStationToWork.toMinutes().toInt(),
                minTransferMin = c.minTransfer.toMinutes().toInt(),
                comfortableTransferMin = c.comfortableTransfer.toMinutes().toInt(),
                outboundWindowStartSec = c.outboundWindowStart.toSecondOfDay(),
                inboundWindowStartSec = c.inboundWindowStart.toSecondOfDay(),
                earliestLeaveHomeSec = c.earliestLeaveHome.toSecondOfDay(),
                workEndsAtSec = c.workEndsAt.toSecondOfDay(),
                leaveHomeDisplayStartSec = c.leaveHomeDisplayStart.toSecondOfDay(),
                leaveHomeDisplayEndSec = c.leaveHomeDisplayEnd.toSecondOfDay(),
                leaveWorkDisplayStartSec = c.leaveWorkDisplayStart.toSecondOfDay(),
                notificationsEnabled = settings.notificationsEnabled,
                dayOff = settings.dayOff?.toString(),
                notifyBeforeLeaveMin = t.beforeLeave.toMinutes().toInt(),
                notifyBeforeFirstLegMin = t.beforeFirstLegDeparture.toMinutes().toInt(),
                notifyBeforeTransferMin = t.beforeTransferArrival.toMinutes().toInt(),
                sentAtEpochMillis = sentAtEpochMillis,
            )
        }

        fun parse(text: String): SyncedSettings = json.decodeFromString(serializer(), text)
    }
}
