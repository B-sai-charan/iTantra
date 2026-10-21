package org.itantra.core.communication.protocol

import kotlin.math.roundToInt

/**
 * Quantization logic for acoustic/language confidence scores in the M2 wire protocol.
 * Based on Section K of docs/communication/M2_PROTOCOL_SPEC.md.
 */
object ConfidenceQuantizer {

    const val MIN_QUANTIZED: Int = 0
    const val MAX_QUANTIZED: Int = 100

    /**
     * Quantizes a float confidence [0.0, 1.0] into an 8-bit unsigned integer in range [0, 100].
     *
     * Invariant:
     * - `null` input returns `null` (physically omitted on wire).
     * - `0.0f` scales to `0`.
     * - `1.0f` scales to `100`.
     * - Values are rounded via standard half-up rounding and clamped to [0, 100].
     *
     * @param confidence Float score between 0.0 and 1.0, or null if uncomputed.
     * @return Quantized integer in range 0..100, or null if input was null.
     */
    fun quantize(confidence: Float?): Int? {
        if (confidence == null) return null
        val scaled = (confidence * 100.0f).roundToInt()
        return scaled.coerceIn(MIN_QUANTIZED, MAX_QUANTIZED)
    }

    /**
     * Dequantizes an integer in range [0, 100] back into float score [0.0, 1.0].
     *
     * @param quantized Integer in range 0..100, or null.
     * @return Float confidence in range 0.0..1.0, or null.
     */
    fun dequantize(quantized: Int?): Float? {
        if (quantized == null) return null
        require(quantized in MIN_QUANTIZED..MAX_QUANTIZED) {
            "Quantized confidence must be between $MIN_QUANTIZED and $MAX_QUANTIZED, got: $quantized"
        }
        return quantized / 100.0f
    }
}
