package org.itantra.core.communication.adapter

import org.itantra.core.communication.protocol.PacketDecoder
import org.itantra.core.communication.protocol.PacketEncoder
import org.itantra.core.communication.protocol.ProtocolException
import org.itantra.core.communication.protocol.TransceiverPacketMapper
import org.itantra.core.communication.transport.MessageTransport
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.communication.transport.TransportFrame
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.pipeline.SpeechMessageSink
import org.itantra.core.tts.SpeechMessageReceiver
import java.util.concurrent.atomic.AtomicLong

/**
 * M2-owned communication adapter integrating M1 [SpeechMessage] domain objects
 * with M2 [PacketEncoder], [PacketDecoder], and [MessageTransport].
 * Implements [SpeechMessageSink] to serve as the direct injectable boundary for the M1 [SpeechPipeline].
 *
 * Pipeline Flow:
 * Outbound:
 *   M1 SpeechMessage -> TransceiverPacketMapper -> TransceiverPacket -> PacketEncoder -> ByteArray -> MessageTransport.send()
 * Inbound:
 *   MessageTransport receive -> TransportFrame.payload -> PacketDecoder.decode() -> TransceiverPacket -> TransceiverPacketMapper.toSpeechMessage() -> Reconstructed SpeechMessage
 *
 * @property transport The underlying physical or virtual [MessageTransport].
 * @property defaultDestination The default remote [TransportEndpoint] if not explicitly specified on send.
 * @param initialSequenceNumber Starting UInt32 sequence number (default: 0L).
 */
class SpeechMessageTransportAdapter(
    transport: MessageTransport? = null,
    val defaultDestination: TransportEndpoint? = null,
    initialSequenceNumber: Long = 0L
) : SpeechMessageSink {
    private val sequenceCounter = AtomicLong(initialSequenceNumber and 0xFFFFFFFFL)

    @Volatile
    private var _transport: MessageTransport? = null

    @Volatile
    private var speechMessageReceiver: (suspend (SpeechMessage) -> Unit)? = null

    @Volatile
    private var protocolErrorListener: ((ProtocolException) -> Unit)? = null

    init {
        transport?.let { bindTransport(it) }
    }

    /**
     * Currently bound [MessageTransport], or null if unbound/disconnected.
     */
    val activeTransport: MessageTransport?
        get() = _transport

    /**
     * Current sequence number value (read-only peek).
     */
    val currentSequenceNumber: Long
        get() = sequenceCounter.get()

    /**
     * Binds a new [MessageTransport] to this adapter:
     * 1. Safely unbinds and clears the receiver from the previous transport if present.
     * 2. Registers this adapter's frame receiver on [newTransport] (if non-null).
     * 3. Sets [activeTransport] to [newTransport].
     *
     * Preserves existing sequence numbers, M3 speech receivers, and error listeners across reconnects.
     */
    fun bindTransport(newTransport: MessageTransport?) {
        synchronized(this) {
            val oldTransport = _transport
            if (oldTransport === newTransport) return

            // 1. Unregister receiver from old transport
            oldTransport?.let {
                try {
                    it.setReceiver(null)
                } catch (_: Exception) {}
            }

            // 2. Register receiver on new transport
            newTransport?.let {
                it.setReceiver { frame ->
                    handleIncomingFrame(frame)
                }
            }

            _transport = newTransport
            android.util.Log.i("PHYSICAL_E2E", "[ADAPTER_BIND] Bound MessageTransport to SpeechMessageTransportAdapter (hasTransport=${newTransport != null})")
        }
    }

    /**
     * Unbinds the current transport and clears its receiver callback.
     */
    fun unbindTransport() {
        bindTransport(null)
    }

    /**
     * Atomically allocates the next UInt32 sequence number in range [0, 4294967295],
     * wrapping around cleanly to 0 on UInt32 overflow.
     */
    fun nextSequenceNumber(): Long {
        while (true) {
            val current = sequenceCounter.get()
            val next = (current + 1L) and 0xFFFFFFFFL
            if (sequenceCounter.compareAndSet(current, next)) {
                return current
            }
        }
    }

    /**
     * Encodes and transmits a [SpeechMessage] over the currently bound [MessageTransport].
     *
     * @param message The M1 speech message to transmit.
     * @param destination The target endpoint, or [defaultDestination] if null.
     * @throws IllegalArgumentException if destination is null and no defaultDestination configured.
     * @throws org.itantra.core.communication.transport.TransportException if no transport is bound or transmission fails.
     * @throws ProtocolException if message violates protocol invariants.
     */
    suspend fun sendSpeechMessage(
        message: SpeechMessage,
        destination: TransportEndpoint? = defaultDestination
    ) {
        val target = destination
            ?: throw IllegalArgumentException("No destination endpoint specified and no defaultDestination configured")

        val active = _transport
            ?: throw org.itantra.core.communication.transport.TransportException(
                "Cannot send speech message: no active MessageTransport bound to SpeechMessageTransportAdapter"
            )

        val seq = nextSequenceNumber()
        val packet = TransceiverPacketMapper.fromSpeechMessage(message, seq)
        val payload = PacketEncoder.encode(packet)
        android.util.Log.i("PHYSICAL_E2E", "[M2_SEND] Sending SpeechMessage seq=$seq (len=${payload.size}B, text='${message.text}', lang='${message.language}') to destination=$target")
        active.send(target, payload)
    }

    override suspend fun emit(message: SpeechMessage) {
        sendSpeechMessage(message)
    }

    /**
     * Registers or unregisters an M3 [org.itantra.core.tts.SpeechMessageReceiver] boundary consumer.
     *
     * @param receiver The M3 consumer invoked on each verified incoming speech message, or null to clear.
     */
    fun setSpeechMessageReceiver(receiver: org.itantra.core.tts.SpeechMessageReceiver?) {
        this.speechMessageReceiver = if (receiver != null) {
            { msg -> receiver.onSpeechMessageReceived(msg) }
        } else {
            null
        }
    }

    /**
     * Registers or unregisters the application listener callback to receive reconstructed [SpeechMessage]s.
     *
     * @param receiver Callback invoked on each valid reconstructed speech message, or null to clear.
     */
    fun setSpeechMessageReceiver(receiver: (suspend (SpeechMessage) -> Unit)?) {
        this.speechMessageReceiver = receiver
    }

    /**
     * Registers or unregisters a listener callback for incoming malformed packets or CRC mismatch errors.
     *
     * @param listener Callback invoked on protocol decoding failures, or null to clear.
     */
    fun setProtocolErrorListener(listener: ((ProtocolException) -> Unit)?) {
        this.protocolErrorListener = listener
    }

    /**
     * Closes the underlying transport channel and unregisters all receivers and error listeners.
     */
    suspend fun close() {
        setSpeechMessageReceiver(null as SpeechMessageReceiver?)
        protocolErrorListener = null
        val toClose = synchronized(this) {
            val t = _transport
            _transport = null
            t
        }
        toClose?.let {
            try { it.setReceiver(null) } catch (_: Exception) {}
            it.close()
        }
    }

    private suspend fun handleIncomingFrame(frame: TransportFrame) {
        android.util.Log.i("PHYSICAL_E2E", "[M2_RECEIVE] Received frame (${frame.payload.size} bytes) from source=${frame.source}")
        val callback = speechMessageReceiver
        if (callback == null) {
            android.util.Log.w("PHYSICAL_E2E", "[M2_DROP] Inbound frame dropped: no SpeechMessageReceiver registered")
            return
        }
        try {
            val packet = PacketDecoder.decode(frame.payload)
            val speechMessage = TransceiverPacketMapper.toSpeechMessage(packet)
            android.util.Log.i("PHYSICAL_E2E", "[M2_DECODE] Decoded packet seq=${packet.sequenceNumber}: text='${speechMessage.text}', lang='${speechMessage.language}'")
            try {
                callback.invoke(speechMessage)
                android.util.Log.i("PHYSICAL_E2E", "[M2_DELIVERED] Delivered SpeechMessage seq=${packet.sequenceNumber} to receiver callback")
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                // Downstream M3 consumer failure is isolated from transport/packet decoding loop
            }
        } catch (e: ProtocolException) {
            // Malformed packet or CRC mismatch is discarded at protocol boundary without producing a SpeechMessage
            android.util.Log.e("PHYSICAL_E2E", "[M2_DECODE_FAIL] Protocol decode error: ${e.message}", e)
            protocolErrorListener?.invoke(e)
        }
    }
}
