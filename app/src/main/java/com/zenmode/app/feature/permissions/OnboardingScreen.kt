package com.zenmode.app.feature.permissions

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenPrimaryButton
import com.zenmode.app.core.designsystem.ZenSecondaryButton
import com.zenmode.app.core.designsystem.ZenTextPrimary
import com.zenmode.app.core.designsystem.ZenTextSecondary

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    OnboardingScreen(
        state = state,
        onEnable = {
            if (!viewModel.openAccessibilitySettings()) {
                onMessage("Could not open Android's accessibility settings on this device.")
            }
        },
        onContinue = {
            viewModel.onOnboardingFinished()
            onFinished()
        },
    )
}

/**
 * First launch (specification §28).
 *
 * Explains the permission before asking for it, says what the other two
 * permissions affect, and offers a way past all of them: Zen Mode is fully
 * usable without any of them, just without blocking or notifications. Nobody is
 * nagged, blocked, or tricked into granting anything.
 */
@Composable
fun OnboardingScreen(
    state: PermissionsUiState,
    onEnable: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .testTag(PermissionsTestTags.ONBOARDING_SCREEN),
    ) {
        Spacer(Modifier.height(64.dp))

        Text(
            text = "ZEN MODE",
            style = MaterialTheme.typography.labelLarge,
            color = ZenTextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Time away from the phone, on purpose.",
            style = MaterialTheme.typography.headlineMedium,
            color = ZenTextPrimary,
        )

        Spacer(Modifier.height(32.dp))

        AccessibilityDisclosure()

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Two smaller ones",
            style = MaterialTheme.typography.titleMedium,
            color = ZenTextPrimary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Android also asks about notifications, which is how Zen Mode shows the " +
                "ongoing session and tells you when it finishes; and about exact alarms, " +
                "which let a session end at the exact moment it is due rather than a few " +
                "minutes later. Both are optional, and you can change them later under " +
                "Settings → Permissions.",
            style = MaterialTheme.typography.bodyLarge,
            color = ZenTextSecondary,
        )

        Spacer(Modifier.height(32.dp))

        PermissionStatusText(enabled = state.accessibilityEnabled)

        Spacer(Modifier.height(24.dp))

        if (!state.accessibilityEnabled) {
            ZenPrimaryButton(
                text = "ENABLE ACCESSIBILITY",
                onClick = onEnable,
                modifier = Modifier.testTag(PermissionsTestTags.ENABLE_BUTTON),
            )
            Spacer(Modifier.height(12.dp))
            ZenSecondaryButton(
                text = "CONTINUE WITHOUT BLOCKING",
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(PermissionsTestTags.SKIP_BUTTON),
            )
        } else {
            ZenPrimaryButton(
                text = "CONTINUE",
                onClick = onContinue,
                modifier = Modifier.testTag(PermissionsTestTags.SKIP_BUTTON),
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}
