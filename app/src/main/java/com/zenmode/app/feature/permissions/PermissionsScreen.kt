package com.zenmode.app.feature.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenHorizontalDivider
import com.zenmode.app.core.designsystem.ZenSectionHeader
import com.zenmode.app.core.designsystem.ZenTopBar

@Composable
fun PermissionsRoute(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Coming back from Android's settings is exactly when these change.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    PermissionsScreen(
        state = state,
        onBack = onBack,
        onOpenAccessibilitySettings = {
            if (!viewModel.openAccessibilitySettings()) {
                onMessage("Could not open Android's accessibility settings on this device.")
            }
        },
        onOpenExactAlarmSettings = {
            if (!viewModel.openExactAlarmSettings()) {
                onMessage("Could not open Android's alarm settings on this device.")
            }
        },
        onOpenNotificationSettings = {
            if (!viewModel.openNotificationSettings()) {
                onMessage("Could not open Android's notification settings on this device.")
            }
        },
    )
}

/**
 * What Android currently permits, and what each answer actually means
 * (specification §28, §29, §34).
 *
 * Every row states the real consequence of the permission being off. None of
 * them is required to use Zen Mode, and none of them is asked for twice.
 */
@Composable
fun PermissionsScreen(
    state: PermissionsUiState,
    onBack: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag(PermissionsTestTags.PERMISSIONS_SCREEN),
    ) {
        ZenTopBar(title = "Permissions", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            PermissionStatusRow(
                title = "App blocking",
                granted = state.accessibilityEnabled,
                grantedDescription = if (state.blockedAppCount > 0) {
                    "Zen Mode returns you to the Zen screen when one of your " +
                        "${state.blockedAppCount} selected apps opens."
                } else {
                    "Accessibility access is on, but no apps are selected yet, " +
                        "so nothing will be blocked."
                },
                deniedDescription = "Sessions still run and are recorded, but no apps " +
                    "will be blocked.",
                actionText = if (state.accessibilityEnabled) {
                    "Open accessibility settings"
                } else {
                    "Enable accessibility"
                },
                onAction = onOpenAccessibilitySettings,
                modifier = Modifier.testTag(PermissionsTestTags.ACCESSIBILITY_STATUS),
            )

            ZenHorizontalDivider()

            PermissionStatusRow(
                title = "Exact alarms",
                granted = state.exactAlarmsAvailable,
                grantedDescription = "Sessions end at the moment they are due, even if " +
                    "the phone is asleep.",
                deniedDescription = "Android may delay the end of a session by a few " +
                    "minutes when Zen Mode is not open. The countdown itself stays accurate.",
                actionText = if (state.exactAlarmSettingExists && !state.exactAlarmsAvailable) {
                    "Allow exact alarms"
                } else {
                    null
                },
                onAction = onOpenExactAlarmSettings,
                modifier = Modifier.testTag(PermissionsTestTags.EXACT_ALARM_STATUS),
            )

            ZenHorizontalDivider()

            PermissionStatusRow(
                title = "Notifications",
                granted = state.notificationsEnabled,
                grantedDescription = "Zen Mode shows a quiet ongoing notification during a " +
                    "session, and tells you when one finishes.",
                deniedDescription = "Sessions run and are recorded as normal, but you will " +
                    "not see the ongoing or completion notifications.",
                actionText = if (state.notificationsEnabled) null else "Allow notifications",
                onAction = onOpenNotificationSettings,
                modifier = Modifier.testTag(PermissionsTestTags.NOTIFICATION_STATUS),
            )

            ZenHorizontalDivider()
            Spacer(Modifier.height(16.dp))
            ZenSectionHeader("About app blocking")

            AccessibilityDisclosure()

            Spacer(Modifier.height(48.dp))
        }
    }
}
