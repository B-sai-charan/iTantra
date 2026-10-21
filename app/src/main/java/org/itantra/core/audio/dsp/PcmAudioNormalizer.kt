package org.itantra.core.audio.dsp

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Per-utterance PCM loudness normalization for offline TTS playback.
 *
 * Measures utterance RMS and peak amplitude, calculates optimal gain to reach
 * a consistent target speech loudness (-17.2 dBFS RMS by default), and enforces
 * a peak headroom margin (10% / ~0.92 dB below full scale) to prevent PCM16 clipping.
 */
object PcmAudioNormalizer {

    const val MAX_PCM16_VALUE = 32767
    const val MIN_PCM16_VALUE = -32768

    /**
     * Target RMS amplitude in PCM16 scale.
     * 4500 out of 32767 corresponds to ~ -17.2 dBFS, optimal for mobile speaker clarity.
     */
    const val DEFAULT_TARGET_RMS = 4500.0f

    /**
     * Safety headroom factor below full scale (0.90 = 10% headroom, peak <= 29490).
     */
    const val DEFAULT_HEADROOM_FACTOR = 0.90f

    /**
     * Maximum allowed peak amplitude under default headroom factor (29490).
     */
    const val DEFAULT_MAX_ALLOWED_PEAK = 29490

    /**
     * Minimum peak amplitude required to consider audio audible.
     * Waveforms below this threshold are treated as silent/near-silent background.
     */
    const val SILENCE_PEAK_THRESHOLD = 50

    /**
     * Minimum RMS required to consider audio audible.
     */
    const val SILENCE_RMS_THRESHOLD = 5.0f

    /**
     * Gain bounds to prevent excessive amplification of background noise or over-attenuation.
     */
    const val MIN_GAIN = 0.05f // -26 dB
    const val MAX_GAIN = 8.0f  // +18 dB

    /**
     * Result of PCM loudness normalization including diagnostic telemetry.
     */
    data class NormalizationResult(
        val samples: ShortArray,
        val originalPeak: Int,
        val originalRms: Float,
        val normalizedPeak: Int,
        val normalizedRms: Float,
        val appliedGain: Float,
        val isClippingProtectionTriggered: Boolean
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as NormalizationResult

            if (!samples.contentEquals(other.samples)) return false
            if (originalPeak != other.originalPeak) return false
            if (originalRms != other.originalRms) return false
            if (normalizedPeak != other.normalizedPeak) return false
            if (normalizedRms != other.normalizedRms) return false
            if (appliedGain != other.appliedGain) return false
            if (isClippingProtectionTriggered != other.isClippingProtectionTriggered) return false

            return true
        }

        override fun hashCode(): Int {
            var result = samples.contentHashCode()
            result = 31 * result + originalPeak
            result = 31 * result + originalRms.hashCode()
            result = 31 * result + normalizedPeak
            result = 31 * result + normalizedRms.hashCode()
            result = 31 * result + appliedGain.hashCode()
            result = 31 * result + isClippingProtectionTriggered.hashCode()
            return result
        }
    }

    /**
     * Calculates the peak absolute amplitude of a 16-bit PCM buffer.
     */
    fun calculatePeak(samples: ShortArray): Int {
        var maxPeak = 0
        for (s in samples) {
            val absVal = kotlin.math.abs(s.toInt()).coerceAtMost(MAX_PCM16_VALUE)
            if (absVal > maxPeak) {
                maxPeak = absVal
            }
        }
        return maxPeak
    }

    /**
     * Calculates the Root-Mean-Square (RMS) amplitude of a 16-bit PCM buffer.
     */
    fun calculateRms(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0.0f
        var sumSquares = 0.0
        for (s in samples) {
            val v = s.toDouble()
            sumSquares += v * v
        }
        return sqrt(sumSquares / samples.size).toFloat()
    }

    /**
     * Normalizes a 16-bit PCM audio buffer to a consistent safe loudness.
     *
     * @param samples Raw input 16-bit signed PCM samples.
     * @param targetRms Target RMS amplitude in PCM16 scale (default: 4500.0f).
     * @param headroomFactor Maximum peak fraction of full scale (default: 0.90f -> 29490).
     * @return [NormalizationResult] containing the normalized buffer and metrics.
     */
    fun normalize(
        samples: ShortArray,
        targetRms: Float = DEFAULT_TARGET_RMS,
        headroomFactor: Float = DEFAULT_HEADROOM_FACTOR
    ): NormalizationResult {
        if (samples.isEmpty()) {
            return NormalizationResult(
                samples = ShortArray(0),
                originalPeak = 0,
                originalRms = 0.0f,
                normalizedPeak = 0,
                normalizedRms = 0.0f,
                appliedGain = 1.0f,
                isClippingProtectionTriggered = false
            )
        }

        val originalPeak = calculatePeak(samples)
        val originalRms = calculateRms(samples)

        // Silent or near-silent: avoid boosting background noise or dividing by zero
        if (originalPeak <= SILENCE_PEAK_THRESHOLD || originalRms <= SILENCE_RMS_THRESHOLD || targetRms <= 0.0f) {
            return NormalizationResult(
                samples = samples.copyOf(),
                originalPeak = originalPeak,
                originalRms = originalRms,
                normalizedPeak = originalPeak,
                normalizedRms = originalRms,
                appliedGain = 1.0f,
                isClippingProtectionTriggered = false
            )
        }

        val maxAllowedPeak = (MAX_PCM16_VALUE * headroomFactor.coerceIn(0.1f, 1.0f)).toInt()

        // 1. Calculate ideal gain to reach target RMS
        val desiredGain = targetRms / originalRms

        // 2. Headroom & clipping protection:
        // Ensure that (originalPeak * gain) does not exceed maxAllowedPeak
        val maxGainWithoutClipping = maxAllowedPeak.toFloat() / originalPeak.toFloat()

        val isClippingProtectionTriggered: Boolean
        val effectiveGain: Float

        if (desiredGain > maxGainWithoutClipping) {
            effectiveGain = maxGainWithoutClipping.coerceIn(MIN_GAIN, MAX_GAIN)
            isClippingProtectionTriggered = true
        } else {
            effectiveGain = desiredGain.coerceIn(MIN_GAIN, MAX_GAIN)
            isClippingProtectionTriggered = false
        }

        // 3. Apply gain with hard clamping to PCM16 boundaries
        val normalized = ShortArray(samples.size)
        for (i in samples.indices) {
            val scaled = (samples[i].toFloat() * effectiveGain).roundToInt()
            normalized[i] = scaled.coerceIn(MIN_PCM16_VALUE, MAX_PCM16_VALUE).toShort()
        }

        val normalizedPeak = calculatePeak(normalized)
        val normalizedRms = calculateRms(normalized)

        return NormalizationResult(
            samples = normalized,
            originalPeak = originalPeak,
            originalRms = originalRms,
            normalizedPeak = normalizedPeak,
            normalizedRms = normalizedRms,
            appliedGain = effectiveGain,
            isClippingProtectionTriggered = isClippingProtectionTriggered
        )
    }
}
