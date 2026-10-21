package org.itantra.core.pipeline

import android.util.Log
import org.itantra.core.vad.VadConfig
import org.itantra.core.vad.VadResult

/**
 * Thread-safe collector that buffers raw PCM16 audio during active speech periods
 * determined by Silero VAD state transitions.
 *
 * Preserves pre-speech audio context (~200ms) to ensure leading phonemes are not clipped.
 */
class SpeechSegmentCollector(
    private val sampleRate: Int = 16000,
    private val preSpeechBufferSizeMs: Int = 200,
    private val minSpeechDurationMs: Int = 250,
    private val maxSegmentDurationSec: Int = 30,
    private val onSegmentReady: (audio: ShortArray, durationMs: Long, speechEndNanos: Long, segmentIndex: Int, startTimestamp: Long, endTimestamp: Long) -> Unit
) {
    companion object {
        private const val TAG = "SpeechCollector"
    }

    private val lock = Any()

    // Circular pre-speech buffer to retain audio immediately preceding VAD speech detection
    private val preSpeechCapacity = (sampleRate * preSpeechBufferSizeMs) / 1000
    private val preSpeechRingBuffer = ShortArray(preSpeechCapacity)
    private var preSpeechWritePos = 0
    private var preSpeechCount = 0

    // Active speech buffer
    private val maxCapacitySamples = sampleRate * maxSegmentDurationSec
    private var activeSpeechBuffer = ArrayList<Short>(sampleRate * 5) // Initial 5s capacity
    private var isCollecting = false
    private var speechStartTimestamp = 0L
    private var startSessionMs = 0L
    private var totalSegmentsCount = 0

    /**
     * Feeds incoming PCM16 audio chunk and corresponding VAD results.
     *
     * @param pcmChunk Raw PCM 16-bit audio chunk.
     * @param readCount Valid samples in [pcmChunk].
     * @param vadResults VAD classification results for frames processed in this chunk.
     * @param sessionMs Monotonic milliseconds since session start.
     */
    fun onAudioData(pcmChunk: ShortArray, readCount: Int, vadResults: List<VadResult>, sessionMs: Long = 0L) {
        synchronized(lock) {
            var speechStartDetected = false
            var speechEndDetected = false

            for (vad in vadResults) {
                if (vad.isSpeechStart) {
                    speechStartDetected = true
                }
                if (vad.isSpeechEnd) {
                    speechEndDetected = true
                }
            }

            // 1. Handle Speech Start Transition
            if (speechStartDetected && !isCollecting) {
                isCollecting = true
                speechStartTimestamp = System.currentTimeMillis()
                startSessionMs = sessionMs
                activeSpeechBuffer.clear()

                // Prepend pre-speech ring buffer
                if (preSpeechCount > 0) {
                    val startPos = if (preSpeechCount < preSpeechCapacity) 0 else preSpeechWritePos
                    for (i in 0 until preSpeechCount) {
                        val idx = (startPos + i) % preSpeechCapacity
                        activeSpeechBuffer.add(preSpeechRingBuffer[idx])
                    }
                }
                Log.d(TAG, "Speech start: initialized buffer with $preSpeechCount pre-speech samples at sessionMs=$sessionMs.")
            }

            // 2. Accumulate samples during active speech
            if (isCollecting) {
                for (i in 0 until readCount) {
                    if (activeSpeechBuffer.size < maxCapacitySamples) {
                        activeSpeechBuffer.add(pcmChunk[i])
                    }
                }
            } else {
                // Buffer pre-speech in circular buffer when idle
                for (i in 0 until readCount) {
                    preSpeechRingBuffer[preSpeechWritePos] = pcmChunk[i]
                    preSpeechWritePos = (preSpeechWritePos + 1) % preSpeechCapacity
                    if (preSpeechCount < preSpeechCapacity) {
                        preSpeechCount++
                    }
                }
            }

            // 3. Handle Speech End Transition
            if (speechEndDetected && isCollecting) {
                val speechEndNanos = System.nanoTime()
                val speechEndTimestamp = System.currentTimeMillis()
                val endSessionMs = sessionMs
                isCollecting = false

                val totalSamples = activeSpeechBuffer.size
                val durationMs = (totalSamples.toLong() * 1000L) / sampleRate

                if (durationMs >= minSpeechDurationMs) {
                    val segmentIndex = ++totalSegmentsCount
                    val segmentAudio = ShortArray(totalSamples)
                    for (i in 0 until totalSamples) {
                        segmentAudio[i] = activeSpeechBuffer[i]
                    }
                    Log.i(TAG, "SEGMENT_COMPLETE\nindex=$segmentIndex\nstartSessionMs=$startSessionMs\nendSessionMs=$endSessionMs\ndurationMs=$durationMs\nsampleCount=$totalSamples")
                    onSegmentReady(segmentAudio, durationMs, speechEndNanos, segmentIndex, speechStartTimestamp, speechEndTimestamp)
                } else {
                    Log.d(TAG, "Speech end: discarded short artifact segment ($durationMs ms < $minSpeechDurationMs ms).")
                }
                activeSpeechBuffer.clear()
            }
        }
    }

    /**
     * Resets collector state and clears all active buffers.
     */
    fun reset() {
        synchronized(lock) {
            isCollecting = false
            activeSpeechBuffer.clear()
            preSpeechCount = 0
            preSpeechWritePos = 0
            speechStartTimestamp = 0L
            startSessionMs = 0L
            totalSegmentsCount = 0
        }
    }
}
