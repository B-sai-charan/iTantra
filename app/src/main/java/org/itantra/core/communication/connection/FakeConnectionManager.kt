package org.itantra.core.communication.connection

import org.itantra.core.communication.transport.MessageTransport
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic test double implementing [ConnectionManager] without physical network sockets.
 *
 * Allows JVM unit tests to programmatically simulate connections, state transitions,
 * injected transports, and connection failures.
 */
class FakeConnectionManager(
    var simulatedTransport: MessageTransport? = null,
    var shouldFailConnect: Boolean = false,
    var failureException: Throwable = IllegalStateException("Simulated connection failure")
) : ConnectionManager {

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

    val connectCount = AtomicInteger(0)
    val disconnectCount = AtomicInteger(0)
    val releaseCount = AtomicInteger(0)

    override fun setListener(listener: ConnectionListener?) {
        this.listener = listener
    }

    override suspend fun connect(): Result<MessageTransport> {
        connectCount.incrementAndGet()
        if (_state == ConnectionState.CONNECTED) {
            return Result.success(_activeTransport ?: simulatedTransport!!)
        }

        updateState(ConnectionState.CONNECTING)

        if (shouldFailConnect) {
            updateState(ConnectionState.FAILED)
            listener?.onError(failureException.message ?: "Connect failure", failureException)
            return Result.failure(failureException)
        }

        val transport = simulatedTransport
            ?: throw IllegalStateException("FakeConnectionManager requires simulatedTransport when connect succeeds")

        _activeTransport = transport
        updateState(ConnectionState.CONNECTED)
        listener?.onConnected(transport)
        return Result.success(transport)
    }

    override suspend fun disconnect() {
        disconnectCount.incrementAndGet()
        if (_state == ConnectionState.DISCONNECTED) return

        updateState(ConnectionState.DISCONNECTING)
        _activeTransport = null
        updateState(ConnectionState.DISCONNECTED)
        listener?.onDisconnected()
    }

    override suspend fun release() {
        releaseCount.incrementAndGet()
        disconnect()
    }

    fun simulateFailure(errorMessage: String, cause: Throwable? = null) {
        _activeTransport = null
        updateState(ConnectionState.FAILED)
        listener?.onError(errorMessage, cause)
    }

    private fun updateState(newState: ConnectionState) {
        _state = newState
        listener?.onStateChanged(newState)
    }
}
