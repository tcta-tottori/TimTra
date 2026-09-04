package com.kazuya.timtra.ui.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kazuya.timtra.core.data.BusTimetable
import com.kazuya.timtra.data.repository.BusTimetableRepository
import com.kazuya.timtra.data.repository.JrTimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AboutUiState(
    val busFeedVersion: String = "",
    val busPublisher: String = "",
    val busFeedStart: String = "",
    val busFeedEnd: String = "",
    val jrVersion: String = "",
    val jrNote: String = "",
    val appVersion: String = "",
)

@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val bus: BusTimetableRepository,
        private val jr: JrTimetableRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(AboutUiState())
        val uiState: StateFlow<AboutUiState> = _uiState

        init {
            viewModelScope.launch {
                val meta = bus.timetable().meta
                val jrTimetable = jr.timetable()
                val appVersion =
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                        .getOrNull()
                        .orEmpty()
                _uiState.value =
                    AboutUiState(
                        busFeedVersion = meta[BusTimetable.META_FEED_VERSION].orEmpty(),
                        busPublisher = meta[BusTimetable.META_FEED_PUBLISHER_NAME].orEmpty(),
                        busFeedStart = meta[BusTimetable.META_FEED_START_DATE].orEmpty(),
                        busFeedEnd = meta[BusTimetable.META_FEED_END_DATE].orEmpty(),
                        jrVersion = jrTimetable.version,
                        jrNote = jrTimetable.note,
                        appVersion = appVersion,
                    )
            }
        }
    }
