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

/**
 * Isolated diagnostic runner for TASK: M1-LIVE-MIC-GAIN-AB-01.
 *
 * Evaluates whether software gain scaling (1x, 2x, 4x, 8x) on the captured
 * live microphone audio segment materially affects Whisper-Tiny INT8 Hindi recognition.
 */
object LiveMicGainAbRunner {

    private const val TAG = "M1_GAIN_AB"
    const val PRESCRIBED_HINDI_REFERENCE = "नमस्ते, यह iTantra का हिंदी परीक्षण है।"
    const val SOURCE_WAV_NAME = "live_hindi_forensic_01.wav"

    data class GainVariantResult(
        val gainLabel: String,
        val multiplier: Float,
        val wavFileName: String,
        val wavFileSize: Long,
        val sha256: String,
        val sampleCount: Int,
        val durationMs: Long,
        val rms: Double,
        val rmsDbfs: Double,
        val peakAbsolute: Int,
        val peakDbfs: Double,
        val clippingCount: Long,
        val clippingPercentage: Double,
        val zeroCount: Long,
        val zeroPercentage: Double,
        val rawHypothesis: String,
        val normalizedHypothesis: String,
        val detectedLanguage: String,
        val inferenceTimeMs: Long,
        val rtf: Double,
        val wer: Double,
        val cer: Double,
        val isExactMatch: Boolean
    )

    data class GainAbReport(
        val deviceModel: String,
        val sourceWavName: String,
        val sourceSha256: String,
        val variants: List<GainVariantResult>,
        val classification: String,
        val classificationDetails: String
    )

    suspend fun runGainAbExperiment(
        context: Context
    ): GainAbReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING M1-LIVE-MIC-GAIN-AB-01 EXPERIMENT")
        Log.i(TAG, "Source WAV: $SOURCE_WAV_NAME")
        Log.i(TAG, "Reference: \"$PRESCRIBED_HINDI_REFERENCE\"")
        Log.i(TAG, "==================================================")

        val sourceFile = File(context.filesDir, SOURCE_WAV_NAME)
        if (!sourceFile.exists()) {
            throw IllegalStateException("Source forensic WAV not found at: ${sourceFile.absolutePath}")
        }

        val rawSamples = readPcm16Wav(sourceFile)
        val sampleCount = rawSamples.size
        val durationMs = (sampleCount * 1000L) / 16000L
        val sourceSha = calculateSha256(sourceFile)

        Log.i(TAG, "Loaded source audio: $sampleCount samples (${durationMs}ms), SHA-256: $sourceSha")

        val normRef = TextNormalizer.normalize(PRESCRIBED_HINDI_REFERENCE)
        val whisperEngine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "hi")

        val gainConfigs = listOf(
            Pair("gain_1x.wav", 1.0f),
            Pair("gain_2x.wav", 2.0f),
            Pair("gain_4x.wav", 4.0f),
            Pair("gain_8x.wav", 8.0f)
        )

        val results = mutableListOf<GainVariantResult>()

        try {
            for ((fileName, mult) in gainConfigs) {
                Log.i(TAG, "--- Processing Gain Variant: $fileName (${mult}x) ---")

                val scaledSamples = ShortArray(sampleCount)
                var clipCount = 0L
                var zeroCount = 0L
                var maxAbs = 0
                var sumSquare = 0.0

                for (i in 0 until sampleCount) {
                    val raw = rawSamples[i]
                    val scaled = kotlin.math.round(raw * mult).toInt()

                    val clamped = when {
                        scaled > Short.MAX_VALUE -> {
                            clipCount++
                            Short.MAX_VALUE
                        }
                        scaled < Short.MIN_VALUE -> {
                            clipCount++
                            Short.MIN_VALUE
                        }
                        else -> scaled.toShort()
                    }

                    scaledSamples[i] = clamped
                    val absVal = kotlin.math.abs(clamped.toInt())
                    if (absVal > maxAbs) maxAbs = absVal
                    if (clamped == 0.toShort()) zeroCount++
                    sumSquare += clamped.toDouble() * clamped.toDouble()
                }

                val meanSquare = if (sampleCount > 0) sumSquare / sampleCount else 0.0
                val rms = kotlin.math.sqrt(meanSquare)
                val rmsDbfs = if (rms > 0.0) 20.0 * kotlin.math.log10(rms / 32768.0) else -96.0
                val peakDbfs = if (maxAbs > 0) 20.0 * kotlin.math.log10(maxAbs / 32768.0) else -96.0
                val clipPct = if (sampleCount > 0) (clipCount.toDouble() / sampleCount.toDouble()) * 100.0 else 0.0
                val zeroPct = if (sampleCount > 0) (zeroCount.toDouble() / sampleCount.toDouble()) * 100.0 else 0.0

                // Write variant WAV to disk
                val variantFile = File(context.filesDir, fileName)
                writePcm16Wav(variantFile, scaledSamples, 16000)
                val variantSha = calculateSha256(variantFile)
                val fileSize = variantFile.length()

                // Run direct Whisper ASR inference
                val asrResult = whisperEngine.transcribe(
                    audio = scaledSamples,
                    sampleRate = 16000,
                    language = "hi"
                )

                val normHyp = TextNormalizer.normalize(asrResult.text)
                val werResult = WerCalculator.calculate(normRef, normHyp)
                val rtf = if (durationMs > 0) asrResult.inferenceTimeMs.toDouble() / durationMs.toDouble() else 0.0

                val variantResult = GainVariantResult(
                    gainLabel = "${mult}x",
                    multiplier = mult,
                    wavFileName = fileName,
                    wavFileSize = fileSize,
                    sha256 = variantSha,
                    sampleCount = sampleCount,
                    durationMs = durationMs,
                    rms = rms,
                    rmsDbfs = rmsDbfs,
                    peakAbsolute = maxAbs,
                    peakDbfs = peakDbfs,
                    clippingCount = clipCount,
                    clippingPercentage = clipPct,
                    zeroCount = zeroCount,
                    zeroPercentage = zeroPct,
                    rawHypothesis = asrResult.text,
                    normalizedHypothesis = normHyp,
                    detectedLanguage = asrResult.language,
                    inferenceTimeMs = asrResult.inferenceTimeMs,
                    rtf = rtf,
                    wer = werResult.wordStats.errorRate,
                    cer = werResult.charStats.errorRate,
                    isExactMatch = werResult.isExactMatch
                )

                results.add(variantResult)

                Log.i(
                    TAG,
                    "[VARIANT ${mult}x] File=$fileName, RMS=${"%.2f".format(rmsDbfs)}dBFS, Peak=$maxAbs (${"%.2f".format(peakDbfs)}dBFS), Clips=$clipCount (${"%.3f".format(clipPct)}%), WER=${"%.1f".format(variantResult.wer * 100)}%, CER=${"%.1f".format(variantResult.cer * 100)}%, Time=${asrResult.inferenceTimeMs}ms (RTF ${"%.3f".format(rtf)}), Raw=\"${asrResult.text}\""
                )
            }
        } finally {
            whisperEngine.close()
        }

        // Classification
        val wer1x = results.find { it.multiplier == 1.0f }?.wer ?: 1.0
        val minWer = results.minOfOrNull { it.wer } ?: 1.0
        val clip8x = results.find { it.multiplier == 8.0f }?.clippingCount ?: 0L

        val (classification, details) = when {
            minWer <= 0.3 && minWer < wer1x -> Pair(
                "A. STRONG GAIN SENSITIVITY",
                "Recognition materially improved from WER ${"%.1f".format(wer1x * 100)}% (1x) to ${"%.1f".format(minWer * 100)}% under gain scaling."
            )
            minWer < wer1x && clip8x > 0 && results.last().wer > minWer -> Pair(
                "C. GAIN HELPS UNTIL CLIPPING",
                "Recognition improved at intermediate gain levels but degraded when clipping occurred at higher gain."
            )
            else -> Pair(
                "B. NO GAIN SENSITIVITY",
                "Recognition remained essentially unchanged across all gain levels (1x WER=${"%.1f".format(wer1x * 100)}%, best WER=${"%.1f".format(minWer * 100)}%)."
            )
        }

        Log.i(TAG, "==================================================")
        Log.i(TAG, "EXPERIMENT CLASSIFICATION: $classification")
        Log.i(TAG, "Details: $details")
        Log.i(TAG, "==================================================")

        GainAbReport(
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            sourceWavName = SOURCE_WAV_NAME,
            sourceSha256 = sourceSha,
            variants = results,
            classification = classification,
            classificationDetails = details
        )
    }

    private fun writePcm16Wav(file: File, samples: ShortArray, sampleRate: Int = 16000) {
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

    private fun readPcm16Wav(file: File): ShortArray {
        FileInputStream(file).use { fis ->
            val bytes = fis.readBytes()
            require(bytes.size >= 44) { "WAV file too small (${bytes.size} bytes)" }
            val dataLen = bytes.size - 44
            val sampleCount = dataLen / 2
            val buffer = ByteBuffer.wrap(bytes, 44, dataLen).order(ByteOrder.LITTLE_ENDIAN)
            val samples = ShortArray(sampleCount)
            for (i in 0 until sampleCount) {
                samples[i] = buffer.short
            }
            return samples
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
