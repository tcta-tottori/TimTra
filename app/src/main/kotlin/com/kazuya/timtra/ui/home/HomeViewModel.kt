package com.kazuya.timtra.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.core.geo.RouteLandmarks
import com.kazuya.timtra.core.journey.BoundBasis
import com.kazuya.timtra.core.journey.BoundDecision
import com.kazuya.timtra.core.journey.BoundResolver
import com.kazuya.timtra.core.journey.CommuteSettings
import com.kazuya.timtra.core.journey.InboundPhase
import com.kazuya.timtra.core.journey.InboundPhaseResolver
import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.journey.LeaveDisplayPolicy
import com.kazuya.timtra.core.journey.PlanRequest
import com.kazuya.timtra.core.journey.RestPolicy
import com.kazuya.timtra.core.journey.ScheduledBus
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.BusDirection
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places
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
import com.kazuya.timtra.ui.timetable.StationFilter
import com.kazuya.timtra.ui.timetable.TimetableCatalog
import com.kazuya.timtra.ui.timetable.TimetableEntry
import com.kazuya.timtra.ui.timetable.TimetableTab
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** ホームのバス / JR カードをタップしたときに出す「次の便」の時刻表。 */
enum class PeekKind(
    val tab: TimetableTab,
    val filter: StationFilter,
) {
    /** 南吉成 発（往路のバス） */
    BUS_HOME(TimetableTab.HOME_STOP, StationFilter.ALL),

    /** 鳥取 発 宝木方面（往路の JR） */
    JR_OUTBOUND(TimetableTab.STATION, StationFilter.JR),

    /** 宝木 発 鳥取方面（復路の JR） */
    JR_INBOUND(TimetableTab.HOUGI, StationFilter.ALL),

    /** 鳥取駅 発 用瀬・智頭方面（復路のバス） */
    BUS_STATION(TimetableTab.STATION, StationFilter.BUS),
}

data class TimetablePeek(
    val kind: PeekKind,
    val today: LocalDate,
    /** 今日の残りの便（現在時刻以降）。 */
    val upcomingToday: List<TimetableEntry>,
    val tomorrow: LocalDate,
    /** 翌日の始発から数本（今日の残りが少ないときの続き）。 */
    val tomorrowHead: List<TimetableEntry>,
)

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
        /**
         * 復路の段階。宝木駅エリアを離れて鳥取方面へ向かっていれば [InboundPhase.TO_BUS] で、
         * 主役は電車ではなく鳥取駅発のバス（[stationBuses]）になる。
         */
        val inboundPhase: InboundPhase,
        /** [InboundPhase.TO_BUS] のときの、鳥取駅を出る次のバス（先頭が直近、2 本目が次の候補）。 */
        val stationBuses: List<ScheduledBus>,
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
        private val timetables: TimetableCatalog,
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

        /** 1 秒刻みの現在時刻。秒単位のカウントダウン用。画面が表示されている間だけ進む。 */
        val now: StateFlow<LocalDateTime> =
            flow {
                while (true) {
                    emit(clock.now())
                    delay(SECOND_MILLIS)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), clock.now())

        /**
         * 現在地。画面が表示されている間だけ数秒おきに更新する（地図と「駅まであと何分」をほぼリアルタイムに）。
         * 許可を得た直後は [refresh] で購読し直す。数 m の揺れでは再計算しない。
         */
        private val liveLocation: StateFlow<GeoPoint?> =
            refreshCount
                .flatMapLatest { locationSource() }
                .distinctUntilChanged { a, b -> a != null && b != null && a.distanceMetersTo(b) < LOCATION_MIN_MOVE_METERS }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        private fun locationSource(): Flow<GeoPoint?> =
            merge<GeoPoint?>(
                flow { emit(location.current()) },
                location.updates(LocationProvider.LIVE_INTERVAL_MILLIS),
            )

        val uiState: StateFlow<HomeUiState> =
            combine(ticker, refreshCount, settingsRepository.settings, manualBound, liveLocation) { _, _, settings, manual, here ->
                Triple(settings, manual, here)
            }.mapLatest { (settings, manual, here) -> compute(settings, manual, here) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState.Loading)

        /** 開いている「次の便」ポップアップ。null なら閉じている。 */
        private val _peek = MutableStateFlow<TimetablePeek?>(null)
        val peek: StateFlow<TimetablePeek?> = _peek

        init {
            // 夜間ジョブの登録と、起動時点での予約の作り直し
            scheduler.ensureScheduled()
        }

        private suspend fun compute(
            settings: AppSettings,
            manual: Bound?,
            here: GeoPoint?,
        ): HomeUiState {
            val now = clock.now()
            val timetable = busTimetable.timetable()
            // 現在位置に近い側の出発時刻を出す。手動切替が最優先、位置が取れなければ時刻帯で決める。
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
            val home = settings.home ?: Places.HOME_DEFAULT
            val landmarks =
                RouteLandmarks.build(
                    homeStop = timetable.homeStopLocation,
                    stationBusStop = timetable.stationStopLocation,
                    workplace = workplace,
                    home = home,
                )
            // 夜、鳥取駅を離れて帰路についたら（自宅側にいたら）残り時間は出さない。翌朝 leaveHomeDisplayStart から再開
            val resting =
                RestPolicy.isResting(
                    now = now,
                    bound = bound,
                    here = here,
                    homeStop = timetable.homeStopLocation,
                    station = timetable.stationStopLocation ?: Places.TOTTORI_STATION,
                    settings = settings.commute,
                )
            val nextMorning =
                if (resting) {
                    // 朝の時間帯より前（深夜〜早朝）なら今日、それ以降（夕方・夜）なら明日の往路
                    val date =
                        if (now.toLocalTime().isBefore(
                                settings.commute.leaveHomeDisplayStart,
                            )
                        ) {
                            now.toLocalDate()
                        } else {
                            now.toLocalDate().plusDays(1)
                        }
                    journeys.planForDate(date, Bound.OUTBOUND)
                } else {
                    null
                }

            suspend fun plan(delays: Map<String, Duration>) =
                journeys.candidates(PlanRequest(now = now, bound = bound, delays = delays), CANDIDATE_COUNT)

            // 手順 3（CLAUDE.md 6）: リアルタイム情報があれば推定遅延を加算して再判定する。
            // 取得は画面が表示されている間（このフローが購読されている間）だけ。30 秒制限はクライアント側で守る。
            var realtimeState = realtime.state.value
            var candidates = plan(realtimeState.delays)
            val primary = candidates.firstOrNull()
            // 復路で宝木駅エリアを離れたら（乗車中・鳥取駅到着後）、電車ではなく鳥取駅発の次のバスを主役にする
            val inboundPhase = InboundPhaseResolver.resolve(bound, here)
            val stationBuses =
                if (inboundPhase ==
                    InboundPhase.TO_BUS
                ) {
                    journeys.nextBuses(now, BusDirection.FROM_STATION, CANDIDATE_COUNT)
                } else {
                    emptyList()
                }
            val pollForStationBus = stationBuses.firstOrNull()?.let { shouldPoll(it, now) } == true
            if ((primary != null && shouldPoll(primary, now)) || pollForStationBus) {
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
                inboundPhase = inboundPhase,
                stationBuses = stationBuses,
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
        ): Boolean = shouldPoll(journey.bus, now)

        private fun shouldPoll(
            bus: ScheduledBus,
            now: LocalDateTime,
        ): Boolean {
            val from = bus.departureAt.minus(POLL_BEFORE_DEPARTURE)
            val until = bus.arrivalAt.plus(POLL_AFTER_ARRIVAL)
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

        /** バス / JR カードのタップ。現在時刻以降の時刻表をポップアップで出す。 */
        fun openPeek(kind: PeekKind) {
            viewModelScope.launch {
                val now = clock.now()
                val today = now.toLocalDate()
                val nowSec = now.toLocalTime().toSecondOfDay()
                val todayEntries = timetables.entries(kind.tab, today, kind.filter).filter { it.seconds >= nowSec }
                val tomorrow = today.plusDays(1)
                val tomorrowEntries =
                    if (todayEntries.size <
                        PEEK_MIN_ROWS
                    ) {
                        timetables.entries(kind.tab, tomorrow, kind.filter).take(PEEK_TOMORROW_ROWS)
                    } else {
                        emptyList()
                    }
                _peek.value = TimetablePeek(kind, today, todayEntries.take(PEEK_MAX_ROWS), tomorrow, tomorrowEntries)
            }
        }

        fun closePeek() {
            _peek.value = null
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
            const val SECOND_MILLIS = 1_000L
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /** これ未満の移動では再計算しない（測位の揺れ対策）。地図の点は再計算のたびに動く。 */
            const val LOCATION_MIN_MOVE_METERS = 8.0
            const val PEEK_MAX_ROWS = 20
            const val PEEK_MIN_ROWS = 5
            const val PEEK_TOMORROW_ROWS = 5
            const val CANDIDATE_COUNT = 2
            val POLL_BEFORE_DEPARTURE: Duration = Duration.ofMinutes(90)
            val POLL_AFTER_ARRIVAL: Duration = Duration.ofMinutes(5)
        }
    }
