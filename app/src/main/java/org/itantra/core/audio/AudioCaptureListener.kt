package org.itantra.core.audio

/**
 * Listener interface for receiving raw PCM16 audio buffers and lifecycle events.
 */
interface AudioCaptureListener {
    /**
     * Invoked when a new chunk of 16-bit PCM mono audio data is ready.
     *
     * @param buffer Raw PCM16 audio sample array.
     * @param readCount Number of valid audio samples in [buffer].
     */
    fun onAudioFrame(buffer: ShortArray, readCount: Int)

    /**
     * Invoked periodically or per-frame with updated telemetry and real-time RMS amplitude.
     */
    fun onAudioTelemetry(metrics: AudioCaptureMetrics, rmsDbfs: Double) {}

    /**
     * Invoked when an audio recording error occurs during initialization or capture.
     *
     * @param errorCode Numeric error identifier (e.g., from AudioRecord or custom error).
     * @param message Human-readable error description.
     */
    fun onError(errorCode: Int, message: String)

    /**
     * Invoked when the capture lifecycle state transitions.
     *
     * @param state New lifecycle state.
     */
    fun onStateChanged(state: AudioCaptureState)
}
