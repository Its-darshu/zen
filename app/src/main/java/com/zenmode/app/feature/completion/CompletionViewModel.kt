package com.zenmode.app.feature.completion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenmode.app.domain.model.SessionStatus
import com.zenmode.app.domain.usecase.GetSessionUseCase
import com.zenmode.app.domain.usecase.GetStreakUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the completion screen shows (specification §44). */
data class CompletionUiState(
    val isLoading: Boolean = true,
    val focusedSeconds: Long = 0L,
    val currentStreak: Int = 0,
    val wasCompleted: Boolean = true,
)

@HiltViewModel
class CompletionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSession: GetSessionUseCase,
    private val getStreak: GetStreakUseCase,
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<String>(SESSION_ID_ARG)?.toLongOrNull() ?: 0L

    private val _uiState = MutableStateFlow(CompletionUiState())
    val uiState: StateFlow<CompletionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val session = getSession(sessionId)
            val streak = getStreak().first()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    focusedSeconds = session?.actualDurationSeconds ?: 0L,
                    currentStreak = streak.currentStreak,
                    wasCompleted = session?.status != SessionStatus.CANCELLED,
                )
            }
        }
    }

    companion object {
        const val SESSION_ID_ARG = "sessionId"
    }
}
