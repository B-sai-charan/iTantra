package org.itantra.core.stt

/**
 * Minimal offline ASR engine interface for M1 Speech-to-Text pipeline.
 */
interface AsrEngine {
    /**
     * Transcribes raw PCM16 audio samples into text.
     *
     * @param audio Raw PCM 16-bit mono audio samples.
     * @param sampleRate Sampling rate in Hz (standard is 16000).
     * @param language Optional target language code (e.g., "en", "hi"). If null, engine default or auto-detect is used.
     * @return [AsrResult] containing the transcript and execution metrics.
     */
    suspend fun transcribe(
        audio: ShortArray,
        sampleRate: Int = 16000,
        language: String? = null
    ): AsrResult

    /**
     * Releases underlying native models and runtime resources.
     */
    fun close()
}
