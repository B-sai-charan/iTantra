package org.itantra.ui.model

import org.itantra.core.communication.connection.ConnectionState
import org.itantra.core.coordinator.TransceiverState

/**
 * Immutable UI State model representing the entire presentation surface of the iTantra transceiver.
 *
 * Governs header connectivity, active role, language routing, conversation cards,
 * tactical PTT button states, and operational status/error banners.
 * Pure presentation data model with zero hardware/network dependencies.
 */
data class TransceiverUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val transceiverState: TransceiverState = TransceiverState.STOPPED,
    val selectedRole: String = "SERVER", // "SERVER" (Receiver) or "CLIENT" (Transmitter)
    val selectedLanguage: String = "en", // "en" (English) or "hi" (Hindi)
    val peerIp: String = "",
    val isServer: Boolean = true,
    
    // Conversation Cards
    val youTranscript: String = "",
    val youLanguageBadge: String = "EN",
    val remoteTranscript: String = "",
    val remoteLanguageBadge: String = "AUTO",
    val remotePlaybackStatus: String = "",
    val isRemotePlaybackVisible: Boolean = false,
    
    // Operational Banner & Action Status
    val statusText: String = "Ready",
    val errorText: String? = null,
    val isErrorVisible: Boolean = false,
    
    // PTT Surface
    val pttLabel: String = "Tap to Speak",
    val isPttActive: Boolean = false,
    val isPttEnabled: Boolean = true,
    
    // Telemetry & Diagnostic Counters
    val outboundMessagesCount: Int = 0,
    val inboundMessagesCount: Int = 0
)
