package org.itantra.core.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.itantra.core.communication.adapter.SpeechMessageTransportAdapter
import org.itantra.core.communication.protocol.MessageType
import org.itantra.core.communication.protocol.PacketEncoder
import org.itantra.core.communication.protocol.ProtocolConstants
import org.itantra.core.communication.protocol.ProtocolLanguage
import org.itantra.core.communication.protocol.TransceiverPacket
import org.itantra.core.communication.protocol.TransceiverPacketMapper
import org.itantra.core.communication.protocol.toPacket
import org.itantra.core.communication.transport.LoopbackTransport
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.domain.SpeechMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic unit tests verifying the M2 (Communication) -> M3 (TTS Presentation) boundary contract
 * via [SpeechMessageReceiver].
 */
class SpeechMessageReceiverBoundaryTest {

    private val localEndpoint = TransportEndpoint("local-node")
    private val remoteEndpoint = TransportEndpoint("remote-node")

    // ==========================================
    // 1. M2 -> M3 DATA INTEGRITY TESTS
    // ==========================================

    @Test
    fun testValidReceivedEnglishSpeechMessageReachesM3BoundaryUnchanged() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val receivedDeferred = CompletableDeferred<SpeechMessage>()
        adapter.setSpeechMessageReceiver(SpeechMessageReceiver { message ->
            receivedDeferred.complete(message)
        })

        val original = SpeechMessage(
            text = "Tactical command acknowledged",
            language = "en",
            timestamp = 1726850001000L,
            confidence = 0.95f
        )

        adapter.sendSpeechMessage(original)

        val received = withTimeout(2000) { receivedDeferred.await() }
        assertEquals("Tactical command acknowledged", received.text)
        assertEquals("en", received.language)
        assertEquals(1726850001000L, received.timestamp)
        assertEquals(0.95f, received.confidence!!, 0.01f)
    }

    @Test
    fun testValidReceivedHindiSpeechMessageReachesM3BoundaryUnchanged() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val receivedDeferred = CompletableDeferred<SpeechMessage>()
        adapter.setSpeechMessageReceiver(SpeechMessageReceiver { message ->
            receivedDeferred.complete(message)
        })

        val original = SpeechMessage(
            text = "आपातकालीन स्थिति में तुरंत रिपोर्ट करें",
            language = "hi",
            timestamp = 1726850002000L,
            confidence = 0.88f
        )

        adapter.sendSpeechMessage(original)

        val received = withTimeout(2000) { receivedDeferred.await() }
        assertEquals("आपातकालीन स्थिति में तुरंत रिपोर्ट करें", received.text)
        assertEquals("hi", received.language)
        assertEquals(1726850002000L, received.timestamp)
        assertEquals(0.88f, received.confidence!!, 0.01f)
    }

    @Test
    fun testExtendedLanguageTagPreservedAtM3Boundary() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val tags = listOf("en-US", "en_GB", "hi-IN", "fr", "es-419")
        for (tag in tags) {
            val receivedDeferred = CompletableDeferred<SpeechMessage>()
            adapter.setSpeechMessageReceiver(SpeechMessageReceiver { message ->
                receivedDeferred.complete(message)
            })

            val original = SpeechMessage(
                text = "Testing tag $tag",
                language = tag,
                timestamp = 1726850003000L,
                confidence = 0.80f
            )

            adapter.sendSpeechMessage(original)

            val received = withTimeout(2000) { receivedDeferred.await() }
            assertEquals("Extended tag must match exactly at M3 boundary", tag, received.language)
            assertEquals("Testing tag $tag", received.text)
        }
    }

    @Test
    fun testConfidenceNullAndZeroDistinctionAtM3Boundary() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        // 1. Confidence = null
        val nullDeferred = CompletableDeferred<SpeechMessage>()
        adapter.setSpeechMessageReceiver(SpeechMessageReceiver { message ->
            nullDeferred.complete(message)
        })
        adapter.sendSpeechMessage(SpeechMessage("Null confidence", "en", 1000L, null))
        val nullReceived = withTimeout(2000) { nullDeferred.await() }
        assertNull("Null confidence must reach M3 as null", nullReceived.confidence)

        // 2. Confidence = 0.0
        val zeroDeferred = CompletableDeferred<SpeechMessage>()
        adapter.setSpeechMessageReceiver(SpeechMessageReceiver { message ->
            zeroDeferred.complete(message)
        })
        adapter.sendSpeechMessage(SpeechMessage("Zero confidence", "en", 2000L, 0.0f))
        val zeroReceived = withTimeout(2000) { zeroDeferred.await() }
        assertNotNull("0.0 confidence must not become null", zeroReceived.confidence)
        assertEquals(0.0f, zeroReceived.confidence!!, 0.001f)
    }

    // ==========================================
    // 2. EMISSION & ERROR BOUNDARY TESTS
    // ==========================================

    @Test
    fun testExactlyOneDecodedPacketProducesExactlyOneDownstreamInvocation() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val receivedCount = AtomicInteger(0)
        val latch = CountDownLatch(1)
        adapter.setSpeechMessageReceiver(SpeechMessageReceiver {
            receivedCount.incrementAndGet()
            latch.countDown()
        })

        adapter.sendSpeechMessage(SpeechMessage("Single delivery test", "en", 1000L, 0.9f))

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        delay(50)
        assertEquals("Exactly one callback invocation must occur per decoded packet", 1, receivedCount.get())
    }

    @Test
    fun testMalformedPacketProducesNoDownstreamMessage() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val receivedCount = AtomicInteger(0)
        adapter.setSpeechMessageReceiver(SpeechMessageReceiver {
            receivedCount.incrementAndGet()
        })

        // Send invalid non-protocol garbage bytes directly into transport
        val garbageBytes = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44)
        loopback.send(localEndpoint, garbageBytes)

        delay(100)
        assertEquals("Malformed frame must produce 0 downstream messages", 0, receivedCount.get())
    }

    @Test
    fun testCrcFailureProducesNoDownstreamMessage() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val receivedCount = AtomicInteger(0)
        adapter.setSpeechMessageReceiver(SpeechMessageReceiver {
            receivedCount.incrementAndGet()
        })

        val original = SpeechMessage("CRC tamper test", "en", 1000L, 0.9f)
        val packet = original.toPacket(1L)
        val wireBytes = PacketEncoder.encode(packet)

        // Corrupt single payload byte
        wireBytes[wireBytes.size - 6] = (wireBytes[wireBytes.size - 6].toInt() xor 0xFF).toByte()

        loopback.send(localEndpoint, wireBytes)

        delay(100)
        assertEquals("CRC-corrupted packet must produce 0 downstream messages", 0, receivedCount.get())
    }

    @Test
    fun testTransportClosureProducesNoFabricatedMessages() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val receivedCount = AtomicInteger(0)
        adapter.setSpeechMessageReceiver(SpeechMessageReceiver {
            receivedCount.incrementAndGet()
        })

        // Close transport
        loopback.close()

        delay(50)
        assertEquals("Closing transport must produce 0 fabricated messages", 0, receivedCount.get())
    }

    @Test
    fun testDownstreamConsumerFailureDoesNotCorruptReceiverLifecycle() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val invocationCount = AtomicInteger(0)
        val secondMsgLatch = CountDownLatch(1)

        adapter.setSpeechMessageReceiver(SpeechMessageReceiver { msg ->
            val count = invocationCount.incrementAndGet()
            if (count == 1) {
                // Simulate unhandled M3 consumer exception (e.g. TTS engine audio buffer failure)
                throw RuntimeException("Simulated M3 TTS engine audio playback failure")
            } else if (count == 2) {
                secondMsgLatch.countDown()
            }
        })

        // 1. Send first message (will trigger exception in M3 callback)
        adapter.sendSpeechMessage(SpeechMessage("Message 1", "en", 1000L, 0.9f))
        delay(50)

        // 2. Send second message (must still be received cleanly by adapter)
        adapter.sendSpeechMessage(SpeechMessage("Message 2", "en", 2000L, 0.9f))

        assertTrue(
            "Second message must reach M3 despite downstream consumer exception on first message",
            secondMsgLatch.await(3, TimeUnit.SECONDS)
        )
        assertEquals(2, invocationCount.get())
    }

    // ==========================================
    // 3. CONTRACT PURITY TEST
    // ==========================================

    @Test
    fun testNoPacketMetadataLeaksIntoM3Contract() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val receivedDeferred = CompletableDeferred<SpeechMessage>()
        adapter.setSpeechMessageReceiver(SpeechMessageReceiver { message ->
            receivedDeferred.complete(message)
        })

        adapter.sendSpeechMessage(SpeechMessage("Contract purity verification", "en", 1726859000000L, 0.88f))

        val msg = withTimeout(2000) { receivedDeferred.await() }

        // Verify SpeechMessage only contains its 4 pure domain fields
        assertEquals("Contract purity verification", msg.text)
        assertEquals("en", msg.language)
        assertEquals(1726859000000L, msg.timestamp)
        assertEquals(0.88f, msg.confidence!!, 0.01f)

        // Verify SpeechMessage class declaration has exactly 4 public properties
        val properties = SpeechMessage::class.java.declaredFields.filter { !it.isSynthetic }
        assertEquals("SpeechMessage must contain exactly 4 domain properties", 4, properties.size)
        val fieldNames = properties.map { it.name }.toSet()
        assertEquals(setOf("text", "language", "timestamp", "confidence"), fieldNames)
    }
}
