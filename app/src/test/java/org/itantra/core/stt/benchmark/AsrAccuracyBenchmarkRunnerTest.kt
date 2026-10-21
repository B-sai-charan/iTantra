package org.itantra.core.stt.benchmark

import kotlinx.coroutines.runBlocking
import org.itantra.core.stt.AsrEngine
import org.itantra.core.stt.AsrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for AsrAccuracyBenchmarkRunner pure evaluation logic and suite aggregation.
 */
class AsrAccuracyBenchmarkRunnerTest {

    private class FakeAsrEngine(
        private val outputText: String,
        private val outputLanguage: String = "en",
        private val inferenceTimeMs: Long = 4000L,
        private val audioDurationMs: Long = 6625L
    ) : AsrEngine {
        override suspend fun transcribe(audio: ShortArray, sampleRate: Int, language: String?): AsrResult {
            return AsrResult(
                text = outputText,
                language = outputLanguage,
                confidence = null,
                inferenceTimeMs = inferenceTimeMs,
                audioDurationMs = audioDurationMs
            )
        }

        override fun close() {}
    }

    private fun createStubCaseResult(
        caseId: String,
        exactMatch: Boolean,
        substitutions: Int,
        deletions: Int,
        insertions: Int,
        hits: Int,
        referenceWordCount: Int,
        charSubstitutions: Int,
        charDeletions: Int,
        charInsertions: Int,
        charHits: Int,
        referenceCharCount: Int,
        audioDurationMs: Long,
        inferenceTimeMs: Long,
        rtf: Double
    ): AsrBenchmarkCaseResult {
        val wer = if (referenceWordCount > 0) (substitutions + deletions + insertions).toDouble() / referenceWordCount else 0.0
        val cer = if (referenceCharCount > 0) (charSubstitutions + charDeletions + charInsertions).toDouble() / referenceCharCount else 0.0

        return AsrBenchmarkCaseResult(
            caseId = caseId,
            language = "en",
            detectedLanguage = "en",
            rawReference = "ref",
            rawHypothesis = "hyp",
            normalizedReference = "ref",
            normalizedHypothesis = "hyp",
            exactMatch = exactMatch,
            substitutions = substitutions,
            deletions = deletions,
            insertions = insertions,
            hits = hits,
            referenceWordCount = referenceWordCount,
            wer = wer,
            charSubstitutions = charSubstitutions,
            charDeletions = charDeletions,
            charInsertions = charInsertions,
            charHits = charHits,
            referenceCharCount = referenceCharCount,
            cer = cer,
            audioDurationMs = audioDurationMs,
            inferenceTimeMs = inferenceTimeMs,
            rtf = rtf
        )
    }

    @Test
    fun testEvaluateCase_ExactMatch() = runBlocking {
        val testCase = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_EN_001
        val fakeEngine = FakeAsrEngine(
            outputText = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
        )
        val dummyAudio = ShortArray(106000)

        val result = AsrAccuracyBenchmarkRunner.evaluateCase(
            engine = fakeEngine,
            audio = dummyAudio,
            case = testCase
        )

        assertEquals("EN-001", result.caseId)
        assertEquals("en", result.language)
        assertEquals("en", result.detectedLanguage)
        assertTrue(result.exactMatch)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(18, result.hits)
        assertEquals(18, result.referenceWordCount)
        assertEquals(87, result.referenceCharCount)
        assertEquals(0.0, result.wer, 1e-9)
        assertEquals(0.0, result.cer, 1e-9)
        assertEquals(6625L, result.audioDurationMs)
        assertEquals(4000L, result.inferenceTimeMs)
        assertEquals(4000.0 / 6625.0, result.rtf, 1e-4)
    }

    @Test
    fun testEvaluateCase_WithSubstitutionAndDeletion() = runBlocking {
        val testCase = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_EN_001
        // Reference has 18 words.
        // Modified hypothesis replaces "brothels" with "town" and deletes "squalid"
        val modifiedHypothesis = "After early nightfall, the yellow lamps would light up here and there the quarter of the town."
        val fakeEngine = FakeAsrEngine(outputText = modifiedHypothesis)
        val dummyAudio = ShortArray(106000)

        val result = AsrAccuracyBenchmarkRunner.evaluateCase(
            engine = fakeEngine,
            audio = dummyAudio,
            case = testCase
        )

        assertEquals("EN-001", result.caseId)
        assertFalse(result.exactMatch)
        assertEquals(1, result.substitutions) // brothels -> town
        assertEquals(1, result.deletions)     // squalid deleted
        assertEquals(0, result.insertions)
        assertEquals(16, result.hits)
        assertEquals(18, result.referenceWordCount)
        assertEquals(2.0 / 18.0, result.wer, 1e-9)
    }

    @Test
    fun testSuiteAggregation_TwoPerfectCases() {
        val case1 = createStubCaseResult(
            caseId = "EN-001",
            exactMatch = true,
            substitutions = 0, deletions = 0, insertions = 0, hits = 18, referenceWordCount = 18,
            charSubstitutions = 0, charDeletions = 0, charInsertions = 0, charHits = 87, referenceCharCount = 87,
            audioDurationMs = 6625L, inferenceTimeMs = 5000L, rtf = 0.75
        )
        val case2 = createStubCaseResult(
            caseId = "EN-002",
            exactMatch = true,
            substitutions = 0, deletions = 0, insertions = 0, hits = 48, referenceWordCount = 48,
            charSubstitutions = 0, charDeletions = 0, charInsertions = 0, charHits = 202, referenceCharCount = 202,
            audioDurationMs = 16715L, inferenceTimeMs = 12500L, rtf = 0.7478
        )

        val report = AsrAccuracyBenchmarkRunner.aggregateResults("Two-Case-Perfect", listOf(case1, case2))

        assertEquals(2, report.totalCases)
        assertEquals(2, report.exactMatchCount)
        assertEquals(1.0, report.exactMatchRate, 1e-9)
        assertEquals(0, report.totalSubstitutions)
        assertEquals(0, report.totalDeletions)
        assertEquals(0, report.totalInsertions)
        assertEquals(66, report.totalHits)
        assertEquals(66, report.totalReferenceWords)
        assertEquals(289, report.totalReferenceCharacters)
        assertEquals(0.0, report.corpusWer, 1e-9)
        assertEquals(0.0, report.corpusCer, 1e-9)
        assertEquals(23340L, report.totalAudioDurationMs)
        assertEquals(17500L, report.totalInferenceTimeMs)
        assertEquals((0.75 + 0.7478) / 2.0, report.meanRtf, 1e-6)
    }

    @Test
    fun testSuiteAggregation_CorpusWerDiffersFromArithmeticMeanWhenLengthsDiffer() {
        // Case 1: 10 words, 1 error -> WER = 0.10
        val case1 = createStubCaseResult(
            caseId = "C1", exactMatch = false,
            substitutions = 1, deletions = 0, insertions = 0, hits = 9, referenceWordCount = 10,
            charSubstitutions = 5, charDeletions = 0, charInsertions = 0, charHits = 45, referenceCharCount = 50,
            audioDurationMs = 2000L, inferenceTimeMs = 1000L, rtf = 0.50
        )
        // Case 2: 100 words, 20 errors -> WER = 0.20
        val case2 = createStubCaseResult(
            caseId = "C2", exactMatch = false,
            substitutions = 20, deletions = 0, insertions = 0, hits = 80, referenceWordCount = 100,
            charSubstitutions = 50, charDeletions = 0, charInsertions = 0, charHits = 450, referenceCharCount = 500,
            audioDurationMs = 20000L, inferenceTimeMs = 16000L, rtf = 0.80
        )

        val report = AsrAccuracyBenchmarkRunner.aggregateResults("Length-Diff", listOf(case1, case2))

        val arithmeticMeanWer = (0.10 + 0.20) / 2.0 // 0.15
        val expectedCorpusWer = (1.0 + 20.0) / (10.0 + 100.0) // 21 / 110 = 0.19090909...

        assertNotEquals("Corpus WER must NOT equal unweighted arithmetic mean", arithmeticMeanWer, report.corpusWer, 1e-4)
        assertEquals(expectedCorpusWer, report.corpusWer, 1e-9)
        assertEquals(21, report.totalSubstitutions)
        assertEquals(110, report.totalReferenceWords)
    }

    @Test
    fun testSuiteAggregation_CorpusCerCalculation() {
        val case1 = createStubCaseResult(
            caseId = "C1", exactMatch = false,
            substitutions = 0, deletions = 0, insertions = 0, hits = 10, referenceWordCount = 10,
            charSubstitutions = 2, charDeletions = 1, charInsertions = 1, charHits = 46, referenceCharCount = 50, // 4 errors / 50 = 0.08
            audioDurationMs = 2000L, inferenceTimeMs = 1000L, rtf = 0.50
        )
        val case2 = createStubCaseResult(
            caseId = "C2", exactMatch = false,
            substitutions = 0, deletions = 0, insertions = 0, hits = 20, referenceWordCount = 20,
            charSubstitutions = 6, charDeletions = 2, charInsertions = 2, charHits = 190, referenceCharCount = 200, // 10 errors / 200 = 0.05
            audioDurationMs = 5000L, inferenceTimeMs = 2500L, rtf = 0.50
        )

        val report = AsrAccuracyBenchmarkRunner.aggregateResults("CER-Test", listOf(case1, case2))

        val expectedCorpusCer = (4.0 + 10.0) / (50.0 + 200.0) // 14 / 250 = 0.056
        assertEquals(expectedCorpusCer, report.corpusCer, 1e-9)
        assertEquals(8, report.totalCharSubstitutions)
        assertEquals(3, report.totalCharDeletions)
        assertEquals(3, report.totalCharInsertions)
        assertEquals(250, report.totalReferenceCharacters)
    }

    @Test
    fun testSuiteAggregation_EmptySuiteHandling() {
        val report = AsrAccuracyBenchmarkRunner.aggregateResults("Empty-Suite", emptyList())

        assertEquals(0, report.totalCases)
        assertEquals(0, report.exactMatchCount)
        assertEquals(0.0, report.exactMatchRate, 1e-9)
        assertEquals(0, report.totalReferenceWords)
        assertEquals(0, report.totalReferenceCharacters)
        assertEquals(0.0, report.corpusWer, 1e-9)
        assertEquals(0.0, report.corpusCer, 1e-9)
        assertEquals(0.0, report.meanRtf, 1e-9)
        assertEquals(0L, report.totalAudioDurationMs)
        assertEquals(0L, report.totalInferenceTimeMs)
        assertTrue(report.perCaseResults.isEmpty())
    }

    @Test
    fun testBenchmarkSuiteStructure_ExpandedSixCases() {
        val suite = AsrAccuracyBenchmarkRunner.ENGLISH_BENCHMARK_SUITE
        assertEquals(6, suite.size)

        // 1. All fixture IDs must be unique
        val ids = suite.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf("EN-001", "EN-002", "EN-003", "EN-004", "EN-005", "EN-006"), ids)

        // 2. All benchmark asset names exist and end with .wav
        for (case in suite) {
            assertTrue("Asset path must not be blank", case.assetPath.isNotBlank())
            assertTrue("Asset path must end with .wav", case.assetPath.endsWith(".wav"))
            assertTrue("Reference transcript must not be blank", case.referenceTranscript.isNotBlank())
            assertEquals("Expected sample rate must be 16000", 16000, case.expectedSampleRate)
            assertEquals("Expected channels must be 1", 1, case.expectedChannels)
            assertEquals("Expected language must be en", "en", case.language)
        }

        // 3. EN-001 and EN-002 references and asset paths remain byte/text identical
        val en001 = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_EN_001
        assertEquals("EN-001", en001.id)
        assertEquals("speech_test.wav", en001.assetPath)
        assertEquals(
            "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels.",
            en001.referenceTranscript
        )

        val en002 = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_EN_002
        assertEquals("EN-002", en002.id)
        assertEquals("speech_test_002.wav", en002.assetPath)
        assertEquals(
            "GOD AS A DIRECT CONSEQUENCE OF THE SIN WHICH MAN THUS PUNISHED HAD GIVEN HER A LOVELY CHILD WHOSE PLACE WAS ON THAT SAME DISHONOURED BOSOM TO CONNECT HER PARENT FOR EVER WITH THE RACE AND DESCENT OF MORTALS AND TO BE FINALLY A BLESSED SOUL IN HEAVEN",
            en002.referenceTranscript
        )

        // 4. Verify EN-003 through EN-006 definitions
        val en003 = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_EN_003
        assertEquals("EN-003", en003.id)
        assertEquals("speech_test_003.wav", en003.assetPath)
        assertEquals("YET THESE THOUGHTS AFFECTED HESTER PRYNNE LESS WITH HOPE THAN APPREHENSION", en003.referenceTranscript)

        val en004 = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_EN_004
        assertEquals("EN-004", en004.id)
        assertEquals("speech_test_004.wav", en004.assetPath)
        assertEquals("well the last year this property had six hundred thousand birds on it", en004.referenceTranscript)

        val en005 = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_EN_005
        assertEquals("EN-005", en005.id)
        assertEquals("speech_test_005.wav", en005.assetPath)
        assertEquals(
            "goes very different ways depending on how you think and whether you think of the remembering self or you think of the experiencing self this is going to influence policy i think in years to come in the united states efforts are being made",
            en005.referenceTranscript
        )

        val en006 = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_EN_006
        assertEquals("EN-006", en006.id)
        assertEquals("speech_test_006.wav", en006.assetPath)
        assertEquals(
            "and he was talking about invisible demons and smoke and how someone was poisoning him in his sleep and i was afraid not for myself but i was afraid that i was going to lose him that he was going to sink into one of his states",
            en006.referenceTranscript
        )
    }

    @Test
    fun testSuiteAggregation_SixCaseAggregation() {
        val cases = listOf(
            createStubCaseResult("EN-001", true, 0, 0, 0, 18, 18, 0, 0, 0, 87, 87, 6625L, 5000L, 0.75),
            createStubCaseResult("EN-002", false, 3, 1, 0, 44, 48, 2, 1, 0, 199, 202, 16715L, 12000L, 0.72),
            createStubCaseResult("EN-003", true, 0, 0, 0, 10, 10, 0, 0, 0, 64, 64, 4825L, 3500L, 0.73),
            createStubCaseResult("EN-004", true, 0, 0, 0, 13, 13, 0, 0, 0, 58, 58, 3652L, 2600L, 0.71),
            createStubCaseResult("EN-005", false, 2, 0, 1, 39, 42, 5, 1, 2, 191, 199, 12610L, 9000L, 0.71),
            createStubCaseResult("EN-006", true, 0, 0, 0, 44, 44, 0, 0, 0, 182, 182, 17330L, 12500L, 0.72)
        )

        val report = AsrAccuracyBenchmarkRunner.aggregateResults("Six-Case-Suite", cases)

        assertEquals(6, report.totalCases)
        assertEquals(4, report.exactMatchCount)
        assertEquals(4.0 / 6.0, report.exactMatchRate, 1e-9)

        val totalWords = 18 + 48 + 10 + 13 + 42 + 44 // 175
        val totalWordErrors = 0 + 4 + 0 + 0 + 3 + 0   // 7
        assertEquals(totalWords, report.totalReferenceWords)
        assertEquals(totalWordErrors.toDouble() / totalWords.toDouble(), report.corpusWer, 1e-9)

        val totalChars = 87 + 202 + 64 + 58 + 199 + 182 // 792
        val totalCharErrors = 0 + 3 + 0 + 0 + 8 + 0      // 11
        assertEquals(totalChars, report.totalReferenceCharacters)
        assertEquals(totalCharErrors.toDouble() / totalChars.toDouble(), report.corpusCer, 1e-9)

        val expectedMeanRtf = (0.75 + 0.72 + 0.73 + 0.71 + 0.71 + 0.72) / 6.0
        assertEquals(expectedMeanRtf, report.meanRtf, 1e-9)
    }

    @Test
    fun testBenchmarkStructure_HindiCase() {
        val case = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_HI_001
        assertEquals("HI-001", case.id)
        assertEquals("hi", case.language)
        assertEquals("speech_test_hi_001.wav", case.assetPath)
        assertEquals(
            "कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,",
            case.referenceTranscript
        )
        assertEquals(16000, case.expectedSampleRate)
        assertEquals(1, case.expectedChannels)
    }

    @Test
    fun testEvaluateCaseIndic_ExactMatch() = runBlocking {
        val testCase = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_HI_001
        val fakeEngine = FakeAsrEngine(
            outputText = "कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है",
            outputLanguage = "hi",
            inferenceTimeMs = 9500L,
            audioDurationMs = 9120L
        )
        val dummyAudio = ShortArray(145920)

        val result = AsrAccuracyBenchmarkRunner.evaluateCaseIndic(
            engine = fakeEngine,
            audio = dummyAudio,
            case = testCase
        )

        assertEquals("HI-001", result.caseId)
        assertEquals("hi", result.language)
        assertEquals("hi", result.detectedLanguage)
        assertTrue(result.exactMatch)
        assertEquals(0, result.substitutions)
        assertEquals(0, result.deletions)
        assertEquals(0, result.insertions)
        assertEquals(24, result.hits)
        assertEquals(24, result.referenceWordCount)
        assertEquals(0.0, result.wer, 1e-9)
        assertEquals(0.0, result.cer, 1e-9)
        assertEquals(9120L, result.audioDurationMs)
        assertEquals(9500L, result.inferenceTimeMs)
    }

    @Test
    fun testBenchmarkStructure_HindiSuiteFiveCases() {
        val suite = AsrAccuracyBenchmarkRunner.HINDI_BENCHMARK_SUITE
        assertEquals(5, suite.size)

        val ids = suite.map { it.id }
        assertEquals(listOf("HI-001", "HI-002", "HI-003", "HI-004", "HI-005"), ids)

        val assets = suite.map { it.assetPath }
        assertEquals(5, assets.toSet().size)
        assertEquals(listOf("speech_test_hi_001.wav", "speech_test_hi_002.wav", "speech_test_hi_003.wav", "speech_test_hi_004.wav", "speech_test_hi_005.wav"), assets)

        for (case in suite) {
            assertEquals("hi", case.language)
            assertTrue("Reference transcript must not be blank", case.referenceTranscript.isNotBlank())
            assertEquals(16000, case.expectedSampleRate)
            assertEquals(1, case.expectedChannels)
            assertTrue(case.assetPath.startsWith("speech_test_hi_"))
            assertTrue(case.assetPath.endsWith(".wav"))
        }

        // Verify HI-001 integrity
        val hi001 = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_HI_001
        assertEquals("HI-001", hi001.id)
        assertEquals("speech_test_hi_001.wav", hi001.assetPath)
        assertEquals("कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,", hi001.referenceTranscript)

        // Verify HI-002 through HI-005 references
        val hi002 = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_HI_002
        assertEquals("HI-002", hi002.id)
        assertEquals("speech_test_hi_002.wav", hi002.assetPath)
        assertEquals("मनोविज्ञान सहित विज्ञान के सभी मामलों पर अरस्तू के विचारों को स्वीकार किया जाता है।", hi002.referenceTranscript)

        val hi003 = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_HI_003
        assertEquals("HI-003", hi003.id)
        assertEquals("speech_test_hi_003.wav", hi003.assetPath)
        assertEquals("लूना को साथी पहलवानों ने भी श्रद्धांजलि दी.", hi003.referenceTranscript)

        val hi004 = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_HI_004
        assertEquals("HI-004", hi004.id)
        assertEquals("speech_test_hi_004.wav", hi004.assetPath)
        assertEquals("रोम में अलग-अलग जगहों पर कई बड़े टेलीविज़न स्क्रीन लगाए गए ताकि लोग समारोह देख सकें.", hi004.referenceTranscript)

        val hi005 = AsrAccuracyBenchmarkRunner.BENCHMARK_CASE_HI_005
        assertEquals("HI-005", hi005.id)
        assertEquals("speech_test_hi_005.wav", hi005.assetPath)
        assertEquals("यह शहर देश के बाकी शहरों से अलग है क्योंकि यह किसी अफ्रीकी शहर की बजाय अरब शहर लगता है.", hi005.referenceTranscript)
    }
}
