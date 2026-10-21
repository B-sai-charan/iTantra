package org.itantra.core.tts.playback

import kotlin.math.roundToInt

/**
 * Pure Kotlin utility for deterministic conversion between audio PCM sample formats.
 *
 * Designed to convert normalized float samples from [org.itantra.core.tts.TtsAudio] into
 * standard 16-bit signed PCM shorts for Android [android.media.AudioTrack] rendering.
 *
 * Invariants:
 * - Clamps float values strictly to [-1.0f, +1.0f].
 * - Preserves sample count, ordering, and channel topology (Mono remains Mono).
 * - Zero resampling, zero normalization, zero DSP, zero volume alteration.
 */
object PcmAudioConverter {

    /**
     * Converts an array of 32-bit floating-point audio samples in range [-1.0f, +1.0f]
     * to 16-bit signed PCM short samples in range [-32768, 32767].
     *
     * @param floatSamples Array of normalized float samples.
     * @return Array of 16-bit signed PCM short samples.
     */
    fun floatToPcm16(floatSamples: FloatArray): ShortArray {
        if (floatSamples.isEmpty()) return ShortArray(0)

        val output = ShortArray(floatSamples.size)
        for (i in floatSamples.indices) {
            val sample = floatSamples[i]
            output[i] = when {
                sample.isNaN() -> 0
                sample >= 1.0f -> Short.MAX_VALUE // 32767
                sample <= -1.0f -> Short.MIN_VALUE // -32768
                sample >= 0.0f -> (sample * 32767.0f).roundToInt().coerceIn(0, Short.MAX_VALUE.toInt()).toShort()
                else -> (sample * 32768.0f).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), 0).toShort()
            }
        }
        return output
    }
}
