package org.itantra.ui

import kotlinx.coroutines.runBlocking
import org.itantra.core.communication.connection.ConnectionConfig
import org.itantra.core.communication.connection.ConnectionState
import org.itantra.core.communication.connection.FakeConnectionManager
import org.itantra.core.communication.transport.LoopbackTransport
import org.itantra.core.communication.transport.TcpTransportConstants
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.composition.AndroidTransceiverComposition
import org.itantra.core.tts.SpeechMessagePlaybackCoordinator
import org.itantra.core.tts.TtsAudio
import org.itantra.core.tts.TtsEngine
import org.itantra.core.tts.TtsEngineState
import org.itantra.core.tts.playback.FakeAudioPlaybackEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying role selection, configuration generation, client connection,
 * and clean server-to-client transport transitions without leaving old transports active.
 */
class NetworkRoleSelectionTest {

    private val localEndpoint = TransportEndpoint("node-local")
    private val remoteEndpoint = TransportEndpoint("node-remote")

    private class FakeTts : TtsEngine {
        override val state: TtsEngineState get() = TtsEngineState.READY
        override val supportedLanguages: Set<String> = setOf("en")
        override fun isLanguageSupported(language: String): Boolean = true
        override fun initialize() {}
        override suspend fun synthesize(text: String, language: String, speed: Float): TtsAudio {
            return TtsAudio(FloatArray(160) { 0f }, 16000, 1, "PCM_FLOAT")
        }
        override fun release() {}
    }

    private fun createComposition(
        config: ConnectionConfig,
        cm: FakeConnectionManager
    ): AndroidTransceiverComposition {
        val tts = FakeTts()
        val playback = FakeAudioPlaybackEngine()
        val playbackCoordinator = SpeechMessagePlaybackCoordinator(
            ttsEngine = tts,
            playbackEngine = playback,
            ownsDependencies = false
        )
        return AndroidTransceiverComposition(
            localEndpoint = localEndpoint,
            defaultDestination = remoteEndpoint,
            connectionConfig = config,
            injectedConnectionManager = cm,
            injectedTtsEngine = tts,
            injectedPlaybackEngine = playback,
            injectedPlaybackCoordinator = playbackCoordinator
        )
    }

    @Test
    fun `receiver role creates valid Server ConnectionConfig`() {
        val serverConfig = ConnectionConfig.Server(
            port = TcpTransportConstants.DEFAULT_PORT
        )
        assertEquals(TcpTransportConstants.DEFAULT_PORT, serverConfig.port)
        assertEquals(1, serverConfig.backlog)
    }

    @Test
    fun `transmitter role with IP creates valid Client ConnectionConfig`() {
        val targetIp = "172.20.84.74"
        val clientConfig = ConnectionConfig.Client(
            host = targetIp,
            port = TcpTransportConstants.DEFAULT_PORT,
            timeoutMs = 8000
        )
        assertEquals("172.20.84.74", clientConfig.host)
        assertEquals(TcpTransportConstants.DEFAULT_PORT, clientConfig.port)
        assertEquals(8000, clientConfig.timeoutMs)
    }

    @Test
    fun `client connection establishes and binds active transport`() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val cm = FakeConnectionManager(simulatedTransport = loopback)

        val clientConfig = ConnectionConfig.Client(
            host = "172.20.84.74",
            port = TcpTransportConstants.DEFAULT_PORT,
            timeoutMs = 8000
        )

        val composition = createComposition(clientConfig, cm)

        // Initial state before connect
        assertEquals(ConnectionState.DISCONNECTED, composition.connectionManager.state)
        assertNull(composition.transportAdapter.activeTransport)

        // Connect client
        val connectResult = composition.connect()
        assertTrue(connectResult.isSuccess)
        assertEquals(ConnectionState.CONNECTED, composition.connectionManager.state)
        assertEquals(loopback, composition.transportAdapter.activeTransport)
    }

    @Test
    fun `switching from Server to Client stops and releases old server transport`() = runBlocking {
        val serverLoopback = LoopbackTransport(localEndpoint)
        val serverCm = FakeConnectionManager(simulatedTransport = serverLoopback)

        val serverComposition = createComposition(
            ConnectionConfig.Server(port = TcpTransportConstants.DEFAULT_PORT),
            serverCm
        )

        // Start server
        val serverConnectRes = serverComposition.connect()
        assertTrue(serverConnectRes.isSuccess)
        assertEquals(ConnectionState.CONNECTED, serverComposition.connectionManager.state)
        assertNotNull(serverComposition.transportAdapter.activeTransport)

        // Switch role: Release old server composition
        serverComposition.release()
        assertTrue(serverComposition.isReleased)
        assertEquals(ConnectionState.DISCONNECTED, serverCm.state)
        assertTrue(serverCm.releaseCount.get() > 0)

        // Create new Client composition
        val clientLoopback = LoopbackTransport(localEndpoint)
        val clientCm = FakeConnectionManager(simulatedTransport = clientLoopback)
        val clientComposition = createComposition(
            ConnectionConfig.Client(host = "172.20.84.74", port = TcpTransportConstants.DEFAULT_PORT),
            clientCm
        )

        // Connect client
        val clientConnectRes = clientComposition.connect()
        assertTrue(clientConnectRes.isSuccess)
        assertEquals(ConnectionState.CONNECTED, clientComposition.connectionManager.state)
        assertEquals(clientLoopback, clientComposition.transportAdapter.activeTransport)

        // Confirm server is completely dead and client is active
        assertTrue(serverComposition.isReleased)
        assertFalse(clientComposition.isReleased)
    }

    @Test
    fun `client connect reaches CONNECTED and allows immediate M2 SpeechMessage transmission`() = runBlocking {
        val clientLoopback = LoopbackTransport(localEndpoint)
        val remoteLoopback = LoopbackTransport(remoteEndpoint)
        clientLoopback.connectPeer(remoteLoopback)

        val clientCm = FakeConnectionManager(simulatedTransport = clientLoopback)
        val clientComposition = createComposition(
            ConnectionConfig.Client(host = "172.20.84.74", port = TcpTransportConstants.DEFAULT_PORT),
            clientCm
        )

        // 1. Connect
        val connectRes = clientComposition.connect()
        assertTrue(connectRes.isSuccess)
        assertEquals(ConnectionState.CONNECTED, clientComposition.connectionManager.state)
        assertEquals(clientLoopback, clientComposition.transportAdapter.activeTransport)

        // 2. Track received frame on remote peer
        var receivedPayload: ByteArray? = null
        remoteLoopback.setReceiver { frame ->
            receivedPayload = frame.payload
        }

        // 3. Transmit M2 SpeechMessage immediately after CONNECTED
        val testMessage = org.itantra.core.domain.SpeechMessage(
            text = "Tactical command check",
            language = "en",
            timestamp = System.currentTimeMillis(),
            confidence = 0.98f
        )
        clientComposition.transportAdapter.sendSpeechMessage(testMessage)

        // 4. Verify frame was delivered into remote peer and state is still CONNECTED
        assertNotNull(receivedPayload)
        assertTrue(receivedPayload!!.isNotEmpty())
        assertEquals(ConnectionState.CONNECTED, clientComposition.connectionManager.state)
        assertFalse(clientComposition.isReleased)
    }
}
