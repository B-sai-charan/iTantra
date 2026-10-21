package org.itantra.core.communication.transport

import java.util.Arrays

/**
 * Immutable container representing opaque binary payload bytes crossing the transport boundary.
 *
 * This layer does NOT interpret packet structure, CRC, or protocol fields; it only
 * carries raw bytes between logical endpoints.
 *
 * @property source The logical originating endpoint, if known (nullable).
 * @property destination The logical target destination endpoint, if known (nullable).
 */
class TransportFrame(
    val source: TransportEndpoint? = null,
    val destination: TransportEndpoint? = null,
    payload: ByteArray
) {
    private val _payload: ByteArray

    init {
        require(payload.isNotEmpty()) {
            "TransportFrame payload must not be empty"
        }
        this._payload = payload.copyOf()
    }

    /**
     * Defensive copy of the raw binary payload carried by this frame.
     */
    val payload: ByteArray
        get() = _payload.copyOf()

    /**
     * Number of bytes in the payload.
     */
    val payloadSize: Int
        get() = _payload.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransportFrame

        if (source != other.source) return false
        if (destination != other.destination) return false
        if (!_payload.contentEquals(other._payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = source?.hashCode() ?: 0
        result = 31 * result + (destination?.hashCode() ?: 0)
        result = 31 * result + _payload.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "TransportFrame(source=$source, destination=$destination, payloadSize=$payloadSize bytes)"
    }
}
