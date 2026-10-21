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
 * Diagnostic runner for Task m1-mic-gain-diagnostic-01.
 *
 * Evaluates whether linear PCM digital gain variants (1.0x, 2.0x, 4.0x) applied offline
 * to the captured microphone audio change Whisper-Tiny INT8 recognition behavior.
 */
object MicGainDiagnosticRunner {

    private const val TAG = "MIC_GAIN_DIAGNOSTIC"
    const val ORIGINAL_WAV_FILE = "production_capture_source_test.wav"
    const val EXPECTED_ORIGINAL_SHA256 = "26298c77438f0491ee172c810a9c328dc8628866612a41310ff6d37c4c0684d2"
    const val REFERENCE_TEXT = "Hello from iTantra. This is a production capture source isolation test."

    data class VariantGainStats(
        val variantName: String,
        val gainMultiplier: Double,
        val fileName: String,
        val filePath: String,
        val fileSize: Long,
        val sha256: String,
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

    data class VariantAsrRun(
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

    data class VariantReport(
        val stats: VariantGainStats,
        val runs: List<VariantAsrRun>,
        val meanWer: Double,
        val meanCer: Double,
        val meanRtf: Double,
        val exactMatchCount: Int
    )

    data class FullGainDiagnosticReport(
        val originalSha256Matched: Boolean,
        val variants: List<VariantReport>
    )

    suspend fun runGainDiagnostic(
        context: Context,
        repetitions: Int = 3
    ): FullGainDiagnosticReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING OFFLINE MICROPHONE GAIN DIAGNOSTIC")
        Log.i(TAG, "Target Original: $ORIGINAL_WAV_FILE")
        Log.i(TAG, "Expected SHA-256: $EXPECTED_ORIGINAL_SHA256")
        Log.i(TAG, "Reference: \"$REFERENCE_TEXT\"")
        Log.i(TAG, "==================================================")

        // 1. Verify and read original WAV
        val origFile = File(context.filesDir, ORIGINAL_WAV_FILE)
        if (!origFile.exists()) {
            throw IllegalStateException("Original file not found: ${origFile.absolutePath}")
        }
        val origSha256 = calculateSha256(origFile)
        Log.i(TAG, "[INPUT] File size: ${origFile.length()} bytes, SHA-256: $origSha256")
        val shaMatched = origSha256.equals(EXPECTED_ORIGINAL_SHA256, ignoreCase = true)
        if (!shaMatched) {
            val err = "SHA-256 MISMATCH! Expected $EXPECTED_ORIGINAL_SHA256 but found $origSha256"
            Log.e(TAG, err)
            throw IllegalStateException(err)
        }

        val origBytes = origFile.readBytes()
        val origSamples = parseWavSamples(origBytes)
        val sampleRate = 16000

        // 2. Generate Gain Variants (1.0x, 2.0x, 4.0x)
        val gainConfigs = listOf(
            Triple("1.0x (Original)", 1.0, ORIGINAL_WAV_FILE),
            Triple("2.0x Gain", 2.0, "production_capture_source_test_gain2x.wav"),
            Triple("4.0x Gain", 4.0, "production_capture_source_test_gain4x.wav")
        )

        val variantReports = mutableListOf<VariantReport>()
        val engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")

        try {
            for ((label, gain, fileName) in gainConfigs) {
                Log.i(TAG, "==================================================")
                Log.i(TAG, "PROCESSING GAIN VARIANT: $label (gain=$gain)")
                Log.i(TAG, "==================================================")

                val targetFile = File(context.filesDir, fileName)
                val processedSamples: ShortArray

                if (gain == 1.0) {
                    processedSamples = origSamples
                } else {
                    processedSamples = ShortArray(origSamples.size)
                    for (idx in origSamples.indices) {
                        val scaled = (origSamples[idx].toDouble() * gain).roundToInt().coerceIn(-32768, 32767)
                        processedSamples[idx] = scaled.toShort()
                    }
                    writePcmToWav(targetFile, processedSamples, sampleRate, 1)
                }

                val variantFileSize = targetFile.length()
                val variantSha256 = calculateSha256(targetFile)
                val stats = computeVariantStats(label, gain, fileName, targetFile.absolutePath, variantFileSize, variantSha256, processedSamples, sampleRate)

                Log.i(TAG, "Variant Stats [$label]:")
                Log.i(TAG, "  File:     ${targetFile.name} ($variantFileSize bytes)")
                Log.i(TAG, "  SHA-256:  $variantSha256")
                Log.i(TAG, "  Min/Max:  ${stats.minSample} / ${stats.maxSample}")
                Log.i(TAG, "  Peak:     ${stats.peakAbsolute} (${"%.2f".format(stats.peakPercentage)}%)")
                Log.i(TAG, "  RMS:      ${"%.2f".format(stats.rms)} (${"%.2f".format(stats.rmsDbfs)} dBFS)")
                Log.i(TAG, "  Zeros:    ${stats.zeroCount} (${"%.2f".format(stats.zeroPercentage)}%)")
                Log.i(TAG, "  Clipping: Min=${stats.clipMinCount}, Max=${stats.clipMaxCount} (Total=${stats.totalClippingCount})")

                // Run 3 ASR passes
                val runs = mutableListOf<VariantAsrRun>()
                for (i in 1..repetitions) {
                    var runErr: String? = null
                    var rawHyp = ""
                    var detectedLang = "en"
                    var infTimeMs = 0L
                    var audDurMs = stats.durationMs
                    var rtf = 0.0

                    try {
                        val asrRes = engine.transcribe(audio = processedSamples, sampleRate = sampleRate, language = "en")
                        rawHyp = asrRes.text
                        detectedLang = asrRes.language
                        infTimeMs = asrRes.inferenceTimeMs
                        audDurMs = asrRes.audioDurationMs
                        rtf = if (audDurMs > 0) infTimeMs.toDouble() / audDurMs.toDouble() else 0.0
                    } catch (t: Throwable) {
                        runErr = "${t.javaClass.simpleName}: ${t.message}"
                        Log.e(TAG, "[$label Pass $i Error] $runErr", t)
                    }

                    val normalizedHyp = TextNormalizer.normalize(rawHyp)
                    val werRes = WerCalculator.calculate(
                        reference = REFERENCE_TEXT,
                        hypothesis = rawHyp,
                        normalize = true
                    )

                    val wer = werRes.wordStats.errorRate
                    val cer = werRes.charStats.errorRate
                    val isExact = werRes.isExactMatch

                    Log.i(TAG, "  [$label Pass $i/$repetitions] Raw: \"$rawHyp\" | WER=${"%.2f".format(wer * 100)}%, CER=${"%.2f".format(cer * 100)}%, RTF=${"%.4f".format(rtf)}, Time=${infTimeMs}ms")

                    runs.add(
                        VariantAsrRun(
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

                val variantReport = VariantReport(
                    stats = stats,
                    runs = runs,
                    meanWer = runs.map { it.wer }.average(),
                    meanCer = runs.map { it.cer }.average(),
                    meanRtf = runs.map { it.rtf }.average(),
                    exactMatchCount = runs.count { it.isExactMatch }
                )
                variantReports.add(variantReport)
            }

            Log.i(TAG, "==================================================")
            Log.i(TAG, "MICROPHONE GAIN DIAGNOSTIC SUMMARY")
            for (vr in variantReports) {
                Log.i(TAG, "${vr.stats.variantName}: RMS=${"%.2f".format(vr.stats.rmsDbfs)} dBFS, Peak=${vr.stats.peakAbsolute}, Clips=${vr.stats.totalClippingCount} | Mean WER=${"%.2f".format(vr.meanWer * 100)}%, Mean CER=${"%.2f".format(vr.meanCer * 100)}%, Exact=${vr.exactMatchCount}/$repetitions -> Hyp: \"${vr.runs.firstOrNull()?.rawHypothesis}\"")
            }
            Log.i(TAG, "==================================================")

            FullGainDiagnosticReport(
                originalSha256Matched = shaMatched,
                variants = variantReports
            )
        } finally {
            engine.close()
            Log.i(TAG, "[CLEANUP] SherpaWhisperAsrEngine closed.")
        }
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

    private fun computeVariantStats(
        variantName: String,
        gain: Double,
        fileName: String,
        filePath: String,
        fileSize: Long,
        sha256: String,
        samples: ShortArray,
        sampleRate: Int
    ): VariantGainStats {
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

        return VariantGainStats(
            variantName = variantName,
            gainMultiplier = gain,
            fileName = fileName,
            filePath = filePath,
            fileSize = fileSize,
            sha256 = sha256,
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
