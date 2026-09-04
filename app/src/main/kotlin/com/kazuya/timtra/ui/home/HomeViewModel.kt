package com.kazuya.timtra.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.core.journey.CommuteSettings
import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.journey.PlanRequest
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.data.di.AppClock
import com.kazuya.timtra.data.realtime.RealtimeRepository
import com.kazuya.timtra.data.realtime.RealtimeState
import com.kazuya.timtra.data.repository.AppSettings
import com.kazuya.timtra.data.repository.BusTimetableRepository
import com.kazuya.timtra.data.repository.JourneyRepository
import com.kazuya.timtra.data.repository.JrTimetableRepository
import com.kazuya.timtra.data.repository.SettingsRepository
import com.kazuya.timtra.notify.NotificationScheduler
import com.kazuya.timtra.notify.PermissionStatus
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
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(
        val now: LocalDateTime,
        val bound: Bound,
        /** 手動で往路/復路を切り替えているか。 */
        val isManualBound: Boolean,
        /** 直近の案。運行が無ければ null。 */
        val journey: Journey?,
        /** 1 本後の候補。 */
        val next: Journey?,
        val dayOff: Boolean,
        val settings: CommuteSettings,
        /** 合成サンプルの時刻表で動いているか。 */
        val sampleData: Boolean,
        /** 通知に必要な権限の状態。欠けていれば案内カードを出す。 */
        val permissions: PermissionStatus,
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
            val bound = manual ?: settings.commute.boundAt(now.toLocalTime())

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

            val sample = busTimetable.timetable().isSampleData || jrTimetable.timetable().version.startsWith("sample")
            return HomeUiState.Ready(
                now = now,
                bound = bound,
                isManualBound = manual != null,
                journey = candidates.getOrNull(0),
                next = candidates.getOrNull(1),
                dayOff = settings.isDayOff(now.toLocalDate()),
                settings = settings.commute,
                sampleData = sample,
                permissions = PermissionStatus.check(context),
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

        private companion object {
            const val TICK_MILLIS = 30_000L
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val CANDIDATE_COUNT = 2
            val POLL_BEFORE_DEPARTURE: Duration = Duration.ofMinutes(90)
            val POLL_AFTER_ARRIVAL: Duration = Duration.ofMinutes(5)
        }
    }
