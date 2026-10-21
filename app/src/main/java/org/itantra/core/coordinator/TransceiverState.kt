package org.itantra.core.coordinator

/**
 * Operational and lifecycle states of the top-level [TransceiverCoordinator].
 */
enum class TransceiverState {
    /**
     * Coordinator is instantiated or stopped, ready to start.
     */
    STOPPED,

    /**
     * Startup sequence is in progress (connecting M2 receiver, starting M1 speech pipeline).
     */
    STARTING,

    /**
     * Transceiver is actively running: local speech capture is active, transport routing is connected,
     * and incoming messages are dispatched to TTS playback.
     */
    RUNNING,

    /**
     * Shutdown sequence is in progress (stopping M1 speech pipeline, detaching M2 receiver).
     */
    STOPPING,

    /**
     * Coordinator has been permanently released and cannot be restarted.
     */
    RELEASED,

    /**
     * Startup or runtime failure occurred.
     */
    FAILED
}
