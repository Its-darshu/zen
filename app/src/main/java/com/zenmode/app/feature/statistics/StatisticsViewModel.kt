package com.zenmode.app.feature.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.domain.model.PeriodStats
import com.zenmode.app.domain.model.StatisticsPeriod
import com.zenmode.app.domain.model.ZenStatistics
import com.zenmode.app.domain.model.forPeriod
import com.zenmode.app.domain.usecase.GetStatisticsUseCase
import com.zenmode.app.domain.usecase.GetStreakUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatisticsUiState(
    val isLoading: Boolean = true,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val statistics: ZenStatistics = ZenStatistics.Empty,
    val selectedPeriod: StatisticsPeriod = StatisticsPeriod.ALL_TIME,
) {
    val selected: PeriodStats get() = statistics.forPeriod(selectedPeriod)

    val isEmpty: Boolean get() = statistics.allTime.isEmpty
}

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    getStatistics: GetStatisticsUseCase,
    getStreak: GetStreakUseCase,
) : ViewModel() {

    private val selectedPeriod = MutableStateFlow(StatisticsPeriod.ALL_TIME)

    val uiState: StateFlow<StatisticsUiState> = combine(
        getStatistics(),
        getStreak(),
        selectedPeriod,
    ) { statistics, streak, period ->
        StatisticsUiState(
            isLoading = false,
            currentStreak = streak.currentStreak,
            bestStreak = streak.bestStreak,
            statistics = statistics,
            selectedPeriod = period,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StatisticsUiState(),
    )

    fun selectPeriod(period: StatisticsPeriod) {
        selectedPeriod.value = period
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
