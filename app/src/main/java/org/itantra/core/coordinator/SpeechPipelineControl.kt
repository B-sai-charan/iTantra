package org.itantra.core.coordinator

import org.itantra.core.pipeline.SpeechPipeline
import org.itantra.core.pipeline.SpeechPipelineListener

/**
 * Controller abstraction over the M1 [SpeechPipeline] allowing clean lifecycle orchestration
 * and deterministic testing without Android hardware.
 */
interface SpeechPipelineControl {
    /**
     * Starts continuous audio capture and speech transcription.
     */
    fun start(listener: SpeechPipelineListener): Result<Unit>

    /**
     * Stops continuous capture without releasing models.
     */
    fun stop()

    /**
     * Releases all native resources.
     */
    fun release()
}

/**
 * Default implementation wrapping a concrete M1 [SpeechPipeline] instance.
 */
class DefaultSpeechPipelineControl(
    val pipeline: SpeechPipeline
) : SpeechPipelineControl {
    override fun start(listener: SpeechPipelineListener): Result<Unit> = pipeline.start(listener)
    override fun stop() = pipeline.stop()
    override fun release() = pipeline.release()
}
