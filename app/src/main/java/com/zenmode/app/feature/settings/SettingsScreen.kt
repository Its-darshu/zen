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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.zenmode.app.domain.model.LockWallpaperCapability
import com.zenmode.app.system.LockdownCapability
import com.zenmode.app.system.launcher.DefaultLauncherState

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
    const val DEFAULT_LAUNCHER = "settings_default_launcher"
    const val GESTURE_SWIPE_UP = "settings_gesture_swipe_up"
    const val GESTURE_LONG_PRESS = "settings_gesture_long_press"
    const val GESTURE_LONG_PRESS_APP = "settings_gesture_long_press_app"
    const val HOME_WALLPAPER = "settings_home_wallpaper"
    const val HOME_WALLPAPER_OFF = "settings_home_wallpaper_off"
    const val LOCK_WALLPAPER = "settings_lock_wallpaper"
    const val LOCK_WALLPAPER_OFF = "settings_lock_wallpaper_off"
    fun duration(minutes: Int) = "settings_duration_$minutes"
}

/**
 * Says what strict mode will actually achieve on *this* device, rather than
 * describing the best case and hoping.
 */
/** Only images, and only the one the user picks. */
private val IMAGE_MIME_TYPES = arrayOf("image/*")

/**
 * Says exactly what changing the lock wallpaper does — including that turning
 * it off cannot restore the previous one, because Android does not let an app
 * read the existing wallpaper to back it up.
 */
private fun lockWallpaperDescription(
    capability: LockWallpaperCapability,
    enabled: Boolean,
): String = when {
    capability == LockWallpaperCapability.UNSUPPORTED ->
        "This device does not allow apps to change the lock-screen wallpaper."
    enabled ->
        "Your image is the device lock-screen wallpaper. This is a system-wide change, " +
            "not just inside Zen Launcher."
    else ->
        "Sets the device lock-screen wallpaper — a system-wide change that replaces your " +
            "current one. It cannot be restored afterwards. Your PIN, password and " +
            "fingerprint are untouched."
}

/** States the real home-app situation, and who gets to change it. */
private fun launcherDescription(state: DefaultLauncherState): String = when (state) {
    DefaultLauncherState.ZEN_LAUNCHER ->
        "Zen Launcher is your home screen. You can switch back at any time here " +
            "or in Android's settings."
    DefaultLauncherState.OTHER_LAUNCHER ->
        "Another launcher is your home screen. Only you can change this — Android " +
            "will ask you to confirm."
    DefaultLauncherState.NOT_CHOSEN ->
        "No home app has been chosen yet, so Android asks each time you press Home."
}

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
    onMessage: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The home app can change while the user is away in Android's picker, and
    // Android does not tell us; re-read when the screen comes back.
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refresh() }

    // OpenDocument rather than the photo picker: only this grants access that
    // survives a reboot, and the wallpaper has to outlive one.
    val homeWallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            viewModel.onHomeWallpaperPicked(it.toString()) { error ->
                if (error != null) onMessage(error)
            }
        }
    }

    val lockWallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            viewModel.onLockWallpaperPicked(it.toString()) { error ->
                if (error != null) onMessage(error)
            }
        }
    }
    SettingsScreen(
        state = state,
        onBack = onBack,
        onOpenBlockedApps = onOpenBlockedApps,
        onOpenPermissions = onOpenPermissions,
        onChooseHomeWallpaper = { homeWallpaperPicker.launch(IMAGE_MIME_TYPES) },
        onClearHomeWallpaper = viewModel::onHomeWallpaperCleared,
        onChooseLockWallpaper = { lockWallpaperPicker.launch(IMAGE_MIME_TYPES) },
        onClearLockWallpaper = {
            viewModel.onLockWallpaperCleared { error -> if (error != null) onMessage(error) }
        },
        onSetDefaultLauncher = {
            // Ask through the system role dialog where possible; otherwise send
            // the user to Android's home-app screen. The app cannot set this.
            val roleIntent = viewModel.requestHomeRoleIntent()
            if (roleIntent != null) roleLauncher.launch(roleIntent) else viewModel.openHomeSettings()
        },
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
    onSetDefaultLauncher: () -> Unit,
    onChooseHomeWallpaper: () -> Unit,
    onClearHomeWallpaper: () -> Unit,
    onChooseLockWallpaper: () -> Unit,
    onClearLockWallpaper: () -> Unit,
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
            ZenSectionHeader("Zen Launcher")

            ZenNavigationRow(
                title = "Set as default launcher",
                value = when (state.defaultLauncherState) {
                    DefaultLauncherState.ZEN_LAUNCHER -> "On"
                    DefaultLauncherState.OTHER_LAUNCHER -> "Off"
                    DefaultLauncherState.NOT_CHOSEN -> "Not chosen"
                },
                description = launcherDescription(state.defaultLauncherState),
                onClick = onSetDefaultLauncher,
                modifier = Modifier.testTag(SettingsTestTags.DEFAULT_LAUNCHER),
            )

            ZenHorizontalDivider()
            ZenSectionHeader("Launcher gestures")

            // Described, not configurable. Gestures are invisible, so saying
            // what they do is worth a few lines; offering remapping we have not
            // built would be a fake setting.
            ZenNavigationRow(
                title = "Swipe up on home",
                value = "App drawer",
                description = "The APPS button does the same thing.",
                onClick = {},
                modifier = Modifier.testTag(SettingsTestTags.GESTURE_SWIPE_UP),
            )
            ZenNavigationRow(
                title = "Long press on home",
                value = "These settings",
                description = "The SETTINGS button does the same thing.",
                onClick = {},
                modifier = Modifier.testTag(SettingsTestTags.GESTURE_LONG_PRESS),
            )
            ZenNavigationRow(
                title = "Long press a pinned app",
                value = "Unpin",
                description = "Long press it again in the app drawer to pin it back. " +
                    "Android's own Back, Home and Recents gestures are untouched.",
                onClick = {},
                modifier = Modifier.testTag(SettingsTestTags.GESTURE_LONG_PRESS_APP),
            )

            ZenHorizontalDivider()
            ZenSectionHeader("Wallpaper")

            ZenNavigationRow(
                title = "Home screen",
                value = if (state.wallpaper.hasHomeWallpaper) "On" else "Off",
                description = if (state.wallpaper.hasHomeWallpaper) {
                    "Your image is drawn behind the launcher. Zen sessions stay pure black."
                } else {
                    "No wallpaper selected — the launcher background is pure black."
                },
                onClick = onChooseHomeWallpaper,
                modifier = Modifier.testTag(SettingsTestTags.HOME_WALLPAPER),
            )
            if (state.wallpaper.hasHomeWallpaper) {
                ZenNavigationRow(
                    title = "Turn off home wallpaper",
                    description = "Goes back to a pure black launcher background.",
                    onClick = onClearHomeWallpaper,
                    modifier = Modifier.testTag(SettingsTestTags.HOME_WALLPAPER_OFF),
                )
            }

            ZenNavigationRow(
                title = "Lock screen",
                value = when {
                    state.lockWallpaperCapability == LockWallpaperCapability.UNSUPPORTED ->
                        "Unavailable"
                    state.wallpaper.hasLockWallpaper -> "On"
                    else -> "Off"
                },
                description = lockWallpaperDescription(
                    capability = state.lockWallpaperCapability,
                    enabled = state.wallpaper.hasLockWallpaper,
                ),
                onClick = {
                    if (state.lockWallpaperCapability == LockWallpaperCapability.SUPPORTED) {
                        onChooseLockWallpaper()
                    }
                },
                modifier = Modifier.testTag(SettingsTestTags.LOCK_WALLPAPER),
            )
            if (state.wallpaper.hasLockWallpaper) {
                ZenNavigationRow(
                    title = "Turn off lock wallpaper",
                    description = "Clears it. Android then mirrors your home wallpaper — it " +
                        "cannot put back whatever lock wallpaper you had before.",
                    onClick = onClearLockWallpaper,
                    modifier = Modifier.testTag(SettingsTestTags.LOCK_WALLPAPER_OFF),
                )
            }

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
