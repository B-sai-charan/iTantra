package org.itantra.core.domain

import org.itantra.core.stt.AsrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying semantic contract and behavior of [SpeechMessage] and [SpeechMessageMapper].
 */
class SpeechMessageTest {

    @Test
    fun testEnglishAsrResultMapping() {
        val asrResult = AsrResult(
            text = "After early nightfall, the yellow lamps would light up.",
            language = "en",
            confidence = 0.95f,
            inferenceTimeMs = 1200L,
            audioDurationMs = 3500L
        )
        val fixedTimestamp = 1726850000000L

        val message = SpeechMessageMapper.fromAsrResult(asrResult, timestamp = fixedTimestamp)

        assertEquals("After early nightfall, the yellow lamps would light up.", message.text)
        assertEquals("en", message.language)
        assertEquals(fixedTimestamp, message.timestamp)
        assertEquals(0.95f, message.confidence)
    }

    @Test
    fun testHindiAsrResultMapping() {
        val hindiText = "कुछ अणुओं में अस्थिर केंद्रक होता है"
        val asrResult = AsrResult(
            text = hindiText,
            language = "hi",
            confidence = null,
            inferenceTimeMs = 2100L,
            audioDurationMs = 4200L
        )
        val fixedTimestamp = 1726850050000L

        val message = asrResult.toSpeechMessage(timestamp = fixedTimestamp)

        assertEquals(hindiText, message.text)
        assertEquals("hi", message.language)
        assertEquals(fixedTimestamp, message.timestamp)
        assertNull(message.confidence)
    }

    @Test
    fun testNullConfidencePreservation() {
        val asrResult = AsrResult(
            text = "Testing null confidence preservation",
            language = "en",
            confidence = null,
            inferenceTimeMs = 500L,
            audioDurationMs = 2000L
        )

        val message = asrResult.toSpeechMessage(timestamp = 1000L)

        assertNull("Confidence must remain null when uncomputed by STT", message.confidence)
    }

    @Test
    fun testNonNullConfidencePreservation() {
        val asrResult = AsrResult(
            text = "Testing non-null confidence",
            language = "en",
            confidence = 0.88f,
            inferenceTimeMs = 500L,
            audioDurationMs = 2000L
        )

        val message = asrResult.toSpeechMessage(timestamp = 1000L)

        assertEquals(0.88f, message.confidence!!, 0.0001f)
    }

    @Test
    fun testNoTextMutationOrTransformation() {
        val specialTexts = listOf(
            "",
            "   \n\t  ",
            "Punctuation! @#\$%^&*()",
            "Mixed English and हिन्दी script 123",
            "ALL CAPS TEXT",
            "lowercase text"
        )

        for (text in specialTexts) {
            val asrResult = AsrResult(
                text = text,
                language = "en",
                confidence = null,
                inferenceTimeMs = 100L,
                audioDurationMs = 1000L
            )
            val message = asrResult.toSpeechMessage(timestamp = 2000L)
            assertEquals("Text must not undergo any trimming, lowercasing, or mutation", text, message.text)
        }
    }

    @Test
    fun testLanguagePreservedExactlyWithoutMappingOrNormalization() {
        val languages = listOf("en", "hi", "te", "ta", "mr", "gu", "bn", "en-US", "hi_IN", "UNKNOWN")
        for (lang in languages) {
            val asrResult = AsrResult(
                text = "Sample",
                language = lang,
                confidence = null,
                inferenceTimeMs = 100L,
                audioDurationMs = 500L
            )
            val message = asrResult.toSpeechMessage(timestamp = 100L)
            assertEquals("Language code must be preserved verbatim", lang, message.language)
        }
    }

    @Test
    fun testExplicitTimestampPropagation() {
        val asrResult = AsrResult(
            text = "Timestamp verification",
            language = "en",
            confidence = null,
            inferenceTimeMs = 300L,
            audioDurationMs = 1500L
        )

        val explicitTimestamp = 1700000000123L
        val message = SpeechMessageMapper.fromAsrResult(asrResult, timestamp = explicitTimestamp)

        assertEquals(explicitTimestamp, message.timestamp)
    }

    @Test
    fun testDefaultTimestampBoundaryAcquisition() {
        val before = System.currentTimeMillis()
        val asrResult = AsrResult(
            text = "Default timestamp check",
            language = "en",
            confidence = null,
            inferenceTimeMs = 100L,
            audioDurationMs = 500L
        )
        val message = asrResult.toSpeechMessage()
        val after = System.currentTimeMillis()

        assertTrue("Timestamp should be >= before time", message.timestamp >= before)
        assertTrue("Timestamp should be <= after time", message.timestamp <= after)
    }

    @Test
    fun testImmutabilityAndDataClassContract() {
        val msg1 = SpeechMessage(
            text = "Hello world",
            language = "en",
            timestamp = 1000L,
            confidence = 0.9f
        )

        val msg2 = msg1.copy()
        assertEquals(msg1, msg2)
        assertEquals(msg1.hashCode(), msg2.hashCode())

        val modified = msg1.copy(text = "Updated")
        assertEquals("Updated", modified.text)
        assertEquals(msg1.language, modified.language)
        assertEquals(msg1.timestamp, modified.timestamp)
        assertEquals(msg1.confidence, modified.confidence)
        assertNotEquals(msg1, modified)
    }

    @Test
    fun testMapperDeterminism() {
        val asrResult = AsrResult(
            text = "Deterministic test",
            language = "en",
            confidence = 0.75f,
            inferenceTimeMs = 400L,
            audioDurationMs = 1600L
        )
        val ts = 1720000000000L

        val msg1 = SpeechMessageMapper.fromAsrResult(asrResult, timestamp = ts)
        val msg2 = asrResult.toSpeechMessage(timestamp = ts)

        assertEquals(msg1, msg2)
    }
}
