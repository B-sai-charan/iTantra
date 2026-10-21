package org.itantra.core.communication.connection

/**
 * Operational lifecycle states for the network connection manager.
 *
 * State Machine Transitions:
 * - DISCONNECTED -> CONNECTING
 * - CONNECTING -> CONNECTED | FAILED | DISCONNECTING | DISCONNECTED
 * - CONNECTED -> DISCONNECTING | FAILED
 * - DISCONNECTING -> DISCONNECTED | FAILED
 * - FAILED -> CONNECTING | DISCONNECTED
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    FAILED;

    /**
     * Checks whether transitioning from this state to [target] is permitted.
     */
    fun canTransitionTo(target: ConnectionState): Boolean {
        return when (this) {
            DISCONNECTED -> target == CONNECTING
            CONNECTING -> target == CONNECTED || target == FAILED || target == DISCONNECTING || target == DISCONNECTED
            CONNECTED -> target == DISCONNECTING || target == FAILED
            DISCONNECTING -> target == DISCONNECTED || target == FAILED
            FAILED -> target == CONNECTING || target == DISCONNECTED
        }
    }
}
