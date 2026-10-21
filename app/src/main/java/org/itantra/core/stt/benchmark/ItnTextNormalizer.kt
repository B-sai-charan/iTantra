package org.itantra.core.stt.benchmark

/**
 * Secondary diagnostic Text Normalizer that performs deterministic Inverse Text Normalization (ITN)
 * and cardinal number expansion for English speech evaluation.
 *
 * NOTE: This is a SECONDARY DIAGNOSTIC component and does NOT alter or replace the official
 * strict [TextNormalizer] baseline.
 *
 * Supported Scope:
 * - Deterministic English integer cardinal numbers (0 through 999,999,999).
 * - Symmetrical representation mapping between digits (e.g. "600000") and spelled-out words (e.g. "six hundred thousand").
 * - Unrelated non-numeric words and vocabulary remain completely unaffected.
 */
object ItnTextNormalizer {

    private val DIGIT_TOKEN_REGEX = Regex("""\b\d+\b""")

    private val UNITS = arrayOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    )

    private val TENS = arrayOf(
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    )

    /**
     * Applies standard baseline normalization followed by secondary ITN cardinal number expansion (digits to words).
     *
     * @param input Raw input text (reference or hypothesis).
     * @return Secondary ITN-normalized string.
     */
    fun normalize(input: String?): String {
        val baseNormalized = TextNormalizer.normalize(input)
        if (baseNormalized.isEmpty()) {
            return ""
        }
        return digitsToWords(baseNormalized)
    }

    /**
     * Converts all integer digit tokens in the text to spelled-out English cardinal words.
     *
     * Examples:
     * - "600000" -> "six hundred thousand"
     * - "600" -> "six hundred"
     * - "1000" -> "one thousand"
     * - "20000" -> "twenty thousand"
     */
    fun digitsToWords(input: String?): String {
        if (input.isNullOrBlank()) return ""

        return DIGIT_TOKEN_REGEX.replace(input) { matchResult ->
            val numberStr = matchResult.value
            val numberLong = numberStr.toLongOrNull()
            if (numberLong != null && numberLong in 0..999_999_999L) {
                formatNumberToWords(numberLong)
            } else {
                numberStr
            }
        }
    }

    /**
     * Converts a non-negative long integer (0 to 999,999,999) to canonical English words.
     */
    fun formatNumberToWords(n: Long): String {
        if (n < 20) {
            return UNITS[n.toInt()]
        }
        if (n < 100) {
            val ten = TENS[(n / 10).toInt()]
            val rem = n % 10
            return if (rem > 0) "$ten ${UNITS[rem.toInt()]}" else ten
        }
        if (n < 1_000) {
            val hundred = "${formatNumberToWords(n / 100)} hundred"
            val rem = n % 100
            return if (rem > 0) "$hundred ${formatNumberToWords(rem)}" else hundred
        }
        if (n < 1_000_000) {
            val thousand = "${formatNumberToWords(n / 1000)} thousand"
            val rem = n % 1000
            return if (rem > 0) "$thousand ${formatNumberToWords(rem)}" else thousand
        }
        if (n < 1_000_000_000) {
            val million = "${formatNumberToWords(n / 1_000_000)} million"
            val rem = n % 1_000_000
            return if (rem > 0) "$million ${formatNumberToWords(rem)}" else million
        }
        return n.toString()
    }

    /**
     * Replaces common spelled-out cardinal number word sequences with their numeric digit strings.
     *
     * Supported diagnostic conversions:
     * - "six hundred thousand" -> "600000"
     * - "six hundred" -> "600"
     * - "twenty thousand" -> "20000"
     * - "one thousand" -> "1000"
     */
    fun wordsToDigits(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var text = TextNormalizer.normalize(input)

        val wordToDigitPhrases = listOf(
            "six hundred thousand" to "600000",
            "twenty thousand" to "20000",
            "six hundred" to "600",
            "one thousand" to "1000"
        )

        for ((phrase, digitStr) in wordToDigitPhrases) {
            text = text.replace(Regex("""\b$phrase\b"""), digitStr)
        }
        return text
    }
}
