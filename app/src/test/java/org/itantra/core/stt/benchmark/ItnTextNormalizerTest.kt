package org.itantra.core.stt.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ItnTextNormalizer] and [ItnWerCalculator] secondary diagnostic scoring.
 */
class ItnTextNormalizerTest {

    @Test
    fun testEn004_StrictVsItnAwareComparison() {
        val reference = "well the last year this property had six hundred thousand birds on it"
        val hypothesis = "alright well last year this property had 600000 birds on it"

        // 1. Strict official baseline scorer (MUST REMAIN UNCHANGED)
        val strictResult = WerCalculator.calculate(reference, hypothesis)
        assertEquals(13, strictResult.wordStats.referenceCount)
        assertEquals(3, strictResult.wordStats.substitutions) // well->alright, the->well, six->600000
        assertEquals(2, strictResult.wordStats.deletions)     // hundred, thousand
        assertEquals(0, strictResult.wordStats.insertions)
        assertEquals(8, strictResult.wordStats.hits)
        assertEquals(5, strictResult.wordStats.errorCount)
        assertEquals(5.0 / 13.0, strictResult.wordStats.errorRate, 1e-4) // 38.46%
        assertFalse(strictResult.isExactMatch)

        // 2. Secondary ITN-aware diagnostic scorer
        val itnResult = ItnWerCalculator.calculate(reference, hypothesis)
        assertEquals(13, itnResult.wordStats.referenceCount)
        assertEquals(2, itnResult.wordStats.substitutions) // well->alright, the->well
        assertEquals(0, itnResult.wordStats.deletions)
        assertEquals(0, itnResult.wordStats.insertions)
        assertEquals(11, itnResult.wordStats.hits)
        assertEquals(2, itnResult.wordStats.errorCount)
        assertEquals(2.0 / 13.0, itnResult.wordStats.errorRate, 1e-4) // 15.38%
        assertFalse(itnResult.isExactMatch)

        // 3. Explicitly verify normalized representations under ITN
        assertEquals(
            "well the last year this property had six hundred thousand birds on it",
            itnResult.normalizedReference
        )
        assertEquals(
            "alright well last year this property had six hundred thousand birds on it",
            itnResult.normalizedHypothesis
        )
    }

    @Test
    fun testDigitsToWords_CardinalNumbers() {
        assertEquals("six hundred", ItnTextNormalizer.digitsToWords("600"))
        assertEquals("six hundred thousand", ItnTextNormalizer.digitsToWords("600000"))
        assertEquals("one thousand", ItnTextNormalizer.digitsToWords("1000"))
        assertEquals("twenty thousand", ItnTextNormalizer.digitsToWords("20000"))
        assertEquals("zero", ItnTextNormalizer.digitsToWords("0"))
        assertEquals("fifteen", ItnTextNormalizer.digitsToWords("15"))
        assertEquals("forty two", ItnTextNormalizer.digitsToWords("42"))
        assertEquals("one million", ItnTextNormalizer.digitsToWords("1000000"))
    }

    @Test
    fun testWordsToDigits_CardinalPhrases() {
        assertEquals("600000", ItnTextNormalizer.wordsToDigits("six hundred thousand"))
        assertEquals("600", ItnTextNormalizer.wordsToDigits("six hundred"))
        assertEquals("1000", ItnTextNormalizer.wordsToDigits("one thousand"))
        assertEquals("20000", ItnTextNormalizer.wordsToDigits("twenty thousand"))
    }

    @Test
    fun testNormalize_WithPunctuationAroundNumbers() {
        assertEquals(
            "there were six hundred thousand birds",
            ItnTextNormalizer.normalize("There were 600,000 birds.")
        )
        assertEquals(
            "cost is one thousand dollars",
            ItnTextNormalizer.normalize("Cost is ($1,000) dollars!")
        )
        assertEquals(
            "twenty thousand items sold",
            ItnTextNormalizer.normalize("20,000 items - sold!")
        )
    }

    @Test
    fun testNormalize_UnrelatedWordsPreserved() {
        val text = "The quick brown fox jumps over the lazy dog"
        assertEquals(
            "the quick brown fox jumps over the lazy dog",
            ItnTextNormalizer.normalize(text)
        )

        val text2 = "property had birds on it last year"
        assertEquals(
            "property had birds on it last year",
            ItnTextNormalizer.normalize(text2)
        )
    }

    @Test
    fun testSymmetricalEquivalence() {
        val ref = "had 600000 birds"
        val hyp = "had six hundred thousand birds"

        val itnResult = ItnWerCalculator.calculate(ref, hyp)
        assertTrue(itnResult.isExactMatch)
        assertEquals(0.0, itnResult.wordStats.errorRate, 1e-9)
        assertEquals(0.0, itnResult.charStats.errorRate, 1e-9)
        assertEquals(5, itnResult.wordStats.referenceCount) // "had six hundred thousand birds" = 5 words
    }

    @Test
    fun testEmptyAndNullInputs() {
        assertEquals("", ItnTextNormalizer.normalize(null))
        assertEquals("", ItnTextNormalizer.normalize(""))
        assertEquals("", ItnTextNormalizer.normalize("   "))

        val result = ItnWerCalculator.calculate("", "")
        assertTrue(result.isExactMatch)
        assertEquals(0.0, result.wordStats.errorRate, 1e-9)
    }
}
