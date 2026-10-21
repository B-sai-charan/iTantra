package org.itantra.core.vad

/**
 * Result produced by [VadDetector] for an evaluated audio frame.
 *
 * @property isSpeech True if voice activity is detected (probability >= threshold).
 * @property probability Raw speech probability value from the Silero VAD neural network [0.0f, 1.0f].
 * @property speechDurationMs Cumulative duration of active speech in the current continuous segment.
 * @property silenceDurationMs Cumulative duration of silence in the current segment.
 * @property isSpeechStart True if this frame marks a state transition from silence to speech.
 * @property isSpeechEnd True if this frame marks a state transition from speech to silence.
 */
data class VadResult(
    val isSpeech: Boolean,
    val probability: Float,
    val speechDurationMs: Long = 0L,
    val silenceDurationMs: Long = 0L,
    val isSpeechStart: Boolean = false,
    val isSpeechEnd: Boolean = false
)
