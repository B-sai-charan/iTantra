package org.itantra.core.vad

/**
 * Public abstraction for Voice Activity Detection in the M1 subsystem.
 *
 * Evaluates audio frames and determines speech probability while maintaining
 * recurrent state across consecutive streaming chunks.
 */
interface VadDetector : AutoCloseable {
    /**
     * Active configuration for this VAD instance.
     */
    val config: VadConfig

    /**
     * True if the underlying ONNX runtime session is initialized and ready for inference.
     */
    val isInitialized: Boolean

    /**
     * Total number of inferences executed.
     */
    val totalInferences: Long

    /**
     * Average inference latency in milliseconds (telemetry).
     */
    val averageInferenceLatencyMs: Double

    /**
     * Evaluates a PCM 16-bit mono audio frame (512 samples for Silero v5 @ 16kHz)
     * and returns the speech classification result.
     *
     * Thread-safe against concurrent invocations.
     *
     * @param samples Raw 16-bit PCM samples.
     * @return [VadResult] containing speech determination and raw probability.
     */
    fun process(samples: ShortArray): VadResult

    /**
     * Resets the recurrent neural network state and rolling context buffers to zeros.
     * Must be called when starting a new utterance or stream.
     */
    fun reset()

    /**
     * Releases underlying ONNX session and native runtime resources.
     */
    override fun close()
}
