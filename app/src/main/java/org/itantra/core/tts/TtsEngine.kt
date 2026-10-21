package org.itantra.core.tts

/**
 * Explicit lifecycle states of a [TtsEngine].
 */
enum class TtsEngineState {
    /** Engine is instantiated but native models/resources are not loaded yet. */
    UNINITIALIZED,
    /** Engine is actively loading native models/resources. */
    INITIALIZING,
    /** Engine is ready to accept synthesis requests. */
    READY,
    /** Engine has been released and native resources have been freed. */
    RELEASED,
    /** Engine initialization failed due to an error or invalid configuration. */
    FAILED
}

/**
 * Minimal offline Text-To-Speech engine abstraction for M3.
 *
 * Decouples neural speech synthesis from hardware audio playback (AudioTrack)
 * and upstream message transport/routing.
 */
interface TtsEngine {

    /**
     * Current explicit lifecycle state of the engine.
     */
    val state: TtsEngineState

    /**
     * Whether the engine is currently in the [TtsEngineState.READY] state and ready to synthesize speech.
     */
    val isInitialized: Boolean
        get() = state == TtsEngineState.READY

    /**
     * Set of normalized ISO-639 / BCP-47 language codes supported by this engine instance.
     */
    val supportedLanguages: Set<String>

    /**
     * Checks if the given language code is supported by this engine instance.
     *
     * @param language The language tag to check (e.g., "en", "en-US").
     * @return True if supported, false otherwise.
     */
    fun isLanguageSupported(language: String): Boolean

    /**
     * Initializes native models and resources.
     *
     * If the engine is already in [TtsEngineState.READY], this is a safe no-op.
     * If the engine is in [TtsEngineState.RELEASED], this throws [IllegalStateException].
     *
     * @throws IllegalStateException if the engine is already released.
     * @throws Exception if native initialization fails.
     */
    fun initialize()

    /**
     * Synthesizes text into raw PCM audio samples for a supported language.
     *
     * Calls are serialized to guarantee thread safety against native JNI sessions.
     *
     * @param text The plain text to synthesize. Must not be blank.
     * @param language The target language code (e.g., "en", "en-US").
     * @param speed Playback speed multiplier (default 1.0f). Must be a positive finite number.
     * @return [TtsAudio] containing raw PCM float samples and audio metadata.
     * @throws IllegalArgumentException if text is blank or speed is non-positive or non-finite.
     * @throws UnsupportedTtsLanguageException if language is unsupported.
     * @throws TtsEngineNotInitializedException if engine is not in [TtsEngineState.READY].
     */
    suspend fun synthesize(
        text: String,
        language: String,
        speed: Float = 1.0f
    ): TtsAudio

    /**
     * Releases all native resources associated with this engine.
     *
     * This operation is idempotent and thread-safe. If synthesis is actively in flight,
     * release waits for active invocation to complete, preventing native use-after-free,
     * and transitions the engine to [TtsEngineState.RELEASED].
     */
    fun release()
}
