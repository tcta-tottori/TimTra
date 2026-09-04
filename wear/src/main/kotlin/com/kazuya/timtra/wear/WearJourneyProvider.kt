package com.kazuya.timtra.wear

import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.journey.PlanRequest
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.data.di.AppClock
import com.kazuya.timtra.data.repository.BusTimetableRepository
import com.kazuya.timtra.data.repository.JourneyRepository
import com.kazuya.timtra.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** 時計側で表示する 1 枚分の情報。タイル・コンプリケーション・UI が共通で使う。 */
data class WearSnapshot(
    val now: LocalDateTime,
    val bound: Bound,
    val journey: Journey?,
    val next: Journey?,
    val dayOff: Boolean,
    val sampleData: Boolean,
    /** スマホから設定を受け取った時刻（epoch ミリ秒）。未同期なら null。 */
    val lastSyncedAt: Long?,
)

/**
 * 同梱データと（スマホから同期された）設定で、時計単独で乗り継ぎを計算する。
 * core が同じなのでスマホと同じ結果になる（CLAUDE.md 3-3）。
 */
@Singleton
class WearJourneyProvider
    @Inject
    constructor(
        private val journeys: JourneyRepository,
        private val settings: SettingsRepository,
        private val bus: BusTimetableRepository,
        private val clock: AppClock,
    ) {
        suspend fun snapshot(manualBound: Bound? = null): WearSnapshot {
            val now = clock.now()
            val appSettings = settings.current()
            val bound = manualBound ?: appSettings.commute.boundAt(now.toLocalTime())
            val candidates = journeys.candidates(PlanRequest(now = now, bound = bound), CANDIDATE_COUNT)
            return WearSnapshot(
                now = now,
                bound = bound,
                journey = candidates.getOrNull(0),
                next = candidates.getOrNull(1),
                dayOff = appSettings.isDayOff(now.toLocalDate()),
                sampleData = bus.timetable().isSampleData,
                lastSyncedAt = settings.lastSyncedAtEpochMillis.first(),
            )
        }

        private companion object {
            const val CANDIDATE_COUNT = 2
        }
    }
