package org.itantra.core.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/**
 * Development-only smoke test utility for Sherpa Whisper-Tiny ASR.
 */
object AsrSmokeTestRunner {

    private const val TAG = "AsrSmokeTest"

    data class SmokeTestRunReport(
        val runIndex: Int,
        val transcript: String,
        val language: String,
        val audioDurationMs: Long,
        val inferenceTimeMs: Long,
        val rtf: Double,
        val confidence: Float?
    )

    data class SmokeTestReport(
        val nativeLibraryLoaded: Boolean,
        val recognizerConstructed: Boolean,
        val encoderLoaded: Boolean,
        val decoderLoaded: Boolean,
        val tokensLoaded: Boolean,
        val runs: List<SmokeTestRunReport>,
        val errorMessage: String? = null
    )

    /**
     * Loads a 16 kHz Mono PCM16 WAV fixture from app assets.
     */
    fun loadWavFromAsset(context: Context, assetName: String = "speech_test.wav"): ShortArray? {
        return try {
            context.assets.open(assetName).use { stream ->
                val bytes = stream.readBytes()
                var dataOffset = 44
                for (i in 0 until bytes.size - 4) {
                    if (bytes[i] == 'd'.code.toByte() &&
                        bytes[i + 1] == 'a'.code.toByte() &&
                        bytes[i + 2] == 't'.code.toByte() &&
                        bytes[i + 3] == 'a'.code.toByte()) {
                        dataOffset = i + 8
                        break
                    }
                }
                val pcmLength = bytes.size - dataOffset
                val shortCount = pcmLength / 2
                val shortBuffer = ByteBuffer.wrap(bytes, dataOffset, pcmLength).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val shorts = ShortArray(shortCount)
                shortBuffer.get(shorts)
                Log.i(TAG, "Loaded WAV fixture '$assetName': $shortCount samples (${(shortCount * 1000L) / 16000} ms)")
                shorts
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not load WAV fixture '$assetName': ${e.message}")
            null
        }
    }

    /**
     * Generates a deterministic 16 kHz mono PCM16 audio fixture.
     * @param durationSeconds Duration in seconds.
     * @param frequencyHz Synthetic carrier frequency (e.g., 440 Hz).
     */
    fun createDeterministicAudioFixture(
        durationSeconds: Double = 3.0,
        frequencyHz: Double = 440.0
    ): ShortArray {
        val sampleRate = 16000
        val totalSamples = (sampleRate * durationSeconds).toInt()
        val buffer = ShortArray(totalSamples)
        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val envelope = sin(2.0 * Math.PI * 2.0 * t)
            val carrier = sin(2.0 * Math.PI * frequencyHz * t)
            val sample = (carrier * envelope * 16384.0).toInt().coerceIn(-32768, 32767)
            buffer[i] = sample.toShort()
        }
        return buffer
    }

    /**
     * Executes the comprehensive isolated ASR smoke test on device.
     */
    fun runFullSmokeTest(
        context: Context,
        iterations: Int = 2
    ): SmokeTestReport {
        Log.i(TAG, "=== STARTING ISOLATED ASR SPEECH TEST ===")

        var nativeLoaded = false
        var recognizerConstructed = false
        var encoderLoaded = false
        var decoderLoaded = false
        var tokensLoaded = false
        val runReports = mutableListOf<SmokeTestRunReport>()

        var engine: SherpaWhisperAsrEngine? = null

        try {
            Log.i(TAG, "[INIT] Loading Sherpa native library and Whisper-Tiny INT8 models...")
            engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")
            nativeLoaded = true
            recognizerConstructed = true
            encoderLoaded = true
            decoderLoaded = true
            tokensLoaded = true
            Log.i(TAG, "[INIT SUCCESS] Sherpa OfflineRecognizer initialized successfully.")

            val speechFixture = loadWavFromAsset(context, "speech_test.wav")
            val audioFixture = speechFixture ?: createDeterministicAudioFixture(durationSeconds = 3.0, frequencyHz = 440.0)
            val audioDurationMs = (audioFixture.size * 1000L) / 16000
            Log.i(TAG, "[FIXTURE] Using audio fixture: ${audioFixture.size} samples ($audioDurationMs ms)")

            for (runIdx in 1..iterations) {
                Log.i(TAG, "--- Executing ASR Run $runIdx / $iterations ---")
                val result = runBlocking {
                    engine.transcribe(audio = audioFixture, sampleRate = 16000, language = "en")
                }
                val rtf = result.inferenceTimeMs.toDouble() / result.audioDurationMs.toDouble()
                Log.i(TAG, "[RESULT Run $runIdx] Transcript: \"${result.text}\"")
                Log.i(TAG, "[RESULT Run $runIdx] Language: ${result.language}")
                Log.i(TAG, "[RESULT Run $runIdx] Audio Duration: ${result.audioDurationMs} ms")
                Log.i(TAG, "[RESULT Run $runIdx] Inference Time: ${result.inferenceTimeMs} ms")
                Log.i(TAG, "[RESULT Run $runIdx] RTF: ${"%.4f".format(rtf)}")
                Log.i(TAG, "[RESULT Run $runIdx] Confidence: ${result.confidence}")

                runReports.add(
                    SmokeTestRunReport(
                        runIndex = runIdx,
                        transcript = result.text,
                        language = result.language,
                        audioDurationMs = result.audioDurationMs,
                        inferenceTimeMs = result.inferenceTimeMs,
                        rtf = rtf,
                        confidence = result.confidence
                    )
                )
            }

            Log.i(TAG, "=== ASR SPEECH TEST COMPLETED SUCCESSFULLY ===")
            return SmokeTestReport(
                nativeLibraryLoaded = nativeLoaded,
                recognizerConstructed = recognizerConstructed,
                encoderLoaded = encoderLoaded,
                decoderLoaded = decoderLoaded,
                tokensLoaded = tokensLoaded,
                runs = runReports
            )
        } catch (t: Throwable) {
            Log.e(TAG, "[ERROR] ASR Smoke Test Failed: ${t.message}", t)
            return SmokeTestReport(
                nativeLibraryLoaded = nativeLoaded,
                recognizerConstructed = recognizerConstructed,
                encoderLoaded = encoderLoaded,
                decoderLoaded = decoderLoaded,
                tokensLoaded = tokensLoaded,
                runs = runReports,
                errorMessage = "${t.javaClass.simpleName}: ${t.message}"
            )
        } finally {
            try {
                engine?.close()
                Log.i(TAG, "[CLEANUP] SherpaWhisperAsrEngine closed.")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing ASR engine", e)
            }
        }
    }

    /**
     * Executes an isolated smoke test using PCM audio samples.
     */
    fun runSmokeTest(
        context: Context,
        pcmAudio: ShortArray,
        targetLanguage: String? = "en"
    ): AsrResult {
        Log.i(TAG, "Initializing SherpaWhisperAsrEngine for smoke test...")
        val engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = targetLanguage ?: "en")
        return try {
            val audioDurationMs = (pcmAudio.size * 1000L) / 16000
            Log.i(TAG, "Transcribing ${pcmAudio.size} PCM samples ($audioDurationMs ms)...")
            val result = runBlocking {
                engine.transcribe(audio = pcmAudio, sampleRate = 16000, language = targetLanguage)
            }
            Log.i(TAG, "=== ASR SMOKE TEST RESULT ===")
            Log.i(TAG, "Transcript: \"${result.text}\"")
            Log.i(TAG, "Language: ${result.language}")
            Log.i(TAG, "Audio Duration: ${result.audioDurationMs} ms")
            Log.i(TAG, "Inference Time: ${result.inferenceTimeMs} ms")
            Log.i(TAG, "Confidence: ${result.confidence}")
            result
        } finally {
            engine.close()
            Log.i(TAG, "SherpaWhisperAsrEngine closed.")
        }
    }

    /**
     * Helper to load raw 16-bit PCM mono from an InputStream.
     */
    fun loadPcmFromStream(inputStream: InputStream): ShortArray {
        val bytes = inputStream.readBytes()
        val shortBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val shorts = ShortArray(shortBuffer.remaining())
        shortBuffer.get(shorts)
        return shorts
    }
}
