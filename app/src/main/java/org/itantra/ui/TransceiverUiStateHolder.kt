package org.itantra.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.itantra.core.communication.connection.ConnectionState
import org.itantra.core.coordinator.TransceiverError
import org.itantra.core.coordinator.TransceiverState
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.tts.TtsAudio
import org.itantra.ui.model.TransceiverUiState

/**
 * Presentation State Holder for the iTantra transceiver.
 *
 * Exposes a lifecycle-safe [StateFlow] of [TransceiverUiState].
 * Pure presentation boundary: accepts events from [MainActivity] or background callbacks
 * and maps them into immutable state without holding business, hardware, audio, or network logic.
 */
class TransceiverUiStateHolder(
    initialState: TransceiverUiState = TransceiverUiState()
) {
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<TransceiverUiState> = _uiState.asStateFlow()

    val currentState: TransceiverUiState
        get() = _uiState.value

    /**
     * Updates transceiver lifecycle state (e.g. STOPPED, RUNNING, FAILED).
     */
    fun onTransceiverStateChanged(state: TransceiverState) {
        _uiState.update { current ->
            val status = TransceiverUiFormatter.formatStatusBanner(state)
            val pttLabel = TransceiverUiFormatter.formatPttLabel(state)
            val isRunning = state == TransceiverState.RUNNING
            current.copy(
                transceiverState = state,
                statusText = status,
                pttLabel = pttLabel,
                isPttActive = isRunning
            )
        }
    }

    /**
     * Updates physical TCP connection state (e.g. CONNECTED, CONNECTING, OFFLINE).
     */
    fun onConnectionStateChanged(state: ConnectionState, isServer: Boolean = _uiState.value.isServer) {
        _uiState.update { current ->
            current.copy(
                connectionState = state,
                isServer = isServer
            )
        }
    }

    /**
     * Updates outbound speech transcript and telemetry counters when local mic STT completes.
     */
    fun onOutboundSpeechMessage(message: SpeechMessage) {
        _uiState.update { current ->
            val transcript = TransceiverUiFormatter.formatOutboundTranscript(message)
            val badge = message.language.uppercase()
            current.copy(
                youTranscript = transcript,
                youLanguageBadge = badge,
                outboundMessagesCount = current.outboundMessagesCount + 1,
                statusText = "Speech transmitted"
            )
        }
    }

    /**
     * Updates inbound transcript when a remote message is received over TCP.
     */
    fun onInboundSpeechMessage(message: SpeechMessage) {
        _uiState.update { current ->
            val transcript = TransceiverUiFormatter.formatInboundTranscript(message)
            val badge = message.language.uppercase()
            current.copy(
                remoteTranscript = transcript,
                remoteLanguageBadge = badge,
                isRemotePlaybackVisible = false,
                inboundMessagesCount = current.inboundMessagesCount + 1,
                statusText = "Received remote speech"
            )
        }
    }

    /**
     * Updates inbound playback badge once Piper neural TTS completes synthesis and playback.
     */
    fun onInboundPlaybackCompleted(message: SpeechMessage, audio: TtsAudio) {
        _uiState.update { current ->
            val playbackStatus = TransceiverUiFormatter.formatPlaybackConfirmation(audio)
            current.copy(
                remotePlaybackStatus = playbackStatus,
                isRemotePlaybackVisible = true
            )
        }
    }

    /**
     * Updates error state from a domain [TransceiverError].
     */
    fun onError(error: TransceiverError) {
        _uiState.update { current ->
            val friendlyMsg = TransceiverUiFormatter.formatUserFriendlyError(error)
            current.copy(
                errorText = friendlyMsg,
                isErrorVisible = true,
                statusText = "Error"
            )
        }
    }

    /**
     * Updates or clears user-visible error banner.
     */
    fun onUserError(errorMsg: String?) {
        _uiState.update { current ->
            current.copy(
                errorText = errorMsg,
                isErrorVisible = !errorMsg.isNullOrBlank()
            )
        }
    }

    /**
     * Updates network role selection (Receiver / Server vs Transmitter / Client).
     */
    fun onRoleChanged(role: String, isServer: Boolean) {
        _uiState.update { current ->
            current.copy(
                selectedRole = role,
                isServer = isServer
            )
        }
    }

    /**
     * Updates selected transcription & synthesis language ("en" vs "hi").
     */
    fun onLanguageChanged(lang: String) {
        _uiState.update { current ->
            current.copy(
                selectedLanguage = lang,
                youLanguageBadge = lang.uppercase()
            )
        }
    }

    /**
     * Updates target peer IP address for client connections.
     */
    fun onPeerIpChanged(ip: String) {
        _uiState.update { current ->
            current.copy(peerIp = ip)
        }
    }

    /**
     * Updates PTT activation and enabled state.
     */
    fun onPttStateChanged(isActive: Boolean, isEnabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                isPttActive = isActive,
                isPttEnabled = isEnabled
            )
        }
    }

    /**
     * Updates custom status banner text directly (e.g. for smoke test diagnostics).
     */
    fun updateStatusText(status: String) {
        _uiState.update { current ->
            current.copy(statusText = status)
        }
    }
}
