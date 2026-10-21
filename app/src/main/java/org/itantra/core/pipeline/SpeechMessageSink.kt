package org.itantra.core.pipeline

import org.itantra.core.domain.SpeechMessage

/**
 * Communication boundary interface for consuming outbound [SpeechMessage] instances
 * produced by the M1 speech pipeline.
 *
 * Decouples the M1 speech recognition pipeline from concrete M2 communication adapters,
 * network transports, test mocks, or local logging sinks.
 */
fun interface SpeechMessageSink {

    /**
     * Emits a completed [SpeechMessage] produced by the speech pipeline.
     *
     * @param message The recognized speech message containing text, language, timestamp, and confidence.
     * @throws Exception if downstream transport fails (handled gracefully by the pipeline).
     */
    suspend fun emit(message: SpeechMessage)
}
