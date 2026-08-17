package com.zenmode.app.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.zenmode.app.core.designsystem.ZenModeTheme
import com.zenmode.app.domain.model.LockWallpaperCapability
import com.zenmode.app.domain.model.WallpaperSettings
import com.zenmode.app.domain.model.ZenSettings
import com.zenmode.app.system.LockdownCapability
import com.zenmode.app.system.launcher.DefaultLauncherState
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
        var setDefaultLauncher = false
        var choseHomeWallpaper = false
        var clearedHomeWallpaper = false
        var choseLockWallpaper = false
        var clearedLockWallpaper = false
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
                    onSetDefaultLauncher = { recorder.setDefaultLauncher = true },
                    onChooseHomeWallpaper = { recorder.choseHomeWallpaper = true },
                    onClearHomeWallpaper = { recorder.clearedHomeWallpaper = true },
                    onChooseLockWallpaper = { recorder.choseLockWallpaper = true },
                    onClearLockWallpaper = { recorder.clearedLockWallpaper = true },
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

        // "Off" now labels several rows, so assert on the accessibility row itself.
        composeRule
            .onNodeWithTag(SettingsTestTags.ACCESSIBILITY)
            .performScrollTo()
            .assertIsDisplayed()
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
    fun `the launcher row reports the real home-app state`() {
        setContent(defaults.copy(defaultLauncherState = DefaultLauncherState.OTHER_LAUNCHER))

        composeRule
            .onNodeWithText("Another launcher is your home screen", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the launcher row never claims the app can set itself as default`() {
        val recorder = setContent(
            defaults.copy(defaultLauncherState = DefaultLauncherState.NOT_CHOSEN),
        )

        composeRule
            .onNodeWithText("Android asks each time", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag(SettingsTestTags.DEFAULT_LAUNCHER)
            .performScrollTo()
            .performClick()

        // Tapping only opens Android's own picker; the choice is the user's.
        assertTrue(recorder.setDefaultLauncher)
    }

    @Test
    fun `the gesture section states what each gesture does`() {
        setContent(defaults)

        composeRule
            .onNodeWithTag(SettingsTestTags.GESTURE_SWIPE_UP)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("The APPS button does the same thing.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the gesture section promises nothing about Android's own gestures`() {
        setContent(defaults)

        composeRule
            .onNodeWithText("Back, Home and Recents gestures are untouched", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `no wallpaper reads as a black background, not as an error`() {
        setContent(defaults)

        composeRule
            .onNodeWithText("No wallpaper selected", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `choosing a home wallpaper opens the picker`() {
        val recorder = setContent(defaults)

        composeRule.onNodeWithTag(SettingsTestTags.HOME_WALLPAPER).performScrollTo().performClick()

        assertTrue(recorder.choseHomeWallpaper)
    }

    @Test
    fun `turning the home wallpaper off is not offered when none is set`() {
        setContent(defaults)

        composeRule.onNodeWithTag(SettingsTestTags.HOME_WALLPAPER_OFF).assertDoesNotExist()
    }

    @Test
    fun `turning the home wallpaper off is offered once one is set`() {
        setContent(
            defaults.copy(
                wallpaper = WallpaperSettings(homeEnabled = true, homeUri = "content://image"),
            ),
        )

        composeRule
            .onNodeWithTag(SettingsTestTags.HOME_WALLPAPER_OFF)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the lock wallpaper warns that it is a system-wide change`() {
        setContent(defaults)

        composeRule
            .onNodeWithText("system-wide change that replaces your current one", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the lock wallpaper says the old one cannot be restored`() {
        setContent(
            defaults.copy(
                wallpaper = WallpaperSettings(lockEnabled = true, lockUri = "content://image"),
            ),
        )

        composeRule
            .onNodeWithText("cannot put back whatever lock wallpaper you had before", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `a device that cannot change the lock wallpaper says so and offers nothing`() {
        val recorder = setContent(
            defaults.copy(lockWallpaperCapability = LockWallpaperCapability.UNSUPPORTED),
        )

        composeRule
            .onNodeWithText("does not allow apps to change the lock-screen wallpaper", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.LOCK_WALLPAPER).performScrollTo().performClick()

        // Tapping an unavailable capability must not open a picker.
        assertFalse(recorder.choseLockWallpaper)
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
