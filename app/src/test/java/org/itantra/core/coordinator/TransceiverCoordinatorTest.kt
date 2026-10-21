package org.itantra.core.coordinator

import kotlinx.coroutines.runBlocking
import org.itantra.core.communication.adapter.SpeechMessageTransportAdapter
import org.itantra.core.communication.protocol.PacketEncoder
import org.itantra.core.communication.protocol.TransceiverPacketMapper
import org.itantra.core.communication.transport.MessageTransport
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.communication.transport.TransportException
import org.itantra.core.communication.transport.TransportFrame
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.pipeline.SpeechPipelineListener
import org.itantra.core.tts.SpeechMessagePlaybackCoordinator
import org.itantra.core.tts.TtsAudio
import org.itantra.core.tts.TtsEngine
import org.itantra.core.tts.TtsEngineState
import org.itantra.core.tts.UnsupportedTtsLanguageException
import org.itantra.core.tts.playback.AudioPlaybackEngine
import org.itantra.core.tts.playback.AudioPlaybackException
import org.itantra.core.tts.playback.AudioPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class TransceiverCoordinatorTest {

    private class FakePipelineControl(
        val shouldFailStart: Boolean = false
    ) : SpeechPipelineControl {
        var lastListener: SpeechPipelineListener? = null
        val startCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)
        val releaseCount = AtomicInteger(0)
        val isRunning = AtomicBoolean(false)

        override fun start(listener: SpeechPipelineListener): Result<Unit> {
            startCount.incrementAndGet()
            lastListener = listener
            if (shouldFailStart) {
                return Result.failure(IllegalStateException("Failed to acquire microphone audio hardware"))
            }
            isRunning.set(true)
            return Result.success(Unit)
        }

        override fun stop() {
            stopCount.incrementAndGet()
            isRunning.set(false)
        }

        override fun release() {
            releaseCount.incrementAndGet()
            stop()
        }

        fun emitSpeechMessage(message: SpeechMessage) {
            lastListener?.onSpeechMessage(message)
        }

        fun emitPipelineError(msg: String) {
            lastListener?.onError(msg)
        }
    }

    private class FakeMessageTransport(
        val localEndpoint: TransportEndpoint = TransportEndpoint("local-node")
    ) : MessageTransport {
        private var internalReceiver: (suspend (TransportFrame) -> Unit)? = null
        val sentFrames = mutableListOf<Pair<TransportEndpoint, ByteArray>>()
        var isClosed = false

        override suspend fun send(destination: TransportEndpoint, payload: ByteArray) {
            if (isClosed) throw TransportException("Transport is closed")
            sentFrames.add(Pair(destination, payload))
        }

        override fun setReceiver(receiver: (suspend (TransportFrame) -> Unit)?) {
            this.internalReceiver = receiver
        }

        override suspend fun close() {
            isClosed = true
            internalReceiver = null
        }

        suspend fun deliverFrame(payload: ByteArray, source: TransportEndpoint = TransportEndpoint("remote-node")) {
            internalReceiver?.invoke(TransportFrame(source = source, destination = localEndpoint, payload = payload))
        }
    }

    private class FakeTtsEngine : TtsEngine {
        val releaseCount = AtomicInteger(0)
        var throwOnSynthesize: Throwable? = null
        val synthesizedMessages = Collections.synchronizedList(mutableListOf<String>())

        override val state: TtsEngineState
            get() = if (releaseCount.get() > 0) TtsEngineState.RELEASED else TtsEngineState.READY

        override val supportedLanguages: Set<String> = setOf("en", "en-US", "en-us", "en_US")

        override fun isLanguageSupported(language: String): Boolean = language in supportedLanguages

        override fun initialize() {}

        override suspend fun synthesize(text: String, language: String, speed: Float): TtsAudio {
            throwOnSynthesize?.let { throw it }
            if (!isLanguageSupported(language)) throw UnsupportedTtsLanguageException(language)
            synthesizedMessages.add(text)
            return TtsAudio(FloatArray(16000) { 0.1f }, 16000)
        }

        override fun release() {
            releaseCount.incrementAndGet()
        }
    }

    private class FakePlaybackEngine : AudioPlaybackEngine {
        val releaseCount = AtomicInteger(0)
        val stopCount = AtomicInteger(0)
        private val _playCount = AtomicInteger(0)
        val playedAudios = Collections.synchronizedList(mutableListOf<TtsAudio>())
        var throwOnPlay: Throwable? = null

        val playCount: Int
            get() = _playCount.get()

        override val state: AudioPlaybackState
            get() = if (releaseCount.get() > 0) AudioPlaybackState.RELEASED else AudioPlaybackState.IDLE

        override suspend fun play(audio: TtsAudio) {
            throwOnPlay?.let { throw it }
            _playCount.incrementAndGet()
            playedAudios.add(audio)
        }

        override fun stop() {
            stopCount.incrementAndGet()
        }

        override fun release() {
            releaseCount.incrementAndGet()
        }
    }

    private class RecordingListener : TransceiverListener {
        val stateHistory = Collections.synchronizedList(mutableListOf<TransceiverState>())
        val outboundMessages = Collections.synchronizedList(mutableListOf<SpeechMessage>())
        val inboundMessages = Collections.synchronizedList(mutableListOf<SpeechMessage>())
        val playbackCompleted = Collections.synchronizedList(mutableListOf<Pair<SpeechMessage, TtsAudio>>())
        val errors = Collections.synchronizedList(mutableListOf<TransceiverError>())

        override fun onStateChanged(state: TransceiverState) {
            stateHistory.add(state)
        }

        override fun onOutboundSpeechMessage(message: SpeechMessage) {
            outboundMessages.add(message)
        }

        override fun onInboundSpeechMessage(message: SpeechMessage) {
            inboundMessages.add(message)
        }

        override fun onInboundPlaybackCompleted(message: SpeechMessage, audio: TtsAudio) {
            playbackCompleted.add(Pair(message, audio))
        }

        override fun onError(error: TransceiverError) {
            errors.add(error)
        }
    }

    private data class TestContext(
        val coordinator: TransceiverCoordinator,
        val pipeline: FakePipelineControl,
        val transport: FakeMessageTransport,
        val transportAdapter: SpeechMessageTransportAdapter,
        val ttsEngine: FakeTtsEngine,
        val playbackEngine: FakePlaybackEngine,
        val playbackCoordinator: SpeechMessagePlaybackCoordinator,
        val listener: RecordingListener
    )

    private fun createTestContext(
        shouldFailPipelineStart: Boolean = false,
        ownsDependencies: Boolean = false
    ): TestContext {
        val pipeline = FakePipelineControl(shouldFailStart = shouldFailPipelineStart)
        val transport = FakeMessageTransport()
        val defaultEndpoint = TransportEndpoint("remote-peer")
        val transportAdapter = SpeechMessageTransportAdapter(
            transport = transport,
            defaultDestination = defaultEndpoint
        )
        val ttsEngine = FakeTtsEngine()
        val playbackEngine = FakePlaybackEngine()
        val playbackCoordinator = SpeechMessagePlaybackCoordinator(
            ttsEngine = ttsEngine,
            playbackEngine = playbackEngine,
            ownsDependencies = ownsDependencies
        )
        val listener = RecordingListener()

        val coordinator = TransceiverCoordinator(
            speechPipeline = pipeline,
            transportAdapter = transportAdapter,
            playbackCoordinator = playbackCoordinator,
            listener = listener,
            ownsDependencies = ownsDependencies
        )

        return TestContext(
            coordinator = coordinator,
            pipeline = pipeline,
            transport = transport,
            transportAdapter = transportAdapter,
            ttsEngine = ttsEngine,
            playbackEngine = playbackEngine,
            playbackCoordinator = playbackCoordinator,
            listener = listener
        )
    }

    // 1. Initial State
    @Test
    fun `initial coordinator state is STOPPED and not running or released`() {
        val ctx = createTestContext()
        assertEquals(TransceiverState.STOPPED, ctx.coordinator.state)
        assertFalse(ctx.coordinator.isRunning)
        assertFalse(ctx.coordinator.isReleased)
        assertEquals(0, ctx.pipeline.startCount.get())
    }

    // 2. Successful Start
    @Test
    fun `successful start transitions to STARTING then RUNNING and starts M1 pipeline`() {
        val ctx = createTestContext()
        val result = ctx.coordinator.start()

        assertTrue(result.isSuccess)
        assertEquals(TransceiverState.RUNNING, ctx.coordinator.state)
        assertTrue(ctx.coordinator.isRunning)
        assertEquals(1, ctx.pipeline.startCount.get())
        assertEquals(listOf(TransceiverState.STARTING, TransceiverState.RUNNING), ctx.listener.stateHistory)
    }

    // 3. Repeated Start (Idempotent)
    @Test
    fun `repeated start when already running is an idempotent no-op`() {
        val ctx = createTestContext()
        ctx.coordinator.start()
        assertEquals(1, ctx.pipeline.startCount.get())

        val secondResult = ctx.coordinator.start()
        assertTrue(secondResult.isSuccess)
        assertEquals(1, ctx.pipeline.startCount.get())
        assertEquals(TransceiverState.RUNNING, ctx.coordinator.state)
    }

    // 4. Stop before Start
    @Test
    fun `stop before start is a safe idempotent no-op leaving state STOPPED`() {
        val ctx = createTestContext()
        ctx.coordinator.stop()

        assertEquals(TransceiverState.STOPPED, ctx.coordinator.state)
        assertEquals(0, ctx.pipeline.stopCount.get())
        assertEquals(0, ctx.listener.stateHistory.size)
    }

    // 5. Successful Stop
    @Test
    fun `successful stop transitions RUNNING to STOPPING then STOPPED and stops M1 pipeline`() {
        val ctx = createTestContext()
        ctx.coordinator.start()
        ctx.coordinator.stop()

        assertEquals(TransceiverState.STOPPED, ctx.coordinator.state)
        assertFalse(ctx.coordinator.isRunning)
        assertEquals(1, ctx.pipeline.stopCount.get())
        assertEquals(
            listOf(TransceiverState.STARTING, TransceiverState.RUNNING, TransceiverState.STOPPING, TransceiverState.STOPPED),
            ctx.listener.stateHistory
        )
    }

    // 6. Repeated Stop (Idempotent)
    @Test
    fun `repeated stop calls are safe and idempotent`() {
        val ctx = createTestContext()
        ctx.coordinator.start()
        ctx.coordinator.stop()
        assertEquals(1, ctx.pipeline.stopCount.get())

        ctx.coordinator.stop()
        assertEquals(1, ctx.pipeline.stopCount.get())
        assertEquals(TransceiverState.STOPPED, ctx.coordinator.state)
    }

    // 7. Start -> Stop -> Start
    @Test
    fun `coordinator can restart cleanly after stop`() {
        val ctx = createTestContext()
        ctx.coordinator.start()
        ctx.coordinator.stop()
        assertEquals(TransceiverState.STOPPED, ctx.coordinator.state)

        val restartResult = ctx.coordinator.start()
        assertTrue(restartResult.isSuccess)
        assertEquals(TransceiverState.RUNNING, ctx.coordinator.state)
        assertEquals(2, ctx.pipeline.startCount.get())
    }

    // 8. Local Outbound SpeechMessage reaches Transport and Listener
    @Test
    fun `local SpeechMessage emitted by M1 reaches transport and notifies listener`() = runBlocking {
        val ctx = createTestContext()
        ctx.coordinator.start()

        val message = SpeechMessage(
            text = "Reconnaissance squad in position.",
            language = "en",
            timestamp = 1700000000000L,
            confidence = 0.98f
        )

        // Simulate M1 emitting speech message
        ctx.pipeline.emitSpeechMessage(message)
        ctx.transportAdapter.emit(message)

        assertEquals(1, ctx.listener.outboundMessages.size)
        assertEquals(message, ctx.listener.outboundMessages.first())
        assertEquals(1, ctx.transport.sentFrames.size)
    }

    // 9. Remote Inbound SpeechMessage reaches M3 and Completes Playback
    @Test
    fun `inbound SpeechMessage received over transport routes through M3 to playback`() = runBlocking {
        val ctx = createTestContext()
        ctx.coordinator.start()

        val inboundMessage = SpeechMessage(
            text = "Understood. Maintain surveillance.",
            language = "en",
            timestamp = 1700000005000L,
            confidence = 0.95f
        )

        // Simulate remote peer sending packet over transport
        val seq = 100L
        val packet = TransceiverPacketMapper.fromSpeechMessage(inboundMessage, seq)
        val payload = PacketEncoder.encode(packet)

        // Deliver frame to transport receiver
        ctx.transport.deliverFrame(payload)

        assertEquals(1, ctx.listener.inboundMessages.size)
        val received = ctx.listener.inboundMessages.first()
        assertEquals("Understood. Maintain surveillance.", received.text)
        assertEquals("en", received.language)
        assertEquals(1700000005000L, received.timestamp)

        assertEquals(1, ctx.ttsEngine.synthesizedMessages.size)
        assertEquals("Understood. Maintain surveillance.", ctx.ttsEngine.synthesizedMessages.first())
        assertEquals(1, ctx.playbackEngine.playCount)
        assertEquals(1, ctx.listener.playbackCompleted.size)
        assertEquals("Understood. Maintain surveillance.", ctx.listener.playbackCompleted.first().first.text)
    }

    // 10. M1 Pipeline Startup Failure
    @Test
    fun `M1 pipeline startup failure transitions to FAILED and reports error`() {
        val ctx = createTestContext(shouldFailPipelineStart = true)
        val result = ctx.coordinator.start()

        assertTrue(result.isFailure)
        assertEquals(TransceiverState.FAILED, ctx.coordinator.state)
        assertFalse(ctx.coordinator.isRunning)
        assertEquals(1, ctx.listener.errors.size)
        assertTrue(ctx.listener.errors.first() is TransceiverError.M1PipelineError)
    }

    // 11. M1 Pipeline Runtime Error
    @Test
    fun `M1 pipeline runtime error is forwarded to listener`() {
        val ctx = createTestContext()
        ctx.coordinator.start()

        ctx.pipeline.emitPipelineError("VAD buffer overrun")

        assertEquals(1, ctx.listener.errors.size)
        val err = ctx.listener.errors.first()
        assertTrue(err is TransceiverError.M1PipelineError)
        assertTrue(err.message.contains("VAD buffer overrun"))
    }

    // 12. M3 Unsupported TTS Language Error
    @Test
    fun `inbound message with unsupported language emits M3TtsError`() = runBlocking {
        val ctx = createTestContext()
        ctx.coordinator.start()

        val hindiMessage = SpeechMessage(
            text = "आदेश का पालन करें",
            language = "hi",
            timestamp = 1700000010000L
        )

        val packet = TransceiverPacketMapper.fromSpeechMessage(hindiMessage, 101L)
        val payload = PacketEncoder.encode(packet)

        ctx.transport.deliverFrame(payload)

        assertEquals(1, ctx.listener.inboundMessages.size)
        assertEquals(1, ctx.listener.errors.size)
        val error = ctx.listener.errors.first()
        assertTrue(error is TransceiverError.M3TtsError)
        assertTrue(error.message.contains("Unsupported TTS language 'hi'"))
        assertEquals(0, ctx.playbackEngine.playCount)
    }

    // 13. M3 Playback Hardware Error
    @Test
    fun `inbound message encountering playback failure emits M3PlaybackError`() = runBlocking {
        val ctx = createTestContext()
        ctx.playbackEngine.throwOnPlay = AudioPlaybackException("Hardware DAC buffer underrun")
        ctx.coordinator.start()

        val msg = SpeechMessage(
            text = "Test playback failure",
            language = "en",
            timestamp = 1700000000000L
        )
        val payload = PacketEncoder.encode(TransceiverPacketMapper.fromSpeechMessage(msg, 1L))

        ctx.transport.deliverFrame(payload)

        assertEquals(1, ctx.listener.inboundMessages.size)
        assertEquals(1, ctx.listener.errors.size)
        val err = ctx.listener.errors.first()
        assertTrue(err is TransceiverError.M3PlaybackError)
        assertTrue(err.message.contains("Hardware DAC buffer underrun"))
    }

    // 14. Release Behavior & Dependency Ownership
    @Test
    fun `release with ownsDependencies false leaves child engines unreleased`() {
        val ctx = createTestContext(ownsDependencies = false)
        ctx.coordinator.start()
        ctx.coordinator.release()

        assertEquals(TransceiverState.RELEASED, ctx.coordinator.state)
        assertTrue(ctx.coordinator.isReleased)
        assertFalse(ctx.coordinator.isRunning)

        assertEquals(0, ctx.pipeline.releaseCount.get())
        assertEquals(0, ctx.ttsEngine.releaseCount.get())
        assertEquals(0, ctx.playbackEngine.releaseCount.get())

        // Start after release must fail
        val restart = ctx.coordinator.start()
        assertTrue(restart.isFailure)
    }

    @Test
    fun `release with ownsDependencies true releases M1, M2, and M3 child engines`() {
        val ctx = createTestContext(ownsDependencies = true)
        ctx.coordinator.start()
        ctx.coordinator.release()

        assertEquals(TransceiverState.RELEASED, ctx.coordinator.state)
        assertEquals(1, ctx.pipeline.releaseCount.get())
        assertEquals(1, ctx.ttsEngine.releaseCount.get())
        assertEquals(1, ctx.playbackEngine.releaseCount.get())
    }

    // 15. Inbound Message Processing While Idle / Stopped (Persistent Receiver)
    @Test
    fun `receiver remains registered and inbound frames received while coordinator is stopped are delivered to M3`() = runBlocking {
        val ctx = createTestContext()
        ctx.coordinator.start()
        ctx.coordinator.stop()
        assertEquals(TransceiverState.STOPPED, ctx.coordinator.state)

        val msg = SpeechMessage(
            text = "Message while stopped",
            language = "en",
            timestamp = 1700000000000L
        )
        val payload = PacketEncoder.encode(TransceiverPacketMapper.fromSpeechMessage(msg, 1L))

        // Inbound message delivered while local microphone/PTT is idle (STOPPED)
        ctx.transport.deliverFrame(payload)

        assertEquals(1, ctx.listener.inboundMessages.size)
        assertEquals("Message while stopped", ctx.listener.inboundMessages.first().text)
        assertEquals(1, ctx.ttsEngine.synthesizedMessages.size)
        assertEquals("Message while stopped", ctx.ttsEngine.synthesizedMessages.first())
        assertEquals(1, ctx.playbackEngine.playCount)
    }

    @Test
    fun `inbound frames received after coordinator release are not forwarded to M3`() = runBlocking {
        val ctx = createTestContext()
        ctx.coordinator.release()
        assertEquals(TransceiverState.RELEASED, ctx.coordinator.state)

        val msg = SpeechMessage(
            text = "Message while released",
            language = "en",
            timestamp = 1700000000000L
        )
        val payload = PacketEncoder.encode(TransceiverPacketMapper.fromSpeechMessage(msg, 1L))

        ctx.transport.deliverFrame(payload)

        assertEquals(0, ctx.listener.inboundMessages.size)
        assertEquals(0, ctx.ttsEngine.synthesizedMessages.size)
        assertEquals(0, ctx.playbackEngine.playCount)
    }

    // 16. Programmatic Outbound Send While Running
    @Test
    fun `programmatic sendOutboundSpeechMessage sends packet and notifies listener`() = runBlocking {
        val ctx = createTestContext()
        ctx.coordinator.start()

        val message = SpeechMessage(
            text = "Tactical waypoint reached.",
            language = "en",
            timestamp = 1700000020000L,
            confidence = 0.99f
        )

        val result = ctx.coordinator.sendOutboundSpeechMessage(message)
        assertTrue(result.isSuccess)
        assertEquals(1, ctx.transport.sentFrames.size)
        assertEquals(1, ctx.listener.outboundMessages.size)
        assertEquals("Tactical waypoint reached.", ctx.listener.outboundMessages.first().text)
    }

    // 17. Programmatic Send Fails When Not Running
    @Test
    fun `programmatic sendOutboundSpeechMessage fails when coordinator is STOPPED`() = runBlocking {
        val ctx = createTestContext()
        val message = SpeechMessage(
            text = "Cannot send while stopped.",
            language = "en",
            timestamp = 1700000020000L
        )

        val result = ctx.coordinator.sendOutboundSpeechMessage(message)
        assertTrue(result.isFailure)
        assertEquals(0, ctx.transport.sentFrames.size)
        assertEquals(1, ctx.listener.errors.size)
        assertTrue(ctx.listener.errors.first() is TransceiverError.LifecycleError)
    }

    // 18. Programmatic Send Transport Failure Emits M2TransportError
    @Test
    fun `programmatic send with closed transport emits M2TransportError`() = runBlocking {
        val ctx = createTestContext()
        ctx.coordinator.start()
        ctx.transport.isClosed = true

        val message = SpeechMessage(
            text = "Should fail due to closed transport.",
            language = "en",
            timestamp = 1700000020000L
        )

        val result = ctx.coordinator.sendOutboundSpeechMessage(message)
        assertTrue(result.isFailure)
        assertEquals(1, ctx.listener.errors.size)
        val err = ctx.listener.errors.first()
        assertTrue(err is TransceiverError.M2TransportError)
        assertTrue(err.message.contains("Transport transmission failed"))
    }

    // 19. M1 Pipeline Transport Error Mapped to M2TransportError
    @Test
    fun `M1 pipeline reporting Transport Error is mapped to TransceiverError M2TransportError`() {
        val ctx = createTestContext()
        ctx.coordinator.start()

        ctx.pipeline.emitPipelineError("Transport Error: Socket write timeout")

        assertEquals(1, ctx.listener.errors.size)
        val err = ctx.listener.errors.first()
        assertTrue(err is TransceiverError.M2TransportError)
        assertTrue(err.message.contains("Socket write timeout"))
    }

    // 20. Protocol Decode Failure Emits M2TransportError
    @Test
    fun `corrupt incoming packet emits M2TransportError to listener`() = runBlocking {
        val ctx = createTestContext()
        ctx.coordinator.start()

        // Send corrupt raw payload (less than header size or invalid magic)
        val corruptPayload = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        ctx.transport.deliverFrame(corruptPayload)

        assertEquals(0, ctx.listener.inboundMessages.size)
        assertEquals(1, ctx.listener.errors.size)
        val err = ctx.listener.errors.first()
        assertTrue(err is TransceiverError.M2TransportError)
        assertTrue(err.message.contains("Protocol decode error"))
    }

    // 21. Repeated Start Does Not Duplicate Inbound Dispatch
    @Test
    fun `repeated start does not cause duplicate message delivery to M3`() = runBlocking {
        val ctx = createTestContext()
        ctx.coordinator.start()
        ctx.coordinator.start() // Repeated start

        val msg = SpeechMessage(text = "Single delivery", language = "en", timestamp = 1700000000000L)
        val payload = PacketEncoder.encode(TransceiverPacketMapper.fromSpeechMessage(msg, 1L))
        ctx.transport.deliverFrame(payload)

        assertEquals(1, ctx.listener.inboundMessages.size)
        assertEquals(1, ctx.ttsEngine.synthesizedMessages.size)
        assertEquals(1, ctx.playbackEngine.playCount)
    }

    // 22. Start and Stop Race Converges Deterministically
    @Test
    fun `concurrent start and stop calls converge to deterministic state without deadlock`() {
        val ctx = createTestContext()
        val threads = (1..10).map { i ->
            Thread {
                if (i % 2 == 0) ctx.coordinator.start() else ctx.coordinator.stop()
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertTrue(ctx.coordinator.state == TransceiverState.RUNNING || ctx.coordinator.state == TransceiverState.STOPPED)
    }

    // 23. Release Closes Transport When ownsDependencies is True
    @Test
    fun `release with ownsDependencies true closes the transport adapter and transport`() {
        val ctx = createTestContext(ownsDependencies = true)
        ctx.coordinator.start()
        ctx.coordinator.release()

        assertTrue(ctx.transport.isClosed)
    }
}
