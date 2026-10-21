package org.itantra.core.communication.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * Deterministic binary encoder for [TransceiverPacket] adhering to the frozen M2 wire specification.
 * Based on Section E & F of docs/communication/M2_PROTOCOL_SPEC.md.
 */
object PacketEncoder {

    /**
     * Encodes a [TransceiverPacket] into its authoritative binary wire format.
     *
     * Wire layout:
     * - Magic: 2 bytes Big-Endian (0x4954 = 'IT')
     * - Version: 1 byte (0x01)
     * - MessageType: 1 byte (0x01 = SPEECH_TEXT)
     * - Flags: 1 byte (Bit 0 = CONFIDENCE_PRESENT)
     * - SequenceNumber: 4 bytes Big-Endian (UInt32)
     * - Timestamp: 8 bytes Big-Endian (Int64)
     * - LanguageId: 1 byte (0x00..0x0D or 0xFE)
     * - [ExtendedLanguageLength]: 1 byte (UInt8, 1..255) iff LanguageId == 0xFE
     * - [ExtendedLanguageTag]: E bytes UTF-8 iff LanguageId == 0xFE
     * - TextLength: 2 bytes Big-Endian (UInt16, 0..65535)
     * - [Confidence]: 1 byte (UInt8, 0..100) iff Flags Bit 0 == 1
     * - TextPayload: L bytes UTF-8
     * - CRC-32: 4 bytes Big-Endian (IEEE 802.3 over Magic through TextPayload)
     *
     * @param packet The logical transceiver packet to serialize.
     * @return Deterministically serialized binary byte array.
     * @throws ProtocolException if packet state violates protocol invariants.
     */
    fun encode(packet: TransceiverPacket): ByteArray {
        val textBytes = packet.text.toByteArray(Charsets.UTF_8)
        if (textBytes.size > ProtocolConstants.MAX_TEXT_BYTES) {
            throw ProtocolException("Text byte length (${textBytes.size}) exceeds protocol maximum (${ProtocolConstants.MAX_TEXT_BYTES})")
        }

        val isExtended = packet.language == ProtocolLanguage.EXTENDED_BCP47
        val extBytes = if (isExtended) {
            val tag = packet.extendedLanguageTag
                ?: throw ProtocolException("extendedLanguageTag must not be null when language is EXTENDED_BCP47")
            val bytes = tag.toByteArray(Charsets.UTF_8)
            if (bytes.isEmpty() || bytes.size > ProtocolLanguage.MAX_EXTENDED_TAG_BYTES) {
                throw ProtocolException("Extended language tag UTF-8 byte length (${bytes.size}) must be in 1..${ProtocolLanguage.MAX_EXTENDED_TAG_BYTES}")
            }
            bytes
        } else {
            if (packet.extendedLanguageTag != null) {
                throw ProtocolException("extendedLanguageTag must be null for registered language '${packet.language}'")
            }
            null
        }

        val confPresent = packet.flags.confidencePresent
        if (confPresent) {
            val conf = packet.confidence
                ?: throw ProtocolException("confidence must not be null when Flags.confidencePresent is true")
            if (conf !in ConfidenceQuantizer.MIN_QUANTIZED..ConfidenceQuantizer.MAX_QUANTIZED) {
                throw ProtocolException("Quantized confidence ($conf) must be in 0..100")
            }
        } else {
            if (packet.confidence != null) {
                throw ProtocolException("confidence must be null when Flags.confidencePresent is false")
            }
        }

        // Header size calculation
        val preHeaderSize = 17 // Magic(2) + Ver(1) + Type(1) + Flags(1) + Seq(4) + Ts(8)
        val langSize = if (isExtended) 2 + extBytes!!.size else 1 // LangId(1) + [ExtLen(1) + ExtTag(E)]
        val textLenSize = 2
        val confSize = if (confPresent) 1 else 0
        val payloadSize = textBytes.size
        val crcSize = 4

        val totalSize = preHeaderSize + langSize + textLenSize + confSize + payloadSize + crcSize

        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)

        // 1. Pre-Header
        buffer.putShort(ProtocolConstants.MAGIC_BYTES.toShort())
        buffer.put(packet.version.toByte())
        buffer.put(packet.messageType.id.toByte())
        buffer.put(packet.flags.rawValue.toByte())
        buffer.putInt((packet.sequenceNumber and 0xFFFFFFFFL).toInt())
        buffer.putLong(packet.timestamp)

        // 2. Language
        buffer.put(packet.language.id.toByte())
        if (isExtended) {
            buffer.put(extBytes!!.size.toByte())
            buffer.put(extBytes)
        }

        // 3. Text Length
        buffer.putShort(textBytes.size.toShort())

        // 4. Confidence (if present)
        if (confPresent) {
            buffer.put(packet.confidence!!.toByte())
        }

        // 5. Text Payload
        buffer.put(textBytes)

        val outBytes = buffer.array()

        // 6. Compute CRC-32 over all bytes up to this point
        val crc = CRC32()
        crc.update(outBytes, 0, totalSize - crcSize)
        val crcValue = crc.value

        // 7. Trailer CRC-32 (Big-Endian UInt32)
        buffer.putInt((crcValue and 0xFFFFFFFFL).toInt())

        return outBytes
    }
}
