package org.itantra.core.stt.diagnostic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.itantra.core.stt.SherpaWhisperAsrEngine
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
 * Diagnostic runner for Task M1-ASR-SILENCE-CONTROL-04.
 *
 * Tests the isolated effect of controlled leading and trailing digital silence
 * on Whisper Tiny INT8 decoding and hallucination behavior.
 */
object AsrSilenceControlRunner {

    private const val TAG = "ASR_SILENCE_CONTROL"

    const val EXPECTED_EN_SHA256 = "5fd0bd41a6cada093ace02871fb63c3eaf15b4cb7389f8d88be028e458ca6695"
    const val BASE_START_SAMPLE = 23552
    const val BASE_END_SAMPLE = 128000
    const val BASE_SAMPLE_COUNT = 104448 // 6.528 s
    const val SAMPLE_RATE = 16000

    const val ENGLISH_REFERENCE = "The quick brown fox jumps over the lazy dog."

    data class SilenceVariantSpec(
        val variantId: String,
        val label: String,
        val fileName: String,
        val leadingSilenceMs: Int,
        val trailingSilenceMs: Int
    )

    data class SilenceVariantResult(
        val variantId: String,
        val label: String,
        val fileName: String,
        val leadingSilenceMs: Int,
        val trailingSilenceMs: Int,
        val leadingSilenceSamples: Int,
        val trailingSilenceSamples: Int,
        val speechSamples: Int,
        val totalSamples: Int,
        val durationMs: Long,
        val durationSeconds: Double,
        val sha256: String,
        val rmsDbfs: Double,
        val peakDbfs: Double,
        val isSpeechByteIdentical: Boolean,
        val rawHypothesis: String,
        val normalizedHypothesis: String,
        val wer: Double,
        val cer: Double,
        val isExactMatch: Boolean,
        val inferenceTimeMs: Long,
        val rtf: Double
    )

    data class BaseSpeechMetrics(
        val fileName: String,
        val sampleCount: Int,
        val durationMs: Long,
        val durationSeconds: Double,
        val sha256: String,
        val rms: Double,
        val rmsDbfs: Double,
        val peak: Int,
        val peakDbfs: Double,
        val clippingCount: Long
    )

    data class FullSilenceExperimentResult(
        val baseMetrics: BaseSpeechMetrics,
        val variantResults: List<SilenceVariantResult>
    )

    val VARIANTS = listOf(
        // Leading Silence Variants
        SilenceVariantSpec("A", "A: Leading 0ms (Base)", "m1_leading_0ms.wav", 0, 0),
        SilenceVariantSpec("B", "B: Leading 500ms", "m1_leading_500ms.wav", 500, 0),
        SilenceVariantSpec("C", "C: Leading 1000ms", "m1_leading_1000ms.wav", 1000, 0),
        SilenceVariantSpec("D", "D: Leading 1500ms", "m1_leading_1500ms.wav", 1500, 0),
        // Trailing Silence Variants
        SilenceVariantSpec("E", "E: Trailing 0ms (Base)", "m1_trailing_0ms.wav", 0, 0),
        SilenceVariantSpec("F", "F: Trailing 200ms", "m1_trailing_200ms.wav", 0, 200),
        SilenceVariantSpec("G", "G: Trailing 500ms", "m1_trailing_500ms.wav", 0, 500),
        SilenceVariantSpec("H", "H: Trailing 1000ms", "m1_trailing_1000ms.wav", 0, 1000),
        SilenceVariantSpec("I", "I: Trailing 2000ms", "m1_trailing_2000ms.wav", 0, 2000),
        // Combined Variant
        SilenceVariantSpec("J", "J: Leading 1500ms + Trailing 1000ms", "m1_leading_1500_trailing_1000ms.wav", 1500, 1000)
    )

    suspend fun runExperiment(context: Context): FullSilenceExperimentResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "STARTING TASK M1-ASR-SILENCE-CONTROL-04 EXPERIMENT")
        Log.i(TAG, "=========================================================")

        val enRawFile = File(context.filesDir, "raw_mic_source_control_en.wav")
        require(enRawFile.exists()) { "Missing raw_mic_source_control_en.wav in ${context.filesDir.absolutePath}" }

        val actualRawSha = calculateSha256(enRawFile)
        require(actualRawSha.equals(EXPECTED_EN_SHA256, ignoreCase = true)) {
            "Raw EN SHA mismatch: expected $EXPECTED_EN_SHA256, got $actualRawSha"
        }

        val rawSamples = readPcm16Wav(enRawFile)
        require(rawSamples.size == 128000) { "Raw samples count mismatch: ${rawSamples.size}" }

        // 1. Create Base Speech Audio: scratch/m1_silence_base_en.wav
        val baseSpeechSamples = rawSamples.copyOfRange(BASE_START_SAMPLE, BASE_END_SAMPLE)
        require(baseSpeechSamples.size == BASE_SAMPLE_COUNT) {
            "Base speech count mismatch: ${baseSpeechSamples.size} != $BASE_SAMPLE_COUNT"
        }

        val baseFile = File(context.filesDir, "m1_silence_base_en.wav")
        writePcm16Wav(baseFile, baseSpeechSamples, SAMPLE_RATE)
        val baseSha256 = calculateSha256(baseFile)

        val baseSignal = computeSignalStats(baseSpeechSamples)
        val baseMetrics = BaseSpeechMetrics(
            fileName = "m1_silence_base_en.wav",
            sampleCount = baseSpeechSamples.size,
            durationMs = (baseSpeechSamples.size * 1000L) / SAMPLE_RATE,
            durationSeconds = baseSpeechSamples.size.toDouble() / SAMPLE_RATE.toDouble(),
            sha256 = baseSha256,
            rms = baseSignal.rms,
            rmsDbfs = baseSignal.rmsDbfs,
            peak = baseSignal.peak,
            peakDbfs = baseSignal.peakDbfs,
            clippingCount = baseSignal.clipCount
        )

        Log.i(TAG, "Base Speech WAV Created: ${baseFile.absolutePath}")
        Log.i(TAG, "  Samples: ${baseMetrics.sampleCount} (${baseMetrics.durationSeconds}s)")
        Log.i(TAG, "  SHA-256: ${baseMetrics.sha256}")
        Log.i(TAG, "  RMS:     ${"%.2f".format(baseMetrics.rmsDbfs)} dBFS (Peak: ${baseMetrics.peak}, ${"%.2f".format(baseMetrics.peakDbfs)} dBFS)")
        Log.i(TAG, "  Clip:    ${baseMetrics.clippingCount}")

        // 2. Build and Evaluate All 10 Variants (A through J)
        val results = mutableListOf<SilenceVariantResult>()
        var whisperEngine: SherpaWhisperAsrEngine? = null

        try {
            whisperEngine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")

            for (spec in VARIANTS) {
                val leadingZeroCount = (spec.leadingSilenceMs * SAMPLE_RATE) / 1000
                val trailingZeroCount = (spec.trailingSilenceMs * SAMPLE_RATE) / 1000
                val totalSamples = leadingZeroCount + BASE_SAMPLE_COUNT + trailingZeroCount

                val assembled = ShortArray(totalSamples)
                // Leading zeros remain default 0
                // Copy base speech
                System.arraycopy(baseSpeechSamples, 0, assembled, leadingZeroCount, BASE_SAMPLE_COUNT)
                // Trailing zeros remain default 0

                // Verify byte-for-byte speech identity
                var isIdentical = true
                for (i in 0 until BASE_SAMPLE_COUNT) {
                    if (assembled[leadingZeroCount + i] != baseSpeechSamples[i]) {
                        isIdentical = false
                        break
                    }
                }

                val variantFile = File(context.filesDir, spec.fileName)
                writePcm16Wav(variantFile, assembled, SAMPLE_RATE)
                val sha = calculateSha256(variantFile)

                val sig = computeSignalStats(assembled)
                val durationMs = (totalSamples * 1000L) / SAMPLE_RATE
                val durationSec = totalSamples.toDouble() / SAMPLE_RATE.toDouble()

                // Transcribe with Whisper Tiny INT8
                val asrRes = whisperEngine.transcribe(assembled, SAMPLE_RATE, "en")
                val normRef = TextNormalizer.normalize(ENGLISH_REFERENCE)
                val normHyp = TextNormalizer.normalize(asrRes.text)
                val werRes = WerCalculator.calculate(normRef, normHyp)
                val rtf = if (durationMs > 0) asrRes.inferenceTimeMs.toDouble() / durationMs.toDouble() else 0.0

                val r = SilenceVariantResult(
                    variantId = spec.variantId,
                    label = spec.label,
                    fileName = spec.fileName,
                    leadingSilenceMs = spec.leadingSilenceMs,
                    trailingSilenceMs = spec.trailingSilenceMs,
                    leadingSilenceSamples = leadingZeroCount,
                    trailingSilenceSamples = trailingZeroCount,
                    speechSamples = BASE_SAMPLE_COUNT,
                    totalSamples = totalSamples,
                    durationMs = durationMs,
                    durationSeconds = durationSec,
                    sha256 = sha,
                    rmsDbfs = sig.rmsDbfs,
                    peakDbfs = sig.peakDbfs,
                    isSpeechByteIdentical = isIdentical,
                    rawHypothesis = asrRes.text,
                    normalizedHypothesis = normHyp,
                    wer = werRes.wordStats.errorRate,
                    cer = werRes.charStats.errorRate,
                    isExactMatch = werRes.isExactMatch,
                    inferenceTimeMs = asrRes.inferenceTimeMs,
                    rtf = rtf
                )
                results.add(r)
                logVariantResult(r)
            }
        } finally {
            whisperEngine?.close()
        }

        FullSilenceExperimentResult(
            baseMetrics = baseMetrics,
            variantResults = results
        )
    }

    private data class SignalStats(
        val rms: Double,
        val rmsDbfs: Double,
        val peak: Int,
        val peakDbfs: Double,
        val clipCount: Long
    )

    private fun computeSignalStats(samples: ShortArray): SignalStats {
        val n = samples.size
        var sumSquares = 0.0
        var peakAbs = 0
        var clipCount = 0L

        for (s in samples) {
            val v = s.toInt()
            val a = abs(v)
            if (a > peakAbs) peakAbs = a
            sumSquares += v.toDouble() * v.toDouble()
            if (s == Short.MIN_VALUE || s == Short.MAX_VALUE) clipCount++
        }

        val rms = if (n > 0) sqrt(sumSquares / n) else 0.0
        val rmsDbfs = if (rms > 0.0) 20.0 * log10(rms / 32768.0) else -96.0
        val peakDbfs = if (peakAbs > 0) 20.0 * log10(peakAbs / 32768.0) else -96.0

        return SignalStats(
            rms = rms,
            rmsDbfs = rmsDbfs,
            peak = peakAbs,
            peakDbfs = peakDbfs,
            clipCount = clipCount
        )
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

    private fun logVariantResult(r: SilenceVariantResult) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "VARIANT: ${r.label} (${r.fileName})")
        Log.i(TAG, "SAMPLES: Total=${r.totalSamples} (LeadingZeros=${r.leadingSilenceSamples}, Speech=${r.speechSamples}, TrailingZeros=${r.trailingSilenceSamples})")
        Log.i(TAG, "DURATION: ${r.durationMs}ms (${"%.3f".format(r.durationSeconds)}s), Identical=${r.isSpeechByteIdentical}")
        Log.i(TAG, "SHA-256: ${r.sha256}")
        Log.i(TAG, "SIGNAL: RMS=${"%.2f".format(r.rmsDbfs)} dBFS, Peak=${"%.2f".format(r.peakDbfs)} dBFS")
        Log.i(TAG, "ASR: WER=${"%.1f".format(r.wer * 100)}%, CER=${"%.1f".format(r.cer * 100)}%, Time=${r.inferenceTimeMs}ms (RTF ${"%.3f".format(r.rtf)})")
        Log.i(TAG, "HYP: \"${r.rawHypothesis}\"")
        Log.i(TAG, "=========================================================")
    }
}
