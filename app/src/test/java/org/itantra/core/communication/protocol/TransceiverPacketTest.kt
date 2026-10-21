package org.itantra.core.communication.protocol

import org.itantra.core.domain.SpeechMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit test suite for [TransceiverPacket], [LanguageRegistry], [ConfidenceQuantizer],
 * and [TransceiverPacketMapper].
 */
class TransceiverPacketTest {

    // ==========================================
    // 1. LANGUAGE REGISTRY & EXTENDED BCP-47 TESTS
    // ==========================================

    @Test
    fun testEnglishLanguageMapping() {
        val (lang1, ext1) = ProtocolLanguage.resolve("en")
        assertEquals(ProtocolLanguage.ENGLISH, lang1)
        assertNull(ext1)

        val (lang2, ext2) = ProtocolLanguage.resolve("EN")
        assertEquals(ProtocolLanguage.ENGLISH, lang2)
        assertNull(ext2)

        // Regional English variants must be preserved as EXTENDED_BCP47 without mutation
        val (lang3, ext3) = ProtocolLanguage.resolve("en-US")
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, lang3)
        assertEquals("en-US", ext3)

        val (lang4, ext4) = ProtocolLanguage.resolve("en_GB")
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, lang4)
        assertEquals("en_GB", ext4)

        val (lang5, ext5) = ProtocolLanguage.resolve("en-GB")
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, lang5)
        assertEquals("en-GB", ext5)
    }

    @Test
    fun testHindiLanguageMapping() {
        val (lang1, ext1) = ProtocolLanguage.resolve("hi")
        assertEquals(ProtocolLanguage.HINDI, lang1)
        assertNull(ext1)

        val (lang2, ext2) = ProtocolLanguage.resolve("HI")
        assertEquals(ProtocolLanguage.HINDI, lang2)
        assertNull(ext2)

        // Regional Hindi variants must be preserved as EXTENDED_BCP47 without mutation
        val (lang3, ext3) = ProtocolLanguage.resolve("hi-IN")
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, lang3)
        assertEquals("hi-IN", ext3)

        val (lang4, ext4) = ProtocolLanguage.resolve("hi_IN")
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, lang4)
        assertEquals("hi_IN", ext4)
    }

    @Test
    fun testMajorIndicLanguageMappings() {
        val (teLang, teExt) = ProtocolLanguage.resolve("te")
        assertEquals(ProtocolLanguage.TELUGU, teLang)
        assertNull(teExt)

        val (taLang, taExt) = ProtocolLanguage.resolve("ta")
        assertEquals(ProtocolLanguage.TAMIL, taLang)
        assertNull(taExt)

        val (mrLang, mrExt) = ProtocolLanguage.resolve("mr")
        assertEquals(ProtocolLanguage.MARATHI, mrLang)
        assertNull(mrExt)

        val (bnLang, bnExt) = ProtocolLanguage.resolve("bn")
        assertEquals(ProtocolLanguage.BENGALI, bnLang)
        assertNull(bnExt)
    }

    @Test
    fun testUndefinedLanguageHandling() {
        val (emptyLang, emptyExt) = ProtocolLanguage.resolve("")
        assertEquals(ProtocolLanguage.UNDEFINED, emptyLang)
        assertNull(emptyExt)

        val (blankLang, blankExt) = ProtocolLanguage.resolve("   ")
        assertEquals(ProtocolLanguage.UNDEFINED, blankLang)
        assertNull(blankExt)

        val (undLang, undExt) = ProtocolLanguage.resolve("und")
        assertEquals(ProtocolLanguage.UNDEFINED, undLang)
        assertNull(undExt)

        val (unkLang, unkExt) = ProtocolLanguage.resolve("unknown")
        assertEquals(ProtocolLanguage.UNDEFINED, unkLang)
        assertNull(unkExt)
    }

    @Test
    fun testExtendedLanguageTagPreservation() {
        // Unregistered language tags and regional variants MUST map to EXTENDED_BCP47 and preserve the original tag
        val (frLang, frExt) = ProtocolLanguage.resolve("fr")
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, frLang)
        assertEquals("fr", frExt)

        val (deLang, deExt) = ProtocolLanguage.resolve("de")
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, deLang)
        assertEquals("de", deExt)

        val (esLang, esExt) = ProtocolLanguage.resolve("es-ES")
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, esLang)
        assertEquals("es-ES", esExt)

        val (jpLang, jpExt) = ProtocolLanguage.resolve("ja-JP")
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, jpLang)
        assertEquals("ja-JP", jpExt)

        // Verbatim distinction between hyphen and underscore delimiters
        val (enGbHyphen, extGbHyphen) = ProtocolLanguage.resolve("en-GB")
        val (enGbUnder, extGbUnder) = ProtocolLanguage.resolve("en_GB")
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, enGbHyphen)
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, enGbUnder)
        assertEquals("en-GB", extGbHyphen)
        assertEquals("en_GB", extGbUnder)
        assertTrue("Hyphen and underscore forms must remain distinct verbatim strings", extGbHyphen != extGbUnder)
    }

    @Test
    fun testLanguageFromIdLookup() {
        assertEquals(ProtocolLanguage.ENGLISH, ProtocolLanguage.fromId(0x01))
        assertEquals(ProtocolLanguage.HINDI, ProtocolLanguage.fromId(0x02))
        assertEquals(ProtocolLanguage.UNDEFINED, ProtocolLanguage.fromId(0x00))
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, ProtocolLanguage.fromId(0xFE))
        assertEquals(ProtocolLanguage.UNDEFINED, ProtocolLanguage.fromId(0x7F))
        assertEquals(ProtocolLanguage.UNDEFINED, ProtocolLanguage.fromId(0x0E)) // Unassigned slot
    }

    // ==========================================
    // 2. LANGUAGE VALIDATION INVARIANTS IN TRANSCEIVERPACKET
    // ==========================================

    @Test
    fun testExtendedBcp47RequiresNonBlankTag() {
        // Valid extended tag
        val validExtended = TransceiverPacket(
            sequenceNumber = 1L,
            timestamp = 1000L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = "fr",
            text = "Bonjour"
        )
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, validExtended.language)
        assertEquals("fr", validExtended.extendedLanguageTag)

        // Null tag on EXTENDED_BCP47 must be rejected
        try {
            TransceiverPacket(
                sequenceNumber = 1L,
                timestamp = 1000L,
                language = ProtocolLanguage.EXTENDED_BCP47,
                extendedLanguageTag = null,
                text = "Bonjour"
            )
            fail("Expected IllegalArgumentException for EXTENDED_BCP47 with null tag")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("extendedLanguageTag must be provided"))
        }

        // Blank tag on EXTENDED_BCP47 must be rejected
        try {
            TransceiverPacket(
                sequenceNumber = 1L,
                timestamp = 1000L,
                language = ProtocolLanguage.EXTENDED_BCP47,
                extendedLanguageTag = "   ",
                text = "Bonjour"
            )
            fail("Expected IllegalArgumentException for EXTENDED_BCP47 with blank tag")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("extendedLanguageTag must be provided"))
        }
    }

    @Test
    fun testNonExtendedLanguageMustHaveNullExtendedTag() {
        try {
            TransceiverPacket(
                sequenceNumber = 1L,
                timestamp = 1000L,
                language = ProtocolLanguage.ENGLISH,
                extendedLanguageTag = "en", // Illegal: must be null for registered language
                text = "Hello"
            )
            fail("Expected IllegalArgumentException for registered language carrying extended tag")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("extendedLanguageTag must be null"))
        }

        try {
            TransceiverPacket(
                sequenceNumber = 1L,
                timestamp = 1000L,
                language = ProtocolLanguage.UNDEFINED,
                extendedLanguageTag = "und", // Illegal: must be null for UNDEFINED
                text = "Hello"
            )
            fail("Expected IllegalArgumentException for UNDEFINED language carrying extended tag")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("extendedLanguageTag must be null"))
        }
    }

    @Test
    fun testReservedLanguageRejected() {
        try {
            TransceiverPacket(
                sequenceNumber = 1L,
                timestamp = 1000L,
                language = ProtocolLanguage.RESERVED,
                text = "Hello"
            )
            fail("Expected IllegalArgumentException for RESERVED language code")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("RESERVED (0xFF) cannot be used"))
        }
    }

    @Test
    fun testExtendedTagLengthValidation() {
        // Exactly 255 ASCII bytes: valid
        val maxTag = "a".repeat(255)
        val validPacket = TransceiverPacket(
            sequenceNumber = 1L,
            timestamp = 1000L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = maxTag,
            text = "Test"
        )
        assertEquals(maxTag, validPacket.extendedLanguageTag)

        // 256 ASCII bytes: exceeds UInt8 capacity, must be rejected
        val overflowTag = "a".repeat(256)
        try {
            TransceiverPacket(
                sequenceNumber = 1L,
                timestamp = 1000L,
                language = ProtocolLanguage.EXTENDED_BCP47,
                extendedLanguageTag = overflowTag,
                text = "Test"
            )
            fail("Expected IllegalArgumentException for extended language tag exceeding 255 bytes")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Extended language tag UTF-8 byte length must be in range"))
        }
    }

    // ==========================================
    // 3. CONFIDENCE QUANTIZATION TESTS
    // ==========================================

    @Test
    fun testNullConfidenceQuantization() {
        assertNull(ConfidenceQuantizer.quantize(null))
        assertNull(ConfidenceQuantizer.dequantize(null))
    }

    @Test
    fun testZeroConfidenceQuantization() {
        val q = ConfidenceQuantizer.quantize(0.0f)
        assertEquals(0, q)
        assertEquals(0.0f, ConfidenceQuantizer.dequantize(q)!!, 0.0001f)
    }

    @Test
    fun testFullConfidenceQuantization() {
        val q = ConfidenceQuantizer.quantize(1.0f)
        assertEquals(100, q)
        assertEquals(1.0f, ConfidenceQuantizer.dequantize(q)!!, 0.0001f)
    }

    @Test
    fun testIntermediateConfidenceQuantization() {
        val q50 = ConfidenceQuantizer.quantize(0.5f)
        assertEquals(50, q50)

        val qRoundDown = ConfidenceQuantizer.quantize(0.504f)
        assertEquals(50, qRoundDown)

        val qRoundUp = ConfidenceQuantizer.quantize(0.506f)
        assertEquals(51, qRoundUp)
    }

    @Test
    fun testDefensiveConfidenceClamping() {
        assertEquals(0, ConfidenceQuantizer.quantize(-0.15f))
        assertEquals(100, ConfidenceQuantizer.quantize(1.25f))
    }

    // ==========================================
    // 4. TEXT & UTF-8 BYTE LENGTH TESTS
    // ==========================================

    @Test
    fun testAsciiTextByteLength() {
        val packet = TransceiverPacket(
            sequenceNumber = 1L,
            timestamp = 1000L,
            language = ProtocolLanguage.ENGLISH,
            text = "Hello tactical world"
        )
        assertEquals(20, packet.textByteLength)
        assertEquals(packet.text.length, packet.textByteLength)
    }

    @Test
    fun testHindiTextByteLengthDiffersFromStringLength() {
        val hindiText = "कुछ अणुओं में अस्थिर केंद्रक होता है"
        val packet = TransceiverPacket(
            sequenceNumber = 2L,
            timestamp = 2000L,
            language = ProtocolLanguage.HINDI,
            text = hindiText
        )
        val expectedUtf8Bytes = hindiText.toByteArray(Charsets.UTF_8).size
        assertEquals(expectedUtf8Bytes, packet.textByteLength)
        // In Devanagari, UTF-8 byte count (96 bytes) is significantly greater than Char count (36 chars)
        assertTrue("UTF-8 byte length must be greater than char length for Devanagari", packet.textByteLength > hindiText.length)
        assertEquals(96, packet.textByteLength)
        assertEquals(36, hindiText.length)
    }

    @Test
    fun testTextExceeding65535BytesRejected() {
        val largeText = "A".repeat(65536)
        try {
            TransceiverPacket(
                sequenceNumber = 1L,
                timestamp = 1000L,
                language = ProtocolLanguage.ENGLISH,
                text = largeText
            )
            fail("Expected IllegalArgumentException for text exceeding 65535 UTF-8 bytes")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("exceeds maximum allowed"))
        }
    }

    // ==========================================
    // 5. SEQUENCE NUMBER & FLAGS TESTS
    // ==========================================

    @Test
    fun testValidSequenceNumberBoundaries() {
        val p0 = TransceiverPacket(sequenceNumber = 0L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Test")
        assertEquals(0L, p0.sequenceNumber)

        val pMax = TransceiverPacket(sequenceNumber = 0xFFFFFFFFL, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Test")
        assertEquals(4294967295L, pMax.sequenceNumber)
    }

    @Test
    fun testInvalidSequenceNumberRejected() {
        try {
            TransceiverPacket(sequenceNumber = -1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Test")
            fail("Expected IllegalArgumentException for negative sequence number")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Sequence number must be in UInt32 range"))
        }

        try {
            TransceiverPacket(sequenceNumber = 0x100000000L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Test")
            fail("Expected IllegalArgumentException for sequence number exceeding UInt32")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Sequence number must be in UInt32 range"))
        }
    }

    @Test
    fun testFlagsDerivationAndValidation() {
        // Without confidence: rawValue = 0x00
        val flagsNoConf = ProtocolFlags(confidencePresent = false)
        assertEquals(0x00, flagsNoConf.rawValue)
        assertFalse(flagsNoConf.confidencePresent)

        // With confidence: rawValue = 0x01
        val flagsWithConf = ProtocolFlags(confidencePresent = true)
        assertEquals(0x01, flagsWithConf.rawValue)
        assertTrue(flagsWithConf.confidencePresent)

        // Parsing from byte
        assertEquals(flagsNoConf, ProtocolFlags.fromByte(0x00))
        assertEquals(flagsWithConf, ProtocolFlags.fromByte(0x01))

        // Reject reserved bits 2-7
        try {
            ProtocolFlags.fromByte(0x04) // Bit 2 set
            fail("Expected IllegalArgumentException for non-zero reserved bits")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Bits 2-7"))
        }

        // Reject fragmentation bit 1 in MVP
        try {
            ProtocolFlags.fromByte(0x02) // Bit 1 set
            fail("Expected IllegalArgumentException for fragmentation bit in MVP")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Bit 1 (fragmentation) is reserved"))
        }
    }

    // ==========================================
    // 6. MAPPING FROM SPEECHMESSAGE TESTS
    // ==========================================

    @Test
    fun testSpeechMessageToPacketMappingEnglish() {
        val speech = SpeechMessage(
            text = "Tactical command recognized",
            language = "en",
            timestamp = 1726850000123L,
            confidence = 0.92f
        )
        val explicitSeq = 105L

        val packet = TransceiverPacketMapper.fromSpeechMessage(speech, sequenceNumber = explicitSeq)

        assertEquals(ProtocolConstants.VERSION_1, packet.version)
        assertEquals(MessageType.SPEECH_TEXT, packet.messageType)
        assertEquals(explicitSeq, packet.sequenceNumber)
        assertEquals(1726850000123L, packet.timestamp)
        assertEquals(ProtocolLanguage.ENGLISH, packet.language)
        assertNull(packet.extendedLanguageTag)
        assertEquals(92, packet.confidence)
        assertTrue(packet.flags.confidencePresent)
        assertEquals("Tactical command recognized", packet.text)
    }

    @Test
    fun testSpeechMessageToPacketMappingHindiNullConfidence() {
        val hindiText = "कुछ अणुओं में अस्थिर केंद्रक होता है"
        val speech = SpeechMessage(
            text = hindiText,
            language = "hi",
            timestamp = 1726850050000L,
            confidence = null
        )
        val explicitSeq = 200L

        val packet = speech.toPacket(sequenceNumber = explicitSeq)

        assertEquals(ProtocolConstants.VERSION_1, packet.version)
        assertEquals(MessageType.SPEECH_TEXT, packet.messageType)
        assertEquals(explicitSeq, packet.sequenceNumber)
        assertEquals(1726850050000L, packet.timestamp)
        assertEquals(ProtocolLanguage.HINDI, packet.language)
        assertNull(packet.extendedLanguageTag)
        assertNull(packet.confidence)
        assertFalse(packet.flags.confidencePresent)
        assertEquals(hindiText, packet.text)
    }

    @Test
    fun testSpeechMessageToPacketMappingExtendedLanguagePreserved() {
        val frenchSpeech = SpeechMessage(
            text = "Message en français",
            language = "fr",
            timestamp = 1726850100000L,
            confidence = 0.85f
        )
        val packet = frenchSpeech.toPacket(sequenceNumber = 301L)

        assertEquals(ProtocolLanguage.EXTENDED_BCP47, packet.language)
        assertEquals("fr", packet.extendedLanguageTag)
        assertEquals(85, packet.confidence)
        assertEquals("Message en français", packet.text)

        val spanishRegionalSpeech = SpeechMessage(
            text = "Mensaje regional",
            language = "es-ES",
            timestamp = 1726850100000L,
            confidence = null
        )
        val packetEs = spanishRegionalSpeech.toPacket(sequenceNumber = 302L)

        assertEquals(ProtocolLanguage.EXTENDED_BCP47, packetEs.language)
        assertEquals("es-ES", packetEs.extendedLanguageTag)
        assertNull(packetEs.confidence)

        // Regional English and Hindi variants preserved verbatim
        val enGbSpeech = SpeechMessage(text = "Hello", language = "en_GB", timestamp = 1726850100000L)
        val packetEnGb = enGbSpeech.toPacket(sequenceNumber = 303L)
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, packetEnGb.language)
        assertEquals("en_GB", packetEnGb.extendedLanguageTag)

        val enGbHyphenSpeech = SpeechMessage(text = "Hello", language = "en-GB", timestamp = 1726850100000L)
        val packetEnGbHyphen = enGbHyphenSpeech.toPacket(sequenceNumber = 304L)
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, packetEnGbHyphen.language)
        assertEquals("en-GB", packetEnGbHyphen.extendedLanguageTag)

        val hiInSpeech = SpeechMessage(text = "नमस्ते", language = "hi-IN", timestamp = 1726850100000L)
        val packetHiIn = hiInSpeech.toPacket(sequenceNumber = 305L)
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, packetHiIn.language)
        assertEquals("hi-IN", packetHiIn.extendedLanguageTag)

        val hiInUnderSpeech = SpeechMessage(text = "नमस्ते", language = "hi_IN", timestamp = 1726850100000L)
        val packetHiInUnder = hiInUnderSpeech.toPacket(sequenceNumber = 306L)
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, packetHiInUnder.language)
        assertEquals("hi_IN", packetHiInUnder.extendedLanguageTag)
    }
}
