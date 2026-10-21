package org.itantra.core.stt.diagnostic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.itantra.core.stt.SherpaWhisperAsrEngine
import org.itantra.core.stt.benchmark.TextNormalizer
import org.itantra.core.stt.benchmark.WerCalculator
import org.itantra.core.stt.benchmark.WerResult
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Diagnostic runner for Task m1-captured-audio-asr-isolation-01.
 *
 * Transcribes the exact captured microphone WAV file using the existing
 * production Sherpa Whisper-Tiny INT8 engine across 3 repetitions and
 * calculates detailed ASR metrics (raw/normalized hypothesis, language, WER, CER, RTF).
 */
object CapturedAudioAsrIsolationRunner {

    private const val TAG = "CAPTURED_AUDIO_ASR_TEST"
    const val EXPECTED_SHA256 = "5246badc37514c353521a2b83369bc1256a17aa74443472d103fe9eb8c9343ba"
    const val PRESCRIBED_REFERENCE = "Hello from iTantra. This is a microphone recording integrity test."

    data class AsrIterationResult(
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

    data class WavMetadata(
        val filePath: String,
        val fileSize: Long,
        val sha256: String,
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val sampleCount: Int,
        val durationMs: Long
    )

    data class AudioStats(
        val minSample: Short,
        val maxSample: Short,
        val peakAbsolute: Int,
        val rms: Double,
        val rmsDbfs: Double,
        val zeroCount: Long,
        val zeroPercentage: Double,
        val clipMinCount: Long,
        val clipMaxCount: Long
    )

    data class IsolationReport(
        val sha256Matched: Boolean,
        val wavMetadata: WavMetadata,
        val audioStats: AudioStats,
        val runs: List<AsrIterationResult>,
        val errorMessage: String? = null
    )

    suspend fun runIsolationTest(
        context: Context,
        wavFileName: String = "mic_integrity_test.wav",
        repetitions: Int = 3
    ): IsolationReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING CAPTURED AUDIO ASR ISOLATION TEST")
        Log.i(TAG, "Target File: $wavFileName")
        Log.i(TAG, "Expected SHA-256: $EXPECTED_SHA256")
        Log.i(TAG, "Reference: \"$PRESCRIBED_REFERENCE\"")
        Log.i(TAG, "==================================================")

        val file = File(context.filesDir, wavFileName)
        if (!file.exists()) {
            val err = "File not found: ${file.absolutePath}"
            Log.e(TAG, err)
            throw IllegalStateException(err)
        }

        val actualSha256 = calculateSha256(file)
        val fileSize = file.length()
        Log.i(TAG, "[WAV] File size: $fileSize bytes, SHA-256: $actualSha256")

        val sha256Matched = actualSha256.equals(EXPECTED_SHA256, ignoreCase = true)
        if (!sha256Matched) {
            val err = "SHA-256 MISMATCH! Expected $EXPECTED_SHA256 but found $actualSha256"
            Log.e(TAG, err)
            throw IllegalStateException(err)
        }

        // Parse WAV file
        val bytes = file.readBytes()
        val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        val riff = String(bytes, 0, 4)
        val wave = String(bytes, 8, 4)
        val channels = header.getShort(22).toInt()
        val sampleRate = header.getInt(24)
        val bitsPerSample = header.getShort(34).toInt()

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
        val samplesBuffer = ByteBuffer.wrap(bytes, dataOffset, pcmLength).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val samples = ShortArray(sampleCount)
        samplesBuffer.get(samples)
        val durationMs = (sampleCount * 1000L) / sampleRate

        val wavMetadata = WavMetadata(
            filePath = file.absolutePath,
            fileSize = fileSize,
            sha256 = actualSha256,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            sampleCount = sampleCount,
            durationMs = durationMs
        )

        // Compute signal statistics
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

        val meanSquare = if (sampleCount > 0) sumSquares / sampleCount else 0.0
        val rms = kotlin.math.sqrt(meanSquare)
        val rmsDbfs = if (rms > 0.0) 20.0 * kotlin.math.log10(rms / 32768.0) else -96.0
        val zeroPercentage = if (sampleCount > 0) (zeroCount.toDouble() / sampleCount) * 100.0 else 0.0

        val audioStats = AudioStats(
            minSample = minSample,
            maxSample = maxSample,
            peakAbsolute = peakAbs,
            rms = rms,
            rmsDbfs = rmsDbfs,
            zeroCount = zeroCount,
            zeroPercentage = zeroPercentage,
            clipMinCount = clipMinCount,
            clipMaxCount = clipMaxCount
        )

        Log.i(TAG, "[AUDIO STATS] Samples: $sampleCount, Duration: ${durationMs}ms, RMS: ${"%.2f".format(rms)} (${"%.2f".format(rmsDbfs)} dBFS), Peak: $peakAbs, Zeros: ${"%.2f".format(zeroPercentage)}%")

        // Execute ASR runs using existing SherpaWhisperAsrEngine
        Log.i(TAG, "[ASR] Initializing SherpaWhisperAsrEngine (Whisper Tiny INT8, lang=en)...")
        val engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")
        val runResults = mutableListOf<AsrIterationResult>()
        val normalizedRef = TextNormalizer.normalize(PRESCRIBED_REFERENCE)

        try {
            for (i in 1..repetitions) {
                Log.i(TAG, "--------------------------------------------------")
                Log.i(TAG, "ASR RUN $i / $repetitions")
                Log.i(TAG, "--------------------------------------------------")

                var runErr: String? = null
                var rawHyp = ""
                var detectedLang = "en"
                var infTimeMs = 0L
                var audDurMs = durationMs
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
                    Log.e(TAG, "ASR Run $i failed: $runErr", t)
                }

                val normalizedHyp = TextNormalizer.normalize(rawHyp)
                val werRes = WerCalculator.calculate(
                    reference = PRESCRIBED_REFERENCE,
                    hypothesis = rawHyp,
                    normalize = true
                )

                val wer = werRes.wordStats.errorRate
                val cer = werRes.charStats.errorRate
                val isExact = werRes.isExactMatch

                Log.i(TAG, "[RUN $i RESULT]")
                Log.i(TAG, "  Raw Hypothesis:        \"$rawHyp\"")
                Log.i(TAG, "  Normalized Hypothesis: \"$normalizedHyp\"")
                Log.i(TAG, "  Detected Language:     $detectedLang")
                Log.i(TAG, "  Reference:             \"$PRESCRIBED_REFERENCE\"")
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

                runResults.add(
                    AsrIterationResult(
                        runIndex = i,
                        rawHypothesis = rawHyp,
                        normalizedHypothesis = normalizedHyp,
                        detectedLanguage = detectedLang,
                        reference = PRESCRIBED_REFERENCE,
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
        Log.i(TAG, "CAPTURED AUDIO ASR ISOLATION SUMMARY")
        for (r in runResults) {
            Log.i(TAG, "Run ${r.runIndex}: WER=${"%.2f".format(r.wer * 100)}%, CER=${"%.2f".format(r.cer * 100)}%, RTF=${"%.4f".format(r.rtf)} | Hyp: \"${r.rawHypothesis}\"")
        }
        Log.i(TAG, "==================================================")

        IsolationReport(
            sha256Matched = sha256Matched,
            wavMetadata = wavMetadata,
            audioStats = audioStats,
            runs = runResults
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
