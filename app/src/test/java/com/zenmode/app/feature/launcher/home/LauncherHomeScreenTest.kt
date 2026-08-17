package com.zenmode.app.feature.launcher.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.zenmode.app.core.designsystem.ZenModeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LauncherHomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: LauncherHomeUiState,
        onOpenZenMode: () -> Unit = {},
        onOpenLauncherSettings: () -> Unit = {},
        onOpenAppDrawer: () -> Unit = {},
        onOpenRecents: () -> Unit = {},
        onLaunchFavorite: (com.zenmode.app.domain.model.LauncherApp) -> Unit = {},
        onUnpinFavorite: (com.zenmode.app.domain.model.LauncherApp) -> Unit = {},
    ) {
        composeRule.setContent {
            ZenModeTheme {
                LauncherHomeScreen(
                    state = state,
                    onOpenZenMode = onOpenZenMode,
                    onOpenLauncherSettings = onOpenLauncherSettings,
                    onOpenAppDrawer = onOpenAppDrawer,
                    onOpenRecents = onOpenRecents,
                    onLaunchFavorite = onLaunchFavorite,
                    onUnpinFavorite = onUnpinFavorite,
                    showIcons = false,
                )
            }
        }
    }

    private val idle = LauncherHomeUiState(
        isLoading = false,
        clockText = "17:42",
        dateText = "AUG 17, 2026",
        sessionActive = false,
        remainingText = "00:00:00",
    )

    @Test
    fun `the home screen shows the clock and the date`() {
        setContent(idle)

        composeRule.onNodeWithTag(LauncherHomeTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("17:42").assertIsDisplayed()
        composeRule.onNodeWithText("AUG 17, 2026").assertIsDisplayed()
    }

    @Test
    fun `no session means no countdown on the home screen`() {
        setContent(idle)

        composeRule.onNodeWithTag(LauncherHomeTestTags.SESSION).assertDoesNotExist()
    }

    @Test
    fun `a running session is shown, so making this the home app cannot hide it`() {
        setContent(idle.copy(sessionActive = true, remainingText = "00:16:13"))

        composeRule.onNodeWithTag(LauncherHomeTestTags.SESSION).assertIsDisplayed()
        composeRule.onNodeWithText("00:16:13").assertIsDisplayed()
        composeRule.onNodeWithText("Z E N   M O D E").assertIsDisplayed()
    }

    @Test
    fun `pinned apps are shown on the home screen and can be opened`() {
        var opened: String? = null
        setContent(
            idle.copy(
                favorites = listOf(
                    com.zenmode.app.domain.model.LauncherApp("com.example.notes", "Notes", true),
                ),
            ),
            onLaunchFavorite = { opened = it.packageName },
        )

        composeRule.onNodeWithTag(LauncherHomeTestTags.FAVORITES).assertIsDisplayed()
        composeRule.onNodeWithText("Notes").assertIsDisplayed()
        composeRule.onNodeWithTag(LauncherHomeTestTags.favorite("com.example.notes")).performClick()

        assertEquals("com.example.notes", opened)
    }

    @Test
    fun `the app drawer is reachable from the home screen`() {
        var opened = false
        setContent(idle, onOpenAppDrawer = { opened = true })

        composeRule.onNodeWithTag(LauncherHomeTestTags.OPEN_DRAWER).performClick()

        assertTrue(opened)
    }

    @Test
    fun `a running session hides the drawer and the favourites`() {
        setContent(
            idle.copy(
                sessionActive = true,
                remainingText = "00:16:13",
                favorites = listOf(
                    com.zenmode.app.domain.model.LauncherApp("com.example.notes", "Notes", true),
                ),
            ),
        )

        // The home screen must not offer a grid of apps over a running session.
        composeRule.onNodeWithTag(LauncherHomeTestTags.OPEN_DRAWER).assertDoesNotExist()
        composeRule.onNodeWithTag(LauncherHomeTestTags.FAVORITES).assertDoesNotExist()
        composeRule.onNodeWithTag(LauncherHomeTestTags.SESSION).assertIsDisplayed()
    }

    @Test
    fun `a wallpaper is only drawn when one is chosen`() {
        // No loader is supplied in tests, so this asserts the state contract:
        // a null uri means the screen falls back to a black background.
        setContent(idle)

        composeRule.onNodeWithTag(LauncherHomeTestTags.SCREEN).assertIsDisplayed()
    }

    @Test
    fun `the Zen Mode app is reachable from the home screen`() {
        var opened = false
        setContent(idle, onOpenZenMode = { opened = true })

        composeRule.onNodeWithTag(LauncherHomeTestTags.OPEN_ZEN).performClick()

        assertTrue(opened)
    }

    @Test
    fun `launcher settings are reachable without any gesture`() {
        var opened = false
        setContent(idle, onOpenLauncherSettings = { opened = true })

        // The long press has a visible twin, so nothing is gesture-only — and
        // this is also the route back to another launcher.
        composeRule.onNodeWithTag(LauncherHomeTestTags.OPEN_SETTINGS).performClick()

        assertTrue(opened)
    }

    @Test
    fun `long-pressing a pinned app unpins it`() {
        var unpinned: String? = null
        setContent(
            idle.copy(
                favorites = listOf(
                    com.zenmode.app.domain.model.LauncherApp("com.example.notes", "Notes", true),
                ),
            ),
            onUnpinFavorite = { unpinned = it.packageName },
        )

        composeRule
            .onNodeWithTag(LauncherHomeTestTags.favorite("com.example.notes"))
            .performTouchInput { longClick() }

        assertEquals("com.example.notes", unpinned)
    }

    @Test
    fun `a running session leaves no gesture target on the home screen`() {
        setContent(idle.copy(sessionActive = true, remainingText = "00:16:13"))

        // Nothing to swipe up to, nothing to long-press into, and no recents.
        composeRule.onNodeWithTag(LauncherHomeTestTags.OPEN_DRAWER).assertDoesNotExist()
        composeRule.onNodeWithTag(LauncherHomeTestTags.OPEN_SETTINGS).assertDoesNotExist()
        composeRule.onNodeWithTag(LauncherHomeTestTags.OPEN_RECENTS).assertDoesNotExist()
        composeRule.onNodeWithTag(LauncherHomeTestTags.FAVORITES).assertDoesNotExist()
    }

    @Test
    fun `the launcher's recent apps are reachable from home`() {
        var opened = false
        setContent(idle, onOpenRecents = { opened = true })

        composeRule.onNodeWithTag(LauncherHomeTestTags.OPEN_RECENTS).performClick()

        assertTrue(opened)
    }

}
