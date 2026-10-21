package org.itantra.core.communication.transport

/**
 * Deterministic in-memory test implementation of [MessageTransport] for JVM tests.
 *
 * Facilitates direct end-to-end integration testing between logical endpoints
 * without requiring Android OS, socket, radio, or physical networking dependencies.
 *
 * @property localEndpoint The logical identifier of this local transport instance.
 */
class LoopbackTransport(
    val localEndpoint: TransportEndpoint = TransportEndpoint("loopback-local")
) : MessageTransport {

    private var receiver: (suspend (TransportFrame) -> Unit)? = null
    private var isClosed = false
    private val peers = mutableMapOf<TransportEndpoint, LoopbackTransport>()

    /**
     * Connects this transport directly to a peer [LoopbackTransport] instance
     * for bidirectional in-memory message delivery.
     */
    fun connectPeer(peer: LoopbackTransport) {
        peers[peer.localEndpoint] = peer
        peer.peers[localEndpoint] = this
    }

    override suspend fun send(destination: TransportEndpoint, payload: ByteArray) {
        if (isClosed) {
            throw TransportException("Cannot send payload: LoopbackTransport '$localEndpoint' is closed")
        }
        require(payload.isNotEmpty()) {
            "Cannot send empty payload"
        }

        val frame = TransportFrame(
            source = localEndpoint,
            destination = destination,
            payload = payload
        )

        if (destination == localEndpoint) {
            receiver?.invoke(frame)
        } else {
            val peer = peers[destination]
                ?: throw TransportException("Unknown destination endpoint: $destination (not connected)")
            if (peer.isClosed) {
                throw TransportException("Destination peer is closed: $destination")
            }
            peer.receiver?.invoke(frame)
        }
    }

    override fun setReceiver(receiver: (suspend (TransportFrame) -> Unit)?) {
        this.receiver = receiver
    }

    override suspend fun close() {
        isClosed = true
        receiver = null
    }

    /**
     * Returns true if this transport has been closed.
     */
    val closed: Boolean
        get() = isClosed
}
