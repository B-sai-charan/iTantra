package org.itantra.core.tts

import android.content.Context
import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * Configuration for the Sherpa-ONNX VITS Offline TTS model.
 *
 * @property modelPath Path to the VITS ONNX model file (e.g. "en_US-lessac-low.onnx" or absolute filesystem path).
 * @property tokensPath Path to the tokens vocabulary file (e.g. "tokens.txt").
 * @property dataDir Path to espeak-ng-data directory.
 * @property dictDir Optional dictionary directory (unused for Piper VITS).
 * @property lexiconPath Optional lexicon file (unused for Piper VITS).
 * @property numThreads Number of CPU computation threads for ONNX inference.
 * @property noiseScale VITS noise scale hyperparameter (default 0.667 for Piper).
 * @property noiseScaleW VITS phoneme duration noise scale hyperparameter (default 0.8 for Piper).
 * @property lengthScale Speech speed inverse scaling factor (default 1.0 for normal rate).
 * @property silenceScale Silence duration scaling factor (default 0.2).
 * @property maxNumSentences Max sentences processed in batch (default 1).
 * @property provider Hardware execution provider ("cpu").
 * @property debug Whether to enable native Sherpa verbose debug logging.
 */
data class SherpaOfflineTtsConfig(
    val modelPath: String,
    val tokensPath: String,
    val dataDir: String = "",
    val dictDir: String = "",
    val lexiconPath: String = "",
    val numThreads: Int = 1,
    val noiseScale: Float = 0.667f,
    val noiseScaleW: Float = 0.8f,
    val lengthScale: Float = 1.0f,
    val silenceScale: Float = 0.2f,
    val maxNumSentences: Int = 1,
    val provider: String = "cpu",
    val debug: Boolean = false
)

/**
 * Internal delegate interface wrapping the Sherpa native TTS session.
 * Allows decoupling of JVM unit tests from native JNI shared libraries.
 */
internal interface OfflineTtsNativeSession {
    fun generate(text: String, sid: Int, speed: Float): TtsAudio
    fun sampleRate(): Int
    fun release()
}

/**
 * Internal factory interface to construct the native TTS session.
 */
internal fun interface OfflineTtsNativeFactory {
    fun create(config: SherpaOfflineTtsConfig, assetManager: AssetManager?): OfflineTtsNativeSession
}

/**
 * Offline Text-to-Speech engine implementation backed by Sherpa-ONNX VITS / Piper neural models.
 *
 * Provides a hardened lifecycle state machine (UNINITIALIZED -> READY -> RELEASED / FAILED),
 * serialized native synthesis access via coroutine Mutex, and thread-safe idempotent release.
 */
/**
 * Offline Text-to-Speech engine implementation backed by Sherpa-ONNX VITS / Piper neural models.
 *
 * Supports multi-language routing across English (e.g. `vits-piper-en_US-lessac-low`, 16 kHz)
 * and Hindi (e.g. `vits-piper-hi_IN-priyamvada-medium`, 22.05 kHz) models with serialized native
 * synthesis access via coroutine Mutex, per-language lazy session lifecycle, and thread-safe idempotent release.
 */
class SherpaOfflineTtsEngine internal constructor(
    private val englishConfig: SherpaOfflineTtsConfig = defaultEnglishConfig(),
    private val hindiConfig: SherpaOfflineTtsConfig? = defaultHindiConfig(),
    private val nativeFactory: OfflineTtsNativeFactory = DefaultNativeFactory,
    private val assetManager: AssetManager? = null,
    autoInitialize: Boolean = true
) : TtsEngine {

    /**
     * Backwards-compatible constructor for a single model configuration.
     */
    internal constructor(
        config: SherpaOfflineTtsConfig,
        nativeFactory: OfflineTtsNativeFactory = DefaultNativeFactory,
        assetManager: AssetManager? = null,
        autoInitialize: Boolean = true
    ) : this(
        englishConfig = if (isHindiConfig(config)) defaultEnglishConfig() else config,
        hindiConfig = if (isHindiConfig(config)) config else defaultHindiConfig(),
        nativeFactory = nativeFactory,
        assetManager = assetManager,
        autoInitialize = autoInitialize
    )

    /**
     * Secondary constructor for Android Context with dual English and Hindi model configurations.
     */
    constructor(
        context: Context,
        englishConfig: SherpaOfflineTtsConfig = defaultEnglishConfig(),
        hindiConfig: SherpaOfflineTtsConfig? = defaultHindiConfig(),
        autoInitialize: Boolean = false
    ) : this(
        englishConfig = prepareConfigForContext(context, englishConfig),
        hindiConfig = hindiConfig?.let { prepareConfigForContext(context, it) },
        nativeFactory = DefaultNativeFactory,
        assetManager = context.assets,
        autoInitialize = autoInitialize
    )

    /**
     * Backwards-compatible constructor for Android Context and single model configuration.
     */
    constructor(
        context: Context,
        config: SherpaOfflineTtsConfig,
        autoInitialize: Boolean = false
    ) : this(
        context = context,
        englishConfig = if (isHindiConfig(config)) defaultEnglishConfig() else config,
        hindiConfig = if (isHindiConfig(config)) config else defaultHindiConfig(),
        autoInitialize = autoInitialize
    )

    /**
     * Secondary constructor for AssetManager with dual English and Hindi model configurations.
     */
    constructor(
        assetManager: AssetManager?,
        englishConfig: SherpaOfflineTtsConfig = defaultEnglishConfig(),
        hindiConfig: SherpaOfflineTtsConfig? = defaultHindiConfig(),
        autoInitialize: Boolean = true
    ) : this(
        englishConfig = englishConfig,
        hindiConfig = hindiConfig,
        nativeFactory = DefaultNativeFactory,
        assetManager = assetManager,
        autoInitialize = autoInitialize
    )

    /**
     * Backwards-compatible constructor for AssetManager and single model configuration.
     */
    constructor(
        assetManager: AssetManager?,
        config: SherpaOfflineTtsConfig,
        autoInitialize: Boolean = true
    ) : this(
        assetManager = assetManager,
        englishConfig = if (isHindiConfig(config)) defaultEnglishConfig() else config,
        hindiConfig = if (isHindiConfig(config)) config else defaultHindiConfig(),
        autoInitialize = autoInitialize
    )

    private val lifecycleLock = Any()
    private val synthesisMutex = Mutex()
    private val activeSynthesisCount = AtomicInteger(0)

    @Volatile
    private var _state: TtsEngineState = TtsEngineState.UNINITIALIZED

    private var englishSession: OfflineTtsNativeSession? = null
    private var hindiSession: OfflineTtsNativeSession? = null

    override val state: TtsEngineState
        get() = _state

    override val supportedLanguages: Set<String>
        get() = buildSet {
            addAll(SUPPORTED_ENGLISH_CODES)
            if (hindiConfig != null) {
                addAll(SUPPORTED_HINDI_CODES)
            }
        }

    init {
        require(englishConfig.modelPath.isNotBlank()) { "englishConfig.modelPath cannot be blank" }
        require(englishConfig.tokensPath.isNotBlank()) { "englishConfig.tokensPath cannot be blank" }
        hindiConfig?.let {
            require(it.modelPath.isNotBlank()) { "hindiConfig.modelPath cannot be blank" }
            require(it.tokensPath.isNotBlank()) { "hindiConfig.tokensPath cannot be blank" }
        }
        if (autoInitialize) {
            initialize()
        }
    }

    override fun initialize() {
        synchronized(lifecycleLock) {
            when (_state) {
                TtsEngineState.READY -> return // Idempotent no-op
                TtsEngineState.RELEASED -> throw IllegalStateException("Cannot initialize a released TTS engine")
                TtsEngineState.INITIALIZING -> return
                TtsEngineState.UNINITIALIZED, TtsEngineState.FAILED -> {
                    _state = TtsEngineState.INITIALIZING
                    try {
                        // Eagerly initialize English primary session if autoInitialize is requested
                        if (englishSession == null) {
                            englishSession = nativeFactory.create(englishConfig, assetManager)
                        }
                        _state = TtsEngineState.READY
                    } catch (t: Throwable) {
                        _state = TtsEngineState.FAILED
                        englishSession = null
                        hindiSession = null
                        throw t
                    }
                }
            }
        }
    }

    override fun isLanguageSupported(language: String): Boolean {
        if (language.isBlank()) return false
        val normalized = normalizeLanguageTag(language)
        if (isEnglishLanguageTag(normalized)) return true
        if (hindiConfig != null && isHindiLanguageTag(normalized)) return true
        return false
    }

    override suspend fun synthesize(
        text: String,
        language: String,
        speed: Float
    ): TtsAudio {
        // Fast-path validations before acquiring coroutine mutex
        if (text.isBlank()) {
            throw IllegalArgumentException("Text cannot be blank for TTS synthesis")
        }
        val normalizedLang = normalizeLanguageTag(language)
        if (!isLanguageSupported(normalizedLang)) {
            throw UnsupportedTtsLanguageException(
                language = language,
                message = "Language '$language' is not supported. Supported languages: $supportedLanguages"
            )
        }
        if (speed <= 0.0f || speed.isNaN() || speed.isInfinite()) {
            throw IllegalArgumentException("Speed multiplier must be a positive finite number, got: $speed")
        }

        // Fast-path state check
        synchronized(lifecycleLock) {
            if (_state != TtsEngineState.READY) {
                throw TtsEngineNotInitializedException(
                    "Cannot synthesize: engine is in state '$_state' (expected 'READY')"
                )
            }
        }

        // Determine target language model group
        val isHindi = isHindiLanguageTag(normalizedLang)

        // Serialize access to the underlying native session across coroutines
        return synthesisMutex.withLock {
            val activeSession: OfflineTtsNativeSession
            synchronized(lifecycleLock) {
                if (_state != TtsEngineState.READY) {
                    throw TtsEngineNotInitializedException(
                        "Cannot synthesize: engine is in state '$_state' (expected 'READY')"
                    )
                }

                try {
                    activeSession = if (isHindi) {
                        hindiSession ?: run {
                            val cfg = hindiConfig ?: throw UnsupportedTtsLanguageException(
                                language = language,
                                message = "Hindi TTS configuration is not provided"
                            )
                            nativeFactory.create(cfg, assetManager).also { hindiSession = it }
                        }
                    } else {
                        englishSession ?: run {
                            nativeFactory.create(englishConfig, assetManager).also { englishSession = it }
                        }
                    }
                } catch (t: Throwable) {
                    _state = TtsEngineState.FAILED
                    throw t
                }

                activeSynthesisCount.incrementAndGet()
            }

            try {
                withContext(Dispatchers.Default) {
                    // sid = 0 for single-speaker VITS models (English Lessac / Hindi Priyamvada)
                    activeSession.generate(text = text, sid = 0, speed = speed)
                }
            } finally {
                synchronized(lifecycleLock) {
                    activeSynthesisCount.decrementAndGet()
                    (lifecycleLock as java.lang.Object).notifyAll()
                }
            }
        }
    }

    override fun release() {
        val engSessionToRelease: OfflineTtsNativeSession?
        val hinSessionToRelease: OfflineTtsNativeSession?
        synchronized(lifecycleLock) {
            if (_state == TtsEngineState.RELEASED) {
                return // Idempotent no-op
            }
            _state = TtsEngineState.RELEASED

            // Wait for active in-flight JNI synthesis to complete before destroying native sessions
            while (activeSynthesisCount.get() > 0) {
                try {
                    (lifecycleLock as java.lang.Object).wait(50)
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            engSessionToRelease = englishSession
            hinSessionToRelease = hindiSession
            englishSession = null
            hindiSession = null
        }
        engSessionToRelease?.release()
        hinSessionToRelease?.release()
    }

    private fun normalizeLanguageTag(tag: String): String {
        return tag.trim()
    }

    companion object {
        private val SUPPORTED_ENGLISH_CODES = setOf("en", "en-US", "en-us", "en_US")
        private val SUPPORTED_HINDI_CODES = setOf("hi", "hi-IN", "hi_IN")

        fun isEnglishLanguageTag(tag: String): Boolean {
            val lower = tag.trim().lowercase()
            return lower == "en" || lower.startsWith("en-") || lower.startsWith("en_")
        }

        fun isHindiLanguageTag(tag: String): Boolean {
            val lower = tag.trim().lowercase()
            return lower == "hi" || lower.startsWith("hi-") || lower.startsWith("hi_")
        }

        private fun isHindiConfig(config: SherpaOfflineTtsConfig): Boolean {
            val path = config.modelPath.lowercase()
            return path.contains("hi_in") || path.contains("rohan")
        }

        fun defaultEnglishConfig(): SherpaOfflineTtsConfig = SherpaOfflineTtsConfig(
            modelPath = "vits-piper-en_US-lessac-low/en_US-lessac-low.onnx",
            tokensPath = "vits-piper-en_US-lessac-low/tokens.txt",
            dataDir = "vits-piper-en_US-lessac-low/espeak-ng-data"
        )

        fun defaultHindiConfig(): SherpaOfflineTtsConfig = SherpaOfflineTtsConfig(
            modelPath = "models/hi/vits-piper-hi_IN-rohan-medium/hi_IN-rohan-medium.onnx",
            tokensPath = "models/hi/vits-piper-hi_IN-rohan-medium/tokens.txt",
            dataDir = "models/hi/vits-piper-hi_IN-rohan-medium/espeak-ng-data"
        )

        /**
         * Extracts relative asset directories to disk if needed and returns the resolved config.
         */
        fun prepareConfigForContext(context: Context, config: SherpaOfflineTtsConfig): SherpaOfflineTtsConfig {
            if (config.dataDir.isNotEmpty() && !java.io.File(config.dataDir).isAbsolute) {
                val targetDir = java.io.File(context.filesDir, config.dataDir)
                copyAssetFolder(context.assets, config.dataDir, targetDir)
                return config.copy(dataDir = targetDir.absolutePath)
            }
            return config
        }

        private fun copyAssetFolder(assets: AssetManager, fromPath: String, toDir: java.io.File) {
            if (!toDir.exists()) {
                toDir.mkdirs()
            }
            val files = assets.list(fromPath) ?: return
            for (file in files) {
                val fromSub = if (fromPath.isEmpty()) file else "$fromPath/$file"
                val toSub = java.io.File(toDir, file)
                val subFiles = assets.list(fromSub)
                if (subFiles != null && subFiles.isNotEmpty()) {
                    copyAssetFolder(assets, fromSub, toSub)
                } else {
                    if (!toSub.exists() || toSub.length() == 0L) {
                        try {
                            assets.open(fromSub).use { input ->
                                toSub.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        /**
         * Default factory creating the real Sherpa native [OfflineTts] instance.
         */
        internal val DefaultNativeFactory = OfflineTtsNativeFactory { cfg, assets ->
            val vitsConfig = OfflineTtsVitsModelConfig(
                model = cfg.modelPath,
                lexicon = cfg.lexiconPath,
                tokens = cfg.tokensPath,
                dataDir = cfg.dataDir,
                dictDir = cfg.dictDir,
                noiseScale = cfg.noiseScale,
                noiseScaleW = cfg.noiseScaleW,
                lengthScale = cfg.lengthScale
            )

            val modelConfig = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = cfg.numThreads,
                debug = cfg.debug,
                provider = cfg.provider
            )

            val ttsConfig = OfflineTtsConfig(
                model = modelConfig,
                ruleFsts = "",
                ruleFars = "",
                maxNumSentences = cfg.maxNumSentences,
                silenceScale = cfg.silenceScale
            )

            val nativeTts = OfflineTts(assetManager = assets, config = ttsConfig)
            DefaultOfflineTtsNativeSession(nativeTts)
        }
    }
}

/**
 * Production implementation of [OfflineTtsNativeSession] calling Sherpa [OfflineTts] JNI bindings.
 */
internal class DefaultOfflineTtsNativeSession(
    private val nativeTts: OfflineTts
) : OfflineTtsNativeSession {

    override fun generate(text: String, sid: Int, speed: Float): TtsAudio {
        val audio: GeneratedAudio = nativeTts.generate(text = text, sid = sid, speed = speed)
        val samples = audio.samples ?: FloatArray(0)
        val rate = if (audio.sampleRate > 0) audio.sampleRate else nativeTts.sampleRate()
        return TtsAudio(
            samples = samples,
            sampleRate = rate,
            channelCount = 1,
            sampleFormat = "PCM_FLOAT"
        )
    }

    override fun sampleRate(): Int {
        return nativeTts.sampleRate()
    }

    override fun release() {
        nativeTts.release()
    }
}
