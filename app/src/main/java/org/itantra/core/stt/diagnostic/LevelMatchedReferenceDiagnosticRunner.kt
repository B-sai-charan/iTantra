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
import kotlin.math.roundToInt

/**
 * Diagnostic runner for Task m1-level-matched-reference-diagnostic-01.
 *
 * Compares Whisper-Tiny INT8 ASR recognition on:
 * Condition A: Original frozen English benchmark fixture (speech_test.wav, RMS -26.54 dBFS)
 * Condition B: Level-matched English reference (speech_test_level_matched_to_mic.wav, RMS scaled to match mic level ~ -35.37 dBFS)
 * Condition C: Production device microphone recording (production_capture_source_test.wav, RMS -35.37 dBFS)
 */
object LevelMatchedReferenceDiagnosticRunner {

    private const val TAG = "LEVEL_MATCH_DIAGNOSTIC"

    const val REF_FIXTURE_ASSET = "speech_test.wav"
    const val REF_FIXTURE_EXPECTED_SHA256 = "6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f"
    const val REF_FIXTURE_REFERENCE = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."

    const val MIC_WAV_FILE = "production_capture_source_test.wav"
    const val MIC_WAV_EXPECTED_SHA256 = "26298c77438f0491ee172c810a9c328dc8628866612a41310ff6d37c4c0684d2"
    const val MIC_WAV_REFERENCE = "Hello from iTantra. This is a production capture source isolation test."

    const val LEVEL_MATCHED_WAV_FILE = "speech_test_level_matched_to_mic.wav"

    data class AudioStats(
        val name: String,
        val filePath: String,
        val fileSize: Long,
        val sha256: String,
        val sampleRate: Int,
        val channels: Int,
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
        val clipMaxCount: Long,
        val totalClippingCount: Long
    )

    data class AsrIterationResult(
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

    data class ConditionReport(
        val conditionName: String,
        val referenceText: String,
        val audioStats: AudioStats,
        val runs: List<AsrIterationResult>,
        val meanWer: Double,
        val meanCer: Double,
        val meanRtf: Double,
        val exactMatchCount: Int
    )

    data class FullLevelMatchReport(
        val refShaMatched: Boolean,
        val micShaMatched: Boolean,
        val calculatedGain: Double,
        val conditionAOriginalRef: ConditionReport,
        val conditionBLevelMatchedRef: ConditionReport,
        val conditionCDeviceMic: ConditionReport
    )

    suspend fun runLevelMatchDiagnostic(
        context: Context,
        repetitions: Int = 3
    ): FullLevelMatchReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING LEVEL-MATCHED REFERENCE ASR DIAGNOSTIC")
        Log.i(TAG, "Reference Fixture: $REF_FIXTURE_ASSET (Expected SHA: $REF_FIXTURE_EXPECTED_SHA256)")
        Log.i(TAG, "Microphone WAV:    $MIC_WAV_FILE (Expected SHA: $MIC_WAV_EXPECTED_SHA256)")
        Log.i(TAG, "==================================================")

        // 1. Verify Input A (Original Reference)
        val refBytes = context.assets.open(REF_FIXTURE_ASSET).use { it.readBytes() }
        val refSha256 = calculateSha256(refBytes)
        val refShaMatched = refSha256.equals(REF_FIXTURE_EXPECTED_SHA256, ignoreCase = true)
        if (!refShaMatched) {
            val err = "Reference fixture SHA mismatch! Expected $REF_FIXTURE_EXPECTED_SHA256 but got $refSha256"
            Log.e(TAG, err)
            throw IllegalStateException(err)
        }
        val refSamples = parseWavSamples(refBytes)
        val origRefStats = computeAudioStats("Original EN-001 Reference", REF_FIXTURE_ASSET, refBytes.size.toLong(), refSha256, refSamples, 16000)

        // 2. Verify Input B (Device Microphone WAV)
        val micFile = File(context.filesDir, MIC_WAV_FILE)
        if (!micFile.exists()) {
            throw IllegalStateException("Microphone WAV file not found: ${micFile.absolutePath}")
        }
        val micBytes = micFile.readBytes()
        val micSha256 = calculateSha256(micBytes)
        val micShaMatched = micSha256.equals(MIC_WAV_EXPECTED_SHA256, ignoreCase = true)
        if (!micShaMatched) {
            val err = "Microphone WAV SHA mismatch! Expected $MIC_WAV_EXPECTED_SHA256 but got $micSha256"
            Log.e(TAG, err)
            throw IllegalStateException(err)
        }
        val micSamples = parseWavSamples(micBytes)
        val micStats = computeAudioStats("Device Microphone Audio", micFile.absolutePath, micBytes.size.toLong(), micSha256, micSamples, 16000)

        // 3. Calculate Level-Matching Gain
        val calculatedGain = if (origRefStats.rms > 0.0) micStats.rms / origRefStats.rms else 1.0
        Log.i(TAG, "[LEVEL MATCHING CALCULATION]")
        Log.i(TAG, "  Reference RMS:  ${origRefStats.rms} (${"%.2f".format(origRefStats.rmsDbfs)} dBFS)")
        Log.i(TAG, "  Microphone RMS: ${micStats.rms} (${"%.2f".format(micStats.rmsDbfs)} dBFS)")
        Log.i(TAG, "  Calculated Gain: $calculatedGain (mic_RMS / ref_RMS)")

        // 4. Create Level-Matched Reference WAV
        val levelMatchedSamples = ShortArray(refSamples.size)
        for (i in refSamples.indices) {
            val scaled = (refSamples[i].toDouble() * calculatedGain).roundToInt().coerceIn(-32768, 32767)
            levelMatchedSamples[i] = scaled.toShort()
        }

        val levelMatchedFile = File(context.filesDir, LEVEL_MATCHED_WAV_FILE)
        writePcmToWav(levelMatchedFile, levelMatchedSamples, 16000, 1)
        val levelMatchedBytes = levelMatchedFile.readBytes()
        val levelMatchedSha256 = calculateSha256(levelMatchedBytes)
        val levelMatchedStats = computeAudioStats("Level-Matched EN-001 Reference", levelMatchedFile.absolutePath, levelMatchedBytes.size.toLong(), levelMatchedSha256, levelMatchedSamples, 16000)

        Log.i(TAG, "[LEVEL-MATCHED WAV CREATED]")
        Log.i(TAG, "  File:    ${levelMatchedFile.name} (${levelMatchedBytes.size} bytes)")
        Log.i(TAG, "  SHA-256: $levelMatchedSha256")
        Log.i(TAG, "  RMS:     ${levelMatchedStats.rms} (${"%.2f".format(levelMatchedStats.rmsDbfs)} dBFS)")
        Log.i(TAG, "  Peak:    ${levelMatchedStats.peakAbsolute} (${"%.2f".format(levelMatchedStats.peakPercentage)}%)")
        Log.i(TAG, "  Clips:   ${levelMatchedStats.totalClippingCount}")

        // 5. Run Whisper Engine across all 3 Conditions
        val engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")

        try {
            // Condition A: Original Reference
            Log.i(TAG, "==================================================")
            Log.i(TAG, "EVALUATING CONDITION A: ORIGINAL EN-001 REFERENCE")
            Log.i(TAG, "==================================================")
            val runsA = executeRuns(engine, refSamples, 16000, REF_FIXTURE_REFERENCE, repetitions, "COND-A-ORIG-REF")

            // Condition B: Level-Matched Reference
            Log.i(TAG, "==================================================")
            Log.i(TAG, "EVALUATING CONDITION B: LEVEL-MATCHED EN-001 REFERENCE")
            Log.i(TAG, "==================================================")
            val runsB = executeRuns(engine, levelMatchedSamples, 16000, REF_FIXTURE_REFERENCE, repetitions, "COND-B-LEVEL-MATCHED-REF")

            // Condition C: Device Microphone
            Log.i(TAG, "==================================================")
            Log.i(TAG, "EVALUATING CONDITION C: DEVICE MICROPHONE RECORDING")
            Log.i(TAG, "==================================================")
            val runsC = executeRuns(engine, micSamples, 16000, MIC_WAV_REFERENCE, repetitions, "COND-C-DEVICE-MIC")

            val reportA = ConditionReport(
                conditionName = "Original EN-001",
                referenceText = REF_FIXTURE_REFERENCE,
                audioStats = origRefStats,
                runs = runsA,
                meanWer = runsA.map { it.wer }.average(),
                meanCer = runsA.map { it.cer }.average(),
                meanRtf = runsA.map { it.rtf }.average(),
                exactMatchCount = runsA.count { it.isExactMatch }
            )

            val reportB = ConditionReport(
                conditionName = "Level-matched EN-001",
                referenceText = REF_FIXTURE_REFERENCE,
                audioStats = levelMatchedStats,
                runs = runsB,
                meanWer = runsB.map { it.wer }.average(),
                meanCer = runsB.map { it.cer }.average(),
                meanRtf = runsB.map { it.rtf }.average(),
                exactMatchCount = runsB.count { it.isExactMatch }
            )

            val reportC = ConditionReport(
                conditionName = "Device microphone",
                referenceText = MIC_WAV_REFERENCE,
                audioStats = micStats,
                runs = runsC,
                meanWer = runsC.map { it.wer }.average(),
                meanCer = runsC.map { it.cer }.average(),
                meanRtf = runsC.map { it.rtf }.average(),
                exactMatchCount = runsC.count { it.isExactMatch }
            )

            Log.i(TAG, "==================================================")
            Log.i(TAG, "LEVEL-MATCHED REFERENCE DIAGNOSTIC SUMMARY")
            Log.i(TAG, "Condition A (Original Ref):    RMS=${"%.2f".format(reportA.audioStats.rmsDbfs)} dBFS | Mean WER=${"%.2f".format(reportA.meanWer * 100)}%, CER=${"%.2f".format(reportA.meanCer * 100)}%, Exact=${reportA.exactMatchCount}/$repetitions")
            Log.i(TAG, "Condition B (Level-Matched):   RMS=${"%.2f".format(reportB.audioStats.rmsDbfs)} dBFS | Mean WER=${"%.2f".format(reportB.meanWer * 100)}%, CER=${"%.2f".format(reportB.meanCer * 100)}%, Exact=${reportB.exactMatchCount}/$repetitions")
            Log.i(TAG, "Condition C (Device Mic):      RMS=${"%.2f".format(reportC.audioStats.rmsDbfs)} dBFS | Mean WER=${"%.2f".format(reportC.meanWer * 100)}%, CER=${"%.2f".format(reportC.meanCer * 100)}%, Exact=${reportC.exactMatchCount}/$repetitions")
            Log.i(TAG, "==================================================")

            FullLevelMatchReport(
                refShaMatched = refShaMatched,
                micShaMatched = micShaMatched,
                calculatedGain = calculatedGain,
                conditionAOriginalRef = reportA,
                conditionBLevelMatchedRef = reportB,
                conditionCDeviceMic = reportC
            )
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
    ): List<AsrIterationResult> {
        val runs = mutableListOf<AsrIterationResult>()
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

            Log.i(TAG, "  [$label Run $i/$repetitions] Raw: \"$rawHyp\" | WER=${"%.2f".format(wer * 100)}%, CER=${"%.2f".format(cer * 100)}%, RTF=${"%.4f".format(rtf)}, Time=${infTimeMs}ms")

            runs.add(
                AsrIterationResult(
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

    fun writePcmToWav(outputFile: File, samples: ShortArray, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        val dataSize = samples.size * 2
        val totalSize = 36 + dataSize

        FileOutputStream(outputFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

            header.put("RIFF".toByteArray())
            header.putInt(totalSize)
            header.put("WAVE".toByteArray())

            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(16)

            header.put("data".toByteArray())
            header.putInt(dataSize)

            fos.write(header.array())

            val pcmBytes = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                pcmBytes.putShort(sample)
            }
            fos.write(pcmBytes.array())
            fos.flush()
        }
    }

    private fun computeAudioStats(
        name: String,
        filePath: String,
        fileSize: Long,
        sha256: String,
        samples: ShortArray,
        sampleRate: Int
    ): AudioStats {
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

        return AudioStats(
            name = name,
            filePath = filePath,
            fileSize = fileSize,
            sha256 = sha256,
            sampleRate = sampleRate,
            channels = 1,
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
            clipMaxCount = clipMaxCount,
            totalClippingCount = clipMinCount + clipMaxCount
        )
    }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
