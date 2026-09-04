package com.kazuya.timtra.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.core.model.BusDirection
import com.kazuya.timtra.core.model.JrLegIds
import com.kazuya.timtra.data.repository.BusTimetableRepository
import com.kazuya.timtra.data.repository.JrTimetableRepository
import com.kazuya.timtra.di.AppClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class TimetableTab { HOME_STOP, STATION, HOUGI }

enum class EntryKind { BUS, JR }

data class TimetableEntry(
    /** 0 時起点の秒。深夜便は 86400 以上（表示は折り返す）。 */
    val seconds: Int,
    val kind: EntryKind,
    /** 系統番号 / 列車番号 */
    val line: String,
    val destination: String,
    val platform: String?,
) {
    val time: LocalTime get() = LocalTime.ofSecondOfDay((seconds % SECONDS_PER_DAY).toLong())

    private companion object {
        const val SECONDS_PER_DAY = 24 * 3600
    }
}

data class TimetableUiState(
    val tab: TimetableTab = TimetableTab.HOME_STOP,
    val date: LocalDate? = null,
    val now: LocalTime = LocalTime.MIDNIGHT,
    val entries: List<TimetableEntry> = emptyList(),
    val loading: Boolean = true,
) {
    /** 現在時刻以降の最初の便。自動スクロールとハイライトに使う。 */
    val upcomingIndex: Int get() = entries.indexOfFirst { it.seconds >= now.toSecondOfDay() }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimetableViewModel
    @Inject
    constructor(
        private val bus: BusTimetableRepository,
        private val jr: JrTimetableRepository,
        private val clock: AppClock,
    ) : ViewModel() {
        private val tab = MutableStateFlow(TimetableTab.HOME_STOP)

        val uiState: StateFlow<TimetableUiState> =
            tab
                .mapLatest { load(it) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimetableUiState())

        fun selectTab(newTab: TimetableTab) {
            tab.value = newTab
        }

        private suspend fun load(tab: TimetableTab): TimetableUiState {
            val now = clock.now()
            val today = now.toLocalDate()
            val busTimetable = bus.timetable()
            val jrTimetable = jr.timetable()
            val entries =
                when (tab) {
                    TimetableTab.HOME_STOP ->
                        busTimetable.tripsOn(today, BusDirection.TO_STATION).map {
                            TimetableEntry(it.departure.seconds, EntryKind.BUS, it.routeShortName, it.headsign, it.boardStop.platformCode)
                        }
                    TimetableTab.STATION -> {
                        val buses =
                            busTimetable.tripsOn(today, BusDirection.FROM_STATION).map {
                                TimetableEntry(
                                    it.departure.seconds,
                                    EntryKind.BUS,
                                    it.routeShortName,
                                    it.headsign,
                                    it.boardStop.platformCode,
                                )
                            }
                        val leg = jrTimetable.leg(JrLegIds.TOTTORI_TO_HOUGI)
                        val trains =
                            jrTimetable.servicesOn(today, leg.id).map {
                                TimetableEntry(it.departure.toSecondOfDay(), EntryKind.JR, it.trainId, leg.to, it.platform.ifBlank { null })
                            }
                        buses + trains
                    }
                    TimetableTab.HOUGI -> {
                        val leg = jrTimetable.leg(JrLegIds.HOUGI_TO_TOTTORI)
                        jrTimetable.servicesOn(today, leg.id).map {
                            TimetableEntry(it.departure.toSecondOfDay(), EntryKind.JR, it.trainId, leg.to, it.platform.ifBlank { null })
                        }
                    }
                }.sortedWith(compareBy({ it.seconds }, { it.kind }, { it.line }))
            return TimetableUiState(tab = tab, date = today, now = now.toLocalTime(), entries = entries, loading = false)
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
