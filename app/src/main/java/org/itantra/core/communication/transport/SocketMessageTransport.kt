package org.itantra.core.communication.transport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Concrete [MessageTransport] implementation backed by an active or client-initiated TCP socket.
 *
 * Implements the 4-byte length-prefixed framing defined in
 * docs/communication/TRANSPORT_ARCHITECTURE_DECISION.md.
 *
 * @property localEndpoint The logical identifier of this local node.
 * @property host The remote host name/IP (if initiating client connection).
 * @property port The remote port number (if initiating client connection).
 * @property remoteEndpoint The logical identifier of the remote peer node, if known.
 */
class SocketMessageTransport(
    val localEndpoint: TransportEndpoint,
    val host: String? = null,
    val port: Int? = null,
    private var socket: Socket? = null,
    var remoteEndpoint: TransportEndpoint? = null
) : MessageTransport {

    @Volatile
    private var _state: TransportState = if (socket != null && socket!!.isConnected) TransportState.CONNECTED else TransportState.CLOSED

    /**
     * Current lifecycle state of this transport connection.
     */
    val state: TransportState
        get() = _state

    private var receiver: (suspend (TransportFrame) -> Unit)? = null
    private val sendMutex = Mutex()
    private val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var receiveJob: Job? = null

    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    init {
        socket?.let { s ->
            if (s.isConnected) {
                try {
                    inputStream = s.getInputStream()
                    outputStream = s.getOutputStream()
                    startReceiveLoop()
                } catch (e: Exception) {
                    _state = TransportState.FAILED
                    closeInternal()
                }
            }
        }
    }

    /**
     * Connects this transport to the configured [host] and [port].
     *
     * @param timeoutMs Connection timeout in milliseconds.
     * @throws TransportException on connection failure, timeout, or illegal state.
     */
    suspend fun connect(timeoutMs: Int = 5000) = withContext(Dispatchers.IO) {
        synchronized(this@SocketMessageTransport) {
            if (!_state.canTransitionTo(TransportState.CONNECTING)) {
                throw TransportException("Cannot connect from state: $_state")
            }
            _state = TransportState.CONNECTING
        }

        if (host.isNullOrBlank() || port == null || port <= 0) {
            synchronized(this@SocketMessageTransport) {
                _state = TransportState.FAILED
            }
            throw TransportException("Invalid connection target: host='$host', port=$port")
        }

        android.util.Log.i("PHYSICAL_E2E", "[SOCKET_CONNECT_START] Target host=$host, port=$port, timeout=${timeoutMs}ms")
        val sock = Socket()
        synchronized(this@SocketMessageTransport) {
            if (_state != TransportState.CONNECTING) {
                try { sock.close() } catch (_: Exception) {}
                android.util.Log.w("PHYSICAL_E2E", "[SOCKET_ABORTED] State is $_state, aborting connect")
                throw TransportException("Connection aborted: transport state is $_state")
            }
            socket = sock
        }

        try {
            sock.tcpNoDelay = true
            android.util.Log.i("PHYSICAL_E2E", "[SOCKET_CONNECTING] Connecting to InetSocketAddress($host, $port)...")
            sock.connect(InetSocketAddress(host, port), timeoutMs)
            android.util.Log.i("PHYSICAL_E2E", "[SOCKET_CONNECTED_RAW] socket.isConnected=${sock.isConnected}, localPort=${sock.localPort}, remoteAddress=${sock.remoteSocketAddress}")
            
            val inStream = sock.getInputStream()
            val outStream = sock.getOutputStream()
            android.util.Log.i("PHYSICAL_E2E", "[SOCKET_STREAMS] InputStream and OutputStream created successfully")

            synchronized(this@SocketMessageTransport) {
                if (_state != TransportState.CONNECTING) {
                    try { sock.close() } catch (_: Exception) {}
                    socket = null
                    android.util.Log.w("PHYSICAL_E2E", "[SOCKET_ABORTED] State changed to $_state during connect")
                    throw TransportException("Connection aborted: transport state changed to $_state during connect")
                }
                inputStream = inStream
                outputStream = outStream
                _state = TransportState.CONNECTED
                android.util.Log.i("PHYSICAL_E2E", "[TRANSPORT_STATE] TransportState transition: CONNECTING -> CONNECTED")
                startReceiveLoop()
            }
        } catch (e: Exception) {
            synchronized(this@SocketMessageTransport) {
                if (_state == TransportState.CONNECTING) {
                    _state = TransportState.FAILED
                }
            }
            val sockClosed = try { sock.isClosed } catch (_: Exception) { true }
            android.util.Log.e("PHYSICAL_E2E", "[TRANSPORT_FAILED] TransportState transition to FAILED: ${e.message}, socket.isClosed=$sockClosed", e)
            closeInternal()
            if (e is TransportException) throw e
            throw TransportException("Failed to connect to TCP host $host:$port: ${e.message}", e)
        }
    }

    override suspend fun send(destination: TransportEndpoint, payload: ByteArray) {
        if (_state != TransportState.CONNECTED) {
            val isClosed = socket?.isClosed ?: true
            val isConn = socket?.isConnected ?: false
            android.util.Log.e("PHYSICAL_E2E", "[SEND_FAILED_STATE] Cannot send payload: state=$_state, socket.isClosed=$isClosed, socket.isConnected=$isConn")
            throw TransportException("Cannot send payload: transport is not CONNECTED (current state: $_state)")
        }
        if (payload.isEmpty()) {
            throw TransportException("Cannot send empty payload")
        }
        if (payload.size > TcpTransportConstants.MAX_FRAME_PAYLOAD_BYTES) {
            throw TransportException(
                "Payload size (${payload.size} bytes) exceeds maximum frame limit (${TcpTransportConstants.MAX_FRAME_PAYLOAD_BYTES} bytes)"
            )
        }

        val safeCopy = payload.copyOf()

        withContext(Dispatchers.IO) {
            sendMutex.withLock {
                if (_state != TransportState.CONNECTED) {
                    throw TransportException("Cannot send payload: transport is not CONNECTED (current state: $_state)")
                }
                val out = outputStream
                    ?: throw TransportException("Cannot send payload: output stream is null")
                try {
                    TcpFraming.writeFrame(out, safeCopy)
                } catch (e: Exception) {
                    synchronized(this@SocketMessageTransport) {
                        if (_state == TransportState.CONNECTED) {
                            _state = TransportState.FAILED
                        }
                    }
                    android.util.Log.e("PHYSICAL_E2E", "[SEND_WRITE_FAILED] Socket write failed: ${e.message}", e)
                    closeInternal()
                    throw TransportException("Socket write failure: ${e.message}", e)
                }
            }
        }
    }

    override fun setReceiver(receiver: (suspend (TransportFrame) -> Unit)?) {
        synchronized(this@SocketMessageTransport) {
            if (receiver != null && this.receiver != null && this.receiver !== receiver) {
                throw IllegalStateException("Receiver already registered on SocketMessageTransport. Unregister by passing null first.")
            }
            this.receiver = receiver
        }
    }

    override suspend fun close() {
        val sockToClose: Socket?
        val inToClose: InputStream?
        val outToClose: OutputStream?

        synchronized(this@SocketMessageTransport) {
            if (_state == TransportState.CLOSED || _state == TransportState.CLOSING) return
            _state = TransportState.CLOSING
            sockToClose = socket
            inToClose = inputStream
            outToClose = outputStream
            socket = null
            inputStream = null
            outputStream = null
        }

        withContext(Dispatchers.IO) {
            try { inToClose?.close() } catch (_: Exception) {}
            try { outToClose?.close() } catch (_: Exception) {}
            try { sockToClose?.close() } catch (_: Exception) {}
            transportScope.cancel()
        }

        synchronized(this@SocketMessageTransport) {
            _state = TransportState.CLOSED
        }
    }

    private fun startReceiveLoop() {
        receiveJob?.cancel()
        receiveJob = transportScope.launch {
            val input = inputStream ?: return@launch

            try {
                while (isActive && _state == TransportState.CONNECTED) {
                    val payload = TcpFraming.readFrame(input)
                    val frame = TransportFrame(
                        source = remoteEndpoint,
                        destination = localEndpoint,
                        payload = payload
                    )
                    deliverToReceiver(frame)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    synchronized(this@SocketMessageTransport) {
                        if (_state == TransportState.CONNECTED) {
                            _state = TransportState.FAILED
                        }
                    }
                    closeInternal()
                }
            }
        }
    }

    private suspend fun deliverToReceiver(frame: TransportFrame) {
        val currentReceiver = synchronized(this) { receiver } ?: return
        try {
            currentReceiver.invoke(frame)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            // Application callback exceptions are isolated from transport lifecycle
            // to prevent application handler errors from crashing the physical socket receive loop.
        }
    }

    private fun closeInternal() {
        try {
            inputStream?.close()
        } catch (_: Exception) {}
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        inputStream = null
        outputStream = null
        transportScope.cancel()
    }
}
