package com.kazuya.timtra.data.repository

import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.journey.JourneyPlanner
import com.kazuya.timtra.core.journey.PlanRequest
import com.kazuya.timtra.core.model.Bound
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** 時刻表と設定を束ねて [JourneyPlanner] を組み立てる。UI・通知ジョブ・Wear 同期の共通入口。 */
@Singleton
class JourneyRepository
    @Inject
    constructor(
        private val bus: BusTimetableRepository,
        private val jr: JrTimetableRepository,
        private val settings: SettingsRepository,
    ) {
        suspend fun planner(): JourneyPlanner = JourneyPlanner(bus.timetable(), jr.timetable(), settings.current().commute)

        /** 直近から [count] 件の案。 */
        suspend fun candidates(
            request: PlanRequest,
            count: Int,
        ): List<Journey> = planner().candidates(request, count)

        /** 前夜のジョブ用（手順 4）。 */
        suspend fun planForDate(
            date: LocalDate,
            bound: Bound,
        ): Journey? = planner().planForDate(date, bound)
    }
