package org.itantra.ui

import org.itantra.core.coordinator.TransceiverError
import org.itantra.core.coordinator.TransceiverState
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.tts.TtsAudio

/**
 * Presentation formatter for Transceiver state, messages, playback, and errors in the UI.
 * Pure presentation logic only; zero business or hardware dependencies.
 */
object TransceiverUiFormatter {

    /**
     * Formats [TransceiverState] for the legacy status banner.
     */
    fun formatState(state: TransceiverState): String = when (state) {
        TransceiverState.STOPPED -> "IDLE (Transceiver Stopped)"
        TransceiverState.STARTING -> "STARTING TRANSCEIVER..."
        TransceiverState.RUNNING -> "RUNNING (Transceiver Active)"
        TransceiverState.STOPPING -> "STOPPING TRANSCEIVER..."
        TransceiverState.RELEASED -> "RELEASED"
        TransceiverState.FAILED -> "FAILED"
    }

    /**
     * Formats actual TCP network connection state for the header badge.
     */
    fun formatConnectionState(
        connectionState: org.itantra.core.communication.connection.ConnectionState,
        isServer: Boolean = false
    ): String = when (connectionState) {
        org.itantra.core.communication.connection.ConnectionState.CONNECTED -> {
            if (isServer) "Listening (42173)" else "Connected"
        }
        org.itantra.core.communication.connection.ConnectionState.CONNECTING -> "Connecting…"
        org.itantra.core.communication.connection.ConnectionState.DISCONNECTING -> "Disconnecting…"
        org.itantra.core.communication.connection.ConnectionState.DISCONNECTED -> "Offline"
        org.itantra.core.communication.connection.ConnectionState.FAILED -> "Disconnected"
    }

    /**
     * Formats local transceiver lifecycle state for diagnostic/capture display.
     */
    fun formatConnectionState(state: TransceiverState): String = when (state) {
        TransceiverState.RUNNING -> "Active"
        TransceiverState.STARTING -> "Starting…"
        TransceiverState.STOPPING -> "Stopping…"
        TransceiverState.STOPPED, TransceiverState.RELEASED -> "Idle"
        TransceiverState.FAILED -> "Error"
    }

    /**
     * Formats concise user-facing status string for the status chip.
     */
    fun formatStatusBanner(state: TransceiverState): String = when (state) {
        TransceiverState.RUNNING -> "Listening for speech…"
        TransceiverState.STARTING -> "Initializing neural models…"
        TransceiverState.STOPPING -> "Stopping transceiver…"
        TransceiverState.STOPPED -> "Ready"
        TransceiverState.RELEASED -> "Offline"
        TransceiverState.FAILED -> "Connection unavailable"
    }

    /**
     * Formats primary PTT action label.
     */
    fun formatPttLabel(state: TransceiverState): String = when (state) {
        TransceiverState.RUNNING -> "Tap to Stop"
        TransceiverState.STARTING -> "Initializing…"
        TransceiverState.STOPPING -> "Stopping…"
        TransceiverState.STOPPED, TransceiverState.RELEASED, TransceiverState.FAILED -> "Tap to Speak"
    }

    /**
     * Formats [TransceiverError] returning a pair of (Category Title, Detailed Message).
     */
    fun formatError(error: TransceiverError): Pair<String, String> {
        val category = when (error) {
            is TransceiverError.M1PipelineError -> "M1 Pipeline Error"
            is TransceiverError.M2TransportError -> "M2 Transport Error"
            is TransceiverError.M3TtsError -> "M3 TTS Error"
            is TransceiverError.M3PlaybackError -> "M3 Playback Error"
            is TransceiverError.LifecycleError -> "Lifecycle Error"
        }
        val detail = "[$category] ${error.message}"
        return Pair(category, detail)
    }

    /**
     * Identifies whether a [TransceiverError] represents the benign condition of an unbound
     * outbound transport in receiver-only mode, rather than a genuine physical network fault.
     */
    fun isReceiverOnlyUnboundTransportError(error: TransceiverError): Boolean {
        return error is TransceiverError.M2TransportError &&
                error.message.contains("no active MessageTransport bound", ignoreCase = true)
    }

    /**
     * Formats the Receiver listening hint with dynamic active IPv4 address.
     */
    fun formatReceiverListeningHint(ip: String?, port: Int = 42173): String {
        return if (ip.isNullOrBlank()) {
            "Listening on port $port — IP unavailable"
        } else {
            "Listening on $ip:$port"
        }
    }

    /**
     * Dynamically discovers the device's current active local IPv4 address from active network interfaces.
     * Rejects loopback (127.0.0.1), link-local (169.254.*), and IPv6 addresses.
     */
    fun getActiveLocalIpv4Address(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return null
            val candidates = mutableListOf<String>()
            for (iface in interfaces.asSequence()) {
                if (!iface.isUp || iface.isLoopback) continue
                val addrs = iface.inetAddresses
                for (addr in addrs.asSequence()) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress
                        if (hostAddress != null && hostAddress != "127.0.0.1" && !hostAddress.startsWith("169.254.")) {
                            val name = iface.name.lowercase()
                            if (name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("rndis") || name.startsWith("eth") || name.startsWith("swlan")) {
                                return hostAddress
                            }
                            candidates.add(hostAddress)
                        }
                    }
                }
            }
            candidates.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Formats a clean, actionable user-facing error message for connection failures.
     * Strips raw POSIX error codes (ECONNREFUSED, ETIMEDOUT, EHOSTUNREACH, etc.),
     * Java package prefixes (java.net.*, java.io.*), and raw socket stack traces.
     */
    fun formatUserFriendlyConnectionError(rawMessage: String?, targetHost: String? = null): String {
        val raw = rawMessage ?: ""
        val hostSuffix = if (!targetHost.isNullOrBlank()) " at $targetHost" else ""

        return when {
            raw.contains("ECONNREFUSED", ignoreCase = true) ||
            raw.contains("Connection refused", ignoreCase = true) -> {
                "Unable to connect to Receiver$hostSuffix. Check the IP address and make sure Receiver is running."
            }
            raw.contains("ETIMEDOUT", ignoreCase = true) ||
            raw.contains("timed out", ignoreCase = true) ||
            raw.contains("Timeout", ignoreCase = true) -> {
                "Connection to Receiver$hostSuffix timed out. Check that both devices are on the same Wi-Fi network."
            }
            raw.contains("EHOSTUNREACH", ignoreCase = true) ||
            raw.contains("ENETUNREACH", ignoreCase = true) ||
            raw.contains("No route to host", ignoreCase = true) -> {
                "Receiver$hostSuffix unreachable. Verify device Wi-Fi connection and IP address."
            }
            raw.contains("isConnected failed", ignoreCase = true) -> {
                "Unable to connect to Receiver$hostSuffix. Please verify Receiver is running."
            }
            raw.contains("no active MessageTransport bound", ignoreCase = true) -> {
                "Receiver Ready — Waiting for peer connection."
            }
            raw.contains("Socket disconnected", ignoreCase = true) ||
            raw.contains("link disconnected", ignoreCase = true) ||
            raw.contains("Socket closed", ignoreCase = true) -> {
                "Network transceiver link disconnected"
            }
            raw.isBlank() -> {
                "Unable to connect to Receiver$hostSuffix. Please verify network settings."
            }
            else -> {
                val cleaned = raw.replace(Regex("^[a-zA-Z0-9_.]+(?:Exception|Error):\\s*"), "")
                    .replace(Regex("failed to connect to /?[0-9.]+ \\(port \\d+\\)(?: from /?[0-9.]+ \\(port \\d+\\))?:?"), "")
                    .replace(Regex("connect failed:\\s*"), "")
                    .trim()
                if (cleaned.isNotBlank() && !cleaned.contains("ECONNREFUSED", ignoreCase = true) && !cleaned.contains("java.net", ignoreCase = true)) {
                    "Connection failed: $cleaned"
                } else {
                    "Unable to connect to Receiver$hostSuffix. Check the IP address and make sure Receiver is running."
                }
            }
        }
    }

    /**
     * Formats a clean, user-friendly error message without raw stack traces.
     */
    fun formatUserFriendlyError(error: TransceiverError, targetHost: String? = null): String = when (error) {
        is TransceiverError.M1PipelineError -> "Microphone capture or recognition unavailable"
        is TransceiverError.M2TransportError -> {
            if (isReceiverOnlyUnboundTransportError(error)) {
                "Receiver Ready — Waiting for Device A"
            } else {
                formatUserFriendlyConnectionError(error.message, targetHost)
            }
        }
        is TransceiverError.M3TtsError, is TransceiverError.M3PlaybackError -> "Audio synthesis / playback unavailable"
        is TransceiverError.LifecycleError -> "Transceiver engine lifecycle alert"
    }

    /**
     * Formats outbound transcribed text for display.
     */
    fun formatOutboundTranscript(message: SpeechMessage): String {
        return if (message.text.isNotBlank()) "\"${message.text.trim()}\"" else "[Empty Message]"
    }

    /**
     * Formats outbound transmission metadata.
     */
    fun formatOutboundMetrics(message: SpeechMessage): String {
        val confStr = message.confidence?.let { " | Conf: ${"%.2f".format(it)}" } ?: ""
        return "Outbound [${message.language}] @ ${message.timestamp}ms$confStr"
    }

    /**
     * Formats inbound received text for display.
     */
    fun formatInboundTranscript(message: SpeechMessage): String {
        val text = if (message.text.isNotBlank()) message.text.trim() else "[Empty]"
        return "[Remote]: \"$text\""
    }

    /**
     * Formats inbound metadata for display.
     */
    fun formatInboundMetrics(message: SpeechMessage): String {
        val confStr = message.confidence?.let { " | Conf: ${"%.2f".format(it)}" } ?: ""
        return "Inbound [${message.language}] @ ${message.timestamp}ms$confStr"
    }

    /**
     * Formats inbound TTS playback result for display.
     */
    fun formatPlaybackMetrics(audio: TtsAudio): String {
        val durationSec = if (audio.sampleRate > 0) audio.samples.size.toDouble() / audio.sampleRate else 0.0
        return "Played Inbound: ${audio.samples.size} samples (${"%.2f".format(durationSec)}s) @ ${audio.sampleRate}Hz"
    }

    /**
     * Formats clean playback confirmation string for the remote speech card.
     */
    fun formatPlaybackConfirmation(audio: TtsAudio): String {
        val durationSec = if (audio.sampleRate > 0) audio.samples.size.toDouble() / audio.sampleRate else 0.0
        return "🔊 Played via offline neural TTS (${"%.1f".format(durationSec)}s)"
    }
}
