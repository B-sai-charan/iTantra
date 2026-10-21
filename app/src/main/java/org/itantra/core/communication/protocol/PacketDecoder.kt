package org.itantra.core.communication.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction
import java.util.zip.CRC32

/**
 * Deterministic binary decoder for [TransceiverPacket] adhering to the frozen M2 wire specification.
 * Based on Section E & F of docs/communication/M2_PROTOCOL_SPEC.md.
 */
object PacketDecoder {

    private const val MIN_PACKET_SIZE = 24 // 17 (pre-header) + 1 (lang) + 2 (textlen) + 0 (conf) + 0 (text) + 4 (crc)
    private const val CRC_SIZE = 4

    /**
     * Decodes a raw byte array into an authoritative [TransceiverPacket].
     *
     * @param bytes Raw wire bytes received over communication transport.
     * @return Decoded and verified [TransceiverPacket].
     * @throws ProtocolException on corruption, truncation, malformed headers, invalid encoding, or CRC mismatch.
     */
    fun decode(bytes: ByteArray): TransceiverPacket {
        if (bytes.size < MIN_PACKET_SIZE) {
            throw ProtocolException("Packet too short: ${bytes.size} bytes (minimum is $MIN_PACKET_SIZE bytes)")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

        // 1. Magic (2 Bytes)
        val magic = buffer.short.toInt() and 0xFFFF
        if (magic != ProtocolConstants.MAGIC_BYTES) {
            throw ProtocolException("Invalid magic synchronizer: 0x${magic.toString(16).uppercase()}, expected 0x4954 ('IT')")
        }

        // 2. Version (1 Byte)
        val version = buffer.get().toInt() and 0xFF
        if (version != ProtocolConstants.VERSION_1) {
            throw ProtocolException("Unsupported protocol version: $version, expected ${ProtocolConstants.VERSION_1}")
        }

        // 3. MessageType (1 Byte)
        val msgTypeId = buffer.get().toInt() and 0xFF
        val messageType = MessageType.fromId(msgTypeId)
            ?: throw ProtocolException("Unsupported or invalid message type: 0x${msgTypeId.toString(16).uppercase()}")
        if (messageType != MessageType.SPEECH_TEXT) {
            throw ProtocolException("Unsupported message type in MVP: $messageType (only SPEECH_TEXT 0x01 is supported)")
        }

        // 4. Flags (1 Byte)
        val flagsByte = buffer.get().toInt() and 0xFF
        val flags = try {
            ProtocolFlags.fromByte(flagsByte)
        } catch (e: IllegalArgumentException) {
            throw ProtocolException("Invalid protocol flags byte 0x${flagsByte.toString(16)}: ${e.message}", e)
        }

        // 5. SequenceNumber (4 Bytes, UInt32)
        val sequenceNumber = buffer.int.toLong() and 0xFFFFFFFFL

        // 6. Timestamp (8 Bytes, Int64)
        val timestamp = buffer.long

        // 7. Language
        val langId = buffer.get().toInt() and 0xFF
        val (language, extendedLanguageTag) = if (langId == ProtocolLanguage.EXTENDED_BCP47.id) {
            if (buffer.remaining() < 1) {
                throw ProtocolException("Truncated packet: missing ExtendedLanguageLength")
            }
            val extLen = buffer.get().toInt() and 0xFF
            if (extLen !in 1..ProtocolLanguage.MAX_EXTENDED_TAG_BYTES) {
                throw ProtocolException("ExtendedLanguageLength must be in range 1..${ProtocolLanguage.MAX_EXTENDED_TAG_BYTES}, got: $extLen")
            }
            if (buffer.remaining() < extLen + 2 + CRC_SIZE) {
                throw ProtocolException("Truncated packet: expected $extLen bytes for ExtendedLanguageTag, but only ${buffer.remaining() - 2 - CRC_SIZE} payload bytes available")
            }
            val extBytes = ByteArray(extLen)
            buffer.get(extBytes)

            val tagString = decodeUtf8Strict(extBytes, "ExtendedLanguageTag")
            if (tagString.isBlank()) {
                throw ProtocolException("ExtendedLanguageTag must not be blank")
            }
            Pair(ProtocolLanguage.EXTENDED_BCP47, tagString)
        } else {
            // Must be one of the explicitly registered language IDs (0x00..0x0D)
            if (langId !in 0x00..0x0D) {
                throw ProtocolException("Invalid or unassigned registered LanguageId: 0x${langId.toString(16).uppercase()}")
            }
            Pair(ProtocolLanguage.fromId(langId), null)
        }

        // 8. TextLength (2 Bytes, UInt16)
        if (buffer.remaining() < 2) {
            throw ProtocolException("Truncated packet: missing TextLength field")
        }
        val textLength = buffer.short.toInt() and 0xFFFF

        // 9. Confidence (1 Byte conditional)
        val confidence = if (flags.confidencePresent) {
            if (buffer.remaining() < 1) {
                throw ProtocolException("Truncated packet: missing Confidence byte when Flags.confidencePresent is true")
            }
            val confByte = buffer.get().toInt() and 0xFF
            if (confByte !in ConfidenceQuantizer.MIN_QUANTIZED..ConfidenceQuantizer.MAX_QUANTIZED) {
                throw ProtocolException("Quantized confidence must be in range 0..100, got: $confByte")
            }
            confByte
        } else {
            null
        }

        // 10. TextPayload (L Bytes UTF-8)
        if (buffer.remaining() < textLength + CRC_SIZE) {
            throw ProtocolException("Truncated packet: expected $textLength text bytes and $CRC_SIZE CRC bytes, but only ${buffer.remaining()} bytes remain")
        }
        val textBytes = ByteArray(textLength)
        buffer.get(textBytes)
        val textPayload = decodeUtf8Strict(textBytes, "TextPayload")

        // 11. CRC-32 Trailer (4 Bytes, Big-Endian UInt32)
        if (buffer.remaining() < CRC_SIZE) {
            throw ProtocolException("Truncated packet: missing CRC-32 checksum trailer")
        }
        val receivedCrc = buffer.int.toLong() and 0xFFFFFFFFL

        // 12. Trailing bytes check
        if (buffer.hasRemaining()) {
            throw ProtocolException("Packet contains ${buffer.remaining()} unexpected trailing bytes after CRC-32 trailer")
        }

        // 13. CRC-32 Checksum Verification
        val crc = CRC32()
        crc.update(bytes, 0, bytes.size - CRC_SIZE)
        val computedCrc = crc.value

        if (receivedCrc != computedCrc) {
            throw ProtocolException(
                "CRC-32 checksum mismatch: computed 0x${computedCrc.toString(16).uppercase()}, received 0x${receivedCrc.toString(16).uppercase()}"
            )
        }

        return TransceiverPacket(
            version = version,
            messageType = messageType,
            sequenceNumber = sequenceNumber,
            timestamp = timestamp,
            language = language,
            extendedLanguageTag = extendedLanguageTag,
            confidence = confidence,
            flags = flags,
            text = textPayload
        )
    }

    private fun decodeUtf8Strict(bytes: ByteArray, fieldName: String): String {
        return try {
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            throw ProtocolException("Malformed UTF-8 encoding in $fieldName: ${e.message}", e)
        }
    }
}
