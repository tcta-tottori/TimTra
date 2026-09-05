package com.kazuya.timtra.core.realtime

import com.google.protobuf.util.JsonFormat
import com.google.transit.realtime.GtfsRealtime
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class GtfsRtClientTest {
    private val t0: Instant = Instant.parse("2026-09-07T07:00:00Z")

    private fun feedBytes(vararg tripIds: String): ByteArray {
        val builder =
            GtfsRealtime.FeedMessage
                .newBuilder()
                .setHeader(
                    GtfsRealtime.FeedHeader
                        .newBuilder()
                        .setGtfsRealtimeVersion("2.0")
                        .setTimestamp(t0.epochSecond),
                )
        tripIds.forEachIndexed { i, tripId ->
            builder.addEntity(
                GtfsRealtime.FeedEntity
                    .newBuilder()
                    .setId("e$i")
                    .setVehicle(
                        GtfsRealtime.VehiclePosition
                            .newBuilder()
                            .setTrip(
                                GtfsRealtime.TripDescriptor
                                    .newBuilder()
                                    .setTripId(tripId)
                                    .setRouteId("R_91"),
                            ).setVehicle(GtfsRealtime.VehicleDescriptor.newBuilder().setId("bus$i"))
                            .setPosition(
                                GtfsRealtime.Position
                                    .newBuilder()
                                    .setLatitude(35.47f)
                                    .setLongitude(134.20f),
                            ).setCurrentStopSequence(3)
                            .setCurrentStatus(GtfsRealtime.VehiclePosition.VehicleStopStatus.IN_TRANSIT_TO)
                            .setStopId("S_YOSHINARI")
                            .setTimestamp(t0.epochSecond - 10),
                    ),
            )
        }
        return builder.build().toByteArray()
    }

    @Test
    fun `parses vehicle positions`() {
        val feed = GtfsRtParser.parseVehiclePositions(feedBytes("T_91_WD_0705"), t0)
        assertEquals(t0, feed.feedTimestamp)
        assertEquals(1, feed.vehicles.size)
        val v = feed.vehicles.single()
        assertEquals("T_91_WD_0705", v.tripId)
        assertEquals("R_91", v.routeId)
        assertEquals("bus0", v.vehicleId)
        assertEquals(3, v.currentStopSequence)
        assertEquals(VehicleStopStatus.IN_TRANSIT_TO, v.currentStatus)
        assertEquals("S_YOSHINARI", v.stopId)
        assertEquals(t0.minusSeconds(10), v.observedAt)
        assertEquals(35.47, v.latitude, 0.001)
        assertNotNull(feed.forTrip("T_91_WD_0705"))
        assertNull(feed.forTrip("nope"))
    }

    @Test
    fun `parses the JSON representation used by the Tottori open data feed`() {
        // 鳥取県の配信そのままの形（snake_case、enum は数値）
        val json =
            """
            {
              "header": { "gtfs_realtime_version": "2.0", "incrementality": 0, "timestamp": ${t0.epochSecond} },
              "entity": [
                {
                  "id": "bus-1",
                  "vehicle": {
                    "trip": { "trip_id": "T_91_WD_0705", "route_id": "R310100111" },
                    "vehicle": { "id": "V001", "label": "1234" },
                    "position": { "latitude": 35.48, "longitude": 134.21, "bearing": 180.0 },
                    "current_stop_sequence": 5,
                    "current_status": 2,
                    "stop_id": "S310100077700100",
                    "timestamp": ${t0.epochSecond - 7}
                  }
                }
              ]
            }
            """.trimIndent()
        val feed = GtfsRtParser.parseVehiclePositions(json.toByteArray(), t0)
        assertEquals(t0, feed.feedTimestamp)
        val v = feed.vehicles.single()
        assertEquals("T_91_WD_0705", v.tripId)
        assertEquals("R310100111", v.routeId)
        assertEquals("V001", v.vehicleId)
        assertEquals(5, v.currentStopSequence)
        assertEquals(VehicleStopStatus.IN_TRANSIT_TO, v.currentStatus)
        assertEquals("S310100077700100", v.stopId)
        assertEquals(t0.minusSeconds(7), v.observedAt)
        assertEquals(35.48, v.latitude, 0.0001)
    }

    @Test
    fun `JSON printed by protobuf itself round-trips and matches the binary parse`() {
        val bytes = feedBytes("T_91_WD_0705", "T_91_WD_0735")
        val json = JsonFormat.printer().print(GtfsRealtime.FeedMessage.parseFrom(bytes))
        val fromJson = GtfsRtParser.parseVehiclePositions(json.toByteArray(), t0)
        val fromBinary = GtfsRtParser.parseVehiclePositions(bytes, t0)
        assertEquals(fromBinary, fromJson)
    }

    @Test
    fun `leading whitespace before JSON is tolerated`() {
        val json = "\n  {\"header\":{\"gtfs_realtime_version\":\"2.0\"},\"entity\":[]}"
        assertEquals(0, GtfsRtParser.parseVehiclePositions(json.toByteArray(), t0).vehicles.size)
    }

    @Test
    fun `throttles to one fetch per 30 seconds and keeps the last feed`() =
        runBlocking {
            var now = t0
            var calls = 0
            val client =
                GtfsRtClient(
                    url = "https://example.invalid/vp",
                    fetcher = {
                        calls++
                        feedBytes("T_91_WD_0705")
                    },
                    clock = { now },
                )
            val first = client.fetchVehiclePositions()
            assertIs<FetchResult.Success>(first)
            assertEquals(1, calls)

            now = t0.plusSeconds(5)
            val second = client.fetchVehiclePositions()
            assertIs<FetchResult.Throttled>(second)
            assertEquals(25, second.retryAfter.seconds)
            assertSame(first.feed, second.lastFeed)
            assertEquals(1, calls, "制限中は HTTP を呼ばない")

            now = t0.plusSeconds(30)
            assertIs<FetchResult.Success>(client.fetchVehiclePositions())
            assertEquals(2, calls)
        }

    @Test
    fun `failure keeps the previous feed and still counts as an attempt`() =
        runBlocking {
            var now = t0
            var fail = false
            val client =
                GtfsRtClient(
                    url = "u",
                    fetcher = { if (fail) throw IOException("boom") else feedBytes("T") },
                    clock = { now },
                )
            val ok = assertIs<FetchResult.Success>(client.fetchVehiclePositions())
            now = t0.plusSeconds(30)
            fail = true
            val failure = assertIs<FetchResult.Failure>(client.fetchVehiclePositions())
            assertSame(ok.feed, failure.lastFeed)
            assertIs<IOException>(failure.error)
            now = t0.plusSeconds(40)
            assertIs<FetchResult.Throttled>(client.fetchVehiclePositions(), "失敗も試行として数える")
        }
}
