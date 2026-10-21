package org.itantra.core.communication.protocol

/**
 * Thrown when encoding or decoding of an M2 transceiver packet fails due to
 * corruption, malformed input, framing errors, or protocol violations.
 */
class ProtocolException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
