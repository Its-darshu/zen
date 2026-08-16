package com.zenmode.app.feature.blockedapps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.zenmode.app.core.designsystem.ZenModeTheme
import com.zenmode.app.domain.model.SelectableApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockedAppsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val apps = listOf(
        SelectableApp("com.example.social", "Social", isBlocked = true),
        SelectableApp("com.example.video", "Video", isBlocked = false),
        SelectableApp("com.example.news", "News Reader", isBlocked = false),
    )

    private fun setContent(
        state: BlockedAppsUiState,
        onQueryChange: (String) -> Unit = {},
        onToggleApp: (SelectableApp, Boolean) -> Unit = { _, _ -> },
        onSelectAll: () -> Unit = {},
        onClearSelection: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZenModeTheme {
                BlockedAppsScreen(
                    state = state,
                    onBack = {},
                    onQueryChange = onQueryChange,
                    onToggleApp = onToggleApp,
                    onSelectAll = onSelectAll,
                    onClearSelection = onClearSelection,
                    // Icons come from the package manager, which has nothing to
                    // return in a JVM test.
                    showIcons = false,
                )
            }
        }
    }

    @Test
    fun `installed apps are listed with their package names`() {
        setContent(BlockedAppsUiState(isLoading = false, apps = apps, selectedCount = 1))

        composeRule.onNodeWithTag(BlockedAppsTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("Social").assertIsDisplayed()
        composeRule.onNodeWithText("com.example.social").assertIsDisplayed()
        composeRule.onNodeWithText("News Reader").assertIsDisplayed()
    }

    @Test
    fun `the number of selected apps is shown`() {
        setContent(BlockedAppsUiState(isLoading = false, apps = apps, selectedCount = 1))

        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
    }

    @Test
    fun `tapping a row toggles that app`() {
        var toggled: Pair<String, Boolean>? = null
        setContent(
            BlockedAppsUiState(isLoading = false, apps = apps),
            onToggleApp = { app, blocked -> toggled = app.packageName to blocked },
        )

        composeRule.onNodeWithTag(BlockedAppsTestTags.app("com.example.video")).performClick()

        assertEquals("com.example.video" to true, toggled)
    }

    @Test
    fun `an already blocked app toggles back off`() {
        var toggled: Pair<String, Boolean>? = null
        setContent(
            BlockedAppsUiState(isLoading = false, apps = apps),
            onToggleApp = { app, blocked -> toggled = app.packageName to blocked },
        )

        composeRule.onNodeWithTag(BlockedAppsTestTags.app("com.example.social")).performClick()

        assertEquals("com.example.social" to false, toggled)
    }

    @Test
    fun `typing in the search box reports the query`() {
        var query = ""
        setContent(
            BlockedAppsUiState(isLoading = false, apps = apps),
            onQueryChange = { query = it },
        )

        composeRule.onNodeWithTag(BlockedAppsTestTags.SEARCH).performTextInput("soc")

        assertEquals("soc", query)
    }

    @Test
    fun `select all and clear are both offered`() {
        var selectedAll = false
        var cleared = false
        setContent(
            BlockedAppsUiState(isLoading = false, apps = apps),
            onSelectAll = { selectedAll = true },
            onClearSelection = { cleared = true },
        )

        composeRule.onNodeWithTag(BlockedAppsTestTags.SELECT_ALL).performClick()
        composeRule.onNodeWithTag(BlockedAppsTestTags.CLEAR).performClick()

        assertTrue(selectedAll)
        assertTrue(cleared)
    }

    @Test
    fun `a search with no matches says so`() {
        setContent(BlockedAppsUiState(isLoading = false, apps = emptyList(), query = "zzz"))

        composeRule.onNodeWithTag(BlockedAppsTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("No apps match “zzz”").assertIsDisplayed()
    }

    @Test
    fun `an empty list explains which apps are never blocked`() {
        setContent(BlockedAppsUiState(isLoading = false, apps = emptyList()))

        composeRule
            .onNodeWithText("The dialer, the launcher and Android settings are never blocked.", substring = true)
            .assertIsDisplayed()
    }
}
