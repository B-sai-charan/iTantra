package org.itantra.core.audio

/**
 * Real-time runtime telemetry for [AudioCapture].
 *
 * Tracks:
 * - Configured sample rate, channel count, encoding
 * - Hardware minBufferSize reported by Android AudioRecord
 * - Actual allocated internal buffer size
 * - Cumulative samples captured
 * - Cumulative frames/chunks emitted
 * - Active capture duration in milliseconds
 * - Error count and last error message
 */
data class AudioCaptureMetrics(
    val sampleRate: Int,
    val minBufferSize: Int,
    val allocatedBufferSize: Int,
    val chunkSampleSize: Int,
    var totalSamplesCaptured: Long = 0L,
    var totalFramesRead: Long = 0L,
    var captureDurationMs: Long = 0L,
    var errorCount: Long = 0L,
    var lastError: String? = null
) {
    /**
     * Resets transient counters prior to a new capture session.
     */
    fun resetSession() {
        totalSamplesCaptured = 0L
        totalFramesRead = 0L
        captureDurationMs = 0L
        errorCount = 0L
        lastError = null
    }
}
