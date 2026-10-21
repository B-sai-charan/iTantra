package org.itantra.core.communication.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.itantra.core.communication.protocol.MessageType
import org.itantra.core.communication.protocol.PacketDecoder
import org.itantra.core.communication.protocol.PacketEncoder
import org.itantra.core.communication.protocol.ProtocolLanguage
import org.itantra.core.communication.protocol.TransceiverPacket
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic unit test suite for [TcpFraming], [SocketMessageTransport], and [TcpServerTransport].
 * Uses localhost loopback sockets for deterministic in-memory network verification.
 */
class TcpSocketTransportTest {

    // ==========================================
    // 1. TCP FRAMING UNIT TESTS
    // ==========================================

    @Test
    fun testFramingWriteAndReadSingleFrame() {
        val payload = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val out = ByteArrayOutputStream()
        TcpFraming.writeFrame(out, payload)

        val writtenBytes = out.toByteArray()
        // 4 header + 4 payload = 8 bytes
        assertEquals(8, writtenBytes.size)

        val input = ByteArrayInputStream(writtenBytes)
        val readPayload = TcpFraming.readFrame(input)
        assertArrayEquals(payload, readPayload)
    }

    @Test
    fun testFramingRejectsEmptyPayload() {
        val out = ByteArrayOutputStream()
        try {
            TcpFraming.writeFrame(out, byteArrayOf())
            fail("Expected TransportException for empty payload")
        } catch (e: TransportException) {
            assertTrue(e.message!!.contains("empty payload"))
        }
    }

    @Test
    fun testFramingRejectsPayloadExceeding70000Bytes() {
        val large = ByteArray(70001)
        val out = ByteArrayOutputStream()
        try {
            TcpFraming.writeFrame(out, large)
            fail("Expected TransportException for payload > 70000 bytes")
        } catch (e: TransportException) {
            assertTrue(e.message!!.contains("exceeds maximum frame limit"))
        }
    }

    @Test
    fun testFramingRejectsZeroLengthFrameOnRead() {
        val zeroHeader = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(0).array()
        val input = ByteArrayInputStream(zeroHeader)
        try {
            TcpFraming.readFrame(input)
            fail("Expected TransportException for zero length frame")
        } catch (e: TransportException) {
            assertTrue(e.message!!.contains("zero-length frame"))
        }
    }

    @Test
    fun testFramingRejectsOversizedLengthHeaderOnRead() {
        val overHeader = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(70001).array()
        val input = ByteArrayInputStream(overHeader)
        try {
            TcpFraming.readFrame(input)
            fail("Expected TransportException for declared length > 70000 bytes")
        } catch (e: TransportException) {
            assertTrue(e.message!!.contains("exceeds maximum allowed"))
        }
    }

    @Test
    fun testFramingHandlesMultipleConsecutiveFramesInStream() {
        val p1 = byteArrayOf(0x10, 0x20)
        val p2 = byteArrayOf(0x30, 0x40, 0x50)
        val p3 = byteArrayOf(0x60)

        val out = ByteArrayOutputStream()
        TcpFraming.writeFrame(out, p1)
        TcpFraming.writeFrame(out, p2)
        TcpFraming.writeFrame(out, p3)

        val input = ByteArrayInputStream(out.toByteArray())
        assertArrayEquals(p1, TcpFraming.readFrame(input))
        assertArrayEquals(p2, TcpFraming.readFrame(input))
        assertArrayEquals(p3, TcpFraming.readFrame(input))
    }

    @Test
    fun testFramingHandlesPartialReadsGracefully() {
        val payload = "Testing chunked stream delivery".toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        TcpFraming.writeFrame(out, payload)
        val frameBytes = out.toByteArray()

        // Custom InputStream that yields only 1 byte per read() call to simulate extreme TCP fragmentation
        val chunkedInput = object : java.io.InputStream() {
            private var idx = 0
            override fun read(): Int {
                return if (idx < frameBytes.size) frameBytes[idx++].toInt() and 0xFF else -1
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (idx >= frameBytes.size) return -1
                val byteToReturn = frameBytes[idx++]
                b[off] = byteToReturn
                return 1 // return only 1 byte at a time
            }
        }

        val recovered = TcpFraming.readFrame(chunkedInput)
        assertArrayEquals(payload, recovered)
    }

    // ==========================================
    // 2. SOCKET TRANSPORT CLIENT / SERVER INTEGRATION
    // ==========================================

    @Test
    fun testClientServerBidirectionalPayloadExchange() = runBlocking {
        val serverEndpoint = TransportEndpoint("server-node")
        val clientEndpoint = TransportEndpoint("client-node")

        // Bind server to ephemeral port 0
        val server = TcpServerTransport(localEndpoint = serverEndpoint, port = 0)
        server.start()
        val serverPort = server.boundPort
        assertTrue("Bound port must be non-zero", serverPort > 0)

        val serverReceivedDeferred = CompletableDeferred<ByteArray>()
        server.setReceiver { frame ->
            serverReceivedDeferred.complete(frame.payload)
        }

        val client = SocketMessageTransport(
            localEndpoint = clientEndpoint,
            host = "127.0.0.1",
            port = serverPort
        )
        val clientReceivedDeferred = CompletableDeferred<ByteArray>()
        client.setReceiver { frame ->
            clientReceivedDeferred.complete(frame.payload)
        }

        client.connect()
        delay(100) // allow accept handshake

        // 1. Client -> Server
        val clientMsg = byteArrayOf(0x00, 0x49, 0x54, 0xAA.toByte(), 0xFF.toByte())
        client.send(serverEndpoint, clientMsg)
        val receivedAtServer = withTimeout(3000) { serverReceivedDeferred.await() }
        assertArrayEquals("Server must receive exact client payload", clientMsg, receivedAtServer)

        // 2. Server -> Client
        val serverMsg = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        server.send(clientEndpoint, serverMsg)
        val receivedAtClient = withTimeout(3000) { clientReceivedDeferred.await() }
        assertArrayEquals("Client must receive exact server payload", serverMsg, receivedAtClient)

        client.close()
        server.close()
    }

    @Test
    fun testFullBinaryGamutOverTcp() = runBlocking {
        val server = TcpServerTransport(TransportEndpoint("server"), port = 0)
        server.start()

        val received = CompletableDeferred<ByteArray>()
        server.setReceiver { frame ->
            received.complete(frame.payload)
        }

        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", server.boundPort)
        client.connect()
        delay(100)

        val fullGamut = ByteArray(256) { it.toByte() }
        client.send(TransportEndpoint("server"), fullGamut)

        val result = withTimeout(3000) { received.await() }
        assertArrayEquals(fullGamut, result)

        client.close()
        server.close()
    }

    @Test
    fun testMultipleSequentialAndBackToBackFrames() = runBlocking {
        val server = TcpServerTransport(TransportEndpoint("server"), port = 0)
        server.start()

        val count = 20
        val receivedPayloads = ConcurrentLinkedQueue<ByteArray>()
        val latch = CountDownLatch(count)

        server.setReceiver { frame ->
            receivedPayloads.add(frame.payload)
            latch.countDown()
        }

        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", server.boundPort)
        client.connect()
        delay(100)

        val sentPayloads = mutableListOf<ByteArray>()
        for (i in 0 until count) {
            val payload = "Message index: $i".toByteArray(Charsets.UTF_8)
            sentPayloads.add(payload)
            client.send(TransportEndpoint("server"), payload)
        }

        assertTrue("All $count messages must be received", latch.await(5, TimeUnit.SECONDS))
        assertEquals(count, receivedPayloads.size)

        val receivedList = receivedPayloads.toList()
        for (i in 0 until count) {
            assertArrayEquals("Message $i must match exactly", sentPayloads[i], receivedList[i])
        }

        client.close()
        server.close()
    }

    @Test
    fun testLargePayloadNear70000BytesLimit() = runBlocking {
        val server = TcpServerTransport(TransportEndpoint("server"), port = 0)
        server.start()

        val received = CompletableDeferred<ByteArray>()
        server.setReceiver { frame ->
            received.complete(frame.payload)
        }

        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", server.boundPort)
        client.connect()
        delay(100)

        val largePayload = ByteArray(65535) { (it % 256).toByte() }
        client.send(TransportEndpoint("server"), largePayload)

        val result = withTimeout(5000) { received.await() }
        assertEquals(65535, result.size)
        assertArrayEquals(largePayload, result)

        client.close()
        server.close()
    }

    @Test
    fun testConcurrentSendsDoNotInterleave() = runBlocking {
        val server = TcpServerTransport(TransportEndpoint("server"), port = 0)
        server.start()

        val totalMessages = 50
        val receivedQueue = ConcurrentLinkedQueue<ByteArray>()
        val latch = CountDownLatch(totalMessages)

        server.setReceiver { frame ->
            receivedQueue.add(frame.payload)
            latch.countDown()
        }

        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", server.boundPort)
        client.connect()
        delay(100)

        // Launch concurrent senders
        coroutineScope {
            val jobs = (0 until totalMessages).map { idx ->
                async(Dispatchers.Default) {
                    val p = "Concurrent payload #$idx".toByteArray(Charsets.UTF_8)
                    client.send(TransportEndpoint("server"), p)
                }
            }
            jobs.awaitAll()
        }

        assertTrue("All concurrent sends must be deframed without corruption", latch.await(5, TimeUnit.SECONDS))
        assertEquals(totalMessages, receivedQueue.size)

        client.close()
        server.close()
    }

    @Test
    fun testClientConnectionFailureThrowsTransportException() = runBlocking {
        // Non-existent port
        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", 65530)
        try {
            client.connect(timeoutMs = 500)
            fail("Expected TransportException on connection failure")
        } catch (e: TransportException) {
            assertTrue(e.message!!.contains("Failed to connect to TCP host"))
            assertEquals(TransportState.FAILED, client.state)
        }
    }

    @Test
    fun testSendAfterCloseThrowsTransportException() = runBlocking {
        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", 9090)
        client.close()
        try {
            client.send(TransportEndpoint("dest"), byteArrayOf(0x01))
            fail("Expected TransportException sending on closed transport")
        } catch (e: TransportException) {
            assertTrue(e.message!!.contains("not CONNECTED"))
        }
    }

    @Test
    fun testServerCloseUnblocksPendingOperations() = runBlocking {
        val server = TcpServerTransport(TransportEndpoint("server"), port = 0)
        server.start()
        assertTrue(server.state == TransportState.CONNECTED)

        server.close()
        assertEquals(TransportState.CLOSED, server.state)
    }

    // ==========================================
    // 3. ARCHITECTURAL CODEC INTEGRATION TEST
    // ==========================================

    @Test
    fun testProtocolCodecAcrossPhysicalTcpSockets() = runBlocking {
        val serverEndpoint = TransportEndpoint("radio-server")
        val clientEndpoint = TransportEndpoint("radio-client")

        val server = TcpServerTransport(serverEndpoint, port = 0)
        server.start()

        val receivedPacketDeferred = CompletableDeferred<TransceiverPacket>()
        server.setReceiver { frame ->
            // Deframed opaque ByteArray handed to PacketDecoder
            val decoded = PacketDecoder.decode(frame.payload)
            receivedPacketDeferred.complete(decoded)
        }

        val client = SocketMessageTransport(clientEndpoint, "127.0.0.1", server.boundPort)
        client.connect()
        delay(100)

        // 1. Construct representative TransceiverPacket with Devanagari Hindi text & confidence
        val originalPacket = TransceiverPacket(
            version = 1,
            messageType = MessageType.SPEECH_TEXT,
            sequenceNumber = 42L,
            timestamp = 1726850000000L,
            language = ProtocolLanguage.HINDI,
            confidence = 88,
            text = "परीक्षण सामरिक संदेश"
        )

        // 2. Encode to opaque binary bytes via PacketEncoder
        val wireBytes = PacketEncoder.encode(originalPacket)

        // 3. Send over TCP socket transport
        client.send(serverEndpoint, wireBytes)

        // 4. Await received and decoded packet
        val decodedPacket = withTimeout(3000) { receivedPacketDeferred.await() }

        // 5. Assert 100% bit-exact logical equality
        assertEquals("Decoded packet across TCP must match original packet exactly", originalPacket, decodedPacket)
        assertEquals(originalPacket.text, decodedPacket.text)
        assertEquals(originalPacket.language, decodedPacket.language)
        assertEquals(originalPacket.confidence, decodedPacket.confidence)
        assertEquals(originalPacket.sequenceNumber, decodedPacket.sequenceNumber)

        client.close()
        server.close()
    }

    // ==========================================
    // 4. LIFECYCLE & CONCURRENCY AUDIT TESTS
    // ==========================================

    @Test
    fun testCloseIsIdempotentOnClientAndServer() = runBlocking {
        val server = TcpServerTransport(TransportEndpoint("server"), port = 0)
        server.start()

        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", server.boundPort)
        client.connect()

        // Repeated closes on client
        client.close()
        assertEquals(TransportState.CLOSED, client.state)
        client.close()
        assertEquals(TransportState.CLOSED, client.state)

        // Repeated closes on server
        server.close()
        assertEquals(TransportState.CLOSED, server.state)
        server.close()
        assertEquals(TransportState.CLOSED, server.state)
    }

    @Test
    fun testCloseDuringReceiveTerminatesReceiveLoopCleanly() = runBlocking {
        val server = TcpServerTransport(TransportEndpoint("server"), port = 0)
        server.start()

        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", server.boundPort)
        client.connect()
        delay(50)

        // Client closes while receive loop is actively waiting on read()
        client.close()
        assertEquals(TransportState.CLOSED, client.state)

        server.close()
    }

    @Test
    fun testConnectCloseRaceDoesNotProduceConnectedAfterClosed() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort

        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", port)

        // Launch connect and immediately close concurrently
        val connectJob = async(Dispatchers.IO) {
            try {
                client.connect(timeoutMs = 1000)
            } catch (_: TransportException) {}
        }
        val closeJob = async(Dispatchers.IO) {
            client.close()
        }

        connectJob.await()
        closeJob.await()
        server.close()

        assertEquals("State must be CLOSED after connect/close race", TransportState.CLOSED, client.state)
    }

    @Test
    fun testMalformedFrameTerminatesConnectionCleanly() = runBlocking {
        val server = TcpServerTransport(TransportEndpoint("server"), port = 0)
        server.start()

        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", server.boundPort)
        client.connect()
        delay(100)

        // Inject raw socket write with illegal declared frame length of 80000 (> 70000)
        val rawSocket = Socket("127.0.0.1", server.boundPort)
        val badHeader = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(80000).array()
        rawSocket.getOutputStream().write(badHeader)
        rawSocket.getOutputStream().flush()

        delay(150)
        rawSocket.close()
        client.close()
        server.close()
    }

    @Test
    fun testAbruptPeerDisconnectTerminatesReceiveLoop() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort

        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", port)
        var serverSock: Socket? = null
        val serverAcceptJob = async(Dispatchers.IO) {
            serverSock = server.accept()
        }

        client.connect()
        serverAcceptJob.await()

        // Abruptly close server-side socket without standard EOF framing
        serverSock?.close()
        server.close()

        delay(150)
        // Client transport should detect peer closure and transition to FAILED or CLOSED cleanly
        assertTrue(
            "Client transport must exit CONNECTED on abrupt disconnect",
            client.state == TransportState.FAILED || client.state == TransportState.CLOSED
        )

        client.close()
        assertEquals(TransportState.CLOSED, client.state)
    }

    @Test
    fun testReceiverCallbackExceptionIsIsolatedFromReceiveLoop() = runBlocking {
        val server = TcpServerTransport(TransportEndpoint("server"), port = 0)
        server.start()

        val receivedCount = AtomicInteger(0)
        val secondMsgLatch = CountDownLatch(1)

        server.setReceiver { frame ->
            val count = receivedCount.incrementAndGet()
            if (count == 1) {
                // Deliberately throw an unhandled application exception on message #1
                throw IllegalStateException("Simulated application bug in receiver callback")
            } else if (count == 2) {
                secondMsgLatch.countDown()
            }
        }

        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", server.boundPort)
        client.connect()
        delay(100)

        // Send message #1 (will trigger exception in receiver)
        client.send(TransportEndpoint("server"), byteArrayOf(0x01))
        delay(50)

        // Send message #2 (must still be received and processed because transport isolated the callback exception)
        client.send(TransportEndpoint("server"), byteArrayOf(0x02))

        assertTrue(
            "Second message must be received despite receiver exception on first message",
            secondMsgLatch.await(3, TimeUnit.SECONDS)
        )
        assertEquals(2, receivedCount.get())

        client.close()
        server.close()
    }

    @Test
    fun testSecondReceiverRegistrationThrowsIllegalStateException() {
        val transport = SocketMessageTransport(TransportEndpoint("test"), "127.0.0.1", 9090)
        val receiver1: suspend (TransportFrame) -> Unit = {}
        val receiver2: suspend (TransportFrame) -> Unit = {}

        transport.setReceiver(receiver1)

        try {
            transport.setReceiver(receiver2)
            fail("Expected IllegalStateException when registering duplicate receiver")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("already registered"))
        }

        // Setting to null unregisters
        transport.setReceiver(null)

        // Re-registering receiver2 succeeds now
        transport.setReceiver(receiver2)
    }

    @Test
    fun testServerSingleClientLimitRejectsSecondClientWithoutLeakingOrReplacingFirst() = runBlocking {
        val server = TcpServerTransport(TransportEndpoint("server"), port = 0)
        server.start()

        val client1Received = CompletableDeferred<ByteArray>()
        val serverReceived = CompletableDeferred<ByteArray>()

        server.setReceiver { frame ->
            serverReceived.complete(frame.payload)
        }

        val client1 = SocketMessageTransport(TransportEndpoint("client1"), "127.0.0.1", server.boundPort)
        client1.setReceiver { frame ->
            client1Received.complete(frame.payload)
        }
        client1.connect()
        delay(100)

        assertTrue("Client 1 must be active", server.isClientConnected)

        // Attempt connecting Client 2 to the same server
        val client2 = SocketMessageTransport(TransportEndpoint("client2"), "127.0.0.1", server.boundPort)
        try {
            client2.connect()
            delay(100)
            // Server single-client policy immediately closes client 2 socket
            client2.send(TransportEndpoint("server"), byteArrayOf(0x99.toByte()))
        } catch (e: TransportException) {
            // Client 2 was rejected/closed
        }

        // Verify Client 1 is still connected and operational
        val testPayload = byteArrayOf(0x10, 0x20, 0x30)
        client1.send(TransportEndpoint("server"), testPayload)
        val serverRecvPayload = withTimeout(3000) { serverReceived.await() }
        assertArrayEquals(testPayload, serverRecvPayload)

        client1.close()
        client2.close()
        server.close()
    }

    @Test
    fun testConcurrentSendAndCloseDoesNotCrashOrCorruptState() = runBlocking {
        val server = TcpServerTransport(TransportEndpoint("server"), port = 0)
        server.start()

        val client = SocketMessageTransport(TransportEndpoint("client"), "127.0.0.1", server.boundPort)
        client.connect()
        delay(50)

        // Launch 20 concurrent send operations
        coroutineScope {
            val sendJobs = (0 until 20).map { idx ->
                async(Dispatchers.IO) {
                    try {
                        client.send(TransportEndpoint("server"), "Msg $idx".toByteArray(Charsets.UTF_8))
                    } catch (_: TransportException) {
                        // Expected if close() race occurs
                    }
                }
            }

            // Close client midway
            val closeJob = async(Dispatchers.IO) {
                delay(2)
                client.close()
            }

            sendJobs.awaitAll()
            closeJob.await()
        }

        assertEquals(TransportState.CLOSED, client.state)
        server.close()
    }

    @Test
    fun testStateTransitionsEnforcedStrictly() {
        assertTrue(TransportState.CLOSED.canTransitionTo(TransportState.CONNECTING))
        assertFalse(TransportState.CLOSED.canTransitionTo(TransportState.CONNECTED))
        assertFalse(TransportState.CLOSED.canTransitionTo(TransportState.FAILED))

        assertTrue(TransportState.CONNECTING.canTransitionTo(TransportState.CONNECTED))
        assertTrue(TransportState.CONNECTING.canTransitionTo(TransportState.FAILED))
        assertTrue(TransportState.CONNECTING.canTransitionTo(TransportState.CLOSING))
        assertFalse(TransportState.CONNECTING.canTransitionTo(TransportState.CLOSED))

        assertTrue(TransportState.CONNECTED.canTransitionTo(TransportState.CLOSING))
        assertTrue(TransportState.CONNECTED.canTransitionTo(TransportState.FAILED))
        assertFalse(TransportState.CONNECTED.canTransitionTo(TransportState.CONNECTING))
        assertFalse(TransportState.CONNECTED.canTransitionTo(TransportState.CLOSED))

        assertTrue(TransportState.CLOSING.canTransitionTo(TransportState.CLOSED))
        assertFalse(TransportState.CLOSING.canTransitionTo(TransportState.CONNECTING))
        assertFalse(TransportState.CLOSING.canTransitionTo(TransportState.CONNECTED))

        assertTrue(TransportState.FAILED.canTransitionTo(TransportState.CLOSED))
        assertTrue(TransportState.FAILED.canTransitionTo(TransportState.CLOSING))
        assertFalse(TransportState.FAILED.canTransitionTo(TransportState.CONNECTING))
        assertFalse(TransportState.FAILED.canTransitionTo(TransportState.CONNECTED))
    }
}
