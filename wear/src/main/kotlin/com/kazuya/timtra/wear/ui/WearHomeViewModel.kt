package com.kazuya.timtra.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.data.repository.SettingsRepository
import com.kazuya.timtra.wear.WearJourneyProvider
import com.kazuya.timtra.wear.WearSnapshot
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
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WearHomeViewModel
    @Inject
    constructor(
        private val provider: WearJourneyProvider,
        settings: SettingsRepository,
    ) : ViewModel() {
        private val manualBound = MutableStateFlow<Bound?>(null)

        private val ticker =
            flow {
                while (true) {
                    emit(Unit)
                    delay(TICK_MILLIS)
                }
            }

        /** null は計算中。 */
        val snapshot: StateFlow<WearSnapshot?> =
            combine(ticker, settings.settings, manualBound) { _, _, manual -> manual }
                .mapLatest { manual -> provider.snapshot(manual) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

        val isManualBound: Boolean get() = manualBound.value != null

        fun toggleBound(current: Bound) {
            manualBound.value = if (current == Bound.OUTBOUND) Bound.INBOUND else Bound.OUTBOUND
        }

        fun resetBound() {
            manualBound.value = null
        }

        private companion object {
            const val TICK_MILLIS = 30_000L
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
