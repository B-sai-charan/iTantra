package org.itantra.core.vad

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * High-performance, offline [VadDetector] implementation backed by the Silero VAD v5 ONNX neural network.
 *
 * Silero VAD v5 Architecture:
 * - Input Tensor (`input`): Shape [1, 576] float32 (64 rolling context samples + 512 new normalized audio samples)
 * - State Tensor (`state`): Shape [2, 1, 128] float32 (recurrent LSTM hidden/cell state)
 * - Sample Rate Tensor (`sr`): Shape [1] int64 (value: 16000L or 8000L)
 * - Output Tensor (`output`): Speech probability [0.0, 1.0]
 * - State Output Tensor (`stateN`): Updated state [2, 1, 128] float32 fed into the next step
 */
class SileroVadEngine private constructor(
    private val ortEnvironment: OrtEnvironment,
    private val ortSession: OrtSession,
    override val config: VadConfig = VadConfig()
) : VadDetector {

    companion object {
        private const val TAG = "iTantra-SileroVAD"

        /**
         * Creates a [SileroVadEngine] instance by loading the ONNX model from Android assets.
         */
        fun fromAsset(context: Context, config: VadConfig = VadConfig()): SileroVadEngine {
            val assetManager = context.assets
            val modelBytes = assetManager.open(config.modelAssetPath).use { it.readBytes() }
            return fromBytes(modelBytes, config)
        }

        /**
         * Creates a [SileroVadEngine] instance from a byte array (useful for unit tests and embedded assets).
         */
        fun fromBytes(modelBytes: ByteArray, config: VadConfig = VadConfig()): SileroVadEngine {
            val env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(1)
                setInterOpNumThreads(1)
            }
            val session = env.createSession(modelBytes, sessionOptions)
            return SileroVadEngine(env, session, config)
        }

        /**
         * Creates a [SileroVadEngine] from a local filesystem model file.
         */
        fun fromFile(modelFile: File, config: VadConfig = VadConfig()): SileroVadEngine {
            val env = OrtEnvironment.getEnvironment()
            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(1)
                setInterOpNumThreads(1)
            }
            val session = env.createSession(modelFile.absolutePath, sessionOptions)
            return SileroVadEngine(env, session, config)
        }
    }

    private val lock = Any()

    @Volatile
    private var closed = false

    override val isInitialized: Boolean
        get() = !closed

    // Rolling context buffer (64 float samples initialized to 0)
    private val contextBuffer = FloatArray(VadConfig.SILERO_V5_CONTEXT_SIZE)

    // Recurrent state tensor (2 x 1 x 128 = 256 floats initialized to 0)
    private var stateBuffer = Array(VadConfig.STATE_DIM_0) {
        Array(VadConfig.STATE_DIM_1) {
            FloatArray(VadConfig.STATE_DIM_2)
        }
    }

    // Combined input buffer (64 context + 512 audio = 576 floats)
    private val inputTensorBuffer = FloatArray(VadConfig.SILERO_V5_INPUT_SIZE)

    // Speech segmentation state tracking
    private var isCurrentlySpeaking = false
    private var currentSpeechDurationMs = 0L
    private var currentSilenceDurationMs = 0L

    // Telemetry
    private var inferenceCount = 0L
    private var totalInferenceTimeNanos = 0L

    override val totalInferences: Long
        get() = inferenceCount

    override val averageInferenceLatencyMs: Double
        get() = if (inferenceCount > 0) (totalInferenceTimeNanos / inferenceCount) / 1_000_000.0 else 0.0

    override fun process(samples: ShortArray): VadResult {
        synchronized(lock) {
            check(!closed) { "SileroVadEngine is closed." }
            require(samples.size == VadConfig.SILERO_V5_FRAME_SIZE) {
                "SileroVadEngine requires exactly ${VadConfig.SILERO_V5_FRAME_SIZE} samples, received: ${samples.size}"
            }

            val startNano = System.nanoTime()

            // 1. Populate the 576-sample input tensor:
            // - First 64 samples: rolling context from previous frame
            System.arraycopy(contextBuffer, 0, inputTensorBuffer, 0, VadConfig.SILERO_V5_CONTEXT_SIZE)

            // - Next 512 samples: normalize PCM16 to float32 [-1.0, 1.0]
            for (i in 0 until VadConfig.SILERO_V5_FRAME_SIZE) {
                inputTensorBuffer[VadConfig.SILERO_V5_CONTEXT_SIZE + i] = samples[i] / 32768.0f
            }

            // - Update rolling context for next inference (last 64 samples of current 512 frame)
            val contextStartIndex = VadConfig.SILERO_V5_INPUT_SIZE - VadConfig.SILERO_V5_CONTEXT_SIZE
            System.arraycopy(inputTensorBuffer, contextStartIndex, contextBuffer, 0, VadConfig.SILERO_V5_CONTEXT_SIZE)

            // 2. Prepare ONNX Input Tensors
            val input2D = arrayOf(inputTensorBuffer)
            val inputTensor = OnnxTensor.createTensor(ortEnvironment, input2D)
            val stateTensor = OnnxTensor.createTensor(ortEnvironment, stateBuffer)
            val srTensor = OnnxTensor.createTensor(ortEnvironment, longArrayOf(config.sampleRate.toLong()))

            val inputs = mapOf(
                "input" to inputTensor,
                "state" to stateTensor,
                "sr" to srTensor
            )

            var probability = 0.0f

            // 3. Execute ONNX Neural Network Inference
            try {
                val results = ortSession.run(inputs)
                try {
                    // Extract probability output
                    val outputTensor = results.get(0) as OnnxTensor
                    val outputValue = outputTensor.value

                    probability = when (outputValue) {
                        is Array<*> -> {
                            when (val first = outputValue[0]) {
                                is FloatArray -> first[0]
                                is Float -> first
                                is Array<*> -> (first[0] as? FloatArray)?.get(0) ?: 0.0f
                                else -> 0.0f
                            }
                        }
                        is FloatArray -> outputValue[0]
                        is Float -> outputValue
                        else -> 0.0f
                    }

                    // Extract and update stateN output [2, 1, 128]
                    if (results.size() > 1) {
                        val stateNTensor = results.get(1) as OnnxTensor
                        @Suppress("UNCHECKED_CAST")
                        val nextState = stateNTensor.value as? Array<Array<FloatArray>>
                        if (nextState != null) {
                            stateBuffer = nextState
                        }
                    }
                } finally {
                    results.close()
                }
            } finally {
                inputTensor.close()
                stateTensor.close()
                srTensor.close()
            }

            val elapsedNano = System.nanoTime() - startNano
            inferenceCount++
            totalInferenceTimeNanos += elapsedNano

            // 4. Calculate Speech State Transitions (Frame duration = 32 ms at 16 kHz)
            val frameDurationMs = (VadConfig.SILERO_V5_FRAME_SIZE * 1000L) / config.sampleRate
            val isSpeech = probability >= config.threshold

            var isSpeechStart = false
            var isSpeechEnd = false

            if (isSpeech) {
                currentSpeechDurationMs += frameDurationMs
                currentSilenceDurationMs = 0L

                if (!isCurrentlySpeaking && currentSpeechDurationMs >= config.minSpeechDurationMs) {
                    isCurrentlySpeaking = true
                    isSpeechStart = true
                }
            } else {
                currentSilenceDurationMs += frameDurationMs

                if (isCurrentlySpeaking && currentSilenceDurationMs >= config.minSilenceDurationMs) {
                    isCurrentlySpeaking = false
                    isSpeechEnd = true
                    currentSpeechDurationMs = 0L
                }
            }

            return VadResult(
                isSpeech = isSpeech,
                probability = probability,
                speechDurationMs = currentSpeechDurationMs,
                silenceDurationMs = currentSilenceDurationMs,
                isSpeechStart = isSpeechStart,
                isSpeechEnd = isSpeechEnd
            )
        }
    }

    override fun reset() {
        synchronized(lock) {
            contextBuffer.fill(0.0f)
            for (i in 0 until VadConfig.STATE_DIM_0) {
                for (j in 0 until VadConfig.STATE_DIM_1) {
                    stateBuffer[i][j].fill(0.0f)
                }
            }
            isCurrentlySpeaking = false
            currentSpeechDurationMs = 0L
            currentSilenceDurationMs = 0L
            Log.d(TAG, "Silero VAD recurrent state reset to zeros.")
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            try {
                ortSession.close()
            } catch (ex: Exception) {
                Log.w(TAG, "Exception closing OrtSession: ${ex.message}")
            }
            try {
                ortEnvironment.close()
            } catch (ex: Exception) {
                Log.w(TAG, "Exception closing OrtEnvironment: ${ex.message}")
            }
            Log.i(TAG, "Silero VAD engine closed. Total inferences=$inferenceCount, AvgLatency=${"%.2f".format(averageInferenceLatencyMs)}ms")
        }
    }
}
