package com.kazuya.timtra.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.core.board.BoardPlace
import com.kazuya.timtra.wear.board.BoardSnapshot
import com.kazuya.timtra.wear.board.DayBoard
import com.kazuya.timtra.wear.board.DaySelection
import com.kazuya.timtra.wear.board.PlaceDistance
import com.kazuya.timtra.wear.board.PlaceResolution
import com.kazuya.timtra.wear.board.WearBoardProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 時計の画面。ホームは 1 画面に収め、時刻表とメニューは一覧で開く。 */
enum class BoardStep {
    /** 現在地を取って地点を決めている最中 */
    LOADING,

    /** ホーム（1 画面） */
    HOME,

    /** この先の発車（現在時刻〜当日終電） */
    UPCOMING,

    /** 時刻表（その地点の 1 日分。今日 / 平日 / 土日祝で切り替え） */
    TIMETABLE,

    /** メニュー（駅・バス停の一覧） */
    MENU,
}

data class BoardUiState(
    val step: BoardStep = BoardStep.LOADING,
    val resolution: PlaceResolution? = null,
    /** ホームの内容。 */
    val snapshot: BoardSnapshot? = null,
    /** 一覧（この先の発車 / 時刻表）の内容。 */
    val dayBoard: DayBoard? = null,
    /** メニューに並べる地点。 */
    val places: List<PlaceDistance> = emptyList(),
    /** ホームに固定している地点。null は「現在地から自動」。 */
    val pinned: BoardPlace? = null,
    val locationPermitted: Boolean = false,
)

@HiltViewModel
class WearBoardViewModel
    @Inject
    constructor(
        private val provider: WearBoardProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow(BoardUiState())
        val state: StateFlow<BoardUiState> = _state.asStateFlow()

        init {
            start()
            // 開いている間だけ、残り時間と次の便を定期的に引き直す
            viewModelScope.launch {
                while (true) {
                    delay(TICK_MILLIS)
                    refreshCurrent()
                }
            }
        }

        /** 最初から: 固定した地点があればそれ、無ければ現在地から最寄りを決めてホームを出す。 */
        fun start() {
            viewModelScope.launch {
                _state.value = BoardUiState(step = BoardStep.LOADING, locationPermitted = provider.locationPermitted)
                _state.value = home(provider.resolvePlace())
            }
        }

        /** ホームの発時刻をタップ / リューズ時計回り: 現在時刻から当日終電までを開く。 */
        fun openUpcoming(place: BoardPlace? = null) {
            val target = place ?: currentPlace() ?: return
            viewModelScope.launch {
                _state.value =
                    _state.value.copy(
                        step = BoardStep.UPCOMING,
                        dayBoard = provider.dayBoard(target, DaySelection.TODAY, onlyUpcoming = true),
                    )
            }
        }

        /** 「時刻表を表示する」/ メニューの地点タップ: 1 日分の時刻表を開く。 */
        fun openTimetable(
            place: BoardPlace? = null,
            selection: DaySelection = DaySelection.TODAY,
        ) {
            val target = place ?: currentPlace() ?: return
            viewModelScope.launch {
                _state.value =
                    _state.value.copy(
                        step = BoardStep.TIMETABLE,
                        dayBoard = provider.dayBoard(target, selection),
                    )
            }
        }

        /** 時刻表の 今日 / 平日 / 土日祝 の切り替え。 */
        fun selectDay(selection: DaySelection) {
            val place = _state.value.dayBoard?.place ?: return
            openTimetable(place, selection)
        }

        /** ホームから下方向スワイプ / リューズ反時計回り: メニューを開く。 */
        fun openMenu() {
            viewModelScope.launch {
                _state.value =
                    _state.value.copy(
                        step = BoardStep.MENU,
                        places = provider.nearby(),
                        pinned = provider.manualPlace(),
                    )
            }
        }

        /** 表示中の地点をホームに固定する（タイルにも効く）。同じ地点をもう一度押すと自動に戻す。 */
        fun togglePinned(place: BoardPlace) {
            viewModelScope.launch {
                val next = if (provider.manualPlace() == place) null else place
                provider.selectPlace(next)
                _state.value = home(provider.resolvePlace())
            }
        }

        /** ホームへ戻る（スワイプで戻る / 戻るボタン）。 */
        fun backHome() {
            val current = _state.value
            if (current.snapshot == null) start() else _state.value = current.copy(step = BoardStep.HOME)
        }

        /** 一覧を開くときの対象地点。一覧を開いていればその地点、そうでなければホームの地点。 */
        private fun currentPlace(): BoardPlace? = _state.value.dayBoard?.place ?: _state.value.resolution?.place

        private suspend fun home(resolution: PlaceResolution): BoardUiState =
            BoardUiState(
                step = BoardStep.HOME,
                resolution = resolution,
                snapshot = provider.board(resolution.place, resolution.basis, HOME_LIMIT),
                pinned = provider.manualPlace(),
                locationPermitted = provider.locationPermitted,
            )

        /** 画面はそのままに、中身だけ最新にする。 */
        private suspend fun refreshCurrent() {
            val current = _state.value
            when (current.step) {
                BoardStep.HOME -> {
                    val resolution = current.resolution ?: return
                    _state.value = current.copy(snapshot = provider.board(resolution.place, resolution.basis, HOME_LIMIT))
                }
                BoardStep.UPCOMING, BoardStep.TIMETABLE -> {
                    val board = current.dayBoard ?: return
                    _state.value = current.copy(dayBoard = provider.dayBoard(board.place, board.selection, board.onlyUpcoming))
                }
                BoardStep.LOADING, BoardStep.MENU -> Unit
            }
        }

        private companion object {
            const val TICK_MILLIS = 30_000L

            /** ホームは次の 1 本しか使わないが、終電後の判定のために少し多めに引く。 */
            const val HOME_LIMIT = 2
        }
    }
