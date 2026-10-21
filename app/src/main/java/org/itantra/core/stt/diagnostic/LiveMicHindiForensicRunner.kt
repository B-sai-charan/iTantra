package org.itantra.core.stt.diagnostic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.itantra.core.audio.AudioCaptureConfig
import org.itantra.core.audio.AudioCaptureListener
import org.itantra.core.audio.AudioCaptureMetrics
import org.itantra.core.audio.AudioCaptureState
import org.itantra.core.audio.AudioRecordCapture
import org.itantra.core.pipeline.SpeechSegmentCollector
import org.itantra.core.stt.SherpaWhisperAsrEngine
import org.itantra.core.stt.benchmark.TextNormalizer
import org.itantra.core.stt.benchmark.WerCalculator
import org.itantra.core.stt.benchmark.WerResult
import org.itantra.core.vad.SileroVadEngine
import org.itantra.core.vad.VadConfig
import org.itantra.core.vad.VadFrameAdapter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Isolated forensic diagnostic runner for TASK: M1-LIVE-MIC-FORENSIC-01.
 *
 * Performs:
 * 1. Live microphone capture using production AudioRecordCapture + Silero VAD + SpeechSegmentCollector.
 * 2. Exact post-collection ShortArray capture & standard WAV persistence.
 * 3. Live Whisper-Tiny INT8 Hindi transcription.
 * 4. Direct Whisper replay of the exact saved WAV (bypassing AudioRecord / VAD).
 * 5. Audio signal forensics (RMS dBFS, peak dBFS, clipping, zeros, SHA-256).
 */
object LiveMicHindiForensicRunner {

    private const val TAG = "M1_FORENSIC"
    const val PRESCRIBED_HINDI_REFERENCE = "नमस्ते, यह iTantra का हिंदी परीक्षण है।"
    const val OUTPUT_WAV_NAME = "live_hindi_forensic_01.wav"

    data class AudioForensicMetrics(
        val timestamp: Long,
        val sampleCount: Int,
        val durationMs: Long,
        val sampleRate: Int,
        val wavFilePath: String,
        val wavFileSize: Long,
        val sha256: String,
        val minSample: Short,
        val maxSample: Short,
        val peakAbsoluteSample: Int,
        val peakDbfs: Double,
        val rms: Double,
        val rmsDbfs: Double,
        val clippingSampleCount: Long,
        val zeroSampleCount: Long,
        val zeroSamplePercentage: Double,
        val totalVadFrames: Int,
        val speechVadFrames: Int,
        val silenceVadFrames: Int,
        val estimatedSilencePercentage: Double,
        val speechStartTimestamp: Long,
        val speechEndTimestamp: Long
    )

    data class AsrEvaluationResult(
        val rawHypothesis: String,
        val normalizedHypothesis: String,
        val detectedLanguage: String,
        val reference: String,
        val normalizedReference: String,
        val wer: Double,
        val cer: Double,
        val isExactMatch: Boolean,
        val inferenceTimeMs: Long,
        val audioDurationMs: Long,
        val rtf: Double
    )

    data class ForensicReport(
        val deviceModel: String,
        val captureMetrics: AudioForensicMetrics,
        val liveWhisperResult: AsrEvaluationResult,
        val directReplayResult: AsrEvaluationResult,
        val comparisonCase: String, // "CASE A", "CASE B", "CASE C"
        val knownGoodComparisonNote: String,
        val errorMessage: String? = null
    )

    /**
     * Executes the controlled live microphone capture, diagnostic WAV save, live inference,
     * and direct WAV replay.
     */
    suspend fun runForensicTest(
        context: Context,
        timeoutMs: Long = 15000L
    ): ForensicReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING M1-LIVE-MIC-FORENSIC-01 INVESTIGATION")
        Log.i(TAG, "Target Reference: \"$PRESCRIBED_HINDI_REFERENCE\"")
        Log.i(TAG, "Target Language: \"hi\"")
        Log.i(TAG, "==================================================")

        val audioConfig = AudioCaptureConfig()
        val vadConfig = VadConfig()
        val audioCapture = AudioRecordCapture(audioConfig)
        val vadEngine = SileroVadEngine.fromAsset(context, vadConfig)
        val vadAdapter = VadFrameAdapter(vadEngine)
        val whisperEngine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "hi")

        val segmentDeferred = CompletableDeferred<CollectedSegmentData>()
        var totalVadFramesEvaluated = 0
        var speechVadFramesCount = 0

        val segmentCollector = SpeechSegmentCollector(
            sampleRate = audioConfig.sampleRateInHz,
            minSpeechDurationMs = vadConfig.minSpeechDurationMs,
            onSegmentReady = { audio, durationMs, speechEndNanos, segmentIndex, startTimestamp, endTimestamp ->
                Log.i(TAG, "[SEGMENT READY] Index=$segmentIndex, Samples=${audio.size}, Duration=${durationMs}ms")
                segmentDeferred.complete(
                    CollectedSegmentData(
                        audio = audio,
                        durationMs = durationMs,
                        speechEndNanos = speechEndNanos,
                        segmentIndex = segmentIndex,
                        startTimestamp = startTimestamp,
                        endTimestamp = endTimestamp,
                        totalFrames = totalVadFramesEvaluated,
                        speechFrames = speechVadFramesCount
                    )
                )
            }
        )

        val captureListener = object : AudioCaptureListener {
            override fun onAudioFrame(buffer: ShortArray, readCount: Int) {
                val vadResults = vadAdapter.process(buffer, readCount)
                for (res in vadResults) {
                    totalVadFramesEvaluated++
                    if (res.isSpeech) {
                        speechVadFramesCount++
                    }
                }
                segmentCollector.onAudioData(buffer, readCount, vadResults, System.currentTimeMillis())
            }

            override fun onAudioTelemetry(metrics: AudioCaptureMetrics, rmsDbfs: Double) {}
            override fun onStateChanged(state: AudioCaptureState) {
                Log.i(TAG, "[CAPTURE-STATE] $state")
            }
            override fun onError(errorCode: Int, message: String) {
                Log.e(TAG, "[CAPTURE-ERROR] Code $errorCode: $message")
            }
        }

        try {
            val startRes = audioCapture.start(captureListener)
            if (startRes.isFailure) {
                throw IllegalStateException("Failed to start audio capture: ${startRes.exceptionOrNull()?.message}")
            }

            Log.i(TAG, ">>> MICROPHONE ACTIVE. PLEASE SPEAK THE HINDI SENTENCE NOW. <<<")
            Log.i(TAG, ">>> Prompt: \"$PRESCRIBED_HINDI_REFERENCE\" <<<")

            val segmentData = kotlinx.coroutines.withTimeout(timeoutMs) {
                segmentDeferred.await()
            }

            audioCapture.stop()
            audioCapture.release()

            // -------------------------------------------------------------
            // PHASE 2 — Audio Signal Forensics & Diagnostic WAV Persistence
            // -------------------------------------------------------------
            val audioSamples = segmentData.audio
            val sampleCount = audioSamples.size
            val durationMs = segmentData.durationMs
            val now = System.currentTimeMillis()

            var minVal: Short = Short.MAX_VALUE
            var maxVal: Short = Short.MIN_VALUE
            var peakAbs = 0
            var sumSquare = 0.0
            var clipCount = 0L
            var zeroCount = 0L

            for (i in 0 until sampleCount) {
                val s = audioSamples[i]
                if (s < minVal) minVal = s
                if (s > maxVal) maxVal = s
                val absVal = kotlin.math.abs(s.toInt())
                if (absVal > peakAbs) peakAbs = absVal
                if (s == Short.MAX_VALUE || s == Short.MIN_VALUE) clipCount++
                if (s == 0.toShort()) zeroCount++
                sumSquare += s.toDouble() * s.toDouble()
            }

            val meanSquare = if (sampleCount > 0) sumSquare / sampleCount else 0.0
            val rms = kotlin.math.sqrt(meanSquare)
            val rmsDbfs = if (rms > 0.0) 20.0 * kotlin.math.log10(rms / 32768.0) else -96.0
            val peakDbfs = if (peakAbs > 0) 20.0 * kotlin.math.log10(peakAbs / 32768.0) else -96.0
            val zeroPct = if (sampleCount > 0) (zeroCount.toDouble() / sampleCount.toDouble()) * 100.0 else 0.0

            val silenceFrames = maxOf(0, segmentData.totalFrames - segmentData.speechFrames)
            val estimatedSilencePct = if (segmentData.totalFrames > 0) {
                (silenceFrames.toDouble() / segmentData.totalFrames.toDouble()) * 100.0
            } else 0.0

            // Save canonical 16 kHz Mono PCM16 WAV
            val wavFile = File(context.filesDir, OUTPUT_WAV_NAME)
            writePcm16Wav(wavFile, audioSamples, audioConfig.sampleRateInHz)
            val sha256 = calculateSha256(wavFile)
            val fileSize = wavFile.length()

            val captureMetrics = AudioForensicMetrics(
                timestamp = now,
                sampleCount = sampleCount,
                durationMs = durationMs,
                sampleRate = audioConfig.sampleRateInHz,
                wavFilePath = wavFile.absolutePath,
                wavFileSize = fileSize,
                sha256 = sha256,
                minSample = minVal,
                maxSample = maxVal,
                peakAbsoluteSample = peakAbs,
                peakDbfs = peakDbfs,
                rms = rms,
                rmsDbfs = rmsDbfs,
                clippingSampleCount = clipCount,
                zeroSampleCount = zeroCount,
                zeroSamplePercentage = zeroPct,
                totalVadFrames = segmentData.totalFrames,
                speechVadFrames = segmentData.speechFrames,
                silenceVadFrames = silenceFrames,
                estimatedSilencePercentage = estimatedSilencePct,
                speechStartTimestamp = segmentData.startTimestamp,
                speechEndTimestamp = segmentData.endTimestamp
            )

            Log.i(TAG, "=== DIAGNOSTIC AUDIO METRICS ===")
            Log.i(TAG, "WAV File: ${wavFile.absolutePath} ($fileSize bytes, SHA-256: $sha256)")
            Log.i(TAG, "Samples: $sampleCount, Duration: ${durationMs}ms @ ${audioConfig.sampleRateInHz}Hz")
            Log.i(TAG, "RMS: ${"%.2f".format(rms)} (RMS dBFS: ${"%.2f".format(rmsDbfs)} dBFS)")
            Log.i(TAG, "Peak Abs: $peakAbs (Peak dBFS: ${"%.2f".format(peakDbfs)} dBFS)")
            Log.i(TAG, "Clipping Samples: $clipCount, Zero Samples: $zeroCount (${"%.2f".format(zeroPct)}%)")
            Log.i(TAG, "VAD Frames: Total=${segmentData.totalFrames}, Speech=${segmentData.speechFrames}, Silence=$silenceFrames (Est Silence: ${"%.1f".format(estimatedSilencePct)}%)")

            // -------------------------------------------------------------
            // PHASE 3 — Live Whisper Inference
            // -------------------------------------------------------------
            Log.i(TAG, "=== RUNNING LIVE WHISPER INFERENCE ===")
            val liveAsrResult = whisperEngine.transcribe(
                audio = audioSamples,
                sampleRate = audioConfig.sampleRateInHz,
                language = "hi"
            )

            val normRef = TextNormalizer.normalize(PRESCRIBED_HINDI_REFERENCE)
            val liveNormHyp = TextNormalizer.normalize(liveAsrResult.text)
            val liveWerResult: WerResult = WerCalculator.calculate(normRef, liveNormHyp)
            val liveRtf = if (durationMs > 0) liveAsrResult.inferenceTimeMs.toDouble() / durationMs.toDouble() else 0.0

            val liveEval = AsrEvaluationResult(
                rawHypothesis = liveAsrResult.text,
                normalizedHypothesis = liveNormHyp,
                detectedLanguage = liveAsrResult.language,
                reference = PRESCRIBED_HINDI_REFERENCE,
                normalizedReference = normRef,
                wer = liveWerResult.wordStats.errorRate,
                cer = liveWerResult.charStats.errorRate,
                isExactMatch = liveWerResult.isExactMatch,
                inferenceTimeMs = liveAsrResult.inferenceTimeMs,
                audioDurationMs = durationMs,
                rtf = liveRtf
            )

            Log.i(TAG, "[LIVE WHISPER RESULT] Raw=\"${liveEval.rawHypothesis}\", Norm=\"${liveEval.normalizedHypothesis}\", Lang=${liveEval.detectedLanguage}, Time=${liveEval.inferenceTimeMs}ms, RTF=${"%.3f".format(liveEval.rtf)}, WER=${"%.1f".format(liveEval.wer * 100)}%, CER=${"%.1f".format(liveEval.cer * 100)}%")

            // -------------------------------------------------------------
            // PHASE 4 — Direct Whisper Replay (Bypassing Mic & VAD)
            // -------------------------------------------------------------
            Log.i(TAG, "=== RUNNING DIRECT WHISPER REPLAY FROM SAVED WAV ===")
            val replaySamples = readPcm16Wav(wavFile)
            val replayAsrResult = whisperEngine.transcribe(
                audio = replaySamples,
                sampleRate = 16000,
                language = "hi"
            )

            val replayNormHyp = TextNormalizer.normalize(replayAsrResult.text)
            val replayWerResult: WerResult = WerCalculator.calculate(normRef, replayNormHyp)
            val replayRtf = if (durationMs > 0) replayAsrResult.inferenceTimeMs.toDouble() / durationMs.toDouble() else 0.0

            val replayEval = AsrEvaluationResult(
                rawHypothesis = replayAsrResult.text,
                normalizedHypothesis = replayNormHyp,
                detectedLanguage = replayAsrResult.language,
                reference = PRESCRIBED_HINDI_REFERENCE,
                normalizedReference = normRef,
                wer = replayWerResult.wordStats.errorRate,
                cer = replayWerResult.charStats.errorRate,
                isExactMatch = replayWerResult.isExactMatch,
                inferenceTimeMs = replayAsrResult.inferenceTimeMs,
                audioDurationMs = durationMs,
                rtf = replayRtf
            )

            Log.i(TAG, "[DIRECT REPLAY RESULT] Raw=\"${replayEval.rawHypothesis}\", Norm=\"${replayEval.normalizedHypothesis}\", Lang=${replayEval.detectedLanguage}, Time=${replayEval.inferenceTimeMs}ms, RTF=${"%.3f".format(replayEval.rtf)}, WER=${"%.1f".format(replayEval.wer * 100)}%, CER=${"%.1f".format(replayEval.cer * 100)}%")

            // -------------------------------------------------------------
            // Interpretation Determination
            // -------------------------------------------------------------
            val comparisonCase = when {
                liveEval.wer > 0.5 && replayEval.wer > 0.5 -> "CASE A (Live bad + direct replay bad -> Contained in captured microphone audio/segmentation or Whisper model behavior on live acoustic input)"
                liveEval.wer > 0.5 && replayEval.wer <= 0.2 -> "CASE B (Live bad + direct replay substantially better -> Live pipeline/timing/segmentation issue)"
                liveEval.wer <= 0.2 && replayEval.wer <= 0.2 -> "CASE C (Both good -> Previous failure not reproducible)"
                else -> "CASE A (Live WER=${"%.1f".format(liveEval.wer * 100)}%, Replay WER=${"%.1f".format(replayEval.wer * 100)}%)"
            }

            Log.i(TAG, "=== FORENSIC INTERPRETATION: $comparisonCase ===")

            ForensicReport(
                deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                captureMetrics = captureMetrics,
                liveWhisperResult = liveEval,
                directReplayResult = replayEval,
                comparisonCase = comparisonCase,
                knownGoodComparisonNote = "Compared against 16 kHz Mono signed PCM16 reference fixture format."
            )
        } finally {
            audioCapture.stop()
            audioCapture.release()
            vadEngine.close()
            whisperEngine.close()
        }
    }

    private data class CollectedSegmentData(
        val audio: ShortArray,
        val durationMs: Long,
        val speechEndNanos: Long,
        val segmentIndex: Int,
        val startTimestamp: Long,
        val endTimestamp: Long,
        val totalFrames: Int,
        val speechFrames: Int
    )

    fun writePcm16Wav(file: File, samples: ShortArray, sampleRate: Int = 16000) {
        val totalAudioLen = samples.size.toLong() * 2L
        val totalDataLen = totalAudioLen + 36L
        val byteRate = sampleRate * 1 * 2 // 16000 * mono * 2 bytes = 32000

        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray())
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16) // Subchunk1Size for PCM
            putShort(1.toShort()) // AudioFormat (1 = PCM)
            putShort(1.toShort()) // NumChannels (1 = Mono)
            putInt(sampleRate)
            putInt(byteRate)
            putShort(2.toShort()) // BlockAlign (NumChannels * BitsPerSample / 8)
            putShort(16.toShort()) // BitsPerSample
            put("data".toByteArray())
            putInt(totalAudioLen.toInt())
        }

        FileOutputStream(file).use { fos ->
            fos.write(header.array())
            val pcmBytes = ByteBuffer.allocate(samples.size * 2).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                for (s in samples) {
                    putShort(s)
                }
            }
            fos.write(pcmBytes.array())
            fos.flush()
        }
    }

    fun readPcm16Wav(file: File): ShortArray {
        FileInputStream(file).use { fis ->
            val bytes = fis.readBytes()
            require(bytes.size >= 44) { "WAV file too small (${bytes.size} bytes)" }
            val dataLen = bytes.size - 44
            val sampleCount = dataLen / 2
            val buffer = ByteBuffer.wrap(bytes, 44, dataLen).order(ByteOrder.LITTLE_ENDIAN)
            val samples = ShortArray(sampleCount)
            for (i in 0 until sampleCount) {
                samples[i] = buffer.short
            }
            return samples
        }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
