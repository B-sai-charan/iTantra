package org.itantra.core.communication.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.itantra.core.communication.transport.MessageTransport
import org.itantra.core.communication.transport.SocketMessageTransport
import org.itantra.core.communication.transport.TcpServerTransport
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.communication.transport.TransportException

/**
 * Concrete TCP implementation of [ConnectionManager].
 *
 * Supports both [ConnectionConfig.Client] and [ConnectionConfig.Server] modes using
 * [SocketMessageTransport] and [TcpServerTransport].
 *
 * @property localEndpoint The logical identifier of this node.
 * @property config Connection configuration (Client or Server parameters).
 * @property ownsTransport Whether this manager owns and closes the underlying transport upon disconnect.
 */
class TcpConnectionManager(
    val localEndpoint: TransportEndpoint,
    val config: ConnectionConfig,
    val ownsTransport: Boolean = true
) : ConnectionManager {

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var _state: ConnectionState = ConnectionState.DISCONNECTED

    override val state: ConnectionState
        get() = _state

    override val isConnected: Boolean
        get() = _state == ConnectionState.CONNECTED

    @Volatile
    private var _activeTransport: MessageTransport? = null

    override val activeTransport: MessageTransport?
        get() = _activeTransport

    @Volatile
    private var listener: ConnectionListener? = null

    override fun setListener(listener: ConnectionListener?) {
        this.listener = listener
    }

    override suspend fun connect(): Result<MessageTransport> = mutex.withLock {
        when (_state) {
            ConnectionState.CONNECTED -> {
                val current = _activeTransport
                return if (current != null) {
                    Result.success(current)
                } else {
                    val err = IllegalStateException("Manager is CONNECTED but active transport is null")
                    Result.failure(err)
                }
            }
            ConnectionState.CONNECTING, ConnectionState.DISCONNECTING -> {
                val err = IllegalStateException("Cannot connect while transition is in progress: $_state")
                listener?.onError(err.message ?: "", err)
                return Result.failure(err)
            }
            ConnectionState.DISCONNECTED, ConnectionState.FAILED -> {
                updateState(ConnectionState.CONNECTING)

                try {
                    val transport: MessageTransport = when (config) {
                        is ConnectionConfig.Client -> {
                            android.util.Log.i("PHYSICAL_E2E", "[CLIENT_CONNECT] Connecting TCP client to ${config.host}:${config.port} (timeout=${config.timeoutMs}ms)...")
                            val socketTransport = SocketMessageTransport(
                                localEndpoint = localEndpoint,
                                host = config.host,
                                port = config.port
                            )
                            socketTransport.connect(config.timeoutMs)
                            socketTransport
                        }
                        is ConnectionConfig.Server -> {
                            android.util.Log.i("PHYSICAL_E2E", "[SERVER_START] Starting TCP server on port ${config.port}...")
                            val serverTransport = TcpServerTransport(
                                localEndpoint = localEndpoint,
                                port = config.port
                            )
                            serverTransport.start(config.backlog)
                            serverTransport
                        }
                    }

                    _activeTransport = transport
                    android.util.Log.i("PHYSICAL_E2E", "[SOCKET_CONNECTED] TCP transport ready in ${config::class.simpleName} mode")
                    updateState(ConnectionState.CONNECTED)
                    listener?.onConnected(transport)
                    Result.success(transport)
                } catch (e: Exception) {
                    val transportToCleanup = _activeTransport
                    _activeTransport = null
                    if (ownsTransport && transportToCleanup != null) {
                        try { transportToCleanup.close() } catch (_: Exception) {}
                    }
                    updateState(ConnectionState.FAILED)
                    val msg = "Connection failed in ${config::class.simpleName} mode: ${e.message}"
                    android.util.Log.e("PHYSICAL_E2E", "[CONNECT_FAILED] $msg", e)
                    listener?.onError(msg, e)
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun disconnect() = mutex.withLock {
        if (_state == ConnectionState.DISCONNECTED) return@withLock

        updateState(ConnectionState.DISCONNECTING)

        val transportToClose = _activeTransport
        _activeTransport = null

        if (ownsTransport && transportToClose != null) {
            try {
                transportToClose.close()
            } catch (e: Exception) {
                listener?.onError("Error closing transport during disconnect: ${e.message}", e)
            }
        }

        android.util.Log.i("PHYSICAL_E2E", "[DISCONNECT] TCP transport disconnected")
        updateState(ConnectionState.DISCONNECTED)
        listener?.onDisconnected()
    }

    override suspend fun release() {
        disconnect()
        scope.cancel()
    }

    private fun updateState(newState: ConnectionState) {
        _state = newState
        listener?.onStateChanged(newState)
    }
}
