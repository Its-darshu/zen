package com.zenmode.app.feature.permissions

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.zenmode.app.core.designsystem.ZenModeTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PermissionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: PermissionsUiState,
        onOpenAccessibilitySettings: () -> Unit = {},
        onOpenExactAlarmSettings: () -> Unit = {},
        onOpenNotificationSettings: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZenModeTheme {
                PermissionsScreen(
                    state = state,
                    onBack = {},
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                    onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                )
            }
        }
    }

    private val allDenied = PermissionsUiState(
        isLoading = false,
        accessibilityEnabled = false,
        exactAlarmsAvailable = false,
        notificationsEnabled = false,
        exactAlarmSettingExists = true,
    )

    @Test
    fun `all three capabilities are shown`() {
        setContent(allDenied)

        composeRule.onNodeWithTag(PermissionsTestTags.PERMISSIONS_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PermissionsTestTags.ACCESSIBILITY_STATUS).assertIsDisplayed()
        composeRule
            .onNodeWithTag(PermissionsTestTags.EXACT_ALARM_STATUS)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag(PermissionsTestTags.NOTIFICATION_STATUS)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `blocking that is off says what that actually costs`() {
        setContent(allDenied)

        composeRule
            .onNodeWithText("Sessions still run and are recorded, but no apps will be blocked.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `blocking that is on but has no apps selected says so`() {
        setContent(allDenied.copy(accessibilityEnabled = true, blockedAppCount = 0))

        composeRule
            .onNodeWithText("no apps are selected yet", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `denied exact alarms warn about a delayed finish, and never promise precision`() {
        setContent(allDenied)

        composeRule
            .onNodeWithText("Android may delay the end of a session", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `granted exact alarms say sessions end on time`() {
        setContent(allDenied.copy(exactAlarmsAvailable = true))

        composeRule
            .onNodeWithText("Sessions end at the moment they are due", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `denied notifications explain that only the notifications are missing`() {
        setContent(allDenied)

        composeRule
            .onNodeWithText("Sessions run and are recorded as normal", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `each capability offers a route to its own Android setting`() {
        var accessibility = false
        var alarms = false
        var notifications = false
        setContent(
            allDenied,
            onOpenAccessibilitySettings = { accessibility = true },
            onOpenExactAlarmSettings = { alarms = true },
            onOpenNotificationSettings = { notifications = true },
        )

        composeRule.onNodeWithText("ENABLE ACCESSIBILITY").performScrollTo().performClick()
        composeRule.onNodeWithText("ALLOW EXACT ALARMS").performScrollTo().performClick()
        composeRule.onNodeWithText("ALLOW NOTIFICATIONS").performScrollTo().performClick()

        assertTrue(accessibility && alarms && notifications)
    }

    @Test
    fun `nothing is offered for exact alarms on versions that do not have the setting`() {
        setContent(allDenied.copy(exactAlarmSettingExists = false))

        composeRule.onNodeWithText("ALLOW EXACT ALARMS").assertDoesNotExist()
    }

    @Test
    fun `granted capabilities are not nagged about`() {
        setContent(
            allDenied.copy(
                accessibilityEnabled = true,
                blockedAppCount = 3,
                exactAlarmsAvailable = true,
                notificationsEnabled = true,
            ),
        )

        composeRule.onNodeWithText("ALLOW EXACT ALARMS").assertDoesNotExist()
        composeRule.onNodeWithText("ALLOW NOTIFICATIONS").assertDoesNotExist()
    }

    @Test
    fun `the disclosure explains what is observed and what is not`() {
        setContent(allDenied)

        composeRule.onNodeWithTag(PermissionsTestTags.DISCLOSURE).performScrollTo().assertExists()
        composeRule
            .onNodeWithText("Only which app is in the foreground", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("It does not read your messages", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Android does not let any app lock down the whole device", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("You can switch this off at any time", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }
}

@RunWith(RobolectricTestRunner::class)
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `first launch explains the permission before asking for it`() {
        composeRule.setContent {
            ZenModeTheme {
                OnboardingScreen(
                    state = PermissionsUiState(isLoading = false, accessibilityEnabled = false),
                    onEnable = {},
                    onContinue = {},
                )
            }
        }

        composeRule.onNodeWithTag(PermissionsTestTags.ONBOARDING_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(PermissionsTestTags.DISCLOSURE).performScrollTo().assertExists()
        composeRule
            .onNodeWithTag(PermissionsTestTags.ENABLE_BUTTON)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the other two permissions are explained up front too`() {
        composeRule.setContent {
            ZenModeTheme {
                OnboardingScreen(
                    state = PermissionsUiState(isLoading = false),
                    onEnable = {},
                    onContinue = {},
                )
            }
        }

        composeRule
            .onNodeWithText("exact alarms", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `the user can carry on without granting anything`() {
        var continued = false
        composeRule.setContent {
            ZenModeTheme {
                OnboardingScreen(
                    state = PermissionsUiState(isLoading = false, accessibilityEnabled = false),
                    onEnable = {},
                    onContinue = { continued = true },
                )
            }
        }

        composeRule
            .onNodeWithText("CONTINUE WITHOUT BLOCKING")
            .performScrollTo()
            .performClick()

        assertTrue(continued)
    }

    @Test
    fun `once granted there is nothing left to ask for`() {
        composeRule.setContent {
            ZenModeTheme {
                OnboardingScreen(
                    state = PermissionsUiState(isLoading = false, accessibilityEnabled = true),
                    onEnable = {},
                    onContinue = {},
                )
            }
        }

        composeRule
            .onNodeWithText("Accessibility access is on")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("CONTINUE").performScrollTo().assertIsDisplayed()
    }
}
