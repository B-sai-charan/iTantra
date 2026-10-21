package org.itantra.core.domain

/**
 * Domain model representing a recognized speech message emitted by the M1 Speech Subsystem
 * to be consumed by the downstream M2 Communication Subsystem.
 *
 * Design constraints:
 * - Pure Kotlin (zero Android SDK dependencies).
 * - Immutable data class.
 * - Minimal semantic contract containing only verified M1 -> M2 boundary fields.
 *
 * Semantic definitions:
 * @property text The exact transcribed text of the spoken utterance as emitted by STT.
 * @property language The exact language tag (e.g. "en", "hi") supplied by STT.
 * @property timestamp Epoch timestamp in milliseconds representing event/acquisition time at the M1 boundary.
 *                     Note: This is an acquisition timestamp, NOT a guaranteed global unique message ID or deduplication key.
 * @property confidence Acoustic/language confidence score [0.0, 1.0], or null if uncomputed/unsupported by the decoder.
 */
data class SpeechMessage(
    val text: String,
    val language: String,
    val timestamp: Long,
    val confidence: Float? = null
)
