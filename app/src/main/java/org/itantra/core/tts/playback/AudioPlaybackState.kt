package org.itantra.core.tts.playback

/**
 * Explicit operational and lifecycle states of an [AudioPlaybackEngine].
 */
enum class AudioPlaybackState {
    /**
     * Engine is initialized, idle, and ready to accept new playback requests.
     */
    IDLE,

    /**
     * Engine is actively playing audio samples through the audio sink.
     */
    PLAYING,

    /**
     * Playback cancellation or stopping is in progress.
     */
    STOPPING,

    /**
     * Engine has been permanently released. No further playback is permitted.
     */
    RELEASED
}
