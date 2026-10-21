package org.itantra.core.tts.playback

import org.itantra.core.tts.TtsAudio

/**
 * Exception thrown when playback is attempted on a released [AudioPlaybackEngine].
 */
class AudioPlaybackReleasedException(
    message: String = "Cannot perform audio playback operation: engine is released"
) : IllegalStateException(message)

/**
 * Exception thrown when an audio playback hardware or driver error occurs.
 */
class AudioPlaybackException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Audio playback abstraction for Module 3 (M3) speech presentation.
 *
 * Decouples raw synthesized audio playback from Android-specific hardware drivers
 * (such as [android.media.AudioTrack]), Activities, and UI lifecycles.
 */
interface AudioPlaybackEngine {

    /**
     * Current operational state of the playback engine.
     */
    val state: AudioPlaybackState

    /**
     * Whether the engine is currently actively playing audio.
     */
    val isPlaying: Boolean
        get() = state == AudioPlaybackState.PLAYING

    /**
     * Whether the engine has been released.
     */
    val isReleased: Boolean
        get() = state == AudioPlaybackState.RELEASED

    /**
     * Plays the provided [TtsAudio] buffer.
     *
     * In accordance with the iTantra serialized transceiver policy, concurrent playback
     * requests are serialized so every received speech message is completely spoken without
     * interleaving or dropping packets.
     *
     * @param audio Synthesized audio containing PCM float samples and sample rate.
     * @throws IllegalArgumentException if audio contains zero samples.
     * @throws AudioPlaybackReleasedException if the engine is released.
     * @throws AudioPlaybackException if a hardware or driver failure occurs.
     */
    suspend fun play(audio: TtsAudio)

    /**
     * Immediately stops and interrupts any active playback, transitioning state back to [AudioPlaybackState.IDLE].
     * If the engine is already IDLE or RELEASED, this is a safe no-op.
     */
    fun stop()

    /**
     * Permanently releases all audio playback resources, stopping any active playback and transitioning
     * state to [AudioPlaybackState.RELEASED].
     *
     * This operation is idempotent and thread-safe.
     */
    fun release()
}
