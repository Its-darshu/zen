package com.zenmode.app.system

import com.zenmode.app.domain.model.ZenSession
import com.zenmode.app.domain.repository.SessionRepository
import com.zenmode.app.domain.usecase.CompleteZenSessionResult
import com.zenmode.app.domain.usecase.CompleteZenSessionUseCase
import com.zenmode.app.domain.usecase.GetActiveSessionUseCase
import com.zenmode.app.domain.usecase.GetSettingsUseCase
import com.zenmode.app.domain.usecase.StartZenSessionResult
import com.zenmode.app.domain.usecase.StartZenSessionUseCase
import com.zenmode.app.domain.usecase.StopZenSessionResult
import com.zenmode.app.domain.usecase.StopZenSessionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** What starting a session did, from the caller's point of view. */
sealed interface ZenStartOutcome {
    data class Started(val session: ZenSession, val alarmPrecision: AlarmPrecision) : ZenStartOutcome
    data class AlreadyActive(val session: ZenSession) : ZenStartOutcome
    data class Rejected(val reason: String) : ZenStartOutcome
}

sealed interface ZenStopOutcome {
    data class Cancelled(val session: ZenSession) : ZenStopOutcome
    data class Completed(val session: ZenSession) : ZenStopOutcome
    data object NoActiveSession : ZenStopOutcome
}

/**
 * The one place that keeps Android's state in step with the database
 * (specification §25).
 *
 * The database stays the source of truth: this never caches an active session
 * or counts down anything. Its job is to make the foreground service, the
 * end-of-session alarm and the notifications agree with whatever the session
 * table says.
 *
 * Every entry point is serialised through [lock], so a tap on Stop racing with
 * the alarm firing cannot half-apply two different transitions.
 */
@Singleton
class ZenModeManager @Inject constructor(
    private val startZenSession: StartZenSessionUseCase,
    private val stopZenSession: StopZenSessionUseCase,
    private val completeZenSession: CompleteZenSessionUseCase,
    private val getActiveSession: GetActiveSessionUseCase,
    private val getSettings: GetSettingsUseCase,
    private val sessionRepository: SessionRepository,
    private val alarmScheduler: SessionAlarmScheduler,
    private val serviceController: ZenServiceController,
    private val notifier: ZenNotifier,
) {

    private val lock = Mutex()

    /** The running session, straight from the database. */
    fun observeActiveSession(): Flow<ZenSession?> = getActiveSession()

    suspend fun activeSession(): ZenSession? = getActiveSession.current()

    /**
     * Starts a session and brings the system state up with it.
     *
     * If the foreground service cannot be started — Android refuses background
     * starts in several situations — the session is removed again rather than
     * left half-running. The user is told, instead of being shown a Zen screen
     * that nothing is enforcing.
     */
    suspend fun startSession(durationMinutes: Int): ZenStartOutcome = lock.withLock {
        when (val result = startZenSession(durationMinutes)) {
            is StartZenSessionResult.AlreadyActive -> {
                // Somebody already started one; make sure the system agrees.
                syncSystemStateTo(result.session)
                ZenStartOutcome.AlreadyActive(result.session)
            }

            is StartZenSessionResult.InvalidDuration ->
                ZenStartOutcome.Rejected(INVALID_DURATION_MESSAGE)

            StartZenSessionResult.Failed -> ZenStartOutcome.Rejected(COULD_NOT_START_MESSAGE)

            is StartZenSessionResult.Started -> {
                val session = result.session
                notifier.cancelCompletionNotification()
                val precision = alarmScheduler.schedule(session.id, session.scheduledEndAt)

                if (!serviceController.startSessionService()) {
                    rollBackStart(session)
                    ZenStartOutcome.Rejected(SERVICE_REFUSED_MESSAGE)
                } else {
                    ZenStartOutcome.Started(session, precision)
                }
            }
        }
    }

    /**
     * Undoes a start that could not be completed.
     *
     * The row is deleted rather than cancelled: a session that never ran should
     * not appear in the user's history as one they gave up on.
     */
    private suspend fun rollBackStart(session: ZenSession) {
        alarmScheduler.cancel()
        serviceController.stopSessionService()
        sessionRepository.deleteSession(session.id)
    }

    /** The user asked to stop (specification §10). */
    suspend fun stopSession(): ZenStopOutcome = lock.withLock {
        when (val result = stopZenSession()) {
            is StopZenSessionResult.Cancelled -> {
                tearDownSystemState()
                ZenStopOutcome.Cancelled(result.session)
            }

            // The timer had already run out, so this counts as completed.
            is StopZenSessionResult.Completed -> {
                tearDownSystemState()
                postCompletionNotification(result.session)
                ZenStopOutcome.Completed(result.session)
            }

            StopZenSessionResult.NoActiveSession -> {
                tearDownSystemState()
                ZenStopOutcome.NoActiveSession
            }
        }
    }

    /**
     * Finishes the session because its time is up.
     *
     * @param expectedSessionId the session the caller believes is running. An
     *   alarm left over from an earlier session carries its own id, so it fails
     *   this check and is ignored — a stale alarm can never end a newer session.
     */
    suspend fun completeIfDue(expectedSessionId: Long? = null): ZenStopOutcome = lock.withLock {
        val active = getActiveSession.current()
            ?: run {
                tearDownSystemState()
                return@withLock ZenStopOutcome.NoActiveSession
            }

        if (expectedSessionId != null && expectedSessionId != active.id) {
            // A stale alarm. The running session keeps running, untouched.
            return@withLock ZenStopOutcome.NoActiveSession
        }

        when (val result = completeZenSession()) {
            is CompleteZenSessionResult.Completed -> {
                tearDownSystemState()
                postCompletionNotification(result.session)
                ZenStopOutcome.Completed(result.session)
            }

            // Woken early. Put the alarm back for the time actually remaining.
            is CompleteZenSessionResult.NotExpired -> {
                alarmScheduler.schedule(active.id, active.scheduledEndAt)
                ZenStopOutcome.NoActiveSession
            }

            CompleteZenSessionResult.NoActiveSession -> {
                tearDownSystemState()
                ZenStopOutcome.NoActiveSession
            }
        }
    }

    /**
     * Puts Android's state back in step with the database (specification §27).
     *
     * Called on app start, when the service starts, and after a reboot. Safe to
     * call at any time and as often as needed: it only ever moves the system
     * towards whatever the session table already says.
     */
    suspend fun recover(): ZenStopOutcome = lock.withLock {
        val active = getActiveSession.current()

        if (active == null) {
            tearDownSystemState()
            return@withLock ZenStopOutcome.NoActiveSession
        }

        val outcome = when (val result = completeZenSession()) {
            // It expired while we were not running — finish it now.
            is CompleteZenSessionResult.Completed -> {
                tearDownSystemState()
                postCompletionNotification(result.session)
                ZenStopOutcome.Completed(result.session)
            }

            // Still running: restore the service and the alarm.
            is CompleteZenSessionResult.NotExpired -> {
                syncSystemStateTo(active)
                ZenStopOutcome.NoActiveSession
            }

            CompleteZenSessionResult.NoActiveSession -> {
                tearDownSystemState()
                ZenStopOutcome.NoActiveSession
            }
        }
        outcome
    }

    /**
     * Makes sure the alarm and the service exist for a session that is running.
     *
     * If Android refuses to start the service — which it may after a reboot on
     * recent versions — the session is *not* cancelled. The database keeps the
     * truth, the alarm still ends the session on time, and the service starts on
     * the next app launch. Nothing pretends the restoration succeeded.
     */
    private fun syncSystemStateTo(session: ZenSession) {
        alarmScheduler.schedule(session.id, session.scheduledEndAt)
        serviceController.startSessionService()
    }

    private fun tearDownSystemState() {
        alarmScheduler.cancel()
        serviceController.stopSessionService()
    }

    private suspend fun postCompletionNotification(session: ZenSession) {
        if (getSettings.current().completionNotification) {
            notifier.notifySessionCompleted(session.actualDurationSeconds)
        }
    }

    private companion object {
        const val INVALID_DURATION_MESSAGE = "That is not a usable session length."
        const val COULD_NOT_START_MESSAGE = "Could not start the session. Please try again."
        const val SERVICE_REFUSED_MESSAGE =
            "Android would not let Zen Mode start in the background. Open the app and try again."
    }
}
