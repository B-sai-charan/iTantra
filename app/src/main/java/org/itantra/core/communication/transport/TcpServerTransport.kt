package org.itantra.core.communication.transport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ServerSocket
import java.net.SocketException

/**
 * Concrete [MessageTransport] implementation acting as a TCP server / listener node.
 *
 * For the MVP, enforces a single-active-client policy and exposes full-duplex
 * bidirectional [MessageTransport] communication over that connection.
 *
 * @property localEndpoint The logical identifier of this server node.
 * @property port The local TCP port to bind (default: [TcpTransportConstants.DEFAULT_PORT], or 0 for dynamic allocation).
 */
class TcpServerTransport(
    val localEndpoint: TransportEndpoint,
    val port: Int = TcpTransportConstants.DEFAULT_PORT
) : MessageTransport {

    @Volatile
    private var _state: TransportState = TransportState.CLOSED

    /**
     * Current lifecycle state of this server transport.
     */
    val state: TransportState
        get() = _state

    private var serverSocket: ServerSocket? = null
    private var activeClient: SocketMessageTransport? = null
    private var receiver: (suspend (TransportFrame) -> Unit)? = null

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null

    /**
     * Actual bound local port (resolves ephemeral port if initialized with 0).
     */
    val boundPort: Int
        get() = serverSocket?.localPort ?: port

    /**
     * True if a remote client is currently connected and active.
     */
    val isClientConnected: Boolean
        get() = synchronized(this) { activeClient?.state == TransportState.CONNECTED }

    /**
     * Binds the server socket and starts accepting client connections in the background.
     *
     * @throws TransportException on bind failure or illegal lifecycle state.
     */
    suspend fun start(backlog: Int = 1) = withContext(Dispatchers.IO) {
        synchronized(this@TcpServerTransport) {
            if (!_state.canTransitionTo(TransportState.CONNECTING)) {
                throw TransportException("Cannot start server from state: $_state")
            }
            _state = TransportState.CONNECTING
        }

        try {
            val server = ServerSocket(port, backlog)
            synchronized(this@TcpServerTransport) {
                if (_state != TransportState.CONNECTING) {
                    try { server.close() } catch (_: Exception) {}
                    throw TransportException("Server start aborted: state changed to $_state")
                }
                serverSocket = server
                _state = TransportState.CONNECTED
                startAcceptLoop(server)
            }
        } catch (e: Exception) {
            synchronized(this@TcpServerTransport) {
                if (_state == TransportState.CONNECTING) {
                    _state = TransportState.FAILED
                }
            }
            closeInternal()
            if (e is TransportException) throw e
            throw TransportException("Failed to bind TCP server on port $port: ${e.message}", e)
        }
    }

    override suspend fun send(destination: TransportEndpoint, payload: ByteArray) {
        val client = synchronized(this) { activeClient }
        if (client == null || client.state != TransportState.CONNECTED) {
            throw TransportException("Cannot send payload: No active client is connected to server")
        }
        client.send(destination, payload)
    }

    override fun setReceiver(receiver: (suspend (TransportFrame) -> Unit)?) {
        synchronized(this@TcpServerTransport) {
            if (receiver != null && this.receiver != null && this.receiver !== receiver) {
                throw IllegalStateException("Receiver already registered on TcpServerTransport. Unregister by passing null first.")
            }
            this.receiver = receiver
            activeClient?.setReceiver(receiver)
        }
    }

    override suspend fun close() {
        val clientToClose: SocketMessageTransport?
        val serverToClose: ServerSocket?

        synchronized(this@TcpServerTransport) {
            if (_state == TransportState.CLOSED || _state == TransportState.CLOSING) return
            _state = TransportState.CLOSING
            clientToClose = activeClient
            activeClient = null
            serverToClose = serverSocket
            serverSocket = null
        }

        withContext(Dispatchers.IO) {
            try {
                clientToClose?.close()
            } catch (_: Exception) {}
            try {
                serverToClose?.close()
            } catch (_: Exception) {}
            serverScope.cancel()
        }

        synchronized(this@TcpServerTransport) {
            _state = TransportState.CLOSED
        }
    }

    private fun startAcceptLoop(server: ServerSocket) {
        acceptJob?.cancel()
        acceptJob = serverScope.launch {
            try {
                while (isActive && !server.isClosed) {
                    val incomingSocket = server.accept()
                    incomingSocket.tcpNoDelay = true

                    synchronized(this@TcpServerTransport) {
                        val current = activeClient
                        if (current != null && current.state == TransportState.CONNECTED) {
                            // Single-client policy: Reject and immediately close redundant concurrent connection
                            try {
                                incomingSocket.close()
                            } catch (_: Exception) {}
                            return@synchronized
                        }

                        // No active connected client: wrap the new connection
                        val clientTransport = SocketMessageTransport(
                            localEndpoint = localEndpoint,
                            socket = incomingSocket,
                            remoteEndpoint = null
                        )
                        activeClient = clientTransport
                        clientTransport.setReceiver(receiver)
                    }
                }
            } catch (e: Exception) {
                if (!server.isClosed && e !is CancellationException) {
                    synchronized(this@TcpServerTransport) {
                        if (_state == TransportState.CONNECTED) {
                            _state = TransportState.FAILED
                        }
                    }
                    closeInternal()
                }
            }
        }
    }

    private fun closeInternal() {
        try {
            activeClient?.let {
                serverScope.launch { it.close() }
            }
        } catch (_: Exception) {}
        activeClient = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverScope.cancel()
    }
}
