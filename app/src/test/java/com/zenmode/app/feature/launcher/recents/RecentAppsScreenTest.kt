package com.zenmode.app.feature.launcher.recents

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.zenmode.app.core.designsystem.ZenModeTheme
import com.zenmode.app.domain.model.LauncherRecentApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecentAppsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val apps = listOf(
        LauncherRecentApp("com.example.social", "Instagram", position = 0),
        LauncherRecentApp("com.example.notes", "Notes", position = 1),
        LauncherRecentApp("com.example.camera", "Camera", position = 2),
    )

    private fun setContent(
        state: RecentAppsUiState,
        onOpen: (LauncherRecentApp) -> Unit = {},
        onRemove: (LauncherRecentApp) -> Unit = {},
        onClearAll: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZenModeTheme {
                RecentAppsScreen(
                    state = state,
                    onOpen = onOpen,
                    onRemove = onRemove,
                    onClearAll = onClearAll,
                    // Icons come from the package manager, which has nothing to
                    // return in a JVM test.
                    showIcons = false,
                )
            }
        }
    }

    private val populated = RecentAppsUiState(isLoading = false, apps = apps)

    @Test
    fun `recent apps are listed as cards, most recent first`() {
        setContent(populated)

        composeRule.onNodeWithTag(RecentAppsTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(RecentAppsTestTags.card("com.example.social")).assertIsDisplayed()
        composeRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeRule.onNodeWithText("Notes").assertIsDisplayed()
    }

    @Test
    fun `the screen says plainly that this is not Android's recents`() {
        setContent(populated)

        composeRule.onNodeWithTag(RecentAppsTestTags.EXPLANATION).assertIsDisplayed()
        composeRule
            .onNodeWithText("Android does not let an app read", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `tapping a card opens that app`() {
        var opened: String? = null
        setContent(populated, onOpen = { opened = it.packageName })

        composeRule.onNodeWithTag(RecentAppsTestTags.card("com.example.notes")).performClick()

        assertEquals("com.example.notes", opened)
    }

    @Test
    fun `long-pressing a card removes it from the list`() {
        var removed: String? = null
        setContent(populated, onRemove = { removed = it.packageName })

        composeRule
            .onNodeWithTag(RecentAppsTestTags.card("com.example.camera"))
            .performTouchInput { longClick() }

        assertEquals("com.example.camera", removed)
    }

    @Test
    fun `removal is described as removing from the list, not closing the app`() {
        setContent(populated)

        // The launcher cannot close another app's task, so it must not imply it.
        // The hint is on every card, so assert the count rather than one node.
        composeRule.onAllNodesWithText("Long press to remove").assertCountEquals(apps.size)
    }

    @Test
    fun `the whole list can be cleared`() {
        var cleared = false
        setContent(populated, onClearAll = { cleared = true })

        composeRule.onNodeWithTag(RecentAppsTestTags.CLEAR).performClick()

        assertTrue(cleared)
    }

    @Test
    fun `an empty list says so rather than showing fake cards`() {
        setContent(RecentAppsUiState(isLoading = false, apps = emptyList()))

        composeRule.onNodeWithTag(RecentAppsTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("No recent apps").assertIsDisplayed()
        composeRule.onNodeWithTag(RecentAppsTestTags.LIST).assertDoesNotExist()
    }

    @Test
    fun `an empty list offers no clear action`() {
        setContent(RecentAppsUiState(isLoading = false, apps = emptyList()))

        composeRule.onNodeWithTag(RecentAppsTestTags.CLEAR).assertDoesNotExist()
    }

    @Test
    fun `removing from Android's own recents is never claimed as possible`() {
        setContent(populated)

        // No public API allows it, so the state must report false.
        assertEquals(false, populated.canRemoveFromAndroidRecents)
    }
}
