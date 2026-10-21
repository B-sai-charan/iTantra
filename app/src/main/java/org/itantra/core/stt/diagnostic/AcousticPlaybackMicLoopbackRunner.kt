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
 * Diagnostic runner for Task m1-acoustic-playback-mic-loopback-01.
 *
 * Evaluates production AudioRecordCapture when the known-good EN-001 speech recording
 * is played externally through an acoustic speaker and captured by Device A's microphone.
 */
object AcousticPlaybackMicLoopbackRunner {

    private const val TAG = "ACOUSTIC_LOOPBACK_DIAG"

    const val TARGET_REFERENCE_SENTENCE = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
    const val REF_FIXTURE_ASSET = "speech_test.wav"
    const val REF_FIXTURE_EXPECTED_SHA256 = "6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f"

    data class AudioStats(
        val repIndex: Int,
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

    data class SingleCaptureResult(
        val repIndex: Int,
        val fileName: String,
        val stats: AudioStats,
        val asrResult: AsrIterationResult
    )

    data class FullReport(
        val fixtureShaMatched: Boolean,
        val baselineRuns: List<AsrIterationResult>,
        val baselineStats: AudioStats,
        val loopbackCaptures: List<SingleCaptureResult>
    )

    suspend fun recordSingleLoopback(
        context: Context,
        repIndex: Int,
        captureDurationMs: Long = 8000L
    ): Pair<ShortArray, AudioStats> = withContext(Dispatchers.IO) {
        val fileName = "acoustic_loopback_rep_$repIndex.wav"
        Log.i(TAG, "[Acoustic Loopback Rep $repIndex] Starting production AudioRecordCapture for $captureDurationMs ms...")

        val prodConfig = AudioCaptureConfig()
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
                Log.i(TAG, "[Rep $repIndex STATE] $state")
            }
            override fun onError(errorCode: Int, message: String) {
                Log.e(TAG, "[Rep $repIndex ERROR] Code $errorCode: $message")
            }
        }

        val startRes = capture.start(listener)
        if (startRes.isFailure) {
            val err = "Failed to start AudioRecordCapture: ${startRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            throw IllegalStateException(err)
        }

        Log.i(TAG, "[Rep $repIndex] Capturing acoustic playback...")
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

        Log.i(TAG, "[Rep $repIndex] Captured $totalCount total samples (${micSamples.size * 2} bytes)")

        val outputFile = File(context.filesDir, fileName)
        writePcmToWav(outputFile, micSamples, prodConfig.sampleRateInHz, 1)
        val fileSize = outputFile.length()
        val sha256 = calculateSha256(outputFile)
        val stats = computeAudioStats(
            repIndex = repIndex,
            filePath = outputFile.absolutePath,
            fileSize = fileSize,
            sha256 = sha256,
            samples = micSamples,
            sampleRate = prodConfig.sampleRateInHz
        )

        Log.i(TAG, "[Rep $repIndex WAV CREATED]")
        Log.i(TAG, "  File:     ${outputFile.name} ($fileSize bytes)")
        Log.i(TAG, "  SHA-256:  $sha256")
        Log.i(TAG, "  RMS:      ${stats.rms} (${"%.2f".format(stats.rmsDbfs)} dBFS)")
        Log.i(TAG, "  Peak:     ${stats.peakAbsolute} (${"%.2f".format(stats.peakPercentage)}%)")

        Pair(micSamples, stats)
    }

    suspend fun evaluateBaseline(
        context: Context,
        engine: SherpaWhisperAsrEngine,
        repetitions: Int = 3
    ): Pair<AudioStats, List<AsrIterationResult>> = withContext(Dispatchers.IO) {
        val fixtureBytes = context.assets.open(REF_FIXTURE_ASSET).use { it.readBytes() }
        val fixtureSha256 = calculateSha256(fixtureBytes)
        val fixtureSamples = parseWavSamples(fixtureBytes)
        val stats = computeAudioStats(
            repIndex = 0,
            filePath = REF_FIXTURE_ASSET,
            fileSize = fixtureBytes.size.toLong(),
            sha256 = fixtureSha256,
            samples = fixtureSamples,
            sampleRate = 16000
        )

        val runs = mutableListOf<AsrIterationResult>()
        val audioDurationMs = (fixtureSamples.size * 1000L) / 16000

        for (i in 1..repetitions) {
            var runErr: String? = null
            var rawHyp = ""
            var detectedLang = "en"
            var infTimeMs = 0L
            var audDurMs = audioDurationMs
            var rtf = 0.0

            try {
                val asrRes = engine.transcribe(audio = fixtureSamples, sampleRate = 16000, language = "en")
                rawHyp = asrRes.text
                detectedLang = asrRes.language
                infTimeMs = asrRes.inferenceTimeMs
                audDurMs = asrRes.audioDurationMs
                rtf = if (audDurMs > 0) infTimeMs.toDouble() / audDurMs.toDouble() else 0.0
            } catch (t: Throwable) {
                runErr = "${t.javaClass.simpleName}: ${t.message}"
                Log.e(TAG, "[Baseline Run $i Error] $runErr", t)
            }

            val normalizedHyp = TextNormalizer.normalize(rawHyp)
            val werRes = WerCalculator.calculate(
                reference = TARGET_REFERENCE_SENTENCE,
                hypothesis = rawHyp,
                normalize = true
            )

            runs.add(
                AsrIterationResult(
                    runIndex = i,
                    rawHypothesis = rawHyp,
                    normalizedHypothesis = normalizedHyp,
                    detectedLanguage = detectedLang,
                    wer = werRes.wordStats.errorRate,
                    cer = werRes.charStats.errorRate,
                    isExactMatch = werRes.isExactMatch,
                    inferenceTimeMs = infTimeMs,
                    audioDurationMs = audDurMs,
                    rtf = rtf,
                    errorMessage = runErr
                )
            )
            Log.i(TAG, "  [Baseline Run $i/$repetitions] Raw: \"$rawHyp\" | WER=${"%.2f".format(werRes.wordStats.errorRate * 100)}%, Time=${infTimeMs}ms")
        }

        Pair(stats, runs)
    }

    suspend fun evaluateSingleCapture(
        engine: SherpaWhisperAsrEngine,
        samples: ShortArray,
        sampleRate: Int,
        repIndex: Int,
        stats: AudioStats
    ): SingleCaptureResult = withContext(Dispatchers.IO) {
        val audioDurationMs = (samples.size * 1000L) / sampleRate
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
            Log.e(TAG, "[Loopback Rep $repIndex Error] $runErr", t)
        }

        val normalizedHyp = TextNormalizer.normalize(rawHyp)
        val werRes = WerCalculator.calculate(
            reference = TARGET_REFERENCE_SENTENCE,
            hypothesis = rawHyp,
            normalize = true
        )

        Log.i(TAG, "  [Loopback Rep $repIndex ASR] Raw: \"$rawHyp\" | WER=${"%.2f".format(werRes.wordStats.errorRate * 100)}%, CER=${"%.2f".format(werRes.charStats.errorRate * 100)}%, Time=${infTimeMs}ms, RTF=${"%.4f".format(rtf)}")

        SingleCaptureResult(
            repIndex = repIndex,
            fileName = "acoustic_loopback_rep_$repIndex.wav",
            stats = stats,
            asrResult = AsrIterationResult(
                runIndex = repIndex,
                rawHypothesis = rawHyp,
                normalizedHypothesis = normalizedHyp,
                detectedLanguage = detectedLang,
                wer = werRes.wordStats.errorRate,
                cer = werRes.charStats.errorRate,
                isExactMatch = werRes.isExactMatch,
                inferenceTimeMs = infTimeMs,
                audioDurationMs = audDurMs,
                rtf = rtf,
                errorMessage = runErr
            )
        )
    }

    suspend fun evaluateWavFile(
        context: Context,
        engine: SherpaWhisperAsrEngine,
        fileName: String,
        repIndex: Int
    ): SingleCaptureResult = withContext(Dispatchers.IO) {
        val targetFile = File(context.filesDir, fileName)
        val bytes = targetFile.readBytes()
        val samples = parseWavSamples(bytes)
        val sha256 = calculateSha256(targetFile)
        val stats = computeAudioStats(
            repIndex = repIndex,
            filePath = targetFile.absolutePath,
            fileSize = targetFile.length(),
            sha256 = sha256,
            samples = samples,
            sampleRate = 16000
        )
        evaluateSingleCapture(engine, samples, 16000, repIndex, stats)
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
        repIndex: Int,
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
            repIndex = repIndex,
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
