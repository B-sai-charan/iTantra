package org.itantra.core.communication.transport

/**
 * Transport framing and networking constants for the M2 TCP socket transport.
 * Adheres strictly to docs/communication/TRANSPORT_ARCHITECTURE_DECISION.md.
 */
object TcpTransportConstants {
    /** Default non-privileged port for iTantra MVP TCP communication. */
    const val DEFAULT_PORT: Int = 42173

    /** Maximum allowed frame payload size in bytes (70 KB safety ceiling). */
    const val MAX_FRAME_PAYLOAD_BYTES: Int = 70000

    /** Size of the 4-byte Big-Endian length prefix header in bytes. */
    const val FRAME_HEADER_SIZE: Int = 4
}
