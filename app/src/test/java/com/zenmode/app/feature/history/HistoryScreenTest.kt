package com.zenmode.app.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenmode.app.core.designsystem.ZenModeTheme
import com.zenmode.app.domain.model.RelativeDay
import com.zenmode.app.domain.model.SessionFilter
import com.zenmode.app.domain.model.SessionHistoryGroup
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 8, 16)

    private fun at(date: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    private val groups = listOf(
        SessionHistoryGroup(
            date = today,
            relativeDay = RelativeDay.TODAY,
            sessions = listOf(
                ZenSession(
                    id = 1,
                    startedAt = at(today, 17, 0),
                    endedAt = at(today, 18, 0),
                    plannedDurationSeconds = 3_600L,
                    actualDurationSeconds = 3_600L,
                    status = SessionStatus.COMPLETED,
                    blockedAppCount = 5,
                ),
            ),
        ),
        SessionHistoryGroup(
            date = today.minusDays(3),
            relativeDay = RelativeDay.EARLIER,
            sessions = listOf(
                ZenSession(
                    id = 2,
                    startedAt = at(today.minusDays(3), 14, 20),
                    endedAt = at(today.minusDays(3), 14, 31),
                    plannedDurationSeconds = 1_500L,
                    actualDurationSeconds = 660L,
                    status = SessionStatus.CANCELLED,
                    blockedAppCount = 5,
                ),
            ),
        ),
    )

    private fun setContent(
        state: HistoryUiState,
        onSelectFilter: (SessionFilter) -> Unit = {},
    ) {
        composeRule.setContent {
            ZenModeTheme {
                HistoryScreen(state = state, onBack = {}, onSelectFilter = onSelectFilter)
            }
        }
    }

    @Test
    fun `sessions are listed under their day with times and outcome`() {
        setContent(HistoryUiState(isLoading = false, groups = groups, zone = zone))

        composeRule.onNodeWithTag(HistoryTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("TODAY").assertIsDisplayed()
        composeRule.onNodeWithText("1h").assertIsDisplayed()
        composeRule.onNodeWithText("Completed").assertIsDisplayed()
        composeRule.onNodeWithText("17:00 → 18:00").assertIsDisplayed()
    }

    @Test
    fun `older days are headed by their date`() {
        setContent(HistoryUiState(isLoading = false, groups = groups, zone = zone))

        composeRule.onNodeWithText("AUG 13").assertIsDisplayed()
        composeRule.onNodeWithText("Cancelled").assertIsDisplayed()
    }

    @Test
    fun `a cancelled session shows how long it actually ran`() {
        setContent(HistoryUiState(isLoading = false, groups = groups, zone = zone))

        composeRule.onNodeWithText("25 min").assertIsDisplayed()
        composeRule.onNodeWithText("11 min focused").assertIsDisplayed()
    }

    @Test
    fun `all three filters are offered`() {
        setContent(HistoryUiState(isLoading = false, groups = groups, zone = zone))

        SessionFilter.entries.forEach { filter ->
            composeRule.onNodeWithTag(HistoryTestTags.filter(filter)).assertIsDisplayed()
        }
    }

    @Test
    fun `choosing a filter reports the choice`() {
        var chosen: SessionFilter? = null
        setContent(
            HistoryUiState(isLoading = false, groups = groups, zone = zone),
            onSelectFilter = { chosen = it },
        )

        composeRule.onNodeWithTag(HistoryTestTags.filter(SessionFilter.CANCELLED)).performClick()

        assertEquals(SessionFilter.CANCELLED, chosen)
    }

    @Test
    fun `an empty history reads clearly`() {
        setContent(HistoryUiState(isLoading = false, groups = emptyList(), zone = zone))

        composeRule.onNodeWithTag(HistoryTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("No sessions yet").assertIsDisplayed()
    }

    @Test
    fun `an empty filter result explains which filter is empty`() {
        setContent(
            HistoryUiState(
                isLoading = false,
                groups = emptyList(),
                filter = SessionFilter.CANCELLED,
                zone = zone,
            ),
        )

        composeRule.onNodeWithText("No cancelled sessions").assertIsDisplayed()
    }

    @Test
    fun `the 12-hour setting is respected`() {
        setContent(
            HistoryUiState(
                isLoading = false,
                groups = groups,
                zone = zone,
                use24HourClock = false,
            ),
        )

        composeRule.onNodeWithText("5:00 PM → 6:00 PM").assertIsDisplayed()
    }
}
