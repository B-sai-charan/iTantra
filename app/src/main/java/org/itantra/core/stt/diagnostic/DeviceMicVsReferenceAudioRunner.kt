package org.itantra.core.stt.diagnostic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.itantra.core.stt.AsrSmokeTestRunner
import org.itantra.core.stt.SherpaWhisperAsrEngine
import org.itantra.core.stt.benchmark.TextNormalizer
import org.itantra.core.stt.benchmark.WerCalculator
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Diagnostic runner for Task m1-device-mic-vs-reference-audio-01.
 *
 * Runs a controlled ASR comparison on physical hardware between:
 * Condition A: Known-good reference fixture (EN-001 / speech_test.wav from assets)
 * Condition B: Newly captured production microphone audio (production_capture_source_test.wav)
 */
object DeviceMicVsReferenceAudioRunner {

    private const val TAG = "MIC_VS_REF_AUDIO"

    const val REF_FIXTURE_ASSET = "speech_test.wav"
    const val REF_FIXTURE_EXPECTED_SHA256 = "6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f"
    const val REF_FIXTURE_REFERENCE = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."

    const val MIC_WAV_FILE = "production_capture_source_test.wav"
    const val MIC_WAV_EXPECTED_SHA256 = "26298c77438f0491ee172c810a9c328dc8628866612a41310ff6d37c4c0684d2"
    const val MIC_WAV_REFERENCE = "Hello from iTantra. This is a production capture source isolation test."

    data class ConditionRunMetrics(
        val runIndex: Int,
        val rawHypothesis: String,
        val normalizedHypothesis: String,
        val detectedLanguage: String,
        val wer: Double,
        val cer: Double,
        val isExactMatch: Boolean,
        val inferenceTimeMs: Long,
        val audioDurationMs: Long,
        val rtf: Double,
        val errorMessage: String? = null
    )

    data class WavCharacteristics(
        val name: String,
        val sha256: String,
        val fileSize: Long,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val sampleCount: Int,
        val durationMs: Long,
        val minSample: Short,
        val maxSample: Short,
        val peakAbsolute: Int,
        val peakPercentage: Double,
        val rms: Double,
        val rmsDbfs: Double,
        val zeroCount: Long,
        val zeroPercentage: Double,
        val clipMinCount: Long,
        val clipMaxCount: Long
    )

    data class ConditionReport(
        val conditionName: String,
        val audioSource: String,
        val referenceText: String,
        val wavDetails: WavCharacteristics,
        val runs: List<ConditionRunMetrics>,
        val meanWer: Double,
        val meanCer: Double,
        val meanRtf: Double,
        val exactMatchCount: Int
    )

    data class ComparisonReport(
        val conditionA: ConditionReport,
        val conditionB: ConditionReport
    )

    suspend fun runComparison(
        context: Context,
        repetitions: Int = 3
    ): ComparisonReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING CONTROLLED ASR COMPARISON: KNOWN-GOOD VS DEVICE MIC")
        Log.i(TAG, "Repetitions per condition: $repetitions")
        Log.i(TAG, "==================================================")

        // 1. Load Condition A: Known-Good Fixture
        val fixtureBytes = context.assets.open(REF_FIXTURE_ASSET).use { it.readBytes() }
        val fixtureSha256 = calculateSha256(fixtureBytes)
        Log.i(TAG, "[FIXTURE A] $REF_FIXTURE_ASSET (${fixtureBytes.size} bytes, SHA: $fixtureSha256)")
        val fixtureSamples = parseWavSamples(fixtureBytes)
        val fixtureChars = computeCharacteristics(REF_FIXTURE_ASSET, fixtureSha256, fixtureBytes.size.toLong(), fixtureSamples, 16000)

        // 2. Load Condition B: Microphone WAV
        val micFile = File(context.filesDir, MIC_WAV_FILE)
        if (!micFile.exists()) {
            throw IllegalStateException("Microphone WAV not found: ${micFile.absolutePath}")
        }
        val micBytes = micFile.readBytes()
        val micSha256 = calculateSha256(micBytes)
        Log.i(TAG, "[MIC B] $MIC_WAV_FILE (${micBytes.size} bytes, SHA: $micSha256)")
        val micSamples = parseWavSamples(micBytes)
        val micChars = computeCharacteristics(MIC_WAV_FILE, micSha256, micBytes.size.toLong(), micSamples, 16000)

        // 3. Initialize Sherpa Whisper Engine
        Log.i(TAG, "Initializing SherpaWhisperAsrEngine for controlled A/B evaluation...")
        val engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")

        try {
            // Evaluate Condition A
            Log.i(TAG, "==================================================")
            Log.i(TAG, "EVALUATING CONDITION A: KNOWN-GOOD REFERENCE FIXTURE")
            Log.i(TAG, "==================================================")
            val runsA = executeRuns(engine, fixtureSamples, 16000, REF_FIXTURE_REFERENCE, repetitions, "A-REF")

            // Evaluate Condition B
            Log.i(TAG, "==================================================")
            Log.i(TAG, "EVALUATING CONDITION B: DEVICE MICROPHONE WAV")
            Log.i(TAG, "==================================================")
            val runsB = executeRuns(engine, micSamples, 16000, MIC_WAV_REFERENCE, repetitions, "B-MIC")

            val reportA = ConditionReport(
                conditionName = "Condition A: Known-Good Reference Fixture",
                audioSource = "Pre-recorded 16kHz Benchmark WAV (speech_test.wav)",
                referenceText = REF_FIXTURE_REFERENCE,
                wavDetails = fixtureChars,
                runs = runsA,
                meanWer = runsA.map { it.wer }.average(),
                meanCer = runsA.map { it.cer }.average(),
                meanRtf = runsA.map { it.rtf }.average(),
                exactMatchCount = runsA.count { it.isExactMatch }
            )

            val reportB = ConditionReport(
                conditionName = "Condition B: Device Microphone Recording",
                audioSource = "Production AudioRecordCapture (production_capture_source_test.wav)",
                referenceText = MIC_WAV_REFERENCE,
                wavDetails = micChars,
                runs = runsB,
                meanWer = runsB.map { it.wer }.average(),
                meanCer = runsB.map { it.cer }.average(),
                meanRtf = runsB.map { it.rtf }.average(),
                exactMatchCount = runsB.count { it.isExactMatch }
            )

            Log.i(TAG, "==================================================")
            Log.i(TAG, "CONTROLLED COMPARISON SUMMARY")
            Log.i(TAG, "Condition A (Known-Good): Mean WER=${"%.2f".format(reportA.meanWer * 100)}%, Mean CER=${"%.2f".format(reportA.meanCer * 100)}%, Mean RTF=${"%.4f".format(reportA.meanRtf)}, Exact=${reportA.exactMatchCount}/$repetitions")
            Log.i(TAG, "Condition B (Device Mic):  Mean WER=${"%.2f".format(reportB.meanWer * 100)}%, Mean CER=${"%.2f".format(reportB.meanCer * 100)}%, Mean RTF=${"%.4f".format(reportB.meanRtf)}, Exact=${reportB.exactMatchCount}/$repetitions")
            Log.i(TAG, "==================================================")

            ComparisonReport(reportA, reportB)
        } finally {
            engine.close()
            Log.i(TAG, "[CLEANUP] SherpaWhisperAsrEngine closed.")
        }
    }

    private suspend fun executeRuns(
        engine: SherpaWhisperAsrEngine,
        samples: ShortArray,
        sampleRate: Int,
        referenceText: String,
        repetitions: Int,
        label: String
    ): List<ConditionRunMetrics> {
        val runs = mutableListOf<ConditionRunMetrics>()
        val normalizedRef = TextNormalizer.normalize(referenceText)
        val audioDurationMs = (samples.size * 1000L) / sampleRate

        for (i in 1..repetitions) {
            var runErr: String? = null
            var rawHyp = ""
            var detectedLang = "en"
            var infTimeMs = 0L
            var audDurMs = audioDurationMs
            var rtf = 0.0

            try {
                val asrRes = engine.transcribe(audio = samples, sampleRate = sampleRate, language = "en")
                rawHyp = asrRes.text
                detectedLang = asrRes.language
                infTimeMs = asrRes.inferenceTimeMs
                audDurMs = asrRes.audioDurationMs
                rtf = if (audDurMs > 0) infTimeMs.toDouble() / audDurMs.toDouble() else 0.0
            } catch (t: Throwable) {
                runErr = "${t.javaClass.simpleName}: ${t.message}"
                Log.e(TAG, "[$label Run $i Error] $runErr", t)
            }

            val normalizedHyp = TextNormalizer.normalize(rawHyp)
            val werRes = WerCalculator.calculate(
                reference = referenceText,
                hypothesis = rawHyp,
                normalize = true
            )

            val wer = werRes.wordStats.errorRate
            val cer = werRes.charStats.errorRate
            val isExact = werRes.isExactMatch

            Log.i(TAG, "[$label Run $i/$repetitions]")
            Log.i(TAG, "  Raw:         \"$rawHyp\"")
            Log.i(TAG, "  Normalized:  \"$normalizedHyp\"")
            Log.i(TAG, "  Lang:        $detectedLang")
            Log.i(TAG, "  WER:         ${"%.2f".format(wer * 100)}% (Sub: ${werRes.wordStats.substitutions}, Del: ${werRes.wordStats.deletions}, Ins: ${werRes.wordStats.insertions})")
            Log.i(TAG, "  CER:         ${"%.2f".format(cer * 100)}% (Sub: ${werRes.charStats.substitutions}, Del: ${werRes.charStats.deletions}, Ins: ${werRes.charStats.insertions})")
            Log.i(TAG, "  Exact Match: $isExact")
            Log.i(TAG, "  Inference:   ${infTimeMs}ms (RTF: ${"%.4f".format(rtf)})")

            runs.add(
                ConditionRunMetrics(
                    runIndex = i,
                    rawHypothesis = rawHyp,
                    normalizedHypothesis = normalizedHyp,
                    detectedLanguage = detectedLang,
                    wer = wer,
                    cer = cer,
                    isExactMatch = isExact,
                    inferenceTimeMs = infTimeMs,
                    audioDurationMs = audDurMs,
                    rtf = rtf,
                    errorMessage = runErr
                )
            )
        }
        return runs
    }

    private fun parseWavSamples(bytes: ByteArray): ShortArray {
        var dataOffset = 44
        for (i in 0 until bytes.size - 4) {
            if (bytes[i] == 'd'.code.toByte() &&
                bytes[i + 1] == 'a'.code.toByte() &&
                bytes[i + 2] == 't'.code.toByte() &&
                bytes[i + 3] == 'a'.code.toByte()
            ) {
                dataOffset = i + 8
                break
            }
        }
        val pcmLength = bytes.size - dataOffset
        val sampleCount = pcmLength / 2
        val buffer = ByteBuffer.wrap(bytes, dataOffset, pcmLength).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(sampleCount)
        buffer.get(samples)
        return samples
    }

    private fun computeCharacteristics(
        name: String,
        sha256: String,
        fileSize: Long,
        samples: ShortArray,
        sampleRate: Int
    ): WavCharacteristics {
        var minSample: Short = 0
        var maxSample: Short = 0
        var peakAbs = 0
        var sumSquares = 0.0
        var zeroCount = 0L
        var clipMinCount = 0L
        var clipMaxCount = 0L

        for (s in samples) {
            if (s < minSample) minSample = s
            if (s > maxSample) maxSample = s
            val absVal = kotlin.math.abs(s.toInt())
            if (absVal > peakAbs) peakAbs = absVal
            sumSquares += s.toDouble() * s.toDouble()
            if (s.toInt() == 0) zeroCount++
            if (s == Short.MIN_VALUE) clipMinCount++
            if (s == Short.MAX_VALUE) clipMaxCount++
        }

        val totalCount = samples.size
        val meanSquare = if (totalCount > 0) sumSquares / totalCount else 0.0
        val rms = kotlin.math.sqrt(meanSquare)
        val rmsDbfs = if (rms > 0.0) 20.0 * kotlin.math.log10(rms / 32768.0) else -96.0
        val zeroPercentage = if (totalCount > 0) (zeroCount.toDouble() / totalCount) * 100.0 else 0.0
        val durationMs = if (sampleRate > 0) (totalCount * 1000L) / sampleRate else 0L

        return WavCharacteristics(
            name = name,
            sha256 = sha256,
            fileSize = fileSize,
            sampleRate = sampleRate,
            channels = 1,
            bitsPerSample = 16,
            sampleCount = totalCount,
            durationMs = durationMs,
            minSample = minSample,
            maxSample = maxSample,
            peakAbsolute = peakAbs,
            peakPercentage = (peakAbs.toDouble() / 32767.0) * 100.0,
            rms = rms,
            rmsDbfs = rmsDbfs,
            zeroCount = zeroCount,
            zeroPercentage = zeroPercentage,
            clipMinCount = clipMinCount,
            clipMaxCount = clipMaxCount
        )
    }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
