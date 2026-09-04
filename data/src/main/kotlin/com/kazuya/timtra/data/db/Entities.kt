package com.kazuya.timtra.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * プリパッケージ DB（tools/gtfs_import.py が生成）のスキーマと 1:1 で対応させる。
 * 列名・型・NOT NULL・主キー順・インデックス名を docs/db_schema.md と一致させること。
 * ずれると Room が createFromAsset() の検証で例外を投げる。
 */

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "stops")
data class StopEntity(
    @PrimaryKey @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "stop_name") val stopName: String,
    @ColumnInfo(name = "stop_lat") val stopLat: Double?,
    @ColumnInfo(name = "stop_lon") val stopLon: Double?,
    @ColumnInfo(name = "platform_code") val platformCode: String?,
    @ColumnInfo(name = "parent_station") val parentStation: String?,
    val role: String?,
)

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey @ColumnInfo(name = "route_id") val routeId: String,
    @ColumnInfo(name = "agency_id") val agencyId: String?,
    @ColumnInfo(name = "route_short_name") val routeShortName: String,
    @ColumnInfo(name = "route_long_name") val routeLongName: String,
)

@Entity(
    tableName = "trips",
    indices = [Index("route_id"), Index("service_id")],
)
data class TripEntity(
    @PrimaryKey @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "route_id") val routeId: String,
    @ColumnInfo(name = "service_id") val serviceId: String,
    @ColumnInfo(name = "direction_id") val directionId: Int?,
    @ColumnInfo(name = "trip_headsign") val tripHeadsign: String,
    @ColumnInfo(name = "block_id") val blockId: String?,
    @ColumnInfo(name = "shape_id") val shapeId: String?,
)

@Entity(
    tableName = "stop_times",
    primaryKeys = ["trip_id", "stop_sequence"],
    indices = [Index("stop_id")],
)
data class StopTimeEntity(
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "stop_sequence") val stopSequence: Int,
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "arrival_secs") val arrivalSecs: Int,
    @ColumnInfo(name = "departure_secs") val departureSecs: Int,
    @ColumnInfo(name = "pickup_type") val pickupType: Int,
    @ColumnInfo(name = "drop_off_type") val dropOffType: Int,
    val timepoint: Int?,
)

@Entity(tableName = "calendar")
data class CalendarEntity(
    @PrimaryKey @ColumnInfo(name = "service_id") val serviceId: String,
    val monday: Int,
    val tuesday: Int,
    val wednesday: Int,
    val thursday: Int,
    val friday: Int,
    val saturday: Int,
    val sunday: Int,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String,
)

@Entity(
    tableName = "calendar_dates",
    primaryKeys = ["service_id", "date"],
)
data class CalendarDateEntity(
    @ColumnInfo(name = "service_id") val serviceId: String,
    val date: String,
    @ColumnInfo(name = "exception_type") val exceptionType: Int,
)

@Entity(
    tableName = "commute_legs",
    indices = [Index("direction")],
)
data class CommuteLegEntity(
    @PrimaryKey @ColumnInfo(name = "trip_id") val tripId: String,
    val direction: String,
    @ColumnInfo(name = "route_id") val routeId: String,
    @ColumnInfo(name = "service_id") val serviceId: String,
    @ColumnInfo(name = "board_stop_id") val boardStopId: String,
    @ColumnInfo(name = "alight_stop_id") val alightStopId: String,
    @ColumnInfo(name = "board_departure_secs") val boardDepartureSecs: Int,
    @ColumnInfo(name = "alight_arrival_secs") val alightArrivalSecs: Int,
)
