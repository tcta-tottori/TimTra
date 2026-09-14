package com.kazuya.timtra.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.core.board.BoardPlace
import com.kazuya.timtra.wear.board.BoardSnapshot
import com.kazuya.timtra.wear.board.PlaceBasis
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

/** 発車標の画面遷移。添付デザインの流れに合わせている。 */
enum class BoardStep {
    /** 現在地を取得中 */
    LOCATING,

    /** 最寄りが見つかったので「この地点で表示」を確認 */
    CONFIRM,

    /** 発車標 */
    BOARD,

    /** 駅・バス停の選択 */
    PICKER,
}

data class BoardUiState(
    val step: BoardStep = BoardStep.LOCATING,
    val resolution: PlaceResolution? = null,
    val snapshot: BoardSnapshot? = null,
    val nearby: List<PlaceDistance> = emptyList(),
    val manual: BoardPlace? = null,
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
            // 画面を開いている間だけ、残り時間と次の便を定期的に引き直す
            viewModelScope.launch {
                while (true) {
                    delay(TICK_MILLIS)
                    val current = _state.value
                    val place = current.snapshot?.place
                    if (current.step == BoardStep.BOARD && place != null) {
                        _state.value = current.copy(snapshot = provider.board(place, current.snapshot.basis, LIMIT))
                    }
                }
            }
        }

        /** 最初から: 手動選択があればそれ、無ければ現在地を取りにいく。 */
        fun start() {
            viewModelScope.launch {
                _state.value = BoardUiState(step = BoardStep.LOCATING, locationPermitted = provider.locationPermitted)
                val resolution = provider.resolvePlace()
                _state.value =
                    when (resolution.basis) {
                        // 手動で選んである、または位置が取れなかった → そのまま出す
                        PlaceBasis.MANUAL -> ready(resolution)
                        PlaceBasis.TIME_OF_DAY -> ready(resolution)
                        // 最寄りが見つかった → 「この地点で表示」を挟む（デザインの 2 画面目）
                        PlaceBasis.NEAR_HERE ->
                            BoardUiState(
                                step = BoardStep.CONFIRM,
                                resolution = resolution,
                                manual = provider.manualPlace(),
                                locationPermitted = provider.locationPermitted,
                            )
                    }
            }
        }

        /** 「この地点で表示」。手動選択としては覚えない（次に開いたらまた現在地から選ぶ）。 */
        fun confirm() {
            val resolution = _state.value.resolution ?: return
            viewModelScope.launch { _state.value = ready(resolution) }
        }

        /** 駅・バス停の選択を開く。 */
        fun openPicker() {
            viewModelScope.launch {
                _state.value =
                    _state.value.copy(
                        step = BoardStep.PICKER,
                        nearby = provider.nearby(),
                        manual = provider.manualPlace(),
                    )
            }
        }

        /** 地点を手で選ぶ。以後はこの地点で固定される（タイルにも効く）。 */
        fun pick(place: BoardPlace) {
            viewModelScope.launch {
                provider.selectPlace(place)
                _state.value = ready(PlaceResolution(place, PlaceBasis.MANUAL, null))
            }
        }

        /** 「現在地から自動」に戻す。 */
        fun useLocation() {
            viewModelScope.launch {
                provider.selectPlace(null)
                start()
            }
        }

        /** 発車標に戻る（選択画面・確認画面の「戻る」）。 */
        fun closePicker() {
            val snapshot = _state.value.snapshot
            if (snapshot == null) start() else _state.value = _state.value.copy(step = BoardStep.BOARD)
        }

        private suspend fun ready(resolution: PlaceResolution): BoardUiState =
            BoardUiState(
                step = BoardStep.BOARD,
                resolution = resolution,
                snapshot = provider.board(resolution.place, resolution.basis, LIMIT),
                manual = provider.manualPlace(),
                locationPermitted = provider.locationPermitted,
            )

        private companion object {
            const val TICK_MILLIS = 30_000L

            /** 画面はスクロールできるので、タイルより多めに出す。 */
            const val LIMIT = 8
        }
    }
