package org.itantra.ui

import org.itantra.core.communication.connection.ConnectionState
import org.itantra.core.coordinator.TransceiverError
import org.itantra.core.coordinator.TransceiverState
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.tts.TtsAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransceiverUiStateHolderTest {

    private lateinit var stateHolder: TransceiverUiStateHolder

    @Before
    fun setup() {
        stateHolder = TransceiverUiStateHolder()
    }

    @Test
    fun `initial state is configured with sensible defaults`() {
        val state = stateHolder.currentState
        assertEquals(ConnectionState.DISCONNECTED, state.connectionState)
        assertEquals(TransceiverState.STOPPED, state.transceiverState)
        assertEquals("SERVER", state.selectedRole)
        assertEquals("en", state.selectedLanguage)
        assertEquals("Ready", state.statusText)
        assertEquals("Tap to Speak", state.pttLabel)
        assertFalse(state.isPttActive)
        assertTrue(state.isPttEnabled)
        assertNull(state.errorText)
        assertFalse(state.isErrorVisible)
        assertEquals(0, state.outboundMessagesCount)
        assertEquals(0, state.inboundMessagesCount)
    }

    @Test
    fun `connection state update modifies connection properties without altering transcripts`() {
        // Given an existing transcript
        val dummyMsg = SpeechMessage(text = "Hello world", language = "en", timestamp = 1000L)
        stateHolder.onOutboundSpeechMessage(dummyMsg)
        assertEquals("\"Hello world\"", stateHolder.currentState.youTranscript)

        // When connection state changes
        stateHolder.onConnectionStateChanged(ConnectionState.CONNECTED, isServer = false)

        // Then
        assertEquals(ConnectionState.CONNECTED, stateHolder.currentState.connectionState)
        assertFalse(stateHolder.currentState.isServer)
        assertEquals("\"Hello world\"", stateHolder.currentState.youTranscript)
    }

    @Test
    fun `transceiver state update modifies operational status and ptt label`() {
        stateHolder.onTransceiverStateChanged(TransceiverState.RUNNING)

        assertEquals(TransceiverState.RUNNING, stateHolder.currentState.transceiverState)
        assertEquals("Listening for speech…", stateHolder.currentState.statusText)
        assertEquals("Tap to Stop", stateHolder.currentState.pttLabel)
        assertTrue(stateHolder.currentState.isPttActive)

        stateHolder.onTransceiverStateChanged(TransceiverState.STOPPED)
        assertEquals(TransceiverState.STOPPED, stateHolder.currentState.transceiverState)
        assertEquals("Ready", stateHolder.currentState.statusText)
        assertEquals("Tap to Speak", stateHolder.currentState.pttLabel)
        assertFalse(stateHolder.currentState.isPttActive)
    }

    @Test
    fun `outbound speech message increments counter and updates transcript and badge`() {
        val msg = SpeechMessage(text = "Tactical report 1", language = "en", timestamp = 5000L, confidence = 0.95f)
        stateHolder.onOutboundSpeechMessage(msg)

        val state = stateHolder.currentState
        assertEquals("\"Tactical report 1\"", state.youTranscript)
        assertEquals("EN", state.youLanguageBadge)
        assertEquals(1, state.outboundMessagesCount)
        assertEquals("Speech transmitted", state.statusText)
    }

    @Test
    fun `inbound speech message increments counter and updates remote card`() {
        val msg = SpeechMessage(text = "Received command from base", language = "hi", timestamp = 6000L)
        stateHolder.onInboundSpeechMessage(msg)

        val state = stateHolder.currentState
        assertEquals("[Remote]: \"Received command from base\"", state.remoteTranscript)
        assertEquals("HI", state.remoteLanguageBadge)
        assertEquals(1, state.inboundMessagesCount)
        assertFalse(state.isRemotePlaybackVisible)
    }

    @Test
    fun `playback completion reveals playback confirmation badge`() {
        val msg = SpeechMessage(text = "Hello", language = "hi", timestamp = 7000L)
        val audio = TtsAudio(samples = FloatArray(22050), sampleRate = 22050)

        stateHolder.onInboundPlaybackCompleted(msg, audio)

        val state = stateHolder.currentState
        assertTrue(state.isRemotePlaybackVisible)
        assertTrue(state.remotePlaybackStatus.contains("Played via offline neural TTS"))
    }

    @Test
    fun `error event sets error text and visibility without clearing conversation`() {
        val msg = SpeechMessage(text = "Message before error", language = "en", timestamp = 1000L)
        stateHolder.onOutboundSpeechMessage(msg)

        val error = TransceiverError.M2TransportError(message = "Socket disconnected", cause = IllegalStateException("Socket disconnected"))
        stateHolder.onError(error)

        val state = stateHolder.currentState
        assertTrue(state.isErrorVisible)
        assertEquals("Network transceiver link disconnected", state.errorText)
        assertEquals("\"Message before error\"", state.youTranscript)
    }

    @Test
    fun `language update adjusts selected language and outbound badge`() {
        stateHolder.onLanguageChanged("hi")

        val state = stateHolder.currentState
        assertEquals("hi", state.selectedLanguage)
        assertEquals("HI", state.youLanguageBadge)
    }

    @Test
    fun `role change updates selected role and server mode flag`() {
        stateHolder.onRoleChanged("CLIENT", isServer = false)

        val state = stateHolder.currentState
        assertEquals("CLIENT", state.selectedRole)
        assertFalse(state.isServer)
    }

    @Test
    fun `peer ip update is preserved in state`() {
        stateHolder.onPeerIpChanged("192.168.1.100")
        assertEquals("192.168.1.100", stateHolder.currentState.peerIp)
    }
}
