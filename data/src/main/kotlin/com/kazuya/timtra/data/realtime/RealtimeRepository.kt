package com.kazuya.timtra.data.realtime

import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.core.model.GtfsTime
import com.kazuya.timtra.core.realtime.DelayEstimate
import com.kazuya.timtra.core.realtime.DelayEstimator
import com.kazuya.timtra.core.realtime.FetchResult
import com.kazuya.timtra.core.realtime.GtfsRtClient
import com.kazuya.timtra.core.realtime.TripStopTime
import com.kazuya.timtra.core.realtime.VehiclePositionFeed
import com.kazuya.timtra.data.db.GtfsDao
import com.kazuya.timtra.data.di.AppClock
import com.kazuya.timtra.data.repository.BusTimetableRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** リアルタイム情報の状態。UI は「推定」であることを必ず明示する。 */
data class RealtimeState(
    val status: Status = Status.NOT_CONFIGURED,
    /** 乗り継ぎ計算に足す遅延（早発は 0）。trip_id → 遅延。 */
    val delays: Map<String, Duration> = emptyMap(),
    val estimates: List<DelayEstimate> = emptyList(),
    /** 最後に成功した取得の時刻。 */
    val fetchedAt: Instant? = null,
    val errorMessage: String? = null,
) {
    enum class Status {
        /** URL 未設定 */
        NOT_CONFIGURED,

        /** まだ取得していない */
        IDLE,

        /** 取得成功（対象便の車両が見つからない場合も含む） */
        OK,

        /** 30 秒制限で待機中（前回の値を保持） */
        THROTTLED,

        /** 取得失敗（前回の値を保持） */
        ERROR,
    }
}

/**
 * GTFS-RT の VehiclePosition を取得し、対象便（南吉成⇔鳥取駅の便）の遅延を推定する。
 * 30 秒スロットリングは [GtfsRtClient] が内蔵。呼び出しは画面がフォアグラウンドの間だけ（ViewModel の責務）。
 */
@Singleton
class RealtimeRepository
    @Inject
    constructor(
        private val dao: GtfsDao,
        private val bus: BusTimetableRepository,
        fetcher: OkHttpFeedFetcher,
        private val clock: AppClock,
    ) {
        private val client = GtfsRtClient(RealtimeEndpoints.VEHICLE_POSITIONS_URL, fetcher)
        private val estimator = DelayEstimator()
        private val mutex = Mutex()
        private val stopTimesCache = HashMap<String, List<TripStopTime>>()

        private val _state =
            MutableStateFlow(
                RealtimeState(
                    status = if (RealtimeEndpoints.isConfigured) RealtimeState.Status.IDLE else RealtimeState.Status.NOT_CONFIGURED,
                ),
            )
        val state: StateFlow<RealtimeState> = _state

        /** 取得して状態を更新する。制限中や失敗時は前回の推定値を保ったまま status だけ変える。 */
        suspend fun refresh(): RealtimeState =
            mutex.withLock {
                if (!RealtimeEndpoints.isConfigured) return _state.value
                val now = clock.now()
                val next =
                    when (val result = client.fetchVehiclePositions()) {
                        is FetchResult.Success -> estimated(result.feed, now).copy(status = RealtimeState.Status.OK)
                        is FetchResult.Throttled -> _state.value.copy(status = RealtimeState.Status.THROTTLED, errorMessage = null)
                        is FetchResult.Failure ->
                            _state.value.copy(
                                status = RealtimeState.Status.ERROR,
                                errorMessage =
                                    result.error.message ?: result.error.javaClass.simpleName,
                            )
                    }
                _state.value = next
                next
            }

        private suspend fun estimated(
            feed: VehiclePositionFeed,
            now: LocalDateTime,
        ): RealtimeState {
            val nowInstant = now.atZone(TimTraConstants.ZONE).toInstant()
            val commuteTrips = bus.timetable().trips.associateBy { it.tripId }
            val estimates =
                feed.vehicles.mapNotNull { vehicle ->
                    val tripId = vehicle.tripId ?: return@mapNotNull null
                    val trip = commuteTrips[tripId] ?: return@mapNotNull null
                    val stopTimes = stopTimes(tripId)
                    // 深夜便（24 時以降に始まる便）は前日のサービス日に属する
                    val serviceDate = serviceDateFor(now.toLocalDate(), stopTimes.firstOrNull()?.departure ?: trip.departure)
                    estimator.estimate(tripId, serviceDate, stopTimes, vehicle, nowInstant, TimTraConstants.ZONE)
                }
            return RealtimeState(
                status = RealtimeState.Status.OK,
                delays = estimates.associate { it.tripId to it.delayForPlanning },
                estimates = estimates,
                fetchedAt = feed.fetchedAt,
                errorMessage = null,
            )
        }

        private suspend fun stopTimes(tripId: String): List<TripStopTime> =
            stopTimesCache[tripId] ?: dao
                .tripStopTimes(tripId)
                .map {
                    TripStopTime(
                        it.stopId,
                        it.stopSequence,
                        GtfsTime(it.arrivalSecs),
                        GtfsTime(it.departureSecs),
                        it.stopLat,
                        it.stopLon,
                    )
                }.also { stopTimesCache[tripId] = it }

        private fun serviceDateFor(
            today: LocalDate,
            firstDeparture: GtfsTime,
        ): LocalDate = if (firstDeparture.isPastMidnight) today.minusDays(1) else today
    }
