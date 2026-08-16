package com.zenmode.app.feature.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenAlert
import com.zenmode.app.core.designsystem.ZenConfirmDialog
import com.zenmode.app.core.designsystem.ZenPrimaryButton
import com.zenmode.app.core.designsystem.ZenSecondaryButton
import com.zenmode.app.core.designsystem.ZenSectionHeader
import com.zenmode.app.core.designsystem.ZenTextPrimary
import com.zenmode.app.core.designsystem.ZenTextSecondary
import com.zenmode.app.core.designsystem.ZenTopBar
import com.zenmode.app.core.time.DurationFormat

object TimerTestTags {
    const val SCREEN = "timer_screen"
    const val START_BUTTON = "timer_start_button"
    const val CUSTOM_BUTTON = "timer_custom_button"
    const val CUSTOM_TOTAL = "timer_custom_total"
    const val VALIDATION = "timer_validation"
    const val ALARM_WARNING = "timer_alarm_warning"
    const val HOURS_PLUS = "timer_hours_plus"
    const val HOURS_MINUS = "timer_hours_minus"
    const val MINUTES_PLUS = "timer_minutes_plus"
    const val MINUTES_MINUS = "timer_minutes_minus"
    fun preset(minutes: Int) = "timer_preset_$minutes"
}

@Composable
fun TimerRoute(
    onBack: () -> Unit,
    onSessionStarted: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: TimerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSetupStatus()
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                TimerEvent.SessionStarted -> onSessionStarted()
                is TimerEvent.Error -> onMessage(event.message)
            }
        }
    }

    TimerScreen(
        state = state,
        onBack = onBack,
        onSelectPreset = viewModel::selectPreset,
        onSelectCustom = viewModel::selectCustom,
        onCustomHoursChange = viewModel::setCustomHours,
        onCustomMinutesChange = viewModel::setCustomMinutes,
        onStart = viewModel::onStartRequested,
        onStartConfirmed = viewModel::onStartConfirmed,
        onStartDismissed = viewModel::onStartDismissed,
    )
}

@Composable
fun TimerScreen(
    state: TimerUiState,
    onBack: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onSelectCustom: () -> Unit,
    onCustomHoursChange: (Int) -> Unit,
    onCustomMinutesChange: (Int) -> Unit,
    onStart: () -> Unit,
    onStartConfirmed: () -> Unit,
    onStartDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag(TimerTestTags.SCREEN),
    ) {
        ZenTopBar(title = "Choose a duration", onBack = onBack)

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(8.dp))

            state.presetMinutes.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { minutes ->
                        ZenSecondaryButton(
                            text = DurationFormat.durationLabel(minutes),
                            onClick = { onSelectPreset(minutes) },
                            selected = !state.isCustom && minutes == state.selectedPresetMinutes,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(TimerTestTags.preset(minutes)),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }

            ZenSecondaryButton(
                text = "CUSTOM",
                onClick = onSelectCustom,
                selected = state.isCustom,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TimerTestTags.CUSTOM_BUTTON),
            )

            if (state.isCustom) {
                Spacer(Modifier.height(24.dp))
                ZenSectionHeader("Custom duration")
                Stepper(
                    label = "Hours",
                    value = state.customHours,
                    onDecrease = { onCustomHoursChange(state.customHours - 1) },
                    onIncrease = { onCustomHoursChange(state.customHours + 1) },
                    decreaseTag = TimerTestTags.HOURS_MINUS,
                    increaseTag = TimerTestTags.HOURS_PLUS,
                )
                Spacer(Modifier.height(12.dp))
                Stepper(
                    label = "Minutes",
                    value = state.customMinutes,
                    onDecrease = { onCustomMinutesChange(state.customMinutes - MINUTE_STEP) },
                    onIncrease = { onCustomMinutesChange(state.customMinutes + MINUTE_STEP) },
                    decreaseTag = TimerTestTags.MINUTES_MINUS,
                    increaseTag = TimerTestTags.MINUTES_PLUS,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = DurationFormat.durationLabel(state.totalMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    color = ZenTextPrimary,
                    modifier = Modifier.testTag(TimerTestTags.CUSTOM_TOTAL),
                )
            }

            val validationMessage = state.validationMessage
            if (validationMessage != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = validationMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZenAlert,
                    modifier = Modifier.testTag(TimerTestTags.VALIDATION),
                )
            }

            Spacer(Modifier.height(32.dp))

            ZenPrimaryButton(
                text = "START ZEN",
                onClick = onStart,
                enabled = state.canStart,
                modifier = Modifier.testTag(TimerTestTags.START_BUTTON),
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Calls stay available during a session.",
                style = MaterialTheme.typography.bodyMedium,
                color = ZenTextSecondary,
                textAlign = TextAlign.Start,
            )
            if (state.sessionEndMayBeDelayed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Android may delay the end of this session because exact " +
                        "alarms are unavailable.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZenTextSecondary,
                    modifier = Modifier.testTag(TimerTestTags.ALARM_WARNING),
                )
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (state.showStartConfirmation) {
        ZenConfirmDialog(
            title = "Start Zen Mode?",
            message = confirmationMessage(state),
            confirmText = "START",
            dismissText = "NOT YET",
            onConfirm = onStartConfirmed,
            onDismiss = onStartDismissed,
        )
    }
}

/**
 * A plus/minus stepper rather than a scroll wheel: it works with TalkBack, at
 * any font size, and needs no extra dependency.
 *
 * Laid out as one full-width row per unit. Two of these side by side overflow a
 * narrow screen once the touch targets are large enough to be usable.
 */
@Composable
private fun Stepper(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseTag: String,
    increaseTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = ZenTextSecondary,
            modifier = Modifier.weight(1f),
        )
        ZenSecondaryButton(
            text = "−",
            onClick = onDecrease,
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = "Decrease $label" }
                .testTag(decreaseTag),
        )
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = ZenTextPrimary,
                modifier = Modifier.semantics { contentDescription = "$value $label" },
            )
        }
        ZenSecondaryButton(
            text = "+",
            onClick = onIncrease,
            modifier = Modifier
                .size(56.dp)
                .semantics { contentDescription = "Increase $label" }
                .testTag(increaseTag),
        )
    }
}

/** Minutes step in fives: fine-grained enough, without endless tapping. */
private const val MINUTE_STEP = 5

private fun confirmationMessage(state: TimerUiState): String = buildString {
    append("For the next ")
    append(DurationFormat.durationLabel(state.totalMinutes).lowercase())
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

    if (state.sessionEndMayBeDelayed) {
        append(
            "\n\nAndroid may delay the end of this session because exact alarms " +
                "are unavailable.",
        )
    }
}
