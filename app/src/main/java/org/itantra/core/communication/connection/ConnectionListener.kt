package org.itantra.core.communication.connection

import org.itantra.core.communication.transport.MessageTransport

/**
 * Observer interface for network connection lifecycle events and errors.
 */
interface ConnectionListener {

    /**
     * Invoked whenever the connection manager transitions to a new [ConnectionState].
     */
    fun onStateChanged(state: ConnectionState) {}

    /**
     * Invoked when a network connection is successfully established.
     *
     * @param transport The active [MessageTransport] ready for packet transmission.
     */
    fun onConnected(transport: MessageTransport) {}

    /**
     * Invoked when a network connection has been disconnected.
     */
    fun onDisconnected() {}

    /**
     * Invoked when a connection error occurs during connect, listen, or runtime.
     *
     * @param message Descriptive error message.
     * @param cause Optional underlying exception.
     */
    fun onError(message: String, cause: Throwable? = null) {}
}
