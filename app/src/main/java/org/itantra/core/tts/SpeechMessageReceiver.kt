package org.itantra.core.tts

import org.itantra.core.domain.SpeechMessage

/**
 * Consumer interface defining the communication boundary between M2 (Communication Subsystem)
 * and M3 (Text-To-Speech / Downstream Presentation Subsystem).
 *
 * M2 delivers reconstructed, verified [SpeechMessage] instances across this boundary.
 * M3 implementations (such as TTS speech synthesis engines, playback queues, or UI loggers)
 * consume incoming messages for local presentation without knowing about wire codecs, TCP sockets,
 * CRC checksums, or transport framing.
 */
fun interface SpeechMessageReceiver {

    /**
     * Invoked when a valid, verified [SpeechMessage] is received from the communication transport.
     *
     * Delivery semantics:
     * - Exactly one invocation per successfully decoded and verified incoming speech packet.
     * - Preserves [SpeechMessage.text], [SpeechMessage.language], [SpeechMessage.timestamp], and [SpeechMessage.confidence] exactly.
     * - Executed asynchronously from the transport receive loop.
     * - Downstream exceptions thrown by this method are isolated from the communication link.
     *
     * @param message The reconstructed speech message received from the remote peer node.
     */
    suspend fun onSpeechMessageReceived(message: SpeechMessage)
}
