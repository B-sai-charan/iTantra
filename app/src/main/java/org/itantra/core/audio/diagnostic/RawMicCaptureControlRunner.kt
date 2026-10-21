package org.itantra.core.audio.diagnostic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.itantra.core.audio.AudioCaptureConfig
import org.itantra.core.audio.AudioCaptureListener
import org.itantra.core.audio.AudioCaptureMetrics
import org.itantra.core.audio.AudioCaptureState
import org.itantra.core.audio.AudioRecordCapture
import org.itantra.core.stt.SherpaWhisperAsrEngine
import org.itantra.core.stt.benchmark.IndicWerCalculator
import org.itantra.core.stt.benchmark.TextNormalizer
import org.itantra.core.stt.benchmark.WerCalculator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Diagnostic runner for TASK: M1-RAW-MIC-CAPTURE-CONTROL-01.
 *
 * Captures raw physical microphone audio for an exact fixed duration of 8.000 seconds
 * (128,000 samples @ 16,000 Hz, Mono, 16-bit Linear PCM) with NO VAD, NO segmentation,
 * and NO preprocessing.
 */
object RawMicCaptureControlRunner {

    private const val TAG = "RAW_MIC_CAPTURE"

    const val EXACT_SAMPLE_COUNT = 128000 // Exactly 8.000 seconds at 16 kHz
    const val SAMPLE_RATE = 16000

    const val ENGLISH_REFERENCE = "The quick brown fox jumps over the lazy dog."
    const val HINDI_REFERENCE = "नमस्ते, यह iTantra का हिंदी परीक्षण है।"

    data class RawCaptureMetrics(
        val testLabel: String,
        val outputFileName: String,
        val wavFilePath: String,
        val wavFileSize: Long,
        val sha256: String,
        val sampleRate: Int,
        val channelCount: Int,
        val bitDepth: Int,
        val sampleCount: Int,
        val durationMs: Long,
        val durationSeconds: Double,
        val minSample: Short,
        val maxSample: Short,
        val peakAbsoluteSample: Int,
        val peakDbfs: Double,
        val rms: Double,
        val rmsDbfs: Double,
        val crestFactor: Double,
        val crestFactorDb: Double,
        val clippingCount: Long,
        val clippingPercentage: Double,
        val zeroCount: Long,
        val zeroPercentage: Double,
        val pctAboveMinus40Dbfs: Double,
        val pctAboveMinus30Dbfs: Double,
        val pctAboveMinus20Dbfs: Double,
        val pctAboveMinus10Dbfs: Double
    )

    data class RawCaptureAsrResult(
        val language: String,
        val referenceText: String,
        val rawHypothesis: String,
        val normalizedHypothesis: String,
        val normalizedReference: String,
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int,
        val hits: Int,
        val referenceTokenCount: Int,
        val wer: Double,
        val cer: Double,
        val isExactMatch: Boolean,
        val inferenceTimeMs: Long,
        val rtf: Double
    )

    data class RawCaptureControlResult(
        val metrics: RawCaptureMetrics,
        val asrResult: RawCaptureAsrResult?
    )

    /**
     * Executes fixed 8-second continuous raw microphone capture without VAD,
     * persists the exact 128,000-sample PCM16 WAV, and runs Whisper evaluation.
     */
    suspend fun captureFixed8Seconds(
        context: Context,
        testLabel: String,
        outputFileName: String,
        referenceText: String,
        language: String
    ): RawCaptureControlResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "STARTING RAW 8-SECOND CAPTURE: $testLabel ($outputFileName)")
        Log.i(TAG, "Target: 128,000 samples @ 16kHz Mono PCM16 (8.000 s)")
        Log.i(TAG, "Reference: \"$referenceText\"")
        Log.i(TAG, "Language: \"$language\"")
        Log.i(TAG, "NO VAD, NO TRIMMING, NO PREPROCESSING")
        Log.i(TAG, "=========================================================")

        val audioConfig = AudioCaptureConfig() // Production AudioRecord config (16kHz, Mono, PCM16, VOICE_RECOGNITION)
        val audioCapture = AudioRecordCapture(audioConfig)

        val assembledSamples = ShortArray(EXACT_SAMPLE_COUNT)
        var samplesCollected = 0
        val captureFinished = CompletableDeferred<Unit>()

        val captureListener = object : AudioCaptureListener {
            override fun onAudioFrame(buffer: ShortArray, readCount: Int) {
                if (readCount <= 0 || captureFinished.isCompleted) return

                synchronized(assembledSamples) {
                    val remaining = EXACT_SAMPLE_COUNT - samplesCollected
                    if (remaining > 0) {
                        val toCopy = minOf(readCount, remaining)
                        System.arraycopy(buffer, 0, assembledSamples, samplesCollected, toCopy)
                        samplesCollected += toCopy

                        if (samplesCollected >= EXACT_SAMPLE_COUNT && !captureFinished.isCompleted) {
                            Log.i(TAG, "[$testLabel] Reached exact target: $samplesCollected samples")
                            captureFinished.complete(Unit)
                        }
                    }
                }
            }

            override fun onAudioTelemetry(metrics: AudioCaptureMetrics, rmsDbfs: Double) {}
            override fun onStateChanged(state: AudioCaptureState) {
                Log.i(TAG, "[$testLabel] AudioRecord state: $state")
            }
            override fun onError(errorCode: Int, message: String) {
                Log.e(TAG, "[$testLabel] AudioRecord error: code=$errorCode, msg=$message")
            }
        }

        val startRes = audioCapture.start(captureListener)
        if (startRes.isFailure) {
            throw IllegalStateException("Failed to start AudioRecord: ${startRes.exceptionOrNull()?.message}")
        }

        Log.i(TAG, "[$testLabel] AudioRecord started. Capturing 8 seconds...")

        // Await exact 8-second collection with safety timeout (12s)
        val completed = withTimeoutOrNull(12000L) {
            captureFinished.await()
        }

        Log.i(TAG, "[$testLabel] Stopping AudioRecord...")
        audioCapture.stop()
        audioCapture.release()

        val finalSamples = if (samplesCollected == EXACT_SAMPLE_COUNT) {
            assembledSamples
        } else {
            Log.w(TAG, "[$testLabel] Collected $samplesCollected / $EXACT_SAMPLE_COUNT samples before stop")
            assembledSamples.copyOf(samplesCollected)
        }

        require(finalSamples.isNotEmpty()) { "Captured audio is empty" }

        // 1. Save standard 16kHz mono PCM16 WAV
        val outputFile = File(context.filesDir, outputFileName)
        writePcm16Wav(outputFile, finalSamples, audioConfig.sampleRateInHz)
        val wavFileSize = outputFile.length()
        val sha256 = calculateSha256(outputFile)

        Log.i(TAG, "[$testLabel] Saved WAV to ${outputFile.absolutePath} ($wavFileSize bytes, SHA-256: $sha256)")

        // 2. Compute Signal Metrics
        val metrics = computeSignalMetrics(
            samples = finalSamples,
            sampleRate = audioConfig.sampleRateInHz,
            testLabel = testLabel,
            outputFileName = outputFileName,
            wavFilePath = outputFile.absolutePath,
            wavFileSize = wavFileSize,
            sha256 = sha256
        )

        // 3. Run Direct Whisper ASR
        val asrResult = runWhisperEvaluation(
            context = context,
            samples = finalSamples,
            sampleRate = audioConfig.sampleRateInHz,
            referenceText = referenceText,
            language = language
        )

        val result = RawCaptureControlResult(
            metrics = metrics,
            asrResult = asrResult
        )

        logResult(result)
        result
    }

    fun computeSignalMetrics(
        samples: ShortArray,
        sampleRate: Int,
        testLabel: String,
        outputFileName: String,
        wavFilePath: String,
        wavFileSize: Long,
        sha256: String
    ): RawCaptureMetrics {
        val n = samples.size
        val durationS = if (sampleRate > 0) n.toDouble() / sampleRate.toDouble() else 0.0
        val durationMs = (durationS * 1000.0).toLong()

        var minSample: Short = 0
        var maxSample: Short = 0
        var peakAbs = 0
        var sumSamples = 0.0
        var sumSquares = 0.0
        var zeroCount = 0L
        var clipCount = 0L

        var countAboveMinus40 = 0L // |s| >= 328
        var countAboveMinus30 = 0L // |s| >= 1036
        var countAboveMinus20 = 0L // |s| >= 3277
        var countAboveMinus10 = 0L // |s| >= 10363

        for (s in samples) {
            val v = s.toInt()
            if (s < minSample) minSample = s
            if (s > maxSample) maxSample = s
            val absVal = abs(v)
            if (absVal > peakAbs) peakAbs = absVal
            sumSamples += v.toDouble()
            sumSquares += v.toDouble() * v.toDouble()

            if (v == 0) zeroCount++
            if (s == Short.MIN_VALUE || s == Short.MAX_VALUE) clipCount++

            if (absVal >= 328) countAboveMinus40++
            if (absVal >= 1036) countAboveMinus30++
            if (absVal >= 3277) countAboveMinus20++
            if (absVal >= 10363) countAboveMinus10++
        }

        val rms = if (n > 0) sqrt(sumSquares / n) else 0.0
        val rmsDbfs = if (rms > 0.0) 20.0 * log10(rms / 32768.0) else -96.0
        val peakDbfs = if (peakAbs > 0) 20.0 * log10(peakAbs / 32768.0) else -96.0
        val crestFactor = if (rms > 0.0) peakAbs.toDouble() / rms else 0.0
        val crestFactorDb = peakDbfs - rmsDbfs

        val clipPct = if (n > 0) (clipCount.toDouble() / n) * 100.0 else 0.0
        val zeroPct = if (n > 0) (zeroCount.toDouble() / n) * 100.0 else 0.0

        val pctMinus40 = if (n > 0) (countAboveMinus40.toDouble() / n) * 100.0 else 0.0
        val pctMinus30 = if (n > 0) (countAboveMinus30.toDouble() / n) * 100.0 else 0.0
        val pctMinus20 = if (n > 0) (countAboveMinus20.toDouble() / n) * 100.0 else 0.0
        val pctMinus10 = if (n > 0) (countAboveMinus10.toDouble() / n) * 100.0 else 0.0

        return RawCaptureMetrics(
            testLabel = testLabel,
            outputFileName = outputFileName,
            wavFilePath = wavFilePath,
            wavFileSize = wavFileSize,
            sha256 = sha256,
            sampleRate = sampleRate,
            channelCount = 1,
            bitDepth = 16,
            sampleCount = n,
            durationMs = durationMs,
            durationSeconds = durationS,
            minSample = minSample,
            maxSample = maxSample,
            peakAbsoluteSample = peakAbs,
            peakDbfs = peakDbfs,
            rms = rms,
            rmsDbfs = rmsDbfs,
            crestFactor = crestFactor,
            crestFactorDb = crestFactorDb,
            clippingCount = clipCount,
            clippingPercentage = clipPct,
            zeroCount = zeroCount,
            zeroPercentage = zeroPct,
            pctAboveMinus40Dbfs = pctMinus40,
            pctAboveMinus30Dbfs = pctMinus30,
            pctAboveMinus20Dbfs = pctMinus20,
            pctAboveMinus10Dbfs = pctMinus10
        )
    }

    private suspend fun runWhisperEvaluation(
        context: Context,
        samples: ShortArray,
        sampleRate: Int,
        referenceText: String,
        language: String
    ): RawCaptureAsrResult? {
        var engine: SherpaWhisperAsrEngine? = null
        return try {
            engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = language)
            val asrRes = engine.transcribe(samples, sampleRate, language)

            val (normRef, normHyp, werRes) = if (language == "hi") {
                val r = IndicWerCalculator.calculate(referenceText, asrRes.text, normalize = true)
                Triple(r.normalizedReference, r.normalizedHypothesis, r)
            } else {
                val nr = TextNormalizer.normalize(referenceText)
                val nh = TextNormalizer.normalize(asrRes.text)
                val r = WerCalculator.calculate(nr, nh)
                Triple(nr, nh, r)
            }

            val durationMs = if (sampleRate > 0) (samples.size * 1000L) / sampleRate else 0L
            val rtf = if (durationMs > 0) asrRes.inferenceTimeMs.toDouble() / durationMs.toDouble() else 0.0

            RawCaptureAsrResult(
                language = language,
                referenceText = referenceText,
                rawHypothesis = asrRes.text,
                normalizedHypothesis = normHyp,
                normalizedReference = normRef,
                substitutions = werRes.wordStats.substitutions,
                deletions = werRes.wordStats.deletions,
                insertions = werRes.wordStats.insertions,
                hits = werRes.wordStats.hits,
                referenceTokenCount = werRes.wordStats.referenceCount,
                wer = werRes.wordStats.errorRate,
                cer = werRes.charStats.errorRate,
                isExactMatch = werRes.isExactMatch,
                inferenceTimeMs = asrRes.inferenceTimeMs,
                rtf = rtf
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Whisper evaluation failed: ${e.message}", e)
            null
        } finally {
            engine?.close()
        }
    }

    private fun writePcm16Wav(file: File, samples: ShortArray, sampleRate: Int) {
        val totalAudioLen = samples.size.toLong() * 2L
        val totalDataLen = totalAudioLen + 36L
        val byteRate = sampleRate * 1 * 2

        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray())
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(2.toShort())
            putShort(16.toShort())
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

    private fun calculateSha256(file: File): String {
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

    private fun logResult(r: RawCaptureControlResult) {
        val m = r.metrics
        val a = r.asrResult
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [RAW CAPTURE RESULT: ${m.testLabel}] ===")
        Log.i(TAG, "FILE: ${m.outputFileName} (${m.sampleCount} samples, ${m.durationMs}ms, ${m.durationSeconds}s)")
        Log.i(TAG, "SHA-256: ${m.sha256}")
        Log.i(TAG, "SIGNAL: RMS=${"%.2f".format(m.rmsDbfs)} dBFS, Peak=${m.peakAbsoluteSample} (${"%.2f".format(m.peakDbfs)} dBFS), CrestFactor=${"%.2f".format(m.crestFactor)} (${"%.2f".format(m.crestFactorDb)} dB)")
        Log.i(TAG, "DISTRIBUTION: >-40dBFS=${"%.1f".format(m.pctAboveMinus40Dbfs)}%, >-30dBFS=${"%.1f".format(m.pctAboveMinus30Dbfs)}%, >-20dBFS=${"%.1f".format(m.pctAboveMinus20Dbfs)}%, >-10dBFS=${"%.1f".format(m.pctAboveMinus10Dbfs)}%")
        Log.i(TAG, "STATS: Zero=${"%.2f".format(m.zeroPercentage)}%, Clip=${"%.3f".format(m.clippingPercentage)}%")
        if (a != null) {
            Log.i(TAG, "ASR (${a.language}): WER=${"%.1f".format(a.wer * 100)}%, CER=${"%.1f".format(a.cer * 100)}%, Time=${a.inferenceTimeMs}ms (RTF ${"%.3f".format(a.rtf)})")
            Log.i(TAG, "RAW HYP: \"${a.rawHypothesis}\"")
        }
        Log.i(TAG, "=========================================================")
    }
}
