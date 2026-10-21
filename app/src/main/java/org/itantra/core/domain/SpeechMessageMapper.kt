package org.itantra.core.domain

import org.itantra.core.stt.AsrResult

/**
 * Deterministic, side-effect-free mapper responsible for converting M1 [AsrResult]
 * into the M1 -> M2 boundary domain model [SpeechMessage].
 */
object SpeechMessageMapper {

    /**
     * Maps an [AsrResult] into an immutable [SpeechMessage].
     *
     * @param asrResult The recognized ASR result emitted by the speech engine.
     * @param timestamp Explicit epoch timestamp in milliseconds representing boundary acquisition time.
     *                  Defaults to [System.currentTimeMillis].
     * @return An immutable [SpeechMessage] containing transcribed speech and associated metadata.
     */
    fun fromAsrResult(
        asrResult: AsrResult,
        timestamp: Long = System.currentTimeMillis()
    ): SpeechMessage {
        return SpeechMessage(
            text = asrResult.text,
            language = asrResult.language,
            timestamp = timestamp,
            confidence = asrResult.confidence
        )
    }
}

/**
 * Extension function to map an [AsrResult] directly to [SpeechMessage].
 */
fun AsrResult.toSpeechMessage(timestamp: Long = System.currentTimeMillis()): SpeechMessage {
    return SpeechMessageMapper.fromAsrResult(this, timestamp)
}
