package com.zenmode.app.domain.model

/**
 * The Zen session state machine (specification §9).
 *
 * Only [ACTIVE], [COMPLETED] and [CANCELLED] are ever written to the database:
 * the other values describe transient states that live in memory while a
 * session is being started or wound down. [PAUSED] exists so the state machine
 * is complete, but pausing is deliberately not offered in the MVP — a Zen
 * session is meant to be continuous.
 */
enum class SessionStatus {
    IDLE,
    STARTING,
    ACTIVE,
    PAUSED,
    COMPLETING,
    COMPLETED,
    CANCELLED;

    /** True for the states a row in the database may legitimately hold. */
    val isPersistable: Boolean
        get() = this == ACTIVE || this == COMPLETED || this == CANCELLED

    /** True once a session can no longer change: nothing more will be written. */
    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED
}
