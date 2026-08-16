package com.zenmode.app.feature.timer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.zenmode.app.core.designsystem.ZenModeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TimerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: TimerUiState,
        onSelectPreset: (Int) -> Unit = {},
        onSelectCustom: () -> Unit = {},
        onCustomHoursChange: (Int) -> Unit = {},
        onCustomMinutesChange: (Int) -> Unit = {},
        onStart: () -> Unit = {},
        onStartConfirmed: () -> Unit = {},
        onStartDismissed: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZenModeTheme {
                TimerScreen(
                    state = state,
                    onBack = onBack,
                    onSelectPreset = onSelectPreset,
                    onSelectCustom = onSelectCustom,
                    onCustomHoursChange = onCustomHoursChange,
                    onCustomMinutesChange = onCustomMinutesChange,
                    onStart = onStart,
                    onStartConfirmed = onStartConfirmed,
                    onStartDismissed = onStartDismissed,
                )
            }
        }
    }

    @Test
    fun `every duration in the specification is offered`() {
        setContent(TimerUiState())

        listOf(15, 25, 45, 60, 90, 120).forEach { minutes ->
            composeRule.onNodeWithTag(TimerTestTags.preset(minutes)).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(TimerTestTags.CUSTOM_BUTTON).assertIsDisplayed()
    }

    @Test
    fun `choosing a preset reports the choice`() {
        var chosen = 0
        setContent(TimerUiState(), onSelectPreset = { chosen = it })

        composeRule.onNodeWithTag(TimerTestTags.preset(90)).performClick()

        assertEquals(90, chosen)
    }

    @Test
    fun `the custom picker only appears once custom is chosen`() {
        setContent(TimerUiState(isCustom = false, selectedPresetMinutes = 25))

        composeRule.onNodeWithTag(TimerTestTags.HOURS_PLUS).assertDoesNotExist()
    }

    @Test
    fun `the custom picker adjusts hours and minutes`() {
        var hours = -1
        var minutes = -1
        setContent(
            TimerUiState(isCustom = true, customHours = 1, customMinutes = 30),
            onCustomHoursChange = { hours = it },
            onCustomMinutesChange = { minutes = it },
        )

        composeRule.onNodeWithTag(TimerTestTags.HOURS_PLUS).performScrollTo().performClick()
        assertEquals(2, hours)

        composeRule.onNodeWithTag(TimerTestTags.MINUTES_MINUS).performScrollTo().performClick()
        assertEquals(25, minutes)
    }

    @Test
    fun `the chosen custom duration is shown back to the user`() {
        setContent(TimerUiState(isCustom = true, customHours = 1, customMinutes = 30))

        // "1 HR 30 MIN" also labels the 90-minute preset, so assert on the
        // custom read-out itself rather than on the text alone.
        composeRule
            .onNodeWithTag(TimerTestTags.CUSTOM_TOTAL)
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextEquals("1 HR 30 MIN")
    }

    @Test
    fun `a zero duration cannot be started and says why`() {
        setContent(TimerUiState(isCustom = true, customHours = 0, customMinutes = 0))

        composeRule.onNodeWithTag(TimerTestTags.VALIDATION).performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText("Choose a duration longer than zero.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TimerTestTags.START_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `an over-long duration cannot be started and says why`() {
        setContent(TimerUiState(isCustom = true, customHours = 13, customMinutes = 0))

        composeRule
            .onNodeWithText("The longest session is 12 hours.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TimerTestTags.START_BUTTON).assertIsNotEnabled()
    }

    @Test
    fun `a valid duration shows no complaint and can be started`() {
        var started = false
        setContent(
            TimerUiState(isCustom = false, selectedPresetMinutes = 45),
            onStart = { started = true },
        )

        composeRule.onNodeWithTag(TimerTestTags.VALIDATION).assertDoesNotExist()
        composeRule
            .onNodeWithTag(TimerTestTags.START_BUTTON)
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        assertTrue(started)
    }

    @Test
    fun `the confirmation can be accepted`() {
        var confirmed = false
        setContent(
            TimerUiState(selectedPresetMinutes = 60, showStartConfirmation = true),
            onStartConfirmed = { confirmed = true },
        )

        composeRule.onNodeWithText("Start Zen Mode?").assertIsDisplayed()
        composeRule.onNodeWithText("START").performClick()

        assertTrue(confirmed)
    }

    @Test
    fun `the screen warns up front when the finish may be delayed`() {
        setContent(TimerUiState(exactAlarmsAvailable = false))

        composeRule
            .onNodeWithTag(TimerTestTags.ALARM_WARNING)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `no delay warning is shown when exact alarms are available`() {
        setContent(TimerUiState(exactAlarmsAvailable = true))

        composeRule.onNodeWithTag(TimerTestTags.ALARM_WARNING).assertDoesNotExist()
    }

    @Test
    fun `the confirmation repeats the timing caveat`() {
        setContent(
            TimerUiState(
                selectedPresetMinutes = 25,
                exactAlarmsAvailable = false,
                showStartConfirmation = true,
            ),
        )

        // The caveat is on the screen and repeated in the dialog; the dialog is
        // drawn last.
        composeRule
            .onAllNodesWithText("Android may delay the end of this session", substring = true)
            .assertCountEquals(2)
        composeRule
            .onAllNodesWithText("Android may delay the end of this session", substring = true)
            .onLast()
            .assertIsDisplayed()
    }

    @Test
    fun `the screen states plainly that calls stay available`() {
        setContent(TimerUiState())

        composeRule
            .onNodeWithText("Calls stay available during a session.")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
