package org.itantra.core.communication.connection

import org.itantra.core.communication.adapter.SpeechMessageTransportAdapter
import org.itantra.core.communication.transport.MessageTransport

/**
 * Binds this [ConnectionManager] to the provided [SpeechMessageTransportAdapter] so that
 * when connection state transitions occur, the active transport is dynamically attached or detached.
 *
 * @param adapter The adapter to bind.
 * @param chainedListener Optional additional listener to receive connection events.
 * @return The registered [ConnectionListener].
 */
fun ConnectionManager.bindTo(
    adapter: SpeechMessageTransportAdapter,
    chainedListener: ConnectionListener? = null
): ConnectionListener {
    // If manager is already connected, bind immediately
    activeTransport?.let { adapter.bindTransport(it) }

    val listener = object : ConnectionListener {
        override fun onStateChanged(state: ConnectionState) {
            chainedListener?.onStateChanged(state)
        }

        override fun onConnected(transport: MessageTransport) {
            adapter.bindTransport(transport)
            chainedListener?.onConnected(transport)
        }

        override fun onDisconnected() {
            adapter.unbindTransport()
            chainedListener?.onDisconnected()
        }

        override fun onError(message: String, cause: Throwable?) {
            chainedListener?.onError(message, cause)
        }
    }

    setListener(listener)
    return listener
}
