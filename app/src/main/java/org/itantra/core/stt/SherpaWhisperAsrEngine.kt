package org.itantra.core.stt

import android.content.Context
import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

/**
 * Offline ASR Engine implementation backed by Sherpa-ONNX Whisper-Tiny INT8.
 *
 * Responsibilities:
 * 1. Loads Whisper-Tiny INT8 encoder/decoder and token mapping via AssetManager or file paths.
 * 2. Preprocesses 16 kHz PCM16 ShortArray to normalized FloatArray [-1.0, +1.0].
 * 3. Feeds complete utterance stream to Sherpa OfflineRecognizer.
 * 4. Executes decoding on a background Dispatcher.
 * 5. Returns [AsrResult] with transcript, detected language, and wall-clock execution metrics.
 */
class SherpaWhisperAsrEngine(
    assetManager: AssetManager? = null,
    encoderPath: String = "models/whisper-tiny/tiny-encoder.int8.onnx",
    decoderPath: String = "models/whisper-tiny/tiny-decoder.int8.onnx",
    tokensPath: String = "models/whisper-tiny/tiny-tokens.txt",
    numThreads: Int = 2,
    private val defaultLanguage: String? = null
) : AsrEngine {

    /**
     * Convenience constructor using Android Context.
     */
    constructor(
        context: Context,
        encoderPath: String = "models/whisper-tiny/tiny-encoder.int8.onnx",
        decoderPath: String = "models/whisper-tiny/tiny-decoder.int8.onnx",
        tokensPath: String = "models/whisper-tiny/tiny-tokens.txt",
        numThreads: Int = 2,
        defaultLanguage: String? = null
    ) : this(
        assetManager = context.assets,
        encoderPath = encoderPath,
        decoderPath = decoderPath,
        tokensPath = tokensPath,
        numThreads = numThreads,
        defaultLanguage = defaultLanguage
    )

    private val recognizer: OfflineRecognizer

    init {
        val featConfig = FeatureConfig(
            sampleRate = 16000,
            featureDim = 80
        )

        val whisperLanguage = if (!defaultLanguage.isNullOrBlank() && !defaultLanguage.equals("auto", ignoreCase = true)) {
            defaultLanguage
        } else {
            ""
        }

        val whisperConfig = OfflineWhisperModelConfig(
            encoder = encoderPath,
            decoder = decoderPath,
            language = whisperLanguage,
            task = "transcribe",
            tailPaddings = -1
        )

        val modelConfig = OfflineModelConfig(
            whisper = whisperConfig,
            tokens = tokensPath,
            numThreads = numThreads,
            debug = false,
            provider = "cpu",
            modelType = "whisper"
        )

        val config = OfflineRecognizerConfig(
            featConfig = featConfig,
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )

        recognizer = OfflineRecognizer(assetManager = assetManager, config = config)
    }

    override suspend fun transcribe(
        audio: ShortArray,
        sampleRate: Int,
        language: String?
    ): AsrResult = withContext(Dispatchers.Default) {
        require(audio.isNotEmpty()) { "Audio buffer cannot be empty for transcription" }
        require(sampleRate == 16000) { "SherpaWhisperAsrEngine expects sampleRate = 16000, got $sampleRate" }

        // Convert ShortArray PCM16 to FloatArray [-1.0, +1.0] using sample / 32768.0f
        val floatSamples = FloatArray(audio.size) { i ->
            audio[i] / 32768.0f
        }

        val audioDurationMs = (audio.size.toLong() * 1000L) / sampleRate

        var transcriptText = ""
        var detectedLang = ""

        val inferenceTimeMs = measureTimeMillis {
            val stream = recognizer.createStream()
            try {
                stream.acceptWaveform(floatSamples, sampleRate)
                recognizer.decode(stream)
                val result = recognizer.getResult(stream)
                transcriptText = result.text.trim()
                detectedLang = result.lang
            } finally {
                stream.release()
            }
        }

        val resultLanguage = resolveResultLanguage(
            requestedLanguage = language,
            defaultLanguage = defaultLanguage,
            detectedLanguage = detectedLang
        )

        android.util.Log.i(
            "SherpaWhisperAsr",
            "[DIAGNOSTIC_TRACE] RequestedLanguage='$language', ConfiguredDefault='$defaultLanguage', RecognizerDetectedLang='$detectedLang', ResolvedLanguage='$resultLanguage', RawRecognizerText=\"$transcriptText\""
        )

        AsrResult(
            text = transcriptText,
            language = resultLanguage,
            confidence = null, // Confidence MUST be null as required
            inferenceTimeMs = inferenceTimeMs,
            audioDurationMs = audioDurationMs
        )
    }

    override fun close() {
        recognizer.release()
    }

    companion object {
        /**
         * Resolves the final language tag for an ASR result adhering to strict forced-language
         * semantics when an explicit target language is supplied, and falling back to
         * model-detected language when in auto-detection mode.
         *
         * @param requestedLanguage Explicit language tag supplied on per-call [transcribe] invocation.
         * @param defaultLanguage Engine-level default/target language supplied during construction.
         * @param detectedLanguage Language tag dynamically detected by Sherpa-ONNX Whisper decoder.
         * @return Resolved two-letter language code ("en", "hi", etc.).
         */
        fun resolveResultLanguage(
            requestedLanguage: String?,
            defaultLanguage: String?,
            detectedLanguage: String?
        ): String {
            val explicitLanguage = when {
                !requestedLanguage.isNullOrBlank() && !requestedLanguage.equals("auto", ignoreCase = true) -> requestedLanguage
                !defaultLanguage.isNullOrBlank() && !defaultLanguage.equals("auto", ignoreCase = true) -> defaultLanguage
                else -> null
            }
            return explicitLanguage ?: if (!detectedLanguage.isNullOrBlank()) detectedLanguage else "en"
        }
    }
}
