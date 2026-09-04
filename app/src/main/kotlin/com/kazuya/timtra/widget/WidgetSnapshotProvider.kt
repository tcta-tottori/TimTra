package com.kazuya.timtra.widget

import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.journey.PlanRequest
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.data.di.AppClock
import com.kazuya.timtra.data.repository.JourneyRepository
import com.kazuya.timtra.data.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** ウィジェット 1 枚分の情報。リアルタイム情報は使わない（バックグラウンドで取得しないため）。 */
data class WidgetSnapshot(
    val now: LocalDateTime,
    val bound: Bound,
    val journey: Journey?,
    val dayOff: Boolean,
)

@Singleton
class WidgetSnapshotProvider
    @Inject
    constructor(
        private val journeys: JourneyRepository,
        private val settings: SettingsRepository,
        private val clock: AppClock,
    ) {
        suspend fun snapshot(): WidgetSnapshot {
            val now = clock.now()
            val appSettings = settings.current()
            val bound = appSettings.commute.boundAt(now.toLocalTime())
            val journey = journeys.candidates(PlanRequest(now = now, bound = bound), 1).firstOrNull()
            return WidgetSnapshot(now, bound, journey, appSettings.isDayOff(now.toLocalDate()))
        }
    }

/** Glance のウィジェットは Hilt で直接注入できないため、EntryPoint 経由で取り出す。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun snapshotProvider(): WidgetSnapshotProvider
}
