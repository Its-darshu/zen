package com.zenmode.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenHorizontalDivider
import com.zenmode.app.core.designsystem.ZenConfirmDialog
import com.zenmode.app.core.designsystem.ZenPrimaryButton
import com.zenmode.app.core.designsystem.ZenSecondaryButton
import com.zenmode.app.core.designsystem.ZenStatBlock
import com.zenmode.app.core.designsystem.ZenStatusBanner
import com.zenmode.app.core.designsystem.ZenTextPrimary
import com.zenmode.app.core.designsystem.ZenTextSecondary
import com.zenmode.app.core.time.DurationFormat

object HomeTestTags {
    const val SCREEN = "home_screen"
    const val START_BUTTON = "home_start_button"
    const val STREAK = "home_streak"
    const val TOTAL_FOCUS = "home_total_focus"
    const val SESSIONS = "home_sessions"
    const val SETUP_BANNER = "home_setup_banner"
    fun preset(minutes: Int) = "home_preset_$minutes"
}

@Composable
fun HomeRoute(
    onSessionStarted: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Exact-alarm and notification access can change while the user is away in
    // Android's settings, and Android does not tell us. Re-read on return.
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSetupStatus()
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                HomeEvent.SessionStarted -> onSessionStarted()
                is HomeEvent.Error -> onMessage(event.message)
            }
        }
    }

    HomeScreen(
        state = state,
        onSelectDuration = viewModel::selectDuration,
        onStart = viewModel::onStartRequested,
        onStartConfirmed = viewModel::onStartConfirmed,
        onStartDismissed = viewModel::onStartDismissed,
        onOpenTimer = onOpenTimer,
        onOpenStatistics = onOpenStatistics,
        onOpenHistory = onOpenHistory,
        onOpenSettings = onOpenSettings,
        onOpenPermissions = onOpenPermissions,
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSelectDuration: (Int) -> Unit,
    onStart: () -> Unit,
    onStartConfirmed: () -> Unit,
    onStartDismissed: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenStatistics: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .testTag(HomeTestTags.SCREEN),
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            text = "ZEN MODE",
            style = MaterialTheme.typography.labelLarge,
            color = ZenTextSecondary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Ready to focus?",
            style = MaterialTheme.typography.displayMedium,
            color = ZenTextPrimary,
        )

        Spacer(Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.quickPresetMinutes.forEach { minutes ->
                ZenSecondaryButton(
                    text = DurationFormat.durationLabel(minutes),
                    onClick = { onSelectDuration(minutes) },
                    selected = minutes == state.selectedMinutes,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(HomeTestTags.preset(minutes)),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        ZenSecondaryButton(
            text = "CUSTOM",
            onClick = onOpenTimer,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))

        ZenPrimaryButton(
            text = if (state.hasActiveSession) "RETURN TO ZEN" else "START ZEN",
            onClick = onStart,
            modifier = Modifier.testTag(HomeTestTags.START_BUTTON),
        )

        Spacer(Modifier.height(24.dp))

        if (!state.isSetUpForBlocking) {
            ZenStatusBanner(
                text = setupMessage(state),
                actionText = if (!state.accessibilityEnabled) "Set up blocking" else "Choose apps",
                onAction = if (!state.accessibilityEnabled) onOpenPermissions else onOpenSettings,
                modifier = Modifier.testTag(HomeTestTags.SETUP_BANNER),
            )
            Spacer(Modifier.height(24.dp))
        }

        ZenHorizontalDivider()

        ZenStatBlock(
            label = "Current streak",
            value = pluralDays(state.currentStreak),
            modifier = Modifier.testTag(HomeTestTags.STREAK),
        )
        ZenStatBlock(
            label = "Total phone-free time",
            value = DurationFormat.total(state.totalFocusSeconds),
            modifier = Modifier.testTag(HomeTestTags.TOTAL_FOCUS),
        )
        ZenStatBlock(
            label = "Sessions",
            value = state.completedSessions.toString(),
            modifier = Modifier.testTag(HomeTestTags.SESSIONS),
        )

        ZenHorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZenSecondaryButton(
                text = "STATS",
                onClick = onOpenStatistics,
                modifier = Modifier.weight(1f),
            )
            ZenSecondaryButton(
                text = "HISTORY",
                onClick = onOpenHistory,
                modifier = Modifier.weight(1f),
            )
            ZenSecondaryButton(
                text = "SETTINGS",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(48.dp))
    }

    if (state.showStartConfirmation) {
        val blockingOff = !state.isSetUpForBlocking
        ZenConfirmDialog(
            title = if (blockingOff) "Start without blocking?" else "Start Zen Mode?",
            message = startConfirmationMessage(state),
            confirmText = if (blockingOff) "START ANYWAY" else "START",
            dismissText = "NOT YET",
            onConfirm = onStartConfirmed,
            onDismiss = onStartDismissed,
            // A way to fix the problem, not just accept it.
            neutralText = if (blockingOff) "SET UP" else null,
            onNeutral = if (blockingOff) {
                {
                    onStartDismissed()
                    if (!state.accessibilityEnabled) onOpenPermissions() else onOpenSettings()
                }
            } else {
                null
            },
        )
    }
}

private fun pluralDays(days: Int): String = when (days) {
    0 -> "No streak yet"
    1 -> "1 day"
    else -> "$days days"
}

/** Says plainly what is and is not set up — no overstating what blocking does. */
private fun setupMessage(state: HomeUiState): String = when {
    !state.accessibilityEnabled && state.blockedAppCount == 0 ->
        "App blocking is not set up yet. Sessions still run, but no apps will be blocked."
    !state.accessibilityEnabled ->
        "Accessibility access is off, so selected apps will not be blocked during a session."
    else -> "No apps are selected yet, so nothing will be blocked during a session."
}

private fun startConfirmationMessage(state: HomeUiState): String = buildString {
    append("For the next ")
    append(DurationFormat.durationLabel(state.selectedMinutes).lowercase())
    append(":\n\n")

    if (state.isSetUpForBlocking) {
        append(state.blockedAppCount)
        append(" selected app")
        if (state.blockedAppCount != 1) append("s")
        append(" will be blocked.")
    } else {
        append("No apps will be blocked — blocking is not set up yet.")
    }

    append("\n\nCalls remain available.")

    // Never imply timing the platform has not granted.
    if (state.sessionEndMayBeDelayed) {
        append(
            "\n\nAndroid may delay the end of this session because exact alarms " +
                "are unavailable. The countdown stays accurate; the finish may " +
                "arrive a few minutes late if the app is not open.",
        )
    }
}
