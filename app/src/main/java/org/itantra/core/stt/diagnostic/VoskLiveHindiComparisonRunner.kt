package org.itantra.core.stt.diagnostic

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.itantra.core.stt.benchmark.IndicWerCalculator
import org.itantra.core.stt.benchmark.WerResult
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Diagnostic-only runner for TASK: M1-HINDI-ASR-LIVE-COMPARISON-01.
 *
 * Evaluates whether the existing offline Vosk Hindi ASR model can recognize
 * the exact captured live Hindi microphone WAV (live_hindi_forensic_01.wav)
 * on which Whisper-Tiny INT8 failed.
 */
object VoskLiveHindiComparisonRunner {

    private const val TAG = "VOSK_LIVE_COMPARISON"
    const val PRESCRIBED_HINDI_REFERENCE = "नमस्ते, यह iTantra का हिंदी परीक्षण है।"
    const val SOURCE_WAV_NAME = "live_hindi_forensic_01.wav"

    data class VoskLiveComparisonResult(
        val deviceModel: String,
        val wavFileName: String,
        val wavFileSize: Long,
        val sha256: String,
        val sampleCount: Int,
        val audioDurationMs: Long,
        val modelPath: String,
        val modelLoadTimeMs: Long,
        val inferenceTimeMs: Long,
        val rtf: Double,
        val rawJson: String,
        val rawHypothesis: String,
        val normalizedReference: String,
        val normalizedHypothesis: String,
        val exactMatch: Boolean,
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int,
        val hits: Int,
        val referenceWordCount: Int,
        val wer: Double,
        val charSubstitutions: Int,
        val charDeletions: Int,
        val charInsertions: Int,
        val charHits: Int,
        val referenceCharCount: Int,
        val cer: Double,
        val memoryUsedMb: Double,
        val runtimeSuccess: Boolean,
        val classification: String,
        val classificationDetails: String,
        val errorMessage: String? = null
    )

    suspend fun runComparison(
        context: Context
    ): VoskLiveComparisonResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "STARTING M1-HINDI-ASR-LIVE-COMPARISON-01 EXPERIMENT")
        Log.i(TAG, "Source WAV: $SOURCE_WAV_NAME")
        Log.i(TAG, "Reference: \"$PRESCRIBED_HINDI_REFERENCE\"")
        Log.i(TAG, "=========================================================")

        // 1. Locate and read source WAV
        val candidateWavFiles = listOf(
            File(context.filesDir, SOURCE_WAV_NAME),
            File(context.getExternalFilesDir(null), SOURCE_WAV_NAME),
            File("/sdcard/Android/data/org.itantra.app/files/$SOURCE_WAV_NAME")
        )

        val sourceFile = candidateWavFiles.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Source forensic WAV not found in: ${candidateWavFiles.map { it.absolutePath }}")

        val rawSamples = readPcm16Wav(sourceFile)
        val sampleCount = rawSamples.size
        val audioDurationMs = (sampleCount * 1000L) / 16000L
        val sha256 = calculateSha256(sourceFile)
        val wavFileSize = sourceFile.length()

        Log.i(TAG, "Loaded source audio: $sampleCount samples (${audioDurationMs}ms), FileSize: $wavFileSize bytes, SHA-256: $sha256")

        // 2. Locate Vosk model directory
        val candidateModelLocations = listOf(
            File(context.getExternalFilesDir(null), "models/vosk-model-small-hi-0.22"),
            File("/sdcard/Android/data/org.itantra.app/files/models/vosk-model-small-hi-0.22"),
            File("/data/local/tmp/vosk-model-small-hi-0.22"),
            File(context.filesDir, "models/vosk-model-small-hi-0.22")
        )

        val modelDir = candidateModelLocations.firstOrNull { it.exists() && it.isDirectory }
            ?: throw IllegalStateException("Vosk model directory not found in: ${candidateModelLocations.map { it.absolutePath }}")

        Log.i(TAG, "Found Vosk model directory: ${modelDir.absolutePath}")

        var model: Model? = null
        var recognizer: Recognizer? = null
        var modelLoadTimeMs = 0L
        var inferenceTimeMs = 0L
        var rawJson = ""
        var rawTranscript = ""
        var runtimeSuccess = false
        var errorMsg: String? = null

        try {
            // 3. Model initialization
            Log.i(TAG, "Loading Vosk Model from ${modelDir.absolutePath}...")
            val loadStart = SystemClock.elapsedRealtime()
            model = Model(modelDir.absolutePath)
            val loadEnd = SystemClock.elapsedRealtime()
            modelLoadTimeMs = loadEnd - loadStart
            Log.i(TAG, "Vosk Model loaded in ${modelLoadTimeMs}ms")

            // 4. Recognizer initialization
            recognizer = Recognizer(model, 16000.0f)
            recognizer.setWords(true)

            // 5. Direct replay inference
            Log.i(TAG, "Executing Vosk direct replay inference on $sampleCount PCM16 samples...")
            val infStart = SystemClock.elapsedRealtime()
            recognizer.acceptWaveForm(rawSamples, rawSamples.size)
            rawJson = recognizer.finalResult
            val infEnd = SystemClock.elapsedRealtime()
            inferenceTimeMs = infEnd - infStart

            val jsonObj = JSONObject(rawJson)
            rawTranscript = jsonObj.optString("text", "").trim()
            runtimeSuccess = true
            Log.i(TAG, "Vosk inference complete in ${inferenceTimeMs}ms. Raw JSON: $rawJson")
            Log.i(TAG, "Vosk raw transcript: \"$rawTranscript\"")
        } catch (e: Throwable) {
            Log.e(TAG, "Exception during Vosk direct replay: ${e.message}", e)
            errorMsg = e.message ?: e.toString()
        } finally {
            try {
                recognizer?.close()
            } catch (e: Throwable) {
                Log.w(TAG, "Error closing recognizer: ${e.message}")
            }
            try {
                model?.close()
            } catch (e: Throwable) {
                Log.w(TAG, "Error closing model: ${e.message}")
            }
        }

        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()).toDouble() / (1024.0 * 1024.0)

        // 6. Calculate WER & CER
        val werResult: WerResult = if (runtimeSuccess) {
            IndicWerCalculator.calculate(
                reference = PRESCRIBED_HINDI_REFERENCE,
                hypothesis = rawTranscript,
                normalize = true
            )
        } else {
            IndicWerCalculator.calculate(
                reference = PRESCRIBED_HINDI_REFERENCE,
                hypothesis = "",
                normalize = true
            )
        }

        val rtf = if (audioDurationMs > 0) inferenceTimeMs.toDouble() / audioDurationMs.toDouble() else 0.0

        // 7. Classification
        // Whisper reference on this exact WAV: WER = 100.0%, CER = 95.5%, Hyp = "ndo ndo ndo ndo"
        val (classification, classDetails) = when {
            !runtimeSuccess -> Pair(
                "C. Vosk cannot run on the captured WAV.",
                "Vosk runtime failed with error: $errorMsg"
            )
            werResult.wordStats.errorRate < 0.60 || (werResult.wordStats.hits > 0 && werResult.charStats.errorRate < 0.50) -> Pair(
                "A. Vosk materially improves recognition on the SAME live WAV.",
                "Evidence supports Vosk as a candidate for further live-Hindi validation. Vosk achieved WER=${"%.2f".format(werResult.wordStats.errorRate * 100)}% / CER=${"%.2f".format(werResult.charStats.errorRate * 100)}% with ${werResult.wordStats.hits}/${werResult.wordStats.referenceCount} word hits compared to Whisper's 100.0% WER / 95.5% CER hallucination."
            )
            else -> Pair(
                "B. Vosk does not materially improve recognition.",
                "Evidence does not support Vosk as the immediate remedy. Vosk achieved WER=${"%.2f".format(werResult.wordStats.errorRate * 100)}% / CER=${"%.2f".format(werResult.charStats.errorRate * 100)}%."
            )
        }

        val result = VoskLiveComparisonResult(
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            wavFileName = SOURCE_WAV_NAME,
            wavFileSize = wavFileSize,
            sha256 = sha256,
            sampleCount = sampleCount,
            audioDurationMs = audioDurationMs,
            modelPath = modelDir.absolutePath,
            modelLoadTimeMs = modelLoadTimeMs,
            inferenceTimeMs = inferenceTimeMs,
            rtf = rtf,
            rawJson = rawJson,
            rawHypothesis = rawTranscript,
            normalizedReference = werResult.normalizedReference,
            normalizedHypothesis = werResult.normalizedHypothesis,
            exactMatch = werResult.isExactMatch,
            substitutions = werResult.wordStats.substitutions,
            deletions = werResult.wordStats.deletions,
            insertions = werResult.wordStats.insertions,
            hits = werResult.wordStats.hits,
            referenceWordCount = werResult.wordStats.referenceCount,
            wer = werResult.wordStats.errorRate,
            charSubstitutions = werResult.charStats.substitutions,
            charDeletions = werResult.charStats.deletions,
            charInsertions = werResult.charStats.insertions,
            charHits = werResult.charStats.hits,
            referenceCharCount = werResult.charStats.referenceCount,
            cer = werResult.charStats.errorRate,
            memoryUsedMb = usedMemMb,
            runtimeSuccess = runtimeSuccess,
            classification = classification,
            classificationDetails = classDetails,
            errorMessage = errorMsg
        )

        logResult(result)
        result
    }

    private fun logResult(r: VoskLiveComparisonResult) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [VOSK_LIVE_COMPARISON] RESULT SUMMARY ===")
        Log.i(TAG, "DEVICE: ${r.deviceModel}")
        Log.i(TAG, "WAV: ${r.wavFileName} (${r.sampleCount} samples, ${r.audioDurationMs}ms, SHA256: ${r.sha256})")
        Log.i(TAG, "MODEL_LOAD_TIME_MS: ${r.modelLoadTimeMs}")
        Log.i(TAG, "INFERENCE_TIME_MS: ${r.inferenceTimeMs} (RTF: ${"%.4f".format(r.rtf)})")
        Log.i(TAG, "RAW_HYPOTHESIS: \"${r.rawHypothesis}\"")
        Log.i(TAG, "NORM_REF: \"${r.normalizedReference}\"")
        Log.i(TAG, "NORM_HYP: \"${r.normalizedHypothesis}\"")
        Log.i(TAG, "EXACT_MATCH: ${r.exactMatch}")
        Log.i(TAG, "WORD_ALIGNMENT: S=${r.substitutions}, D=${r.deletions}, I=${r.insertions}, H=${r.hits}, N=${r.referenceWordCount} -> WER=${"%.2f".format(r.wer * 100)}%")
        Log.i(TAG, "CHAR_ALIGNMENT: S=${r.charSubstitutions}, D=${r.charDeletions}, I=${r.charInsertions}, H=${r.charHits}, N=${r.referenceCharCount} -> CER=${"%.2f".format(r.cer * 100)}%")
        Log.i(TAG, "CLASSIFICATION: ${r.classification}")
        Log.i(TAG, "DETAILS: ${r.classificationDetails}")
        Log.i(TAG, "=========================================================")
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
