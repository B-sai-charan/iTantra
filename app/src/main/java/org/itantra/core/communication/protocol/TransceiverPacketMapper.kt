package org.itantra.core.communication.protocol

import org.itantra.core.domain.SpeechMessage

/**
 * Pure, deterministic mapper converting M1 [SpeechMessage] domain model
 * into M2 [TransceiverPacket] wire envelope.
 */
object TransceiverPacketMapper {

    /**
     * Maps an M1 [SpeechMessage] to an M2 [TransceiverPacket].
     *
     * Mapping rules:
     * - `text` is preserved verbatim without alteration.
     * - `timestamp` is preserved verbatim.
     * - `language` and `extendedLanguageTag` are resolved via [ProtocolLanguage.resolve].
     * - `confidence` is quantized via [ConfidenceQuantizer.quantize].
     * - `sequenceNumber` is assigned explicitly by the caller (never auto-generated).
     *
     * @param speechMessage The semantic speech message from M1.
     * @param sequenceNumber Explicit sequence number allocated by M2 transport/state manager.
     * @return Immutable [TransceiverPacket] instance.
     */
    fun fromSpeechMessage(
        speechMessage: SpeechMessage,
        sequenceNumber: Long
    ): TransceiverPacket {
        val quantizedConf = ConfidenceQuantizer.quantize(speechMessage.confidence)
        val (protoLang, extendedTag) = ProtocolLanguage.resolve(speechMessage.language)

        return TransceiverPacket(
            version = ProtocolConstants.VERSION_1,
            messageType = MessageType.SPEECH_TEXT,
            sequenceNumber = sequenceNumber,
            timestamp = speechMessage.timestamp,
            language = protoLang,
            extendedLanguageTag = extendedTag,
            confidence = quantizedConf,
            flags = ProtocolFlags(confidencePresent = quantizedConf != null),
            text = speechMessage.text
        )
    }

    /**
     * Maps an incoming M2 [TransceiverPacket] back into an M1 [SpeechMessage] domain model.
     *
     * Mapping rules:
     * - `text` is preserved verbatim.
     * - `language` is resolved to verbatim extended tag if [ProtocolLanguage.EXTENDED_BCP47],
     *   or the canonical tag of the registered [ProtocolLanguage].
     * - `timestamp` is preserved verbatim.
     * - `confidence` is dequantized via [ConfidenceQuantizer.dequantize].
     *
     * @param packet The decoded wire packet.
     * @return Reconstructed [SpeechMessage].
     */
    fun toSpeechMessage(packet: TransceiverPacket): SpeechMessage {
        val languageTag = when (packet.language) {
            ProtocolLanguage.EXTENDED_BCP47 -> packet.extendedLanguageTag
                ?: throw ProtocolException("EXTENDED_BCP47 packet is missing extendedLanguageTag")
            else -> packet.language.tag
        }
        val dequantizedConf = ConfidenceQuantizer.dequantize(packet.confidence)

        return SpeechMessage(
            text = packet.text,
            language = languageTag,
            timestamp = packet.timestamp,
            confidence = dequantizedConf
        )
    }
}

/**
 * Extension function for mapping [SpeechMessage] to [TransceiverPacket].
 */
fun SpeechMessage.toPacket(sequenceNumber: Long): TransceiverPacket {
    return TransceiverPacketMapper.fromSpeechMessage(this, sequenceNumber)
}

/**
 * Extension function for mapping [TransceiverPacket] back to [SpeechMessage].
 */
fun TransceiverPacket.toSpeechMessage(): SpeechMessage {
    return TransceiverPacketMapper.toSpeechMessage(this)
}
