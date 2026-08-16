package com.zenmode.app.feature.home

import androidx.compose.ui.test.assertIsDisplayed
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
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: HomeUiState,
        onSelectDuration: (Int) -> Unit = {},
        onStart: () -> Unit = {},
        onStartConfirmed: () -> Unit = {},
        onStartDismissed: () -> Unit = {},
        onOpenTimer: () -> Unit = {},
        onOpenStatistics: () -> Unit = {},
        onOpenHistory: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenPermissions: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZenModeTheme {
                HomeScreen(
                    state = state,
                    onSelectDuration = onSelectDuration,
                    onStart = onStart,
                    onStartConfirmed = onStartConfirmed,
                    onStartDismissed = onStartDismissed,
                    onOpenTimer = onOpenTimer,
                    onOpenStatistics = onOpenStatistics,
                    onOpenHistory = onOpenHistory,
                    onOpenSettings = onOpenSettings,
                    onOpenPermissions = onOpenPermissions,
                )
            }
        }
    }

    @Test
    fun `the home screen shows the streak, total focus time and session count`() {
        setContent(
            HomeUiState(
                isLoading = false,
                currentStreak = 12,
                totalFocusSeconds = 139_320L,
                completedSessions = 47,
                accessibilityEnabled = true,
                blockedAppCount = 5,
            ),
        )

        composeRule.onNodeWithTag(HomeTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("12 days").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("38h 42m").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("47").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a fresh install reads sensibly rather than showing zeroes everywhere`() {
        setContent(HomeUiState(isLoading = false, accessibilityEnabled = true, blockedAppCount = 1))

        composeRule.onNodeWithText("No streak yet").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("0m").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `choosing a preset duration reports the choice`() {
        var chosen = 0
        setContent(
            HomeUiState(isLoading = false, quickPresetMinutes = listOf(25, 45, 60)),
            onSelectDuration = { chosen = it },
        )

        composeRule.onNodeWithTag(HomeTestTags.preset(45)).performClick()

        assertEquals(45, chosen)
    }

    @Test
    fun `start asks the ViewModel to start`() {
        var started = false
        setContent(HomeUiState(isLoading = false), onStart = { started = true })

        composeRule.onNodeWithTag(HomeTestTags.START_BUTTON).performClick()

        assertTrue(started)
    }

    @Test
    fun `the confirmation says what will happen and can be dismissed`() {
        var dismissed = false
        setContent(
            HomeUiState(
                isLoading = false,
                selectedMinutes = 60,
                accessibilityEnabled = true,
                blockedAppCount = 5,
                showStartConfirmation = true,
            ),
            onStartDismissed = { dismissed = true },
        )

        composeRule.onNodeWithText("Start Zen Mode?").assertIsDisplayed()
        composeRule.onNodeWithText("NOT YET").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun `the confirmation is honest when blocking is not set up`() {
        setContent(
            HomeUiState(
                isLoading = false,
                selectedMinutes = 25,
                accessibilityEnabled = false,
                blockedAppCount = 0,
                showStartConfirmation = true,
            ),
        )

        composeRule
            .onNodeWithText("No apps will be blocked", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `a setup banner appears while blocking cannot work`() {
        setContent(HomeUiState(isLoading = false, accessibilityEnabled = false, blockedAppCount = 0))

        composeRule.onNodeWithTag(HomeTestTags.SETUP_BANNER).assertIsDisplayed()
        composeRule
            .onNodeWithText("App blocking is not set up yet", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `the banner points at the permission when only that is missing`() {
        var openedPermissions = false
        setContent(
            HomeUiState(isLoading = false, accessibilityEnabled = false, blockedAppCount = 4),
            onOpenPermissions = { openedPermissions = true },
        )

        composeRule.onNodeWithText("SET UP BLOCKING").performClick()

        assertTrue(openedPermissions)
    }

    @Test
    fun `no banner is shown once blocking is ready`() {
        setContent(HomeUiState(isLoading = false, accessibilityEnabled = true, blockedAppCount = 4))

        composeRule.onNodeWithTag(HomeTestTags.SETUP_BANNER).assertDoesNotExist()
    }

    @Test
    fun `a running session offers a way back to it`() {
        setContent(HomeUiState(isLoading = false, hasActiveSession = true))

        composeRule.onNodeWithText("RETURN TO ZEN").assertIsDisplayed()
    }

    @Test
    fun `the confirmation warns when Android may end the session late`() {
        setContent(
            HomeUiState(
                isLoading = false,
                selectedMinutes = 25,
                accessibilityEnabled = true,
                blockedAppCount = 3,
                exactAlarmsAvailable = false,
                showStartConfirmation = true,
            ),
        )

        composeRule
            .onNodeWithText("Android may delay the end of this session", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `no timing warning is shown when exact alarms are available`() {
        setContent(
            HomeUiState(
                isLoading = false,
                accessibilityEnabled = true,
                blockedAppCount = 3,
                exactAlarmsAvailable = true,
                showStartConfirmation = true,
            ),
        )

        composeRule
            .onNodeWithText("Android may delay", substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun `starting without blocking is named for what it is`() {
        setContent(
            HomeUiState(
                isLoading = false,
                accessibilityEnabled = false,
                blockedAppCount = 0,
                showStartConfirmation = true,
            ),
        )

        composeRule.onNodeWithText("Start without blocking?").assertIsDisplayed()
        composeRule.onNodeWithText("START ANYWAY").assertIsDisplayed()
    }

    @Test
    fun `the confirmation offers a way to fix blocking instead of only accepting it`() {
        var openedPermissions = false
        setContent(
            HomeUiState(
                isLoading = false,
                accessibilityEnabled = false,
                showStartConfirmation = true,
            ),
            onOpenPermissions = { openedPermissions = true },
        )

        composeRule.onNodeWithText("SET UP").performClick()

        assertTrue(openedPermissions)
    }

    @Test
    fun `a fully set up session is confirmed without warnings or extra actions`() {
        setContent(
            HomeUiState(
                isLoading = false,
                accessibilityEnabled = true,
                blockedAppCount = 3,
                exactAlarmsAvailable = true,
                showStartConfirmation = true,
            ),
        )

        composeRule.onNodeWithText("Start Zen Mode?").assertIsDisplayed()
        composeRule.onNodeWithText("SET UP").assertDoesNotExist()
    }

    @Test
    fun `the other screens are reachable`() {
        var statistics = false
        var history = false
        var settings = false
        setContent(
            HomeUiState(isLoading = false),
            onOpenStatistics = { statistics = true },
            onOpenHistory = { history = true },
            onOpenSettings = { settings = true },
        )

        composeRule.onNodeWithText("STATS").performScrollTo().performClick()
        composeRule.onNodeWithText("HISTORY").performScrollTo().performClick()
        composeRule.onNodeWithText("SETTINGS").performScrollTo().performClick()

        assertTrue(statistics && history && settings)
    }
}
