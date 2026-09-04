package com.kazuya.timtra.core.realtime

import com.google.transit.realtime.GtfsRealtime
import java.time.Instant

/** GTFS-RT の FeedMessage（protobuf）から VehiclePosition を取り出す。 */
object GtfsRtParser {
    fun parseVehiclePositions(
        bytes: ByteArray,
        fetchedAt: Instant,
    ): VehiclePositionFeed {
        val message = GtfsRealtime.FeedMessage.parseFrom(bytes)
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
}
