package org.itantra.core.communication.transport

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Low-level transport framing helper for TCP byte stream transport.
 *
 * Framing Format:
 * [4-byte Big-Endian UInt32 Frame Length] + [Opaque Payload Bytes]
 */
object TcpFraming {

    /**
     * Reads a single framed payload from the [InputStream].
     *
     * @param input Stream to read from.
     * @return Opaque payload byte array.
     * @throws TransportException on truncation, EOF, zero-length frame, or exceeding MAX_FRAME_PAYLOAD_BYTES.
     */
    fun readFrame(input: InputStream): ByteArray {
        val headerBuf = ByteArray(TcpTransportConstants.FRAME_HEADER_SIZE)
        readFullyExact(input, headerBuf, 0, TcpTransportConstants.FRAME_HEADER_SIZE)

        val lengthLong = ByteBuffer.wrap(headerBuf).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFFFFFFL

        if (lengthLong == 0L) {
            throw TransportException("Framing error: received invalid zero-length frame")
        }
        if (lengthLong > TcpTransportConstants.MAX_FRAME_PAYLOAD_BYTES) {
            throw TransportException(
                "Framing error: declared frame length ($lengthLong bytes) exceeds maximum allowed (${TcpTransportConstants.MAX_FRAME_PAYLOAD_BYTES} bytes)"
            )
        }

        val length = lengthLong.toInt()
        val payloadBuf = ByteArray(length)
        readFullyExact(input, payloadBuf, 0, length)
        return payloadBuf
    }

    /**
     * Writes an opaque binary payload with a 4-byte Big-Endian length header to the [OutputStream].
     *
     * @param output Stream to write to.
     * @param payload Opaque byte array.
     * @throws TransportException if payload violates size constraints or writing fails.
     */
    fun writeFrame(output: OutputStream, payload: ByteArray) {
        if (payload.isEmpty()) {
            throw TransportException("Cannot send empty payload")
        }
        if (payload.size > TcpTransportConstants.MAX_FRAME_PAYLOAD_BYTES) {
            throw TransportException(
                "Payload size (${payload.size} bytes) exceeds maximum frame limit (${TcpTransportConstants.MAX_FRAME_PAYLOAD_BYTES} bytes)"
            )
        }

        val header = ByteBuffer.allocate(TcpTransportConstants.FRAME_HEADER_SIZE)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt((payload.size.toLong() and 0xFFFFFFFFL).toInt())
            .array()

        val fullFrame = ByteArray(TcpTransportConstants.FRAME_HEADER_SIZE + payload.size)
        System.arraycopy(header, 0, fullFrame, 0, TcpTransportConstants.FRAME_HEADER_SIZE)
        System.arraycopy(payload, 0, fullFrame, TcpTransportConstants.FRAME_HEADER_SIZE, payload.size)

        output.write(fullFrame)
        output.flush()
    }

    /**
     * Reads exactly [length] bytes into [buffer] starting at [offset].
     * Loops until all bytes are accumulated or EOF is reached.
     */
    fun readFullyExact(input: InputStream, buffer: ByteArray, offset: Int, length: Int) {
        var bytesRead = 0
        while (bytesRead < length) {
            val count = input.read(buffer, offset + bytesRead, length - bytesRead)
            if (count < 0) {
                if (bytesRead == 0) {
                    throw TransportException("End of stream reached (EOF)")
                } else {
                    throw TransportException("Unexpected EOF while reading frame (read $bytesRead of $length bytes)")
                }
            }
            bytesRead += count
        }
    }
}
