package org.itantra.core.audio

import android.media.AudioFormat
import android.media.MediaRecorder

/**
 * Configuration for M1 audio capture pipeline.
 *
 * Defaults:
 * - 16,000 Hz sample rate (optimal standard for offline STT/VAD models like Silero & Indic ASR)
 * - Mono channel (1 channel)
 * - PCM 16-bit encoding (2 bytes per sample, native uncompressed)
 * - MediaRecorder.AudioSource.VOICE_RECOGNITION (prefers speech-tuned microphone input, falls back to MIC)
 * - 20ms frame chunk duration (320 samples per frame at 16 kHz)
 */
data class AudioCaptureConfig(
    val sampleRateInHz: Int = DEFAULT_SAMPLE_RATE,
    val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT,
    val audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION,
    val chunkDurationMs: Int = DEFAULT_CHUNK_DURATION_MS
) {
    companion object {
        const val DEFAULT_SAMPLE_RATE = 16000
        const val DEFAULT_CHUNK_DURATION_MS = 20
        const val BYTES_PER_SAMPLE = 2 // 16-bit PCM = 2 bytes
        const val CHANNEL_COUNT = 1    // Mono = 1 channel
    }

    /**
     * Number of PCM 16-bit samples per chunk based on [chunkDurationMs].
     * At 16 kHz and 20ms: (16000 * 20) / 1000 = 320 samples.
     */
    val chunkSampleCount: Int
        get() = (sampleRateInHz * chunkDurationMs) / 1000

    /**
     * Chunk size in bytes.
     * At 16 kHz, 20ms, Mono, 16-bit: 320 * 2 * 1 = 640 bytes.
     */
    val chunkSizeBytes: Int
        get() = chunkSampleCount * BYTES_PER_SAMPLE * CHANNEL_COUNT

    init {
        require(sampleRateInHz > 0) { "sampleRateInHz must be positive, got: $sampleRateInHz" }
        require(chunkDurationMs > 0) { "chunkDurationMs must be positive, got: $chunkDurationMs" }
    }
}
