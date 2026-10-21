package org.itantra.core.coordinator

import org.itantra.core.domain.SpeechMessage
import org.itantra.core.tts.TtsAudio

/**
 * Observer interface for top-level [TransceiverCoordinator] events, message flows, and errors.
 */
interface TransceiverListener {

    /**
     * Invoked when the operational state of the transceiver changes.
     */
    fun onStateChanged(state: TransceiverState) {}

    /**
     * Invoked when a locally spoken [SpeechMessage] is produced by M1 and dispatched to M2 transport.
     */
    fun onOutboundSpeechMessage(message: SpeechMessage) {}

    /**
     * Invoked when a remote [SpeechMessage] is received via M2 transport and forwarded to M3 TTS.
     */
    fun onInboundSpeechMessage(message: SpeechMessage) {}

    /**
     * Invoked when an incoming [SpeechMessage] has been synthesized by M3 TTS and rendered to audio playback.
     */
    fun onInboundPlaybackCompleted(message: SpeechMessage, audio: TtsAudio) {}

    /**
     * Invoked when an error occurs in any subsystem (M1, M2, M3, or coordinator lifecycle).
     */
    fun onError(error: TransceiverError) {}
}
