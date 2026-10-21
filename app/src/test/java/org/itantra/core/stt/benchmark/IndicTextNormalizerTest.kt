package org.itantra.core.stt.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

/**
 * Comprehensive Unicode-safety and deterministic behavior tests for [IndicTextNormalizer].
 */
class IndicTextNormalizerTest {

    @Test
    fun testHindiTextRemainsIntact() {
        val input = "यह एक हिंदी वाक्य है"
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("यह एक हिंदी वाक्य है", normalized)
    }

    @Test
    fun testDevanagariMatrasRemainIntact() {
        // Contains short and long i, u, e, ai, o, au matras: कि की कु कू के कै को कौ
        val input = "कि की कु कू के कै को कौ कृपा"
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("कि की कु कू के कै को कौ कृपा", normalized)
    }

    @Test
    fun testViramaHalantRemainsIntact() {
        // Contains conjuncts with virama/halant: क्या, विद्या, शास्त्र, प्रवृत्ति
        val input = "क्या विद्या शास्त्र प्रवृत्ति"
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("क्या विद्या शास्त्र प्रवृत्ति", normalized)
    }

    @Test
    fun testNuktaRemainsIntact() {
        // Contains nukta characters: क़, ख़, ग़, ज़, ड़, ढ़, फ़
        val input = "क़लम ख़ास ग़ज़ल ज़मीन पेड़ पढ़ना फ़िल्म"
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("क़लम ख़ास ग़ज़ल ज़मीन पेड़ पढ़ना फ़िल्म", normalized)
    }

    @Test
    fun testAnusvaraRemainsIntact() {
        // Contains anusvara: केंद्र, पतंग, संबंध
        val input = "केंद्र पतंग संबंध"
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("केंद्र पतंग संबंध", normalized)
    }

    @Test
    fun testCandrabinduRemainsIntact() {
        // Contains candrabindu: माँ, आँख, चाँद, गाँव
        val input = "माँ आँख चाँद गाँव"
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("माँ आँख चाँद गाँव", normalized)
    }

    @Test
    fun testDevanagariDandaIsRemoved() {
        // Single danda '।' and double danda '॥'
        val input = "होती है। प्रथम चरण समाप्त॥"
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("होती है प्रथम चरण समाप्त", normalized)
    }

    @Test
    fun testAsciiPunctuationIsRemoved() {
        val input = "नमस्ते, दुनिया! क्या हाल है? (सब ठीक है)."
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("नमस्ते दुनिया क्या हाल है सब ठीक है", normalized)
    }

    @Test
    fun testRepeatedWhitespaceCollapses() {
        val input = "कुछ   अणुओं   \t\n  में   अस्थिर"
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("कुछ अणुओं में अस्थिर", normalized)
    }

    @Test
    fun testLeadingAndTrailingWhitespaceRemoved() {
        val input = "   \t केंद्रक होता है \n   "
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("केंद्रक होता है", normalized)
    }

    @Test
    fun testNfcNormalizationIsDeterministic() {
        // Decomposed NFD string normalized to NFC
        val nfdText = Normalizer.normalize("पेड़ और फिल्म", Normalizer.Form.NFD)
        val normalized = IndicTextNormalizer.normalize(nfdText)
        val expectedNfc = Normalizer.normalize("पेड़ और फिल्म", Normalizer.Form.NFC)
        assertEquals(expectedNfc, normalized)
    }

    @Test
    fun testEnglishTextNormalizesSensibly() {
        val input = "Hello, World! This is a TEST."
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("hello world this is a test", normalized)
    }

    @Test
    fun testNumericDigitsRemainDigits() {
        val input = "साल 2026 में 100 प्रतिशत सफलता (१२३४५)"
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("साल 2026 में 100 प्रतिशत सफलता १२३४५", normalized)
    }

    @Test
    fun testMixedHindiAndEnglishText() {
        val input = "AI4Bharat का IndicSTT मॉडल बहुत Fast है!"
        val normalized = IndicTextNormalizer.normalize(input)
        assertEquals("ai4bharat का indicstt मॉडल बहुत fast है", normalized)
    }

    @Test
    fun testEmptyAndBlankInputs() {
        assertEquals("", IndicTextNormalizer.normalize(null))
        assertEquals("", IndicTextNormalizer.normalize(""))
        assertEquals("", IndicTextNormalizer.normalize("   \t\n   "))
        assertEquals("", IndicTextNormalizer.normalize("!@#$%^&*()_+-=~`|\\:;\"'<>,.?/॥।"))
    }

    @Test
    fun testVerifiedFleursHindiFixtureNormalization() {
        val fleursRef = "कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,"
        val normalized = IndicTextNormalizer.normalize(fleursRef)

        assertFalse("Normalized text must not be empty", normalized.isEmpty())
        assertFalse("Normalized text must not contain commas", normalized.contains(","))

        val expected = "कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"
        assertEquals(expected, normalized)

        val tokens = normalized.split(" ")
        assertEquals("Must produce exactly 24 whitespace-delimited word tokens", 24, tokens.size)
        assertEquals("कुछ", tokens[0])
        assertEquals("अणुओं", tokens[1])
        assertEquals("है", tokens[tokens.size - 1])
    }
}
