package org.itantra.core.stt.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for WerCalculator verifying Word Error Rate (WER),
 * Character Error Rate (CER), substitutions, deletions, insertions,
 * exact match, and edge cases.
 */
class WerCalculatorTest {

    @Test
    fun test1_ExactMatch() {
        val ref = "Hello world"
        val hyp = "Hello world"

        val result = WerCalculator.calculate(ref, hyp)

        assertTrue(result.isExactMatch)
        assertEquals("hello world", result.normalizedReference)
        assertEquals("hello world", result.normalizedHypothesis)

        // Word-level stats
        assertEquals(0, result.wordStats.substitutions)
        assertEquals(0, result.wordStats.deletions)
        assertEquals(0, result.wordStats.insertions)
        assertEquals(2, result.wordStats.hits)
        assertEquals(2, result.wordStats.referenceCount)
        assertEquals(0, result.wordStats.errorCount)
        assertEquals(0.0, result.wordStats.errorRate, 1e-9)

        // Char-level stats ("helloworld" = 10 chars)
        assertEquals(0, result.charStats.substitutions)
        assertEquals(0, result.charStats.deletions)
        assertEquals(0, result.charStats.insertions)
        assertEquals(10, result.charStats.hits)
        assertEquals(10, result.charStats.referenceCount)
        assertEquals(0, result.charStats.errorCount)
        assertEquals(0.0, result.charStats.errorRate, 1e-9)
    }

    @Test
    fun test2_CapitalizationAndPunctuationNormalization() {
        val ref = "Hello, World!"
        val hyp = "hello world"

        val result = WerCalculator.calculate(ref, hyp)

        assertTrue("Normalized strings must be identical", result.isExactMatch)
        assertEquals("hello world", result.normalizedReference)
        assertEquals("hello world", result.normalizedHypothesis)
        assertEquals(0.0, result.wordStats.errorRate, 1e-9)
        assertEquals(0.0, result.charStats.errorRate, 1e-9)
    }

    @Test
    fun test3_OneSubstitution() {
        val ref = "hello world"
        val hyp = "hello earth"

        val result = WerCalculator.calculate(ref, hyp)

        assertFalse(result.isExactMatch)
        assertEquals(1, result.wordStats.substitutions)
        assertEquals(0, result.wordStats.deletions)
        assertEquals(0, result.wordStats.insertions)
        assertEquals(1, result.wordStats.hits)
        assertEquals(2, result.wordStats.referenceCount)
        assertEquals(1, result.wordStats.errorCount)
        assertEquals(0.5, result.wordStats.errorRate, 1e-9)
    }

    @Test
    fun test4_OneDeletion() {
        val ref = "hello world"
        val hyp = "hello"

        val result = WerCalculator.calculate(ref, hyp)

        assertFalse(result.isExactMatch)
        assertEquals(0, result.wordStats.substitutions)
        assertEquals(1, result.wordStats.deletions)
        assertEquals(0, result.wordStats.insertions)
        assertEquals(1, result.wordStats.hits)
        assertEquals(2, result.wordStats.referenceCount)
        assertEquals(1, result.wordStats.errorCount)
        assertEquals(0.5, result.wordStats.errorRate, 1e-9)
    }

    @Test
    fun test5_OneInsertion() {
        val ref = "hello world"
        val hyp = "hello big world"

        val result = WerCalculator.calculate(ref, hyp)

        assertFalse(result.isExactMatch)
        assertEquals(0, result.wordStats.substitutions)
        assertEquals(0, result.wordStats.deletions)
        assertEquals(1, result.wordStats.insertions)
        assertEquals(2, result.wordStats.hits)
        assertEquals(2, result.wordStats.referenceCount)
        assertEquals(1, result.wordStats.errorCount)
        assertEquals(0.5, result.wordStats.errorRate, 1e-9)
    }

    @Test
    fun test6_EmptyHypothesis() {
        val ref = "hello world"
        val hyp = ""

        val result = WerCalculator.calculate(ref, hyp)

        assertFalse(result.isExactMatch)
        assertEquals(0, result.wordStats.substitutions)
        assertEquals(2, result.wordStats.deletions)
        assertEquals(0, result.wordStats.insertions)
        assertEquals(0, result.wordStats.hits)
        assertEquals(2, result.wordStats.referenceCount)
        assertEquals(2, result.wordStats.errorCount)
        assertEquals(1.0, result.wordStats.errorRate, 1e-9)
    }

    @Test
    fun test7_EmptyReference_ExplicitBehavior() {
        // Subcase 7A: Both reference and hypothesis empty -> Perfect match, 0 error rate
        val resultBothEmpty = WerCalculator.calculate("", "")
        assertTrue(resultBothEmpty.isExactMatch)
        assertEquals(0, resultBothEmpty.wordStats.referenceCount)
        assertEquals(0, resultBothEmpty.wordStats.errorCount)
        assertEquals(0.0, resultBothEmpty.wordStats.errorRate, 1e-9)

        // Subcase 7B: Reference empty, Hypothesis non-empty -> Pure insertions, rate mapped to 1.0 (no div by zero)
        val resultRefEmpty = WerCalculator.calculate("", "hello world")
        assertFalse(resultRefEmpty.isExactMatch)
        assertEquals(0, resultRefEmpty.wordStats.referenceCount)
        assertEquals(2, resultRefEmpty.wordStats.insertions)
        assertEquals(2, resultRefEmpty.wordStats.errorCount)
        assertEquals(1.0, resultRefEmpty.wordStats.errorRate, 1e-9)
    }

    @Test
    fun test8_CharacterLevelCer() {
        // Substitution: "cat" -> "hat" (3 chars, 1 substitution)
        val resultSub = WerCalculator.calculate("cat", "hat")
        assertEquals(1, resultSub.charStats.substitutions)
        assertEquals(0, resultSub.charStats.deletions)
        assertEquals(0, resultSub.charStats.insertions)
        assertEquals(2, resultSub.charStats.hits)
        assertEquals(3, resultSub.charStats.referenceCount)
        assertEquals(1.0 / 3.0, resultSub.charStats.errorRate, 1e-6)

        // Insertion: "cat" -> "cats" (3 chars, 1 insertion)
        val resultIns = WerCalculator.calculate("cat", "cats")
        assertEquals(0, resultIns.charStats.substitutions)
        assertEquals(0, resultIns.charStats.deletions)
        assertEquals(1, resultIns.charStats.insertions)
        assertEquals(3, resultIns.charStats.hits)
        assertEquals(3, resultIns.charStats.referenceCount)
        assertEquals(1.0 / 3.0, resultIns.charStats.errorRate, 1e-6)

        // Deletion: "cat" -> "ca" (3 chars, 1 deletion)
        val resultDel = WerCalculator.calculate("cat", "ca")
        assertEquals(0, resultDel.charStats.substitutions)
        assertEquals(1, resultDel.charStats.deletions)
        assertEquals(0, resultDel.charStats.insertions)
        assertEquals(2, resultDel.charStats.hits)
        assertEquals(3, resultDel.charStats.referenceCount)
        assertEquals(1.0 / 3.0, resultDel.charStats.errorRate, 1e-6)
    }

    @Test
    fun test9_ComplexSentenceEvaluation() {
        val ref = "I need to send a message to my brother because he is waiting for me."
        val hyp = "I need to send a message to my brother because he is waiting for me"

        val result = WerCalculator.calculate(ref, hyp)

        assertTrue("Should be exact match after punctuation removal", result.isExactMatch)
        assertEquals(0.0, result.wordStats.errorRate, 1e-9)
        assertEquals(0.0, result.charStats.errorRate, 1e-9)
        assertEquals(15, result.wordStats.referenceCount)
    }
}
