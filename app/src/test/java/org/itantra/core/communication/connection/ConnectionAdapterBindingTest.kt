package org.itantra.core.communication.connection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.itantra.core.communication.adapter.SpeechMessageTransportAdapter
import org.itantra.core.communication.protocol.PacketEncoder
import org.itantra.core.communication.protocol.TransceiverPacketMapper
import org.itantra.core.communication.transport.LoopbackTransport
import org.itantra.core.communication.transport.MessageTransport
import org.itantra.core.communication.transport.SocketMessageTransport
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.communication.transport.TransportException
import org.itantra.core.communication.transport.TransportFrame
import org.itantra.core.domain.SpeechMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic unit tests auditing the binding between [ConnectionManager], [MessageTransport],
 * and [SpeechMessageTransportAdapter] across connect, disconnect, and reconnect lifecycles.
 */
class ConnectionAdapterBindingTest {

    private val localEndpoint = TransportEndpoint("node-local")
    private val remoteEndpoint = TransportEndpoint("node-remote")

    private class FakeTrackableTransport(
        val endpoint: TransportEndpoint = TransportEndpoint("test-node")
    ) : MessageTransport {
        private var internalReceiver: (suspend (TransportFrame) -> Unit)? = null
        val receiver: (suspend (TransportFrame) -> Unit)?
            get() = internalReceiver

        val sentFrames = Collections.synchronizedList(mutableListOf<Pair<TransportEndpoint, ByteArray>>())
        val closeCount = AtomicInteger(0)
        var isClosed = false

        override suspend fun send(destination: TransportEndpoint, payload: ByteArray) {
            if (isClosed) throw TransportException("Transport is closed")
            sentFrames.add(Pair(destination, payload))
        }

        override fun setReceiver(receiver: (suspend (TransportFrame) -> Unit)?) {
            this.internalReceiver = receiver
        }

        override suspend fun close() {
            closeCount.incrementAndGet()
            isClosed = true
            internalReceiver = null
        }

        suspend fun deliverFrame(payload: ByteArray, source: TransportEndpoint = TransportEndpoint("remote-node")) {
            internalReceiver?.invoke(TransportFrame(source = source, destination = endpoint, payload = payload))
        }
    }

    // 1. Client connection produces active transport & binds to adapter
    @Test
    fun `client connection produces active transport and binds to SpeechMessageTransportAdapter`() = runBlocking {
        val transport = FakeTrackableTransport(localEndpoint)
        val manager = FakeConnectionManager(simulatedTransport = transport)
        val adapter = SpeechMessageTransportAdapter(defaultDestination = remoteEndpoint)

        assertNull(adapter.activeTransport)
        manager.bindTo(adapter)

        val connectResult = manager.connect()
        assertTrue(connectResult.isSuccess)
        assertEquals(transport, adapter.activeTransport)
        assertNotNull(transport.receiver)
    }

    // 2. Outbound SpeechMessage uses current active transport
    @Test
    fun `outbound SpeechMessage uses currently bound transport`() = runBlocking {
        val transport = FakeTrackableTransport(localEndpoint)
        val manager = FakeConnectionManager(simulatedTransport = transport)
        val adapter = SpeechMessageTransportAdapter(defaultDestination = remoteEndpoint)
        manager.bindTo(adapter)
        manager.connect()

        val msg = SpeechMessage(text = "Alpha team ready", language = "en", timestamp = 1000L, confidence = 0.95f)
        adapter.sendSpeechMessage(msg)

        assertEquals(1, transport.sentFrames.size)
        assertEquals(remoteEndpoint, transport.sentFrames.first().first)
        assertEquals(1L, adapter.currentSequenceNumber)
    }

    // 3. Outbound SpeechMessage fails cleanly when adapter is unbound / disconnected
    @Test
    fun `outbound SpeechMessage throws TransportException when adapter is unbound`() = runBlocking {
        val adapter = SpeechMessageTransportAdapter(defaultDestination = remoteEndpoint)
        assertNull(adapter.activeTransport)

        val msg = SpeechMessage(text = "Unsent", language = "en", timestamp = 1000L)
        try {
            adapter.sendSpeechMessage(msg)
            fail("Should throw TransportException when no transport is bound")
        } catch (e: TransportException) {
            assertTrue(e.message!!.contains("no active MessageTransport bound"))
        }
    }

    // 4. Inbound frame reaches current adapter and delivers to M3 receiver
    @Test
    fun `inbound frame on active transport reaches M3 speech message receiver`() = runBlocking {
        val transport = FakeTrackableTransport(localEndpoint)
        val manager = FakeConnectionManager(simulatedTransport = transport)
        val adapter = SpeechMessageTransportAdapter(defaultDestination = remoteEndpoint)
        manager.bindTo(adapter)
        manager.connect()

        val receivedList = mutableListOf<SpeechMessage>()
        adapter.setSpeechMessageReceiver { receivedList.add(it) }

        val inboundMsg = SpeechMessage(text = "Bravo acknowledges", language = "en", timestamp = 2000L)
        val payload = PacketEncoder.encode(TransceiverPacketMapper.fromSpeechMessage(inboundMsg, 42L))

        transport.deliverFrame(payload)

        assertEquals(1, receivedList.size)
        assertEquals("Bravo acknowledges", receivedList.first().text)
    }

    // 5. Disconnect unbinds transport from adapter
    @Test
    fun `disconnect clears active transport from adapter`() = runBlocking {
        val transport = FakeTrackableTransport(localEndpoint)
        val manager = FakeConnectionManager(simulatedTransport = transport)
        val adapter = SpeechMessageTransportAdapter(defaultDestination = remoteEndpoint)
        manager.bindTo(adapter)
        manager.connect()
        assertEquals(transport, adapter.activeTransport)

        manager.disconnect()
        assertNull(adapter.activeTransport)
        assertEquals(ConnectionState.DISCONNECTED, manager.state)
    }

    // 6. Reconnect replaces stale transport correctly
    @Test
    fun `reconnect replaces stale transport and updates active binding`() = runBlocking {
        val transport1 = FakeTrackableTransport(localEndpoint)
        val transport2 = FakeTrackableTransport(localEndpoint)
        val manager = FakeConnectionManager(simulatedTransport = transport1)
        val adapter = SpeechMessageTransportAdapter(defaultDestination = remoteEndpoint)
        manager.bindTo(adapter)

        // First connection
        manager.connect()
        assertEquals(transport1, adapter.activeTransport)
        assertNotNull(transport1.receiver)

        // Disconnect
        manager.disconnect()
        assertNull(adapter.activeTransport)
        assertNull(transport1.receiver)

        // Second connection with new transport
        manager.simulatedTransport = transport2
        manager.connect()
        assertEquals(transport2, adapter.activeTransport)
        assertNotNull(transport2.receiver)

        // Verify sending uses transport2, not transport1
        val msg = SpeechMessage(text = "Message on transport 2", language = "en", timestamp = 3000L)
        adapter.sendSpeechMessage(msg)

        assertEquals(0, transport1.sentFrames.size)
        assertEquals(1, transport2.sentFrames.size)
    }

    // 7. Old transport cannot deliver messages after reconnect
    @Test
    fun `old disconnected transport cannot deliver frames to adapter`() = runBlocking {
        val transport1 = FakeTrackableTransport(localEndpoint)
        val transport2 = FakeTrackableTransport(localEndpoint)
        val manager = FakeConnectionManager(simulatedTransport = transport1)
        val adapter = SpeechMessageTransportAdapter(defaultDestination = remoteEndpoint)
        val received = mutableListOf<SpeechMessage>()
        adapter.setSpeechMessageReceiver { received.add(it) }

        manager.bindTo(adapter)
        manager.connect()
        manager.disconnect()

        manager.simulatedTransport = transport2
        manager.connect()

        val oldMsg = SpeechMessage(text = "Ghost message from old socket", language = "en", timestamp = 100L)
        val payload = PacketEncoder.encode(TransceiverPacketMapper.fromSpeechMessage(oldMsg, 1L))

        // Deliver on old transport
        transport1.deliverFrame(payload)
        assertEquals("Old transport must not deliver frames after being unbound", 0, received.size)

        // Deliver on new transport
        val newMsg = SpeechMessage(text = "Valid message on new socket", language = "en", timestamp = 200L)
        val newPayload = PacketEncoder.encode(TransceiverPacketMapper.fromSpeechMessage(newMsg, 2L))
        transport2.deliverFrame(newPayload)
        assertEquals(1, received.size)
        assertEquals("Valid message on new socket", received.first().text)
    }

    // 8. Monotonic sequence counter is preserved across reconnects
    @Test
    fun `sequence counter continues monotonically across disconnect and reconnect`() = runBlocking {
        val transport1 = FakeTrackableTransport(localEndpoint)
        val transport2 = FakeTrackableTransport(localEndpoint)
        val manager = FakeConnectionManager(simulatedTransport = transport1)
        val adapter = SpeechMessageTransportAdapter(defaultDestination = remoteEndpoint, initialSequenceNumber = 10L)
        manager.bindTo(adapter)

        manager.connect()
        adapter.sendSpeechMessage(SpeechMessage("Msg 10", "en", 1000L))
        adapter.sendSpeechMessage(SpeechMessage("Msg 11", "en", 1001L))
        assertEquals(12L, adapter.currentSequenceNumber)

        manager.disconnect()
        manager.simulatedTransport = transport2
        manager.connect()

        adapter.sendSpeechMessage(SpeechMessage("Msg 12", "en", 1002L))
        assertEquals(13L, adapter.currentSequenceNumber)
    }

    // 9. Server mode binding and client connection lifecycle over local TCP sockets
    @Test
    fun `server mode binding establishes bidirectional communication with client`() = runBlocking {
        val serverEndpoint = TransportEndpoint("server-node")
        val clientEndpoint = TransportEndpoint("client-node")

        // Server setup
        val serverManager = TcpConnectionManager(serverEndpoint, ConnectionConfig.Server(port = 0))
        val serverAdapter = SpeechMessageTransportAdapter(defaultDestination = clientEndpoint)
        serverManager.bindTo(serverAdapter)
        val serverResult = serverManager.connect()
        assertTrue(serverResult.isSuccess)
        val serverTransport = serverResult.getOrThrow() as org.itantra.core.communication.transport.TcpServerTransport
        val boundPort = serverTransport.boundPort

        val serverReceived = CompletableDeferred<SpeechMessage>()
        serverAdapter.setSpeechMessageReceiver { serverReceived.complete(it) }

        // Client setup
        val clientManager = TcpConnectionManager(clientEndpoint, ConnectionConfig.Client("127.0.0.1", boundPort, 3000))
        val clientAdapter = SpeechMessageTransportAdapter(defaultDestination = serverEndpoint)
        clientManager.bindTo(clientAdapter)
        val clientResult = clientManager.connect()
        assertTrue(clientResult.isSuccess)

        val clientReceived = CompletableDeferred<SpeechMessage>()
        clientAdapter.setSpeechMessageReceiver { clientReceived.complete(it) }

        delay(100)

        // Transmit Client -> Server
        val clientMsg = SpeechMessage("Client hello", "en", 5000L, 0.9f)
        clientAdapter.sendSpeechMessage(clientMsg)

        val atServer = withTimeout(3000) { serverReceived.await() }
        assertEquals(clientMsg.text, atServer.text)

        // Transmit Server -> Client
        val serverMsg = SpeechMessage("Server ack", "en", 5050L, 0.95f)
        serverAdapter.sendSpeechMessage(serverMsg)

        val atClient = withTimeout(3000) { clientReceived.await() }
        assertEquals(serverMsg.text, atClient.text)

        // Teardown
        clientManager.disconnect()
        serverManager.disconnect()

        assertNull(clientAdapter.activeTransport)
        assertNull(serverAdapter.activeTransport)

        clientManager.release()
        serverManager.release()
    }

    // 10. No double-close when adapter and connection manager teardown occurs
    @Test
    fun `teardown does not trigger double-close errors on active transport`() = runBlocking {
        val transport = FakeTrackableTransport(localEndpoint)
        val manager = FakeConnectionManager(simulatedTransport = transport)
        val adapter = SpeechMessageTransportAdapter(defaultDestination = remoteEndpoint)
        manager.bindTo(adapter)
        manager.connect()

        manager.disconnect()
        adapter.close()

        assertEquals(1, manager.disconnectCount.get())
        assertNull(adapter.activeTransport)
    }

    // 11. Server adapter seamlessly routes frames across sequential client reconnects without rebinding
    @Test
    fun `server adapter maintains binding across client disconnect and subsequent new client connection`() = runBlocking {
        val serverEndpoint = TransportEndpoint("server-node")
        val clientEndpoint = TransportEndpoint("client-node")

        val serverManager = TcpConnectionManager(serverEndpoint, ConnectionConfig.Server(port = 0))
        val serverAdapter = SpeechMessageTransportAdapter(defaultDestination = clientEndpoint)
        serverManager.bindTo(serverAdapter)
        val serverResult = serverManager.connect()
        assertTrue(serverResult.isSuccess)
        val serverTransport = serverResult.getOrThrow() as org.itantra.core.communication.transport.TcpServerTransport
        val boundPort = serverTransport.boundPort

        val receivedMessages = java.util.Collections.synchronizedList(mutableListOf<SpeechMessage>())
        serverAdapter.setSpeechMessageReceiver { receivedMessages.add(it) }

        // --- Client 1 connects, sends, then disconnects ---
        val client1 = SocketMessageTransport(clientEndpoint, "127.0.0.1", boundPort)
        client1.connect()
        delay(100)

        val msg1 = SpeechMessage("First client message", "en", 1000L)
        val payload1 = PacketEncoder.encode(TransceiverPacketMapper.fromSpeechMessage(msg1, 1L))
        client1.send(serverEndpoint, payload1)
        delay(100)

        assertEquals(1, receivedMessages.size)
        assertEquals("First client message", receivedMessages[0].text)

        client1.close()
        delay(100)

        // --- Client 2 connects to the same listening server ---
        val client2 = SocketMessageTransport(clientEndpoint, "127.0.0.1", boundPort)
        val client2Received = CompletableDeferred<SpeechMessage>()
        val client2Adapter = SpeechMessageTransportAdapter(client2, defaultDestination = serverEndpoint)
        client2Adapter.setSpeechMessageReceiver { client2Received.complete(it) }
        client2.connect()
        delay(100)

        val msg2 = SpeechMessage("Second client message", "en", 2000L)
        val payload2 = PacketEncoder.encode(TransceiverPacketMapper.fromSpeechMessage(msg2, 2L))
        client2.send(serverEndpoint, payload2)
        delay(100)

        assertEquals(2, receivedMessages.size)
        assertEquals("Second client message", receivedMessages[1].text)

        // --- Server sends response back to Client 2 via existing serverAdapter ---
        val serverReply = SpeechMessage("Server reply to client 2", "en", 2050L)
        serverAdapter.sendSpeechMessage(serverReply)

        val client2RecvMsg = withTimeout(3000) { client2Received.await() }
        assertEquals("Server reply to client 2", client2RecvMsg.text)

        client2.close()
        serverManager.disconnect()
        serverManager.release()
    }
}
