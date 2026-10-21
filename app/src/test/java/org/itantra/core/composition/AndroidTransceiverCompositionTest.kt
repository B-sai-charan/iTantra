package org.itantra.core.composition

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.itantra.core.communication.adapter.SpeechMessageTransportAdapter
import org.itantra.core.communication.connection.ConnectionConfig
import org.itantra.core.communication.connection.ConnectionState
import org.itantra.core.communication.connection.FakeConnectionManager
import org.itantra.core.communication.protocol.PacketEncoder
import org.itantra.core.communication.protocol.TransceiverPacketMapper
import org.itantra.core.communication.transport.LoopbackTransport
import org.itantra.core.communication.transport.MessageTransport
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.communication.transport.TransportFrame
import org.itantra.core.coordinator.DefaultSpeechPipelineControl
import org.itantra.core.coordinator.SpeechPipelineControl
import org.itantra.core.coordinator.TransceiverError
import org.itantra.core.coordinator.TransceiverListener
import org.itantra.core.coordinator.TransceiverState
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.pipeline.SpeechPipelineListener
import org.itantra.core.tts.SpeechMessagePlaybackCoordinator
import org.itantra.core.tts.TtsAudio
import org.itantra.core.tts.TtsEngine
import org.itantra.core.tts.TtsEngineState
import org.itantra.core.tts.playback.AudioPlaybackState
import org.itantra.core.tts.playback.FakeAudioPlaybackEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic JVM unit test suite verifying [AndroidTransceiverComposition]
 * construction, subsystem wiring, lifecycle control, ownership, and teardown order.
 */
class AndroidTransceiverCompositionTest {

    private val localEndpoint = TransportEndpoint("node-local")
    private val remoteEndpoint = TransportEndpoint("node-remote")

    private class FakePipelineControl : SpeechPipelineControl {
        var isStarted = false
        var isStopped = false
        var isReleased = false
        var listener: SpeechPipelineListener? = null

        override fun start(listener: SpeechPipelineListener): Result<Unit> {
            this.listener = listener
            isStarted = true
            return Result.success(Unit)
        }

        override fun stop() {
            isStopped = true
            isStarted = false
        }

        override fun release() {
            isReleased = true
            isStarted = false
        }
    }

    private class FakeTtsEngine : TtsEngine {
        val synthesizeCount = AtomicInteger(0)
        val releaseCount = AtomicInteger(0)

        override val state: TtsEngineState
            get() = if (releaseCount.get() > 0) TtsEngineState.RELEASED else TtsEngineState.READY

        override val supportedLanguages: Set<String> = setOf("en", "en-US", "en_US")

        override fun isLanguageSupported(language: String): Boolean = true
        override fun initialize() {}

        override suspend fun synthesize(text: String, language: String, speed: Float): TtsAudio {
            synthesizeCount.incrementAndGet()
            return TtsAudio(
                samples = FloatArray(1600) { 0.1f },
                sampleRate = 16000,
                channelCount = 1,
                sampleFormat = "PCM_FLOAT"
            )
        }

        override fun release() {
            releaseCount.incrementAndGet()
        }
    }

    private fun createTestComposition(
        pipelineControl: SpeechPipelineControl = FakePipelineControl(),
        ttsEngine: FakeTtsEngine = FakeTtsEngine(),
        playbackEngine: FakeAudioPlaybackEngine = FakeAudioPlaybackEngine(),
        transportAdapter: SpeechMessageTransportAdapter = SpeechMessageTransportAdapter(defaultDestination = remoteEndpoint),
        connectionManager: FakeConnectionManager? = null,
        listener: TransceiverListener? = null
    ): AndroidTransceiverComposition {
        val cm = connectionManager ?: FakeConnectionManager()
        return AndroidTransceiverComposition(
            localEndpoint = localEndpoint,
            defaultDestination = remoteEndpoint,
            listener = listener,
            injectedPipelineControl = pipelineControl,
            injectedTransportAdapter = transportAdapter,
            injectedConnectionManager = cm,
            injectedTtsEngine = ttsEngine,
            injectedPlaybackEngine = playbackEngine
        )
    }

    // 1. Verification of Subsystem Construction and Wiring
    @Test
    fun `composition instantiates and exposes all expected subsystems`() {
        val tts = FakeTtsEngine()
        val playback = FakeAudioPlaybackEngine()
        val composition = createTestComposition(ttsEngine = tts, playbackEngine = playback)

        assertNotNull(composition.coordinator)
        assertNotNull(composition.connectionManager)
        assertNotNull(composition.transportAdapter)
        assertNotNull(composition.playbackCoordinator)
        assertNotNull(composition.ttsEngine)
        assertNotNull(composition.playbackEngine)

        assertFalse(composition.isReleased)
        assertEquals(TransceiverState.STOPPED, composition.coordinator.state)
        assertEquals(ConnectionState.DISCONNECTED, composition.connectionManager.state)
    }

    // 2. Lifecycle: start and stop delegate cleanly
    @Test
    fun `start and stop delegate to coordinator and update state`() {
        val pipeline = FakePipelineControl()
        val composition = createTestComposition(pipelineControl = pipeline)

        val startResult = composition.start()
        assertTrue(startResult.isSuccess)
        assertTrue(pipeline.isStarted)
        assertEquals(TransceiverState.RUNNING, composition.coordinator.state)

        composition.stop()
        assertTrue(pipeline.isStopped)
        assertEquals(TransceiverState.STOPPED, composition.coordinator.state)
    }

    // 3. Network Lifecycle is independently controllable
    @Test
    fun `network connect and disconnect operate independently from coordinator`() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val cm = FakeConnectionManager(simulatedTransport = loopback)
        val composition = createTestComposition(connectionManager = cm)

        // Coordinator is STOPPED initially
        assertEquals(TransceiverState.STOPPED, composition.coordinator.state)

        // Connect network
        val connectRes = composition.connect()
        assertTrue(connectRes.isSuccess)
        assertEquals(ConnectionState.CONNECTED, composition.connectionManager.state)
        assertEquals(loopback, composition.transportAdapter.activeTransport)

        // Disconnect network
        composition.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, composition.connectionManager.state)
        assertNull(composition.transportAdapter.activeTransport)

        // Coordinator remains STOPPED throughout independent network toggling
        assertEquals(TransceiverState.STOPPED, composition.coordinator.state)
    }

    // 4. Inbound frame delivers to TTS and playback through composition
    @Test
    fun `inbound speech message across bound transport triggers TTS and playback`() = runBlocking {
        val localLoopback = LoopbackTransport(localEndpoint)
        val remoteLoopback = LoopbackTransport(remoteEndpoint)
        localLoopback.connectPeer(remoteLoopback)

        val cm = FakeConnectionManager(simulatedTransport = localLoopback)
        val tts = FakeTtsEngine()
        val playback = FakeAudioPlaybackEngine()

        var playbackCompleted = false
        val listener = object : TransceiverListener {
            override fun onInboundPlaybackCompleted(message: SpeechMessage, audio: TtsAudio) {
                playbackCompleted = true
            }
        }

        val composition = createTestComposition(
            connectionManager = cm,
            ttsEngine = tts,
            playbackEngine = playback,
            listener = listener
        )

        composition.connect()
        composition.start()

        // Send incoming speech frame from remote loopback peer to local
        val msg = SpeechMessage("Incoming tactical alert", "en", 1000L)
        val payload = PacketEncoder.encode(TransceiverPacketMapper.fromSpeechMessage(msg, 1L))
        remoteLoopback.send(localEndpoint, payload)

        assertTrue("TTS should have synthesized text", tts.synthesizeCount.get() > 0)
        assertEquals(1, playback.playCount)
        assertTrue("Playback completion listener should fire", playbackCompleted)

        composition.release()
    }

    // 5. Outbound speech message transmits over bound transport
    @Test
    fun `outbound speech message from pipeline transmits over active transport`() = runBlocking {
        val localLoopback = LoopbackTransport(localEndpoint)
        val remoteLoopback = LoopbackTransport(remoteEndpoint)
        localLoopback.connectPeer(remoteLoopback)

        val cm = FakeConnectionManager(simulatedTransport = localLoopback)
        val pipeline = FakePipelineControl()
        val adapter = SpeechMessageTransportAdapter(defaultDestination = remoteEndpoint)

        val receivedRemoteFrames = mutableListOf<TransportFrame>()
        remoteLoopback.setReceiver { frame -> receivedRemoteFrames.add(frame) }

        val composition = createTestComposition(
            pipelineControl = pipeline,
            connectionManager = cm,
            transportAdapter = adapter
        )

        composition.connect()
        composition.start()

        val msg = SpeechMessage("Outbound report", "en", 2000L)
        adapter.sendSpeechMessage(msg)

        assertEquals(1, receivedRemoteFrames.size)
        assertEquals(localEndpoint, receivedRemoteFrames.first().source)
        assertEquals(remoteEndpoint, receivedRemoteFrames.first().destination)

        composition.release()
    }

    // 6. Release cascades to all child dependencies in strict order
    @Test
    fun `release halts all subsystems and is idempotent`() = runBlocking {
        val pipeline = FakePipelineControl()
        val tts = FakeTtsEngine()
        val playback = FakeAudioPlaybackEngine()
        val cm = FakeConnectionManager(simulatedTransport = LoopbackTransport(localEndpoint))

        val composition = createTestComposition(
            pipelineControl = pipeline,
            ttsEngine = tts,
            playbackEngine = playback,
            connectionManager = cm
        )

        composition.start()
        composition.connect()

        assertFalse(composition.isReleased)

        // First release
        composition.release()

        assertTrue(composition.isReleased)
        assertTrue(pipeline.isReleased)
        assertEquals(1, tts.releaseCount.get())
        assertTrue(playback.isReleased)
        assertEquals(TransceiverState.RELEASED, composition.coordinator.state)
        assertEquals(ConnectionState.DISCONNECTED, composition.connectionManager.state)

        // Second release must be an idempotent safe no-op
        composition.release()
        assertTrue(composition.isReleased)
    }

    // 7. Operations after release fail explicitly
    @Test
    fun `operations after release fail with explicit IllegalStateException`() = runBlocking {
        val composition = createTestComposition()
        composition.release()

        val startRes = composition.start()
        assertTrue(startRes.isFailure)
        assertTrue(startRes.exceptionOrNull() is IllegalStateException)

        val connectRes = composition.connect()
        assertTrue(connectRes.isFailure)
        assertTrue(connectRes.exceptionOrNull() is IllegalStateException)

        val initRes = composition.initialize()
        assertTrue(initRes.isFailure)
        assertTrue(initRes.exceptionOrNull() is IllegalStateException)
    }

    // 8. Repeated initialize is idempotent and safe
    @Test
    fun `repeated initialize succeeds idempotently`() {
        val composition = createTestComposition()

        val res1 = composition.initialize()
        assertTrue(res1.isSuccess)

        val res2 = composition.initialize()
        assertTrue(res2.isSuccess)
    }

    // 9. Start and stop idempotence
    @Test
    fun `repeated start and repeated stop are safe and idempotent`() {
        val pipeline = FakePipelineControl()
        val composition = createTestComposition(pipelineControl = pipeline)

        // Stop when already stopped is safe
        composition.stop()
        assertEquals(TransceiverState.STOPPED, composition.coordinator.state)

        // First start
        val res1 = composition.start()
        assertTrue(res1.isSuccess)
        assertEquals(TransceiverState.RUNNING, composition.coordinator.state)

        // Second start is an idempotent success
        val res2 = composition.start()
        assertTrue(res2.isSuccess)
        assertEquals(TransceiverState.RUNNING, composition.coordinator.state)

        // First stop
        composition.stop()
        assertEquals(TransceiverState.STOPPED, composition.coordinator.state)

        // Second stop is safe
        composition.stop()
        assertEquals(TransceiverState.STOPPED, composition.coordinator.state)
    }

    // 10. Disconnect before connect is safe and idempotent
    @Test
    fun `disconnect before connect and repeated disconnect are safe`() = runBlocking {
        val composition = createTestComposition()

        // Disconnect when already disconnected
        composition.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, composition.connectionManager.state)

        // Connect
        val loopback = LoopbackTransport(localEndpoint)
        val cm = FakeConnectionManager(simulatedTransport = loopback)
        val compositionWithCm = createTestComposition(connectionManager = cm)

        compositionWithCm.connect()
        assertEquals(ConnectionState.CONNECTED, compositionWithCm.connectionManager.state)

        // First disconnect
        compositionWithCm.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, compositionWithCm.connectionManager.state)

        // Second disconnect
        compositionWithCm.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, compositionWithCm.connectionManager.state)
    }
}

