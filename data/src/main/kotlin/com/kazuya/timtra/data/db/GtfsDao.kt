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

/** 1 便の停車時刻と停留所座標。GTFS-RT の遅延推定用。 */
data class TripStopTimeRow(
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "stop_sequence") val stopSequence: Int,
    @ColumnInfo(name = "arrival_secs") val arrivalSecs: Int,
    @ColumnInfo(name = "departure_secs") val departureSecs: Int,
    @ColumnInfo(name = "stop_lat") val stopLat: Double?,
    @ColumnInfo(name = "stop_lon") val stopLon: Double?,
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

    /** 1 便の全停車時刻と座標。GTFS-RT の車両位置と予定位置の比較に使う。 */
    @Query(
        """
        SELECT st.stop_id, st.stop_sequence, st.arrival_secs, st.departure_secs, s.stop_lat, s.stop_lon
        FROM stop_times st
        JOIN stops s ON s.stop_id = st.stop_id
        WHERE st.trip_id = :tripId
        ORDER BY st.stop_sequence
        """,
    )
    suspend fun tripStopTimes(tripId: String): List<TripStopTimeRow>
}
