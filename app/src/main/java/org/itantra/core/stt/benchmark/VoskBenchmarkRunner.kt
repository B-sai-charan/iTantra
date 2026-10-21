package org.itantra.core.stt.benchmark

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.itantra.core.stt.AsrSmokeTestRunner
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File

/**
 * Diagnostic-only benchmark runner for isolated physical Vosk Hindi evaluation (sttbench25 & sttbench28).
 * Completely isolated from production SpeechPipeline and SherpaWhisperAsrEngine.
 */
object VoskBenchmarkRunner {

    private const val TAG = "VoskBenchmark"

    data class VoskBenchmarkResult(
        val caseId: String = "HI-001",
        val modelPath: String,
        val modelLoadTimeMs: Long,
        val audioDurationMs: Long,
        val recognitionTimeMs: Long,
        val rtf: Double,
        val rawJson: String,
        val rawTranscript: String,
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
        val errorMessage: String? = null
    )

    data class VoskBenchmarkSuiteReport(
        val suiteName: String,
        val totalCases: Int,
        val exactMatchCount: Int,
        val exactMatchRate: Double,
        val totalSubstitutions: Int,
        val totalDeletions: Int,
        val totalInsertions: Int,
        val totalHits: Int,
        val totalReferenceWords: Int,
        val totalCharSubstitutions: Int,
        val totalCharDeletions: Int,
        val totalCharInsertions: Int,
        val totalCharHits: Int,
        val totalReferenceCharacters: Int,
        val corpusWer: Double,
        val corpusCer: Double,
        val totalAudioDurationMs: Long,
        val totalInferenceTimeMs: Long,
        val meanRtf: Double,
        val perCaseResults: List<VoskBenchmarkResult>
    )

    /**
     * Executes ONE physical isolated benchmark run with the Vosk Hindi model against HI-001.
     */
    suspend fun runVoskHindiBenchmark(context: Context): VoskBenchmarkResult? = withContext(Dispatchers.Default) {
        val suite = listOf(AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_HI_001)
        val report = runVoskHindiSuite(context, suite, "Vosk-Hindi-Single-Case")
        report?.perCaseResults?.firstOrNull()
    }

    /**
     * Executes an entire Indic-language benchmark suite sequentially with single Vosk Model lifecycle.
     */
    suspend fun runVoskHindiSuite(
        context: Context,
        suite: List<AsrBenchmarkCase> = AsrAccuracyBenchmarkRunner.HINDI_BENCHMARK_SUITE,
        suiteName: String = "Vosk-Hindi-Benchmark-Suite"
    ): VoskBenchmarkSuiteReport? = withContext(Dispatchers.Default) {
        if (suite.isEmpty()) {
            Log.w(TAG, "[VOSK_BENCHMARK_SUITE] Empty suite provided.")
            return@withContext null
        }

        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [VOSK_BENCHMARK_SUITE] STARTING VOSK INDIC SUITE: $suiteName (${suite.size} cases) ===")
        Log.i(TAG, "=== EXPERIMENTAL VOSK HINDI 5-CASE BENCHMARK ===")
        Log.i(TAG, "=========================================================")

        // 1. Resolve model directory on device
        val candidateLocations = listOf(
            File(context.getExternalFilesDir(null), "models/vosk-model-small-hi-0.22"),
            File("/sdcard/Android/data/org.itantra.app/files/models/vosk-model-small-hi-0.22"),
            File("/data/local/tmp/vosk-model-small-hi-0.22"),
            File(context.filesDir, "models/vosk-model-small-hi-0.22")
        )

        val modelDir = candidateLocations.firstOrNull { it.exists() && it.isDirectory }
        if (modelDir == null) {
            val err = "Vosk model directory not found in candidate locations: ${candidateLocations.map { it.absolutePath }}"
            Log.e(TAG, "[VOSK_BENCHMARK_SUITE] $err")
            return@withContext null
        }

        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] Found model directory at: ${modelDir.absolutePath}")

        var model: Model? = null
        var modelLoadTimeMs = 0L
        val caseResults = mutableListOf<VoskBenchmarkResult>()

        try {
            // 2. Load model
            Log.i(TAG, "[VOSK_BENCHMARK_SUITE] Loading Vosk Model from ${modelDir.absolutePath}...")
            val loadStart = SystemClock.elapsedRealtime()
            model = Model(modelDir.absolutePath)
            val loadEnd = SystemClock.elapsedRealtime()
            modelLoadTimeMs = loadEnd - loadStart
            Log.i(TAG, "[VOSK_BENCHMARK_SUITE] Vosk Model loaded in ${modelLoadTimeMs}ms")

            for ((index, case) in suite.withIndex()) {
                Log.i(TAG, "[VOSK_BENCHMARK_SUITE] Running Vosk case ${index + 1}/${suite.size}: ${case.id}...")
                val audio = AsrSmokeTestRunner.loadWavFromAsset(context, case.assetPath)
                if (audio == null) {
                    val err = "Failed to load frozen WAV fixture from asset: ${case.assetPath}"
                    Log.e(TAG, "[VOSK_BENCHMARK_SUITE] $err")
                    continue
                }

                val audioDurationMs = (audio.size.toLong() * 1000L) / case.expectedSampleRate

                var recognizer: Recognizer? = null
                var recognitionTimeMs = 0L
                var rawJson = ""
                var rawTranscript = ""

                try {
                    recognizer = Recognizer(model, case.expectedSampleRate.toFloat())
                    recognizer.setWords(true)

                    val recStart = SystemClock.elapsedRealtime()
                    recognizer.acceptWaveForm(audio, audio.size)
                    rawJson = recognizer.finalResult
                    val recEnd = SystemClock.elapsedRealtime()
                    recognitionTimeMs = recEnd - recStart

                    val jsonObj = JSONObject(rawJson)
                    rawTranscript = jsonObj.optString("text", "").trim()
                } catch (e: Throwable) {
                    Log.e(TAG, "[VOSK_BENCHMARK_SUITE] Exception during case ${case.id}: ${e.message}", e)
                } finally {
                    try {
                        recognizer?.close()
                    } catch (e: Throwable) {
                        Log.w(TAG, "[VOSK_BENCHMARK_SUITE] Error closing recognizer: ${e.message}")
                    }
                }

                val werResult = IndicWerCalculator.calculate(
                    reference = case.referenceTranscript,
                    hypothesis = rawTranscript,
                    normalize = true
                )

                val rtf = if (audioDurationMs > 0) recognitionTimeMs.toDouble() / audioDurationMs.toDouble() else 0.0

                val caseResult = VoskBenchmarkResult(
                    caseId = case.id,
                    modelPath = modelDir.absolutePath,
                    modelLoadTimeMs = modelLoadTimeMs,
                    audioDurationMs = audioDurationMs,
                    recognitionTimeMs = recognitionTimeMs,
                    rtf = rtf,
                    rawJson = rawJson,
                    rawTranscript = rawTranscript,
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
                    cer = werResult.charStats.errorRate
                )

                logVoskResult(caseResult, case.referenceTranscript)
                caseResults.add(caseResult)
            }

            val suiteReport = aggregateVoskResults(suiteName, caseResults)
            logVoskSuiteReport(suiteReport)
            suiteReport
        } catch (e: Throwable) {
            Log.e(TAG, "[VOSK_BENCHMARK_SUITE] Exception during Vosk suite: ${e.message}", e)
            null
        } finally {
            try {
                model?.close()
            } catch (e: Throwable) {
                Log.w(TAG, "[VOSK_BENCHMARK_SUITE] Error closing model: ${e.message}")
            }
        }
    }

    private fun aggregateVoskResults(
        suiteName: String,
        results: List<VoskBenchmarkResult>
    ): VoskBenchmarkSuiteReport {
        val totalCases = results.size
        val exactMatchCount = results.count { it.exactMatch }
        val exactMatchRate = if (totalCases > 0) exactMatchCount.toDouble() / totalCases.toDouble() else 0.0

        val totalS = results.sumOf { it.substitutions }
        val totalD = results.sumOf { it.deletions }
        val totalI = results.sumOf { it.insertions }
        val totalH = results.sumOf { it.hits }
        val totalRefWords = results.sumOf { it.referenceWordCount }

        val totalCharS = results.sumOf { it.charSubstitutions }
        val totalCharD = results.sumOf { it.charDeletions }
        val totalCharI = results.sumOf { it.charInsertions }
        val totalCharH = results.sumOf { it.charHits }
        val totalRefChars = results.sumOf { it.referenceCharCount }

        val totalWordErrors = totalS + totalD + totalI
        val totalCharErrors = totalCharS + totalCharD + totalCharI

        val corpusWer = if (totalRefWords > 0) totalWordErrors.toDouble() / totalRefWords.toDouble() else 0.0
        val corpusCer = if (totalRefChars > 0) totalCharErrors.toDouble() / totalRefChars.toDouble() else 0.0

        val totalAudioDurationMs = results.sumOf { it.audioDurationMs }
        val totalInferenceTimeMs = results.sumOf { it.recognitionTimeMs }
        val meanRtf = if (totalCases > 0) results.sumOf { it.rtf } / totalCases.toDouble() else 0.0

        return VoskBenchmarkSuiteReport(
            suiteName = suiteName,
            totalCases = totalCases,
            exactMatchCount = exactMatchCount,
            exactMatchRate = exactMatchRate,
            totalSubstitutions = totalS,
            totalDeletions = totalD,
            totalInsertions = totalI,
            totalHits = totalH,
            totalReferenceWords = totalRefWords,
            totalCharSubstitutions = totalCharS,
            totalCharDeletions = totalCharD,
            totalCharInsertions = totalCharI,
            totalCharHits = totalCharH,
            totalReferenceCharacters = totalRefChars,
            corpusWer = corpusWer,
            corpusCer = corpusCer,
            totalAudioDurationMs = totalAudioDurationMs,
            totalInferenceTimeMs = totalInferenceTimeMs,
            meanRtf = meanRtf,
            perCaseResults = results
        )
    }

    private fun logVoskResult(result: VoskBenchmarkResult, rawRef: String) {
        Log.i(TAG, "---------------------------------------------------------")
        Log.i(TAG, "[VOSK_BENCHMARK] CASE_ID: ${result.caseId}")
        Log.i(TAG, "[VOSK_BENCHMARK] MODEL_PATH: ${result.modelPath}")
        Log.i(TAG, "[VOSK_BENCHMARK] MODEL_LOAD_TIME_MS: ${result.modelLoadTimeMs}")
        Log.i(TAG, "[VOSK_BENCHMARK] RAW_REF: \"$rawRef\"")
        Log.i(TAG, "[VOSK_BENCHMARK] RAW_HYP: \"${result.rawTranscript}\"")
        Log.i(TAG, "[VOSK_BENCHMARK] NORM_REF: \"${result.normalizedReference}\"")
        Log.i(TAG, "[VOSK_BENCHMARK] NORM_HYP: \"${result.normalizedHypothesis}\"")
        Log.i(TAG, "[VOSK_BENCHMARK] EXACT_MATCH: ${result.exactMatch}")
        Log.i(TAG, "[VOSK_BENCHMARK] ALIGNMENT: S=${result.substitutions}, D=${result.deletions}, I=${result.insertions}, H=${result.hits}, N=${result.referenceWordCount}")
        Log.i(TAG, "[VOSK_BENCHMARK] CHAR_ALIGNMENT: S=${result.charSubstitutions}, D=${result.charDeletions}, I=${result.charInsertions}, H=${result.charHits}, N=${result.referenceCharCount}")
        Log.i(TAG, "[VOSK_BENCHMARK] WER: ${"%.4f".format(result.wer)} (${"%.2f".format(result.wer * 100.0)}%)")
        Log.i(TAG, "[VOSK_BENCHMARK] CER: ${"%.4f".format(result.cer)} (${"%.2f".format(result.cer * 100.0)}%)")
        Log.i(TAG, "[VOSK_BENCHMARK] AUDIO_DURATION_MS: ${result.audioDurationMs}")
        Log.i(TAG, "[VOSK_BENCHMARK] INFERENCE_TIME_MS: ${result.recognitionTimeMs}")
        Log.i(TAG, "[VOSK_BENCHMARK] RTF: ${"%.4f".format(result.rtf)}")
        Log.i(TAG, "---------------------------------------------------------")
    }

    private fun logVoskSuiteReport(report: VoskBenchmarkSuiteReport) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [VOSK_BENCHMARK_SUITE] SUMMARY: ${report.suiteName} ===")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] TOTAL_CASES: ${report.totalCases}")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] EXACT_MATCH_COUNT: ${report.exactMatchCount}")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] EXACT_MATCH_RATE: ${"%.4f".format(report.exactMatchRate)} (${"%.2f".format(report.exactMatchRate * 100.0)}%)")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] TOTAL_S: ${report.totalSubstitutions}")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] TOTAL_D: ${report.totalDeletions}")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] TOTAL_I: ${report.totalInsertions}")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] TOTAL_H: ${report.totalHits}")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] TOTAL_REFERENCE_WORDS: ${report.totalReferenceWords}")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] TOTAL_REFERENCE_CHARS: ${report.totalReferenceCharacters}")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] CORPUS_WER: ${"%.4f".format(report.corpusWer)} (${"%.2f".format(report.corpusWer * 100.0)}%)")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] CORPUS_CER: ${"%.4f".format(report.corpusCer)} (${"%.2f".format(report.corpusCer * 100.0)}%)")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] TOTAL_AUDIO_DURATION_MS: ${report.totalAudioDurationMs}")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] TOTAL_INFERENCE_TIME_MS: ${report.totalInferenceTimeMs}")
        Log.i(TAG, "[VOSK_BENCHMARK_SUITE] MEAN_RTF: ${"%.4f".format(report.meanRtf)}")
        Log.i(TAG, "=========================================================")
    }
}
