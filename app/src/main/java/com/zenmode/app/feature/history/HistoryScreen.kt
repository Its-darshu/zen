package com.zenmode.app.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zenmode.app.core.designsystem.ZenEmptyState
import com.zenmode.app.core.designsystem.ZenHorizontalDivider
import com.zenmode.app.core.designsystem.ZenSecondaryButton
import com.zenmode.app.core.designsystem.ZenSectionHeader
import com.zenmode.app.core.designsystem.ZenTextPrimary
import com.zenmode.app.core.designsystem.ZenTextSecondary
import com.zenmode.app.core.designsystem.ZenTopBar
import com.zenmode.app.core.time.DurationFormat
import com.zenmode.app.domain.model.RelativeDay
import com.zenmode.app.domain.model.SessionFilter
import com.zenmode.app.domain.model.SessionHistoryGroup
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.model.ZenSession
import java.time.ZoneId

object HistoryTestTags {
    const val SCREEN = "history_screen"
    const val LIST = "history_list"
    const val EMPTY = "history_empty"
    fun filter(filter: SessionFilter) = "history_filter_${filter.name}"
    fun session(id: Long) = "history_session_$id"
}

@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(state = state, onBack = onBack, onSelectFilter = viewModel::selectFilter)
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onBack: () -> Unit,
    onSelectFilter: (SessionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .testTag(HistoryTestTags.SCREEN),
    ) {
        ZenTopBar(title = "History", onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SessionFilter.entries.forEach { filter ->
                ZenSecondaryButton(
                    text = filter.label(),
                    onClick = { onSelectFilter(filter) },
                    selected = filter == state.filter,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(HistoryTestTags.filter(filter)),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.isEmpty) {
            ZenEmptyState(
                title = emptyTitle(state.filter),
                description = "Sessions you run will be listed here, on the day they finished.",
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .testTag(HistoryTestTags.EMPTY),
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HistoryTestTags.LIST),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 24.dp,
                end = 24.dp,
                bottom = 48.dp,
            ),
        ) {
            state.groups.forEach { group ->
                item(key = "header-${group.date}") {
                    ZenSectionHeader(group.heading())
                }
                items(items = group.sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        zone = state.zone,
                        use24HourClock = state.use24HourClock,
                    )
                    ZenHorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: ZenSession,
    zone: ZoneId,
    use24HourClock: Boolean,
) {
    val planned = DurationFormat.sessionLength(session.plannedDurationSeconds)
    val actual = DurationFormat.sessionLength(session.actualDurationSeconds)
    val outcome = session.status.label()
    val range = DurationFormat.timeRange(session.startedAt, session.endedAt, zone, use24HourClock)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "$planned planned, $outcome, $range, $actual focused"
            }
            .padding(vertical = 12.dp)
            .testTag(HistoryTestTags.session(session.id)),
    ) {
        Text(
            text = planned,
            style = MaterialTheme.typography.titleMedium,
            color = ZenTextPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = outcome,
            style = MaterialTheme.typography.bodyMedium,
            color = ZenTextSecondary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = range,
            style = MaterialTheme.typography.bodyMedium,
            color = ZenTextSecondary,
        )
        // Only worth showing when it differs from the plan.
        if (session.actualDurationSeconds != session.plannedDurationSeconds) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "$actual focused",
                style = MaterialTheme.typography.bodyMedium,
                color = ZenTextSecondary,
            )
        }
    }
}

private fun SessionHistoryGroup.heading(): String = when (relativeDay) {
    RelativeDay.TODAY -> "TODAY"
    RelativeDay.YESTERDAY -> "YESTERDAY"
    RelativeDay.EARLIER -> DurationFormat.shortDate(date)
}

private fun SessionStatus.label(): String = when (this) {
    SessionStatus.COMPLETED -> "Completed"
    SessionStatus.CANCELLED -> "Cancelled"
    SessionStatus.ACTIVE -> "In progress"
    else -> name.lowercase().replaceFirstChar { it.uppercase() }
}

private fun SessionFilter.label(): String = when (this) {
    SessionFilter.ALL -> "ALL"
    SessionFilter.COMPLETED -> "COMPLETED"
    SessionFilter.CANCELLED -> "CANCELLED"
}

private fun emptyTitle(filter: SessionFilter): String = when (filter) {
    SessionFilter.ALL -> "No sessions yet"
    SessionFilter.COMPLETED -> "No completed sessions yet"
    SessionFilter.CANCELLED -> "No cancelled sessions"
}
