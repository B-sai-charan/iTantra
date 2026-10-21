package org.itantra.core.stt

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for language resolution semantics in [SherpaWhisperAsrEngine].
 */
class SherpaWhisperAsrEngineTest {

    @Test
    fun testForcedHindi_whenModelDetectsEnglish_preservesHindi() {
        // Case 1: Explicit targetLanguage = "hi", model reports "en"
        val resolvedWithRequested = SherpaWhisperAsrEngine.resolveResultLanguage(
            requestedLanguage = "hi",
            defaultLanguage = "en",
            detectedLanguage = "en"
        )
        assertEquals("hi", resolvedWithRequested)

        val resolvedWithDefault = SherpaWhisperAsrEngine.resolveResultLanguage(
            requestedLanguage = null,
            defaultLanguage = "hi",
            detectedLanguage = "en"
        )
        assertEquals("hi", resolvedWithDefault)
    }

    @Test
    fun testForcedEnglish_whenModelDetectsHindi_preservesEnglish() {
        // Case 2: Explicit targetLanguage = "en", model reports "hi"
        val resolvedWithRequested = SherpaWhisperAsrEngine.resolveResultLanguage(
            requestedLanguage = "en",
            defaultLanguage = "hi",
            detectedLanguage = "hi"
        )
        assertEquals("en", resolvedWithRequested)

        val resolvedWithDefault = SherpaWhisperAsrEngine.resolveResultLanguage(
            requestedLanguage = null,
            defaultLanguage = "en",
            detectedLanguage = "hi"
        )
        assertEquals("en", resolvedWithDefault)
    }

    @Test
    fun testAutoDetect_whenModelDetectsHindi_returnsHindi() {
        // Case 3: Auto-detect (null or "auto"), model reports "hi"
        val resolvedNull = SherpaWhisperAsrEngine.resolveResultLanguage(
            requestedLanguage = null,
            defaultLanguage = null,
            detectedLanguage = "hi"
        )
        assertEquals("hi", resolvedNull)

        val resolvedAuto = SherpaWhisperAsrEngine.resolveResultLanguage(
            requestedLanguage = "auto",
            defaultLanguage = "auto",
            detectedLanguage = "hi"
        )
        assertEquals("hi", resolvedAuto)
    }

    @Test
    fun testAutoDetect_whenModelDetectsEnglish_returnsEnglish() {
        // Case 4: Auto-detect (null or "auto"), model reports "en"
        val resolvedNull = SherpaWhisperAsrEngine.resolveResultLanguage(
            requestedLanguage = null,
            defaultLanguage = null,
            detectedLanguage = "en"
        )
        assertEquals("en", resolvedNull)

        val resolvedAuto = SherpaWhisperAsrEngine.resolveResultLanguage(
            requestedLanguage = "auto",
            defaultLanguage = "auto",
            detectedLanguage = "en"
        )
        assertEquals("en", resolvedAuto)
    }

    @Test
    fun testAutoDetect_whenModelReturnsBlank_fallsBackToEnglish() {
        val resolved = SherpaWhisperAsrEngine.resolveResultLanguage(
            requestedLanguage = null,
            defaultLanguage = null,
            detectedLanguage = ""
        )
        assertEquals("en", resolved)
    }
}
