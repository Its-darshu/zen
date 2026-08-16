package com.zenmode.app.feature.zen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenBlack
import com.zenmode.app.core.designsystem.ZenConfirmDialog
import com.zenmode.app.core.designsystem.ZenMinTouchTarget
import com.zenmode.app.core.designsystem.ZenSurface
import com.zenmode.app.core.designsystem.ZenTextPrimary
import com.zenmode.app.core.designsystem.ZenTextSecondary
import com.zenmode.app.core.designsystem.ZenTimerTextStyle
import com.zenmode.app.core.time.DurationFormat

object ZenTestTags {
    const val SCREEN = "zen_screen"
    const val REMAINING = "zen_remaining"
    const val CLOCK = "zen_clock"
    const val DATE = "zen_date"
    const val CALL = "zen_call"
    const val LABEL = "zen_label"
}

@Composable
fun ZenRoute(
    onSessionCompleted: (Long) -> Unit,
    onSessionEnded: () -> Unit,
    onCall: () -> Unit,
    viewModel: ZenViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ZenEvent.SessionCompleted -> onSessionCompleted(event.sessionId)
                ZenEvent.SessionCancelled -> onSessionEnded()
                ZenEvent.NoSession -> onSessionEnded()
            }
        }
    }

    ZenScreen(
        state = state,
        onCall = onCall,
        onAdminEscapeRequested = viewModel::onAdminEscapeRequested,
        onAdminEscapeConfirmed = viewModel::onAdminEscapeConfirmed,
        onAdminEscapeDismissed = viewModel::onAdminEscapeDismissed,
    )
}

/**
 * The Zen screen (specification §7).
 *
 * Deliberately close to empty: black, four pieces of text and one control. No
 * animation, no progress ring, nothing that rewards looking at it.
 *
 * **A started session is a commitment.** There is no Stop control and Back does
 * not leave — pressing it does nothing at all. A session ends when its time is
 * up, and that is the whole design.
 *
 * The one exception is the administrative escape: a long press on the ZEN MODE
 * label, then a confirmation. It is not advertised, and it exists so that a
 * session can be ended deliberately during testing and so nobody is locked out
 * of their own device by a mistake. Android's own controls — Settings, force
 * stop, uninstall — always remain available as well.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZenScreen(
    state: ZenUiState,
    onCall: () -> Unit,
    onAdminEscapeRequested: () -> Unit,
    onAdminEscapeConfirmed: () -> Unit,
    onAdminEscapeDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Back is swallowed while a session runs: no confirmation, no exit, no
    // navigation back into the ordinary screens.
    BackHandler(enabled = state.hasActiveSession) { /* A session is a commitment. */ }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (state.pureBlack) ZenBlack else ZenSurface)
            .systemBarsPadding()
            .padding(horizontal = 32.dp)
            .testTag(ZenTestTags.SCREEN),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))

        if (state.showClock) {
            Text(
                text = state.clockText,
                style = MaterialTheme.typography.displayMedium,
                color = ZenTextPrimary,
                modifier = Modifier.testTag(ZenTestTags.CLOCK),
            )
        }
        if (state.showDate) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.dateText,
                style = MaterialTheme.typography.labelMedium,
                color = ZenTextSecondary,
                modifier = Modifier.testTag(ZenTestTags.DATE),
            )
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = state.remainingText,
            style = ZenTimerTextStyle,
            color = ZenTextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    // Announced as words, and only when it changes meaningfully.
                    contentDescription = "${DurationFormat.sessionLength(
                        state.timer.remainingSeconds,
                    )} remaining"
                    liveRegion = LiveRegionMode.Polite
                }
                .testTag(ZenTestTags.REMAINING),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Z E N   M O D E",
            style = MaterialTheme.typography.labelLarge,
            color = ZenTextSecondary,
            modifier = Modifier
                .heightIn(min = ZenMinTouchTarget)
                .combinedClickable(
                    onClick = { /* Nothing: the label is not a button. */ },
                    onLongClick = onAdminEscapeRequested,
                )
                .padding(vertical = 12.dp)
                .testTag(ZenTestTags.LABEL),
        )

        Spacer(Modifier.weight(1f))

        if (state.showCallButton) {
            CallButton(onCall = onCall)
        }

        Spacer(Modifier.height(56.dp))
    }

    if (state.showAdminEscape) {
        ZenConfirmDialog(
            title = "End this session?",
            message = "This is the administrative escape, meant for testing and for " +
                "recovering a device.\n\nEnding now saves the session as cancelled: it " +
                "will not count towards your statistics or your streak.",
            confirmText = "END SESSION",
            dismissText = "KEEP GOING",
            onConfirm = onAdminEscapeConfirmed,
            onDismiss = onAdminEscapeDismissed,
            destructive = true,
        )
    }
}

@Composable
private fun CallButton(onCall: () -> Unit) {
    Row(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .width(200.dp)
            .background(Color.Transparent, RoundedCornerShape(28.dp))
            .clickable(role = Role.Button, onClick = onCall)
            .semantics { contentDescription = "Open the phone dialer" }
            .testTag(ZenTestTags.CALL),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Call,
            contentDescription = null,
            tint = ZenTextPrimary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "CALL",
            style = MaterialTheme.typography.labelLarge,
            color = ZenTextPrimary,
        )
    }
}
