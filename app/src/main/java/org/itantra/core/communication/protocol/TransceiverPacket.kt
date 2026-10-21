package org.itantra.core.communication.protocol

/**
 * Protocol framing constants for the M2 communication subsystem.
 * Based on Section E of docs/communication/M2_PROTOCOL_SPEC.md.
 */
object ProtocolConstants {
    /** 2-byte Magic synchronizer: 'I' 'T' = 0x4954. */
    const val MAGIC_BYTES: Int = 0x4954

    /** Protocol version 1 (MVP). */
    const val VERSION_1: Int = 0x01

    /** Maximum allowed UTF-8 text payload length in bytes (UInt16 capacity). */
    const val MAX_TEXT_BYTES: Int = 65535

    /** Maximum valid sequence number (UInt32 capacity). */
    const val MAX_SEQUENCE_NUMBER: Long = 0xFFFFFFFFL
}

/**
 * Application payload discriminator types for M2 packets.
 */
enum class MessageType(val id: Int) {
    /** Recognized speech transcription payload (the only supported message type in MVP). */
    SPEECH_TEXT(0x01),

    /** Protocol-reserved future message type (unsupported in MVP). */
    HEARTBEAT(0x02),

    /** Protocol-reserved future message type (unsupported in MVP). */
    ACK(0x03);

    companion object {
        fun fromId(id: Int): MessageType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Single authoritative representation of M2 protocol flags.
 *
 * Bit 0: CONFIDENCE_PRESENT (1 = 1-byte quantized confidence follows TextLength; 0 = absent).
 * Bit 1: RESERVED_FRAGMENT (must be 0 in MVP; reserved for future fragmentation header).
 * Bits 2-7: RESERVED (must be 0 in v1).
 *
 * @property confidencePresent True if confidence score is present in packet payload.
 */
data class ProtocolFlags(
    val confidencePresent: Boolean = false
) {
    /**
     * Exact 8-bit wire representation of this flag state.
     */
    val rawValue: Int
        get() = if (confidencePresent) 0x01 else 0x00

    companion object {
        /**
         * Parses a raw byte into [ProtocolFlags] and strictly validates that reserved bits are zero.
         */
        fun fromByte(flagsByte: Int): ProtocolFlags {
            require(flagsByte in 0x00..0xFF) {
                "Flags byte must be an 8-bit unsigned value [0..255], got: $flagsByte"
            }
            require((flagsByte and 0xFC) == 0) {
                "Bits 2-7 of flags byte are reserved and must be 0, got raw flags: 0x${flagsByte.toString(16)}"
            }
            require((flagsByte and 0x02) == 0) {
                "Bit 1 (fragmentation) is reserved and must be 0 in MVP, got raw flags: 0x${flagsByte.toString(16)}"
            }
            val confPresent = (flagsByte and 0x01) != 0
            return ProtocolFlags(confidencePresent = confPresent)
        }
    }
}

/**
 * Pure Kotlin domain model representing an immutable logical M2 communication wire packet.
 * Directly encapsulates the wire layout defined in docs/communication/M2_PROTOCOL_SPEC.md.
 *
 * @property version Protocol version (must be 1 for MVP).
 * @property messageType Application message type (must be [MessageType.SPEECH_TEXT] for MVP).
 * @property sequenceNumber Monotonically increasing per-node sequence counter in range [0, 4294967295].
 * @property timestamp M1 boundary acquisition epoch milliseconds.
 * @property language Protocol language identifier code from [ProtocolLanguage].
 * @property extendedLanguageTag Verbatim BCP-47 language tag string when [language] is [ProtocolLanguage.EXTENDED_BCP47]; null otherwise.
 * @property confidence Optional quantized confidence score in range [0, 100] (null if absent).
 * @property flags Protocol bitmask flags (defaults automatically based on [confidence]).
 * @property text The exact transcribed speech text payload.
 */
data class TransceiverPacket(
    val version: Int = ProtocolConstants.VERSION_1,
    val messageType: MessageType = MessageType.SPEECH_TEXT,
    val sequenceNumber: Long,
    val timestamp: Long,
    val language: ProtocolLanguage,
    val extendedLanguageTag: String? = null,
    val confidence: Int? = null,
    val flags: ProtocolFlags = ProtocolFlags(confidencePresent = confidence != null),
    val text: String
) {
    /**
     * Exact byte length of the UTF-8 encoded text payload.
     * Note: Evaluated on actual UTF-8 byte count, NOT String.length.
     */
    val textByteLength: Int
        get() = text.toByteArray(Charsets.UTF_8).size

    init {
        require(version == ProtocolConstants.VERSION_1) {
            "Unsupported protocol version: $version. Expected: ${ProtocolConstants.VERSION_1}"
        }
        require(messageType == MessageType.SPEECH_TEXT) {
            "MVP only supports MessageType.SPEECH_TEXT (0x01), got unsupported type: $messageType"
        }
        require(language != ProtocolLanguage.RESERVED) {
            "ProtocolLanguage.RESERVED (0xFF) cannot be used as a runtime language"
        }
        if (language == ProtocolLanguage.EXTENDED_BCP47) {
            require(!extendedLanguageTag.isNullOrBlank()) {
                "extendedLanguageTag must be provided and non-blank when language is EXTENDED_BCP47"
            }
            val extBytes = extendedLanguageTag.toByteArray(Charsets.UTF_8).size
            require(extBytes in 1..ProtocolLanguage.MAX_EXTENDED_TAG_BYTES) {
                "Extended language tag UTF-8 byte length must be in range [1, ${ProtocolLanguage.MAX_EXTENDED_TAG_BYTES}], got: $extBytes"
            }
        } else {
            require(extendedLanguageTag == null) {
                "extendedLanguageTag must be null for registered language '$language', got: '$extendedLanguageTag'"
            }
        }
        require(sequenceNumber in 0L..ProtocolConstants.MAX_SEQUENCE_NUMBER) {
            "Sequence number must be in UInt32 range [0, ${ProtocolConstants.MAX_SEQUENCE_NUMBER}], got: $sequenceNumber"
        }
        if (confidence != null) {
            require(confidence in ConfidenceQuantizer.MIN_QUANTIZED..ConfidenceQuantizer.MAX_QUANTIZED) {
                "Quantized confidence must be in range [0, 100], got: $confidence"
            }
            require(flags.confidencePresent) {
                "Flags.confidencePresent must be true when confidence is non-null"
            }
        } else {
            require(!flags.confidencePresent) {
                "Flags.confidencePresent must be false when confidence is null"
            }
        }
        val byteLen = textByteLength
        require(byteLen <= ProtocolConstants.MAX_TEXT_BYTES) {
            "Text UTF-8 byte length exceeds maximum allowed (${ProtocolConstants.MAX_TEXT_BYTES} bytes), got: $byteLen bytes"
        }
    }
}
