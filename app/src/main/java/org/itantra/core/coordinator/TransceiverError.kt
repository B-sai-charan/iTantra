package org.itantra.core.coordinator

/**
 * High-level error hierarchy emitted by [TransceiverCoordinator] to observers.
 * Preserves the underlying exception cause for diagnostics.
 */
sealed class TransceiverError(
    val message: String,
    val cause: Throwable? = null
) {
    /**
     * Error originating from the M1 Speech Subsystem (AudioRecord capture, Silero VAD, Whisper ASR).
     */
    class M1PipelineError(message: String, cause: Throwable? = null) : TransceiverError(message, cause)

    /**
     * Error originating from the M2 Communication Subsystem (packet encoding/decoding, TCP sockets, CRC).
     */
    class M2TransportError(message: String, cause: Throwable? = null) : TransceiverError(message, cause)

    /**
     * Error originating from the M3 TTS Subsystem (unsupported language, model failure, blank text).
     */
    class M3TtsError(message: String, cause: Throwable? = null) : TransceiverError(message, cause)

    /**
     * Error originating from the M3 Audio Playback Subsystem (playback hardware error, release violation).
     */
    class M3PlaybackError(message: String, cause: Throwable? = null) : TransceiverError(message, cause)

    /**
     * General lifecycle or state transition error within the coordinator itself.
     */
    class LifecycleError(message: String, cause: Throwable? = null) : TransceiverError(message, cause)

    override fun toString(): String {
        return "${this::class.simpleName}(message='$message', cause=$cause)"
    }
}
