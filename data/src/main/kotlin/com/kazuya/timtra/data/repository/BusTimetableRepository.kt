package com.kazuya.timtra.data.repository

import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.core.data.BusTimetable
import com.kazuya.timtra.core.model.BusDirection
import com.kazuya.timtra.core.model.BusStop
import com.kazuya.timtra.core.model.BusTrip
import com.kazuya.timtra.core.model.CalendarException
import com.kazuya.timtra.core.model.CalendarRule
import com.kazuya.timtra.core.model.ExceptionType
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.GtfsTime
import com.kazuya.timtra.core.model.ServiceCalendar
import com.kazuya.timtra.core.model.StopRole
import com.kazuya.timtra.data.db.CalendarEntity
import com.kazuya.timtra.data.db.GtfsDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * プリパッケージ DB を読み、core の [BusTimetable] に詰め替える。
 * 対象便は数十本なので初回に全件読み込み、以後はメモリ上のものを返す。
 */
@Singleton
class BusTimetableRepository
    @Inject
    constructor(
        private val dao: GtfsDao,
    ) {
        private val mutex = Mutex()

        @Volatile
        private var cached: BusTimetable? = null

        suspend fun timetable(): BusTimetable =
            cached ?: mutex.withLock {
                cached ?: load().also { cached = it }
            }

        private suspend fun load(): BusTimetable {
            val meta = dao.meta().associate { it.key to it.value }
            val schema = meta[BusTimetable.META_SCHEMA_VERSION]?.toIntOrNull()
            check(schema == BusTimetable.SUPPORTED_SCHEMA_VERSION) {
                "プリパッケージ DB の schema_version が想定と違います: $schema"
            }
            val stops =
                dao.stops().associate {
                    val location = if (it.stopLat != null && it.stopLon != null) GeoPoint(it.stopLat, it.stopLon) else null
                    it.stopId to BusStop(it.stopId, it.stopName, it.platformCode, StopRole.fromDbValue(it.role), location)
                }
            val trips =
                dao.commuteLegs().map { row ->
                    BusTrip(
                        tripId = row.tripId,
                        routeId = row.routeId,
                        routeShortName = row.routeShortName,
                        headsign = row.tripHeadsign,
                        serviceId = row.serviceId,
                        direction = BusDirection.fromDbValue(row.direction),
                        boardStop = stops.getValue(row.boardStopId),
                        alightStop = stops.getValue(row.alightStopId),
                        departure = GtfsTime(row.boardDepartureSecs),
                        arrival = GtfsTime(row.alightArrivalSecs),
                        routeLongName = row.routeLongName,
                    )
                }
            val rules = dao.calendar().map { it.toRule() }
            val exceptions =
                dao.calendarDates().map {
                    CalendarException(it.serviceId, gtfsDate(it.date), ExceptionType.fromGtfsValue(it.exceptionType))
                }
            return BusTimetable(trips, ServiceCalendar(rules, exceptions), meta)
        }

        private fun CalendarEntity.toRule(): CalendarRule {
            val flags = listOf(monday, tuesday, wednesday, thursday, friday, saturday, sunday)
            val days = DayOfWeek.entries.filterIndexed { i, _ -> flags[i] == 1 }.toSet()
            return CalendarRule(serviceId, days, gtfsDate(startDate), gtfsDate(endDate))
        }

        private fun gtfsDate(text: String): LocalDate = LocalDate.parse(text, TimTraConstants.GTFS_DATE)
    }
