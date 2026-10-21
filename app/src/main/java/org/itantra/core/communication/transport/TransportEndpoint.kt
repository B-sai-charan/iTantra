package org.itantra.core.communication.transport

/**
 * Represents an opaque logical endpoint in the M2 communication subsystem.
 *
 * This abstraction contains only a stable logical identifier string and makes
 * zero assumptions about physical networking, IP addresses, MAC addresses,
 * Bluetooth handles, or Wi-Fi Direct interfaces.
 *
 * @property id Non-blank logical identifier string representing the node.
 */
data class TransportEndpoint(
    val id: String
) {
    init {
        require(id.isNotBlank()) {
            "TransportEndpoint id must not be blank"
        }
    }

    override fun toString(): String = "TransportEndpoint($id)"
}
