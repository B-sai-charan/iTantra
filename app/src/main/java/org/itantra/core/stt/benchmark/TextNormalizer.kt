package org.itantra.core.stt.benchmark

/**
 * Deterministic text normalizer for ASR benchmark evaluation.
 *
 * Normalization Rules:
 * 1. Convert text to lowercase.
 * 2. Remove all punctuation and symbols (retain only alphanumeric characters and whitespace).
 * 3. Normalize all whitespace sequences (tabs, newlines, multiple spaces) to a single space.
 * 4. Trim leading and trailing whitespace.
 * 5. Strictly preserve alphabetic and numeric word tokens.
 * 6. No semantic or spelling alterations are performed.
 *
 * This normalizer is applied symmetrically to both reference transcripts and ASR hypotheses.
 */
object TextNormalizer {

    private val PUNCTUATION_AND_SYMBOLS_REGEX = Regex("""[^\p{L}\p{N}\s]""")
    private val MULTI_WHITESPACE_REGEX = Regex("""\s+""")

    /**
     * Normalizes an input string according to standard ASR benchmark rules.
     *
     * @param input Raw input text (reference or hypothesis).
     * @return Deterministically normalized string.
     */
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) {
            return ""
        }

        return input
            .lowercase()
            .replace(PUNCTUATION_AND_SYMBOLS_REGEX, "")
            .replace(MULTI_WHITESPACE_REGEX, " ")
            .trim()
    }
}
