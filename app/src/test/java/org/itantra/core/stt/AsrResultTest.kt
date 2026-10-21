package org.itantra.core.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test for AsrResult data structure and invariants.
 */
class AsrResultTest {

    @Test
    fun testAsrResultFields() {
        val result = AsrResult(
            text = "Hello world",
            language = "en",
            confidence = null,
            inferenceTimeMs = 245L,
            audioDurationMs = 2000L
        )

        assertEquals("Hello world", result.text)
        assertEquals("en", result.language)
        assertNull("Confidence must be null until defensible scoring is implemented", result.confidence)
        assertEquals(245L, result.inferenceTimeMs)
        assertEquals(2000L, result.audioDurationMs)
    }

    @Test
    fun testShortArrayToFloatArrayConversionInvariant() {
        val shortSamples = shortArrayOf(0, 32767, -32768, 16384, -16384)
        val floatSamples = FloatArray(shortSamples.size) { i ->
            shortSamples[i] / 32768.0f
        }

        assertEquals(0.0f, floatSamples[0], 1e-5f)
        assertTrue("Max positive sample should be ~0.99997f", floatSamples[1] in 0.999f..1.0f)
        assertEquals(-1.0f, floatSamples[2], 1e-5f)
        assertEquals(0.5f, floatSamples[3], 1e-5f)
        assertEquals(-0.5f, floatSamples[4], 1e-5f)
    }
}
