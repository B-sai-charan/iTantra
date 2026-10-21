package org.itantra.core.communication.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Deterministic unit test suite for [PacketEncoder] and [PacketDecoder].
 */
class PacketCodecTest {

    // ==========================================
    // 1. REGISTERED LANGUAGE CODEC TESTS
    // ==========================================

    @Test
    fun testRegisteredLanguageNoConfidence() {
        val packet = TransceiverPacket(
            version = 1,
            messageType = MessageType.SPEECH_TEXT,
            sequenceNumber = 42L,
            timestamp = 1726850000000L,
            language = ProtocolLanguage.ENGLISH,
            extendedLanguageTag = null,
            confidence = null,
            text = "Alpha team advance"
        )

        val bytes = PacketEncoder.encode(packet)
        // 24 fixed + 18 text bytes = 42 bytes
        assertEquals(24 + 18, bytes.size)

        val decoded = PacketDecoder.decode(bytes)
        assertEquals(packet, decoded)
        assertNull(decoded.confidence)
        assertFalse(decoded.flags.confidencePresent)
        assertEquals(ProtocolLanguage.ENGLISH, decoded.language)
        assertNull(decoded.extendedLanguageTag)
    }

    @Test
    fun testRegisteredLanguageWithConfidence() {
        val packet = TransceiverPacket(
            version = 1,
            messageType = MessageType.SPEECH_TEXT,
            sequenceNumber = 100L,
            timestamp = 1726850000100L,
            language = ProtocolLanguage.HINDI,
            confidence = 88,
            text = "नमस्ते"
        )

        val bytes = PacketEncoder.encode(packet)
        val textBytes = "नमस्ते".toByteArray(Charsets.UTF_8)
        // 25 fixed + text bytes
        assertEquals(25 + textBytes.size, bytes.size)

        val decoded = PacketDecoder.decode(bytes)
        assertEquals(packet, decoded)
        assertEquals(88, decoded.confidence)
        assertTrue(decoded.flags.confidencePresent)
        assertEquals(ProtocolLanguage.HINDI, decoded.language)
    }

    @Test
    fun testConfidenceBoundaryZero() {
        val packet = TransceiverPacket(
            sequenceNumber = 1L,
            timestamp = 1000L,
            language = ProtocolLanguage.ENGLISH,
            confidence = 0,
            text = "Zero confidence"
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)
        assertEquals(0, decoded.confidence)
        assertTrue(decoded.flags.confidencePresent)
    }

    @Test
    fun testConfidenceBoundaryHundred() {
        val packet = TransceiverPacket(
            sequenceNumber = 1L,
            timestamp = 1000L,
            language = ProtocolLanguage.ENGLISH,
            confidence = 100,
            text = "Full confidence"
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)
        assertEquals(100, decoded.confidence)
        assertTrue(decoded.flags.confidencePresent)
    }

    @Test
    fun testUndefinedLanguageCodec() {
        val packet = TransceiverPacket(
            sequenceNumber = 7L,
            timestamp = 1726850000000L,
            language = ProtocolLanguage.UNDEFINED,
            extendedLanguageTag = null,
            confidence = null,
            text = "Unknown language audio"
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)
        assertEquals(ProtocolLanguage.UNDEFINED, decoded.language)
        assertNull(decoded.extendedLanguageTag)
        assertEquals(packet, decoded)
    }

    // ==========================================
    // 2. EXTENDED LANGUAGE CODEC & PRESERVATION TESTS
    // ==========================================

    @Test
    fun testExtendedLanguageEnUs() {
        val packet = TransceiverPacket(
            sequenceNumber = 10L,
            timestamp = 1726850000000L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = "en-US",
            confidence = 95,
            text = "American dialect"
        )

        val bytes = PacketEncoder.encode(packet)
        val textBytes = "American dialect".toByteArray(Charsets.UTF_8)
        // 26 + 5 (en-US) + 16 (text) = 47 bytes
        assertEquals(26 + 5 + textBytes.size, bytes.size)

        val decoded = PacketDecoder.decode(bytes)
        assertEquals(packet, decoded)
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, decoded.language)
        assertEquals("en-US", decoded.extendedLanguageTag)
    }

    @Test
    fun testExtendedLanguageEnGbHyphenVsUnderscore() {
        val packetHyphen = TransceiverPacket(
            sequenceNumber = 11L,
            timestamp = 1726850000000L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = "en-GB",
            confidence = null,
            text = "British English"
        )

        val packetUnder = TransceiverPacket(
            sequenceNumber = 11L,
            timestamp = 1726850000000L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = "en_GB",
            confidence = null,
            text = "British English"
        )

        val bytesHyphen = PacketEncoder.encode(packetHyphen)
        val bytesUnder = PacketEncoder.encode(packetUnder)

        // Ensure serialized bytes are distinct
        assertFalse("Hyphen and underscore tags must produce distinct wire bytes", bytesHyphen.contentEquals(bytesUnder))

        val decodedHyphen = PacketDecoder.decode(bytesHyphen)
        val decodedUnder = PacketDecoder.decode(bytesUnder)

        assertEquals("en-GB", decodedHyphen.extendedLanguageTag)
        assertEquals("en_GB", decodedUnder.extendedLanguageTag)
    }

    @Test
    fun testExtendedLanguageUnicodeCharacters() {
        val unicodeTag = "zh-Hant-TW"
        val packet = TransceiverPacket(
            sequenceNumber = 12L,
            timestamp = 1726850000000L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = unicodeTag,
            confidence = 80,
            text = "繁體中文"
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)
        assertEquals(packet, decoded)
        assertEquals(unicodeTag, decoded.extendedLanguageTag)
        assertEquals("繁體中文", decoded.text)
    }

    @Test
    fun testExtendedLanguage255BytesBoundary() {
        val maxTag = "a".repeat(255)
        assertEquals(255, maxTag.toByteArray(Charsets.UTF_8).size)

        val packet = TransceiverPacket(
            sequenceNumber = 13L,
            timestamp = 1000L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = maxTag,
            text = "Max tag length"
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)
        assertEquals(maxTag, decoded.extendedLanguageTag)
        assertEquals(packet, decoded)
    }

    @Test
    fun testExtendedLanguage256BytesRejected() {
        val overTag = "a".repeat(256)
        try {
            TransceiverPacket(
                sequenceNumber = 1L,
                timestamp = 1000L,
                language = ProtocolLanguage.EXTENDED_BCP47,
                extendedLanguageTag = overTag,
                text = "Over max"
            )
            fail("Expected IllegalArgumentException for 256-byte extended tag")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Extended language tag UTF-8 byte length must be in range"))
        }
    }

    // ==========================================
    // 3. TEXT PAYLOAD & UTF-8 TESTS
    // ==========================================

    @Test
    fun testHindiUtf8Text() {
        val hindiText = "कुछ अणुओं में अस्थिर केंद्रक होता है"
        val packet = TransceiverPacket(
            sequenceNumber = 20L,
            timestamp = 1726850000000L,
            language = ProtocolLanguage.HINDI,
            text = hindiText
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)
        assertEquals(hindiText, decoded.text)
        assertEquals(packet, decoded)
    }

    @Test
    fun testEmojiAndMultibyteText() {
        val emojiText = "Mission objective alpha 🎯 completed! 🚀 Status: OK 👍"
        val packet = TransceiverPacket(
            sequenceNumber = 21L,
            timestamp = 1726850000000L,
            language = ProtocolLanguage.ENGLISH,
            text = emojiText
        )

        val bytes = PacketEncoder.encode(packet)
        val decoded = PacketDecoder.decode(bytes)
        assertEquals(emojiText, decoded.text)
        assertEquals(packet, decoded)
    }

    @Test
    fun testEmptyTextPayload() {
        // Registered language with empty text
        val emptyReg = TransceiverPacket(
            sequenceNumber = 22L,
            timestamp = 1000L,
            language = ProtocolLanguage.ENGLISH,
            text = ""
        )
        val bytesReg = PacketEncoder.encode(emptyReg)
        assertEquals(24, bytesReg.size)
        val decodedReg = PacketDecoder.decode(bytesReg)
        assertEquals("", decodedReg.text)
        assertEquals(emptyReg, decodedReg)

        // Extended language with empty text
        val emptyExt = TransceiverPacket(
            sequenceNumber = 23L,
            timestamp = 1000L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = "fr",
            text = ""
        )
        val bytesExt = PacketEncoder.encode(emptyExt)
        assertEquals(25 + 2 + 0, bytesExt.size) // 27 bytes
        val decodedExt = PacketDecoder.decode(bytesExt)
        assertEquals("", decodedExt.text)
        assertEquals(emptyExt, decodedExt)
    }

    @Test
    fun testLargeTextNear65535Bytes() {
        val largeText = "A".repeat(65530)
        val packet = TransceiverPacket(
            sequenceNumber = 24L,
            timestamp = 1000L,
            language = ProtocolLanguage.ENGLISH,
            text = largeText
        )

        val bytes = PacketEncoder.encode(packet)
        assertEquals(24 + 65530, bytes.size)

        val decoded = PacketDecoder.decode(bytes)
        assertEquals(largeText, decoded.text)
        assertEquals(packet, decoded)
    }

    @Test
    fun testMaxText65535Bytes() {
        val maxText = "A".repeat(65535)
        val packet = TransceiverPacket(
            sequenceNumber = 25L,
            timestamp = 1000L,
            language = ProtocolLanguage.ENGLISH,
            text = maxText
        )

        val bytes = PacketEncoder.encode(packet)
        assertEquals(24 + 65535, bytes.size)

        val decoded = PacketDecoder.decode(bytes)
        assertEquals(maxText, decoded.text)
    }

    @Test
    fun testTextExceeding65535BytesRejected() {
        val overflowText = "A".repeat(65536)
        try {
            TransceiverPacket(
                sequenceNumber = 1L,
                timestamp = 1000L,
                language = ProtocolLanguage.ENGLISH,
                text = overflowText
            )
            fail("Expected IllegalArgumentException for text exceeding 65535 bytes")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("exceeds maximum allowed"))
        }
    }

    // ==========================================
    // 4. SEQUENCE NUMBER & TIMESTAMP BOUNDARIES
    // ==========================================

    @Test
    fun testSequenceNumberBoundaries() {
        // Zero
        val p0 = TransceiverPacket(sequenceNumber = 0L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Seq 0")
        assertEquals(p0, PacketDecoder.decode(PacketEncoder.encode(p0)))

        // UInt32 Max (0xFFFFFFFFL = 4294967295L)
        val pMax = TransceiverPacket(sequenceNumber = 0xFFFFFFFFL, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Seq Max")
        val decodedMax = PacketDecoder.decode(PacketEncoder.encode(pMax))
        assertEquals(0xFFFFFFFFL, decodedMax.sequenceNumber)
        assertEquals(pMax, decodedMax)
    }

    @Test
    fun testTimestampBoundaries() {
        // Timestamp 0
        val p0 = TransceiverPacket(sequenceNumber = 1L, timestamp = 0L, language = ProtocolLanguage.ENGLISH, text = "Ts 0")
        assertEquals(0L, PacketDecoder.decode(PacketEncoder.encode(p0)).timestamp)

        // Negative Timestamp
        val pNeg = TransceiverPacket(sequenceNumber = 1L, timestamp = -1000L, language = ProtocolLanguage.ENGLISH, text = "Ts Neg")
        assertEquals(-1000L, PacketDecoder.decode(PacketEncoder.encode(pNeg)).timestamp)

        // Large Positive Epoch
        val pEpoch = TransceiverPacket(sequenceNumber = 1L, timestamp = 1726850000123L, language = ProtocolLanguage.ENGLISH, text = "Ts Epoch")
        assertEquals(1726850000123L, PacketDecoder.decode(PacketEncoder.encode(pEpoch)).timestamp)
    }

    // ==========================================
    // 5. DETERMINISM & EXACT BYTE / CRC ASSERTION
    // ==========================================

    @Test
    fun testDeterministicEncoding() {
        val packet = TransceiverPacket(
            sequenceNumber = 42L,
            timestamp = 1726850000000L,
            language = ProtocolLanguage.HINDI,
            confidence = 85,
            text = "परीक्षण संदेश"
        )

        val bytes1 = PacketEncoder.encode(packet)
        val bytes2 = PacketEncoder.encode(packet)

        assertArrayEquals("Encoding must be strictly deterministic", bytes1, bytes2)
    }

    @Test
    fun testKnownDeterministicPacketExactBytesAndCrc() {
        // Construct a completely known deterministic packet:
        // Magic = 0x4954 ('IT')
        // Version = 0x01
        // MessageType = 0x01 (SPEECH_TEXT)
        // Flags = 0x00 (Confidence absent)
        // SequenceNumber = 0x00000001 (1)
        // Timestamp = 0x00000000000003E8 (1000 ms)
        // LanguageId = 0x01 (ENGLISH)
        // TextLength = 0x0004 (4 bytes)
        // Text = "Test" (0x54 0x65 0x73 0x74)
        // Covered byte length = 2 + 1 + 1 + 1 + 4 + 8 + 1 + 2 + 4 = 24 bytes

        val packet = TransceiverPacket(
            version = 1,
            messageType = MessageType.SPEECH_TEXT,
            sequenceNumber = 1L,
            timestamp = 1000L,
            language = ProtocolLanguage.ENGLISH,
            extendedLanguageTag = null,
            confidence = null,
            text = "Test"
        )

        val bytes = PacketEncoder.encode(packet)
        assertEquals(28, bytes.size) // 24 header/payload + 4 CRC

        // Verify header prefix bytes manually
        assertEquals(0x49.toByte(), bytes[0])
        assertEquals(0x54.toByte(), bytes[1])
        assertEquals(0x01.toByte(), bytes[2]) // Version
        assertEquals(0x01.toByte(), bytes[3]) // SPEECH_TEXT
        assertEquals(0x00.toByte(), bytes[4]) // Flags
        // Seq (4B)
        assertEquals(0x00.toByte(), bytes[5])
        assertEquals(0x00.toByte(), bytes[6])
        assertEquals(0x00.toByte(), bytes[7])
        assertEquals(0x01.toByte(), bytes[8])
        // Timestamp (8B = 1000L)
        assertEquals(0x00.toByte(), bytes[9])
        assertEquals(0x00.toByte(), bytes[10])
        assertEquals(0x00.toByte(), bytes[11])
        assertEquals(0x00.toByte(), bytes[12])
        assertEquals(0x00.toByte(), bytes[13])
        assertEquals(0x00.toByte(), bytes[14])
        assertEquals(0x03.toByte(), bytes[15])
        assertEquals(0xE8.toByte(), bytes[16])
        // LangId (1B = ENGLISH = 0x01)
        assertEquals(0x01.toByte(), bytes[17])
        // TextLen (2B = 4)
        assertEquals(0x00.toByte(), bytes[18])
        assertEquals(0x04.toByte(), bytes[19])
        // Text ("Test" = 54 65 73 74)
        assertEquals(0x54.toByte(), bytes[20])
        assertEquals(0x65.toByte(), bytes[21])
        assertEquals(0x73.toByte(), bytes[22])
        assertEquals(0x74.toByte(), bytes[23])

        // Compute expected CRC over first 24 bytes
        val crc = CRC32()
        crc.update(bytes, 0, 24)
        val expectedCrc = crc.value

        // Read CRC from bytes[24..27]
        val buffer = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.BIG_ENDIAN)
        val wireCrc = buffer.int.toLong() and 0xFFFFFFFFL

        assertEquals(expectedCrc, wireCrc)
    }

    // ==========================================
    // 6. DECODER CORRUPTION & ERROR HANDLING TESTS
    // ==========================================

    @Test
    fun testCrcCorruptionDetection() {
        val packet = TransceiverPacket(
            sequenceNumber = 1L,
            timestamp = 1000L,
            language = ProtocolLanguage.ENGLISH,
            text = "Reliability check"
        )
        val validBytes = PacketEncoder.encode(packet)

        // Corrupt a single payload byte
        val corruptedBytes = validBytes.clone()
        corruptedBytes[20] = (corruptedBytes[20].toInt() xor 0x01).toByte()

        try {
            PacketDecoder.decode(corruptedBytes)
            fail("Expected ProtocolException for CRC mismatch")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("CRC-32 checksum mismatch"))
        }
    }

    @Test
    fun testMagicCorruptionDetection() {
        val packet = TransceiverPacket(sequenceNumber = 1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Hello")
        val bytes = PacketEncoder.encode(packet)
        bytes[0] = 0x58 // Change 'I' to 'X'

        try {
            PacketDecoder.decode(bytes)
            fail("Expected ProtocolException for invalid magic")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("Invalid magic synchronizer"))
        }
    }

    @Test
    fun testVersionCorruptionDetection() {
        val packet = TransceiverPacket(sequenceNumber = 1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Hello")
        val bytes = PacketEncoder.encode(packet)
        bytes[2] = 0x02 // Unsupported version 2

        try {
            PacketDecoder.decode(bytes)
            fail("Expected ProtocolException for unsupported version")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("Unsupported protocol version"))
        }
    }

    @Test
    fun testMessageTypeCorruptionDetection() {
        val packet = TransceiverPacket(sequenceNumber = 1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Hello")
        val bytes = PacketEncoder.encode(packet)
        bytes[3] = 0x02 // HEARTBEAT (unsupported in MVP)

        try {
            PacketDecoder.decode(bytes)
            fail("Expected ProtocolException for unsupported message type")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("Unsupported message type"))
        }
    }

    @Test
    fun testReservedFlagBitsRejection() {
        val packet = TransceiverPacket(sequenceNumber = 1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Hello")
        val bytes = PacketEncoder.encode(packet)
        bytes[4] = 0x04 // Reserved bit 2 set

        try {
            PacketDecoder.decode(bytes)
            fail("Expected ProtocolException for reserved flag bits")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("Invalid protocol flags"))
        }
    }

    @Test
    fun testTruncatedPacketRejection() {
        val packet = TransceiverPacket(sequenceNumber = 1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Hello")
        val bytes = PacketEncoder.encode(packet)

        // Truncate to 15 bytes (< 24 min)
        val truncatedShort = bytes.copyOf(15)
        try {
            PacketDecoder.decode(truncatedShort)
            fail("Expected ProtocolException for too-short packet")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("Packet too short"))
        }

        // Truncate last 2 bytes (truncated CRC)
        val truncatedCrc = bytes.copyOf(bytes.size - 2)
        try {
            PacketDecoder.decode(truncatedCrc)
            fail("Expected ProtocolException for truncated packet")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("Truncated packet"))
        }
    }

    @Test
    fun testTruncatedExtendedLanguageTagRejection() {
        val packet = TransceiverPacket(
            sequenceNumber = 1L,
            timestamp = 1000L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = "en-US",
            text = "Hello"
        )
        val bytes = PacketEncoder.encode(packet)
        assertEquals(35, bytes.size)

        // Truncate to 24 bytes (valid minimum packet size, but truncated for extended tag + text + CRC)
        val truncatedTag = bytes.copyOf(24)
        try {
            PacketDecoder.decode(truncatedTag)
            fail("Expected ProtocolException for truncated extended tag")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("Truncated packet"))
        }

        // Truncate to 18 bytes (cuts off inside extended language length/tag)
        val truncatedVeryShort = bytes.copyOf(18)
        try {
            PacketDecoder.decode(truncatedVeryShort)
            fail("Expected ProtocolException for too short packet")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("Packet too short"))
        }
    }

    @Test
    fun testInvalidLanguageIdRejection() {
        val packet = TransceiverPacket(sequenceNumber = 1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Hello")
        val bytes = PacketEncoder.encode(packet)
        // Offset 17 is LanguageId. Change to unassigned 0x0E
        bytes[17] = 0x0E.toByte()

        try {
            PacketDecoder.decode(bytes)
            fail("Expected ProtocolException for unassigned language ID")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("Invalid or unassigned registered LanguageId"))
        }
    }

    @Test
    fun testTrailingBytesRejection() {
        val packet = TransceiverPacket(sequenceNumber = 1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Hello")
        val bytes = PacketEncoder.encode(packet)

        // Append 1 extra byte
        val withTrailing = bytes.copyOf(bytes.size + 1)
        withTrailing[bytes.size] = 0x00

        try {
            PacketDecoder.decode(withTrailing)
            fail("Expected ProtocolException for trailing bytes")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("unexpected trailing bytes"))
        }
    }

    @Test
    fun testInvalidUtf8TextRejection() {
        val packet = TransceiverPacket(sequenceNumber = 1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Hello")
        val bytes = PacketEncoder.encode(packet)

        // Offset 20 is the start of text payload ("Hello"). Inject invalid UTF-8 byte 0xFF
        bytes[20] = 0xFF.toByte()

        // Recompute CRC so that CRC check would pass if UTF-8 was not checked
        val crc = CRC32()
        crc.update(bytes, 0, bytes.size - 4)
        val buffer = ByteBuffer.wrap(bytes, bytes.size - 4, 4).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt((crc.value and 0xFFFFFFFFL).toInt())

        try {
            PacketDecoder.decode(bytes)
            fail("Expected ProtocolException for invalid UTF-8 text")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("Malformed UTF-8 encoding"))
        }
    }

    @Test
    fun testRoundTripEqualityMultiplePackets() {
        val testPackets = listOf(
            TransceiverPacket(sequenceNumber = 0L, timestamp = 0L, language = ProtocolLanguage.ENGLISH, text = "Simple"),
            TransceiverPacket(sequenceNumber = 1L, timestamp = 1726850000000L, language = ProtocolLanguage.HINDI, confidence = 0, text = "शून्य विश्वास"),
            TransceiverPacket(sequenceNumber = 2L, timestamp = 1726850000000L, language = ProtocolLanguage.HINDI, confidence = 100, text = "पूर्ण विश्वास"),
            TransceiverPacket(sequenceNumber = 3L, timestamp = 1726850000000L, language = ProtocolLanguage.TELUGU, confidence = 75, text = "తెలుగు వాక్యం"),
            TransceiverPacket(sequenceNumber = 4L, timestamp = 1726850000000L, language = ProtocolLanguage.EXTENDED_BCP47, extendedLanguageTag = "en-US", confidence = null, text = "Regional"),
            TransceiverPacket(sequenceNumber = 5L, timestamp = 1726850000000L, language = ProtocolLanguage.EXTENDED_BCP47, extendedLanguageTag = "en_GB", confidence = 90, text = "Preserved"),
            TransceiverPacket(sequenceNumber = 6L, timestamp = 1726850000000L, language = ProtocolLanguage.EXTENDED_BCP47, extendedLanguageTag = "fr", confidence = null, text = "Bonjour le monde"),
            TransceiverPacket(sequenceNumber = 0xFFFFFFFFL, timestamp = -5000L, language = ProtocolLanguage.UNDEFINED, text = "")
        )

        for (packet in testPackets) {
            val encoded = PacketEncoder.encode(packet)
            val decoded = PacketDecoder.decode(encoded)
            assertEquals("Round-trip failed for packet: $packet", packet, decoded)
        }
    }
}
