package org.itantra.core.stt.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [IndicWerCalculator] verifying Levenshtein WER and code-point CER calculations.
 */
class IndicWerCalculatorTest {

    @Test
    fun testExactMatchZeroError() {
        val ref = "यह एक हिंदी वाक्य है।"
        val hyp = "यह एक हिंदी वाक्य है"
        val result = IndicWerCalculator.calculate(ref, hyp, normalize = true)

        assertTrue(result.isExactMatch)
        assertEquals(0, result.wordStats.errorCount)
        assertEquals(0.0, result.wordStats.errorRate, 0.0001)
        assertEquals(5, result.wordStats.referenceCount)
        assertEquals(0, result.charStats.errorCount)
        assertEquals(0.0, result.charStats.errorRate, 0.0001)
    }

    @Test
    fun testSingleWordSubstitution() {
        val ref = "भारत देश महान है"
        val hyp = "भारत देश सुंदर है"
        val result = IndicWerCalculator.calculate(ref, hyp, normalize = true)

        assertFalse(result.isExactMatch)
        assertEquals(1, result.wordStats.substitutions)
        assertEquals(0, result.wordStats.deletions)
        assertEquals(0, result.wordStats.insertions)
        assertEquals(3, result.wordStats.hits)
        assertEquals(4, result.wordStats.referenceCount)
        assertEquals(0.25, result.wordStats.errorRate, 0.0001)
    }

    @Test
    fun testPureWordDeletion() {
        val ref = "पहला दूसरा तीसरा"
        val hyp = "पहला तीसरा"
        val result = IndicWerCalculator.calculate(ref, hyp, normalize = true)

        assertFalse(result.isExactMatch)
        assertEquals(0, result.wordStats.substitutions)
        assertEquals(1, result.wordStats.deletions)
        assertEquals(0, result.wordStats.insertions)
        assertEquals(2, result.wordStats.hits)
        assertEquals(3, result.wordStats.referenceCount)
        assertEquals(1.0 / 3.0, result.wordStats.errorRate, 0.0001)
    }

    @Test
    fun testPureWordInsertion() {
        val ref = "पहला दूसरा तीसरा"
        val hyp = "पहला दूसरा तीसरा चौथा"
        val result = IndicWerCalculator.calculate(ref, hyp, normalize = true)

        assertFalse(result.isExactMatch)
        assertEquals(0, result.wordStats.substitutions)
        assertEquals(0, result.wordStats.deletions)
        assertEquals(1, result.wordStats.insertions)
        assertEquals(3, result.wordStats.hits)
        assertEquals(3, result.wordStats.referenceCount)
        assertEquals(1.0 / 3.0, result.wordStats.errorRate, 0.0001)
    }

    @Test
    fun testEmptyReferenceAndEmptyHypothesis() {
        val result = IndicWerCalculator.calculate("", "", normalize = true)

        assertTrue(result.isExactMatch)
        assertEquals(0, result.wordStats.referenceCount)
        assertEquals(0, result.wordStats.errorCount)
        assertEquals(0.0, result.wordStats.errorRate, 0.0001)
        assertEquals(0, result.charStats.referenceCount)
        assertEquals(0, result.charStats.errorCount)
        assertEquals(0.0, result.charStats.errorRate, 0.0001)
    }

    @Test
    fun testEmptyReferenceWithNonEmptyHypothesis() {
        val result = IndicWerCalculator.calculate("", "अतिरिक्त शब्द", normalize = true)

        assertFalse(result.isExactMatch)
        assertEquals(0, result.wordStats.referenceCount)
        assertEquals(2, result.wordStats.insertions)
        assertEquals(2, result.wordStats.errorCount)
        assertEquals(1.0, result.wordStats.errorRate, 0.0001) // 100% insertion error
    }

    @Test
    fun testNonEmptyReferenceWithEmptyHypothesis() {
        val result = IndicWerCalculator.calculate("मूल पाठ", "", normalize = true)

        assertFalse(result.isExactMatch)
        assertEquals(2, result.wordStats.referenceCount)
        assertEquals(2, result.wordStats.deletions)
        assertEquals(2, result.wordStats.errorCount)
        assertEquals(1.0, result.wordStats.errorRate, 0.0001) // 100% deletion error
    }

    @Test
    fun testVerifiedFleursHindiFixtureScoring() {
        val fleursRef = "कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,"
        val perfectHyp = "कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"

        val result = IndicWerCalculator.calculate(fleursRef, perfectHyp, normalize = true)

        assertTrue("Should be exact match after punctuation removal", result.isExactMatch)
        assertEquals(24, result.wordStats.referenceCount)
        assertEquals(24, result.wordStats.hits)
        assertEquals(0, result.wordStats.errorCount)
        assertEquals(0.0, result.wordStats.errorRate, 0.0001)
        assertEquals(0.0, result.charStats.errorRate, 0.0001)
    }
}
