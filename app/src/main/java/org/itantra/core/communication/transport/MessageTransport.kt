package org.itantra.core.communication.transport

/**
 * Minimal, technology-agnostic abstraction for raw byte transport across logical endpoints.
 *
 * This contract isolates the M2 protocol codec from concrete physical communication links
 * (such as Wi-Fi Direct, BLE L2CAP, sockets, or loopback in-memory channels).
 */
interface MessageTransport {

    /**
     * Transmits an opaque raw binary payload to a designated logical destination endpoint.
     *
     * @param destination The target logical endpoint.
     * @param payload The raw byte array to transmit.
     * @throws TransportException if the transport is closed or transmission fails.
     */
    suspend fun send(
        destination: TransportEndpoint,
        payload: ByteArray
    )

    /**
     * Registers or unregisters a listener callback invoked when a [TransportFrame] arrives.
     *
     * @param receiver The callback receiving incoming transport frames, or null to clear.
     */
    fun setReceiver(
        receiver: (suspend (TransportFrame) -> Unit)?
    )

    /**
     * Gracefully closes the transport channel and releases all associated resources.
     */
    suspend fun close()
}
