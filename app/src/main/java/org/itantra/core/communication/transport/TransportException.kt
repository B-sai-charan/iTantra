package org.itantra.core.communication.transport

/**
 * Thrown when an operation on a [MessageTransport] fails due to closure,
 * connection loss, or transport-layer error.
 */
class TransportException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
