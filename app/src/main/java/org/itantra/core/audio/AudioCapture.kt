package org.itantra.core.audio

/**
 * Public abstraction for offline audio capture in the M1 subsystem.
 *
 * Exposes lifecycle control and telemetry without leaking native Android AudioRecord
 * classes into outer components.
 */
interface AudioCapture {
    /**
     * Current lifecycle state of this audio capture instance.
     */
    val state: AudioCaptureState

    /**
     * True if audio is actively recording and emitting PCM frames.
     */
    val isRecording: Boolean

    /**
     * Telemetry metrics including sample rate, buffer size, frames read, and duration.
     */
    val metrics: AudioCaptureMetrics

    /**
     * Starts continuous background audio capture on a dedicated thread.
     * Safe against concurrent calls.
     *
     * @param listener Callback receiver for audio frames and lifecycle changes.
     * @return [Result.success] if capture started successfully, or [Result.failure] with error.
     */
    fun start(listener: AudioCaptureListener): Result<Unit>

    /**
     * Stops continuous audio capture cleanly without throwing.
     * Safe to invoke repeatedly or when already stopped.
     */
    fun stop()

    /**
     * Releases underlying hardware audio resources.
     * Safe to invoke multiple times.
     */
    fun release()
}
