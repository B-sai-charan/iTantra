package org.itantra.core.coordinator

import org.itantra.core.audio.AudioCaptureMetrics
import org.itantra.core.audio.AudioCaptureState
import org.itantra.core.communication.adapter.SpeechMessageTransportAdapter
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.pipeline.SpeechPipeline
import org.itantra.core.pipeline.SpeechPipelineListener
import org.itantra.core.stt.AsrResult
import org.itantra.core.tts.SpeechMessagePlaybackCoordinator
import org.itantra.core.tts.SpeechMessageReceiver
import org.itantra.core.tts.TtsAudio
import org.itantra.core.tts.TtsEngineNotInitializedException
import org.itantra.core.tts.UnsupportedTtsLanguageException
import org.itantra.core.tts.playback.AudioPlaybackException
import org.itantra.core.tts.playback.AudioPlaybackReleasedException

/**
 * Top-level application coordinator for the iTantra tactical speech transceiver.
 *
 * Connects the three core subsystems:
 * - Module 1 (M1): Local speech capture, VAD, and Whisper ASR ([SpeechPipeline]).
 * - Module 2 (M2): Packet serialization, framing, and TCP transport ([SpeechMessageTransportAdapter]).
 * - Module 3 (M3): Neural TTS synthesis and serialized audio playback ([SpeechMessagePlaybackCoordinator]).
 *
 * Responsibilities:
 * 1. Orchestrates startup, shutdown, and release across M1, M2, and M3.
 * 2. Bridges local outbound [SpeechMessage] emissions to the M2 transport sink.
 * 3. Bridges remote inbound [SpeechMessage] receipts from M2 to the M3 playback coordinator.
 * 4. Dispatches unified lifecycle and error notifications to registered [TransceiverListener] observers.
 *
 * Non-responsibilities:
 * - Does NOT perform audio capture, VAD, ASR, packet codecs, TCP socket handling, TTS, or AudioTrack rendering directly.
 * - Does NOT own or manipulate Android UI or Activities.
 *
 * @property speechPipeline M1 speech capture and transcription controller.
 * @property transportAdapter M2 communication transport adapter.
 * @property playbackCoordinator M3 neural synthesis and playback coordinator.
 * @property listener Optional observer for transceiver lifecycle events, message flows, and errors.
 * @property ownsDependencies If true, releasing the coordinator will cascade teardown to M1, M2, and M3 dependencies.
 */
class TransceiverCoordinator(
    private val speechPipeline: SpeechPipelineControl? = null,
    val transportAdapter: SpeechMessageTransportAdapter,
    val playbackCoordinator: SpeechMessagePlaybackCoordinator,
    var listener: TransceiverListener? = null,
    private val ownsDependencies: Boolean = false
) {

    /**
     * Convenience constructor accepting a concrete [SpeechPipeline] instance.
     */
    constructor(
        speechPipeline: SpeechPipeline?,
        transportAdapter: SpeechMessageTransportAdapter,
        playbackCoordinator: SpeechMessagePlaybackCoordinator,
        listener: TransceiverListener? = null,
        ownsDependencies: Boolean = false
    ) : this(
        speechPipeline = speechPipeline?.let { DefaultSpeechPipelineControl(it) },
        transportAdapter = transportAdapter,
        playbackCoordinator = playbackCoordinator,
        listener = listener,
        ownsDependencies = ownsDependencies
    )

    private val lifecycleLock = Any()

    @Volatile
    private var _state: TransceiverState = TransceiverState.STOPPED

    /**
     * Current operational state of the transceiver coordinator.
     */
    val state: TransceiverState
        get() = _state

    /**
     * Whether the transceiver coordinator is actively running.
     */
    val isRunning: Boolean
        get() = _state == TransceiverState.RUNNING

    /**
     * Whether the transceiver coordinator has been permanently released.
     */
    val isReleased: Boolean
        get() = _state == TransceiverState.RELEASED

    /**
     * Internal listener receiving events from the M1 [SpeechPipeline].
     */
    private val pipelineListener = object : SpeechPipelineListener {
        override fun onSpeechStart() {}
        override fun onSpeechEnd(durationMs: Long) {}
        override fun onAsrStart(segmentDurationMs: Long) {}
        override fun onAsrResult(result: AsrResult, postSpeechLatencyMs: Long, segmentIndex: Int) {}

        override fun onSpeechMessage(message: SpeechMessage) {
            if (_state != TransceiverState.RUNNING) return
            listener?.onOutboundSpeechMessage(message)
        }

        override fun onVadProbability(probability: Float, isSpeech: Boolean) {}
        override fun onAudioTelemetry(metrics: AudioCaptureMetrics, rmsDbfs: Double) {}

        override fun onError(message: String) {
            if (message.startsWith("Transport Error:")) {
                listener?.onError(TransceiverError.M2TransportError(message))
            } else {
                listener?.onError(TransceiverError.M1PipelineError(message))
            }
        }

        override fun onStateChanged(state: AudioCaptureState) {}
    }

    init {
        attachInboundReceiver()
    }

    private fun attachInboundReceiver() {
        val receiver = SpeechMessageReceiver { incomingMessage ->
            handleInboundSpeechMessage(incomingMessage)
        }
        transportAdapter.setSpeechMessageReceiver(receiver)
        transportAdapter.setProtocolErrorListener { protocolEx ->
            listener?.onError(
                TransceiverError.M2TransportError(
                    message = "Protocol decode error: ${protocolEx.message}",
                    cause = protocolEx
                )
            )
        }
    }

    /**
     * Starts the transceiver system:
     * 1. Ensures the M3 receiver is attached to the M2 transport adapter.
     * 2. Starts the M1 speech capture and transcription pipeline.
     * 3. Transitions state to [TransceiverState.RUNNING].
     *
     * @return [Result.success] on successful start, or [Result.failure] if startup fails.
     */
    fun start(): Result<Unit> {
        synchronized(lifecycleLock) {
            when (_state) {
                TransceiverState.RUNNING -> return Result.success(Unit) // Idempotent no-op
                TransceiverState.RELEASED -> {
                    val err = IllegalStateException("Cannot start a released TransceiverCoordinator")
                    listener?.onError(TransceiverError.LifecycleError(err.message ?: "", err))
                    return Result.failure(err)
                }
                TransceiverState.STARTING, TransceiverState.STOPPING -> {
                    val err = IllegalStateException("Cannot start transceiver while transition is in progress: $_state")
                    listener?.onError(TransceiverError.LifecycleError(err.message ?: "", err))
                    return Result.failure(err)
                }
                TransceiverState.STOPPED, TransceiverState.FAILED -> {
                    updateState(TransceiverState.STARTING)

                    // Ensure M2 received messages to M3 playback coordinator are attached
                    attachInboundReceiver()

                    // Start M1 Speech Pipeline
                    val pipelineResult = speechPipeline?.start(pipelineListener) ?: Result.success(Unit)
                    if (pipelineResult.isFailure) {
                        updateState(TransceiverState.FAILED)
                        val cause = pipelineResult.exceptionOrNull()
                        val error = TransceiverError.M1PipelineError(
                            message = "Failed to start speech pipeline: ${cause?.message}",
                            cause = cause
                        )
                        listener?.onError(error)
                        return pipelineResult
                    }

                    updateState(TransceiverState.RUNNING)
                    return Result.success(Unit)
                }
            }
        }
    }

    /**
     * Programmatically transmits an outbound [SpeechMessage] over the M2 transport adapter.
     *
     * @param message The speech message to transmit.
     * @param destination Optional specific destination endpoint. If null, uses adapter's default destination.
     * @return [Result.success] on transmission dispatch, or [Result.failure] on error.
     */
    suspend fun sendOutboundSpeechMessage(
        message: SpeechMessage,
        destination: org.itantra.core.communication.transport.TransportEndpoint? = null
    ): Result<Unit> {
        if (_state != TransceiverState.RUNNING) {
            val err = IllegalStateException("Cannot send message: coordinator is not RUNNING (current state: $_state)")
            listener?.onError(TransceiverError.LifecycleError(err.message ?: "", err))
            return Result.failure(err)
        }
        return try {
            if (destination != null) {
                transportAdapter.sendSpeechMessage(message, destination)
            } else {
                transportAdapter.sendSpeechMessage(message)
            }
            listener?.onOutboundSpeechMessage(message)
            Result.success(Unit)
        } catch (e: org.itantra.core.communication.transport.TransportException) {
            val err = TransceiverError.M2TransportError("Transport transmission failed: ${e.message}", e)
            listener?.onError(err)
            Result.failure(e)
        } catch (e: org.itantra.core.communication.protocol.ProtocolException) {
            val err = TransceiverError.M2TransportError("Protocol encoding failed: ${e.message}", e)
            listener?.onError(err)
            Result.failure(e)
        } catch (e: Exception) {
            val err = TransceiverError.M2TransportError("Unexpected error sending message: ${e.message}", e)
            listener?.onError(err)
            Result.failure(e)
        }
    }

    /**
     * Stops the transceiver system:
     * 1. Stops the M1 speech capture and transcription pipeline.
     * 2. Transitions state to [TransceiverState.STOPPED].
     * (Inbound receiver remains active so device can receive peer messages while idle).
     *
     * This operation is idempotent.
     */
    fun stop() {
        synchronized(lifecycleLock) {
            when (_state) {
                TransceiverState.STOPPED, TransceiverState.RELEASED -> return // Idempotent no-op
                TransceiverState.STARTING, TransceiverState.RUNNING, TransceiverState.STOPPING, TransceiverState.FAILED -> {
                    updateState(TransceiverState.STOPPING)

                    // 1. Stop M1 Speech Pipeline
                    try {
                        speechPipeline?.stop()
                    } catch (e: Exception) {
                        listener?.onError(TransceiverError.M1PipelineError("Error stopping speech pipeline: ${e.message}", e))
                    }

                    // Inbound receiver remains attached across PTT / mic stop
                    updateState(TransceiverState.STOPPED)
                }
            }
        }
    }

    /**
     * Permanently releases all transceiver resources and transitions state to [TransceiverState.RELEASED].
     * If [ownsDependencies] is true, releases M1, M2, and M3 child dependencies.
     *
     * This operation is idempotent and thread-safe.
     */
    fun release() {
        synchronized(lifecycleLock) {
            if (_state == TransceiverState.RELEASED) return // Idempotent no-op

            if (_state == TransceiverState.RUNNING || _state == TransceiverState.STARTING) {
                stop()
            }

            updateState(TransceiverState.RELEASED)

            // Detach M2 receiver and protocol error listener on permanent release
            transportAdapter.setSpeechMessageReceiver(null as SpeechMessageReceiver?)
            transportAdapter.setProtocolErrorListener(null)

            if (ownsDependencies) {
                try {
                    speechPipeline?.release()
                } catch (e: Exception) {
                    listener?.onError(TransceiverError.M1PipelineError("Error releasing speech pipeline: ${e.message}", e))
                }
                try {
                    kotlinx.coroutines.runBlocking { transportAdapter.close() }
                } catch (e: Exception) {
                    listener?.onError(TransceiverError.M2TransportError("Error closing transport adapter: ${e.message}", e))
                }
                playbackCoordinator.release()
            }
        }
    }

    /**
     * Internal handler for incoming [SpeechMessage] instances delivered across the M2 boundary.
     */
    private suspend fun handleInboundSpeechMessage(message: SpeechMessage) {
        if (_state == TransceiverState.RELEASED) return

        listener?.onInboundSpeechMessage(message)

        try {
            val audio: TtsAudio = playbackCoordinator.processMessage(message)
            listener?.onInboundPlaybackCompleted(message, audio)
        } catch (e: UnsupportedTtsLanguageException) {
            listener?.onError(TransceiverError.M3TtsError("Unsupported TTS language '${e.language}' for received message", e))
        } catch (e: TtsEngineNotInitializedException) {
            listener?.onError(TransceiverError.M3TtsError("TTS engine not initialized for received message: ${e.message}", e))
        } catch (e: AudioPlaybackReleasedException) {
            listener?.onError(TransceiverError.M3PlaybackError("Audio playback engine is released: ${e.message}", e))
        } catch (e: AudioPlaybackException) {
            listener?.onError(TransceiverError.M3PlaybackError("Audio playback failed: ${e.message}", e))
        } catch (e: IllegalArgumentException) {
            listener?.onError(TransceiverError.M3TtsError("Invalid message parameters: ${e.message}", e))
        } catch (e: Throwable) {
            listener?.onError(TransceiverError.M3TtsError("Unexpected error during speech playback: ${e.message}", e))
        }
    }

    private fun updateState(newState: TransceiverState) {
        _state = newState
        listener?.onStateChanged(newState)
    }
}
