package org.itantra.core.vad

/**
 * Configuration parameters for Silero Voice Activity Detection v5.
 *
 * @property sampleRate Sample rate in Hz (Silero v5 supports 16000 or 8000 Hz).
 * @property threshold Speech probability threshold [0.0f, 1.0f] (default 0.5f).
 * @property minSpeechDurationMs Minimum active speech duration to trigger a speech-start event.
 * @property minSilenceDurationMs Minimum silence duration to trigger a speech-end event.
 * @property speechPadMs Padding duration in milliseconds around detected speech boundaries.
 * @property modelAssetPath Relative path to the ONNX model in Android assets.
 */
data class VadConfig(
    val sampleRate: Int = 16000,
    val threshold: Float = DEFAULT_THRESHOLD,
    val minSpeechDurationMs: Int = 250,
    val minSilenceDurationMs: Int = 400,
    val speechPadMs: Int = 30,
    val modelAssetPath: String = DEFAULT_MODEL_PATH
) {
    companion object {
        const val DEFAULT_THRESHOLD = 0.5f
        const val DEFAULT_MODEL_PATH = "models/silero_vad.onnx"

        // Silero VAD v5 constants
        const val SILERO_V5_FRAME_SIZE = 512       // 512 new samples (32 ms @ 16kHz)
        const val SILERO_V5_CONTEXT_SIZE = 64      // 64 rolling context samples
        const val SILERO_V5_INPUT_SIZE = 576       // 512 + 64 = 576 total input tensor length
        const val STATE_DIM_0 = 2
        const val STATE_DIM_1 = 1
        const val STATE_DIM_2 = 128
    }

    init {
        require(sampleRate == 16000 || sampleRate == 8000) {
            "Silero VAD only supports 16000 or 8000 Hz, got $sampleRate"
        }
        require(threshold in 0.0f..1.0f) {
            "threshold must be between 0.0 and 1.0, got $threshold"
        }
        require(minSpeechDurationMs >= 0) { "minSpeechDurationMs must be non-negative" }
        require(minSilenceDurationMs >= 0) { "minSilenceDurationMs must be non-negative" }
    }
}
