package com.kazuya.timtra.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.core.journey.CommuteSettings
import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.journey.PlanRequest
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.data.di.AppClock
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
            val candidates = journeys.candidates(PlanRequest(now = now, bound = bound), CANDIDATE_COUNT)
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
            )
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
        }
    }
