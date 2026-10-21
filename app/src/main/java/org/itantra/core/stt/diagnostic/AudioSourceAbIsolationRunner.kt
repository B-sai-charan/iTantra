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
 * Diagnostic runner for Task m1-audiosource-ab-isolation-01.
 *
 * Compares audio waveforms and Whisper ASR performance across Android AudioRecord audio sources:
 * - Capture A: [MediaRecorder.AudioSource.VOICE_RECOGNITION]
 * - Capture B: [MediaRecorder.AudioSource.MIC]
 *
 * Evaluates the exact target sentence:
 * "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
 */
object AudioSourceAbIsolationRunner {

    private const val TAG = "AUDIOSOURCE_AB_DIAG"

    const val TARGET_REFERENCE_SENTENCE = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
    const val FILE_VOICE_RECOGNITION = "scratch_audio_source_voice_recognition.wav"
    const val FILE_MIC = "scratch_audio_source_mic.wav"

    data class AudioStats(
        val sourceName: String,
        val audioSourceId: Int,
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

    data class SourceReport(
        val sourceName: String,
        val audioSourceId: Int,
        val fileName: String,
        val referenceText: String,
        val audioStats: AudioStats,
        val runs: List<AsrIterationResult>,
        val meanWer: Double,
        val meanCer: Double,
        val meanRtf: Double,
        val exactMatchCount: Int
    )

    data class AbReport(
        val reportA: SourceReport,
        val reportB: SourceReport
    )

    suspend fun recordSingleSource(
        context: Context,
        audioSource: Int,
        outputFileName: String,
        captureDurationMs: Long = 8000L
    ): Pair<ShortArray, AudioStats> = withContext(Dispatchers.IO) {
        val sourceLabel = if (audioSource == MediaRecorder.AudioSource.VOICE_RECOGNITION) "VOICE_RECOGNITION" else "MIC"
        Log.i(TAG, "Starting live capture for source: $sourceLabel ($audioSource), duration: $captureDurationMs ms")

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
                Log.i(TAG, "[$sourceLabel STATE] $state")
            }
            override fun onError(errorCode: Int, message: String) {
                Log.e(TAG, "[$sourceLabel ERROR] Code $errorCode: $message")
            }
        }

        val startRes = capture.start(listener)
        if (startRes.isFailure) {
            val err = "Failed to start AudioRecordCapture for $sourceLabel: ${startRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            throw IllegalStateException(err)
        }

        Log.i(TAG, "[$sourceLabel] Recording live audio for $captureDurationMs ms (Sentence: \"$TARGET_REFERENCE_SENTENCE\")...")
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

        Log.i(TAG, "[$sourceLabel] Captured $totalCount samples (${samples.size * 2} bytes)")

        val outputFile = File(context.filesDir, outputFileName)
        writePcmToWav(outputFile, samples, config.sampleRateInHz, 1)
        val fileSize = outputFile.length()
        val sha256 = calculateSha256(outputFile)
        val stats = computeAudioStats(sourceLabel, audioSource, outputFile.absolutePath, fileSize, sha256, samples, config.sampleRateInHz)

        Log.i(TAG, "[$sourceLabel WAV CREATED]")
        Log.i(TAG, "  File:     ${outputFile.name} ($fileSize bytes)")
        Log.i(TAG, "  SHA-256:  $sha256")
        Log.i(TAG, "  RMS:      ${stats.rms} (${"%.2f".format(stats.rmsDbfs)} dBFS)")
        Log.i(TAG, "  Peak:     ${stats.peakAbsolute} (${"%.2f".format(stats.peakPercentage)}%)")
        Log.i(TAG, "  Mean DC:  ${stats.meanSample}")
        Log.i(TAG, "  Clips:    ${stats.totalClippingCount}")

        Pair(samples, stats)
    }

    suspend fun evaluateWav(
        context: Context,
        engine: SherpaWhisperAsrEngine,
        samples: ShortArray,
        sampleRate: Int,
        sourceLabel: String,
        audioSourceId: Int,
        outputFileName: String,
        stats: AudioStats,
        repetitions: Int = 3
    ): SourceReport = withContext(Dispatchers.IO) {
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
                Log.e(TAG, "[$sourceLabel Run $i Error] $runErr", t)
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

            Log.i(TAG, "  [$sourceLabel Run $i/$repetitions] Raw: \"$rawHyp\" | WER=${"%.2f".format(wer * 100)}%, CER=${"%.2f".format(cer * 100)}%, RTF=${"%.4f".format(rtf)}, Time=${infTimeMs}ms")

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

        SourceReport(
            sourceName = sourceLabel,
            audioSourceId = audioSourceId,
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

    suspend fun runFullAbIsolationTest(
        context: Context,
        captureDurationMs: Long = 8000L,
        repetitions: Int = 3
    ): AbReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING AUDIOSOURCE A/B ISOLATION DIAGNOSTIC")
        Log.i(TAG, "Target Spoken Sentence: \"$TARGET_REFERENCE_SENTENCE\"")
        Log.i(TAG, "Capture Duration: $captureDurationMs ms per source")
        Log.i(TAG, "==================================================")

        // Capture A: VOICE_RECOGNITION
        Log.i(TAG, "--- STEP 1: CAPTURING SOURCE A (VOICE_RECOGNITION) ---")
        val (samplesA, statsA) = recordSingleSource(
            context = context,
            audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION,
            outputFileName = FILE_VOICE_RECOGNITION,
            captureDurationMs = captureDurationMs
        )

        Log.i(TAG, "Pausing 2 seconds before source B...")
        delay(2000L)

        // Capture B: MIC
        Log.i(TAG, "--- STEP 2: CAPTURING SOURCE B (MIC) ---")
        val (samplesB, statsB) = recordSingleSource(
            context = context,
            audioSource = MediaRecorder.AudioSource.MIC,
            outputFileName = FILE_MIC,
            captureDurationMs = captureDurationMs
        )

        // Whisper Evaluation
        val engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")
        try {
            Log.i(TAG, "--- STEP 3: EVALUATING SOURCE A (VOICE_RECOGNITION) ---")
            val reportA = evaluateWav(
                context = context,
                engine = engine,
                samples = samplesA,
                sampleRate = 16000,
                sourceLabel = "VOICE_RECOGNITION",
                audioSourceId = MediaRecorder.AudioSource.VOICE_RECOGNITION,
                outputFileName = FILE_VOICE_RECOGNITION,
                stats = statsA,
                repetitions = repetitions
            )

            Log.i(TAG, "--- STEP 4: EVALUATING SOURCE B (MIC) ---")
            val reportB = evaluateWav(
                context = context,
                engine = engine,
                samples = samplesB,
                sampleRate = 16000,
                sourceLabel = "MIC",
                audioSourceId = MediaRecorder.AudioSource.MIC,
                outputFileName = FILE_MIC,
                stats = statsB,
                repetitions = repetitions
            )

            Log.i(TAG, "==================================================")
            Log.i(TAG, "AUDIOSOURCE A/B ISOLATION SUMMARY")
            Log.i(TAG, "VOICE_RECOGNITION: Mean WER=${"%.2f".format(reportA.meanWer * 100)}%, CER=${"%.2f".format(reportA.meanCer * 100)}%, Exact=${reportA.exactMatchCount}/$repetitions -> \"${reportA.runs.firstOrNull()?.rawHypothesis}\"")
            Log.i(TAG, "MIC:               Mean WER=${"%.2f".format(reportB.meanWer * 100)}%, CER=${"%.2f".format(reportB.meanCer * 100)}%, Exact=${reportB.exactMatchCount}/$repetitions -> \"${reportB.runs.firstOrNull()?.rawHypothesis}\"")
            Log.i(TAG, "==================================================")

            AbReport(reportA = reportA, reportB = reportB)
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
        sourceName: String,
        audioSourceId: Int,
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
            sourceName = sourceName,
            audioSourceId = audioSourceId,
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
