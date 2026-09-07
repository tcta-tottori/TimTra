package com.kazuya.timtra.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.core.geo.RouteLandmarks
import com.kazuya.timtra.core.journey.BoundBasis
import com.kazuya.timtra.core.journey.BoundDecision
import com.kazuya.timtra.core.journey.BoundResolver
import com.kazuya.timtra.core.journey.CommuteSettings
import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.journey.LeaveDisplayPolicy
import com.kazuya.timtra.core.journey.PlanRequest
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.data.di.AppClock
import com.kazuya.timtra.data.realtime.RealtimeRepository
import com.kazuya.timtra.data.realtime.RealtimeState
import com.kazuya.timtra.data.repository.AppSettings
import com.kazuya.timtra.data.repository.BusTimetableRepository
import com.kazuya.timtra.data.repository.JourneyRepository
import com.kazuya.timtra.data.repository.JrTimetableRepository
import com.kazuya.timtra.data.repository.SettingsRepository
import com.kazuya.timtra.location.LocationProvider
import com.kazuya.timtra.notify.NotificationScheduler
import com.kazuya.timtra.notify.PermissionStatus
import com.kazuya.timtra.notify.TrainReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        val now: LocalDateTime,
        val bound: Bound,
        /** 往路/復路をどう決めたか（手動 / 現在地 / 時刻帯）。 */
        val boundBasis: BoundBasis,
        /** 位置情報の許可があるか。無ければホームに小さな案内を出す。 */
        val locationPermitted: Boolean,
        /** 現在地。許可が無い・取れないときは null。地図と距離表示に使う。 */
        val location: GeoPoint?,
        /** 地図に出す地点（南吉成・鳥取駅・宝木駅・勤務先）。 */
        val landmarks: RouteLandmarks,
        /**
         * 「家を出る時刻」「職場を出る時刻」を出すか（[LeaveDisplayPolicy]）。
         * false のときは代わりに最初の便（バス / JR）の発車を主役にする。
         */
        val showLeaveTime: Boolean,
        /** 直近の案。運行が無ければ null。 */
        val journey: Journey?,
        /** 1 本後の候補。 */
        val next: Journey?,
        val dayOff: Boolean,
        val settings: CommuteSettings,
        /** バス時刻表が合成サンプルか。 */
        val sampleBus: Boolean,
        /** JR 時刻表がサンプルか（実ダイヤ未転記）。 */
        val sampleJr: Boolean,
        /** 通知に必要な権限の状態。欠けていれば案内カードを出す。 */
        val permissions: PermissionStatus,
        /** 勤務先を離れたため、今日の「次の電車」リマインダーが止まっているか。 */
        val reminderOffToday: Boolean,
        /** GTFS-RT の取得状態と推定遅延。 */
        val realtime: RealtimeState,
    ) : HomeUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val journeys: JourneyRepository,
        private val settingsRepository: SettingsRepository,
        private val busTimetable: BusTimetableRepository,
        private val jrTimetable: JrTimetableRepository,
        private val scheduler: NotificationScheduler,
        private val realtime: RealtimeRepository,
        private val location: LocationProvider,
        private val trainReminders: TrainReminderScheduler,
        private val clock: AppClock,
    ) : ViewModel() {
        private val manualBound = MutableStateFlow<Bound?>(null)
        private val refreshCount = MutableStateFlow(0)

        /** 画面が表示されている間だけ 30 秒ごとに再計算する（常駐はしない）。 */
        private val ticker =
            flow {
                while (true) {
                    emit(Unit)
                    delay(TICK_MILLIS)
                }
            }

        val uiState: StateFlow<HomeUiState> =
            combine(ticker, refreshCount, settingsRepository.settings, manualBound) { _, _, settings, manual -> settings to manual }
                .mapLatest { (settings, manual) -> compute(settings, manual) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState.Loading)

        init {
            // 夜間ジョブの登録と、起動時点での予約の作り直し
            scheduler.ensureScheduled()
        }

        private suspend fun compute(
            settings: AppSettings,
            manual: Bound?,
        ): HomeUiState {
            val now = clock.now()
            val timetable = busTimetable.timetable()
            // 現在位置に近い側の出発時刻を出す。手動切替が最優先、位置が取れなければ時刻帯で決める。
            val here = location.current()
            val decision =
                manual?.let { BoundDecision(it, BoundBasis.MANUAL) }
                    ?: BoundResolver.resolve(
                        location = here,
                        homeStop = timetable.homeStopLocation,
                        byTime = settings.commute.boundAt(now.toLocalTime()),
                        workStation = TrainReminderScheduler.workplaceOf(settings),
                    )
            val bound = decision.bound
            // 勤務先を離れていれば、その日の「次の電車」リマインダーを止める
            trainReminders.onLocationObserved(here, now)
            val workplace = TrainReminderScheduler.workplaceOf(settings)
            val landmarks =
                RouteLandmarks.build(
                    homeStop = timetable.homeStopLocation,
                    stationBusStop = timetable.stationStopLocation,
                    workplace = workplace,
                )

            suspend fun plan(delays: Map<String, Duration>) =
                journeys.candidates(PlanRequest(now = now, bound = bound, delays = delays), CANDIDATE_COUNT)

            // 手順 3（CLAUDE.md 6）: リアルタイム情報があれば推定遅延を加算して再判定する。
            // 取得は画面が表示されている間（このフローが購読されている間）だけ。30 秒制限はクライアント側で守る。
            var realtimeState = realtime.state.value
            var candidates = plan(realtimeState.delays)
            val primary = candidates.firstOrNull()
            if (primary != null && shouldPoll(primary, now)) {
                val refreshed = realtime.refresh()
                if (refreshed.delays != realtimeState.delays) candidates = plan(refreshed.delays)
                realtimeState = refreshed
            }

            val primaryJourney = candidates.getOrNull(0)
            // 出発時刻は決まった時間帯にだけ出す（家: 朝の時間帯、職場: 17 時以降〜終電、勤務先付近にいる間）
            val showLeaveTime =
                primaryJourney != null &&
                    when (bound) {
                        Bound.OUTBOUND -> LeaveDisplayPolicy.showLeaveHome(now, settings.commute)
                        Bound.INBOUND ->
                            LeaveDisplayPolicy.showLeaveWork(
                                now = now,
                                trainDate = primaryJourney.train.date,
                                location = here,
                                workplace = workplace,
                                settings = settings.commute,
                            )
                    }

            return HomeUiState.Ready(
                now = now,
                bound = bound,
                boundBasis = decision.basis,
                locationPermitted = location.hasPermission,
                location = here,
                landmarks = landmarks,
                showLeaveTime = showLeaveTime,
                journey = primaryJourney,
                next = candidates.getOrNull(1),
                dayOff = settings.isDayOff(now.toLocalDate()),
                settings = settings.commute,
                sampleBus = timetable.isSampleData,
                sampleJr = jrTimetable.timetable().version.startsWith("sample"),
                permissions = PermissionStatus.check(context),
                reminderOffToday = settings.trainReminder.enabled && settings.isTrainReminderOff(now.toLocalDate()),
                realtime = realtimeState,
            )
        }

        /** バスの発車 90 分前から到着 5 分後までだけ車両位置を見る。それ以外の時間帯に取りに行っても意味がない。 */
        private fun shouldPoll(
            journey: Journey,
            now: LocalDateTime,
        ): Boolean {
            val from = journey.bus.departureAt.minus(POLL_BEFORE_DEPARTURE)
            val until = journey.bus.arrivalAt.plus(POLL_AFTER_ARRIVAL)
            return !now.isBefore(from) && !now.isAfter(until)
        }

        /** null で自動判定に戻す。 */
        fun setBound(bound: Bound?) {
            manualBound.value = bound
        }

        /** 手動更新と、権限画面から戻ったときの状態再取得。 */
        fun refresh() {
            refreshCount.value += 1
        }

        /** 「再開」: 勤務先を離れた記録を消してリマインダーを予約し直す。 */
        fun resumeTrainReminders() {
            viewModelScope.launch {
                trainReminders.resume()
                refresh()
            }
        }

        private companion object {
            const val TICK_MILLIS = 30_000L
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val CANDIDATE_COUNT = 2
            val POLL_BEFORE_DEPARTURE: Duration = Duration.ofMinutes(90)
            val POLL_AFTER_ARRIVAL: Duration = Duration.ofMinutes(5)
        }
    }
