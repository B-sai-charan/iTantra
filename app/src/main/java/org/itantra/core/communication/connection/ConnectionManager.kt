package org.itantra.core.communication.connection

import org.itantra.core.communication.transport.MessageTransport

/**
 * Abstraction governing network session lifecycle, peer connection establishment,
 * and active [MessageTransport] exposure.
 *
 * This isolates higher-level application components (such as the transceiver coordinator)
 * from direct socket, host, port, and IP network topologies.
 */
interface ConnectionManager {

    /**
     * Current operational state of the connection.
     */
    val state: ConnectionState

    /**
     * Whether a connection is currently active and ready for transport I/O.
     */
    val isConnected: Boolean

    /**
     * The active [MessageTransport] instance when in [ConnectionState.CONNECTED], or null otherwise.
     */
    val activeTransport: MessageTransport?

    /**
     * Initiates connection establishment (client connect or server listen).
     *
     * @return [Result.success] with the active [MessageTransport] on success, or [Result.failure] on error.
     */
    suspend fun connect(): Result<MessageTransport>

    /**
     * Gracefully terminates the active connection and transitions to [ConnectionState.DISCONNECTED].
     */
    suspend fun disconnect()

    /**
     * Permanently releases all connection resources and underlying transports.
     */
    suspend fun release()

    /**
     * Registers or unregisters an observer listener for connection lifecycle events.
     *
     * @param listener Callback observer, or null to clear.
     */
    fun setListener(listener: ConnectionListener?)
}
