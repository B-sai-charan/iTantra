package org.itantra.core.stt.benchmark

/**
 * Definition of a single deterministic ASR benchmark case.
 */
data class AsrBenchmarkCase(
    val id: String,
    val language: String,
    val assetPath: String,
    val referenceTranscript: String,
    val expectedSampleRate: Int = 16000,
    val expectedChannels: Int = 1
)

/**
 * Complete evaluation result for a single benchmark case execution.
 */
data class AsrBenchmarkCaseResult(
    val caseId: String,
    val language: String,
    val detectedLanguage: String,
    val rawReference: String,
    val rawHypothesis: String,
    val normalizedReference: String,
    val normalizedHypothesis: String,
    val exactMatch: Boolean,
    // Word-level alignment metrics
    val substitutions: Int,
    val deletions: Int,
    val insertions: Int,
    val hits: Int,
    val referenceWordCount: Int,
    val wer: Double,
    // Character-level alignment metrics
    val charSubstitutions: Int,
    val charDeletions: Int,
    val charInsertions: Int,
    val charHits: Int,
    val referenceCharCount: Int,
    val cer: Double,
    // Performance metrics
    val audioDurationMs: Long,
    val inferenceTimeMs: Long,
    val rtf: Double
)

/**
 * Suite-level aggregate evaluation report over multiple benchmark cases.
 *
 * Corpus WER is strictly defined as total word errors / total reference words across all cases.
 * Corpus CER is strictly defined as total character errors / total reference characters across all cases.
 * Mean RTF is defined as the arithmetic mean of per-case RTF values.
 */
data class AsrBenchmarkSuiteReport(
    val suiteName: String,
    val totalCases: Int,
    val exactMatchCount: Int,
    val exactMatchRate: Double,
    // Cumulative word-level counts
    val totalSubstitutions: Int,
    val totalDeletions: Int,
    val totalInsertions: Int,
    val totalHits: Int,
    val totalReferenceWords: Int,
    // Cumulative character-level counts
    val totalCharSubstitutions: Int,
    val totalCharDeletions: Int,
    val totalCharInsertions: Int,
    val totalCharHits: Int,
    val totalReferenceCharacters: Int,
    // Corpus-level error rates
    val corpusWer: Double,
    val corpusCer: Double,
    // Aggregate timing and performance
    val totalAudioDurationMs: Long,
    val totalInferenceTimeMs: Long,
    val meanRtf: Double,
    // Per-case detailed results
    val perCaseResults: List<AsrBenchmarkCaseResult>
)
