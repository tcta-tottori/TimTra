package com.kazuya.timtra.core.realtime

import com.google.protobuf.util.JsonFormat
import com.google.transit.realtime.GtfsRealtime
import java.time.Instant

/**
 * GTFS-RT の FeedMessage から VehiclePosition を取り出す。
 * protobuf のバイナリと、その JSON 表現（鳥取県オープンデータは snake_case の JSON で配信）の両方を受け付ける。
 */
object GtfsRtParser {
    private val jsonParser = JsonFormat.parser().ignoringUnknownFields()

    fun parseVehiclePositions(
        bytes: ByteArray,
        fetchedAt: Instant,
    ): VehiclePositionFeed {
        val message = decode(bytes)
        val header = message.header
        val feedTimestamp = if (header.hasTimestamp()) Instant.ofEpochSecond(header.timestamp) else null
        val vehicles =
            message.entityList.mapNotNull { entity ->
                if (!entity.hasVehicle()) return@mapNotNull null
                val v = entity.vehicle
                if (!v.hasPosition()) return@mapNotNull null
                VehiclePosition(
                    vehicleId = if (v.hasVehicle() && v.vehicle.hasId()) v.vehicle.id else entity.id.ifBlank { null },
                    tripId = if (v.hasTrip() && v.trip.hasTripId()) v.trip.tripId else null,
                    routeId = if (v.hasTrip() && v.trip.hasRouteId()) v.trip.routeId else null,
                    latitude = v.position.latitude.toDouble(),
                    longitude = v.position.longitude.toDouble(),
                    stopId = if (v.hasStopId()) v.stopId else null,
                    currentStopSequence = if (v.hasCurrentStopSequence()) v.currentStopSequence else null,
                    currentStatus =
                        if (v.hasCurrentStatus()) {
                            when (v.currentStatus) {
                                GtfsRealtime.VehiclePosition.VehicleStopStatus.INCOMING_AT -> VehicleStopStatus.INCOMING_AT
                                GtfsRealtime.VehiclePosition.VehicleStopStatus.STOPPED_AT -> VehicleStopStatus.STOPPED_AT
                                GtfsRealtime.VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO -> VehicleStopStatus.IN_TRANSIT_TO
                                else -> null
                            }
                        } else {
                            null
                        },
                    observedAt = if (v.hasTimestamp()) Instant.ofEpochSecond(v.timestamp) else null,
                )
            }
        return VehiclePositionFeed(fetchedAt = fetchedAt, feedTimestamp = feedTimestamp, vehicles = vehicles)
    }

    /** 先頭が '{' なら JSON、それ以外は protobuf バイナリとして読む。 */
    internal fun decode(bytes: ByteArray): GtfsRealtime.FeedMessage {
        val firstVisible =
            bytes.firstOrNull {
                it != ' '.code.toByte() &&
                    it != '\n'.code.toByte() &&
                    it != '\r'.code.toByte() &&
                    it != '\t'.code.toByte()
            }
        return if (firstVisible == '{'.code.toByte()) {
            val builder = GtfsRealtime.FeedMessage.newBuilder()
            jsonParser.merge(String(bytes, Charsets.UTF_8), builder)
            // proto2 の required（header.gtfs_realtime_version）が欠けていても落とさない
            builder.buildPartial()
        } else {
            GtfsRealtime.FeedMessage.parseFrom(bytes)
        }
    }
}
