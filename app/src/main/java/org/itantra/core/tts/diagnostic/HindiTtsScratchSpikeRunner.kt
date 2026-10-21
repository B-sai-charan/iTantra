package org.itantra.core.tts.diagnostic

import android.content.Context
import android.os.Debug
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.itantra.core.tts.TtsAudio
import org.itantra.core.tts.playback.AndroidAudioTrackPlaybackEngine
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * Isolated diagnostic runner for M3 Hindi TTS model feasibility spike.
 *
 * Exercises the staged candidate model (vits-piper-hi_IN-priyamvada-medium)
 * using the existing Sherpa-ONNX JNI native runtime and AndroidAudioTrackPlaybackEngine.
 */
object HindiTtsScratchSpikeRunner {

    private const val TAG = "HINDI_TTS_SPIKE"

    const val PROMPT_1 = "नमस्ते। यह iTantra का हिंदी टेक्स्ट टू स्पीच परीक्षण है।"
    const val PROMPT_2 = "यह एक ऑफलाइन संचार प्रणाली है जो कम बैंडविड्थ वाले नेटवर्क पर काम करती है।"
    const val PROMPT_3 = "आपातकालीन संदेश को हिंदी में स्पष्ट और विश्वसनीय रूप से सुनाया जाना चाहिए।"

    data class MemorySnapshot(
        val totalPssKb: Long = 0L,
        val nativeHeapAllocatedMb: Double = 0.0,
        val nativeHeapSizeMb: Double = 0.0,
        val javaHeapAllocatedMb: Double = 0.0
    )

    data class PromptResult(
        val promptIndex: Int,
        val text: String,
        val synthesisSuccess: Boolean,
        val sampleCount: Int,
        val sampleRate: Int,
        val channelCount: Int,
        val audioDurationSeconds: Float,
        val synthesisTimeMs: Long,
        val rtf: Double,
        val samplesFinite: Boolean,
        val minSample: Float,
        val maxSample: Float,
        val playbackSuccess: Boolean,
        val playbackDurationMs: Long,
        val memoryAfterSynthesis: MemorySnapshot,
        val errorMessage: String? = null
    )

    data class SpikeReport(
        val deviceModel: String,
        val androidVersion: String,
        val modelPath: String,
        val initSuccess: Boolean,
        val initLatencyMs: Long,
        val memoryBefore: MemorySnapshot,
        val memoryAfterInit: MemorySnapshot,
        val promptResults: List<PromptResult>,
        val allPassed: Boolean,
        val errorMessage: String? = null
    )

    private fun captureMemory(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val javaHeapAllocated = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
        val nativeAllocated = Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)
        val nativeSize = Debug.getNativeHeapSize() / (1024.0 * 1024.0)
        val pss = Debug.getPss()
        return MemorySnapshot(
            totalPssKb = pss,
            nativeHeapAllocatedMb = nativeAllocated,
            nativeHeapSizeMb = nativeSize,
            javaHeapAllocatedMb = javaHeapAllocated
        )
    }

    /**
     * Executes the isolated Hindi TTS spike on physical Android device.
     */
    fun runSpike(
        context: Context,
        modelDir: String = "/data/local/tmp/vits-piper-hi_IN-priyamvada-medium",
        enablePlayback: Boolean = true
    ): SpikeReport {
        Log.i(TAG, "############################################################")
        Log.i(TAG, "STARTING ISOLATED HINDI TTS SCRATCH SPIKE (PRIYAMVADA MEDIUM)")
        Log.i(TAG, "Model Directory: $modelDir")
        Log.i(TAG, "############################################################")

        val memBefore = captureMemory()
        Log.i(TAG, "[MEM-BEFORE] PSS: ${memBefore.totalPssKb}KB, NativeAlloc: ${"%.2f".format(memBefore.nativeHeapAllocatedMb)}MB")

        val modelFile = File(modelDir, "hi_IN-priyamvada-medium.onnx")
        val tokensFile = File(modelDir, "tokens.txt")
        val espeakDir = File(modelDir, "espeak-ng-data")

        if (!modelFile.exists()) {
            val err = "Model file not found at ${modelFile.absolutePath}"
            Log.e(TAG, err)
            return SpikeReport(
                deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                modelPath = modelDir,
                initSuccess = false,
                initLatencyMs = 0L,
                memoryBefore = memBefore,
                memoryAfterInit = memBefore,
                promptResults = emptyList(),
                allPassed = false,
                errorMessage = err
            )
        }

        var nativeTts: OfflineTts? = null
        var initTimeMs = 0L

        try {
            initTimeMs = measureTimeMillis {
                val vitsConfig = OfflineTtsVitsModelConfig(
                    model = modelFile.absolutePath,
                    tokens = tokensFile.absolutePath,
                    dataDir = espeakDir.absolutePath,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )

                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )

                val ttsConfig = OfflineTtsConfig(
                    model = modelConfig,
                    maxNumSentences = 1,
                    silenceScale = 0.2f
                )

                nativeTts = OfflineTts(assetManager = null, config = ttsConfig)
            }
            Log.i(TAG, "[INIT-SUCCESS] Native OfflineTts initialized in ${initTimeMs}ms (SampleRate=${nativeTts?.sampleRate()}Hz, Speakers=${nativeTts?.numSpeakers()})")
        } catch (e: Throwable) {
            val err = "Initialization failed: ${e.message}"
            Log.e(TAG, err, e)
            return SpikeReport(
                deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
                modelPath = modelDir,
                initSuccess = false,
                initLatencyMs = initTimeMs,
                memoryBefore = memBefore,
                memoryAfterInit = captureMemory(),
                promptResults = emptyList(),
                allPassed = false,
                errorMessage = err
            )
        }

        val memAfterInit = captureMemory()
        Log.i(TAG, "[MEM-AFTER-INIT] PSS: ${memAfterInit.totalPssKb}KB, NativeAlloc: ${"%.2f".format(memAfterInit.nativeHeapAllocatedMb)}MB")

        val prompts = listOf(
            Pair(1, PROMPT_1),
            Pair(2, PROMPT_2),
            Pair(3, PROMPT_3)
        )

        val results = mutableListOf<PromptResult>()
        val playbackEngine = if (enablePlayback) AndroidAudioTrackPlaybackEngine() else null

        try {
            val tts = nativeTts!!

            for ((idx, promptText) in prompts) {
                Log.i(TAG, "------------------------------------------------------------")
                Log.i(TAG, "[PROMPT $idx START] Text: \"$promptText\"")

                var synthTimeMs = 0L
                var genAudio: GeneratedAudio? = null
                var synthError: String? = null

                try {
                    synthTimeMs = measureTimeMillis {
                        genAudio = tts.generate(text = promptText, sid = 0, speed = 1.0f)
                    }
                } catch (t: Throwable) {
                    synthError = t.message
                    Log.e(TAG, "[PROMPT $idx SYNTH-FAIL] Error: $synthError", t)
                }

                val memAfterSynth = captureMemory()

                if (genAudio == null || genAudio?.samples == null) {
                    results.add(
                        PromptResult(
                            promptIndex = idx,
                            text = promptText,
                            synthesisSuccess = false,
                            sampleCount = 0,
                            sampleRate = 0,
                            channelCount = 1,
                            audioDurationSeconds = 0.0f,
                            synthesisTimeMs = synthTimeMs,
                            rtf = 0.0,
                            samplesFinite = false,
                            minSample = 0.0f,
                            maxSample = 0.0f,
                            playbackSuccess = false,
                            playbackDurationMs = 0L,
                            memoryAfterSynthesis = memAfterSynth,
                            errorMessage = synthError ?: "Generated samples null"
                        )
                    )
                    continue
                }

                val samples = genAudio!!.samples!!
                val sampleRate = if (genAudio!!.sampleRate > 0) genAudio!!.sampleRate else tts.sampleRate()
                val durationSec = if (sampleRate > 0) samples.size.toFloat() / sampleRate else 0.0f
                val durationMs = (durationSec * 1000.0).toLong()
                val rtf = if (durationMs > 0) synthTimeMs.toDouble() / durationMs.toDouble() else 0.0

                // Validate audio samples
                var allFinite = true
                var minVal = Float.MAX_VALUE
                var maxVal = -Float.MAX_VALUE

                for (s in samples) {
                    if (s.isNaN() || s.isInfinite()) {
                        allFinite = false
                    }
                    if (s < minVal) minVal = s
                    if (s > maxVal) maxVal = s
                }

                Log.i(TAG, "[PROMPT $idx SYNTH-SUCCESS] Samples: ${samples.size}, Rate: ${sampleRate}Hz, Duration: ${"%.3f".format(durationSec)}s, Time: ${synthTimeMs}ms, RTF: ${"%.4f".format(rtf)}")
                Log.i(TAG, "[PROMPT $idx AUDIO-VALIDATION] Finite: $allFinite, Min: ${"%.4f".format(minVal)}, Max: ${"%.4f".format(maxVal)}")

                // Playback test via AudioTrack
                var playbackSuccess = false
                var playDurationMs = 0L
                var playError: String? = null

                if (enablePlayback && playbackEngine != null) {
                    val ttsAudio = TtsAudio(
                        samples = samples,
                        sampleRate = sampleRate,
                        channelCount = 1,
                        sampleFormat = "PCM_FLOAT"
                    )

                    try {
                        Log.i(TAG, "[PROMPT $idx PLAYBACK-START] Streaming to AudioTrack ($sampleRate Hz)...")
                        playDurationMs = measureTimeMillis {
                            runBlocking {
                                playbackEngine.play(ttsAudio)
                            }
                        }
                        playbackSuccess = true
                        Log.i(TAG, "[PROMPT $idx PLAYBACK-COMPLETE] Finished in ${playDurationMs}ms")
                    } catch (t: Throwable) {
                        playError = t.message
                        Log.e(TAG, "[PROMPT $idx PLAYBACK-FAIL] Error: $playError", t)
                    }
                }

                results.add(
                    PromptResult(
                        promptIndex = idx,
                        text = promptText,
                        synthesisSuccess = true,
                        sampleCount = samples.size,
                        sampleRate = sampleRate,
                        channelCount = 1,
                        audioDurationSeconds = durationSec,
                        synthesisTimeMs = synthTimeMs,
                        rtf = rtf,
                        samplesFinite = allFinite,
                        minSample = minVal,
                        maxSample = maxVal,
                        playbackSuccess = playbackSuccess,
                        playbackDurationMs = playDurationMs,
                        memoryAfterSynthesis = memAfterSynth,
                        errorMessage = playError
                    )
                )

                Thread.sleep(500) // Brief cooling interval
            }
        } finally {
            try {
                playbackEngine?.release()
            } catch (_: Exception) {}
            try {
                nativeTts?.release()
            } catch (_: Exception) {}
        }

        val allOk = results.isNotEmpty() && results.all { it.synthesisSuccess && it.samplesFinite && (!enablePlayback || it.playbackSuccess) }

        Log.i(TAG, "############################################################")
        Log.i(TAG, "HINDI TTS SCRATCH SPIKE COMPLETE — ALL PASSED: $allOk")
        Log.i(TAG, "############################################################")

        return SpikeReport(
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
            modelPath = modelDir,
            initSuccess = true,
            initLatencyMs = initTimeMs,
            memoryBefore = memBefore,
            memoryAfterInit = memAfterInit,
            promptResults = results,
            allPassed = allOk
        )
    }
}
