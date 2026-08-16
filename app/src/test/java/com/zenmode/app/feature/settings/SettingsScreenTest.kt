package com.zenmode.app.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.zenmode.app.core.designsystem.ZenModeTheme
import com.zenmode.app.domain.model.ZenSettings
import com.zenmode.app.system.LockdownCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Recorder {
        var confirmStart: Boolean? = null
        var completionNotification: Boolean? = null
        var pureBlack: Boolean? = null
        var showClock: Boolean? = null
        var showDate: Boolean? = null
        var use24Hour: Boolean? = null
        var callButton: Boolean? = null
        var strictMode: Boolean? = null
        var defaultDuration: Int? = null
        var clearHistoryRequested = false
        var clearHistoryConfirmed = false
        var openedBlockedApps = false
        var openedPermissions = false
    }

    private fun setContent(
        state: SettingsUiState,
        recorder: Recorder = Recorder(),
    ): Recorder {
        composeRule.setContent {
            ZenModeTheme {
                SettingsScreen(
                    state = state,
                    onBack = {},
                    onOpenBlockedApps = { recorder.openedBlockedApps = true },
                    onOpenPermissions = { recorder.openedPermissions = true },
                    onSelectDefaultDuration = { recorder.defaultDuration = it },
                    onConfirmStartChange = { recorder.confirmStart = it },
                    onCompletionNotificationChange = { recorder.completionNotification = it },
                    onPureBlackChange = { recorder.pureBlack = it },
                    onShowClockChange = { recorder.showClock = it },
                    onShowDateChange = { recorder.showDate = it },
                    onUse24HourChange = { recorder.use24Hour = it },
                    onCallButtonChange = { recorder.callButton = it },
                    onStrictModeChange = { recorder.strictMode = it },
                    onClearHistory = { recorder.clearHistoryRequested = true },
                    onClearHistoryConfirmed = { recorder.clearHistoryConfirmed = true },
                    onClearHistoryDismissed = {},
                )
            }
        }
        return recorder
    }

    private val defaults = SettingsUiState(
        isLoading = false,
        settings = ZenSettings(),
        blockedAppCount = 5,
        accessibilityEnabled = true,
    )

    @Test
    fun `every setting from the specification is present`() {
        setContent(defaults)

        listOf(
            SettingsTestTags.BLOCKED_APPS,
            SettingsTestTags.CONFIRM_START,
            SettingsTestTags.COMPLETION_NOTIFICATION,
            SettingsTestTags.PURE_BLACK,
            SettingsTestTags.SHOW_CLOCK,
            SettingsTestTags.SHOW_DATE,
            SettingsTestTags.USE_24_HOUR,
            SettingsTestTags.CALL_BUTTON,
            SettingsTestTags.STRICT_MODE,
            SettingsTestTags.ACCESSIBILITY,
            SettingsTestTags.CLEAR_HISTORY,
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun `toggling a switch reports the new value`() {
        val recorder = setContent(defaults)

        composeRule.onNodeWithTag(SettingsTestTags.SHOW_CLOCK).performScrollTo().performClick()

        assertEquals(false, recorder.showClock)
    }

    @Test
    fun `a switch that is off toggles on`() {
        val recorder = setContent(
            defaults.copy(settings = ZenSettings(use24HourClock = false)),
        )

        composeRule.onNodeWithTag(SettingsTestTags.USE_24_HOUR).performScrollTo().performClick()

        assertEquals(true, recorder.use24Hour)
    }

    @Test
    fun `each behaviour toggle is wired to its own setting`() {
        val recorder = setContent(defaults)

        composeRule.onNodeWithTag(SettingsTestTags.CONFIRM_START).performScrollTo().performClick()
        composeRule.onNodeWithTag(SettingsTestTags.STRICT_MODE).performScrollTo().performClick()
        composeRule.onNodeWithTag(SettingsTestTags.CALL_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(SettingsTestTags.PURE_BLACK).performScrollTo().performClick()
        composeRule
            .onNodeWithTag(SettingsTestTags.COMPLETION_NOTIFICATION)
            .performScrollTo()
            .performClick()

        assertEquals(false, recorder.confirmStart)
        assertEquals(true, recorder.strictMode)
        assertEquals(false, recorder.callButton)
        assertEquals(false, recorder.pureBlack)
        assertEquals(false, recorder.completionNotification)
    }

    @Test
    fun `the default duration can be changed`() {
        val recorder = setContent(defaults)

        composeRule.onNodeWithTag(SettingsTestTags.duration(45)).performScrollTo().performClick()

        assertEquals(45, recorder.defaultDuration)
    }

    @Test
    fun `the blocked app count is shown and the screen is reachable`() {
        val recorder = setContent(defaults)

        composeRule.onNodeWithText("5 selected").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.BLOCKED_APPS).performScrollTo().performClick()

        assertTrue(recorder.openedBlockedApps)
    }

    @Test
    fun `the accessibility row reports the real state`() {
        setContent(defaults.copy(accessibilityEnabled = false))

        composeRule.onNodeWithText("Off").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText("Blocking is inactive", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `strict mode says what this device can actually enforce`() {
        setContent(defaults.copy(lockdownCapability = LockdownCapability.SCREEN_PINNING))

        // Screen pinning must not be described as a kiosk: Android keeps its
        // own escape and the lock screen still appears.
        composeRule
            .onNodeWithText("hold Back and", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `a provisioned device is described as the stronger thing it is`() {
        setContent(defaults.copy(lockdownCapability = LockdownCapability.KIOSK))

        composeRule
            .onNodeWithText("dedicated device", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `clearing history asks first`() {
        val recorder = setContent(defaults)

        composeRule.onNodeWithTag(SettingsTestTags.CLEAR_HISTORY).performScrollTo().performClick()

        assertTrue(recorder.clearHistoryRequested)
        assertFalse(recorder.clearHistoryConfirmed)
    }

    @Test
    fun `the clear-history confirmation is explicit about what goes`() {
        val recorder = setContent(defaults.copy(showClearHistoryConfirmation = true))

        composeRule.onNodeWithText("Clear history?").assertIsDisplayed()
        composeRule
            .onNodeWithText("statistics and streaks", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("DELETE").performClick()

        assertTrue(recorder.clearHistoryConfirmed)
    }
}
