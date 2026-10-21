package org.itantra.core.stt.benchmark

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for TextNormalizer verifying deterministic text normalization rules.
 */
class TextNormalizerTest {

    @Test
    fun testExactMatchUnchangedWhenAlreadyNormalized() {
        val input = "hello world"
        val normalized = TextNormalizer.normalize(input)
        assertEquals("hello world", normalized)
    }

    @Test
    fun testLowercaseConversion() {
        val input = "HELLO WORLD"
        val normalized = TextNormalizer.normalize(input)
        assertEquals("hello world", normalized)
    }

    @Test
    fun testPunctuationRemoval() {
        val input = "Hello, World!"
        val normalized = TextNormalizer.normalize(input)
        assertEquals("hello world", normalized)
    }

    @Test
    fun testMixedPunctuationAndSymbols() {
        val input = "  It's... AMAZING! (Right?) #100%  "
        val normalized = TextNormalizer.normalize(input)
        assertEquals("its amazing right 100", normalized)
    }

    @Test
    fun testRepeatedWhitespaceAndTabsAndNewlines() {
        val input = "hello \t\n   world \r\n test"
        val normalized = TextNormalizer.normalize(input)
        assertEquals("hello world test", normalized)
    }

    @Test
    fun testLeadingAndTrailingWhitespace() {
        val input = "   hello world   "
        val normalized = TextNormalizer.normalize(input)
        assertEquals("hello world", normalized)
    }

    @Test
    fun testAlphanumericPreservation() {
        val input = "Order 12345 was received at 10am."
        val normalized = TextNormalizer.normalize(input)
        assertEquals("order 12345 was received at 10am", normalized)
    }

    @Test
    fun testNullAndEmptyInputs() {
        assertEquals("", TextNormalizer.normalize(null))
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals("", TextNormalizer.normalize("   \t\n   "))
        assertEquals("", TextNormalizer.normalize("!@#$%^&*()"))
    }
}
