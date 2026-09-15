package com.kazuya.timtra.ui.timetable

import com.kazuya.timtra.core.board.BoardPlace
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
 *
 * JR は ◆特定日のみ運転の便も含める（終電まで見せる）。毎日は走らないことは備考で示す。
 * 通勤案とリマインダーの計算では従来どおり除外する（`JrTimetable.servicesOn` の既定）。
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
                            jrTimetable.servicesOn(date, leg.id, includeIrregular = true).map {
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
                        // 終電まで見せたいので ◆特定日のみ運転の便も並べる（備考に運転日を出す）
                        jrTimetable.servicesOn(date, leg.id, includeIrregular = true).map {
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

/** 発車標の地点（core の [BoardPlace]）に対応する時刻表のタブ。 */
fun BoardPlace.tab(): TimetableTab =
    when (this) {
        BoardPlace.HOME_STOP -> TimetableTab.HOME_STOP
        BoardPlace.STATION_BUS, BoardPlace.TOTTORI_JR -> TimetableTab.STATION
        BoardPlace.HOUGI_JR -> TimetableTab.HOUGI
    }

/** 鳥取駅タブはバスと JR が混ざるので、地点に合わせて絞り込む。 */
fun BoardPlace.stationFilter(): StationFilter =
    when (this) {
        BoardPlace.STATION_BUS -> StationFilter.BUS
        BoardPlace.TOTTORI_JR -> StationFilter.JR
        BoardPlace.HOME_STOP, BoardPlace.HOUGI_JR -> StationFilter.ALL
    }
