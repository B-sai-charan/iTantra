package org.itantra.core.stt.diagnostic

import android.content.Context
import android.media.MediaRecorder
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
 * Diagnostic runner for Task m1-audiosource-production-effect-01.
 *
 * Evaluates whether changing ONLY the AudioRecord AudioSource from production VOICE_RECOGNITION (6)
 * to MIC (1) materially changes live Whisper recognition on the Lenovo TB-X6C6F.
 *
 * Preserves the exact production AudioRecordCapture path, buffer sizing, thread priority,
 * and SherpaWhisperAsrEngine configuration across both conditions.
 */
object AudioSourceProductionEffectRunner {

    private const val TAG = "AUDIOSOURCE_PROD_EFFECT"

    const val TARGET_REFERENCE_SENTENCE = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
    const val FILE_PROD_VR = "production_effect_voice_recognition.wav"
    const val FILE_ALT_MIC = "production_effect_mic.wav"

    data class AudioStats(
        val conditionName: String,
        val audioSourceId: Int,
        val audioSourceName: String,
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
        val audioSourceId: Int,
        val audioSourceName: String,
        val fileName: String,
        val referenceText: String,
        val audioStats: AudioStats,
        val runs: List<AsrIterationResult>,
        val meanWer: Double,
        val meanCer: Double,
        val meanRtf: Double,
        val exactMatchCount: Int
    )

    data class ExperimentReport(
        val conditionA: ConditionReport,
        val conditionB: ConditionReport
    )

    suspend fun recordCondition(
        context: Context,
        conditionName: String,
        audioSource: Int,
        outputFileName: String,
        captureDurationMs: Long = 8000L
    ): Pair<ShortArray, AudioStats> = withContext(Dispatchers.IO) {
        val sourceLabel = if (audioSource == MediaRecorder.AudioSource.VOICE_RECOGNITION) "VOICE_RECOGNITION" else "MIC"
        Log.i(TAG, "[$conditionName] Initializing production AudioRecordCapture with AudioSource=$sourceLabel ($audioSource)...")

        // Construct AudioCaptureConfig changing ONLY audioSource
        val config = AudioCaptureConfig(audioSource = audioSource)
        val capture = AudioRecordCapture(config)
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
                Log.i(TAG, "[$conditionName STATE] $state")
            }
            override fun onError(errorCode: Int, message: String) {
                Log.e(TAG, "[$conditionName ERROR] Code $errorCode: $message")
            }
        }

        val startRes = capture.start(listener)
        if (startRes.isFailure) {
            val err = "Failed to start AudioRecordCapture for $conditionName: ${startRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            throw IllegalStateException(err)
        }

        Log.i(TAG, "[$conditionName] Recording live speech for $captureDurationMs ms (Target Sentence: \"$TARGET_REFERENCE_SENTENCE\")...")
        delay(captureDurationMs)

        capture.stop()
        capture.release()

        val totalCount = totalSamplesCaptured.toInt()
        val samples = ShortArray(totalCount)
        var offset = 0
        synchronized(collectedChunks) {
            for (chunk in collectedChunks) {
                System.arraycopy(chunk, 0, samples, offset, chunk.size)
                offset += chunk.size
            }
        }

        Log.i(TAG, "[$conditionName] Captured $totalCount samples (${samples.size * 2} bytes)")

        val outputFile = File(context.filesDir, outputFileName)
        writePcmToWav(outputFile, samples, config.sampleRateInHz, 1)
        val fileSize = outputFile.length()
        val sha256 = calculateSha256(outputFile)
        val stats = computeAudioStats(conditionName, audioSource, sourceLabel, outputFile.absolutePath, fileSize, sha256, samples, config.sampleRateInHz)

        Log.i(TAG, "[$conditionName WAV CREATED]")
        Log.i(TAG, "  File:     ${outputFile.name} ($fileSize bytes)")
        Log.i(TAG, "  SHA-256:  $sha256")
        Log.i(TAG, "  RMS:      ${stats.rms} (${"%.2f".format(stats.rmsDbfs)} dBFS)")
        Log.i(TAG, "  Peak:     ${stats.peakAbsolute} (${"%.2f".format(stats.peakPercentage)}%)")
        Log.i(TAG, "  Mean DC:  ${stats.meanSample}")
        Log.i(TAG, "  Clips:    ${stats.totalClippingCount}")

        Pair(samples, stats)
    }

    suspend fun evaluateCondition(
        context: Context,
        engine: SherpaWhisperAsrEngine,
        samples: ShortArray,
        sampleRate: Int,
        conditionName: String,
        audioSourceId: Int,
        audioSourceName: String,
        outputFileName: String,
        stats: AudioStats,
        repetitions: Int = 3
    ): ConditionReport = withContext(Dispatchers.IO) {
        val runs = mutableListOf<AsrIterationResult>()
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
                Log.e(TAG, "[$conditionName Run $i Error] $runErr", t)
            }

            val normalizedHyp = TextNormalizer.normalize(rawHyp)
            val werRes = WerCalculator.calculate(
                reference = TARGET_REFERENCE_SENTENCE,
                hypothesis = rawHyp,
                normalize = true
            )

            val wer = werRes.wordStats.errorRate
            val cer = werRes.charStats.errorRate
            val isExact = werRes.isExactMatch

            Log.i(TAG, "  [$conditionName Run $i/$repetitions] Raw: \"$rawHyp\" | WER=${"%.2f".format(wer * 100)}%, CER=${"%.2f".format(cer * 100)}%, RTF=${"%.4f".format(rtf)}, Time=${infTimeMs}ms")

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

        ConditionReport(
            conditionName = conditionName,
            audioSourceId = audioSourceId,
            audioSourceName = audioSourceName,
            fileName = outputFileName,
            referenceText = TARGET_REFERENCE_SENTENCE,
            audioStats = stats,
            runs = runs,
            meanWer = runs.map { it.wer }.average(),
            meanCer = runs.map { it.cer }.average(),
            meanRtf = runs.map { it.rtf }.average(),
            exactMatchCount = runs.count { it.isExactMatch }
        )
    }

    suspend fun runProductionEffectExperiment(
        context: Context,
        captureDurationMs: Long = 8000L,
        repetitions: Int = 3
    ): ExperimentReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING AUDIOSOURCE PRODUCTION EFFECT EXPERIMENT")
        Log.i(TAG, "Target Spoken Sentence: \"$TARGET_REFERENCE_SENTENCE\"")
        Log.i(TAG, "Duration per source: $captureDurationMs ms")
        Log.i(TAG, "==================================================")

        // Step A: Production Source (VOICE_RECOGNITION)
        Log.i(TAG, "--- CONDITION A: PRODUCTION SOURCE (VOICE_RECOGNITION) ---")
        val (samplesA, statsA) = recordCondition(
            context = context,
            conditionName = "Condition A (Production: VOICE_RECOGNITION)",
            audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION,
            outputFileName = FILE_PROD_VR,
            captureDurationMs = captureDurationMs
        )

        Log.i(TAG, "Pausing 2 seconds before Condition B...")
        delay(2000L)

        // Step B: Alternate Source (MIC)
        Log.i(TAG, "--- CONDITION B: ALTERNATE SOURCE (MIC) ---")
        val (samplesB, statsB) = recordCondition(
            context = context,
            conditionName = "Condition B (Alternate: MIC)",
            audioSource = MediaRecorder.AudioSource.MIC,
            outputFileName = FILE_ALT_MIC,
            captureDurationMs = captureDurationMs
        )

        // Evaluate both through exact production Whisper engine
        val engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")
        try {
            Log.i(TAG, "--- EVALUATING CONDITION A (VOICE_RECOGNITION) ---")
            val reportA = evaluateCondition(
                context = context,
                engine = engine,
                samples = samplesA,
                sampleRate = 16000,
                conditionName = "Condition A (VOICE_RECOGNITION)",
                audioSourceId = MediaRecorder.AudioSource.VOICE_RECOGNITION,
                audioSourceName = "VOICE_RECOGNITION",
                outputFileName = FILE_PROD_VR,
                stats = statsA,
                repetitions = repetitions
            )

            Log.i(TAG, "--- EVALUATING CONDITION B (MIC) ---")
            val reportB = evaluateCondition(
                context = context,
                engine = engine,
                samples = samplesB,
                sampleRate = 16000,
                conditionName = "Condition B (MIC)",
                audioSourceId = MediaRecorder.AudioSource.MIC,
                audioSourceName = "MIC",
                outputFileName = FILE_ALT_MIC,
                stats = statsB,
                repetitions = repetitions
            )

            Log.i(TAG, "==================================================")
            Log.i(TAG, "AUDIOSOURCE PRODUCTION EFFECT SUMMARY")
            Log.i(TAG, "Condition A (VOICE_REC): Mean WER=${"%.2f".format(reportA.meanWer * 100)}%, CER=${"%.2f".format(reportA.meanCer * 100)}%, Exact=${reportA.exactMatchCount}/$repetitions -> \"${reportA.runs.firstOrNull()?.rawHypothesis}\"")
            Log.i(TAG, "Condition B (MIC):       Mean WER=${"%.2f".format(reportB.meanWer * 100)}%, CER=${"%.2f".format(reportB.meanCer * 100)}%, Exact=${reportB.exactMatchCount}/$repetitions -> \"${reportB.runs.firstOrNull()?.rawHypothesis}\"")
            Log.i(TAG, "==================================================")

            ExperimentReport(conditionA = reportA, conditionB = reportB)
        } finally {
            engine.close()
            Log.i(TAG, "[CLEANUP] SherpaWhisperAsrEngine closed.")
        }
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
        conditionName: String,
        audioSourceId: Int,
        audioSourceName: String,
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
            conditionName = conditionName,
            audioSourceId = audioSourceId,
            audioSourceName = audioSourceName,
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
