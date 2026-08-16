package com.zenmode.app.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenEmptyState
import com.zenmode.app.core.designsystem.ZenHorizontalDivider
import com.zenmode.app.core.designsystem.ZenSecondaryButton
import com.zenmode.app.core.designsystem.ZenSectionHeader
import com.zenmode.app.core.designsystem.ZenStatBlock
import com.zenmode.app.core.designsystem.ZenTopBar
import com.zenmode.app.core.time.DurationFormat
import com.zenmode.app.domain.model.StatisticsPeriod

object StatisticsTestTags {
    const val SCREEN = "statistics_screen"
    const val CURRENT_STREAK = "statistics_current_streak"
    const val BEST_STREAK = "statistics_best_streak"
    const val TOTAL_FOCUS = "statistics_total_focus"
    const val SESSIONS = "statistics_sessions"
    const val AVERAGE = "statistics_average"
    const val EMPTY = "statistics_empty"
    fun period(period: StatisticsPeriod) = "statistics_period_${period.name}"
}

@Composable
fun StatisticsRoute(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatisticsScreen(state = state, onBack = onBack, onSelectPeriod = viewModel::selectPeriod)
}

@Composable
fun StatisticsScreen(
    state: StatisticsUiState,
    onBack: () -> Unit,
    onSelectPeriod: (StatisticsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag(StatisticsTestTags.SCREEN),
    ) {
        ZenTopBar(title = "Focus stats", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            if (state.isEmpty) {
                ZenEmptyState(
                    title = "No completed sessions yet",
                    description = "Finish a Zen session and your focus time will show up here.",
                    modifier = Modifier.testTag(StatisticsTestTags.EMPTY),
                )
                return@Column
            }

            ZenStatBlock(
                label = "Current streak",
                value = days(state.currentStreak),
                modifier = Modifier.testTag(StatisticsTestTags.CURRENT_STREAK),
            )
            ZenStatBlock(
                label = "Best streak",
                value = days(state.bestStreak),
                modifier = Modifier.testTag(StatisticsTestTags.BEST_STREAK),
            )

            ZenHorizontalDivider()
            Spacer(Modifier.height(16.dp))
            ZenSectionHeader("Period")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatisticsPeriod.entries.forEach { period ->
                    ZenSecondaryButton(
                        text = period.label(),
                        onClick = { onSelectPeriod(period) },
                        selected = period == state.selectedPeriod,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(StatisticsTestTags.period(period)),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            ZenStatBlock(
                label = "Total focus",
                value = DurationFormat.total(state.selected.totalFocusSeconds),
                modifier = Modifier.testTag(StatisticsTestTags.TOTAL_FOCUS),
            )
            ZenStatBlock(
                label = "Sessions completed",
                value = state.selected.sessionCount.toString(),
                modifier = Modifier.testTag(StatisticsTestTags.SESSIONS),
            )
            ZenStatBlock(
                label = "Average session",
                value = DurationFormat.sessionLength(state.selected.averageSessionSeconds),
                modifier = Modifier.testTag(StatisticsTestTags.AVERAGE),
            )

            Spacer(Modifier.height(48.dp))
        }
    }
}

private fun days(count: Int): String = when (count) {
    0 -> "None"
    1 -> "1 day"
    else -> "$count days"
}

private fun StatisticsPeriod.label(): String = when (this) {
    StatisticsPeriod.TODAY -> "TODAY"
    StatisticsPeriod.THIS_WEEK -> "WEEK"
    StatisticsPeriod.THIS_MONTH -> "MONTH"
    StatisticsPeriod.ALL_TIME -> "ALL"
}
