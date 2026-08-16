package com.zenmode.app.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.core.time.ZenClock
import com.zenmode.app.domain.model.SessionFilter
import com.zenmode.app.domain.model.SessionHistoryGroup
import com.zenmode.app.domain.usecase.GetSessionHistoryUseCase
import com.zenmode.app.domain.usecase.GetSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

data class HistoryUiState(
    val isLoading: Boolean = true,
    val filter: SessionFilter = SessionFilter.ALL,
    val groups: List<SessionHistoryGroup> = emptyList(),
    val use24HourClock: Boolean = true,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getSessionHistory: GetSessionHistoryUseCase,
    getSettings: GetSettingsUseCase,
    clock: ZenClock,
) : ViewModel() {

    private val filter = MutableStateFlow(SessionFilter.ALL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HistoryUiState> = combine(
        filter.flatMapLatest { getSessionHistory(it) },
        filter,
        getSettings(),
    ) { groups, currentFilter, settings ->
        HistoryUiState(
            isLoading = false,
            filter = currentFilter,
            groups = groups,
            use24HourClock = settings.use24HourClock,
            zone = clock.zone(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HistoryUiState(zone = clock.zone()),
    )

    fun selectFilter(filter: SessionFilter) {
        this.filter.value = filter
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
