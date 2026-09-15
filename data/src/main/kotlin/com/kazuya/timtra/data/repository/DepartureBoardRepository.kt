package com.kazuya.timtra.data.repository

import com.kazuya.timtra.core.board.BoardPlace
import com.kazuya.timtra.core.board.Departure
import com.kazuya.timtra.core.board.DepartureBoard
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.DayType
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.data.di.AppClock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 発車標（[DepartureBoard]）の入口。時刻表 2 つを束ねるだけで、計算は core に任せる。
 * 時計のタイルと時計アプリが使う。
 */
@Singleton
class DepartureBoardRepository
    @Inject
    constructor(
        private val bus: BusTimetableRepository,
        private val jr: JrTimetableRepository,
        private val clock: AppClock,
    ) {
        /** [place] から [now] 以降に出る便を早い順に最大 [limit] 件。 */
        suspend fun upcoming(
            place: BoardPlace,
            now: LocalDateTime = clock.now(),
            limit: Int = DepartureBoard.DEFAULT_LIMIT,
        ): List<Departure> = DepartureBoard.upcoming(place, now, bus.timetable(), jr.timetable(), limit)

        /** [place] の [date] 1 日分（始発から終電まで）。 */
        suspend fun onDate(
            place: BoardPlace,
            date: LocalDate,
        ): List<Departure> = DepartureBoard.onDate(place, date, bus.timetable(), jr.timetable())

        /** [dayTypes] に当てはまる直近の日（今日を含む）。空なら [today] そのまま。 */
        suspend fun resolveDate(
            today: LocalDate,
            dayTypes: Set<DayType>,
        ): LocalDate = DepartureBoard.resolveDate(today, dayTypes, jr.timetable())

        /** 各地点の位置。位置が分からない地点は含まれない。 */
        suspend fun locations(): Map<BoardPlace, GeoPoint> = DepartureBoard.locations(bus.timetable())

        /** 現在地から最寄りの地点。通勤圏外なら null。 */
        suspend fun nearest(
            here: GeoPoint,
            bound: Bound,
        ): BoardPlace? = DepartureBoard.nearest(here, locations(), bound)
    }
