package com.zenmode.app.feature.completion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenPrimaryButton
import com.zenmode.app.core.designsystem.ZenTextPrimary
import com.zenmode.app.core.designsystem.ZenTextSecondary
import com.zenmode.app.core.time.DurationFormat

object CompletionTestTags {
    const val SCREEN = "completion_screen"
    const val DURATION = "completion_duration"
    const val STREAK = "completion_streak"
    const val DONE = "completion_done"
}

@Composable
fun CompletionRoute(
    onDone: () -> Unit,
    viewModel: CompletionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CompletionScreen(state = state, onDone = onDone)
}

@Composable
fun CompletionScreen(
    state: CompletionUiState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Back means the same as Done here: there is nothing to go back to.
    BackHandler(onBack = onDone)

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 32.dp)
            .testTag(CompletionTestTags.SCREEN),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Text(
            text = if (state.wasCompleted) "ZEN COMPLETE" else "SESSION ENDED",
            style = MaterialTheme.typography.labelLarge,
            color = ZenTextSecondary,
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = if (state.wasCompleted) "You stayed away for" else "You focused for",
            style = MaterialTheme.typography.bodyLarge,
            color = ZenTextSecondary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = DurationFormat.sessionLength(state.focusedSeconds),
            style = MaterialTheme.typography.displayLarge,
            color = ZenTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(CompletionTestTags.DURATION),
        )

        if (state.currentStreak > 0) {
            Spacer(Modifier.height(32.dp))
            Text(
                text = "Streak: ${state.currentStreak} day${if (state.currentStreak == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                color = ZenTextSecondary,
                modifier = Modifier.testTag(CompletionTestTags.STREAK),
            )
        }

        Spacer(Modifier.weight(1f))

        ZenPrimaryButton(
            text = "DONE",
            onClick = onDone,
            modifier = Modifier.testTag(CompletionTestTags.DONE),
        )

        Spacer(Modifier.height(48.dp))
    }
}
