package org.itantra.core.pipeline

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.itantra.core.audio.AudioCapture
import org.itantra.core.audio.AudioCaptureConfig
import org.itantra.core.audio.AudioCaptureListener
import org.itantra.core.audio.AudioCaptureMetrics
import org.itantra.core.audio.AudioCaptureState
import org.itantra.core.communication.adapter.SpeechMessageTransportAdapter
import org.itantra.core.communication.transport.LoopbackTransport
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.communication.transport.TransportException
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.domain.SpeechMessageMapper
import org.itantra.core.stt.AsrEngine
import org.itantra.core.stt.AsrResult
import org.itantra.core.vad.VadConfig
import org.itantra.core.vad.VadDetector
import org.itantra.core.vad.VadResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic unit tests for the M1 SpeechPipeline -> SpeechMessage -> SpeechMessageSink integration.
 */
class SpeechPipelineIntegrationTest {

    // ==========================================
    // 1. M1 ASRRESULT -> SPEECHMESSAGE MAPPING TESTS
    // ==========================================

    @Test
    fun testAsrResultToSpeechMessagePreservesAllFields() {
        val asrResult = AsrResult(
            text = "Tactical command center online",
            language = "en",
            confidence = 0.94f,
            inferenceTimeMs = 120L,
            audioDurationMs = 2500L
        )
        val explicitTimestamp = 1726850000000L

        val speechMessage = SpeechMessageMapper.fromAsrResult(asrResult, explicitTimestamp)

        assertEquals("Tactical command center online", speechMessage.text)
        assertEquals("en", speechMessage.language)
        assertEquals(explicitTimestamp, speechMessage.timestamp)
        assertEquals(0.94f, speechMessage.confidence!!, 0.001f)
    }

    @Test
    fun testAsrResultWithNullConfidenceMapsToNull() {
        val asrResult = AsrResult(
            text = "No confidence score",
            language = "hi",
            confidence = null,
            inferenceTimeMs = 80L,
            audioDurationMs = 1000L
        )

        val speechMessage = SpeechMessageMapper.fromAsrResult(asrResult, timestamp = 5000L)

        assertEquals("No confidence score", speechMessage.text)
        assertEquals("hi", speechMessage.language)
        assertEquals(5000L, speechMessage.timestamp)
        assertNull(speechMessage.confidence)
    }

    @Test
    fun testAsrResultWithHindiDevanagariPreservesUnicode() {
        val asrResult = AsrResult(
            text = "मिशन प्रारंभ करें",
            language = "hi",
            confidence = 0.89f,
            inferenceTimeMs = 150L,
            audioDurationMs = 3000L
        )

        val speechMessage = SpeechMessageMapper.fromAsrResult(asrResult, timestamp = 123456L)

        assertEquals("मिशन प्रारंभ करें", speechMessage.text)
        assertEquals("hi", speechMessage.language)
        assertEquals(123456L, speechMessage.timestamp)
        assertEquals(0.89f, speechMessage.confidence!!, 0.001f)
    }

    // ==========================================
    // 2. PIPELINE -> SPEECHMESSAGESINK INTEGRATION TESTS
    // ==========================================

    @Test
    fun testCompletedSegmentProducesExactlyOneSpeechMessageEmission() = runBlocking {
        val fakeAudioCapture = FakeAudioCapture()
        val fakeVadDetector = FakeVadDetector()
        val fakeAsrEngine = FakeAsrEngine(
            resultsToReturn = listOf(
                AsrResult("Alpha unit ready", "en", 0.95f, inferenceTimeMs = 50L, audioDurationMs = 1000L)
            )
        )

        val emittedMessages = ConcurrentLinkedQueue<SpeechMessage>()
        val sinkLatch = CountDownLatch(1)
        val sink = SpeechMessageSink { msg ->
            emittedMessages.add(msg)
            sinkLatch.countDown()
        }

        val localMessages = ConcurrentLinkedQueue<SpeechMessage>()
        val localResults = ConcurrentLinkedQueue<AsrResult>()
        val listener = object : BasePipelineListener() {
            override fun onAsrResult(result: AsrResult, postSpeechLatencyMs: Long, segmentIndex: Int) {
                localResults.add(result)
            }
            override fun onSpeechMessage(message: SpeechMessage) {
                localMessages.add(message)
            }
        }

        val pipeline = SpeechPipeline(
            messageSink = sink,
            injectedAudioCapture = fakeAudioCapture,
            injectedVadDetector = fakeVadDetector,
            injectedAsrEngine = fakeAsrEngine
        )
        pipeline.initialize()
        pipeline.start(listener)

        // Simulate speech segment: Speech start -> 500ms audio -> Speech end
        fakeVadDetector.nextResult = VadResult(isSpeech = true, probability = 0.9f, isSpeechStart = true, isSpeechEnd = false)
        fakeAudioCapture.emitAudio(ShortArray(3200)) // 200ms

        fakeVadDetector.nextResult = VadResult(isSpeech = true, probability = 0.9f, isSpeechStart = false, isSpeechEnd = false)
        fakeAudioCapture.emitAudio(ShortArray(4800)) // 300ms

        fakeVadDetector.nextResult = VadResult(isSpeech = false, probability = 0.1f, isSpeechStart = false, isSpeechEnd = true)
        fakeAudioCapture.emitAudio(ShortArray(1600)) // 100ms

        assertTrue("Sink must receive emitted SpeechMessage", sinkLatch.await(3, TimeUnit.SECONDS))
        assertEquals(1, emittedMessages.size)
        assertEquals(1, localMessages.size)
        assertEquals(1, localResults.size)

        val emitted = emittedMessages.peek()
        assertNotNull(emitted)
        assertEquals("Alpha unit ready", emitted!!.text)
        assertEquals("en", emitted.language)
        assertEquals(0.95f, emitted.confidence!!, 0.01f)

        pipeline.release()
    }

    @Test
    fun testNoTransportConfiguredPreservesLocalStt() = runBlocking {
        val fakeAudioCapture = FakeAudioCapture()
        val fakeVadDetector = FakeVadDetector()
        val fakeAsrEngine = FakeAsrEngine(
            resultsToReturn = listOf(
                AsrResult("Local standalone transcription", "en", 0.90f, 40L, 800L)
            )
        )

        val localMessages = ConcurrentLinkedQueue<SpeechMessage>()
        val localResults = ConcurrentLinkedQueue<AsrResult>()
        val latch = CountDownLatch(1)

        val listener = object : BasePipelineListener() {
            override fun onAsrResult(result: AsrResult, postSpeechLatencyMs: Long, segmentIndex: Int) {
                localResults.add(result)
            }
            override fun onSpeechMessage(message: SpeechMessage) {
                localMessages.add(message)
                latch.countDown()
            }
        }

        // Initialize pipeline with messageSink = null
        val pipeline = SpeechPipeline(
            messageSink = null,
            injectedAudioCapture = fakeAudioCapture,
            injectedVadDetector = fakeVadDetector,
            injectedAsrEngine = fakeAsrEngine
        )
        pipeline.initialize()
        pipeline.start(listener)

        // Trigger speech segment
        fakeVadDetector.nextResult = VadResult(isSpeech = true, probability = 0.9f, isSpeechStart = true, isSpeechEnd = false)
        fakeAudioCapture.emitAudio(ShortArray(4800)) // 300ms
        fakeVadDetector.nextResult = VadResult(isSpeech = false, probability = 0.1f, isSpeechStart = false, isSpeechEnd = true)
        fakeAudioCapture.emitAudio(ShortArray(1600))

        assertTrue("Local listener must receive SpeechMessage even when no transport is configured", latch.await(3, TimeUnit.SECONDS))
        assertEquals(1, localResults.size)
        assertEquals(1, localMessages.size)
        val msg = localMessages.peek()
        assertNotNull(msg)
        assertEquals("Local standalone transcription", msg!!.text)

        pipeline.release()
    }

    @Test
    fun testTransportFailureDoesNotCrashPipelineOrCorruptLocalStt() = runBlocking {
        val fakeAudioCapture = FakeAudioCapture()
        val fakeVadDetector = FakeVadDetector()
        val fakeAsrEngine = FakeAsrEngine(
            resultsToReturn = listOf(
                AsrResult("Segment 1 during transport failure", "en", 0.85f, 50L, 800L),
                AsrResult("Segment 2 after transport failure", "en", 0.92f, 50L, 800L)
            )
        )

        val errorReported = AtomicBoolean(false)
        val localMessages = ConcurrentLinkedQueue<SpeechMessage>()
        val latch = CountDownLatch(2)

        val listener = object : BasePipelineListener() {
            override fun onSpeechMessage(message: SpeechMessage) {
                localMessages.add(message)
                latch.countDown()
            }
            override fun onError(message: String) {
                if (message.contains("Transport Error")) {
                    errorReported.set(true)
                }
            }
        }

        // Failing sink that throws TransportException on emit
        val failingSink = SpeechMessageSink { msg ->
            if (msg.text.contains("Segment 1")) {
                throw TransportException("Simulated socket connection loss during send")
            }
        }

        val pipeline = SpeechPipeline(
            messageSink = failingSink,
            injectedAudioCapture = fakeAudioCapture,
            injectedVadDetector = fakeVadDetector,
            injectedAsrEngine = fakeAsrEngine
        )
        pipeline.initialize()
        pipeline.start(listener)

        // Emit Segment 1 (triggers transport failure)
        fakeVadDetector.nextResult = VadResult(isSpeech = true, probability = 0.9f, isSpeechStart = true, isSpeechEnd = false)
        fakeAudioCapture.emitAudio(ShortArray(4800))
        fakeVadDetector.nextResult = VadResult(isSpeech = false, probability = 0.1f, isSpeechStart = false, isSpeechEnd = true)
        fakeAudioCapture.emitAudio(ShortArray(1600))

        delay(100)

        // Emit Segment 2 (pipeline should still be alive and processing)
        fakeVadDetector.nextResult = VadResult(isSpeech = true, probability = 0.9f, isSpeechStart = true, isSpeechEnd = false)
        fakeAudioCapture.emitAudio(ShortArray(4800))
        fakeVadDetector.nextResult = VadResult(isSpeech = false, probability = 0.1f, isSpeechStart = false, isSpeechEnd = true)
        fakeAudioCapture.emitAudio(ShortArray(1600))

        assertTrue("Both segments must be processed locally despite transport failure on segment 1", latch.await(3, TimeUnit.SECONDS))
        assertTrue("Transport error must be reported to listener", errorReported.get())
        assertEquals(2, localMessages.size)

        pipeline.release()
    }

    @Test
    fun testEndToEndPipelineToSpeechMessageTransportAdapter() = runBlocking {
        val localEndpoint = TransportEndpoint("local-tx")
        val remoteEndpoint = TransportEndpoint("remote-rx")

        val txLoopback = LoopbackTransport(localEndpoint)
        val rxLoopback = LoopbackTransport(remoteEndpoint)
        txLoopback.connectPeer(rxLoopback)

        val txAdapter = SpeechMessageTransportAdapter(
            transport = txLoopback,
            defaultDestination = remoteEndpoint
        )
        val rxAdapter = SpeechMessageTransportAdapter(
            transport = rxLoopback,
            defaultDestination = localEndpoint
        )

        val receivedAtReceiver = ConcurrentLinkedQueue<SpeechMessage>()
        val receiverLatch = CountDownLatch(1)
        rxAdapter.setSpeechMessageReceiver { msg ->
            receivedAtReceiver.add(msg)
            receiverLatch.countDown()
        }

        val fakeAudioCapture = FakeAudioCapture()
        val fakeVadDetector = FakeVadDetector()
        val fakeAsrEngine = FakeAsrEngine(
            resultsToReturn = listOf(
                AsrResult("दस्ता आगे बढ़ रहा है", "hi", 0.93f, 60L, 1000L)
            )
        )

        // Inject txAdapter directly as the SpeechMessageSink
        val pipeline = SpeechPipeline(
            messageSink = txAdapter,
            injectedAudioCapture = fakeAudioCapture,
            injectedVadDetector = fakeVadDetector,
            injectedAsrEngine = fakeAsrEngine
        )
        pipeline.initialize()
        pipeline.start(BasePipelineListener())

        // Trigger Hindi speech utterance
        fakeVadDetector.nextResult = VadResult(isSpeech = true, probability = 0.9f, isSpeechStart = true, isSpeechEnd = false)
        fakeAudioCapture.emitAudio(ShortArray(4800))
        fakeVadDetector.nextResult = VadResult(isSpeech = false, probability = 0.1f, isSpeechStart = false, isSpeechEnd = true)
        fakeAudioCapture.emitAudio(ShortArray(1600))

        assertTrue("Received message across loopback transport must arrive", receiverLatch.await(3, TimeUnit.SECONDS))
        assertEquals(1, receivedAtReceiver.size)

        val received = receivedAtReceiver.peek()
        assertNotNull(received)
        assertEquals("दस्ता आगे बढ़ रहा है", received!!.text)
        assertEquals("hi", received.language)
        assertEquals(0.93f, received.confidence!!, 0.01f)

        pipeline.release()
    }

    // ==========================================
    // TEST DOUBLES / FAKES
    // ==========================================

    private open class BasePipelineListener : SpeechPipelineListener {
        override fun onSpeechStart() {}
        override fun onSpeechEnd(durationMs: Long) {}
        override fun onAsrStart(segmentDurationMs: Long) {}
        override fun onAsrResult(result: AsrResult, postSpeechLatencyMs: Long, segmentIndex: Int) {}
        override fun onSpeechMessage(message: SpeechMessage) {}
        override fun onVadProbability(probability: Float, isSpeech: Boolean) {}
        override fun onError(message: String) {}
        override fun onStateChanged(state: AudioCaptureState) {}
    }

    private class FakeAudioCapture : AudioCapture {
        override val metrics: AudioCaptureMetrics = AudioCaptureMetrics(0, 0, 0, 0, 0)
        override var state: AudioCaptureState = AudioCaptureState.STOPPED
            private set
        override val isRecording: Boolean
            get() = state == AudioCaptureState.RECORDING

        private var listener: AudioCaptureListener? = null

        override fun start(listener: AudioCaptureListener): Result<Unit> {
            this.listener = listener
            this.state = AudioCaptureState.RECORDING
            listener.onStateChanged(AudioCaptureState.RECORDING)
            return Result.success(Unit)
        }

        override fun stop() {
            this.state = AudioCaptureState.STOPPED
            listener?.onStateChanged(AudioCaptureState.STOPPED)
        }

        override fun release() {
            stop()
        }

        fun emitAudio(pcmChunk: ShortArray) {
            listener?.onAudioFrame(pcmChunk, pcmChunk.size)
        }
    }

    private class FakeVadDetector : VadDetector {
        override val config: VadConfig = VadConfig()
        override val isInitialized: Boolean = true
        override val totalInferences: Long = 0L
        override val averageInferenceLatencyMs: Double = 0.0

        var nextResult: VadResult = VadResult(isSpeech = false, probability = 0.0f)

        override fun process(samples: ShortArray): VadResult {
            return nextResult
        }

        override fun reset() {}

        override fun close() {}
    }

    private class FakeAsrEngine(
        private val resultsToReturn: List<AsrResult>
    ) : AsrEngine {
        private var callIndex = 0

        override suspend fun transcribe(audio: ShortArray, sampleRate: Int, language: String?): AsrResult {
            if (callIndex < resultsToReturn.size) {
                return resultsToReturn[callIndex++]
            }
            return AsrResult("Default fallback text", language ?: "en", 0.8f, 10L, 500L)
        }

        override fun close() {}
    }
}
