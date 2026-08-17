package com.zenmode.app.feature.launcher.appdrawer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.zenmode.app.core.designsystem.ZenModeTheme
import com.zenmode.app.domain.model.LauncherApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppDrawerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val apps = listOf(
        LauncherApp("com.example.camera", "Camera", isFavorite = false),
        LauncherApp("com.example.social", "Instagram", isFavorite = true),
        LauncherApp("com.example.notes", "Notes", isFavorite = false),
    )

    private fun setContent(
        state: AppDrawerUiState,
        onQueryChange: (String) -> Unit = {},
        onLaunch: (LauncherApp) -> Unit = {},
        onToggleFavorite: (LauncherApp) -> Unit = {},
    ) {
        composeRule.setContent {
            ZenModeTheme {
                AppDrawerScreen(
                    state = state,
                    onQueryChange = onQueryChange,
                    onLaunch = onLaunch,
                    onToggleFavorite = onToggleFavorite,
                    // Icons come from the package manager, which has nothing to
                    // return in a JVM test.
                    showIcons = false,
                )
            }
        }
    }

    private val populated = AppDrawerUiState(isLoading = false, apps = apps)

    @Test
    fun `the drawer lists apps by name`() {
        setContent(populated)

        composeRule.onNodeWithTag(AppDrawerTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeRule.onNodeWithText("Notes").assertIsDisplayed()
    }

    @Test
    fun `pinned apps are marked in the drawer`() {
        setContent(populated)

        composeRule.onNodeWithText("PINNED").assertIsDisplayed()
    }

    @Test
    fun `tapping an app launches it`() {
        var launched: String? = null
        setContent(populated, onLaunch = { launched = it.packageName })

        composeRule.onNodeWithTag(AppDrawerTestTags.app("com.example.notes")).performClick()

        assertEquals("com.example.notes", launched)
    }

    @Test
    fun `typing reports the query`() {
        var query = ""
        setContent(populated, onQueryChange = { query = it })

        composeRule.onNodeWithTag(AppDrawerTestTags.SEARCH).performTextInput("insta")

        assertEquals("insta", query)
    }

    @Test
    fun `a search with no matches says so`() {
        setContent(AppDrawerUiState(isLoading = false, apps = emptyList(), query = "zzz"))

        composeRule.onNodeWithTag(AppDrawerTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("No apps match “zzz”").assertIsDisplayed()
    }

    @Test
    fun `an empty drawer explains itself rather than showing nothing`() {
        setContent(AppDrawerUiState(isLoading = false, apps = emptyList()))

        composeRule.onNodeWithTag(AppDrawerTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText("No apps to show").assertIsDisplayed()
    }

    @Test
    fun `the drawer shows only what it was given, so filtering happens upstream`() {
        setContent(populated.copy(apps = apps.filter { it.appName == "Instagram" }, query = "insta"))

        composeRule.onNodeWithText("Instagram").assertIsDisplayed()
        composeRule.onNodeWithText("Camera").assertDoesNotExist()
    }
}
