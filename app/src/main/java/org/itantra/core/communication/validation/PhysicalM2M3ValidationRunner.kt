package org.itantra.core.communication.validation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.itantra.core.communication.connection.ConnectionConfig
import org.itantra.core.communication.connection.ConnectionListener
import org.itantra.core.communication.connection.ConnectionState
import org.itantra.core.communication.transport.MessageTransport
import org.itantra.core.communication.transport.TcpTransportConstants
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.composition.AndroidTransceiverComposition
import org.itantra.core.coordinator.TransceiverError
import org.itantra.core.coordinator.TransceiverListener
import org.itantra.core.coordinator.TransceiverState
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.tts.TtsAudio
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Controlled physical-device validation runner for the M2 -> M3 production integration.
 *
 * Flow:
 * Device A (Client)
 *   SpeechMessage -> production TCP/Wi-Fi transport
 *   -> Device B (Server/Receiver)
 *   -> TransceiverCoordinator / SpeechMessageReceiver boundary
 *   -> SpeechMessagePlaybackCoordinator
 *   -> SherpaOfflineTtsEngine (Piper VITS ONNX)
 *   -> TtsAudio
 *   -> AndroidAudioTrackPlaybackEngine
 *   -> Physical Device Speaker
 */
object PhysicalM2M3ValidationRunner {

    private const val TAG = "M2_M3_TEST"

    data class ServerResult(
        val isListening: Boolean,
        val boundPort: Int,
        val receivedCount: Int,
        val playbackCompletedCount: Int,
        val errorMessage: String? = null
    )

    data class SenderResult(
        val serverHost: String,
        val serverPort: Int,
        val connectionSuccess: Boolean,
        val msg1Sent: Boolean,
        val msg2Sent: Boolean,
        val sentCount: Int,
        val errorMessage: String? = null
    )

    @Volatile
    private var serverComposition: AndroidTransceiverComposition? = null
    private val isServerRunning = AtomicBoolean(false)
    private val receivedMessages = CopyOnWriteArrayList<SpeechMessage>()
    private val completedPlaybacks = CopyOnWriteArrayList<TtsAudio>()
    private val serverErrors = CopyOnWriteArrayList<String>()

    /**
     * Starts Device B in Server / Receiver role using full production [AndroidTransceiverComposition].
     */
    fun startReceiver(context: Context, port: Int = TcpTransportConstants.DEFAULT_PORT): Boolean {
        synchronized(this) {
            if (isServerRunning.get()) {
                Log.w(TAG, "[RECEIVER] Already running on port $port")
                return true
            }

            receivedMessages.clear()
            completedPlaybacks.clear()
            serverErrors.clear()

            Log.i(TAG, "==================================================")
            Log.i(TAG, "[RECEIVER] STARTING M2 -> M3 RECEIVER ON PORT $port")
            Log.i(TAG, "==================================================")

            try {
                val listener = object : TransceiverListener {
                    override fun onStateChanged(state: TransceiverState) {
                        Log.i(TAG, "[RECEIVER-STATE] Transceiver state: $state")
                    }

                    override fun onOutboundSpeechMessage(message: SpeechMessage) {}

                    override fun onInboundSpeechMessage(message: SpeechMessage) {
                        receivedMessages.add(message)
                        Log.i(TAG, "[RECEIVER-INBOUND-CB] Received inbound message #${receivedMessages.size}: text='${message.text}', lang='${message.language}'")
                    }

                    override fun onInboundPlaybackCompleted(message: SpeechMessage, audio: TtsAudio) {
                        completedPlaybacks.add(audio)
                        Log.i(TAG, "[RECEIVER-PLAYBACK-CB] Completed playback #${completedPlaybacks.size} for text='${message.text}' (samples=${audio.sampleCount})")
                    }

                    override fun onError(error: TransceiverError) {
                        val errMsg = "Error [${error::class.simpleName}]: ${error.message}"
                        serverErrors.add(errMsg)
                        Log.e(TAG, "[RECEIVER-ERROR] $errMsg", error.cause)
                    }
                }

                val comp = AndroidTransceiverComposition(
                    context = context,
                    localEndpoint = TransportEndpoint("device-b-receiver"),
                    defaultDestination = TransportEndpoint("device-a-sender"),
                    connectionConfig = ConnectionConfig.Server(port = port),
                    listener = listener
                )

                // 1. Initialize models (Piper TTS & VAD/ASR)
                Log.i(TAG, "[RECEIVER] Initializing transceiver composition & TTS model...")
                val initRes = comp.initialize()
                if (initRes.isFailure) {
                    val err = "Failed to initialize composition: ${initRes.exceptionOrNull()?.message}"
                    Log.e(TAG, "[RECEIVER-FAIL] $err")
                    runBlocking { comp.release() }
                    return false
                }
                Log.i(TAG, "[RECEIVER] Transceiver composition initialized successfully.")

                // 2. Start transceiver coordinator (attaches M3 receiver to M2 adapter)
                val startRes = comp.start()
                if (startRes.isFailure) {
                    val err = "Failed to start transceiver coordinator: ${startRes.exceptionOrNull()?.message}"
                    Log.e(TAG, "[RECEIVER-FAIL] $err")
                    runBlocking { comp.release() }
                    return false
                }
                Log.i(TAG, "[RECEIVER] Transceiver coordinator started.")

                // 3. Bind and start listening on TCP Server port
                runBlocking {
                    val connectRes = comp.connect()
                    if (connectRes.isFailure) {
                        val err = "Failed to bind TCP server on port $port: ${connectRes.exceptionOrNull()?.message}"
                        Log.e(TAG, "[RECEIVER-FAIL] $err")
                        comp.release()
                        return@runBlocking
                    }
                    serverComposition = comp
                    isServerRunning.set(true)
                    Log.i(TAG, "[RECEIVER-READY] M2-M3 Receiver is listening on port $port. Ready for inbound speech messages.")
                }

                return isServerRunning.get()
            } catch (e: Exception) {
                Log.e(TAG, "[RECEIVER-EXCEPTION] Exception starting receiver", e)
                return false
            }
        }
    }

    /**
     * Stops the Receiver on Device B.
     */
    fun stopReceiver() {
        synchronized(this) {
            val comp = serverComposition
            serverComposition = null
            isServerRunning.set(false)
            if (comp != null) {
                Log.i(TAG, "[RECEIVER] Stopping M2-M3 Receiver...")
                runBlocking {
                    try {
                        comp.release()
                    } catch (e: Exception) {
                        Log.w(TAG, "[RECEIVER] Error releasing composition: ${e.message}")
                    }
                }
                Log.i(TAG, "[RECEIVER] Receiver stopped.")
            }
        }
    }

    fun getReceiverStatus(): ServerResult {
        return ServerResult(
            isListening = isServerRunning.get(),
            boundPort = TcpTransportConstants.DEFAULT_PORT,
            receivedCount = receivedMessages.size,
            playbackCompletedCount = completedPlaybacks.size,
            errorMessage = if (serverErrors.isNotEmpty()) serverErrors.joinToString("; ") else null
        )
    }

    /**
     * Executes the sender role from Device A, connecting to Device B and transmitting the test messages.
     */
    suspend fun runSenderSuite(
        context: Context,
        serverHost: String,
        serverPort: Int = TcpTransportConstants.DEFAULT_PORT
    ): SenderResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "[SENDER] STARTING M2 -> M3 SENDER SUITE")
        Log.i(TAG, "[SENDER] Connecting to receiver at $serverHost:$serverPort")
        Log.i(TAG, "==================================================")

        var comp: AndroidTransceiverComposition? = null
        var connOk = false
        var msg1Sent = false
        var msg2Sent = false
        var errorDetail: String? = null
        val sentCount = AtomicInteger(0)

        try {
            comp = AndroidTransceiverComposition(
                context = context,
                localEndpoint = TransportEndpoint("device-a-sender"),
                defaultDestination = TransportEndpoint("device-b-receiver"),
                connectionConfig = ConnectionConfig.Client(host = serverHost, port = serverPort, timeoutMs = 8000)
            )

            val startRes = comp.start()
            if (startRes.isFailure) {
                errorDetail = "Failed to start sender coordinator: ${startRes.exceptionOrNull()?.message}"
                Log.e(TAG, "[SENDER-FAIL] $errorDetail")
                return@withContext SenderResult(serverHost, serverPort, false, false, false, 0, errorDetail)
            }

            Log.i(TAG, "[SENDER] Connecting TCP client to $serverHost:$serverPort...")
            val connectRes = comp.connect()
            if (connectRes.isFailure) {
                errorDetail = "Failed to connect to receiver: ${connectRes.exceptionOrNull()?.message}"
                Log.e(TAG, "[SENDER-FAIL] $errorDetail")
                return@withContext SenderResult(serverHost, serverPort, false, false, false, 0, errorDetail)
            }

            connOk = comp.connectionManager.isConnected
            Log.i(TAG, "[SENDER-CONNECTED] Connected successfully to Device B receiver.")

            delay(300)

            // TEST 1 — HINDI MESSAGE
            val text1 = "नमस्ते, यह iTantra का नियंत्रित Zwei-Geräte परीक्षण संदेश है।"
            val ts1 = System.currentTimeMillis()
            val msg1 = SpeechMessage(text = text1, language = "hi", timestamp = ts1, confidence = null)

            Log.i(TAG, "[SENDER-OUTBOUND] Transmitting Test 1 Message (Hindi): '$text1'")
            comp.coordinator.sendOutboundSpeechMessage(msg1)
            msg1Sent = true
            sentCount.incrementAndGet()
            Log.i(TAG, "[SENDER-SENT] Test 1 Message dispatched over TCP.")

            // Wait 14 seconds for Device B to synthesize (Rohan) & play audio through speaker
            Log.i(TAG, "[SENDER] Waiting 14s for Device B synthesis & audio playback...")
            delay(14000)

            // TEST 2 — SECOND HINDI MESSAGE
            val text2 = "यह iTantra का दूसरा नियंत्रित हिंदी संदेश है।"
            val ts2 = System.currentTimeMillis()
            val msg2 = SpeechMessage(text = text2, language = "hi", timestamp = ts2, confidence = null)

            Log.i(TAG, "[SENDER-OUTBOUND] Transmitting Test 2 Message (Hindi): '$text2'")
            comp.coordinator.sendOutboundSpeechMessage(msg2)
            msg2Sent = true
            sentCount.incrementAndGet()
            Log.i(TAG, "[SENDER-SENT] Test 2 Message dispatched over TCP.")

            // Wait 14 seconds for Device B to synthesize (Rohan) & play audio through speaker
            Log.i(TAG, "[SENDER] Waiting 14s for Device B synthesis & audio playback...")
            delay(14000)

            // TEST 3 — ENGLISH REGRESSION MESSAGE
            val text3 = "Hello, this is a controlled iTantra communication test."
            val ts3 = System.currentTimeMillis()
            val msg3 = SpeechMessage(text = text3, language = "en", timestamp = ts3, confidence = null)

            Log.i(TAG, "[SENDER-OUTBOUND] Transmitting Test 3 Message (English): '$text3'")
            comp.coordinator.sendOutboundSpeechMessage(msg3)
            sentCount.incrementAndGet()
            Log.i(TAG, "[SENDER-SENT] Test 3 Message dispatched over TCP.")

            // Wait 10 seconds for Device B to synthesize (Lessac) & play audio through speaker
            Log.i(TAG, "[SENDER] Waiting 10s for Device B synthesis & audio playback...")
            delay(10000)

            Log.i(TAG, "[SENDER] M2->M3 transmission sequence complete. Total sent: ${sentCount.get()}")

        } catch (e: Exception) {
            errorDetail = e.message ?: e.toString()
            Log.e(TAG, "[SENDER-EXCEPTION] Error during sender suite", e)
        } finally {
            try {
                comp?.release()
            } catch (e: Exception) {
                Log.w(TAG, "[SENDER] Error releasing sender composition: ${e.message}")
            }
        }

        SenderResult(
            serverHost = serverHost,
            serverPort = serverPort,
            connectionSuccess = connOk,
            msg1Sent = msg1Sent,
            msg2Sent = msg2Sent,
            sentCount = sentCount.get(),
            errorMessage = errorDetail
        )
    }
}
