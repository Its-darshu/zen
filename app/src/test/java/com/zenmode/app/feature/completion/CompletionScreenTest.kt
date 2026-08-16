package com.zenmode.app.feature.completion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenmode.app.core.designsystem.ZenModeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CompletionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a finished session shows what was focused and the streak`() {
        composeRule.setContent {
            ZenModeTheme {
                CompletionScreen(
                    state = CompletionUiState(
                        isLoading = false,
                        focusedSeconds = 3_600L,
                        currentStreak = 13,
                        wasCompleted = true,
                    ),
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("ZEN COMPLETE").assertIsDisplayed()
        composeRule.onNodeWithTag(CompletionTestTags.DURATION).assertIsDisplayed()
        composeRule.onNodeWithText("1h").assertIsDisplayed()
        composeRule.onNodeWithText("Streak: 13 days").assertIsDisplayed()
    }

    @Test
    fun `done reports back`() {
        var done = false
        composeRule.setContent {
            ZenModeTheme {
                CompletionScreen(
                    state = CompletionUiState(isLoading = false, focusedSeconds = 1_500L),
                    onDone = { done = true },
                )
            }
        }

        composeRule.onNodeWithTag(CompletionTestTags.DONE).performClick()

        assertTrue(done)
    }

    @Test
    fun `a session that was stopped early is not called complete`() {
        composeRule.setContent {
            ZenModeTheme {
                CompletionScreen(
                    state = CompletionUiState(
                        isLoading = false,
                        focusedSeconds = 600L,
                        currentStreak = 0,
                        wasCompleted = false,
                    ),
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("SESSION ENDED").assertIsDisplayed()
        composeRule.onNodeWithText("10 min").assertIsDisplayed()
    }
}
