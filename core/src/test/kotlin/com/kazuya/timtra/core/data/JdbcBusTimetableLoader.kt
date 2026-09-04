package com.kazuya.timtra.core.data

import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.core.model.BusDirection
import com.kazuya.timtra.core.model.BusStop
import com.kazuya.timtra.core.model.BusTrip
import com.kazuya.timtra.core.model.CalendarException
import com.kazuya.timtra.core.model.CalendarRule
import com.kazuya.timtra.core.model.ExceptionType
import com.kazuya.timtra.core.model.GtfsTime
import com.kazuya.timtra.core.model.ServiceCalendar
import com.kazuya.timtra.core.model.StopRole
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * テスト専用: tools/gtfs_import.py が生成したプリパッケージ DB を JDBC で読む。
 * Android 側の Room DAO はここと同じ結合・詰め替えを行う（docs/db_schema.md）。
 */
object JdbcBusTimetableLoader {
    fun load(path: String): BusTimetable =
        DriverManager.getConnection("jdbc:sqlite:$path").use { con ->
            val meta = con.rows("SELECT key, value FROM meta") { it.getString(1) to it.getString(2) }.toMap()
            val schema = meta[BusTimetable.META_SCHEMA_VERSION]?.toIntOrNull()
            require(schema == BusTimetable.SUPPORTED_SCHEMA_VERSION) { "未対応の schema_version: $schema" }

            val stops =
                con
                    .rows("SELECT stop_id, stop_name, platform_code, role FROM stops") {
                        BusStop(it.getString(1), it.getString(2), it.getString(3), StopRole.fromDbValue(it.getString(4)))
                    }.associateBy { it.stopId }

            val trips =
                con.rows(
                    """
                    SELECT l.trip_id, l.route_id, r.route_short_name, t.trip_headsign, l.service_id, l.direction,
                           l.board_stop_id, l.alight_stop_id, l.board_departure_secs, l.alight_arrival_secs
                    FROM commute_legs l
                    JOIN trips t ON t.trip_id = l.trip_id
                    JOIN routes r ON r.route_id = l.route_id
                    """.trimIndent(),
                ) {
                    BusTrip(
                        tripId = it.getString(1),
                        routeId = it.getString(2),
                        routeShortName = it.getString(3),
                        headsign = it.getString(4),
                        serviceId = it.getString(5),
                        direction = BusDirection.fromDbValue(it.getString(6)),
                        boardStop = stops.getValue(it.getString(7)),
                        alightStop = stops.getValue(it.getString(8)),
                        departure = GtfsTime(it.getInt(9)),
                        arrival = GtfsTime(it.getInt(10)),
                    )
                }

            val dayColumns = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
            val rules =
                con.rows("SELECT service_id, ${dayColumns.joinToString()}, start_date, end_date FROM calendar") { rs ->
                    val days = DayOfWeek.entries.filterIndexed { i, _ -> rs.getInt(2 + i) == 1 }.toSet()
                    CalendarRule(rs.getString(1), days, gtfsDate(rs.getString(9)), gtfsDate(rs.getString(10)))
                }
            val exceptions =
                con.rows("SELECT service_id, date, exception_type FROM calendar_dates") {
                    CalendarException(it.getString(1), gtfsDate(it.getString(2)), ExceptionType.fromGtfsValue(it.getInt(3)))
                }
            BusTimetable(trips, ServiceCalendar(rules, exceptions), meta)
        }

    private fun gtfsDate(text: String): LocalDate = LocalDate.parse(text, TimTraConstants.GTFS_DATE)

    private fun <T> Connection.rows(
        sql: String,
        map: (ResultSet) -> T,
    ): List<T> =
        createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(map(rs)) }
            }
        }
}
