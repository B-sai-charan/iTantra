package org.itantra.core.communication.adapter

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.itantra.core.communication.protocol.PacketEncoder
import org.itantra.core.communication.protocol.TransceiverPacketMapper
import org.itantra.core.communication.protocol.toPacket
import org.itantra.core.communication.protocol.toSpeechMessage
import org.itantra.core.communication.transport.LoopbackTransport
import org.itantra.core.communication.transport.SocketMessageTransport
import org.itantra.core.communication.transport.TcpServerTransport
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.domain.SpeechMessage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic unit tests for [SpeechMessageTransportAdapter] and [TransceiverPacketMapper]
 * bidirectional conversion between M1 [SpeechMessage] and M2 wire transport.
 */
class SpeechMessageTransportAdapterTest {

    private val localEndpoint = TransportEndpoint("node-local")
    private val remoteEndpoint = TransportEndpoint("node-remote")

    // ==========================================
    // 1. MAPPER ROUND-TRIP UNIT TESTS
    // ==========================================

    @Test
    fun testSpeechMessageToPacketAndBackRoundTripEnglish() {
        val original = SpeechMessage(
            text = "Tactical command acknowledged",
            language = "en",
            timestamp = 1726850000123L,
            confidence = 0.95f
        )

        val packet = TransceiverPacketMapper.fromSpeechMessage(original, sequenceNumber = 1L)
        val reconstructed = TransceiverPacketMapper.toSpeechMessage(packet)

        assertEquals(original.text, reconstructed.text)
        assertEquals("en", reconstructed.language)
        assertEquals(original.timestamp, reconstructed.timestamp)
        assertEquals(0.95f, reconstructed.confidence!!, 0.01f)
    }

    @Test
    fun testSpeechMessageToPacketAndBackRoundTripHindi() {
        val original = SpeechMessage(
            text = "सामरिक संदेश प्राप्त हुआ",
            language = "hi",
            timestamp = 1726850000456L,
            confidence = 0.88f
        )

        val packet = original.toPacket(sequenceNumber = 42L)
        val reconstructed = packet.toSpeechMessage()

        assertEquals(original.text, reconstructed.text)
        assertEquals("hi", reconstructed.language)
        assertEquals(original.timestamp, reconstructed.timestamp)
        assertEquals(0.88f, reconstructed.confidence!!, 0.01f)
    }

    @Test
    fun testSpeechMessageExtendedLanguageTagPreservedVerbatim() {
        val tags = listOf("en-US", "en_GB", "hi-IN", "fr", "es-419", "zh-Hant")
        for (tag in tags) {
            val original = SpeechMessage(
                text = "Language test for $tag",
                language = tag,
                timestamp = 1726850000000L,
                confidence = 0.75f
            )

            val packet = original.toPacket(sequenceNumber = 10L)
            val reconstructed = packet.toSpeechMessage()

            assertEquals("Extended language tag '$tag' must be preserved verbatim", tag, reconstructed.language)
            assertEquals(original.text, reconstructed.text)
            assertEquals(original.timestamp, reconstructed.timestamp)
        }
    }

    @Test
    fun testConfidenceNullPreserved() {
        val original = SpeechMessage(
            text = "No confidence score",
            language = "en",
            timestamp = 1000L,
            confidence = null
        )

        val packet = original.toPacket(0L)
        assertNull(packet.confidence)

        val reconstructed = packet.toSpeechMessage()
        assertNull(reconstructed.confidence)
    }

    @Test
    fun testConfidenceZeroDistinguishableFromNull() {
        val original = SpeechMessage(
            text = "Zero confidence score",
            language = "en",
            timestamp = 1000L,
            confidence = 0.0f
        )

        val packet = original.toPacket(0L)
        assertEquals(0, packet.confidence)

        val reconstructed = packet.toSpeechMessage()
        assertEquals(0.0f, reconstructed.confidence!!, 0.001f)
    }

    @Test
    fun testEmptyTextRoundTrip() {
        val original = SpeechMessage(
            text = "",
            language = "en",
            timestamp = 2000L,
            confidence = 0.5f
        )

        val packet = original.toPacket(1L)
        assertEquals("", packet.text)

        val reconstructed = packet.toSpeechMessage()
        assertEquals("", reconstructed.text)
    }

    // ==========================================
    // 2. SEQUENCE NUMBER ALLOCATION TESTS
    // ==========================================

    @Test
    fun testSequenceNumberAllocationInitialAndIncrement() {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = remoteEndpoint, initialSequenceNumber = 0L)

        assertEquals(0L, adapter.nextSequenceNumber())
        assertEquals(1L, adapter.nextSequenceNumber())
        assertEquals(2L, adapter.nextSequenceNumber())
    }

    @Test
    fun testSequenceNumberWraparoundAtUInt32Max() {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(
            loopback,
            defaultDestination = remoteEndpoint,
            initialSequenceNumber = 0xFFFFFFFFL
        )

        assertEquals(0xFFFFFFFFL, adapter.nextSequenceNumber())
        // Wraparound to 0
        assertEquals(0L, adapter.nextSequenceNumber())
        assertEquals(1L, adapter.nextSequenceNumber())
    }

    // ==========================================
    // 3. ADAPTER OVER LOOPBACK TRANSPORT
    // ==========================================

    @Test
    fun testEndToEndSpeechMessageDeliveryOverLoopback() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val receivedDeferred = CompletableDeferred<SpeechMessage>()
        adapter.setSpeechMessageReceiver { message ->
            receivedDeferred.complete(message)
        }

        val original = SpeechMessage(
            text = "Loopback integration test message",
            language = "hi",
            timestamp = 1726851234567L,
            confidence = 0.91f
        )

        adapter.sendSpeechMessage(original)

        val received = withTimeout(2000) { receivedDeferred.await() }
        assertEquals(original.text, received.text)
        assertEquals(original.language, received.language)
        assertEquals(original.timestamp, received.timestamp)
        assertEquals(0.91f, received.confidence!!, 0.01f)
    }

    @Test
    fun testMultipleSequentialMessagesPreserveIndependentMetadata() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val count = 10
        val receivedQueue = ConcurrentLinkedQueue<SpeechMessage>()
        val latch = CountDownLatch(count)

        adapter.setSpeechMessageReceiver { msg ->
            receivedQueue.add(msg)
            latch.countDown()
        }

        val sentList = mutableListOf<SpeechMessage>()
        for (i in 0 until count) {
            val msg = SpeechMessage(
                text = "Utterance #$i",
                language = if (i % 2 == 0) "en" else "hi",
                timestamp = 1726850000000L + i * 1000L,
                confidence = 0.50f + (i * 0.04f)
            )
            sentList.add(msg)
            adapter.sendSpeechMessage(msg)
        }

        assertTrue("All $count messages must be received", latch.await(3, TimeUnit.SECONDS))
        assertEquals(count, receivedQueue.size)

        val receivedList = receivedQueue.toList()
        for (i in 0 until count) {
            val expected = sentList[i]
            val actual = receivedList[i]
            assertEquals(expected.text, actual.text)
            assertEquals(expected.language, actual.language)
            assertEquals(expected.timestamp, actual.timestamp)
            assertEquals(expected.confidence!!, actual.confidence!!, 0.01f)
        }
    }

    // ==========================================
    // 4. PROTOCOL ERROR & MALFORMED PACKET ISOLATION
    // ==========================================

    @Test
    fun testMalformedPacketIsDiscardedWithoutInvokingReceiver() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val invokedCount = AtomicInteger(0)
        adapter.setSpeechMessageReceiver {
            invokedCount.incrementAndGet()
        }

        // Send corrupt raw payload directly into transport
        val corruptPayload = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)
        loopback.send(localEndpoint, corruptPayload)

        delay(100)
        assertEquals("Malformed frame must be discarded without invoking SpeechMessage receiver", 0, invokedCount.get())

        // Now send valid message and verify adapter still operates
        val validMsg = SpeechMessage("Valid after corrupt", "en", 1000L, 0.9f)
        val validDeferred = CompletableDeferred<SpeechMessage>()
        adapter.setSpeechMessageReceiver { validDeferred.complete(it) }
        adapter.sendSpeechMessage(validMsg)

        val validReceived = withTimeout(1000) { validDeferred.await() }
        assertEquals(validMsg.text, validReceived.text)
    }

    @Test
    fun testCrcCorruptedPacketIsDiscardedWithoutInvokingReceiver() = runBlocking {
        val loopback = LoopbackTransport(localEndpoint)
        val adapter = SpeechMessageTransportAdapter(loopback, defaultDestination = localEndpoint)

        val invokedCount = AtomicInteger(0)
        adapter.setSpeechMessageReceiver {
            invokedCount.incrementAndGet()
        }

        val validMsg = SpeechMessage("CRC Test", "en", 1000L, 0.8f)
        val packet = validMsg.toPacket(0L)
        val wireBytes = PacketEncoder.encode(packet)

        // Corrupt text byte inside payload
        val corruptedBytes = wireBytes.copyOf()
        corruptedBytes[wireBytes.size - 5] = (corruptedBytes[wireBytes.size - 5].toInt() xor 0xFF).toByte()

        loopback.send(localEndpoint, corruptedBytes)
        delay(100)

        assertEquals("CRC-corrupted packet must not produce a SpeechMessage", 0, invokedCount.get())
    }

    // ==========================================
    // 5. END-TO-END PIPELINE OVER PHYSICAL TCP TRANSPORT
    // ==========================================

    @Test
    fun testFullPipelineOverTcpSockets() = runBlocking {
        val serverEndpoint = TransportEndpoint("tactical-server")
        val clientEndpoint = TransportEndpoint("tactical-client")

        // 1. Start TCP Server on ephemeral port
        val serverTransport = TcpServerTransport(localEndpoint = serverEndpoint, port = 0)
        serverTransport.start()
        val boundPort = serverTransport.boundPort

        // 2. Wrap in SpeechMessageTransportAdapter
        val serverAdapter = SpeechMessageTransportAdapter(serverTransport, defaultDestination = clientEndpoint)
        val serverReceivedDeferred = CompletableDeferred<SpeechMessage>()
        serverAdapter.setSpeechMessageReceiver { msg ->
            serverReceivedDeferred.complete(msg)
        }

        // 3. Connect TCP Client
        val clientTransport = SocketMessageTransport(
            localEndpoint = clientEndpoint,
            host = "127.0.0.1",
            port = boundPort
        )
        val clientAdapter = SpeechMessageTransportAdapter(clientTransport, defaultDestination = serverEndpoint)
        val clientReceivedDeferred = CompletableDeferred<SpeechMessage>()
        clientAdapter.setSpeechMessageReceiver { msg ->
            clientReceivedDeferred.complete(msg)
        }

        clientTransport.connect()
        delay(100)

        // 4. Transmit Client -> Server (Hindi SpeechMessage)
        val clientSpeech = SpeechMessage(
            text = "आपातकालीन प्रतिक्रिया दल आगे बढ़ें",
            language = "hi",
            timestamp = 1726859999000L,
            confidence = 0.96f
        )
        clientAdapter.sendSpeechMessage(clientSpeech)

        val receivedAtServer = withTimeout(3000) { serverReceivedDeferred.await() }
        assertEquals(clientSpeech.text, receivedAtServer.text)
        assertEquals(clientSpeech.language, receivedAtServer.language)
        assertEquals(clientSpeech.timestamp, receivedAtServer.timestamp)
        assertEquals(clientSpeech.confidence!!, receivedAtServer.confidence!!, 0.01f)

        // 5. Transmit Server -> Client (English SpeechMessage)
        val serverSpeech = SpeechMessage(
            text = "Emergency response team moving to sector 4",
            language = "en-US",
            timestamp = 1726859999500L,
            confidence = 0.92f
        )
        serverAdapter.sendSpeechMessage(serverSpeech)

        val receivedAtClient = withTimeout(3000) { clientReceivedDeferred.await() }
        assertEquals(serverSpeech.text, receivedAtClient.text)
        assertEquals("en-US", receivedAtClient.language)
        assertEquals(serverSpeech.timestamp, receivedAtClient.timestamp)
        assertEquals(serverSpeech.confidence!!, receivedAtClient.confidence!!, 0.01f)

        clientTransport.close()
        serverTransport.close()
    }
}
