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
 * READ-ONLY / TEST-ONLY Golden Vector Audit for the frozen M2 binary wire protocol.
 * Verifies exact interoperability and bit-level accuracy against independently constructed byte arrays.
 */
class PacketGoldenVectorTest {

    /**
     * Independent test-only CRC-32 calculator.
     * Used solely to verify golden vectors without calling production encoders.
     */
    private fun computeIndependentCrc32(bytes: ByteArray, length: Int): Long {
        val crc = CRC32()
        crc.update(bytes, 0, length)
        return crc.value and 0xFFFFFFFFL
    }

    private fun appendBigEndianCrc(body: ByteArray): ByteArray {
        val crcVal = computeIndependentCrc32(body, body.size)
        val fullPacket = ByteArray(body.size + 4)
        System.arraycopy(body, 0, fullPacket, 0, body.size)
        val buffer = ByteBuffer.wrap(fullPacket, body.size, 4).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt((crcVal and 0xFFFFFFFFL).toInt())
        return fullPacket
    }

    // ==========================================
    // VECTOR 1 — Registered English, no confidence
    // ==========================================

    @Test
    fun testGoldenVector1RegisteredEnglishNoConfidence() {
        val packet = TransceiverPacket(
            version = 1,
            messageType = MessageType.SPEECH_TEXT,
            sequenceNumber = 0L,
            timestamp = 0L,
            language = ProtocolLanguage.ENGLISH,
            extendedLanguageTag = null,
            confidence = null,
            text = "hello"
        )

        // Independently constructed expected byte sequence (24 header/payload + 4 CRC = 28 bytes):
        val expectedBody = byteArrayOf(
            0x49.toByte(), 0x54.toByte(),                         // Magic: 0x4954 ('IT')
            0x01.toByte(),                                         // Version: 0x01
            0x01.toByte(),                                         // MessageType: 0x01 (SPEECH_TEXT)
            0x00.toByte(),                                         // Flags: 0x00 (Confidence absent)
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // SequenceNumber: 0
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // Timestamp: 0 (high 4 bytes)
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // Timestamp: 0 (low 4 bytes)
            0x01.toByte(),                                         // LanguageId: 0x01 (ENGLISH)
            0x00.toByte(), 0x05.toByte(),                         // TextLength: 5
            0x68.toByte(), 0x65.toByte(), 0x6C.toByte(), 0x6C.toByte(), 0x6F.toByte() // "hello"
        )
        assertEquals(25, expectedBody.size)

        val expectedCrc = computeIndependentCrc32(expectedBody, expectedBody.size)
        val expectedGoldenBytes = appendBigEndianCrc(expectedBody)
        assertEquals(29, expectedGoldenBytes.size)

        // 1. Assert encoder produces bit-exact golden bytes
        val encodedBytes = PacketEncoder.encode(packet)
        assertArrayEquals("Vector 1 encoded bytes must match golden vector", expectedGoldenBytes, encodedBytes)

        // 2. Assert CRC bytes match Big-Endian representation
        val crcFromWire = ByteBuffer.wrap(encodedBytes, 25, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        assertEquals("Vector 1 CRC must match independent calculation", expectedCrc, crcFromWire)

        // 3. Assert decoder decodes golden bytes into identical logical packet
        val decoded = PacketDecoder.decode(expectedGoldenBytes)
        assertEquals("Vector 1 decoded packet must match original", packet, decoded)
        assertNull(decoded.confidence)
        assertFalse(decoded.flags.confidencePresent)
        assertEquals(ProtocolLanguage.ENGLISH, decoded.language)
        assertNull(decoded.extendedLanguageTag)

        // 4. Determinism check
        val encodedBytesSecond = PacketEncoder.encode(packet)
        assertArrayEquals("Vector 1 encoding must be strictly deterministic", encodedBytes, encodedBytesSecond)
    }

    // ==========================================
    // VECTOR 2 — Registered Hindi, confidence present
    // ==========================================

    @Test
    fun testGoldenVector2RegisteredHindiWithConfidence() {
        val confidenceFloat = 0.75f
        val quantizedConf = ConfidenceQuantizer.quantize(confidenceFloat)!!
        assertEquals(75, quantizedConf) // 75 = 0x4B

        val hindiText = "नमस्ते"
        val expectedTextUtf8Bytes = byteArrayOf(
            0xE0.toByte(), 0xA4.toByte(), 0xA8.toByte(), // न (U+0928)
            0xE0.toByte(), 0xA4.toByte(), 0xAE.toByte(), // म (U+092E)
            0xE0.toByte(), 0xA4.toByte(), 0xB8.toByte(), // स (U+0938)
            0xE0.toByte(), 0xA5.toByte(), 0x8D.toByte(), // ् (U+094D, virama)
            0xE0.toByte(), 0xA4.toByte(), 0xA4.toByte(), // त (U+0924)
            0xE0.toByte(), 0xA5.toByte(), 0x87.toByte()  // े (U+0947, vowel e)
        )
        assertEquals(18, expectedTextUtf8Bytes.size)

        val packet = TransceiverPacket(
            version = 1,
            messageType = MessageType.SPEECH_TEXT,
            sequenceNumber = 1L,
            timestamp = 1000L,
            language = ProtocolLanguage.HINDI,
            extendedLanguageTag = null,
            confidence = quantizedConf,
            text = hindiText
        )

        // Independently constructed expected byte sequence (25 fixed + 18 text + 4 CRC = 47 bytes):
        val expectedBody = byteArrayOf(
            0x49.toByte(), 0x54.toByte(),                         // Magic: 0x4954 ('IT')
            0x01.toByte(),                                         // Version: 0x01
            0x01.toByte(),                                         // MessageType: 0x01 (SPEECH_TEXT)
            0x01.toByte(),                                         // Flags: 0x01 (Confidence present)
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), // SequenceNumber: 1
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // Timestamp: 1000ms (high 4 bytes)
            0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0xE8.toByte(), // Timestamp: 1000ms (low 4 bytes)
            0x02.toByte(),                                         // LanguageId: 0x02 (HINDI)
            0x00.toByte(), 0x12.toByte(),                         // TextLength: 18 bytes (0x0012)
            0x4B.toByte(),                                         // Confidence: 75 (0x4B)
            // 18 UTF-8 text bytes
            0xE0.toByte(), 0xA4.toByte(), 0xA8.toByte(),
            0xE0.toByte(), 0xA4.toByte(), 0xAE.toByte(),
            0xE0.toByte(), 0xA4.toByte(), 0xB8.toByte(),
            0xE0.toByte(), 0xA5.toByte(), 0x8D.toByte(),
            0xE0.toByte(), 0xA4.toByte(), 0xA4.toByte(),
            0xE0.toByte(), 0xA5.toByte(), 0x87.toByte()
        )
        assertEquals(39, expectedBody.size)

        val expectedCrc = computeIndependentCrc32(expectedBody, expectedBody.size)
        val expectedGoldenBytes = appendBigEndianCrc(expectedBody)
        assertEquals(43, expectedGoldenBytes.size)

        // 1. Assert encoder produces bit-exact golden bytes
        val encodedBytes = PacketEncoder.encode(packet)
        assertArrayEquals("Vector 2 encoded bytes must match golden vector", expectedGoldenBytes, encodedBytes)

        // 2. Assert CRC matches independent calculation
        val crcFromWire = ByteBuffer.wrap(encodedBytes, 39, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        assertEquals("Vector 2 CRC must match independent calculation", expectedCrc, crcFromWire)

        // 3. Assert decoder decodes golden bytes into identical logical packet
        val decoded = PacketDecoder.decode(expectedGoldenBytes)
        assertEquals("Vector 2 decoded packet must match original", packet, decoded)
        assertEquals(75, decoded.confidence)
        assertTrue(decoded.flags.confidencePresent)
        assertEquals(ProtocolLanguage.HINDI, decoded.language)
        assertEquals(hindiText, decoded.text)

        // 4. Determinism check
        val encodedBytesSecond = PacketEncoder.encode(packet)
        assertArrayEquals("Vector 2 encoding must be strictly deterministic", encodedBytes, encodedBytesSecond)
    }

    // ==========================================
    // VECTOR 3 — Extended language with underscore ("en_GB")
    // ==========================================

    @Test
    fun testGoldenVector3ExtendedLanguageUnderscore() {
        val packet = TransceiverPacket(
            version = 1,
            messageType = MessageType.SPEECH_TEXT,
            sequenceNumber = 0xFFFFFFFFL,
            timestamp = -1L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = "en_GB",
            confidence = null,
            text = "test"
        )

        // Independently constructed expected byte sequence (25 + 5 + 4 text + 4 CRC = 38 bytes):
        val expectedBody = byteArrayOf(
            0x49.toByte(), 0x54.toByte(),                         // Magic: 0x4954 ('IT')
            0x01.toByte(),                                         // Version: 0x01
            0x01.toByte(),                                         // MessageType: 0x01 (SPEECH_TEXT)
            0x00.toByte(),                                         // Flags: 0x00 (Confidence absent)
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // SequenceNumber: 0xFFFFFFFF
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // Timestamp: -1L (high 4 bytes)
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // Timestamp: -1L (low 4 bytes)
            0xFE.toByte(),                                         // LanguageId: 0xFE (EXTENDED_BCP47)
            0x05.toByte(),                                         // ExtendedLanguageLength: 5
            0x65.toByte(), 0x6E.toByte(), 0x5F.toByte(), 0x47.toByte(), 0x42.toByte(), // "en_GB"
            0x00.toByte(), 0x04.toByte(),                         // TextLength: 4
            0x74.toByte(), 0x65.toByte(), 0x73.toByte(), 0x74.toByte() // "test"
        )
        assertEquals(30, expectedBody.size)

        val expectedCrc = computeIndependentCrc32(expectedBody, expectedBody.size)
        val expectedGoldenBytes = appendBigEndianCrc(expectedBody)
        assertEquals(34, expectedGoldenBytes.size)

        // 1. Assert encoder produces bit-exact golden bytes
        val encodedBytes = PacketEncoder.encode(packet)
        assertArrayEquals("Vector 3 encoded bytes must match golden vector", expectedGoldenBytes, encodedBytes)

        // 2. Assert CRC matches independent calculation
        val crcFromWire = ByteBuffer.wrap(encodedBytes, 30, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        assertEquals("Vector 3 CRC must match independent calculation", expectedCrc, crcFromWire)

        // 3. Assert decoder decodes golden bytes into identical logical packet with "en_GB" preserved
        val decoded = PacketDecoder.decode(expectedGoldenBytes)
        assertEquals("Vector 3 decoded packet must match original", packet, decoded)
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, decoded.language)
        assertEquals("en_GB", decoded.extendedLanguageTag)
        assertEquals("test", decoded.text)

        // 4. Determinism check
        val encodedBytesSecond = PacketEncoder.encode(packet)
        assertArrayEquals("Vector 3 encoding must be strictly deterministic", encodedBytes, encodedBytesSecond)
    }

    // ==========================================
    // VECTOR 4 — Extended language with Unicode ("é")
    // ==========================================

    @Test
    fun testGoldenVector4ExtendedLanguageUnicode() {
        val confidenceFloat = 0.0f
        val quantizedConf = ConfidenceQuantizer.quantize(confidenceFloat)!!
        assertEquals(0, quantizedConf) // 0 = 0x00

        val unicodeTag = "é" // U+00E9 encodes to 2 UTF-8 bytes: 0xC3, 0xA9
        val tagBytes = unicodeTag.toByteArray(Charsets.UTF_8)
        assertEquals(2, tagBytes.size)

        val indicText = "अ" // U+0905 encodes to 3 UTF-8 bytes: 0xE0, 0xA4, 0x85
        val textBytes = indicText.toByteArray(Charsets.UTF_8)
        assertEquals(3, textBytes.size)

        val packet = TransceiverPacket(
            version = 1,
            messageType = MessageType.SPEECH_TEXT,
            sequenceNumber = 42L,
            timestamp = 123456789L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = unicodeTag,
            confidence = quantizedConf,
            text = indicText
        )

        // Independently constructed expected byte sequence (26 + 2 tag + 3 text + 4 CRC = 35 bytes):
        val expectedBody = byteArrayOf(
            0x49.toByte(), 0x54.toByte(),                         // Magic: 0x4954 ('IT')
            0x01.toByte(),                                         // Version: 0x01
            0x01.toByte(),                                         // MessageType: 0x01 (SPEECH_TEXT)
            0x01.toByte(),                                         // Flags: 0x01 (Confidence present)
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x2A.toByte(), // SequenceNumber: 42 (0x0000002A)
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // Timestamp: 123456789 (high 4 bytes)
            0x07.toByte(), 0x5B.toByte(), 0xCD.toByte(), 0x15.toByte(), // Timestamp: 123456789 (low 4 bytes: 0x075BCD15)
            0xFE.toByte(),                                         // LanguageId: 0xFE (EXTENDED_BCP47)
            0x02.toByte(),                                         // ExtendedLanguageLength: 2 bytes
            0xC3.toByte(), 0xA9.toByte(),                         // "é" (2 UTF-8 bytes)
            0x00.toByte(), 0x03.toByte(),                         // TextLength: 3 bytes
            0x00.toByte(),                                         // Confidence: 0 (0x00)
            0xE0.toByte(), 0xA4.toByte(), 0x85.toByte()          // "अ" (3 UTF-8 bytes)
        )
        assertEquals(27, expectedBody.size)

        val expectedCrc = computeIndependentCrc32(expectedBody, expectedBody.size)
        val expectedGoldenBytes = appendBigEndianCrc(expectedBody)
        assertEquals(31, expectedGoldenBytes.size)

        // 1. Assert encoder produces bit-exact golden bytes
        val encodedBytes = PacketEncoder.encode(packet)
        assertArrayEquals("Vector 4 encoded bytes must match golden vector", expectedGoldenBytes, encodedBytes)

        // 2. Assert CRC matches independent calculation
        val crcFromWire = ByteBuffer.wrap(encodedBytes, 27, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        assertEquals("Vector 4 CRC must match independent calculation", expectedCrc, crcFromWire)

        // 3. Assert decoder decodes golden bytes into identical logical packet
        val decoded = PacketDecoder.decode(expectedGoldenBytes)
        assertEquals("Vector 4 decoded packet must match original", packet, decoded)
        assertEquals(ProtocolLanguage.EXTENDED_BCP47, decoded.language)
        assertEquals("é", decoded.extendedLanguageTag)
        assertEquals(0, decoded.confidence)
        assertTrue(decoded.flags.confidencePresent)
        assertEquals("अ", decoded.text)

        // 4. Determinism check
        val encodedBytesSecond = PacketEncoder.encode(packet)
        assertArrayEquals("Vector 4 encoding must be strictly deterministic", encodedBytes, encodedBytesSecond)
    }

    // ==========================================
    // VECTOR 5 — Undefined language (Minimal valid registered packet)
    // ==========================================

    @Test
    fun testGoldenVector5UndefinedLanguageMinimalPacket() {
        val packet = TransceiverPacket(
            version = 1,
            messageType = MessageType.SPEECH_TEXT,
            sequenceNumber = 7L,
            timestamp = 123L,
            language = ProtocolLanguage.UNDEFINED,
            extendedLanguageTag = null,
            confidence = null,
            text = ""
        )

        // Independently constructed expected byte sequence (24 fixed + 0 text + 4 CRC = 24 bytes total):
        val expectedBody = byteArrayOf(
            0x49.toByte(), 0x54.toByte(),                         // Magic: 0x4954 ('IT')
            0x01.toByte(),                                         // Version: 0x01
            0x01.toByte(),                                         // MessageType: 0x01 (SPEECH_TEXT)
            0x00.toByte(),                                         // Flags: 0x00 (Confidence absent)
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x07.toByte(), // SequenceNumber: 7
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), // Timestamp: 123 (high 4 bytes)
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x7B.toByte(), // Timestamp: 123 (low 4 bytes: 0x7B)
            0x00.toByte(),                                         // LanguageId: 0x00 (UNDEFINED)
            0x00.toByte(), 0x00.toByte()                          // TextLength: 0
        )
        assertEquals(20, expectedBody.size)

        val expectedCrc = computeIndependentCrc32(expectedBody, expectedBody.size)
        val expectedGoldenBytes = appendBigEndianCrc(expectedBody)
        assertEquals(24, expectedGoldenBytes.size)

        // 1. Assert encoder produces bit-exact golden bytes
        val encodedBytes = PacketEncoder.encode(packet)
        assertArrayEquals("Vector 5 encoded bytes must match golden vector", expectedGoldenBytes, encodedBytes)

        // 2. Assert CRC matches independent calculation
        val crcFromWire = ByteBuffer.wrap(encodedBytes, 20, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL
        assertEquals("Vector 5 CRC must match independent calculation", expectedCrc, crcFromWire)

        // 3. Assert decoder decodes golden bytes into identical logical packet
        val decoded = PacketDecoder.decode(expectedGoldenBytes)
        assertEquals("Vector 5 decoded packet must match original", packet, decoded)
        assertEquals(ProtocolLanguage.UNDEFINED, decoded.language)
        assertNull(decoded.extendedLanguageTag)
        assertNull(decoded.confidence)
        assertEquals("", decoded.text)

        // 4. Determinism check
        val encodedBytesSecond = PacketEncoder.encode(packet)
        assertArrayEquals("Vector 5 encoding must be strictly deterministic", encodedBytes, encodedBytesSecond)
    }

    // ==========================================
    // NEGATIVE INTEROPERABILITY CHECKS
    // ==========================================

    @Test
    fun testNegativeSingleCoveredByteCorruptionRejection() {
        val packet = TransceiverPacket(sequenceNumber = 1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Valid")
        val goldenBytes = PacketEncoder.encode(packet)

        // Flip 1 bit in timestamp offset
        val corrupted = goldenBytes.clone()
        corrupted[10] = (corrupted[10].toInt() xor 0x01).toByte()

        try {
            PacketDecoder.decode(corrupted)
            fail("Expected ProtocolException for CRC mismatch on single bit flip")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("CRC-32 checksum mismatch"))
        }
    }

    @Test
    fun testNegativeCrcBytesCorruptionRejection() {
        val packet = TransceiverPacket(sequenceNumber = 1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Valid")
        val goldenBytes = PacketEncoder.encode(packet)

        // Mutate only the last CRC byte
        val corruptedCrc = goldenBytes.clone()
        val lastIdx = corruptedCrc.size - 1
        corruptedCrc[lastIdx] = (corruptedCrc[lastIdx].toInt() xor 0xFF).toByte()

        try {
            PacketDecoder.decode(corruptedCrc)
            fail("Expected ProtocolException for corrupted CRC trailer")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("CRC-32 checksum mismatch"))
        }
    }

    @Test
    fun testNegativeAppendedTrailingDataRejection() {
        val packet = TransceiverPacket(sequenceNumber = 1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Valid")
        val goldenBytes = PacketEncoder.encode(packet)

        // Append 1 trailing byte
        val withTrailing = goldenBytes.copyOf(goldenBytes.size + 1)
        withTrailing[goldenBytes.size] = 0xAA.toByte()

        try {
            PacketDecoder.decode(withTrailing)
            fail("Expected ProtocolException for trailing bytes")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("unexpected trailing bytes"))
        }
    }

    @Test
    fun testNegativeEnGbVsEnDashGbDistinction() {
        val packetUnder = TransceiverPacket(
            sequenceNumber = 1L,
            timestamp = 1000L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = "en_GB",
            text = "Dialect"
        )
        val bytesUnder = PacketEncoder.encode(packetUnder)

        val packetHyphen = TransceiverPacket(
            sequenceNumber = 1L,
            timestamp = 1000L,
            language = ProtocolLanguage.EXTENDED_BCP47,
            extendedLanguageTag = "en-GB",
            text = "Dialect"
        )
        val bytesHyphen = PacketEncoder.encode(packetHyphen)

        assertFalse("Wire representations of 'en_GB' and 'en-GB' must differ", bytesUnder.contentEquals(bytesHyphen))

        val decodedUnder = PacketDecoder.decode(bytesUnder)
        val decodedHyphen = PacketDecoder.decode(bytesHyphen)

        assertEquals("en_GB", decodedUnder.extendedLanguageTag)
        assertEquals("en-GB", decodedHyphen.extendedLanguageTag)
        assertTrue("Decoded extended tags must not be equal", decodedUnder.extendedLanguageTag != decodedHyphen.extendedLanguageTag)
    }

    @Test
    fun testNegativeReservedFlagsRejection() {
        val packet = TransceiverPacket(sequenceNumber = 1L, timestamp = 1000L, language = ProtocolLanguage.ENGLISH, text = "Valid")
        val goldenBytes = PacketEncoder.encode(packet)

        // Set Bit 1 (Reserved fragmentation)
        val reservedBit1 = goldenBytes.clone()
        reservedBit1[4] = 0x02.toByte()
        try {
            PacketDecoder.decode(reservedBit1)
            fail("Expected ProtocolException for reserved flag bit 1")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("Invalid protocol flags"))
        }

        // Set Bit 7 (Reserved future flag)
        val reservedBit7 = goldenBytes.clone()
        reservedBit7[4] = 0x80.toByte()
        try {
            PacketDecoder.decode(reservedBit7)
            fail("Expected ProtocolException for reserved flag bit 7")
        } catch (e: ProtocolException) {
            assertTrue(e.message!!.contains("Invalid protocol flags"))
        }
    }
}
