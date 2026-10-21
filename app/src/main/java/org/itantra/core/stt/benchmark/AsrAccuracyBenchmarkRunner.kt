package org.itantra.core.stt.benchmark

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.itantra.core.stt.AsrEngine
import org.itantra.core.stt.AsrSmokeTestRunner
import org.itantra.core.stt.SherpaWhisperAsrEngine

/**
 * Runner for deterministic STT accuracy benchmark suites on Android hardware.
 */
object AsrAccuracyBenchmarkRunner {

    private const val TAG = "AsrBenchmark"

    /**
     * Authoritative English LibriSpeech benchmark case 001.
     */
    val BENCHMARK_CASE_EN_001 = AsrBenchmarkCase(
        id = "EN-001",
        language = "en",
        assetPath = "speech_test.wav",
        referenceTranscript = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels.",
        expectedSampleRate = 16000,
        expectedChannels = 1
    )

    /**
     * Authoritative English LibriSpeech benchmark case 002.
     */
    val BENCHMARK_CASE_EN_002 = AsrBenchmarkCase(
        id = "EN-002",
        language = "en",
        assetPath = "speech_test_002.wav",
        referenceTranscript = "GOD AS A DIRECT CONSEQUENCE OF THE SIN WHICH MAN THUS PUNISHED HAD GIVEN HER A LOVELY CHILD WHOSE PLACE WAS ON THAT SAME DISHONOURED BOSOM TO CONNECT HER PARENT FOR EVER WITH THE RACE AND DESCENT OF MORTALS AND TO BE FINALLY A BLESSED SOUL IN HEAVEN",
        expectedSampleRate = 16000,
        expectedChannels = 1
    )

    /**
     * Authoritative English LibriSpeech benchmark case 003.
     */
    val BENCHMARK_CASE_EN_003 = AsrBenchmarkCase(
        id = "EN-003",
        language = "en",
        assetPath = "speech_test_003.wav",
        referenceTranscript = "YET THESE THOUGHTS AFFECTED HESTER PRYNNE LESS WITH HOPE THAN APPREHENSION",
        expectedSampleRate = 16000,
        expectedChannels = 1
    )

    /**
     * Authoritative English TED-LIUM 3 benchmark case 004.
     */
    val BENCHMARK_CASE_EN_004 = AsrBenchmarkCase(
        id = "EN-004",
        language = "en",
        assetPath = "speech_test_004.wav",
        referenceTranscript = "well the last year this property had six hundred thousand birds on it",
        expectedSampleRate = 16000,
        expectedChannels = 1
    )

    /**
     * Authoritative English TED-LIUM 3 benchmark case 005.
     */
    val BENCHMARK_CASE_EN_005 = AsrBenchmarkCase(
        id = "EN-005",
        language = "en",
        assetPath = "speech_test_005.wav",
        referenceTranscript = "goes very different ways depending on how you think and whether you think of the remembering self or you think of the experiencing self this is going to influence policy i think in years to come in the united states efforts are being made",
        expectedSampleRate = 16000,
        expectedChannels = 1
    )

    /**
     * Authoritative English TED-LIUM 3 benchmark case 006.
     */
    val BENCHMARK_CASE_EN_006 = AsrBenchmarkCase(
        id = "EN-006",
        language = "en",
        assetPath = "speech_test_006.wav",
        referenceTranscript = "and he was talking about invisible demons and smoke and how someone was poisoning him in his sleep and i was afraid not for myself but i was afraid that i was going to lose him that he was going to sink into one of his states",
        expectedSampleRate = 16000,
        expectedChannels = 1
    )

    /**
     * Standard verified multi-case English benchmark suite.
     */
    val ENGLISH_BENCHMARK_SUITE: List<AsrBenchmarkCase> = listOf(
        BENCHMARK_CASE_EN_001,
        BENCHMARK_CASE_EN_002,
        BENCHMARK_CASE_EN_003,
        BENCHMARK_CASE_EN_004,
        BENCHMARK_CASE_EN_005,
        BENCHMARK_CASE_EN_006
    )

    /**
     * Experimental Hindi FLEURS benchmark case 001.
     * Source: Google FLEURS (google/fleurs), config hi_in, split test, row ID 1766.
     * PCM16 asset SHA256: 3614bd9301b65e0fc8a12c43cdab7a403b834c7948337352f97a22e26475df43
     */
    val BENCHMARK_CASE_HI_001 = AsrBenchmarkCase(
        id = "HI-001",
        language = "hi",
        assetPath = "speech_test_hi_001.wav",
        referenceTranscript = "कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,",
        expectedSampleRate = 16000,
        expectedChannels = 1
    )

    /**
     * Experimental Hindi FLEURS benchmark case 002.
     * Source: Google FLEURS (google/fleurs), config hi_in, split test, row ID 1926.
     * PCM16 asset SHA256: 059942f06d572e46ccec1eb005522c118fc2a78b0612b160e84288315d04883d
     */
    val BENCHMARK_CASE_HI_002 = AsrBenchmarkCase(
        id = "HI-002",
        language = "hi",
        assetPath = "speech_test_hi_002.wav",
        referenceTranscript = "मनोविज्ञान सहित विज्ञान के सभी मामलों पर अरस्तू के विचारों को स्वीकार किया जाता है।",
        expectedSampleRate = 16000,
        expectedChannels = 1
    )

    /**
     * Experimental Hindi FLEURS benchmark case 003.
     * Source: Google FLEURS (google/fleurs), config hi_in, split test, row ID 1747.
     * PCM16 asset SHA256: 1481c27cf6651bff893b7ba8977103e01a7fb82b9e79b8e7c0931a90b648f191
     */
    val BENCHMARK_CASE_HI_003 = AsrBenchmarkCase(
        id = "HI-003",
        language = "hi",
        assetPath = "speech_test_hi_003.wav",
        referenceTranscript = "लूना को साथी पहलवानों ने भी श्रद्धांजलि दी.",
        expectedSampleRate = 16000,
        expectedChannels = 1
    )

    /**
     * Experimental Hindi FLEURS benchmark case 004.
     * Source: Google FLEURS (google/fleurs), config hi_in, split test, row ID 1917.
     * PCM16 asset SHA256: 4c8bbc7b39e3b0a0d052c03ce22f06e84210d4cd1c799669faecbdb90b5d2883
     */
    val BENCHMARK_CASE_HI_004 = AsrBenchmarkCase(
        id = "HI-004",
        language = "hi",
        assetPath = "speech_test_hi_004.wav",
        referenceTranscript = "रोम में अलग-अलग जगहों पर कई बड़े टेलीविज़न स्क्रीन लगाए गए ताकि लोग समारोह देख सकें.",
        expectedSampleRate = 16000,
        expectedChannels = 1
    )

    /**
     * Experimental Hindi FLEURS benchmark case 005.
     * Source: Google FLEURS (google/fleurs), config hi_in, split test, row ID 1958.
     * PCM16 asset SHA256: f7cd61da6030853b5da777619c1ac00227debc17294039a882bbc27784aa5519
     */
    val BENCHMARK_CASE_HI_005 = AsrBenchmarkCase(
        id = "HI-005",
        language = "hi",
        assetPath = "speech_test_hi_005.wav",
        referenceTranscript = "यह शहर देश के बाकी शहरों से अलग है क्योंकि यह किसी अफ्रीकी शहर की बजाय अरब शहर लगता है.",
        expectedSampleRate = 16000,
        expectedChannels = 1
    )

    /**
     * Experimental Hindi benchmark suite — NOT part of the official English baseline.
     */
    val HINDI_BENCHMARK_SUITE: List<AsrBenchmarkCase> = listOf(
        BENCHMARK_CASE_HI_001,
        BENCHMARK_CASE_HI_002,
        BENCHMARK_CASE_HI_003,
        BENCHMARK_CASE_HI_004,
        BENCHMARK_CASE_HI_005
    )

    /**
     * Evaluates a single benchmark case given pre-loaded PCM16 audio and an initialized ASR engine.
     *
     * @param engine ASR engine instance.
     * @param audio PCM16 audio samples at 16 kHz mono.
     * @param case Benchmark case metadata and reference transcript.
     * @return [AsrBenchmarkCaseResult] with complete timing and accuracy metrics.
     */
    suspend fun evaluateCase(
        engine: AsrEngine,
        audio: ShortArray,
        case: AsrBenchmarkCase
    ): AsrBenchmarkCaseResult {
        val asrResult = engine.transcribe(
            audio = audio,
            sampleRate = case.expectedSampleRate,
            language = case.language
        )

        val werResult = WerCalculator.calculate(
            reference = case.referenceTranscript,
            hypothesis = asrResult.text,
            normalize = true
        )

        val rtf = if (asrResult.audioDurationMs > 0) {
            asrResult.inferenceTimeMs.toDouble() / asrResult.audioDurationMs.toDouble()
        } else {
            0.0
        }

        val caseResult = AsrBenchmarkCaseResult(
            caseId = case.id,
            language = case.language,
            detectedLanguage = asrResult.language,
            rawReference = case.referenceTranscript,
            rawHypothesis = asrResult.text,
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
            audioDurationMs = asrResult.audioDurationMs,
            inferenceTimeMs = asrResult.inferenceTimeMs,
            rtf = rtf
        )

        logBenchmarkResult(caseResult)
        return caseResult
    }

    /**
     * Evaluates a single Indic-language benchmark case using [IndicWerCalculator] for Unicode-safe scoring.
     *
     * This method is isolated from [evaluateCase] which uses [WerCalculator] for English.
     * The Indic scorer applies Unicode NFC normalization and preserves Devanagari combining marks.
     *
     * @param engine ASR engine instance.
     * @param audio PCM16 audio samples at 16 kHz mono.
     * @param case Benchmark case metadata and reference transcript.
     * @return [AsrBenchmarkCaseResult] with complete timing and accuracy metrics.
     */
    suspend fun evaluateCaseIndic(
        engine: AsrEngine,
        audio: ShortArray,
        case: AsrBenchmarkCase
    ): AsrBenchmarkCaseResult {
        val asrResult = engine.transcribe(
            audio = audio,
            sampleRate = case.expectedSampleRate,
            language = case.language
        )

        val werResult = IndicWerCalculator.calculate(
            reference = case.referenceTranscript,
            hypothesis = asrResult.text,
            normalize = true
        )

        val rtf = if (asrResult.audioDurationMs > 0) {
            asrResult.inferenceTimeMs.toDouble() / asrResult.audioDurationMs.toDouble()
        } else {
            0.0
        }

        val caseResult = AsrBenchmarkCaseResult(
            caseId = case.id,
            language = case.language,
            detectedLanguage = asrResult.language,
            rawReference = case.referenceTranscript,
            rawHypothesis = asrResult.text,
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
            audioDurationMs = asrResult.audioDurationMs,
            inferenceTimeMs = asrResult.inferenceTimeMs,
            rtf = rtf
        )

        logBenchmarkResult(caseResult)
        return caseResult
    }

    /**
     * Aggregates a list of case results into a structured suite report.
     *
     * Mathematical Invariants:
     * - Corpus WER is calculated as sum(S + D + I) / sum(N_words), NOT the arithmetic mean of case WERs.
     * - Corpus CER is calculated as sum(char errors) / sum(N_chars).
     * - Mean RTF is the arithmetic mean of per-case RTF values across the suite.
     */
    fun aggregateResults(
        suiteName: String,
        results: List<AsrBenchmarkCaseResult>
    ): AsrBenchmarkSuiteReport {
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
        val totalInferenceTimeMs = results.sumOf { it.inferenceTimeMs }
        val meanRtf = if (totalCases > 0) results.sumOf { it.rtf } / totalCases.toDouble() else 0.0

        return AsrBenchmarkSuiteReport(
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

    /**
     * Executes a single benchmark case end-to-end on Android device.
     */
    suspend fun runSingleCase(
        context: Context,
        engine: AsrEngine? = null,
        case: AsrBenchmarkCase = BENCHMARK_CASE_EN_001
    ): AsrBenchmarkCaseResult? = withContext(Dispatchers.Default) {
        Log.i(TAG, "=== [ASR_BENCHMARK] STARTING BENCHMARK CASE: ${case.id} (${case.language}) ===")

        val audio = AsrSmokeTestRunner.loadWavFromAsset(context, case.assetPath)
        if (audio == null) {
            Log.e(TAG, "[ASR_BENCHMARK] Failed to load WAV fixture from '${case.assetPath}'")
            return@withContext null
        }

        var localEngine = engine
        var shouldRelease = false
        if (localEngine == null) {
            Log.i(TAG, "[ASR_BENCHMARK] Initializing SherpaWhisperAsrEngine for benchmark...")
            localEngine = SherpaWhisperAsrEngine(context = context, defaultLanguage = case.language)
            shouldRelease = true
        }

        try {
            evaluateCase(engine = localEngine, audio = audio, case = case)
        } finally {
            if (shouldRelease) {
                localEngine.close()
            }
        }
    }

    /**
     * Executes an entire benchmark suite sequentially with single ASR engine lifecycle.
     */
    suspend fun runSuite(
        context: Context,
        suite: List<AsrBenchmarkCase> = ENGLISH_BENCHMARK_SUITE,
        suiteName: String = "English-Benchmark-Suite"
    ): AsrBenchmarkSuiteReport? = withContext(Dispatchers.Default) {
        if (suite.isEmpty()) {
            Log.w(TAG, "[ASR_BENCHMARK_SUITE] Attempted to run empty suite '$suiteName'")
            return@withContext aggregateResults(suiteName, emptyList())
        }

        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [ASR_BENCHMARK_SUITE] STARTING SUITE: $suiteName (${suite.size} cases) ===")
        Log.i(TAG, "=========================================================")

        var engine: SherpaWhisperAsrEngine? = null
        val caseResults = mutableListOf<AsrBenchmarkCaseResult>()

        try {
            Log.i(TAG, "[ASR_BENCHMARK_SUITE] Initializing SherpaWhisperAsrEngine for suite...")
            engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = suite.first().language)

            for ((index, case) in suite.withIndex()) {
                Log.i(TAG, "[ASR_BENCHMARK_SUITE] Running case ${index + 1}/${suite.size}: ${case.id}...")
                val audio = AsrSmokeTestRunner.loadWavFromAsset(context, case.assetPath)
                if (audio == null) {
                    Log.e(TAG, "[ASR_BENCHMARK_SUITE] Failed to load fixture '${case.assetPath}' for case ${case.id}. Skipping.")
                    continue
                }

                val result = evaluateCase(engine = engine, audio = audio, case = case)
                caseResults.add(result)
            }

            val suiteReport = aggregateResults(suiteName, caseResults)
            logSuiteReport(suiteReport)
            suiteReport
        } catch (e: Exception) {
            Log.e(TAG, "[ASR_BENCHMARK_SUITE] Exception during suite execution: ${e.message}", e)
            null
        } finally {
            engine?.close()
        }
    }

    /**
     * Executes an Indic-language benchmark suite using [IndicWerCalculator] for Unicode-safe scoring.
     * Isolated from [runSuite] to avoid contaminating the official English baseline.
     */
    suspend fun runIndicSuite(
        context: Context,
        suite: List<AsrBenchmarkCase> = HINDI_BENCHMARK_SUITE,
        suiteName: String = "Hindi-Benchmark-Suite"
    ): AsrBenchmarkSuiteReport? = withContext(Dispatchers.Default) {
        if (suite.isEmpty()) {
            Log.w(TAG, "[ASR_BENCHMARK_SUITE] Attempted to run empty Indic suite '$suiteName'")
            return@withContext aggregateResults(suiteName, emptyList())
        }

        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [ASR_BENCHMARK_SUITE] STARTING INDIC SUITE: $suiteName (${suite.size} cases) ===")
        Log.i(TAG, "=== EXPERIMENTAL HINDI RESULT — FIRST RUN ===")
        Log.i(TAG, "=========================================================")

        var engine: SherpaWhisperAsrEngine? = null
        val caseResults = mutableListOf<AsrBenchmarkCaseResult>()

        try {
            Log.i(TAG, "[ASR_BENCHMARK_SUITE] Initializing SherpaWhisperAsrEngine for Indic suite (language=${suite.first().language})...")
            engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = suite.first().language)

            for ((index, case) in suite.withIndex()) {
                Log.i(TAG, "[ASR_BENCHMARK_SUITE] Running Indic case ${index + 1}/${suite.size}: ${case.id}...")
                val audio = AsrSmokeTestRunner.loadWavFromAsset(context, case.assetPath)
                if (audio == null) {
                    Log.e(TAG, "[ASR_BENCHMARK_SUITE] Failed to load fixture '${case.assetPath}' for case ${case.id}. Skipping.")
                    continue
                }

                val result = evaluateCaseIndic(engine = engine, audio = audio, case = case)
                caseResults.add(result)
            }

            val suiteReport = aggregateResults(suiteName, caseResults)
            logSuiteReport(suiteReport)
            suiteReport
        } catch (e: Exception) {
            Log.e(TAG, "[ASR_BENCHMARK_SUITE] Exception during Indic suite execution: ${e.message}", e)
            null
        } finally {
            engine?.close()
        }
    }

    /**
     * Diagnostic runner for Hindi language token handling experiment (sttbench23).
     * Compares Case A (language="hi") vs Case B (language=null / default) on frozen Hindi asset.
     */
    suspend fun runHindiLanguageDiagnostic(
        context: Context
    ): Pair<AsrBenchmarkCaseResult, AsrBenchmarkCaseResult>? = withContext(Dispatchers.Default) {
        val case = BENCHMARK_CASE_HI_001
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [HINDI_DIAGNOSTIC] STARTING CONTROLLED A/B EXPERIMENT ===")
        Log.i(TAG, "=== DIAGNOSTIC — NOT AN OFFICIAL BENCHMARK ===")
        Log.i(TAG, "=========================================================")

        val audio = AsrSmokeTestRunner.loadWavFromAsset(context, case.assetPath)
        if (audio == null) {
            Log.e(TAG, "[HINDI_DIAGNOSTIC] Failed to load fixture '${case.assetPath}'")
            return@withContext null
        }

        Log.i(TAG, "[HINDI_DIAGNOSTIC] Audio loaded: ${audio.size} samples (${audio.size / 16000.0} seconds)")

        // CASE A: language = "hi"
        Log.i(TAG, "--- [HINDI_DIAGNOSTIC] RUNNING CASE A: language = \"hi\" ---")
        val engineA = SherpaWhisperAsrEngine(context = context, defaultLanguage = "hi")
        val resultA = try {
            val asrRes = engineA.transcribe(audio = audio, sampleRate = 16000, language = "hi")
            val werRes = IndicWerCalculator.calculate(reference = case.referenceTranscript, hypothesis = asrRes.text, normalize = true)
            val rtf = if (asrRes.audioDurationMs > 0) asrRes.inferenceTimeMs.toDouble() / asrRes.audioDurationMs.toDouble() else 0.0
            AsrBenchmarkCaseResult(
                caseId = "HI-001-CASE-A",
                language = "hi",
                detectedLanguage = asrRes.language,
                rawReference = case.referenceTranscript,
                rawHypothesis = asrRes.text,
                normalizedReference = werRes.normalizedReference,
                normalizedHypothesis = werRes.normalizedHypothesis,
                exactMatch = werRes.isExactMatch,
                substitutions = werRes.wordStats.substitutions,
                deletions = werRes.wordStats.deletions,
                insertions = werRes.wordStats.insertions,
                hits = werRes.wordStats.hits,
                referenceWordCount = werRes.wordStats.referenceCount,
                wer = werRes.wordStats.errorRate,
                charSubstitutions = werRes.charStats.substitutions,
                charDeletions = werRes.charStats.deletions,
                charInsertions = werRes.charStats.insertions,
                charHits = werRes.charStats.hits,
                referenceCharCount = werRes.charStats.referenceCount,
                cer = werRes.charStats.errorRate,
                audioDurationMs = asrRes.audioDurationMs,
                inferenceTimeMs = asrRes.inferenceTimeMs,
                rtf = rtf
            )
        } finally {
            engineA.close()
        }
        logBenchmarkResult(resultA)

        // CASE B: language = null
        Log.i(TAG, "--- [HINDI_DIAGNOSTIC] RUNNING CASE B: language = null ---")
        val engineB = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")
        val resultB = try {
            val asrRes = engineB.transcribe(audio = audio, sampleRate = 16000, language = null)
            val werRes = IndicWerCalculator.calculate(reference = case.referenceTranscript, hypothesis = asrRes.text, normalize = true)
            val rtf = if (asrRes.audioDurationMs > 0) asrRes.inferenceTimeMs.toDouble() / asrRes.audioDurationMs.toDouble() else 0.0
            AsrBenchmarkCaseResult(
                caseId = "HI-001-CASE-B",
                language = "null",
                detectedLanguage = asrRes.language,
                rawReference = case.referenceTranscript,
                rawHypothesis = asrRes.text,
                normalizedReference = werRes.normalizedReference,
                normalizedHypothesis = werRes.normalizedHypothesis,
                exactMatch = werRes.isExactMatch,
                substitutions = werRes.wordStats.substitutions,
                deletions = werRes.wordStats.deletions,
                insertions = werRes.wordStats.insertions,
                hits = werRes.wordStats.hits,
                referenceWordCount = werRes.wordStats.referenceCount,
                wer = werRes.wordStats.errorRate,
                charSubstitutions = werRes.charStats.substitutions,
                charDeletions = werRes.charStats.deletions,
                charInsertions = werRes.charStats.insertions,
                charHits = werRes.charStats.hits,
                referenceCharCount = werRes.charStats.referenceCount,
                cer = werRes.charStats.errorRate,
                audioDurationMs = asrRes.audioDurationMs,
                inferenceTimeMs = asrRes.inferenceTimeMs,
                rtf = rtf
            )
        } finally {
            engineB.close()
        }
        logBenchmarkResult(resultB)

        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [HINDI_DIAGNOSTIC] CONTROLLED A/B COMPLETE ===")
        Log.i(TAG, "Case A (hi):   WER = ${"%.2f".format(resultA.wer * 100)}%, CER = ${"%.2f".format(resultA.cer * 100)}%, Raw: \"${resultA.rawHypothesis}\"")
        Log.i(TAG, "Case B (null): WER = ${"%.2f".format(resultB.wer * 100)}%, CER = ${"%.2f".format(resultB.cer * 100)}%, Raw: \"${resultB.rawHypothesis}\"")
        Log.i(TAG, "=========================================================")

        Pair(resultA, resultB)
    }

    /**
     * Emits structured, unambiguous Logcat telemetry for a single case.
     */
    private fun logBenchmarkResult(result: AsrBenchmarkCaseResult) {
        Log.i(TAG, "---------------------------------------------------------")
        Log.i(TAG, "[ASR_BENCHMARK] CASE_ID: ${result.caseId}")
        Log.i(TAG, "[ASR_BENCHMARK] LANGUAGE: ${result.language} (Detected: ${result.detectedLanguage})")
        Log.i(TAG, "[ASR_BENCHMARK] RAW_REF: \"${result.rawReference}\"")
        Log.i(TAG, "[ASR_BENCHMARK] RAW_HYP: \"${result.rawHypothesis}\"")
        Log.i(TAG, "[ASR_BENCHMARK] NORM_REF: \"${result.normalizedReference}\"")
        Log.i(TAG, "[ASR_BENCHMARK] NORM_HYP: \"${result.normalizedHypothesis}\"")
        Log.i(TAG, "[ASR_BENCHMARK] EXACT_MATCH: ${result.exactMatch}")
        Log.i(TAG, "[ASR_BENCHMARK] ALIGNMENT: S=${result.substitutions}, D=${result.deletions}, I=${result.insertions}, H=${result.hits}, N=${result.referenceWordCount}")
        Log.i(TAG, "[ASR_BENCHMARK] CHAR_ALIGNMENT: S=${result.charSubstitutions}, D=${result.charDeletions}, I=${result.charInsertions}, H=${result.charHits}, N=${result.referenceCharCount}")
        Log.i(TAG, "[ASR_BENCHMARK] WER: ${"%.4f".format(result.wer)} (${"%.2f".format(result.wer * 100.0)}%)")
        Log.i(TAG, "[ASR_BENCHMARK] CER: ${"%.4f".format(result.cer)} (${"%.2f".format(result.cer * 100.0)}%)")
        Log.i(TAG, "[ASR_BENCHMARK] AUDIO_DURATION_MS: ${result.audioDurationMs}")
        Log.i(TAG, "[ASR_BENCHMARK] INFERENCE_TIME_MS: ${result.inferenceTimeMs}")
        Log.i(TAG, "[ASR_BENCHMARK] RTF: ${"%.4f".format(result.rtf)}")
        Log.i(TAG, "---------------------------------------------------------")
    }

    /**
     * Emits structured, unambiguous Logcat telemetry for an entire suite.
     */
    private fun logSuiteReport(report: AsrBenchmarkSuiteReport) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [ASR_BENCHMARK_SUITE] SUMMARY: ${report.suiteName} ===")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] TOTAL_CASES: ${report.totalCases}")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] EXACT_MATCH_COUNT: ${report.exactMatchCount}")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] EXACT_MATCH_RATE: ${"%.4f".format(report.exactMatchRate)} (${"%.2f".format(report.exactMatchRate * 100.0)}%)")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] TOTAL_S: ${report.totalSubstitutions}")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] TOTAL_D: ${report.totalDeletions}")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] TOTAL_I: ${report.totalInsertions}")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] TOTAL_H: ${report.totalHits}")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] TOTAL_REFERENCE_WORDS: ${report.totalReferenceWords}")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] TOTAL_REFERENCE_CHARS: ${report.totalReferenceCharacters}")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] CORPUS_WER: ${"%.4f".format(report.corpusWer)} (${"%.2f".format(report.corpusWer * 100.0)}%)")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] CORPUS_CER: ${"%.4f".format(report.corpusCer)} (${"%.2f".format(report.corpusCer * 100.0)}%)")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] TOTAL_AUDIO_DURATION_MS: ${report.totalAudioDurationMs}")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] TOTAL_INFERENCE_TIME_MS: ${report.totalInferenceTimeMs}")
        Log.i(TAG, "[ASR_BENCHMARK_SUITE] MEAN_RTF: ${"%.4f".format(report.meanRtf)}")
        Log.i(TAG, "=========================================================")
    }
}
