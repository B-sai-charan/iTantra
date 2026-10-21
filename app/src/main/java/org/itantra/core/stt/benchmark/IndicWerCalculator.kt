package org.itantra.core.stt.benchmark

/**
 * Deterministic Levenshtein Dynamic Programming Calculator for Word Error Rate (WER)
 * and Character Error Rate (CER) tailored for Indic and multilingual speech evaluation.
 *
 * Scorer Methodology:
 * - Word Units (WER): Whitespace-delimited Unicode tokens after [IndicTextNormalizer.normalize].
 * - Character Units (CER): Unicode code points (excluding whitespace).
 *   NOTE: This implementation computes code-point CER. Grapheme-cluster (akshara) segmentation
 *   is an advanced topic and is explicitly distinguished from code-point CER.
 *
 * Edge Case Handling:
 * - Empty Reference & Empty Hypothesis:
 *     Reference count = 0, Errors = 0, Error Rate = 0.0, Exact Match = true.
 * - Empty Reference & Non-Empty Hypothesis (Pure Insertions):
 *     Reference count = 0, Errors = M (insertions), Error Rate = 1.0 (100% insertion error), Exact Match = false.
 * - Non-Empty Reference & Empty Hypothesis (Pure Deletions):
 *     Reference count = N, Errors = N (deletions), Error Rate = 1.0 (100% deletion error), Exact Match = false.
 */
object IndicWerCalculator {

    /**
     * Evaluates reference against hypothesis text, calculating Indic-normalized WER and code-point CER.
     *
     * @param reference Ground truth reference transcript.
     * @param hypothesis ASR transcribed hypothesis.
     * @param normalize If true, applies [IndicTextNormalizer.normalize] to both texts before scoring.
     * @return [WerResult] containing complete alignment metrics.
     */
    fun calculate(
        reference: String,
        hypothesis: String,
        normalize: Boolean = true
    ): WerResult {
        val normRef = if (normalize) IndicTextNormalizer.normalize(reference) else reference.trim()
        val normHyp = if (normalize) IndicTextNormalizer.normalize(hypothesis) else hypothesis.trim()

        val isExactMatch = normRef == normHyp

        // Word-level tokenization (split on single space)
        val refWords = if (normRef.isEmpty()) emptyList() else normRef.split(" ")
        val hypWords = if (normHyp.isEmpty()) emptyList() else normHyp.split(" ")
        val wordStats = alignTokens(refWords, hypWords)

        // Character-level tokenization (Unicode code points, excluding whitespace)
        val refChars = mutableListOf<String>()
        normRef.codePoints().forEach { cp ->
            if (!Character.isWhitespace(cp)) {
                refChars.add(String(Character.toChars(cp)))
            }
        }

        val hypChars = mutableListOf<String>()
        normHyp.codePoints().forEach { cp ->
            if (!Character.isWhitespace(cp)) {
                hypChars.add(String(Character.toChars(cp)))
            }
        }

        val charStats = alignTokens(refChars, hypChars)

        return WerResult(
            rawReference = reference,
            rawHypothesis = hypothesis,
            normalizedReference = normRef,
            normalizedHypothesis = normHyp,
            isExactMatch = isExactMatch,
            wordStats = wordStats,
            charStats = charStats
        )
    }

    /**
     * Computes Levenshtein edit distance and counts S, D, I, H via standard dynamic programming backtrace.
     *
     * @param ref List of reference tokens (words or character code points).
     * @param hyp List of hypothesis tokens (words or character code points).
     * @return [AlignmentStats] with substitution, deletion, insertion, and rate metrics.
     */
    fun alignTokens(ref: List<String>, hyp: List<String>): AlignmentStats {
        val n = ref.size
        val m = hyp.size

        // Edge case: Empty Reference
        if (n == 0) {
            return if (m == 0) {
                AlignmentStats(
                    substitutions = 0,
                    deletions = 0,
                    insertions = 0,
                    hits = 0,
                    referenceCount = 0,
                    errorCount = 0,
                    errorRate = 0.0
                )
            } else {
                AlignmentStats(
                    substitutions = 0,
                    deletions = 0,
                    insertions = m,
                    hits = 0,
                    referenceCount = 0,
                    errorCount = m,
                    errorRate = 1.0
                )
            }
        }

        // Edge case: Empty Hypothesis
        if (m == 0) {
            return AlignmentStats(
                substitutions = 0,
                deletions = n,
                insertions = 0,
                hits = 0,
                referenceCount = n,
                errorCount = n,
                errorRate = 1.0
            )
        }

        // DP table allocation
        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0..n) {
            dp[i][0] = i
        }
        for (j in 0..m) {
            dp[0][j] = j
        }

        for (i in 1..n) {
            val refToken = ref[i - 1]
            for (j in 1..m) {
                val hypToken = hyp[j - 1]
                val cost = if (refToken == hypToken) 0 else 1

                val deletion = dp[i - 1][j] + 1
                val insertion = dp[i][j - 1] + 1
                val substitution = dp[i - 1][j - 1] + cost

                dp[i][j] = minOf(deletion, insertion, substitution)
            }
        }

        // Backtrace to count S, D, I, H
        var i = n
        var j = m
        var substitutions = 0
        var deletions = 0
        var insertions = 0
        var hits = 0

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && ref[i - 1] == hyp[j - 1] && dp[i][j] == dp[i - 1][j - 1]) {
                hits++
                i--
                j--
            } else if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                substitutions++
                i--
                j--
            } else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                deletions++
                i--
            } else if (j > 0 && dp[i][j] == dp[i][j - 1] + 1) {
                insertions++
                j--
            } else {
                // Fallback diagonal/boundary path
                if (i > 0 && j > 0) {
                    if (ref[i - 1] == hyp[j - 1]) hits++ else substitutions++
                    i--
                    j--
                } else if (i > 0) {
                    deletions++
                    i--
                } else {
                    insertions++
                    j--
                }
            }
        }

        val totalErrors = substitutions + deletions + insertions
        val errorRate = totalErrors.toDouble() / n.toDouble()

        return AlignmentStats(
            substitutions = substitutions,
            deletions = deletions,
            insertions = insertions,
            hits = hits,
            referenceCount = n,
            errorCount = totalErrors,
            errorRate = errorRate
        )
    }
}
