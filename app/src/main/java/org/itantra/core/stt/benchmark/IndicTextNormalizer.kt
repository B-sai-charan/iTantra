package org.itantra.core.stt.benchmark

import java.text.Normalizer
import java.util.Locale

/**
 * Unicode-safe text normalizer tailored for Indic (Devanagari, etc.) and multilingual ASR evaluation.
 *
 * Design Principles:
 * 1. Unicode NFC Normalization: Canonicalizes composite vs decomposed character sequences.
 * 2. Script Preservation: Strictly retains all Unicode letters (\p{L}), combining marks/matras (\p{M}),
 *    and numeric digits (\p{N}).
 * 3. Punctuation Stripping: Removes Latin punctuation, symbols, and native Indic punctuation (such as
 *    Devanagari Danda '।' U+0964 and Double Danda '॥' U+0965) without stripping adjacent letters or matras.
 * 4. Whitespace Normalization: Collapses multiple whitespace characters to a single space and trims extremities.
 * 5. Safe Casing: Applies Unicode-aware lowercase via [Locale.ROOT] without mutating non-cased Indic scripts.
 * 6. Non-Destructive: Does NOT perform transliteration, phonetic normalization, number-to-word conversion,
 *    or homophone merging.
 */
object IndicTextNormalizer {

    /**
     * Regex matching any character that is NOT a Unicode Letter (\p{L}),
     * Combining Mark (\p{M}), Number (\p{N}), or Whitespace (\s).
     *
     * This safely catches all punctuation (\p{P}) and symbols (\p{S}) across all Unicode scripts.
     */
    private val PUNCTUATION_AND_SYMBOLS_REGEX = Regex("""[^\p{L}\p{M}\p{N}\s]""")
    private val MULTI_WHITESPACE_REGEX = Regex("""\s+""")

    /**
     * Normalizes an input string according to Indic-safe Unicode ASR benchmark rules.
     *
     * @param input Raw input text (reference transcript or ASR hypothesis).
     * @return Deterministically normalized string.
     */
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) {
            return ""
        }

        // 1. Unicode Normalization Form C (Canonical Decomposition followed by Canonical Composition)
        val nfcText = Normalizer.normalize(input, Normalizer.Form.NFC)

        // 2. Safe lowercasing (effects Latin/Cyrillic scripts without mutating Indic characters)
        val lowercased = nfcText.lowercase(Locale.ROOT)

        // 3. Remove punctuation and symbols while preserving letters, marks/matras, numbers, and spaces
        val stripped = PUNCTUATION_AND_SYMBOLS_REGEX.replace(lowercased, "")

        // 4. Collapse whitespace sequences to single space and trim extremities
        return MULTI_WHITESPACE_REGEX.replace(stripped, " ").trim()
    }
}
