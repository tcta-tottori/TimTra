package com.kazuya.timtra.ui.timetable

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.core.model.DayType
import com.kazuya.timtra.data.di.AppClock
import com.kazuya.timtra.data.repository.DepartureBoardRepository
import com.kazuya.timtra.data.repository.SettingsRepository
import com.kazuya.timtra.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

enum class TimetableTab { HOME_STOP, STATION, HOUGI }

enum class EntryKind { BUS, JR }

data class TimetableEntry(
    /** 0 時起点の秒。深夜便は 86400 以上（表示は折り返す）。 */
    val seconds: Int,
    val kind: EntryKind,
    /** 路線名 / 系統（例: "用瀬智頭線"、"山陰本線"）。 */
    val line: String,
    val destination: String,
    val platform: String?,
    /** 列車番号など、便を特定する短い記号。無ければ null。 */
    val code: String? = null,
    /** 備考（JR の note）。 */
    val note: String? = null,
) {
    val time: LocalTime get() = LocalTime.ofSecondOfDay((seconds % SECONDS_PER_DAY).toLong())

    /** 時間帯ごとの見出しに使う「時」。深夜便は 24, 25 … のまま（表示は 0 時台に折り返さない）。 */
    val hour: Int get() = seconds / 3600

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

/** 鳥取駅タブの絞り込み。バスと JR が混ざるので分けて見られるようにする。 */
enum class StationFilter {
    ALL,
    BUS,
    JR,
    ;

    fun accepts(kind: EntryKind): Boolean =
        when (this) {
            ALL -> true
            BUS -> kind == EntryKind.BUS
            JR -> kind == EntryKind.JR
        }
}

/**
 * ホームから時刻表を開くときの初期表示。ホームの主役表示のバス / JR のピルをタップすると、
 * その区間の駅・バス停のタブで開く。指定せずに開いたときは現在地の最寄りで決める。
 */
enum class TimetableFocus(
    val tab: TimetableTab,
    val filter: StationFilter,
) {
    /** 南吉成（往路のバス）。 */
    HOME_STOP(TimetableTab.HOME_STOP, StationFilter.ALL),

    /** 鳥取駅のバスのりば（復路のバス）。 */
    STATION_BUS(TimetableTab.STATION, StationFilter.BUS),

    /** JR 鳥取駅（往路の JR）。 */
    STATION_JR(TimetableTab.STATION, StationFilter.JR),

    /** 鳥取駅のバスと JR の両方（地図の地点チップから開くとき）。 */
    STATION_ALL(TimetableTab.STATION, StationFilter.ALL),

    /** JR 宝木駅（復路の JR）。 */
    HOUGI(TimetableTab.HOUGI, StationFilter.ALL),
    ;

    companion object {
        /** 画面の行き来で渡す名前。 */
        const val ARG = "focus"

        fun of(name: String?): TimetableFocus? = entries.firstOrNull { it.name == name }
    }
}

data class TimetableUiState(
    val tab: TimetableTab = TimetableTab.HOME_STOP,
    val day: DaySelection = DaySelection.TODAY,
    /** 鳥取駅タブでのみ有効。 */
    val stationFilter: StationFilter = StationFilter.ALL,
    /** 実際に時刻表を引いた日。日種別指定のときは、その種別に該当する直近の日。 */
    val date: LocalDate? = null,
    val now: LocalTime = LocalTime.MIDNIGHT,
    val entries: List<TimetableEntry> = emptyList(),
    val loading: Boolean = true,
) {
    val isToday: Boolean get() = day == DaySelection.TODAY

    /** 現在時刻以降の最初の便。今日の表示でのみ自動スクロールとハイライトに使う。 */
    val upcomingIndex: Int get() = if (isToday) entries.indexOfFirst { it.seconds >= now.toSecondOfDay() } else -1

    /** 時間帯ごとにまとめた表示用の並び。 */
    val hourGroups: List<HourGroup>
        get() =
            entries
                .withIndex()
                .groupBy { it.value.hour }
                .toSortedMap()
                .map { (hour, items) -> HourGroup(hour, items.map { IndexedEntry(it.index, it.value) }) }
}

data class IndexedEntry(
    val index: Int,
    val entry: TimetableEntry,
)

data class HourGroup(
    val hour: Int,
    val items: List<IndexedEntry>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimetableViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val catalog: TimetableCatalog,
        private val board: DepartureBoardRepository,
        private val location: LocationProvider,
        private val settings: SettingsRepository,
        private val clock: AppClock,
    ) : ViewModel() {
        private val tab = MutableStateFlow(TimetableTab.HOME_STOP)
        private val day = MutableStateFlow(DaySelection.TODAY)
        private val stationFilter = MutableStateFlow(StationFilter.ALL)

        /** タブを手で選んだら、あとから来る現在地の判定で勝手に動かさない。 */
        private var tabPicked = false

        /** 現在地から初期タブを決めている間は true。決まる（か諦める）まで一覧を出さない。 */
        private val resolving = MutableStateFlow(true)

        init {
            val focus = TimetableFocus.of(savedStateHandle.get<String>(TimetableFocus.ARG))
            if (focus != null) {
                // ホームで区間を指定して来たときは、その駅・バス停のタブで開く
                tabPicked = true
                tab.value = focus.tab
                stationFilter.value = focus.filter
                resolving.value = false
            } else {
                openNearestTab()
            }
        }

        /**
         * 初回表示のタブを現在地の最寄りにする。指定なしで開いたときはこちら。
         *
         * 位置が取れない・通勤圏外・待っても決まらない、のいずれかなら既定（南吉成）のまま出す。
         * 決まるまで一覧を出さない（先に別の駅を見せてから差し替わると読み違える）。
         * 位置は数分キャッシュされるので、ホームから開いた直後はたいてい待ち時間ゼロで決まる。
         */
        private fun openNearestTab() {
            viewModelScope.launch {
                try {
                    withTimeoutOrNull(NEAREST_TIMEOUT_MILLIS) {
                        val here = location.currentFix()?.point ?: return@withTimeoutOrNull
                        val bound = settings.current().commute.boundAt(clock.now().toLocalTime())
                        val place = board.nearest(here, bound) ?: return@withTimeoutOrNull
                        if (tabPicked) return@withTimeoutOrNull
                        tab.value = place.tab()
                        stationFilter.value = place.stationFilter()
                    }
                } finally {
                    resolving.value = false
                }
            }
        }

        /** 「あと n 分」と現在位置のハイライトを最新に保つため、表示中は 30 秒ごとに読み直す。 */
        private val ticker =
            flow {
                while (true) {
                    emit(Unit)
                    delay(TICK_MILLIS)
                }
            }

        val uiState: StateFlow<TimetableUiState> =
            combine(tab, day, stationFilter, resolving, ticker) { t, d, f, waiting, _ -> Query(t, d, f, waiting) }
                .mapLatest { q ->
                    if (q.waiting) {
                        TimetableUiState(tab = q.tab, day = q.day, stationFilter = q.filter)
                    } else {
                        load(q.tab, q.day, q.filter)
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TimetableUiState())

        fun selectTab(newTab: TimetableTab) {
            tabPicked = true
            resolving.value = false
            tab.value = newTab
        }

        fun selectDay(newDay: DaySelection) {
            day.value = newDay
        }

        fun selectStationFilter(filter: StationFilter) {
            stationFilter.value = filter
        }

        private suspend fun load(
            tab: TimetableTab,
            day: DaySelection,
            filter: StationFilter,
        ): TimetableUiState {
            val now = clock.now()
            val today = catalog.resolveDate(now.toLocalDate(), day)
            return TimetableUiState(
                tab = tab,
                day = day,
                stationFilter = filter,
                date = today,
                now = now.toLocalTime(),
                entries = catalog.entries(tab, today, filter),
                loading = false,
            )
        }

        /** 一覧を引くための選択。現在地の判定待ちかどうかも一緒に流す。 */
        private data class Query(
            val tab: TimetableTab,
            val day: DaySelection,
            val filter: StationFilter,
            val waiting: Boolean,
        )

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val TICK_MILLIS = 30_000L

            /** 現在地を待つ上限。これを過ぎたら既定のタブで出す。 */
            const val NEAREST_TIMEOUT_MILLIS = 3_000L
        }
    }
