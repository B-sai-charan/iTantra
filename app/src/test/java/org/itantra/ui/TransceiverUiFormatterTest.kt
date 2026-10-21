package org.itantra.ui

import org.itantra.core.coordinator.TransceiverError
import org.itantra.core.coordinator.TransceiverState
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.tts.TtsAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransceiverUiFormatterTest {

    @Test
    fun testFormatStateAllStates() {
        assertEquals("IDLE (Transceiver Stopped)", TransceiverUiFormatter.formatState(TransceiverState.STOPPED))
        assertEquals("STARTING TRANSCEIVER...", TransceiverUiFormatter.formatState(TransceiverState.STARTING))
        assertEquals("RUNNING (Transceiver Active)", TransceiverUiFormatter.formatState(TransceiverState.RUNNING))
        assertEquals("STOPPING TRANSCEIVER...", TransceiverUiFormatter.formatState(TransceiverState.STOPPING))
        assertEquals("RELEASED", TransceiverUiFormatter.formatState(TransceiverState.RELEASED))
        assertEquals("FAILED", TransceiverUiFormatter.formatState(TransceiverState.FAILED))
    }

    @Test
    fun testFormatConnectionStateTruthfulNetworkMapping() {
        assertEquals("Listening (42173)", TransceiverUiFormatter.formatConnectionState(org.itantra.core.communication.connection.ConnectionState.CONNECTED, isServer = true))
        assertEquals("Connected", TransceiverUiFormatter.formatConnectionState(org.itantra.core.communication.connection.ConnectionState.CONNECTED, isServer = false))
        assertEquals("Connecting…", TransceiverUiFormatter.formatConnectionState(org.itantra.core.communication.connection.ConnectionState.CONNECTING))
        assertEquals("Disconnecting…", TransceiverUiFormatter.formatConnectionState(org.itantra.core.communication.connection.ConnectionState.DISCONNECTING))
        assertEquals("Offline", TransceiverUiFormatter.formatConnectionState(org.itantra.core.communication.connection.ConnectionState.DISCONNECTED))
        assertEquals("Disconnected", TransceiverUiFormatter.formatConnectionState(org.itantra.core.communication.connection.ConnectionState.FAILED))
    }

    @Test
    fun testFormatErrorDistinguishesAllCategories() {
        val err1 = TransceiverError.M1PipelineError("Mic failure")
        val (cat1, detail1) = TransceiverUiFormatter.formatError(err1)
        assertEquals("M1 Pipeline Error", cat1)
        assertEquals("[M1 Pipeline Error] Mic failure", detail1)

        val err2 = TransceiverError.M2TransportError("Socket reset")
        val (cat2, detail2) = TransceiverUiFormatter.formatError(err2)
        assertEquals("M2 Transport Error", cat2)
        assertEquals("[M2 Transport Error] Socket reset", detail2)

        val err3 = TransceiverError.M3TtsError("Model not loaded")
        val (cat3, detail3) = TransceiverUiFormatter.formatError(err3)
        assertEquals("M3 TTS Error", cat3)
        assertEquals("[M3 TTS Error] Model not loaded", detail3)

        val err4 = TransceiverError.M3PlaybackError("AudioTrack error")
        val (cat4, detail4) = TransceiverUiFormatter.formatError(err4)
        assertEquals("M3 Playback Error", cat4)
        assertEquals("[M3 Playback Error] AudioTrack error", detail4)

        val err5 = TransceiverError.LifecycleError("Cannot start released coordinator")
        val (cat5, detail5) = TransceiverUiFormatter.formatError(err5)
        assertEquals("Lifecycle Error", cat5)
        assertEquals("[Lifecycle Error] Cannot start released coordinator", detail5)
    }

    @Test
    fun testReceiverOnlyUnboundTransportErrorFormatting() {
        val unboundErr = TransceiverError.M2TransportError(
            "Transport Error: Cannot send speech message: no active MessageTransport bound to SpeechMessageTransportAdapter"
        )
        assertTrue(TransceiverUiFormatter.isReceiverOnlyUnboundTransportError(unboundErr))
        assertEquals("Receiver Ready — Waiting for Device A", TransceiverUiFormatter.formatUserFriendlyError(unboundErr))

        val realErr = TransceiverError.M2TransportError("Connection refused to 10.0.0.5:42173")
        org.junit.Assert.assertFalse(TransceiverUiFormatter.isReceiverOnlyUnboundTransportError(realErr))
        val formatted = TransceiverUiFormatter.formatUserFriendlyError(realErr, "10.0.0.5")
        assertEquals("Unable to connect to Receiver at 10.0.0.5. Check the IP address and make sure Receiver is running.", formatted)
    }

    @Test
    fun testFormatReceiverListeningHint() {
        assertEquals("Listening on 192.168.1.50:42173", TransceiverUiFormatter.formatReceiverListeningHint("192.168.1.50", 42173))
        assertEquals("Listening on port 42173 — IP unavailable", TransceiverUiFormatter.formatReceiverListeningHint(null, 42173))
        assertEquals("Listening on port 42173 — IP unavailable", TransceiverUiFormatter.formatReceiverListeningHint("", 42173))
        assertEquals("Listening on port 42173 — IP unavailable", TransceiverUiFormatter.formatReceiverListeningHint("   ", 42173))
    }

    @Test
    fun testFormatUserFriendlyConnectionErrorSanitizesPosixAndJavaExceptions() {
        val refusedPosix = "java.net.ConnectException: failed to connect to /10.0.0.5 (port 42173) from /10.0.0.6 (port 55123): connect failed: ECONNREFUSED (Connection refused)"
        val sanitizedRefused = TransceiverUiFormatter.formatUserFriendlyConnectionError(refusedPosix, "10.0.0.5")
        assertEquals("Unable to connect to Receiver at 10.0.0.5. Check the IP address and make sure Receiver is running.", sanitizedRefused)

        val timeoutErr = "java.net.SocketTimeoutException: failed to connect to /10.0.0.5 (port 42173) after 8000ms: isConnected failed: ETIMEDOUT (Connection timed out)"
        val sanitizedTimeout = TransceiverUiFormatter.formatUserFriendlyConnectionError(timeoutErr, "10.0.0.5")
        assertEquals("Connection to Receiver at 10.0.0.5 timed out. Check that both devices are on the same Wi-Fi network.", sanitizedTimeout)

        val unreachableErr = "java.net.NoRouteToHostException: connect failed: EHOSTUNREACH (No route to host)"
        val sanitizedUnreachable = TransceiverUiFormatter.formatUserFriendlyConnectionError(unreachableErr, "10.0.0.5")
        assertEquals("Receiver at 10.0.0.5 unreachable. Verify device Wi-Fi connection and IP address.", sanitizedUnreachable)

        val isConnectedErr = "isConnected failed"
        val sanitizedIsConnected = TransceiverUiFormatter.formatUserFriendlyConnectionError(isConnectedErr, "10.0.0.5")
        assertEquals("Unable to connect to Receiver at 10.0.0.5. Please verify Receiver is running.", sanitizedIsConnected)
    }

    @Test
    fun testFormatOutboundTranscriptAndMetrics() {
        val msg = SpeechMessage(
            text = "Target acquired",
            language = "en",
            timestamp = 1000L,
            confidence = 0.95f
        )

        val transcript = TransceiverUiFormatter.formatOutboundTranscript(msg)
        assertEquals("\"Target acquired\"", transcript)

        val metrics = TransceiverUiFormatter.formatOutboundMetrics(msg)
        assertEquals("Outbound [en] @ 1000ms | Conf: 0.95", metrics)
    }

    @Test
    fun testFormatInboundTranscriptAndMetrics() {
        val msg = SpeechMessage(
            text = "Copy that",
            language = "hi",
            timestamp = 2000L,
            confidence = null
        )

        val transcript = TransceiverUiFormatter.formatInboundTranscript(msg)
        assertEquals("[Remote]: \"Copy that\"", transcript)

        val metrics = TransceiverUiFormatter.formatInboundMetrics(msg)
        assertEquals("Inbound [hi] @ 2000ms", metrics)
    }

    @Test
    fun testFormatPlaybackMetrics() {
        val audio = TtsAudio(
            samples = FloatArray(16000),
            sampleRate = 16000,
            channelCount = 1,
            sampleFormat = "PCM_FLOAT"
        )
        val playbackStr = TransceiverUiFormatter.formatPlaybackMetrics(audio)
        assertTrue(playbackStr.contains("16000 samples"))
        assertTrue(playbackStr.contains("1.00s"))
        assertTrue(playbackStr.contains("16000Hz"))
    }
}
