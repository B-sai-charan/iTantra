package org.itantra.core.audio.diagnostic

import android.content.Context
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
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
 * Diagnostic runner for Task m1-production-capture-source-isolation-01.
 *
 * Captures live audio using the EXACT production [AudioRecordCapture] and [AudioCaptureConfig],
 * saves the raw PCM16 samples to a canonical 16kHz mono WAV file, computes complete signal metrics,
 * and executes 3 repeated Whisper-Tiny INT8 offline ASR inference passes against the captured audio.
 */
object ProductionCaptureSourceIsolationRunner {

    private const val TAG = "PROD_CAPTURE_ISOLATION"
    const val PRESCRIBED_SENTENCE = "Hello from iTantra. This is a production capture source isolation test."
    const val OUTPUT_WAV_NAME = "production_capture_source_test.wav"

    data class ProductionCaptureConfigSummary(
        val sampleRateInHz: Int,
        val channelConfig: Int,
        val channelCount: Int,
        val audioFormat: Int,
        val primaryAudioSource: Int,
        val primaryAudioSourceName: String,
        val fallbackAudioSource: Int,
        val fallbackAudioSourceName: String,
        val chunkDurationMs: Int,
        val chunkSampleCount: Int,
        val chunkSizeBytes: Int,
        val minBufferSize: Int,
        val allocatedBufferSize: Int
    )

    data class AudioSignalMetrics(
        val sampleCount: Long,
        val durationMs: Long,
        val minSample: Short,
        val maxSample: Short,
        val peakAbsolute: Int,
        val rms: Double,
        val rmsDbfs: Double,
        val zeroCount: Long,
        val zeroPercentage: Double,
        val clippingMinCount: Long,
        val clippingMaxCount: Long,
        val wavFilePath: String,
        val wavFileSize: Long,
        val wavSha256: String
    )

    data class AsrPassResult(
        val runIndex: Int,
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
        val rtf: Double,
        val errorMessage: String? = null
    )

    data class FullIsolationResult(
        val configSummary: ProductionCaptureConfigSummary,
        val signalMetrics: AudioSignalMetrics,
        val asrRuns: List<AsrPassResult>
    )

    suspend fun recordAndEvaluate(
        context: Context,
        captureDurationMs: Long = 8000L,
        repetitions: Int = 3
    ): FullIsolationResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING PRODUCTION CAPTURE SOURCE ISOLATION TEST")
        Log.i(TAG, "Target Speech: \"$PRESCRIBED_SENTENCE\"")
        Log.i(TAG, "Capture Duration: $captureDurationMs ms")
        Log.i(TAG, "==================================================")

        // 1. Inspect production configuration exactly
        val prodConfig = AudioCaptureConfig()
        val minBufSize = AudioRecord.getMinBufferSize(
            prodConfig.sampleRateInHz,
            prodConfig.channelConfig,
            prodConfig.audioFormat
        )
        val allocBufSize = maxOf(minBufSize * 2, prodConfig.chunkSizeBytes * 4)

        val configSummary = ProductionCaptureConfigSummary(
            sampleRateInHz = prodConfig.sampleRateInHz,
            channelConfig = prodConfig.channelConfig,
            channelCount = AudioCaptureConfig.CHANNEL_COUNT,
            audioFormat = prodConfig.audioFormat,
            primaryAudioSource = prodConfig.audioSource,
            primaryAudioSourceName = "VOICE_RECOGNITION (6)",
            fallbackAudioSource = MediaRecorder.AudioSource.MIC,
            fallbackAudioSourceName = "MIC (1)",
            chunkDurationMs = prodConfig.chunkDurationMs,
            chunkSampleCount = prodConfig.chunkSampleCount,
            chunkSizeBytes = prodConfig.chunkSizeBytes,
            minBufferSize = minBufSize,
            allocatedBufferSize = allocBufSize
        )

        Log.i(TAG, "[PROD CONFIG] SampleRate=${configSummary.sampleRateInHz}Hz, Channels=${configSummary.channelCount}, " +
                "Source=${configSummary.primaryAudioSourceName} (fallback ${configSummary.fallbackAudioSourceName}), " +
                "Chunk=${configSummary.chunkDurationMs}ms (${configSummary.chunkSampleCount} samples / ${configSummary.chunkSizeBytes} bytes), " +
                "AllocatedBuffer=${configSummary.allocatedBufferSize} bytes")

        // 2. Capture live audio using existing production AudioRecordCapture
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
                Log.i(TAG, "[CAPTURE STATE] $state")
            }
            override fun onError(errorCode: Int, message: String) {
                Log.e(TAG, "[CAPTURE ERROR] Code $errorCode: $message")
            }
        }

        val startRes = capture.start(listener)
        if (startRes.isFailure) {
            val err = "Failed to start AudioRecordCapture: ${startRes.exceptionOrNull()?.message}"
            Log.e(TAG, err)
            throw IllegalStateException(err)
        }

        Log.i(TAG, "Recording audio for $captureDurationMs ms from production AudioRecordCapture...")
        delay(captureDurationMs)

        capture.stop()
        capture.release()

        val totalCount = totalSamplesCaptured.toInt()
        val allSamples = ShortArray(totalCount)
        var offset = 0
        synchronized(collectedChunks) {
            for (chunk in collectedChunks) {
                System.arraycopy(chunk, 0, allSamples, offset, chunk.size)
                offset += chunk.size
            }
        }

        Log.i(TAG, "Captured $totalCount total samples (${allSamples.size * 2} bytes)")

        // 3. Compute signal statistics
        var minSample: Short = 0
        var maxSample: Short = 0
        var peakAbs = 0
        var sumSquares = 0.0
        var zeroCount = 0L
        var clipMinCount = 0L
        var clipMaxCount = 0L

        for (s in allSamples) {
            if (s < minSample) minSample = s
            if (s > maxSample) maxSample = s
            val absVal = kotlin.math.abs(s.toInt())
            if (absVal > peakAbs) peakAbs = absVal
            sumSquares += s.toDouble() * s.toDouble()
            if (s.toInt() == 0) zeroCount++
            if (s == Short.MIN_VALUE) clipMinCount++
            if (s == Short.MAX_VALUE) clipMaxCount++
        }

        val meanSquare = if (totalCount > 0) sumSquares / totalCount else 0.0
        val rms = kotlin.math.sqrt(meanSquare)
        val rmsDbfs = if (rms > 0.0) 20.0 * kotlin.math.log10(rms / 32768.0) else -96.0
        val zeroPercentage = if (totalCount > 0) (zeroCount.toDouble() / totalCount) * 100.0 else 0.0
        val durationMs = if (prodConfig.sampleRateInHz > 0) (totalCount * 1000L) / prodConfig.sampleRateInHz else 0L

        // Write WAV file
        val outputFile = File(context.filesDir, OUTPUT_WAV_NAME)
        writePcmToWav(outputFile, allSamples, prodConfig.sampleRateInHz, 1)
        val wavFileSize = outputFile.length()
        val sha256 = calculateSha256(outputFile)

        val signalMetrics = AudioSignalMetrics(
            sampleCount = totalCount.toLong(),
            durationMs = durationMs,
            minSample = minSample,
            maxSample = maxSample,
            peakAbsolute = peakAbs,
            rms = rms,
            rmsDbfs = rmsDbfs,
            zeroCount = zeroCount,
            zeroPercentage = zeroPercentage,
            clippingMinCount = clipMinCount,
            clippingMaxCount = clipMaxCount,
            wavFilePath = outputFile.absolutePath,
            wavFileSize = wavFileSize,
            wavSha256 = sha256
        )

        Log.i(TAG, "==================================================")
        Log.i(TAG, "CAPTURED AUDIO SIGNAL METRICS")
        Log.i(TAG, "  WAV File:      ${outputFile.absolutePath} ($wavFileSize bytes)")
        Log.i(TAG, "  SHA-256:       $sha256")
        Log.i(TAG, "  Sample Count:  $totalCount")
        Log.i(TAG, "  Duration:      ${durationMs}ms")
        Log.i(TAG, "  Min/Max:       $minSample / $maxSample")
        Log.i(TAG, "  Peak Absolute: $peakAbs / 32767 (${"%.2f".format((peakAbs.toDouble() / 32767.0) * 100.0)}%)")
        Log.i(TAG, "  RMS:           ${"%.2f".format(rms)} (${"%.2f".format(rmsDbfs)} dBFS)")
        Log.i(TAG, "  Zeros:         $zeroCount (${"%.2f".format(zeroPercentage)}%)")
        Log.i(TAG, "  Clips:         Min=$clipMinCount, Max=$clipMaxCount")
        Log.i(TAG, "==================================================")

        // 4. Run existing Whisper ASR for 3 passes
        Log.i(TAG, "Initializing SherpaWhisperAsrEngine for 3 evaluation passes...")
        val engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")
        val normalizedRef = TextNormalizer.normalize(PRESCRIBED_SENTENCE)
        val asrPasses = mutableListOf<AsrPassResult>()

        try {
            for (i in 1..repetitions) {
                Log.i(TAG, "--------------------------------------------------")
                Log.i(TAG, "ASR PASS $i / $repetitions")
                Log.i(TAG, "--------------------------------------------------")

                var runErr: String? = null
                var rawHyp = ""
                var detectedLang = "en"
                var infTimeMs = 0L
                var audDurMs = durationMs
                var rtf = 0.0

                try {
                    val asrRes = engine.transcribe(audio = allSamples, sampleRate = prodConfig.sampleRateInHz, language = "en")
                    rawHyp = asrRes.text
                    detectedLang = asrRes.language
                    infTimeMs = asrRes.inferenceTimeMs
                    audDurMs = asrRes.audioDurationMs
                    rtf = if (audDurMs > 0) infTimeMs.toDouble() / audDurMs.toDouble() else 0.0
                } catch (t: Throwable) {
                    runErr = "${t.javaClass.simpleName}: ${t.message}"
                    Log.e(TAG, "ASR Pass $i failed: $runErr", t)
                }

                val normalizedHyp = TextNormalizer.normalize(rawHyp)
                val werRes = WerCalculator.calculate(
                    reference = PRESCRIBED_SENTENCE,
                    hypothesis = rawHyp,
                    normalize = true
                )

                val wer = werRes.wordStats.errorRate
                val cer = werRes.charStats.errorRate
                val isExact = werRes.isExactMatch

                Log.i(TAG, "[PASS $i RESULT]")
                Log.i(TAG, "  Raw Hypothesis:        \"$rawHyp\"")
                Log.i(TAG, "  Normalized Hypothesis: \"$normalizedHyp\"")
                Log.i(TAG, "  Detected Language:     $detectedLang")
                Log.i(TAG, "  Reference:             \"$PRESCRIBED_SENTENCE\"")
                Log.i(TAG, "  Normalized Reference:  \"$normalizedRef\"")
                Log.i(TAG, "  WER:                   ${"%.2f".format(wer * 100)}% (Sub: ${werRes.wordStats.substitutions}, Del: ${werRes.wordStats.deletions}, Ins: ${werRes.wordStats.insertions}, RefWords: ${werRes.wordStats.referenceCount})")
                Log.i(TAG, "  CER:                   ${"%.2f".format(cer * 100)}% (Sub: ${werRes.charStats.substitutions}, Del: ${werRes.charStats.deletions}, Ins: ${werRes.charStats.insertions}, RefChars: ${werRes.charStats.referenceCount})")
                Log.i(TAG, "  Exact Match:           $isExact")
                Log.i(TAG, "  Inference Time:        ${infTimeMs} ms")
                Log.i(TAG, "  Audio Duration:        ${audDurMs} ms")
                Log.i(TAG, "  RTF:                   ${"%.4f".format(rtf)}")
                if (runErr != null) {
                    Log.i(TAG, "  Error:                 $runErr")
                }

                asrPasses.add(
                    AsrPassResult(
                        runIndex = i,
                        rawHypothesis = rawHyp,
                        normalizedHypothesis = normalizedHyp,
                        detectedLanguage = detectedLang,
                        reference = PRESCRIBED_SENTENCE,
                        normalizedReference = normalizedRef,
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
        } finally {
            engine.close()
            Log.i(TAG, "[CLEANUP] SherpaWhisperAsrEngine closed.")
        }

        Log.i(TAG, "==================================================")
        Log.i(TAG, "PRODUCTION CAPTURE ISOLATION SUMMARY")
        for (r in asrPasses) {
            Log.i(TAG, "Pass ${r.runIndex}: WER=${"%.2f".format(r.wer * 100)}%, CER=${"%.2f".format(r.cer * 100)}%, RTF=${"%.4f".format(r.rtf)} | Hyp: \"${r.rawHypothesis}\"")
        }
        Log.i(TAG, "==================================================")

        FullIsolationResult(
            configSummary = configSummary,
            signalMetrics = signalMetrics,
            asrRuns = asrPasses
        )
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
