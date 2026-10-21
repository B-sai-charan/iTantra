package org.itantra.core.stt

/**
 * Minimal offline ASR transcription result representation for M1 pipeline.
 *
 * @property text Transcribed text returned by the ASR engine.
 * @property language Detected or requested language tag (e.g. "en", "hi").
 * @property confidence Defensible confidence score, or null if uncomputed/unsupported.
 * @property inferenceTimeMs Wall-clock duration of the inference decoding pass in milliseconds.
 * @property audioDurationMs Duration of the input audio in milliseconds calculated from sample count.
 */
data class AsrResult(
    val text: String,
    val language: String,
    val confidence: Float?,
    val inferenceTimeMs: Long,
    val audioDurationMs: Long
)
