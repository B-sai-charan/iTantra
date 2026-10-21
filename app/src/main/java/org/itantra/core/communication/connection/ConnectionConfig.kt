package org.itantra.core.communication.connection

import org.itantra.core.communication.transport.TcpTransportConstants

/**
 * Configuration options for establishing a network connection session.
 */
sealed class ConnectionConfig {

    /**
     * Client configuration connecting to a remote host and port.
     *
     * @property host Remote hostname or IP address (e.g., "192.168.43.1").
     * @property port Remote TCP port (default: [TcpTransportConstants.DEFAULT_PORT]).
     * @property timeoutMs Connection timeout in milliseconds.
     */
    data class Client(
        val host: String,
        val port: Int = TcpTransportConstants.DEFAULT_PORT,
        val timeoutMs: Int = 5000
    ) : ConnectionConfig() {
        init {
            require(host.isNotBlank()) { "Client host cannot be blank" }
            require(port in 1..65535) { "Client port must be in range 1..65535 (was $port)" }
            require(timeoutMs > 0) { "Timeout must be positive (was $timeoutMs ms)" }
        }
    }

    /**
     * Server configuration listening on a local TCP port.
     *
     * @property port Local TCP port to bind (default: [TcpTransportConstants.DEFAULT_PORT], or 0 for ephemeral).
     * @property backlog Maximum queued incoming connections before accept.
     */
    data class Server(
        val port: Int = TcpTransportConstants.DEFAULT_PORT,
        val backlog: Int = 1
    ) : ConnectionConfig() {
        init {
            require(port in 0..65535) { "Server port must be in range 0..65535 (was $port)" }
            require(backlog > 0) { "Backlog must be positive (was $backlog)" }
        }
    }
}
