package com.kazuya.timtra.ui.timetable

import com.kazuya.timtra.core.model.BusDirection
import com.kazuya.timtra.core.model.JrLegIds
import com.kazuya.timtra.data.repository.BusTimetableRepository
import com.kazuya.timtra.data.repository.JrTimetableRepository
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 時刻表画面とホームの「次の便」ポップアップが共有する、表示用エントリの組み立て。
 * バス（GTFS）と JR（JSON）を同じ [TimetableEntry] にそろえる。
 */
@Singleton
class TimetableCatalog
    @Inject
    constructor(
        private val bus: BusTimetableRepository,
        private val jr: JrTimetableRepository,
    ) {
        /** その日・そのタブの便を発時刻順に。 */
        suspend fun entries(
            tab: TimetableTab,
            date: LocalDate,
            filter: StationFilter = StationFilter.ALL,
        ): List<TimetableEntry> {
            val busTimetable = bus.timetable()
            val jrTimetable = jr.timetable()
            val list =
                when (tab) {
                    TimetableTab.HOME_STOP ->
                        busTimetable.tripsOn(date, BusDirection.TO_STATION).map {
                            TimetableEntry(it.departure.seconds, EntryKind.BUS, it.routeDisplayName, it.headsign, it.boardStop.platformCode)
                        }
                    TimetableTab.STATION -> {
                        val buses =
                            busTimetable.tripsOn(date, BusDirection.FROM_STATION).map {
                                TimetableEntry(
                                    it.departure.seconds,
                                    EntryKind.BUS,
                                    it.routeDisplayName,
                                    it.headsign,
                                    it.boardStop.platformCode,
                                )
                            }
                        val leg = jrTimetable.leg(JrLegIds.TOTTORI_TO_HOUGI)
                        val trains =
                            jrTimetable.servicesOn(date, leg.id).map {
                                TimetableEntry(
                                    seconds = it.departure.toSecondOfDay(),
                                    kind = EntryKind.JR,
                                    line = leg.line,
                                    destination = leg.to,
                                    platform = it.platform.ifBlank { null },
                                    code = it.trainId,
                                    note = it.note.ifBlank { null },
                                )
                            }
                        (buses + trains).filter { filter.accepts(it.kind) }
                    }
                    TimetableTab.HOUGI -> {
                        val leg = jrTimetable.leg(JrLegIds.HOUGI_TO_TOTTORI)
                        jrTimetable.servicesOn(date, leg.id).map {
                            TimetableEntry(
                                seconds = it.departure.toSecondOfDay(),
                                kind = EntryKind.JR,
                                line = leg.line,
                                destination = leg.to,
                                platform = it.platform.ifBlank { null },
                                code = it.trainId,
                                note = it.note.ifBlank { null },
                            )
                        }
                    }
                }
            return list.sortedWith(compareBy({ it.seconds }, { it.kind }, { it.line }))
        }

        /**
         * 表示日を決める。「今日」はそのまま、日種別指定は今日以降で最初にその種別になる日。
         * JR と同じ判定（overrides・祝日）を使うので、バスの calendar_dates とも概ね一致する。
         */
        suspend fun resolveDate(
            today: LocalDate,
            day: DaySelection,
        ): LocalDate {
            val wanted = day.dayType ?: return today
            val jrTimetable = jr.timetable()
            return (0L until LOOKAHEAD_DAYS)
                .asSequence()
                .map { today.plusDays(it) }
                .firstOrNull { jrTimetable.dayTypeOf(it) == wanted }
                ?: today
        }

        private companion object {
            /** 日種別を探す範囲。祝日を含む連休でも 2 週間あれば各種別が 1 日は見つかる。 */
            const val LOOKAHEAD_DAYS = 14L
        }
    }
