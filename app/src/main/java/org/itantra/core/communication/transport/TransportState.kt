package org.itantra.core.communication.transport

/**
 * Lifecycle states for socket-based transport connections.
 * Based on Section 11 of docs/communication/TRANSPORT_ARCHITECTURE_DECISION.md.
 */
enum class TransportState {
    CLOSED,
    CONNECTING,
    CONNECTED,
    CLOSING,
    FAILED;

    /**
     * Checks whether transitioning from this state to [target] is permitted.
     * Enforces the lifecycle state machine:
     * CLOSED -> CONNECTING
     * CONNECTING -> CONNECTED | FAILED | CLOSING
     * CONNECTED -> CLOSING | FAILED
     * CLOSING -> CLOSED
     * FAILED -> CLOSED | CLOSING
     */
    fun canTransitionTo(target: TransportState): Boolean {
        return when (this) {
            CLOSED -> target == CONNECTING
            CONNECTING -> target == CONNECTED || target == FAILED || target == CLOSING
            CONNECTED -> target == CLOSING || target == FAILED
            CLOSING -> target == CLOSED
            FAILED -> target == CLOSED || target == CLOSING
        }
    }
}
