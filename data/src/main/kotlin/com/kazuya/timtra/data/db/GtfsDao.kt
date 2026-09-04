package com.kazuya.timtra.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

/** commute_legs に routes / trips を結合した行。core の BusTrip に詰め替える。 */
data class CommuteLegRow(
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "route_id") val routeId: String,
    @ColumnInfo(name = "route_short_name") val routeShortName: String,
    @ColumnInfo(name = "trip_headsign") val tripHeadsign: String,
    @ColumnInfo(name = "service_id") val serviceId: String,
    val direction: String,
    @ColumnInfo(name = "board_stop_id") val boardStopId: String,
    @ColumnInfo(name = "alight_stop_id") val alightStopId: String,
    @ColumnInfo(name = "board_departure_secs") val boardDepartureSecs: Int,
    @ColumnInfo(name = "alight_arrival_secs") val alightArrivalSecs: Int,
)

@Dao
interface GtfsDao {
    @Query("SELECT * FROM meta")
    suspend fun meta(): List<MetaEntity>

    @Query("SELECT * FROM stops")
    suspend fun stops(): List<StopEntity>

    @Query("SELECT * FROM calendar")
    suspend fun calendar(): List<CalendarEntity>

    @Query("SELECT * FROM calendar_dates")
    suspend fun calendarDates(): List<CalendarDateEntity>

    @Query(
        """
        SELECT l.trip_id, l.route_id, r.route_short_name, t.trip_headsign, l.service_id, l.direction,
               l.board_stop_id, l.alight_stop_id, l.board_departure_secs, l.alight_arrival_secs
        FROM commute_legs l
        JOIN trips t ON t.trip_id = l.trip_id
        JOIN routes r ON r.route_id = l.route_id
        ORDER BY l.direction, l.board_departure_secs, l.trip_id
        """,
    )
    suspend fun commuteLegs(): List<CommuteLegRow>

    /** 1 便の全停車時刻。GTFS-RT の車両位置と予定位置の比較に使う（手順 6）。 */
    @Query("SELECT * FROM stop_times WHERE trip_id = :tripId ORDER BY stop_sequence")
    suspend fun stopTimes(tripId: String): List<StopTimeEntity>
}
