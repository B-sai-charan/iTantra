package org.itantra.core.pipeline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.itantra.core.audio.AudioCapture
import org.itantra.core.audio.AudioCaptureConfig
import org.itantra.core.audio.AudioCaptureListener
import org.itantra.core.audio.AudioCaptureState
import org.itantra.core.audio.AudioRecordCapture
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.domain.SpeechMessageMapper
import org.itantra.core.stt.AsrEngine
import org.itantra.core.stt.AsrResult
import org.itantra.core.stt.SherpaWhisperAsrEngine
import org.itantra.core.vad.SileroVadEngine
import org.itantra.core.vad.VadConfig
import org.itantra.core.vad.VadDetector
import org.itantra.core.vad.VadFrameAdapter
import org.itantra.core.vad.VadResult

/**
 * Listener interface for full M1 Speech Pipeline events, local transcription outputs,
 * and boundary [SpeechMessage] emissions.
 */
interface SpeechPipelineListener {
    fun onSpeechStart()
    fun onSpeechEnd(durationMs: Long)
    fun onAsrStart(segmentDurationMs: Long)
    fun onAsrResult(result: AsrResult, postSpeechLatencyMs: Long, segmentIndex: Int)
    fun onSpeechMessage(message: SpeechMessage) {}
    fun onVadProbability(probability: Float, isSpeech: Boolean)
    fun onAudioTelemetry(metrics: org.itantra.core.audio.AudioCaptureMetrics, rmsDbfs: Double) {}
    fun onError(message: String)
    fun onStateChanged(state: AudioCaptureState)
}

/**
 * Integrated M1 Speech Pipeline orchestrator:
 * MIC (AudioRecord) -> Silero VAD v5 -> SpeechSegmentCollector -> Sherpa Whisper ASR -> SpeechMessage -> [SpeechMessageSink].
 *
 * @param context Android Application Context (optional if components are injected).
 * @param audioConfig Audio capture configuration (sample rate, channel, format).
 * @param vadConfig Silero VAD detection parameters.
 * @param targetLanguage Target ASR transcription language code (e.g. "en", "hi").
 * @param messageSink Optional injected communication boundary sink (e.g. SpeechMessageTransportAdapter).
 * @param injectedAudioCapture Optional injected audio capture instance for testing.
 * @param injectedVadDetector Optional injected VAD detector instance for testing.
 * @param injectedAsrEngine Optional injected ASR engine instance for testing.
 */
class SpeechPipeline(
    private val context: Context? = null,
    private val audioConfig: AudioCaptureConfig = AudioCaptureConfig(),
    private val vadConfig: VadConfig = VadConfig(),
    private val targetLanguage: String = "en",
    private val messageSink: SpeechMessageSink? = null,
    private val injectedAudioCapture: AudioCapture? = null,
    private val injectedVadDetector: VadDetector? = null,
    private val injectedAsrEngine: AsrEngine? = null
) {
    companion object {
        private const val TAG = "SpeechPipeline"
    }

    private val pipelineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Data class for queued ASR jobs
    private data class AsrJob(
        val audio: ShortArray,
        val durationMs: Long,
        val speechEndNanos: Long,
        val segmentIndex: Int,
        val startTimestamp: Long,
        val endTimestamp: Long
    )

    // Serial unbounded channel for ASR tasks to prevent blocking capture thread
    private val asrChannel = Channel<AsrJob>(Channel.UNLIMITED)

    private val audioCapture: AudioCapture = injectedAudioCapture ?: AudioRecordCapture(audioConfig)
    private var vadDetector: VadDetector? = injectedVadDetector
    private var vadAdapter: VadFrameAdapter? = injectedVadDetector?.let { VadFrameAdapter(it) }
    private var asrEngine: AsrEngine? = injectedAsrEngine
    private var segmentCollector: SpeechSegmentCollector? = null

    private var listener: SpeechPipelineListener? = null

    @Volatile
    private var isRunning = false
    private var sessionStartTimeNanos = 0L

    init {
        startAsrWorker()
    }

    /**
     * Initializes VAD and ASR native runtimes.
     */
    fun initialize(): Result<Unit> {
        return try {
            val detector = vadDetector ?: run {
                val ctx = context ?: throw IllegalStateException("Context required when VadDetector is not injected")
                Log.i(TAG, "[INIT] Loading Silero VAD v5 (threshold=${vadConfig.threshold}, minSpeech=${vadConfig.minSpeechDurationMs}ms, minSilence=${vadConfig.minSilenceDurationMs}ms)...")
                val d = SileroVadEngine.fromAsset(ctx, vadConfig)
                vadDetector = d
                d
            }

            if (vadAdapter == null) {
                vadAdapter = VadFrameAdapter(detector)
            }

            if (asrEngine == null) {
                val ctx = context ?: throw IllegalStateException("Context required when AsrEngine is not injected")
                Log.i(TAG, "[INIT] Loading Sherpa Whisper ASR engine (targetLanguage=$targetLanguage)...")
                asrEngine = SherpaWhisperAsrEngine(context = ctx, defaultLanguage = targetLanguage)
            }

            segmentCollector = SpeechSegmentCollector(
                sampleRate = audioConfig.sampleRateInHz,
                minSpeechDurationMs = vadConfig.minSpeechDurationMs,
                onSegmentReady = { audio, durationMs, speechEndNanos, segmentIndex, startTimestamp, endTimestamp ->
                    listener?.onSpeechEnd(durationMs)
                    asrChannel.trySend(AsrJob(audio, durationMs, speechEndNanos, segmentIndex, startTimestamp, endTimestamp))
                }
            )

            Log.i(TAG, "[INIT SUCCESS] All M1 pipeline components initialized.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "[INIT FAILED] Pipeline initialization error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Starts continuous audio capture and real-time VAD processing.
     */
    fun start(pipelineListener: SpeechPipelineListener): Result<Unit> {
        listener = pipelineListener

        if (vadDetector == null || asrEngine == null || segmentCollector == null) {
            val initRes = initialize()
            if (initRes.isFailure) {
                return initRes
            }
        }

        vadAdapter?.reset()
        segmentCollector?.reset()
        sessionStartTimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
        isRunning = true

        val sessionStartWall = System.currentTimeMillis()
        Log.i(TAG, "SESSION_START\ntimestamp=$sessionStartWall")

        var lastVadSampleMs = 0L
        var lastAudioCaptureLogMs = 0L

        val captureListener = object : AudioCaptureListener {
            override fun onAudioFrame(buffer: ShortArray, readCount: Int) {
                if (!isRunning) return

                val sessionMs = (android.os.SystemClock.elapsedRealtimeNanos() - sessionStartTimeNanos) / 1_000_000

                // 1. Evaluate frame in Silero VAD
                val vadResults = vadAdapter?.process(buffer, readCount) ?: emptyList()

                for (res in vadResults) {
                    listener?.onVadProbability(res.probability, res.isSpeech)
                    if (res.isSpeechStart) {
                        Log.i(TAG, "VAD_TRANSITION\nsessionMs=$sessionMs\nprevious=SILENCE\ncurrent=SPEECH\nprobability=${"%.4f".format(res.probability)}")
                        Log.i(TAG, "[VAD] Speech start detected (prob=${res.probability}) at sessionMs=$sessionMs.")
                        listener?.onSpeechStart()
                    }
                    if (res.isSpeechEnd) {
                        Log.i(TAG, "VAD_TRANSITION\nsessionMs=$sessionMs\nprevious=SPEECH\ncurrent=SILENCE\nprobability=${"%.4f".format(res.probability)}")
                        Log.i(TAG, "[VAD] Speech end detected (prob=${res.probability}) at sessionMs=$sessionMs.")
                    }
                }

                // 2. Periodic ~100ms VAD sample logging
                if (sessionMs - lastVadSampleMs >= 100) {
                    lastVadSampleMs = sessionMs
                    val lastProb = vadResults.lastOrNull()?.probability ?: 0.0f
                    val isSpeech = vadResults.any { it.isSpeech }
                    val stateStr = if (isSpeech) "SPEECH" else "SILENCE"
                    Log.i(TAG, "VAD_SAMPLE\nsessionMs=$sessionMs\nprobability=${"%.4f".format(lastProb)}\nstate=$stateStr")
                }

                // 3. Periodic ~1000ms AUDIO_CAPTURE logging
                if (sessionMs - lastAudioCaptureLogMs >= 1000) {
                    lastAudioCaptureLogMs = sessionMs
                    Log.i(TAG, "AUDIO_CAPTURE\nsessionMs=$sessionMs\ntotalSamples=${audioCapture.metrics.totalSamplesCaptured}\ncaptureDurationMs=${audioCapture.metrics.captureDurationMs}")
                }

                // 4. Feed into SpeechSegmentCollector with session monotonic timestamp
                segmentCollector?.onAudioData(buffer, readCount, vadResults, sessionMs)
            }

            override fun onAudioTelemetry(metrics: org.itantra.core.audio.AudioCaptureMetrics, rmsDbfs: Double) {
                listener?.onAudioTelemetry(metrics, rmsDbfs)
            }

            override fun onError(errorCode: Int, message: String) {
                Log.e(TAG, "[CAPTURE ERROR] Code $errorCode: $message")
                listener?.onError("Capture Error: $message")
            }

            override fun onStateChanged(state: AudioCaptureState) {
                listener?.onStateChanged(state)
            }
        }

        return audioCapture.start(captureListener)
    }

    /**
     * Dedicated serial worker coroutine processing speech segments sequentially.
     */
    private fun startAsrWorker() {
        pipelineScope.launch {
            for (job in asrChannel) {
                val asr = asrEngine
                if (asr == null) {
                    Log.w(TAG, "AsrEngine not available; dropping segment.")
                    continue
                }

                try {
                    Log.i(TAG, "[ASR START] Transcribing speech segment #${job.segmentIndex} (${job.durationMs} ms)...")
                    listener?.onAsrStart(job.durationMs)

                    val asrResult = asr.transcribe(
                        audio = job.audio,
                        sampleRate = audioConfig.sampleRateInHz,
                        language = targetLanguage
                    )

                    val completionNanos = System.nanoTime()
                    val postSpeechLatencyMs = (completionNanos - job.speechEndNanos) / 1_000_000
                    val rtfVal = if (job.durationMs > 0) asrResult.inferenceTimeMs.toDouble() / job.durationMs.toDouble() else 0.0
                    val rtfStr = "%.4f".format(rtfVal)

                    Log.i(TAG, "ASR_RESULT\nsegmentIndex=${job.segmentIndex}\naudioDurationMs=${job.durationMs}\ninferenceTimeMs=${asrResult.inferenceTimeMs}\nrtf=$rtfStr\nlanguage=${asrResult.language}\ntext=\"${asrResult.text}\"")

                    Log.i(TAG, "=== ASR TRANSCRIPTION RESULT ===")
                    Log.i(TAG, "Transcript: \"${asrResult.text}\"")
                    Log.i(TAG, "Speech Duration: ${job.durationMs} ms")
                    Log.i(TAG, "Speech Samples: ${job.audio.size}")
                    Log.i(TAG, "ASR Inference Time: ${asrResult.inferenceTimeMs} ms")
                    Log.i(TAG, "Post-Speech Latency: $postSpeechLatencyMs ms")
                    Log.i(TAG, "RTF: $rtfStr")

                    listener?.onAsrResult(asrResult, postSpeechLatencyMs, job.segmentIndex)

                    // Map completed segment to M1 -> M2 SpeechMessage
                    val speechMessage = SpeechMessageMapper.fromAsrResult(
                        asrResult = asrResult,
                        timestamp = job.startTimestamp
                    )
                    Log.i(TAG, "[DIAGNOSTIC_TRACE] SpeechPipeline emission: RawASRText=\"${asrResult.text}\", ResolvedLang='${asrResult.language}', SpeechMessageText=\"${speechMessage.text}\", SpeechMessageLang='${speechMessage.language}'")
                    listener?.onSpeechMessage(speechMessage)

                    // Emit to communication boundary sink (if configured)
                    messageSink?.let { sink ->
                        try {
                            sink.emit(speechMessage)
                        } catch (e: Exception) {
                            Log.e(TAG, "[COMMUNICATION ERROR] Failed to emit SpeechMessage to sink: ${e.message}", e)
                            listener?.onError("Transport Error: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[ASR ERROR] Transcription failed: ${e.message}", e)
                    listener?.onError("ASR Error: ${e.message}")
                }
            }
        }
    }

    /**
     * Stops continuous capture without destroying models.
     */
    fun stop() {
        isRunning = false
        val sessionStopWall = System.currentTimeMillis()
        Log.i(TAG, "SESSION_STOP\ntimestamp=$sessionStopWall")
        audioCapture.stop()
        segmentCollector?.reset()
        vadAdapter?.reset()
    }

    /**
     * Releases all native resources (AudioRecord, Silero ONNX Session, Sherpa OfflineRecognizer).
     */
    fun release() {
        stop()
        pipelineScope.cancel()
        asrChannel.close()

        audioCapture.release()

        try {
            vadDetector?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing VadDetector: ${e.message}")
        }
        vadDetector = null
        vadAdapter = null

        try {
            asrEngine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing AsrEngine: ${e.message}")
        }
        asrEngine = null
        segmentCollector = null

        Log.i(TAG, "[LIFECYCLE] Full SpeechPipeline released.")
    }
}
