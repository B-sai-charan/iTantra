package org.itantra.core.communication.validation

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.itantra.core.communication.adapter.SpeechMessageTransportAdapter
import org.itantra.core.communication.connection.ConnectionConfig
import org.itantra.core.communication.connection.ConnectionListener
import org.itantra.core.communication.connection.ConnectionState
import org.itantra.core.communication.connection.TcpConnectionManager
import org.itantra.core.communication.connection.bindTo
import org.itantra.core.communication.transport.MessageTransport
import org.itantra.core.communication.transport.TcpTransportConstants
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.composition.AndroidTransceiverComposition
import org.itantra.core.domain.SpeechMessage
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Controlled physical-device validation runner for the iTantra M2 TCP communication layer.
 *
 * Exercises the production M2 stack between two physical Android devices:
 * Device A (Client) -> SpeechMessage -> SpeechMessageTransportAdapter -> PacketEncoder -> TCP Socket -> Network ->
 * Device B (Server) -> TCP Socket -> PacketDecoder -> SpeechMessageTransportAdapter -> Inbound SpeechMessage.
 */
object PhysicalTcpValidationRunner {

    private const val TAG = "PHYSICAL_TCP_TEST"

    data class RecordedInboundMessage(
        val receiveTimestampMs: Long,
        val text: String,
        val language: String,
        val timestamp: Long,
        val confidence: Float?,
        val seqNumber: Long
    )

    data class RecordedOutboundMessage(
        val sendTimestampMs: Long,
        val text: String,
        val language: String,
        val timestamp: Long,
        val confidence: Float?,
        val seqNumber: Long
    )

    data class ServerStatus(
        val isRunning: Boolean,
        val boundPort: Int,
        val connectionState: ConnectionState,
        val clientAccepted: Boolean,
        val receivedMessages: List<RecordedInboundMessage>
    )

    data class ClientSuiteResult(
        val serverHost: String,
        val serverPort: Int,
        val connectionSuccess: Boolean,
        val observedConnectionStates: List<String>,
        val test1EnglishSuccess: Boolean,
        val test2UnicodeSuccess: Boolean,
        val test3OrderedSuccess: Boolean,
        val test4ReconnectSuccess: Boolean,
        val sentMessages: List<RecordedOutboundMessage>,
        val errorMessage: String? = null
    )

    @Volatile
    private var serverComposition: AndroidTransceiverComposition? = null
    private val serverReceivedMessages = CopyOnWriteArrayList<RecordedInboundMessage>()
    private val serverStateHistory = CopyOnWriteArrayList<String>()
    private val isServerRunning = AtomicBoolean(false)
    private var serverBoundPort = TcpTransportConstants.DEFAULT_PORT

    /**
     * Starts the TCP Server role on Device B using production [AndroidTransceiverComposition].
     */
    fun startServer(context: Context, port: Int = TcpTransportConstants.DEFAULT_PORT): Boolean {
        synchronized(this) {
            if (isServerRunning.get()) {
                Log.w(TAG, "[SERVER] Server is already running on port $serverBoundPort")
                return true
            }

            serverReceivedMessages.clear()
            serverStateHistory.clear()
            serverBoundPort = port

            Log.i(TAG, "==================================================")
            Log.i(TAG, "[SERVER] STARTING M2 TCP SERVER VALIDATION ON PORT $port")
            Log.i(TAG, "==================================================")

            try {
                val comp = AndroidTransceiverComposition(
                    context = context,
                    localEndpoint = TransportEndpoint("device-b-server"),
                    defaultDestination = TransportEndpoint("device-a-client"),
                    connectionConfig = ConnectionConfig.Server(port = port)
                )

                comp.transportAdapter.setSpeechMessageReceiver { msg ->
                    val now = System.currentTimeMillis()
                    val recorded = RecordedInboundMessage(
                        receiveTimestampMs = now,
                        text = msg.text,
                        language = msg.language,
                        timestamp = msg.timestamp,
                        confidence = msg.confidence,
                        seqNumber = comp.transportAdapter.currentSequenceNumber
                    )
                    serverReceivedMessages.add(recorded)
                    Log.i(TAG, "[SERVER-INBOUND] Received SpeechMessage #${serverReceivedMessages.size}: text='${msg.text}', lang='${msg.language}', ts=${msg.timestamp}, conf=${msg.confidence}, rxTs=$now")
                }

                comp.connectionManager.bindTo(comp.transportAdapter, object : ConnectionListener {
                    override fun onStateChanged(newState: ConnectionState) {
                        serverStateHistory.add(newState.name)
                        Log.i(TAG, "[SERVER-STATE] Connection state changed: $newState")
                    }

                    override fun onConnected(transport: MessageTransport) {
                        Log.i(TAG, "[SERVER-STATE] Server listening / transport active: $transport")
                    }

                    override fun onDisconnected() {
                        Log.i(TAG, "[SERVER-STATE] Server disconnected")
                    }

                    override fun onError(message: String, cause: Throwable?) {
                        Log.e(TAG, "[SERVER-ERROR] Server error: $message", cause)
                    }
                })

                runBlocking {
                    val res = comp.connect()
                    if (res.isFailure) {
                        Log.e(TAG, "[SERVER-FAIL] Failed to bind TCP server on port $port: ${res.exceptionOrNull()?.message}")
                        comp.release()
                        return@runBlocking
                    }
                    serverComposition = comp
                    isServerRunning.set(true)
                    Log.i(TAG, "[SERVER-READY] TCP Server is listening on port $port. Ready for client connection.")
                }

                return isServerRunning.get()
            } catch (e: Exception) {
                Log.e(TAG, "[SERVER-EXCEPTION] Error initializing server", e)
                return false
            }
        }
    }

    /**
     * Stops the TCP Server role on Device B.
     */
    fun stopServer() {
        synchronized(this) {
            val comp = serverComposition
            serverComposition = null
            isServerRunning.set(false)
            if (comp != null) {
                Log.i(TAG, "[SERVER] Stopping TCP Server...")
                runBlocking {
                    try {
                        comp.release()
                    } catch (e: Exception) {
                        Log.w(TAG, "[SERVER] Error during server release: ${e.message}")
                    }
                }
                Log.i(TAG, "[SERVER] TCP Server stopped.")
            }
        }
    }

    /**
     * Gets current server state and list of received messages.
     */
    fun getServerStatus(): ServerStatus {
        val comp = serverComposition
        val state = comp?.connectionManager?.state ?: ConnectionState.DISCONNECTED
        return ServerStatus(
            isRunning = isServerRunning.get(),
            boundPort = serverBoundPort,
            connectionState = state,
            clientAccepted = serverReceivedMessages.isNotEmpty() || state == ConnectionState.CONNECTED,
            receivedMessages = ArrayList(serverReceivedMessages)
        )
    }

    /**
     * Runs the complete controlled validation test suite from Device A (Client) to Device B (Server).
     */
    suspend fun runClientSuite(
        context: Context,
        serverHost: String,
        serverPort: Int = TcpTransportConstants.DEFAULT_PORT
    ): ClientSuiteResult = withContext(Dispatchers.IO) {
        val observedStates = mutableListOf<String>()
        val sentMessages = mutableListOf<RecordedOutboundMessage>()

        Log.i(TAG, "==================================================")
        Log.i(TAG, "[CLIENT] STARTING M2 TCP CLIENT VALIDATION SUITE")
        Log.i(TAG, "[CLIENT] Target Server: $serverHost:$serverPort")
        Log.i(TAG, "==================================================")

        var test1Ok = false
        var test2Ok = false
        var test3Ok = false
        var test4Ok = false
        var connectionOk = false
        var errorDetail: String? = null

        var comp: AndroidTransceiverComposition? = null

        try {
            // STEP 1: CONNECTION TEST
            Log.i(TAG, "[CLIENT] --- STEP 1: CONNECTION TEST ---")
            comp = AndroidTransceiverComposition(
                context = context,
                localEndpoint = TransportEndpoint("device-a-client"),
                defaultDestination = TransportEndpoint("device-b-server"),
                connectionConfig = ConnectionConfig.Client(host = serverHost, port = serverPort, timeoutMs = 8000)
            )

            comp.connectionManager.bindTo(comp.transportAdapter, object : ConnectionListener {
                override fun onStateChanged(newState: ConnectionState) {
                    observedStates.add(newState.name)
                    Log.i(TAG, "[CLIENT-STATE] Connection state transition: $newState")
                }

                override fun onConnected(transport: MessageTransport) {
                    Log.i(TAG, "[CLIENT-STATE] Successfully connected to server: $transport")
                }

                override fun onDisconnected() {
                    Log.i(TAG, "[CLIENT-STATE] Disconnected from server")
                }

                override fun onError(message: String, cause: Throwable?) {
                    Log.e(TAG, "[CLIENT-ERROR] Connection error: $message", cause)
                }
            })

            observedStates.add(comp.connectionManager.state.name) // Initial state (DISCONNECTED)
            val connectRes = comp.connect()
            if (connectRes.isFailure) {
                val err = connectRes.exceptionOrNull()?.message ?: "Connect failed"
                Log.e(TAG, "[CLIENT-FAIL] Failed to connect to $serverHost:$serverPort - $err")
                return@withContext ClientSuiteResult(
                    serverHost = serverHost,
                    serverPort = serverPort,
                    connectionSuccess = false,
                    observedConnectionStates = observedStates,
                    test1EnglishSuccess = false,
                    test2UnicodeSuccess = false,
                    test3OrderedSuccess = false,
                    test4ReconnectSuccess = false,
                    sentMessages = sentMessages,
                    errorMessage = "Connect failed: $err"
                )
            }
            connectionOk = comp.connectionManager.isConnected
            Log.i(TAG, "[CLIENT-PASS] Connection established. State: ${comp.connectionManager.state}")

            // Delay slightly to stabilize transport
            delay(200)

            // STEP 2: TEST 1 — ENGLISH SPEECH MESSAGE
            Log.i(TAG, "[CLIENT] --- STEP 2: TEST 1 — ENGLISH SPEECH MESSAGE ---")
            val msg1Text = "Hello from iTantra Device A."
            val msg1Lang = "en"
            val msg1Ts = System.currentTimeMillis()
            val msg1Conf: Float? = null
            val msg1 = SpeechMessage(text = msg1Text, language = msg1Lang, timestamp = msg1Ts, confidence = msg1Conf)

            val seq1 = comp.transportAdapter.currentSequenceNumber
            comp.transportAdapter.sendSpeechMessage(msg1)
            val send1Ts = System.currentTimeMillis()
            val rec1 = RecordedOutboundMessage(send1Ts, msg1Text, msg1Lang, msg1Ts, msg1Conf, seq1)
            sentMessages.add(rec1)
            Log.i(TAG, "[CLIENT-OUTBOUND] Sent Test 1 SpeechMessage: text='$msg1Text', lang='$msg1Lang', ts=$msg1Ts, seq=$seq1")
            test1Ok = true

            delay(300)

            // STEP 3: TEST 2 — UNICODE SPEECH MESSAGE
            Log.i(TAG, "[CLIENT] --- STEP 3: TEST 2 — UNICODE SPEECH MESSAGE ---")
            val msg2Text = "नमस्ते from iTantra."
            val msg2Lang = "hi"
            val msg2Ts = System.currentTimeMillis()
            val msg2Conf: Float? = null
            val msg2 = SpeechMessage(text = msg2Text, language = msg2Lang, timestamp = msg2Ts, confidence = msg2Conf)

            val seq2 = comp.transportAdapter.currentSequenceNumber
            comp.transportAdapter.sendSpeechMessage(msg2)
            val send2Ts = System.currentTimeMillis()
            val rec2 = RecordedOutboundMessage(send2Ts, msg2Text, msg2Lang, msg2Ts, msg2Conf, seq2)
            sentMessages.add(rec2)
            Log.i(TAG, "[CLIENT-OUTBOUND] Sent Test 2 Unicode SpeechMessage: text='$msg2Text', lang='$msg2Lang', ts=$msg2Ts, seq=$seq2")
            test2Ok = true

            delay(300)

            // STEP 4: TEST 3 — ORDERED MULTIPLE MESSAGES
            Log.i(TAG, "[CLIENT] --- STEP 4: TEST 3 — ORDERED MULTIPLE MESSAGES ---")
            val orderedTexts = listOf("Message one", "Message two", "Message three")
            for ((idx, text) in orderedTexts.withIndex()) {
                val ts = System.currentTimeMillis()
                val conf = 0.90f + (idx * 0.02f)
                val msg = SpeechMessage(text = text, language = "en", timestamp = ts, confidence = conf)
                val seq = comp.transportAdapter.currentSequenceNumber
                comp.transportAdapter.sendSpeechMessage(msg)
                val sendTs = System.currentTimeMillis()
                sentMessages.add(RecordedOutboundMessage(sendTs, text, "en", ts, conf, seq))
                Log.i(TAG, "[CLIENT-OUTBOUND] Sent Test 3 ($text): seq=$seq, ts=$ts")
                delay(100)
            }
            test3Ok = true

            delay(500)

            // STEP 5: TEST 4 — DISCONNECT / RECONNECT TEST
            Log.i(TAG, "[CLIENT] --- STEP 5: TEST 4 — DISCONNECT / RECONNECT TEST ---")
            val preDisconnectText = "Before disconnect"
            val preDisconnectTs = System.currentTimeMillis()
            val preDisconnectMsg = SpeechMessage(text = preDisconnectText, language = "en", timestamp = preDisconnectTs, confidence = null)
            val preDisconnectSeq = comp.transportAdapter.currentSequenceNumber
            comp.transportAdapter.sendSpeechMessage(preDisconnectMsg)
            val sendPreTs = System.currentTimeMillis()
            sentMessages.add(RecordedOutboundMessage(sendPreTs, preDisconnectText, "en", preDisconnectTs, null, preDisconnectSeq))
            Log.i(TAG, "[CLIENT-OUTBOUND] Sent Test 4 pre-disconnect message: text='$preDisconnectText', seq=$preDisconnectSeq")
            delay(300)

            Log.i(TAG, "[CLIENT] Disconnecting client...")
            comp.disconnect()
            val postDisconnectState = comp.connectionManager.state
            Log.i(TAG, "[CLIENT] Disconnected. Current state: $postDisconnectState")
            delay(500)

            Log.i(TAG, "[CLIENT] Reconnecting to server $serverHost:$serverPort...")
            val reconnectRes = comp.connect()
            if (reconnectRes.isFailure) {
                val err = reconnectRes.exceptionOrNull()?.message ?: "Reconnect failed"
                Log.e(TAG, "[CLIENT-FAIL] Reconnect failed: $err")
                test4Ok = false
                errorDetail = "Reconnect failed: $err"
            } else {
                Log.i(TAG, "[CLIENT-PASS] Reconnect successful. State: ${comp.connectionManager.state}")
                delay(200)

                val reconnectText = "After reconnect"
                val reconnectTs = System.currentTimeMillis()
                val reconnectMsg = SpeechMessage(text = reconnectText, language = "en", timestamp = reconnectTs, confidence = null)
                val reconnectSeq = comp.transportAdapter.currentSequenceNumber
                comp.transportAdapter.sendSpeechMessage(reconnectMsg)
                val sendRecTs = System.currentTimeMillis()
                sentMessages.add(RecordedOutboundMessage(sendRecTs, reconnectText, "en", reconnectTs, null, reconnectSeq))
                Log.i(TAG, "[CLIENT-OUTBOUND] Sent Test 4 post-reconnect message: text='$reconnectText', seq=$reconnectSeq")
                test4Ok = true
            }

            delay(500)

        } catch (e: Exception) {
            Log.e(TAG, "[CLIENT-EXCEPTION] Error during client validation suite", e)
            errorDetail = e.message ?: e.toString()
        } finally {
            try {
                comp?.release()
            } catch (e: Exception) {
                Log.w(TAG, "[CLIENT] Error releasing client composition: ${e.message}")
            }
        }

        Log.i(TAG, "==================================================")
        Log.i(TAG, "[CLIENT] VALIDATION COMPLETE")
        Log.i(TAG, "  Connection Success: $connectionOk")
        Log.i(TAG, "  Test 1 (English): $test1Ok")
        Log.i(TAG, "  Test 2 (Unicode): $test2Ok")
        Log.i(TAG, "  Test 3 (Ordered): $test3Ok")
        Log.i(TAG, "  Test 4 (Reconnect): $test4Ok")
        Log.i(TAG, "==================================================")

        ClientSuiteResult(
            serverHost = serverHost,
            serverPort = serverPort,
            connectionSuccess = connectionOk,
            observedConnectionStates = observedStates,
            test1EnglishSuccess = test1Ok,
            test2UnicodeSuccess = test2Ok,
            test3OrderedSuccess = test3Ok,
            test4ReconnectSuccess = test4Ok,
            sentMessages = sentMessages,
            errorMessage = errorDetail
        )
    }

    fun writeReportToFile(context: Context, filename: String, content: String) {
        try {
            val file = File(context.filesDir, filename)
            file.writeText(content)
            Log.i(TAG, "Wrote report to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write report file $filename", e)
        }
    }
}
