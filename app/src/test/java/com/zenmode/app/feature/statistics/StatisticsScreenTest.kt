package com.zenmode.app.feature.statistics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.zenmode.app.core.designsystem.ZenModeTheme
import com.zenmode.app.domain.model.PeriodStats
import com.zenmode.app.domain.model.StatisticsPeriod
import com.zenmode.app.domain.model.ZenStatistics
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StatisticsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val populated = StatisticsUiState(
        isLoading = false,
        currentStreak = 12,
        bestStreak = 21,
        statistics = ZenStatistics(
            today = PeriodStats(sessionCount = 2, totalFocusSeconds = 5_400L),
            thisWeek = PeriodStats(sessionCount = 8, totalFocusSeconds = 28_800L),
            thisMonth = PeriodStats(sessionCount = 20, totalFocusSeconds = 72_000L),
            allTime = PeriodStats(sessionCount = 47, totalFocusSeconds = 139_320L),
        ),
        selectedPeriod = StatisticsPeriod.ALL_TIME,
    )

    private fun setContent(
        state: StatisticsUiState,
        onSelectPeriod: (StatisticsPeriod) -> Unit = {},
    ) {
        composeRule.setContent {
            ZenModeTheme {
                StatisticsScreen(state = state, onBack = {}, onSelectPeriod = onSelectPeriod)
            }
        }
    }

    @Test
    fun `every figure from the specification is shown`() {
        setContent(populated)

        composeRule.onNodeWithTag(StatisticsTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("12 days").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("21 days").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("38h 42m").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("47").performScrollTo().assertIsDisplayed()
        // 139320 / 47 = 2964 seconds ≈ 49 min
        composeRule.onNodeWithText("49 min").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `all four periods are offered`() {
        setContent(populated)

        StatisticsPeriod.entries.forEach { period ->
            composeRule.onNodeWithTag(StatisticsTestTags.period(period)).assertIsDisplayed()
        }
    }

    @Test
    fun `choosing a period reports the choice`() {
        var chosen: StatisticsPeriod? = null
        setContent(populated, onSelectPeriod = { chosen = it })

        composeRule.onNodeWithTag(StatisticsTestTags.period(StatisticsPeriod.TODAY)).performClick()

        assertEquals(StatisticsPeriod.TODAY, chosen)
    }

    @Test
    fun `the selected period drives the figures shown`() {
        setContent(populated.copy(selectedPeriod = StatisticsPeriod.TODAY))

        composeRule.onNodeWithText("1h 30m").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("2").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `an empty history says so instead of showing a wall of zeroes`() {
        setContent(StatisticsUiState(isLoading = false))

        composeRule.onNodeWithTag(StatisticsTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("No completed sessions yet").assertIsDisplayed()
        composeRule.onNodeWithTag(StatisticsTestTags.TOTAL_FOCUS).assertDoesNotExist()
    }

    @Test
    fun `a streak that has not started reads as none`() {
        setContent(
            populated.copy(
                currentStreak = 0,
                bestStreak = 0,
            ),
        )

        composeRule.onNodeWithTag(StatisticsTestTags.CURRENT_STREAK).assertIsDisplayed()
        // Both the current and the best streak read "None".
        composeRule.onAllNodesWithText("None").assertCountEquals(2)
    }
}
