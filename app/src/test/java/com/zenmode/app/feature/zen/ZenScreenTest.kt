package com.zenmode.app.feature.zen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenmode.app.core.designsystem.ZenModeTheme
import com.zenmode.app.domain.model.TimerSnapshot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ZenScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun runningState(
        remainingSeconds: Long = 5_077L,
        showClock: Boolean = true,
        showDate: Boolean = true,
        showCallButton: Boolean = true,
        showAdminEscape: Boolean = false,
    ) = ZenUiState(
        isLoading = false,
        hasActiveSession = true,
        timer = TimerSnapshot(
            isRunning = true,
            remainingSeconds = remainingSeconds,
            elapsedSeconds = 3_600L - remainingSeconds,
            plannedDurationSeconds = 5_400L,
            progress = 0.2f,
            isExpired = false,
        ),
        clockText = "17:42",
        dateText = "AUG 16, 2026",
        showClock = showClock,
        showDate = showDate,
        showCallButton = showCallButton,
        showAdminEscape = showAdminEscape,
    )

    private fun setContent(
        state: ZenUiState,
        onCall: () -> Unit = {},
        onAdminEscapeRequested: () -> Unit = {},
        onAdminEscapeConfirmed: () -> Unit = {},
        onAdminEscapeDismissed: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZenModeTheme {
                ZenScreen(
                    state = state,
                    onCall = onCall,
                    onAdminEscapeRequested = onAdminEscapeRequested,
                    onAdminEscapeConfirmed = onAdminEscapeConfirmed,
                    onAdminEscapeDismissed = onAdminEscapeDismissed,
                )
            }
        }
    }

    @Test
    fun `the Zen screen shows the time, the date and the remaining countdown`() {
        setContent(runningState())

        composeRule.onNodeWithTag(ZenTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ZenTestTags.CLOCK).assertIsDisplayed()
        composeRule.onNodeWithText("17:42").assertIsDisplayed()
        composeRule.onNodeWithText("AUG 16, 2026").assertIsDisplayed()
        composeRule.onNodeWithText("01:24:37").assertIsDisplayed()
        composeRule.onNodeWithText("Z E N   M O D E").assertIsDisplayed()
    }

    @Test
    fun `the countdown is whatever the domain timer says, formatted`() {
        setContent(runningState(remainingSeconds = 0L))

        composeRule.onNodeWithText("00:00:00").assertIsDisplayed()
    }

    @Test
    fun `the clock and date can be turned off`() {
        setContent(runningState(showClock = false, showDate = false))

        composeRule.onNodeWithTag(ZenTestTags.CLOCK).assertDoesNotExist()
        composeRule.onNodeWithTag(ZenTestTags.DATE).assertDoesNotExist()
        // The countdown itself is never hidden.
        composeRule.onNodeWithTag(ZenTestTags.REMAINING).assertIsDisplayed()
    }

    @Test
    fun `there is no stop control anywhere on the Zen screen`() {
        setContent(runningState())

        // A started session is a commitment: no Stop button, no exit affordance.
        composeRule.onNodeWithText("STOP").assertDoesNotExist()
        composeRule.onNodeWithText("Stop Zen Mode?").assertDoesNotExist()
        composeRule.onNodeWithText("CANCEL").assertDoesNotExist()
        composeRule.onNodeWithText("END SESSION").assertDoesNotExist()
    }

    @Test
    fun `the screen shows only the time, date, countdown, label and call`() {
        setContent(runningState())

        composeRule.onNodeWithTag(ZenTestTags.CLOCK).assertIsDisplayed()
        composeRule.onNodeWithTag(ZenTestTags.DATE).assertIsDisplayed()
        composeRule.onNodeWithTag(ZenTestTags.REMAINING).assertIsDisplayed()
        composeRule.onNodeWithTag(ZenTestTags.LABEL).assertIsDisplayed()
        composeRule.onNodeWithTag(ZenTestTags.CALL).assertIsDisplayed()
    }

    @Test
    fun `the administrative escape is not shown until it is asked for`() {
        setContent(runningState())

        composeRule.onNodeWithText("End this session?").assertDoesNotExist()
    }

    @Test
    fun `the administrative escape confirms before ending anything`() {
        var confirmed = false
        setContent(
            runningState(showAdminEscape = true),
            onAdminEscapeConfirmed = { confirmed = true },
        )

        composeRule.onNodeWithText("End this session?").assertIsDisplayed()
        composeRule
            .onNodeWithText("will not count towards your statistics", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("END SESSION").performClick()

        assertTrue(confirmed)
    }

    @Test
    fun `the administrative escape can be backed out of`() {
        var dismissed = false
        setContent(
            runningState(showAdminEscape = true),
            onAdminEscapeDismissed = { dismissed = true },
        )

        composeRule.onNodeWithText("KEEP GOING").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun `the call action is offered and reports the tap`() {
        var called = false
        setContent(runningState(), onCall = { called = true })

        composeRule.onNodeWithTag(ZenTestTags.CALL).assertIsDisplayed().performClick()

        assertTrue(called)
    }

    @Test
    fun `the call button can be turned off`() {
        setContent(runningState(showCallButton = false))

        composeRule.onNodeWithTag(ZenTestTags.CALL).assertDoesNotExist()
    }

}
