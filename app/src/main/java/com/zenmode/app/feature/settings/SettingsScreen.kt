package com.zenmode.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenAlert
import com.zenmode.app.core.designsystem.ZenConfirmDialog
import com.zenmode.app.core.designsystem.ZenHorizontalDivider
import com.zenmode.app.core.designsystem.ZenMinTouchTarget
import com.zenmode.app.core.designsystem.ZenNavigationRow
import com.zenmode.app.core.designsystem.ZenSecondaryButton
import com.zenmode.app.core.designsystem.ZenSectionHeader
import com.zenmode.app.core.designsystem.ZenSwitchRow
import com.zenmode.app.core.designsystem.ZenTextSecondary
import com.zenmode.app.core.designsystem.ZenTopBar
import com.zenmode.app.core.time.DurationFormat
import com.zenmode.app.system.LockdownCapability

object SettingsTestTags {
    const val SCREEN = "settings_screen"
    const val CONFIRM_START = "settings_confirm_start"
    const val COMPLETION_NOTIFICATION = "settings_completion_notification"
    const val PURE_BLACK = "settings_pure_black"
    const val SHOW_CLOCK = "settings_show_clock"
    const val SHOW_DATE = "settings_show_date"
    const val USE_24_HOUR = "settings_24_hour"
    const val CALL_BUTTON = "settings_call_button"
    const val STRICT_MODE = "settings_strict_mode"
    const val BLOCKED_APPS = "settings_blocked_apps"
    const val ACCESSIBILITY = "settings_accessibility"
    const val CLEAR_HISTORY = "settings_clear_history"
    fun duration(minutes: Int) = "settings_duration_$minutes"
}

/**
 * Says what strict mode will actually achieve on *this* device, rather than
 * describing the best case and hoping.
 */
private fun strictModeDescription(capability: LockdownCapability): String {
    val blocking = "Blocks every app instead of just your list, and blocks the home " +
        "screen too, so a session is the only thing on the phone. The dialer, " +
        "Android Settings and the system bar always stay reachable."
    val platform = when (capability) {
        LockdownCapability.KIOSK ->
            " This device is a dedicated device, so Home and Recents are blocked " +
                "outright and the lock screen is skipped."
        LockdownCapability.SCREEN_PINNING ->
            " Also pins the session. Android keeps its own way out — hold Back and " +
                "Overview together — and locking the phone still shows your normal " +
                "lock screen, which no app can change."
        LockdownCapability.UNAVAILABLE -> ""
    }
    return blocking + platform
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenBlockedApps: () -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onOpenBlockedApps = onOpenBlockedApps,
        onOpenPermissions = onOpenPermissions,
        onSelectDefaultDuration = viewModel::setDefaultDuration,
        onConfirmStartChange = viewModel::setConfirmStart,
        onCompletionNotificationChange = viewModel::setCompletionNotification,
        onPureBlackChange = viewModel::setPureBlackZenScreen,
        onShowClockChange = viewModel::setShowClock,
        onShowDateChange = viewModel::setShowDate,
        onUse24HourChange = viewModel::setUse24HourClock,
        onCallButtonChange = viewModel::setShowCallButton,
        onStrictModeChange = viewModel::setStrictMode,
        onClearHistory = viewModel::onClearHistoryRequested,
        onClearHistoryConfirmed = viewModel::onClearHistoryConfirmed,
        onClearHistoryDismissed = viewModel::onClearHistoryDismissed,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenBlockedApps: () -> Unit,
    onOpenPermissions: () -> Unit,
    onSelectDefaultDuration: (Int) -> Unit,
    onConfirmStartChange: (Boolean) -> Unit,
    onCompletionNotificationChange: (Boolean) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
    onShowClockChange: (Boolean) -> Unit,
    onShowDateChange: (Boolean) -> Unit,
    onUse24HourChange: (Boolean) -> Unit,
    onCallButtonChange: (Boolean) -> Unit,
    onStrictModeChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onClearHistoryConfirmed: () -> Unit,
    onClearHistoryDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag(SettingsTestTags.SCREEN),
    ) {
        ZenTopBar(title = "Settings", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            ZenSectionHeader("Focus")

            Text(
                text = "Default session duration",
                style = MaterialTheme.typography.bodyLarge,
                color = com.zenmode.app.core.designsystem.ZenTextPrimary,
            )
            Spacer(Modifier.height(12.dp))
            state.durationOptions.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { minutes ->
                        ZenSecondaryButton(
                            text = DurationFormat.durationLabel(minutes),
                            onClick = { onSelectDefaultDuration(minutes) },
                            selected = minutes == settings.defaultDurationMinutes,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(SettingsTestTags.duration(minutes)),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(8.dp))

            ZenNavigationRow(
                title = "Blocked apps",
                value = "${state.blockedAppCount} selected",
                onClick = onOpenBlockedApps,
                modifier = Modifier.testTag(SettingsTestTags.BLOCKED_APPS),
            )
            ZenSwitchRow(
                title = "Confirm before starting",
                description = "Show a summary before a session begins.",
                checked = settings.confirmStart,
                onCheckedChange = onConfirmStartChange,
                modifier = Modifier.testTag(SettingsTestTags.CONFIRM_START),
            )
            ZenSwitchRow(
                title = "Completion notification",
                description = "Tell me when a session finishes.",
                checked = settings.completionNotification,
                onCheckedChange = onCompletionNotificationChange,
                modifier = Modifier.testTag(SettingsTestTags.COMPLETION_NOTIFICATION),
            )

            ZenHorizontalDivider()
            ZenSectionHeader("Appearance")

            ZenSwitchRow(
                title = "Pure black Zen screen",
                description = "Full black, easiest on OLED screens.",
                checked = settings.pureBlackZenScreen,
                onCheckedChange = onPureBlackChange,
                modifier = Modifier.testTag(SettingsTestTags.PURE_BLACK),
            )
            ZenSwitchRow(
                title = "Show clock",
                checked = settings.showClock,
                onCheckedChange = onShowClockChange,
                modifier = Modifier.testTag(SettingsTestTags.SHOW_CLOCK),
            )
            ZenSwitchRow(
                title = "Show date",
                checked = settings.showDate,
                onCheckedChange = onShowDateChange,
                modifier = Modifier.testTag(SettingsTestTags.SHOW_DATE),
            )
            ZenSwitchRow(
                title = "24-hour clock",
                checked = settings.use24HourClock,
                onCheckedChange = onUse24HourChange,
                modifier = Modifier.testTag(SettingsTestTags.USE_24_HOUR),
            )

            ZenHorizontalDivider()
            ZenSectionHeader("Behaviour")

            ZenSwitchRow(
                title = "Call button on the Zen screen",
                description = "Opens the phone dialer. Emergency calls always work, " +
                    "whether or not this is on.",
                checked = settings.showCallButton,
                onCheckedChange = onCallButtonChange,
                modifier = Modifier.testTag(SettingsTestTags.CALL_BUTTON),
            )
            ZenSwitchRow(
                title = "Strict mode",
                description = strictModeDescription(state.lockdownCapability),
                checked = settings.strictMode,
                onCheckedChange = onStrictModeChange,
                modifier = Modifier.testTag(SettingsTestTags.STRICT_MODE),
            )
            ZenNavigationRow(
                title = "Accessibility access",
                value = if (state.accessibilityEnabled) "On" else "Off",
                description = if (state.accessibilityEnabled) {
                    "Zen Mode can return you to the Zen screen when a blocked app opens."
                } else {
                    "Blocking is inactive. Sessions still run and are recorded."
                },
                onClick = onOpenPermissions,
                modifier = Modifier.testTag(SettingsTestTags.ACCESSIBILITY),
            )

            ZenHorizontalDivider()
            ZenSectionHeader("Statistics")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ZenMinTouchTarget)
                    .clickable(role = Role.Button, onClick = onClearHistory)
                    .padding(vertical = 12.dp)
                    .testTag(SettingsTestTags.CLEAR_HISTORY),
            ) {
                Text(
                    text = "Clear history",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ZenAlert,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    // Honest about the blast radius: the numbers are derived from
                    // the sessions, so there is no separate reset that keeps them.
                    text = "Deletes every stored session. Statistics and streaks are " +
                        "calculated from those sessions, so they reset too.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZenTextSecondary,
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }

    if (state.showClearHistoryConfirmation) {
        ZenConfirmDialog(
            title = "Clear history?",
            message = "Every stored session will be deleted, along with the statistics " +
                "and streaks calculated from them. This cannot be undone.",
            confirmText = "DELETE",
            dismissText = "KEEP",
            onConfirm = onClearHistoryConfirmed,
            onDismiss = onClearHistoryDismissed,
            destructive = true,
        )
    }
}
