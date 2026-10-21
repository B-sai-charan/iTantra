package org.itantra.core.stt.diagnostic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.itantra.core.stt.SherpaWhisperAsrEngine
import org.itantra.core.stt.benchmark.IndicWerCalculator
import org.itantra.core.stt.benchmark.TextNormalizer
import org.itantra.core.stt.benchmark.WerCalculator
import org.itantra.core.vad.SileroVadEngine
import org.itantra.core.vad.VadConfig
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
 * Diagnostic runner for Task M1-ASR-SEGMENTATION-CONTROL-03.
 *
 * Tests the hypothesis: "Whisper Tiny INT8 is sensitive to substantial trailing silence
 * in the unsegmented fixed-window input."
 *
 * Slices the exact 8.000s raw capture into speech-only and controlled trailing-silence
 * variations without modifying production code, models, or configurations.
 */
object AsrSegmentationControlRunner {

    private const val TAG = "ASR_SEG_CONTROL"

    const val EXPECTED_EN_SHA256 = "5fd0bd41a6cada093ace02871fb63c3eaf15b4cb7389f8d88be028e458ca6695"
    const val EXPECTED_SAMPLE_COUNT = 128000
    const val SAMPLE_RATE = 16000

    const val ENGLISH_REFERENCE = "The quick brown fox jumps over the lazy dog."
    const val HINDI_REFERENCE = "नमस्ते, यह iTantra का हिंदी परीक्षण है।"

    data class SpeechBoundary(
        val speechStartSample: Int,
        val speechEndSample: Int,
        val speechStartMs: Long,
        val speechEndMs: Long,
        val speechDurationMs: Long,
        val leadingSilenceMs: Long,
        val trailingSilenceMs: Long,
        val activeFrameCount: Int,
        val totalFrameCount: Int
    )

    data class DerivedSegmentResult(
        val label: String,
        val fileName: String,
        val startSample: Int,
        val endSample: Int,
        val sampleCount: Int,
        val durationMs: Long,
        val durationSeconds: Double,
        val sha256: String,
        val rmsDbfs: Double,
        val peakDbfs: Double,
        val rawHypothesis: String,
        val normalizedHypothesis: String,
        val wer: Double,
        val cer: Double,
        val isExactMatch: Boolean,
        val inferenceTimeMs: Long,
        val rtf: Double
    )

    data class HindiLanguageAuditResult(
        val currentProductionConfig: String,
        val isHardcodedOrInferred: String,
        val sherpaLanguageSupport: String,
        val defaultLangResult: DerivedSegmentResult?,
        val explicitHindiResult: DerivedSegmentResult?
    )

    data class FullExperimentResult(
        val originalEnSha256: String,
        val isOriginalShaVerified: Boolean,
        val speechBoundary: SpeechBoundary,
        val segmentationResults: List<DerivedSegmentResult>,
        val hindiAudit: HindiLanguageAuditResult
    )

    suspend fun runFullExperiment(context: Context): FullExperimentResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "STARTING TASK M1-ASR-SEGMENTATION-CONTROL-03 EXPERIMENT")
        Log.i(TAG, "=========================================================")

        val enRawFile = File(context.filesDir, "raw_mic_source_control_en.wav")
        require(enRawFile.exists()) { "Missing raw_mic_source_control_en.wav in ${context.filesDir.absolutePath}" }

        val actualSha256 = calculateSha256(enRawFile)
        val isShaValid = actualSha256.equals(EXPECTED_EN_SHA256, ignoreCase = true)
        Log.i(TAG, "Original EN WAV: ${enRawFile.absolutePath}")
        Log.i(TAG, "Expected SHA-256: $EXPECTED_EN_SHA256")
        Log.i(TAG, "Actual SHA-256:   $actualSha256 (Valid: $isShaValid)")

        val rawSamples = readPcm16Wav(enRawFile)
        require(rawSamples.size == EXPECTED_SAMPLE_COUNT) {
            "Expected $EXPECTED_SAMPLE_COUNT samples, got ${rawSamples.size}"
        }

        // 1. Identify Speech Boundary using Silero VAD
        val speechBoundary = detectSpeechBoundary(context, rawSamples)
        Log.i(TAG, "Speech Boundary Detected:")
        Log.i(TAG, "  Start: ${speechBoundary.speechStartMs} ms (sample ${speechBoundary.speechStartSample})")
        Log.i(TAG, "  End:   ${speechBoundary.speechEndMs} ms (sample ${speechBoundary.speechEndSample})")
        Log.i(TAG, "  Speech Duration:    ${speechBoundary.speechDurationMs} ms")
        Log.i(TAG, "  Leading Silence:    ${speechBoundary.leadingSilenceMs} ms")
        Log.i(TAG, "  Trailing Silence:   ${speechBoundary.trailingSilenceMs} ms")

        // 2. Prepare 5 Derived Variations
        val full8sSamples = rawSamples
        val speechOnlySamples = rawSamples.copyOfRange(speechBoundary.speechStartSample, speechBoundary.speechEndSample)

        val endPlus200Sample = minOf(rawSamples.size, speechBoundary.speechEndSample + (200 * SAMPLE_RATE / 1000))
        val speechPlus200Samples = rawSamples.copyOfRange(speechBoundary.speechStartSample, endPlus200Sample)

        val endPlus500Sample = minOf(rawSamples.size, speechBoundary.speechEndSample + (500 * SAMPLE_RATE / 1000))
        val speechPlus500Samples = rawSamples.copyOfRange(speechBoundary.speechStartSample, endPlus500Sample)

        val endPlus1000Sample = minOf(rawSamples.size, speechBoundary.speechEndSample + (1000 * SAMPLE_RATE / 1000))
        val speechPlus1000Samples = rawSamples.copyOfRange(speechBoundary.speechStartSample, endPlus1000Sample)

        val variations = listOf(
            Triple("A. Full 8.0s (Original)", "m1_seg_full_8s.wav", Pair(0, rawSamples.size)),
            Triple("B. Speech Only", "m1_seg_speech_only.wav", Pair(speechBoundary.speechStartSample, speechBoundary.speechEndSample)),
            Triple("C. Speech + 200ms", "m1_seg_speech_plus_200ms.wav", Pair(speechBoundary.speechStartSample, endPlus200Sample)),
            Triple("D. Speech + 500ms", "m1_seg_speech_plus_500ms.wav", Pair(speechBoundary.speechStartSample, endPlus500Sample)),
            Triple("E. Speech + 1000ms", "m1_seg_speech_plus_1000ms.wav", Pair(speechBoundary.speechStartSample, endPlus1000Sample))
        )

        val segResults = mutableListOf<DerivedSegmentResult>()
        var whisperEngineEn: SherpaWhisperAsrEngine? = null

        try {
            whisperEngineEn = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")

            for ((label, fileName, sampleRange) in variations) {
                val start = sampleRange.first
                val end = sampleRange.second
                val samplesSlice = rawSamples.copyOfRange(start, end)

                val file = File(context.filesDir, fileName)
                writePcm16Wav(file, samplesSlice, SAMPLE_RATE)
                val sha = calculateSha256(file)

                val metrics = computeSignalMetrics(samplesSlice)
                val durationMs = (samplesSlice.size * 1000L) / SAMPLE_RATE
                val durationSec = samplesSlice.size.toDouble() / SAMPLE_RATE.toDouble()

                val asrRes = whisperEngineEn.transcribe(samplesSlice, SAMPLE_RATE, "en")
                val normRef = TextNormalizer.normalize(ENGLISH_REFERENCE)
                val normHyp = TextNormalizer.normalize(asrRes.text)
                val werRes = WerCalculator.calculate(normRef, normHyp)
                val rtf = if (durationMs > 0) asrRes.inferenceTimeMs.toDouble() / durationMs.toDouble() else 0.0

                val r = DerivedSegmentResult(
                    label = label,
                    fileName = fileName,
                    startSample = start,
                    endSample = end,
                    sampleCount = samplesSlice.size,
                    durationMs = durationMs,
                    durationSeconds = durationSec,
                    sha256 = sha,
                    rmsDbfs = metrics.first,
                    peakDbfs = metrics.second,
                    rawHypothesis = asrRes.text,
                    normalizedHypothesis = normHyp,
                    wer = werRes.wordStats.errorRate,
                    cer = werRes.charStats.errorRate,
                    isExactMatch = werRes.isExactMatch,
                    inferenceTimeMs = asrRes.inferenceTimeMs,
                    rtf = rtf
                )
                segResults.add(r)
                logSegmentResult(r)
            }
        } finally {
            whisperEngineEn?.close()
        }

        // 3. Hindi Language Control Audit
        val hindiAudit = evaluateHindiLanguageControl(context)

        FullExperimentResult(
            originalEnSha256 = actualSha256,
            isOriginalShaVerified = isShaValid,
            speechBoundary = speechBoundary,
            segmentationResults = segResults,
            hindiAudit = hindiAudit
        )
    }

    private fun detectSpeechBoundary(context: Context, samples: ShortArray): SpeechBoundary {
        val vadConfig = VadConfig(threshold = 0.5f)
        val vadEngine = SileroVadEngine.fromAsset(context, vadConfig)

        val frameSize = VadConfig.SILERO_V5_FRAME_SIZE // 512 samples = 32 ms
        val numFrames = samples.size / frameSize

        var firstSpeechFrame = -1
        var lastSpeechFrame = -1
        var activeFrames = 0

        try {
            for (i in 0 until numFrames) {
                val frame = samples.copyOfRange(i * frameSize, (i + 1) * frameSize)
                val res = vadEngine.process(frame)
                if (res.probability >= 0.5f) {
                    if (firstSpeechFrame == -1) {
                        firstSpeechFrame = i
                    }
                    lastSpeechFrame = i
                    activeFrames++
                }
            }
        } finally {
            vadEngine.close()
        }

        // Fallback if VAD detects nothing (which would be abnormal)
        val startFrame = if (firstSpeechFrame >= 0) firstSpeechFrame else 0
        val endFrame = if (lastSpeechFrame >= 0) lastSpeechFrame + 1 else numFrames

        val startSample = startFrame * frameSize
        val endSample = minOf(samples.size, endFrame * frameSize)

        val startMs = (startSample * 1000L) / SAMPLE_RATE
        val endMs = (endSample * 1000L) / SAMPLE_RATE
        val speechDurationMs = endMs - startMs
        val leadingSilenceMs = startMs
        val trailingSilenceMs = (samples.size * 1000L) / SAMPLE_RATE - endMs

        return SpeechBoundary(
            speechStartSample = startSample,
            speechEndSample = endSample,
            speechStartMs = startMs,
            speechEndMs = endMs,
            speechDurationMs = speechDurationMs,
            leadingSilenceMs = leadingSilenceMs,
            trailingSilenceMs = trailingSilenceMs,
            activeFrameCount = activeFrames,
            totalFrameCount = numFrames
        )
    }

    private suspend fun evaluateHindiLanguageControl(context: Context): HindiLanguageAuditResult = withContext(Dispatchers.IO) {
        val hiRawFile = File(context.filesDir, "raw_mic_source_control_hi.wav")
        if (!hiRawFile.exists()) {
            return@withContext HindiLanguageAuditResult(
                currentProductionConfig = "SherpaWhisperAsrEngine defaultLanguage=\"en\" via OfflineWhisperModelConfig",
                isHardcodedOrInferred = "Fixed at instantiation in OfflineWhisperModelConfig(language=defaultLanguage)",
                sherpaLanguageSupport = "OfflineWhisperModelConfig exposes language property at initialization",
                defaultLangResult = null,
                explicitHindiResult = null
            )
        }

        val hiSamples = readPcm16Wav(hiRawFile)
        var defaultRes: DerivedSegmentResult? = null
        var explicitHiRes: DerivedSegmentResult? = null

        // A. Default Language ("en")
        var engineDefault: SherpaWhisperAsrEngine? = null
        try {
            engineDefault = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")
            val asrRes = engineDefault.transcribe(hiSamples, SAMPLE_RATE, "en")
            val normRef = TextNormalizer.normalize(HINDI_REFERENCE)
            val normHyp = TextNormalizer.normalize(asrRes.text)
            val werRes = WerCalculator.calculate(normRef, normHyp)
            val durationMs = (hiSamples.size * 1000L) / SAMPLE_RATE
            val rtf = if (durationMs > 0) asrRes.inferenceTimeMs.toDouble() / durationMs.toDouble() else 0.0

            defaultRes = DerivedSegmentResult(
                label = "Hindi Audio with Default ('en') Model Config",
                fileName = "raw_mic_source_control_hi.wav",
                startSample = 0,
                endSample = hiSamples.size,
                sampleCount = hiSamples.size,
                durationMs = durationMs,
                durationSeconds = hiSamples.size.toDouble() / SAMPLE_RATE.toDouble(),
                sha256 = calculateSha256(hiRawFile),
                rmsDbfs = computeSignalMetrics(hiSamples).first,
                peakDbfs = computeSignalMetrics(hiSamples).second,
                rawHypothesis = asrRes.text,
                normalizedHypothesis = normHyp,
                wer = werRes.wordStats.errorRate,
                cer = werRes.charStats.errorRate,
                isExactMatch = werRes.isExactMatch,
                inferenceTimeMs = asrRes.inferenceTimeMs,
                rtf = rtf
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Hindi default evaluation failed: ${e.message}")
        } finally {
            engineDefault?.close()
        }

        // B. Explicit Hindi Language ("hi")
        var engineHi: SherpaWhisperAsrEngine? = null
        try {
            engineHi = SherpaWhisperAsrEngine(context = context, defaultLanguage = "hi")
            val asrRes = engineHi.transcribe(hiSamples, SAMPLE_RATE, "hi")
            val werRes = IndicWerCalculator.calculate(HINDI_REFERENCE, asrRes.text, normalize = true)
            val durationMs = (hiSamples.size * 1000L) / SAMPLE_RATE
            val rtf = if (durationMs > 0) asrRes.inferenceTimeMs.toDouble() / durationMs.toDouble() else 0.0

            explicitHiRes = DerivedSegmentResult(
                label = "Hindi Audio with Explicit ('hi') Model Config",
                fileName = "raw_mic_source_control_hi.wav",
                startSample = 0,
                endSample = hiSamples.size,
                sampleCount = hiSamples.size,
                durationMs = durationMs,
                durationSeconds = hiSamples.size.toDouble() / SAMPLE_RATE.toDouble(),
                sha256 = calculateSha256(hiRawFile),
                rmsDbfs = computeSignalMetrics(hiSamples).first,
                peakDbfs = computeSignalMetrics(hiSamples).second,
                rawHypothesis = asrRes.text,
                normalizedHypothesis = werRes.normalizedHypothesis,
                wer = werRes.wordStats.errorRate,
                cer = werRes.charStats.errorRate,
                isExactMatch = werRes.isExactMatch,
                inferenceTimeMs = asrRes.inferenceTimeMs,
                rtf = rtf
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Hindi explicit evaluation failed: ${e.message}")
        } finally {
            engineHi?.close()
        }

        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [HINDI LANGUAGE CONTROL AUDIT] ===")
        if (defaultRes != null) {
            Log.i(TAG, "Default Config Hyp: '${defaultRes.rawHypothesis}' (WER=${"%.1f".format(defaultRes.wer * 100)}%)")
        }
        if (explicitHiRes != null) {
            Log.i(TAG, "Explicit ('hi') Hyp: '${explicitHiRes.rawHypothesis}' (WER=${"%.1f".format(explicitHiRes.wer * 100)}%)")
        }
        Log.i(TAG, "=========================================================")

        HindiLanguageAuditResult(
            currentProductionConfig = "SherpaWhisperAsrEngine(defaultLanguage=\"en\")",
            isHardcodedOrInferred = "Fixed at initialization in OfflineWhisperModelConfig(language=defaultLanguage)",
            sherpaLanguageSupport = "Supported at initialization via OfflineWhisperModelConfig(language=\"hi\")",
            defaultLangResult = defaultRes,
            explicitHindiResult = explicitHiRes
        )
    }

    private fun computeSignalMetrics(samples: ShortArray): Pair<Double, Double> {
        val n = samples.size
        var sumSquares = 0.0
        var peakAbs = 0
        for (s in samples) {
            val v = s.toInt()
            val a = abs(v)
            if (a > peakAbs) peakAbs = a
            sumSquares += v.toDouble() * v.toDouble()
        }
        val rms = if (n > 0) sqrt(sumSquares / n) else 0.0
        val rmsDbfs = if (rms > 0.0) 20.0 * log10(rms / 32768.0) else -96.0
        val peakDbfs = if (peakAbs > 0) 20.0 * log10(peakAbs / 32768.0) else -96.0
        return Pair(rmsDbfs, peakDbfs)
    }

    private fun readPcm16Wav(file: File): ShortArray {
        val bytes = file.readBytes()
        require(bytes.size >= 44) { "WAV file too small (${bytes.size} bytes)" }
        val pcmByteCount = bytes.size - 44
        val shortCount = pcmByteCount / 2
        val shorts = ShortArray(shortCount)
        val buf = ByteBuffer.wrap(bytes, 44, pcmByteCount).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until shortCount) {
            shorts[i] = buf.short
        }
        return shorts
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

    private fun logSegmentResult(r: DerivedSegmentResult) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "VARIATION: ${r.label}")
        Log.i(TAG, "FILE: ${r.fileName} (${r.sampleCount} samples, ${r.durationMs}ms, ${"%.3f".format(r.durationSeconds)}s)")
        Log.i(TAG, "SAMPLES: [${r.startSample} .. ${r.endSample}]")
        Log.i(TAG, "SHA-256: ${r.sha256}")
        Log.i(TAG, "SIGNAL: RMS=${"%.2f".format(r.rmsDbfs)} dBFS, Peak=${"%.2f".format(r.peakDbfs)} dBFS")
        Log.i(TAG, "ASR: WER=${"%.1f".format(r.wer * 100)}%, CER=${"%.1f".format(r.cer * 100)}%, Time=${r.inferenceTimeMs}ms (RTF ${"%.3f".format(r.rtf)})")
        Log.i(TAG, "HYP: \"${r.rawHypothesis}\"")
        Log.i(TAG, "=========================================================")
    }
}
