package org.itantra.core.stt.diagnostic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.itantra.core.audio.AudioCaptureConfig
import org.itantra.core.audio.AudioCaptureListener
import org.itantra.core.audio.AudioCaptureMetrics
import org.itantra.core.audio.AudioCaptureState
import org.itantra.core.audio.AudioRecordCapture
import org.itantra.core.stt.SherpaWhisperAsrEngine
import org.itantra.core.stt.benchmark.TextNormalizer
import org.itantra.core.stt.benchmark.WerCalculator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.ArrayList

/**
 * Diagnostic runner for Task m1-mic-loopback-reference-comparison-01.
 *
 * Compares live production-path AudioRecordCapture against the frozen EN-001 reference fixture
 * using the EXACT same spoken sentence and the EXACT same SherpaWhisperAsrEngine.
 */
object MicLoopbackReferenceComparisonRunner {

    private const val TAG = "MIC_LOOPBACK_DIAG"

    const val TARGET_REFERENCE_SENTENCE = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
    const val REF_FIXTURE_ASSET = "speech_test.wav"
    const val REF_FIXTURE_EXPECTED_SHA256 = "6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f"
    const val MIC_OUTPUT_WAV = "mic_loopback_ref_test.wav"

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
        val meanSample: Double,
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
        val audioSourceDescription: String,
        val fileName: String,
        val referenceText: String,
        val audioStats: AudioStats,
        val runs: List<AsrIterationResult>,
        val meanWer: Double,
        val meanCer: Double,
        val meanRtf: Double,
        val exactMatchCount: Int
    )

    data class ComparisonReport(
        val fixtureShaMatched: Boolean,
        val referenceReport: ConditionReport,
        val micReport: ConditionReport
    )

    suspend fun runComparison(
        context: Context,
        captureDurationMs: Long = 8000L,
        repetitions: Int = 3
    ): ComparisonReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING MIC LOOPBACK / REFERENCE COMPARISON DIAGNOSTIC")
        Log.i(TAG, "Target Sentence: \"$TARGET_REFERENCE_SENTENCE\"")
        Log.i(TAG, "Capture Duration: $captureDurationMs ms")
        Log.i(TAG, "==================================================")

        // 1. Verify Reference Fixture
        val fixtureBytes = context.assets.open(REF_FIXTURE_ASSET).use { it.readBytes() }
        val fixtureSha256 = calculateSha256(fixtureBytes)
        val fixtureShaMatched = fixtureSha256.equals(REF_FIXTURE_EXPECTED_SHA256, ignoreCase = true)
        if (!fixtureShaMatched) {
            val err = "Reference fixture SHA mismatch! Expected $REF_FIXTURE_EXPECTED_SHA256 but got $fixtureSha256"
            Log.e(TAG, err)
            throw IllegalStateException(err)
        }
        val fixtureSamples = parseWavSamples(fixtureBytes)
        val fixtureStats = computeAudioStats(
            name = "Frozen EN-001 Benchmark Fixture",
            filePath = REF_FIXTURE_ASSET,
            fileSize = fixtureBytes.size.toLong(),
            sha256 = fixtureSha256,
            samples = fixtureSamples,
            sampleRate = 16000
        )

        // 2. Capture Live Speech from Unmodified Production AudioRecordCapture Path
        val prodConfig = AudioCaptureConfig() // Exact default production config (VOICE_RECOGNITION, 16kHz, mono, 20ms frame)
        val capture = AudioRecordCapture(prodConfig)
        val collectedChunks = ArrayList<ShortArray>()
        var totalSamplesCaptured = 0L

        val listener = object : AudioCaptureListener {
            override fun onAudioFrame(audioData: ShortArray, frameSize: Int) {
                val copy = audioData.copyOf(frameSize)
                synchronized(collectedChunks) {
                    collectedChunks.add(copy)
                    totalSamplesCaptured += frameSize
                }
            }

            override fun onAudioTelemetry(metrics: AudioCaptureMetrics, rmsDbfs: Double) {}
            override fun onStateChanged(state: AudioCaptureState) {
                Log.i(TAG, "[PROD CAPTURE STATE] $state")
            }
            override fun onError(errorCode: Int, message: String) {
                Log.e(TAG, "[PROD CAPTURE ERROR] Code $errorCode: $message")
            }
        }

        val startRes = capture.start(listener)
        if (startRes.isFailure) {
            val err = "Failed to start production AudioRecordCapture: ${startRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            throw IllegalStateException(err)
        }

        Log.i(TAG, "Recording live audio for $captureDurationMs ms (Sentence: \"$TARGET_REFERENCE_SENTENCE\")...")
        delay(captureDurationMs)

        capture.stop()
        capture.release()

        val totalCount = totalSamplesCaptured.toInt()
        val micSamples = ShortArray(totalCount)
        var offset = 0
        synchronized(collectedChunks) {
            for (chunk in collectedChunks) {
                System.arraycopy(chunk, 0, micSamples, offset, chunk.size)
                offset += chunk.size
            }
        }

        Log.i(TAG, "Captured $totalCount total samples (${micSamples.size * 2} bytes)")

        val outputFile = File(context.filesDir, MIC_OUTPUT_WAV)
        writePcmToWav(outputFile, micSamples, prodConfig.sampleRateInHz, 1)
        val micFileSize = outputFile.length()
        val micSha256 = calculateSha256(outputFile)
        val micStats = computeAudioStats(
            name = "Live Production Microphone Capture",
            filePath = outputFile.absolutePath,
            fileSize = micFileSize,
            sha256 = micSha256,
            samples = micSamples,
            sampleRate = prodConfig.sampleRateInHz
        )

        Log.i(TAG, "[MIC WAV CREATED]")
        Log.i(TAG, "  File:     ${outputFile.name} ($micFileSize bytes)")
        Log.i(TAG, "  SHA-256:  $micSha256")
        Log.i(TAG, "  RMS:      ${micStats.rms} (${"%.2f".format(micStats.rmsDbfs)} dBFS)")
        Log.i(TAG, "  Peak:     ${micStats.peakAbsolute} (${"%.2f".format(micStats.peakPercentage)}%)")
        Log.i(TAG, "  Mean DC:  ${micStats.meanSample}")
        Log.i(TAG, "  Clips:    ${micStats.totalClippingCount}")

        // 3. Evaluate Both through Exact Production SherpaWhisperAsrEngine
        val engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")
        try {
            // Condition 1: Reference Fixture
            Log.i(TAG, "--- EVALUATING FROZEN REFERENCE (speech_test.wav) ---")
            val refRuns = executeRuns(engine, fixtureSamples, 16000, TARGET_REFERENCE_SENTENCE, repetitions, "REF-FIXTURE")

            // Condition 2: Live Production Mic WAV
            Log.i(TAG, "--- EVALUATING LIVE MIC WAV (mic_loopback_ref_test.wav) ---")
            val micRuns = executeRuns(engine, micSamples, prodConfig.sampleRateInHz, TARGET_REFERENCE_SENTENCE, repetitions, "LIVE-MIC")

            val refReport = ConditionReport(
                conditionName = "Frozen EN-001 Reference Benchmark Fixture",
                audioSourceDescription = "Pre-recorded LibriSpeech asset (speech_test.wav)",
                fileName = REF_FIXTURE_ASSET,
                referenceText = TARGET_REFERENCE_SENTENCE,
                audioStats = fixtureStats,
                runs = refRuns,
                meanWer = refRuns.map { it.wer }.average(),
                meanCer = refRuns.map { it.cer }.average(),
                meanRtf = refRuns.map { it.rtf }.average(),
                exactMatchCount = refRuns.count { it.isExactMatch }
            )

            val micReport = ConditionReport(
                conditionName = "Live Production Microphone Capture",
                audioSourceDescription = "Current production AudioRecordCapture (VOICE_RECOGNITION, 16kHz mono)",
                fileName = MIC_OUTPUT_WAV,
                referenceText = TARGET_REFERENCE_SENTENCE,
                audioStats = micStats,
                runs = micRuns,
                meanWer = micRuns.map { it.wer }.average(),
                meanCer = micRuns.map { it.cer }.average(),
                meanRtf = micRuns.map { it.rtf }.average(),
                exactMatchCount = micRuns.count { it.isExactMatch }
            )

            Log.i(TAG, "==================================================")
            Log.i(TAG, "MIC LOOPBACK REFERENCE COMPARISON SUMMARY")
            Log.i(TAG, "Reference: Mean WER=${"%.2f".format(refReport.meanWer * 100)}%, CER=${"%.2f".format(refReport.meanCer * 100)}%, Exact=${refReport.exactMatchCount}/$repetitions -> \"${refRuns.firstOrNull()?.rawHypothesis}\"")
            Log.i(TAG, "Live Mic:  Mean WER=${"%.2f".format(micReport.meanWer * 100)}%, CER=${"%.2f".format(micReport.meanCer * 100)}%, Exact=${micReport.exactMatchCount}/$repetitions -> \"${micRuns.firstOrNull()?.rawHypothesis}\"")
            Log.i(TAG, "==================================================")

            ComparisonReport(
                fixtureShaMatched = fixtureShaMatched,
                referenceReport = refReport,
                micReport = micReport
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
        var sumSamples = 0.0
        var zeroCount = 0L
        var clipMinCount = 0L
        var clipMaxCount = 0L

        for (s in samples) {
            if (s < minSample) minSample = s
            if (s > maxSample) maxSample = s
            val absVal = kotlin.math.abs(s.toInt())
            if (absVal > peakAbs) peakAbs = absVal
            sumSquares += s.toDouble() * s.toDouble()
            sumSamples += s.toDouble()
            if (s.toInt() == 0) zeroCount++
            if (s == Short.MIN_VALUE) clipMinCount++
            if (s == Short.MAX_VALUE) clipMaxCount++
        }

        val totalCount = samples.size
        val meanSquare = if (totalCount > 0) sumSquares / totalCount else 0.0
        val meanSample = if (totalCount > 0) sumSamples / totalCount else 0.0
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
            meanSample = meanSample,
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
}
