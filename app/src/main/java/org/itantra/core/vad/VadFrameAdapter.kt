package org.itantra.core.vad

/**
 * Thread-safe adapter that bridges AudioRecord chunk sizes (e.g., 320 samples / 20ms)
 * to Silero VAD frame requirements (512 samples / 32ms).
 *
 * Buffering Behavior:
 * - Accumulates incoming PCM16 samples in an internal FIFO buffer.
 * - Dispatches a 512-sample evaluation frame to [vadDetector] whenever >= 512 samples are available.
 * - Added buffering delay: Audio is evaluated every 32ms on average, introducing a maximum
 *   buffering accumulation latency equal to one frame period (32ms).
 */
class VadFrameAdapter(
    val vadDetector: VadDetector,
    val targetFrameSize: Int = VadConfig.SILERO_V5_FRAME_SIZE
) {
    private val buffer = ArrayList<Short>(targetFrameSize * 2)
    private val lock = Any()

    /**
     * Number of unconsumed samples currently in the accumulator buffer.
     */
    val pendingSampleCount: Int
        get() = synchronized(lock) { buffer.size }

    /**
     * Feeds incoming PCM16 audio samples and returns any [VadResult]s produced
     * by full 512-sample frame evaluations.
     *
     * @param pcmChunk Raw PCM 16-bit audio chunk.
     * @param readCount Valid number of samples in [pcmChunk].
     * @return List of [VadResult] items generated during this chunk processing.
     */
    fun process(pcmChunk: ShortArray, readCount: Int = pcmChunk.size): List<VadResult> {
        val results = mutableListOf<VadResult>()
        synchronized(lock) {
            for (i in 0 until readCount) {
                buffer.add(pcmChunk[i])
            }

            while (buffer.size >= targetFrameSize) {
                val frame = ShortArray(targetFrameSize)
                for (i in 0 until targetFrameSize) {
                    frame[i] = buffer[i]
                }
                // Discard consumed 512 samples
                buffer.subList(0, targetFrameSize).clear()

                val vadResult = vadDetector.process(frame)
                results.add(vadResult)
            }
        }
        return results
    }

    /**
     * Resets both the internal sample accumulator and the underlying VAD recurrent state.
     */
    fun reset() {
        synchronized(lock) {
            buffer.clear()
            vadDetector.reset()
        }
    }
}
