package com.kazuya.timtra.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.core.model.BusDirection
import com.kazuya.timtra.core.model.DayType
import com.kazuya.timtra.core.model.JrLegIds
import com.kazuya.timtra.data.di.AppClock
import com.kazuya.timtra.data.repository.BusTimetableRepository
import com.kazuya.timtra.data.repository.JrTimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

/** 表示する日種別。null は「今日」（初期表示）。 */
enum class DaySelection(
    val dayType: DayType?,
) {
    TODAY(null),
    WEEKDAY(DayType.WEEKDAY),
    SATURDAY(DayType.SATURDAY),
    HOLIDAY(DayType.HOLIDAY),
}

data class TimetableUiState(
    val tab: TimetableTab = TimetableTab.HOME_STOP,
    val day: DaySelection = DaySelection.TODAY,
    /** 実際に時刻表を引いた日。日種別指定のときは、その種別に該当する直近の日。 */
    val date: LocalDate? = null,
    val now: LocalTime = LocalTime.MIDNIGHT,
    val entries: List<TimetableEntry> = emptyList(),
    val loading: Boolean = true,
) {
    val isToday: Boolean get() = day == DaySelection.TODAY

    /** 現在時刻以降の最初の便。今日の表示でのみ自動スクロールとハイライトに使う。 */
    val upcomingIndex: Int get() = if (isToday) entries.indexOfFirst { it.seconds >= now.toSecondOfDay() } else -1
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
        private val day = MutableStateFlow(DaySelection.TODAY)

        val uiState: StateFlow<TimetableUiState> =
            combine(tab, day) { t, d -> t to d }
                .mapLatest { (t, d) -> load(t, d) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimetableUiState())

        fun selectTab(newTab: TimetableTab) {
            tab.value = newTab
        }

        fun selectDay(newDay: DaySelection) {
            day.value = newDay
        }

        private suspend fun load(
            tab: TimetableTab,
            day: DaySelection,
        ): TimetableUiState {
            val now = clock.now()
            val busTimetable = bus.timetable()
            val jrTimetable = jr.timetable()
            val today = resolveDate(now.toLocalDate(), day, jrTimetable::dayTypeOf)
            val entries =
                when (tab) {
                    TimetableTab.HOME_STOP ->
                        busTimetable.tripsOn(today, BusDirection.TO_STATION).map {
                            TimetableEntry(it.departure.seconds, EntryKind.BUS, it.routeDisplayName, it.headsign, it.boardStop.platformCode)
                        }
                    TimetableTab.STATION -> {
                        val buses =
                            busTimetable.tripsOn(today, BusDirection.FROM_STATION).map {
                                TimetableEntry(
                                    it.departure.seconds,
                                    EntryKind.BUS,
                                    it.routeDisplayName,
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
            return TimetableUiState(tab = tab, day = day, date = today, now = now.toLocalTime(), entries = entries, loading = false)
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /** 日種別を探す範囲。祝日を含む連休でも 2 週間あれば各種別が 1 日は見つかる。 */
            const val LOOKAHEAD_DAYS = 14L

            /**
             * 表示日を決める。「今日」はそのまま、日種別指定は今日以降で最初にその種別になる日。
             * JR と同じ判定（overrides・祝日）を使うので、バスの calendar_dates とも概ね一致する。
             */
            fun resolveDate(
                today: LocalDate,
                day: DaySelection,
                dayTypeOf: (LocalDate) -> DayType,
            ): LocalDate {
                val wanted = day.dayType ?: return today
                return (0L until LOOKAHEAD_DAYS)
                    .asSequence()
                    .map { today.plusDays(it) }
                    .firstOrNull { dayTypeOf(it) == wanted }
                    ?: today
            }
        }
    }
