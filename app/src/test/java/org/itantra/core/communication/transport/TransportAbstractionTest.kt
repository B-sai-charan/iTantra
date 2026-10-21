package org.itantra.core.communication.transport

import kotlinx.coroutines.runBlocking
import org.itantra.core.communication.protocol.MessageType
import org.itantra.core.communication.protocol.PacketDecoder
import org.itantra.core.communication.protocol.PacketEncoder
import org.itantra.core.communication.protocol.ProtocolLanguage
import org.itantra.core.communication.protocol.TransceiverPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit test suite for the M2 transport abstraction layer and in-memory [LoopbackTransport].
 */
class TransportAbstractionTest {

    // ==========================================
    // 1. TRANSPORT ENDPOINT TESTS
    // ==========================================

    @Test
    fun testTransportEndpointValidId() {
        val endpoint = TransportEndpoint("node-tactical-01")
        assertEquals("node-tactical-01", endpoint.id)
        assertEquals("TransportEndpoint(node-tactical-01)", endpoint.toString())
    }

    @Test
    fun testTransportEndpointRejectsBlankId() {
        try {
            TransportEndpoint("")
            fail("Expected IllegalArgumentException for empty endpoint id")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("must not be blank"))
        }

        try {
            TransportEndpoint("   \t\n")
            fail("Expected IllegalArgumentException for blank endpoint id")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("must not be blank"))
        }
    }

    // ==========================================
    // 2. TRANSPORT FRAME & DEFENSIVE COPYING TESTS
    // ==========================================

    @Test
    fun testTransportFramePreservesPayloadBytes() {
        val source = TransportEndpoint("source-node")
        val destination = TransportEndpoint("dest-node")
        val rawPayload = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        val frame = TransportFrame(source = source, destination = destination, payload = rawPayload)

        assertEquals(source, frame.source)
        assertEquals(destination, frame.destination)
        assertEquals(4, frame.payloadSize)
        assertArrayEquals(rawPayload, frame.payload)
    }

    @Test
    fun testTransportFrameNullableEndpoints() {
        val rawPayload = byteArrayOf(0x10, 0x20)
        val frame = TransportFrame(source = null, destination = null, payload = rawPayload)

        assertNull(frame.source)
        assertNull(frame.destination)
        assertEquals(2, frame.payloadSize)
    }

    @Test
    fun testTransportFrameRejectsEmptyPayload() {
        try {
            TransportFrame(payload = byteArrayOf())
            fail("Expected IllegalArgumentException for empty payload")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("must not be empty"))
        }
    }

    @Test
    fun testTransportFrameProtectsPayloadFromExternalMutation() {
        val mutableInput = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val frame = TransportFrame(payload = mutableInput)

        // 1. Mutating input array after construction must not affect frame.payload
        mutableInput[0] = 0x00
        assertEquals(0xAA.toByte(), frame.payload[0])

        // 2. Mutating getter output must not affect internal frame state
        val exposedPayload = frame.payload
        exposedPayload[1] = 0x00
        assertEquals(0xBB.toByte(), frame.payload[1])
    }

    @Test
    fun testTransportFrameContentBasedEqualityAndHashCode() {
        val epA = TransportEndpoint("A")
        val epB = TransportEndpoint("B")

        val payload1 = byteArrayOf(0x01, 0x02, 0x03)
        val payload2 = byteArrayOf(0x01, 0x02, 0x03) // distinct array instance with same content

        val frame1 = TransportFrame(source = epA, destination = epB, payload = payload1)
        val frame2 = TransportFrame(source = epA, destination = epB, payload = payload2)

        assertEquals("Frames with identical content must be equal", frame1, frame2)
        assertEquals("Frames with identical content must have equal hashCodes", frame1.hashCode(), frame2.hashCode())

        val frame3 = TransportFrame(source = epA, destination = epB, payload = byteArrayOf(0x01, 0x02, 0x04))
        assertFalse("Frames with different payload content must not be equal", frame1 == frame3)
    }

    // ==========================================
    // 3. LOOPBACK TRANSPORT INTERACTION TESTS
    // ==========================================

    @Test
    fun testLoopbackSelfSendAndReceive() = runBlocking {
        val local = TransportEndpoint("node-local")
        val transport = LoopbackTransport(localEndpoint = local)

        var receivedFrame: TransportFrame? = null
        transport.setReceiver { frame ->
            receivedFrame = frame
        }

        val testBytes = byteArrayOf(0x00, 0x49, 0x54, 0xFF.toByte(), 0x7E)
        transport.send(destination = local, payload = testBytes)

        assertNotNull("Receiver should have been invoked", receivedFrame)
        assertEquals(local, receivedFrame!!.source)
        assertEquals(local, receivedFrame!!.destination)
        assertArrayEquals(testBytes, receivedFrame!!.payload)
        assertEquals(testBytes.size, receivedFrame!!.payloadSize)
    }

    @Test
    fun testLoopbackPeerToPeerExchange() = runBlocking {
        val nodeA = TransportEndpoint("node-alpha")
        val nodeB = TransportEndpoint("node-bravo")

        val transportA = LoopbackTransport(localEndpoint = nodeA)
        val transportB = LoopbackTransport(localEndpoint = nodeB)

        transportA.connectPeer(transportB)

        var receivedAtB: TransportFrame? = null
        transportB.setReceiver { frame ->
            receivedAtB = frame
        }

        var receivedAtA: TransportFrame? = null
        transportA.setReceiver { frame ->
            receivedAtA = frame
        }

        // A sends to B
        val msgFromA = byteArrayOf(0x01, 0x02, 0x03)
        transportA.send(destination = nodeB, payload = msgFromA)

        assertNotNull(receivedAtB)
        assertEquals(nodeA, receivedAtB!!.source)
        assertEquals(nodeB, receivedAtB!!.destination)
        assertArrayEquals(msgFromA, receivedAtB!!.payload)

        // B sends to A
        val msgFromB = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        transportB.send(destination = nodeA, payload = msgFromB)

        assertNotNull(receivedAtA)
        assertEquals(nodeB, receivedAtA!!.source)
        assertEquals(nodeA, receivedAtA!!.destination)
        assertArrayEquals(msgFromB, receivedAtA!!.payload)
    }

    @Test
    fun testLoopbackPreservesArbitraryBinaryBytes() = runBlocking {
        val transport = LoopbackTransport(TransportEndpoint("binary-test"))
        var receivedPayload: ByteArray? = null
        transport.setReceiver { frame ->
            receivedPayload = frame.payload
        }

        // Full 256-byte byte gamut (0x00 through 0xFF)
        val fullGamut = ByteArray(256) { it.toByte() }
        transport.send(destination = transport.localEndpoint, payload = fullGamut)

        assertNotNull(receivedPayload)
        assertArrayEquals(fullGamut, receivedPayload)
    }

    @Test
    fun testClosedTransportRejectsSend() = runBlocking {
        val transport = LoopbackTransport(TransportEndpoint("node-close-test"))
        transport.close()
        assertTrue(transport.closed)

        try {
            transport.send(transport.localEndpoint, byteArrayOf(0x01))
            fail("Expected TransportException on send over closed transport")
        } catch (e: TransportException) {
            assertTrue(e.message!!.contains("is closed"))
        }
    }

    @Test
    fun testReceiverNotInvokedAfterClose() = runBlocking {
        val nodeA = TransportEndpoint("A")
        val nodeB = TransportEndpoint("B")
        val transportA = LoopbackTransport(nodeA)
        val transportB = LoopbackTransport(nodeB)
        transportA.connectPeer(transportB)

        var receiverCalled = false
        transportB.setReceiver {
            receiverCalled = true
        }

        transportB.close()

        try {
            transportA.send(nodeB, byteArrayOf(0x01))
            fail("Expected TransportException sending to closed peer")
        } catch (e: TransportException) {
            assertTrue(e.message!!.contains("closed"))
        }

        assertFalse("Receiver must not be invoked after transport is closed", receiverCalled)
    }

    // ==========================================
    // 4. ARCHITECTURAL INTEGRATION TEST
    // ==========================================

    @Test
    fun testProtocolCodecAcrossTransportAbstraction() = runBlocking {
        // Construct representative domain packet
        val originalPacket = TransceiverPacket(
            version = 1,
            messageType = MessageType.SPEECH_TEXT,
            sequenceNumber = 105L,
            timestamp = 1726850000123L,
            language = ProtocolLanguage.HINDI,
            extendedLanguageTag = null,
            confidence = 92,
            text = "कुछ अणुओं में अस्थिर केंद्रक होता है"
        )

        // 1. Encode packet to binary wire bytes via PacketEncoder
        val wireBytes = PacketEncoder.encode(originalPacket)
        assertTrue(wireBytes.isNotEmpty())

        // 2. Transmit opaque wire bytes across MessageTransport abstraction
        val senderEndpoint = TransportEndpoint("tactical-radio-tx")
        val receiverEndpoint = TransportEndpoint("tactical-radio-rx")

        val senderTransport = LoopbackTransport(senderEndpoint)
        val receiverTransport = LoopbackTransport(receiverEndpoint)
        senderTransport.connectPeer(receiverTransport)

        var receivedWireBytes: ByteArray? = null
        receiverTransport.setReceiver { frame ->
            assertEquals(senderEndpoint, frame.source)
            assertEquals(receiverEndpoint, frame.destination)
            receivedWireBytes = frame.payload
        }

        senderTransport.send(destination = receiverEndpoint, payload = wireBytes)

        assertNotNull("Wire bytes should have traversed transport", receivedWireBytes)
        assertArrayEquals("Transport must deliver bit-exact byte stream", wireBytes, receivedWireBytes)

        // 3. Decode received wire bytes back into TransceiverPacket via PacketDecoder
        val decodedPacket = PacketDecoder.decode(receivedWireBytes!!)

        // 4. Verify complete logical equality
        assertEquals("Decoded packet must exactly equal original packet", originalPacket, decodedPacket)
        assertEquals(originalPacket.text, decodedPacket.text)
        assertEquals(originalPacket.language, decodedPacket.language)
        assertEquals(originalPacket.confidence, decodedPacket.confidence)
        assertEquals(originalPacket.sequenceNumber, decodedPacket.sequenceNumber)
    }
}
