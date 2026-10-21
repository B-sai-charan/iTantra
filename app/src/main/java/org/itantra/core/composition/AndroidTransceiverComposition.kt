package org.itantra.core.composition

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.itantra.core.audio.AudioCaptureConfig
import org.itantra.core.communication.adapter.SpeechMessageTransportAdapter
import org.itantra.core.communication.connection.ConnectionConfig
import org.itantra.core.communication.connection.ConnectionManager
import org.itantra.core.communication.connection.TcpConnectionManager
import org.itantra.core.communication.connection.bindTo
import org.itantra.core.communication.transport.MessageTransport
import org.itantra.core.communication.transport.TransportEndpoint
import org.itantra.core.coordinator.DefaultSpeechPipelineControl
import org.itantra.core.coordinator.SpeechPipelineControl
import org.itantra.core.coordinator.TransceiverCoordinator
import org.itantra.core.coordinator.TransceiverListener
import org.itantra.core.pipeline.SpeechPipeline
import org.itantra.core.tts.SherpaOfflineTtsConfig
import org.itantra.core.tts.SherpaOfflineTtsEngine
import org.itantra.core.tts.SpeechMessagePlaybackCoordinator
import org.itantra.core.tts.TtsEngine
import org.itantra.core.tts.playback.AndroidAudioTrackPlaybackEngine
import org.itantra.core.tts.playback.AudioPlaybackEngine
import org.itantra.core.vad.VadConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Production Android Composition Root for the iTantra tactical speech transceiver.
 *
 * Explicitly instantiates, wires, and governs the lifecycle of the complete dependency graph:
 * - Module 1 (M1): [SpeechPipeline] (Audio capture, Silero VAD, Whisper ASR)
 * - Module 2 (M2): [SpeechMessageTransportAdapter], [TcpConnectionManager], and [MessageTransport]
 * - Module 3 (M3): [SherpaOfflineTtsEngine], [AndroidAudioTrackPlaybackEngine], and [SpeechMessagePlaybackCoordinator]
 * - Top-Level: [TransceiverCoordinator] orchestrating cross-module lifecycle and error dispatching.
 *
 * Ownership Semantics:
 * - This composition root is the sole lifecycle and release owner of [SpeechPipeline], [TtsEngine],
 *   [AudioPlaybackEngine], [SpeechMessagePlaybackCoordinator], [SpeechMessageTransportAdapter],
 *   [TransceiverCoordinator], and [ConnectionManager].
 * - [ConnectionManager] owns and closes physical/virtual [MessageTransport] socket instances.
 * - [SpeechMessageTransportAdapter] borrows the active transport via [ConnectionManager.bindTo].
 * - [TransceiverCoordinator] and [SpeechMessagePlaybackCoordinator] have `ownsDependencies = false`,
 *   guaranteeing zero double-release or orphan leaks during teardown.
 */
class AndroidTransceiverComposition(
    val context: Context? = null,
    val localEndpoint: TransportEndpoint = TransportEndpoint("node-local"),
    val defaultDestination: TransportEndpoint = TransportEndpoint("node-remote"),
    val connectionConfig: ConnectionConfig = ConnectionConfig.Server(),
    val audioConfig: AudioCaptureConfig = AudioCaptureConfig(),
    val vadConfig: VadConfig = VadConfig(),
    val targetLanguage: String = "en",
    val ttsConfig: SherpaOfflineTtsConfig? = null,
    var listener: TransceiverListener? = null,
    // Optional dependency injections for testing & decoupling
    injectedPipeline: SpeechPipeline? = null,
    injectedPipelineControl: SpeechPipelineControl? = null,
    injectedTransportAdapter: SpeechMessageTransportAdapter? = null,
    injectedConnectionManager: ConnectionManager? = null,
    injectedTtsEngine: TtsEngine? = null,
    injectedPlaybackEngine: AudioPlaybackEngine? = null,
    injectedPlaybackCoordinator: SpeechMessagePlaybackCoordinator? = null,
    injectedCoordinator: TransceiverCoordinator? = null
) {
    private val lifecycleLock = Any()
    private val released = AtomicBoolean(false)

    /**
     * Whether this composition root has been permanently released.
     */
    val isReleased: Boolean
        get() = released.get()

    // -------------------------------------------------------------
    // M2: Communication Subsystem
    // -------------------------------------------------------------
    val transportAdapter: SpeechMessageTransportAdapter = injectedTransportAdapter ?: SpeechMessageTransportAdapter(
        defaultDestination = defaultDestination
    )

    val connectionManager: ConnectionManager = (injectedConnectionManager ?: TcpConnectionManager(
        localEndpoint = localEndpoint,
        config = connectionConfig,
        ownsTransport = true
    )).also { manager ->
        manager.bindTo(transportAdapter)
    }

    // -------------------------------------------------------------
    // M1: Speech Capture & ASR Subsystem
    // -------------------------------------------------------------
    val speechPipeline: SpeechPipeline? = injectedPipeline ?: context?.let { ctx ->
        SpeechPipeline(
            context = ctx,
            audioConfig = audioConfig,
            vadConfig = vadConfig,
            targetLanguage = targetLanguage,
            messageSink = transportAdapter
        )
    }

    private val pipelineControl: SpeechPipelineControl? = injectedPipelineControl
        ?: speechPipeline?.let { DefaultSpeechPipelineControl(it) }

    // -------------------------------------------------------------
    // M3: TTS Synthesis & Audio Playback Subsystem
    // -------------------------------------------------------------
    val ttsEngine: TtsEngine? = injectedTtsEngine ?: context?.let { ctx ->
        val engCfg = ttsConfig ?: SherpaOfflineTtsEngine.defaultEnglishConfig()
        val hinCfg = SherpaOfflineTtsEngine.defaultHindiConfig()
        SherpaOfflineTtsEngine(
            context = ctx,
            englishConfig = engCfg,
            hindiConfig = hinCfg,
            autoInitialize = false
        )
    }

    val playbackEngine: AudioPlaybackEngine = injectedPlaybackEngine ?: AndroidAudioTrackPlaybackEngine()

    val playbackCoordinator: SpeechMessagePlaybackCoordinator = injectedPlaybackCoordinator
        ?: if (ttsEngine != null) {
            SpeechMessagePlaybackCoordinator(
                ttsEngine = ttsEngine,
                playbackEngine = playbackEngine,
                ownsDependencies = false // Composition root owns lifecycle
            )
        } else {
            throw IllegalArgumentException(
                "Cannot construct SpeechMessagePlaybackCoordinator: TtsEngine is null. " +
                        "Provide a valid Context, ttsConfig, or injectedTtsEngine."
            )
        }

    // -------------------------------------------------------------
    // Top-Level Transceiver Coordinator
    // -------------------------------------------------------------
    val coordinator: TransceiverCoordinator = injectedCoordinator ?: TransceiverCoordinator(
        speechPipeline = pipelineControl,
        transportAdapter = transportAdapter,
        playbackCoordinator = playbackCoordinator,
        listener = listener,
        ownsDependencies = false // Composition root owns lifecycle
    )

    /**
     * Initializes any asynchronous dependencies (such as [SpeechPipeline] VAD/ASR neural models).
     *
     * @return [Result.success] on initialization, or [Result.failure] if model loading fails.
     */
    fun initialize(): Result<Unit> {
        synchronized(lifecycleLock) {
            if (released.get()) {
                return Result.failure(IllegalStateException("Cannot initialize a released AndroidTransceiverComposition"))
            }
            try {
                ttsEngine?.initialize()
            } catch (e: Exception) {
                return Result.failure(e)
            }
            return speechPipeline?.initialize() ?: Result.success(Unit)
        }
    }

    /**
     * Starts the local transceiver system (M1 capture, M2 listener wiring, M3 presentation).
     *
     * Note: Network connection is managed explicitly via [connect] or [connectionManager].
     *
     * @return [Result.success] on successful start, or [Result.failure] if startup fails.
     */
    fun start(): Result<Unit> {
        synchronized(lifecycleLock) {
            if (released.get()) {
                return Result.failure(IllegalStateException("Cannot start a released AndroidTransceiverComposition"))
            }
            return coordinator.start()
        }
    }

    /**
     * Stops the local transceiver capture and speech presentation.
     */
    fun stop() {
        synchronized(lifecycleLock) {
            if (released.get()) return
            coordinator.stop()
        }
    }

    /**
     * Initiates network connection establishment (client connect or server listen).
     */
    suspend fun connect(): Result<MessageTransport> {
        if (released.get()) {
            return Result.failure(IllegalStateException("Cannot connect a released AndroidTransceiverComposition"))
        }
        return connectionManager.connect()
    }

    /**
     * Disconnects the active network connection without tearing down local transceiver components.
     */
    suspend fun disconnect() {
        connectionManager.disconnect()
    }

    /**
     * Permanently releases all transceiver subsystems in strict, deterministic teardown order:
     * 1. Stops the coordinator (halting M1 capture and detaching M3 receivers).
     * 2. Disconnects and releases [ConnectionManager] (closing underlying sockets).
     * 3. Releases [TransceiverCoordinator].
     * 4. Releases [SpeechPipeline].
     * 5. Releases [SpeechMessagePlaybackCoordinator].
     * 6. Releases [TtsEngine].
     * 7. Releases [AudioPlaybackEngine].
     *
     * This operation is idempotent and thread-safe.
     */
    suspend fun release() {
        if (!released.compareAndSet(false, true)) return

        // 1. Stop active coordinator
        try {
            coordinator.stop()
        } catch (_: Exception) {}

        // 2. Disconnect and release connection manager
        try {
            connectionManager.release()
        } catch (_: Exception) {}

        // 3. Release coordinator
        try {
            coordinator.release()
        } catch (_: Exception) {}

        // 4. Release M1 pipeline
        try {
            pipelineControl?.release()
        } catch (_: Exception) {}

        // 5. Release M3 playback coordinator
        try {
            playbackCoordinator.release()
        } catch (_: Exception) {}

        // 6. Release TTS engine
        try {
            ttsEngine?.release()
        } catch (_: Exception) {}

        // 7. Release playback engine
        try {
            playbackEngine.release()
        } catch (_: Exception) {}
    }
}
