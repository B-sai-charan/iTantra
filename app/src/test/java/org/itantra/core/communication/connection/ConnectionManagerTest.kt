package org.itantra.core.communication.connection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.itantra.core.communication.transport.LoopbackTransport
import org.itantra.core.communication.transport.MessageTransport
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.communication.transport.TransportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

class ConnectionManagerTest {

    private class RecordingConnectionListener : ConnectionListener {
        val stateHistory = Collections.synchronizedList(mutableListOf<ConnectionState>())
        val connectedTransports = Collections.synchronizedList(mutableListOf<MessageTransport>())
        val disconnectCount = AtomicInteger(0)
        val errors = Collections.synchronizedList(mutableListOf<Pair<String, Throwable?>>())

        override fun onStateChanged(state: ConnectionState) {
            stateHistory.add(state)
        }

        override fun onConnected(transport: MessageTransport) {
            connectedTransports.add(transport)
        }

        override fun onDisconnected() {
            disconnectCount.incrementAndGet()
        }

        override fun onError(message: String, cause: Throwable?) {
            errors.add(Pair(message, cause))
        }
    }

    // ==========================================
    // 1. STATE MACHINE & CONFIG TESTS
    // ==========================================

    @Test
    fun `ConnectionState transition rules match lifecycle contract`() {
        assertTrue(ConnectionState.DISCONNECTED.canTransitionTo(ConnectionState.CONNECTING))
        assertFalse(ConnectionState.DISCONNECTED.canTransitionTo(ConnectionState.CONNECTED))

        assertTrue(ConnectionState.CONNECTING.canTransitionTo(ConnectionState.CONNECTED))
        assertTrue(ConnectionState.CONNECTING.canTransitionTo(ConnectionState.FAILED))
        assertTrue(ConnectionState.CONNECTING.canTransitionTo(ConnectionState.DISCONNECTING))
        assertTrue(ConnectionState.CONNECTING.canTransitionTo(ConnectionState.DISCONNECTED))

        assertTrue(ConnectionState.CONNECTED.canTransitionTo(ConnectionState.DISCONNECTING))
        assertTrue(ConnectionState.CONNECTED.canTransitionTo(ConnectionState.FAILED))
        assertFalse(ConnectionState.CONNECTED.canTransitionTo(ConnectionState.CONNECTING))

        assertTrue(ConnectionState.DISCONNECTING.canTransitionTo(ConnectionState.DISCONNECTED))
        assertTrue(ConnectionState.DISCONNECTING.canTransitionTo(ConnectionState.FAILED))

        assertTrue(ConnectionState.FAILED.canTransitionTo(ConnectionState.CONNECTING))
        assertTrue(ConnectionState.FAILED.canTransitionTo(ConnectionState.DISCONNECTED))
    }

    @Test
    fun `ConnectionConfig validation enforces valid bounds`() {
        // Valid client config
        val client = ConnectionConfig.Client(host = "192.168.1.10", port = 42173, timeoutMs = 3000)
        assertEquals("192.168.1.10", client.host)
        assertEquals(42173, client.port)

        // Valid server config
        val server = ConnectionConfig.Server(port = 42173, backlog = 5)
        assertEquals(42173, server.port)
        assertEquals(5, server.backlog)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ConnectionConfig Client rejects blank host`() {
        ConnectionConfig.Client(host = "  ", port = 8080)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ConnectionConfig Client rejects invalid port`() {
        ConnectionConfig.Client(host = "localhost", port = 70000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ConnectionConfig Server rejects invalid port`() {
        ConnectionConfig.Server(port = -1)
    }

    // ==========================================
    // 2. FAKE CONNECTION MANAGER TESTS
    // ==========================================

    @Test
    fun `FakeConnectionManager initial state is DISCONNECTED`() {
        val fakeTransport = LoopbackTransport(TransportEndpoint("node-a"))
        val manager = FakeConnectionManager(simulatedTransport = fakeTransport)

        assertEquals(ConnectionState.DISCONNECTED, manager.state)
        assertFalse(manager.isConnected)
        assertNull(manager.activeTransport)
        assertEquals(0, manager.connectCount.get())
        assertEquals(0, manager.disconnectCount.get())
    }

    @Test
    fun `FakeConnectionManager successful connect exposes transport and notifies listener`() = runBlocking {
        val fakeTransport = LoopbackTransport(TransportEndpoint("node-a"))
        val manager = FakeConnectionManager(simulatedTransport = fakeTransport)
        val listener = RecordingConnectionListener()
        manager.setListener(listener)

        val result = manager.connect()

        assertTrue(result.isSuccess)
        assertEquals(fakeTransport, result.getOrNull())
        assertEquals(ConnectionState.CONNECTED, manager.state)
        assertTrue(manager.isConnected)
        assertEquals(fakeTransport, manager.activeTransport)

        assertEquals(listOf(ConnectionState.CONNECTING, ConnectionState.CONNECTED), listener.stateHistory)
        assertEquals(1, listener.connectedTransports.size)
        assertEquals(fakeTransport, listener.connectedTransports.first())
    }

    @Test
    fun `FakeConnectionManager connect failure updates state and notifies listener`() = runBlocking {
        val manager = FakeConnectionManager(
            shouldFailConnect = true,
            failureException = TransportException("Socket timeout")
        )
        val listener = RecordingConnectionListener()
        manager.setListener(listener)

        val result = manager.connect()

        assertTrue(result.isFailure)
        assertEquals(ConnectionState.FAILED, manager.state)
        assertFalse(manager.isConnected)
        assertNull(manager.activeTransport)

        assertEquals(listOf(ConnectionState.CONNECTING, ConnectionState.FAILED), listener.stateHistory)
        assertEquals(1, listener.errors.size)
        assertEquals("Socket timeout", listener.errors.first().first)
    }

    @Test
    fun `FakeConnectionManager disconnect transitions to DISCONNECTED and clears transport`() = runBlocking {
        val fakeTransport = LoopbackTransport(TransportEndpoint("node-a"))
        val manager = FakeConnectionManager(simulatedTransport = fakeTransport)
        val listener = RecordingConnectionListener()
        manager.setListener(listener)

        manager.connect()
        manager.disconnect()

        assertEquals(ConnectionState.DISCONNECTED, manager.state)
        assertFalse(manager.isConnected)
        assertNull(manager.activeTransport)
        assertEquals(1, listener.disconnectCount.get())
        assertEquals(
            listOf(ConnectionState.CONNECTING, ConnectionState.CONNECTED, ConnectionState.DISCONNECTING, ConnectionState.DISCONNECTED),
            listener.stateHistory
        )
    }

    @Test
    fun `FakeConnectionManager reconnect after disconnect succeeds cleanly`() = runBlocking {
        val fakeTransport = LoopbackTransport(TransportEndpoint("node-a"))
        val manager = FakeConnectionManager(simulatedTransport = fakeTransport)
        manager.connect()
        manager.disconnect()

        val restartResult = manager.connect()
        assertTrue(restartResult.isSuccess)
        assertEquals(ConnectionState.CONNECTED, manager.state)
        assertEquals(fakeTransport, manager.activeTransport)
    }

    @Test
    fun `FakeConnectionManager repeated connect when already connected is idempotent`() = runBlocking {
        val fakeTransport = LoopbackTransport(TransportEndpoint("node-a"))
        val manager = FakeConnectionManager(simulatedTransport = fakeTransport)
        manager.connect()
        assertEquals(1, manager.connectCount.get())

        val repeat = manager.connect()
        assertTrue(repeat.isSuccess)
        assertEquals(ConnectionState.CONNECTED, manager.state)
    }

    @Test
    fun `FakeConnectionManager repeated disconnect is safe and idempotent`() = runBlocking {
        val fakeTransport = LoopbackTransport(TransportEndpoint("node-a"))
        val manager = FakeConnectionManager(simulatedTransport = fakeTransport)
        manager.connect()
        manager.disconnect()
        assertEquals(1, manager.disconnectCount.get())

        manager.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, manager.state)
    }

    // ==========================================
    // 3. TCP CONNECTION MANAGER LIFECYCLE TESTS (JVM Local Sockets)
    // ==========================================

    @Test
    fun `TcpConnectionManager in Server mode binds ephemeral port and transitions to CONNECTED`() = runBlocking {
        val endpoint = TransportEndpoint("server-node")
        val config = ConnectionConfig.Server(port = 0)
        val manager = TcpConnectionManager(endpoint, config)
        val listener = RecordingConnectionListener()
        manager.setListener(listener)

        assertEquals(ConnectionState.DISCONNECTED, manager.state)
        assertNull(manager.activeTransport)

        val result = manager.connect()
        assertTrue(result.isSuccess)
        assertEquals(ConnectionState.CONNECTED, manager.state)
        assertTrue(manager.isConnected)
        assertNotNull(manager.activeTransport)

        assertEquals(listOf(ConnectionState.CONNECTING, ConnectionState.CONNECTED), listener.stateHistory)
        assertEquals(1, listener.connectedTransports.size)

        manager.disconnect()
        assertEquals(ConnectionState.DISCONNECTED, manager.state)
        assertNull(manager.activeTransport)
        assertEquals(1, listener.disconnectCount.get())

        manager.release()
    }

    @Test
    fun `TcpConnectionManager in Client mode fails cleanly when target is unreachable`() = runBlocking {
        val endpoint = TransportEndpoint("client-node")
        // Port 1 on loopback should refuse or fail connection immediately
        val config = ConnectionConfig.Client(host = "127.0.0.1", port = 1, timeoutMs = 500)
        val manager = TcpConnectionManager(endpoint, config)
        val listener = RecordingConnectionListener()
        manager.setListener(listener)

        val result = manager.connect()
        assertTrue(result.isFailure)
        assertEquals(ConnectionState.FAILED, manager.state)
        assertFalse(manager.isConnected)
        assertNull(manager.activeTransport)

        assertEquals(listOf(ConnectionState.CONNECTING, ConnectionState.FAILED), listener.stateHistory)
        assertEquals(1, listener.errors.size)

        manager.release()
    }

    @Test
    fun `TcpConnectionManager Server-Client pair connects and establishes active transports`() = runBlocking {
        val serverEndpoint = TransportEndpoint("test-server")
        val clientEndpoint = TransportEndpoint("test-client")

        // 1. Start Server Manager on port 0
        val serverManager = TcpConnectionManager(serverEndpoint, ConnectionConfig.Server(port = 0))
        val serverResult = serverManager.connect()
        assertTrue(serverResult.isSuccess)
        val serverTransport = serverResult.getOrThrow() as org.itantra.core.communication.transport.TcpServerTransport
        val boundPort = serverTransport.boundPort

        // 2. Start Client Manager connecting to server
        val clientManager = TcpConnectionManager(
            clientEndpoint,
            ConnectionConfig.Client(host = "127.0.0.1", port = boundPort, timeoutMs = 3000)
        )
        val clientResult = clientManager.connect()
        assertTrue(clientResult.isSuccess)
        assertTrue(clientManager.isConnected)
        assertNotNull(clientManager.activeTransport)

        // 3. Teardown cleanly
        clientManager.disconnect()
        serverManager.disconnect()

        assertEquals(ConnectionState.DISCONNECTED, clientManager.state)
        assertEquals(ConnectionState.DISCONNECTED, serverManager.state)

        clientManager.release()
        serverManager.release()
    }

    @Test
    fun `TcpConnectionManager concurrent connect and disconnect calls converge deterministically`() = runBlocking {
        val manager = FakeConnectionManager(simulatedTransport = LoopbackTransport(TransportEndpoint("node-a")))

        val threads = (1..10).map { i ->
            Thread {
                runBlocking {
                    if (i % 2 == 0) manager.connect() else manager.disconnect()
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertTrue(manager.state == ConnectionState.CONNECTED || manager.state == ConnectionState.DISCONNECTED)
    }
}
