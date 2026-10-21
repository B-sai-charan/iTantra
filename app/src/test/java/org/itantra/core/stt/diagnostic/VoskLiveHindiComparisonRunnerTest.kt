package org.itantra.core.stt.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.itantra.core.stt.benchmark.IndicWerCalculator

/**
 * Unit tests for VoskLiveHindiComparisonRunner scoring and classification logic.
 */
class VoskLiveHindiComparisonRunnerTest {

    @Test
    fun testEmptyHypothesisScoring() {
        val ref = "नमस्ते, यह iTantra का हिंदी परीक्षण है।"
        val hyp = ""
        val werResult = IndicWerCalculator.calculate(ref, hyp, normalize = true)

        assertFalse(werResult.isExactMatch)
        assertEquals(7, werResult.wordStats.referenceCount)
        assertEquals(0, werResult.wordStats.hits)
        assertEquals(7, werResult.wordStats.deletions)
        assertEquals(0, werResult.wordStats.substitutions)
        assertEquals(0, werResult.wordStats.insertions)
        assertEquals(1.0, werResult.wordStats.errorRate, 1e-4) // 100.0% WER
        assertEquals(1.0, werResult.charStats.errorRate, 1e-4) // 100.0% CER
    }

    @Test
    fun testPrescribedHindiReference() {
        assertEquals("नमस्ते, यह iTantra का हिंदी परीक्षण है।", VoskLiveHindiComparisonRunner.PRESCRIBED_HINDI_REFERENCE)
        assertEquals("live_hindi_forensic_01.wav", VoskLiveHindiComparisonRunner.SOURCE_WAV_NAME)
    }
}
