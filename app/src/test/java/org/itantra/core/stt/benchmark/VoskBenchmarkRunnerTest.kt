package org.itantra.core.stt.benchmark

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for Vosk benchmark data structures and scoring integration.
 */
class VoskBenchmarkRunnerTest {

    @Test
    fun testVoskJsonParsingAndScoring() {
        val text = "कुछ अरु में आज तक केंद्रक होता है जिसका मतलब यह कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"
        val ref = "कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,"
        val werResult = IndicWerCalculator.calculate(ref, text, normalize = true)

        assertFalse(werResult.isExactMatch)
        assertEquals(24, werResult.wordStats.referenceCount)
        assertEquals(21, werResult.wordStats.hits)
        assertEquals(2, werResult.wordStats.substitutions)
        assertEquals(1, werResult.wordStats.deletions)
        assertEquals(1, werResult.wordStats.insertions)
        assertEquals(4.0 / 24.0, werResult.wordStats.errorRate, 1e-4) // 16.67%
        assertEquals(11.0 / 93.0, werResult.charStats.errorRate, 1e-4) // 11.83%
    }
}
