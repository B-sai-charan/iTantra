package org.itantra.core.audio

/**
 * Lifecycle states of the [AudioCapture] instance.
 */
enum class AudioCaptureState {
    UNINITIALIZED,
    INITIALIZED,
    RECORDING,
    STOPPED,
    RELEASED,
    ERROR
}
