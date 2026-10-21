package org.itantra.core.tts.diagnostic

import android.content.Context
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.itantra.core.tts.SherpaOfflineTtsConfig
import org.itantra.core.tts.SherpaOfflineTtsEngine
import org.itantra.core.tts.TtsAudio
import org.itantra.core.tts.playback.AndroidAudioTrackPlaybackEngine
import kotlin.system.measureTimeMillis

/**
 * Diagnostic entry point to execute, profile, and physically validate Sherpa-ONNX TTS synthesis
 * and AndroidAudioTrackPlaybackEngine playback on physical Android hardware (e.g. Lenovo TB-X6C6F).
 *
 * NOTE: This is an isolated developer validation utility that uses production M3 components
 * (SherpaOfflineTtsEngine, AndroidAudioTrackPlaybackEngine) without modifying production architecture.
 */
object TtsDiagnosticRunner {

    private const val TAG = "PHYSICAL_TTS_TEST"

    val DEFAULT_CONFIG = SherpaOfflineTtsConfig(
        modelPath = "vits-piper-en_US-lessac-low/en_US-lessac-low.onnx",
        tokensPath = "vits-piper-en_US-lessac-low/tokens.txt",
        dataDir = "vits-piper-en_US-lessac-low/espeak-ng-data"
    )

    const val TEST_PROMPT_1 = "Hello from iTantra. This is an offline text to speech test."
    const val TEST_PROMPT_2 = "The quick brown fox jumps over the lazy dog."

    data class MemorySnapshot(
        val totalPssKb: Long = 0L,
        val nativeHeapAllocatedMb: Double = 0.0,
        val nativeHeapSizeMb: Double = 0.0,
        val javaHeapAllocatedMb: Double = 0.0
    )

    data class PlaybackRunResult(
        val runIndex: Int,
        val text: String,
        val language: String,
        val initSuccess: Boolean,
        val synthesisSuccess: Boolean,
        val playbackSuccess: Boolean,
        val sampleCount: Int = 0,
        val sampleRate: Int = 0,
        val channelCount: Int = 1,
        val audioDurationSeconds: Float = 0.0f,
        val initLatencyMs: Long = 0L,
        val synthesisLatencyMs: Long = 0L,
        val playbackDurationMs: Long = 0L,
        val rtf: Double = 0.0,
        val errorMessage: String? = null,
        val memoryBefore: MemorySnapshot? = null,
        val memoryAfterInit: MemorySnapshot? = null,
        val memoryAfterSynthesis: MemorySnapshot? = null
    )

    data class PhysicalTtsSuiteReport(
        val deviceModel: String,
        val androidVersion: String,
        val repeatedRuns: List<PlaybackRunResult>,
        val secondSentenceRun: PlaybackRunResult,
        val allPassed: Boolean,
        val errorMessage: String? = null
    )

    fun captureMemory(): MemorySnapshot {
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
     * Executes a single end-to-end TTS synthesis and AudioTrack playback run.
     */
    fun runSinglePlaybackTest(
        context: Context,
        runIndex: Int,
        text: String,
        language: String = "en",
        speed: Float = 1.0f,
        config: SherpaOfflineTtsConfig = DEFAULT_CONFIG
    ): PlaybackRunResult {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "[RUN $runIndex] Starting TTS synthesis & AudioTrack playback")
        Log.i(TAG, "[RUN $runIndex] Prompt: '$text' (lang=$language, speed=$speed)")
        Log.i(TAG, "==================================================")

        val memBefore = captureMemory()
        Log.i(TAG, "[MEM-BEFORE] Native: ${"%.2f".format(memBefore.nativeHeapAllocatedMb)}MB / ${"%.2f".format(memBefore.nativeHeapSizeMb)}MB, PSS: ${memBefore.totalPssKb}KB")

        var engine: SherpaOfflineTtsEngine? = null
        var playbackEngine: AndroidAudioTrackPlaybackEngine? = null
        var initTimeMs = 0L

        try {
            initTimeMs = measureTimeMillis {
                engine = SherpaOfflineTtsEngine(context = context, config = config, autoInitialize = true)
                playbackEngine = AndroidAudioTrackPlaybackEngine()
            }
            Log.i(TAG, "[RUN $runIndex] SherpaOfflineTtsEngine initialized in ${initTimeMs}ms (State: ${engine?.state})")
        } catch (e: Throwable) {
            Log.e(TAG, "[RUN $runIndex] TTS Engine initialization failed", e)
            return PlaybackRunResult(
                runIndex = runIndex,
                text = text,
                language = language,
                initSuccess = false,
                synthesisSuccess = false,
                playbackSuccess = false,
                initLatencyMs = initTimeMs,
                errorMessage = "Initialization error: ${e.message}",
                memoryBefore = memBefore
            )
        }

        val memAfterInit = captureMemory()
        Log.i(TAG, "[MEM-INIT] Native: ${"%.2f".format(memAfterInit.nativeHeapAllocatedMb)}MB / ${"%.2f".format(memAfterInit.nativeHeapSizeMb)}MB, PSS: ${memAfterInit.totalPssKb}KB")

        var synthesisTimeMs = 0L
        val audio: TtsAudio
        try {
            audio = runBlocking {
                val measured: TtsAudio
                synthesisTimeMs = measureTimeMillis {
                    measured = engine!!.synthesize(text = text, language = language, speed = speed)
                }
                measured
            }
            Log.i(TAG, "[RUN $runIndex-SYNTHESIS] Synthesized ${audio.sampleCount} PCM samples in ${synthesisTimeMs}ms (rate=${audio.sampleRate}Hz, duration=${"%.2f".format(audio.durationSeconds)}s)")
        } catch (e: Throwable) {
            Log.e(TAG, "[RUN $runIndex] Synthesis failed", e)
            engine?.release()
            playbackEngine?.release()
            return PlaybackRunResult(
                runIndex = runIndex,
                text = text,
                language = language,
                initSuccess = true,
                synthesisSuccess = false,
                playbackSuccess = false,
                initLatencyMs = initTimeMs,
                synthesisLatencyMs = synthesisTimeMs,
                errorMessage = "Synthesis error: ${e.message}",
                memoryBefore = memBefore,
                memoryAfterInit = memAfterInit
            )
        }

        val memAfterSynth = captureMemory()
        Log.i(TAG, "[MEM-SYNTH] Native: ${"%.2f".format(memAfterSynth.nativeHeapAllocatedMb)}MB / ${"%.2f".format(memAfterSynth.nativeHeapSizeMb)}MB, PSS: ${memAfterSynth.totalPssKb}KB")

        val durationSeconds = audio.durationSeconds
        val audioDurationMs = (durationSeconds * 1000.0).toLong()
        val rtf = if (audioDurationMs > 0) synthesisTimeMs.toDouble() / audioDurationMs.toDouble() else 0.0

        var playbackDurationMs = 0L
        var playbackSuccess = false
        var playbackError: String? = null

        try {
            Log.i(TAG, "[RUN $runIndex-PLAYBACK] Starting AudioTrack playback...")
            playbackDurationMs = measureTimeMillis {
                runBlocking {
                    playbackEngine!!.play(audio)
                }
            }
            playbackSuccess = true
            Log.i(TAG, "[RUN $runIndex-PLAYBACK] AudioTrack playback completed in ${playbackDurationMs}ms (Audio duration: ${audioDurationMs}ms)")
        } catch (e: Throwable) {
            Log.e(TAG, "[RUN $runIndex] Playback failed", e)
            playbackError = "Playback error: ${e.message}"
        } finally {
            try {
                playbackEngine?.release()
            } catch (_: Exception) {}
            try {
                engine?.release()
            } catch (_: Exception) {}
        }

        Log.i(TAG, "==================================================")
        Log.i(TAG, "[RUN $runIndex SUMMARY]")
        Log.i(TAG, "  Init: ${initTimeMs}ms")
        Log.i(TAG, "  Synthesis: ${synthesisTimeMs}ms (${audio.sampleCount} samples @ ${audio.sampleRate}Hz)")
        Log.i(TAG, "  Duration: ${"%.3f".format(durationSeconds)}s")
        Log.i(TAG, "  RTF: ${"%.3f".format(rtf)}")
        Log.i(TAG, "  Playback: ${playbackDurationMs}ms (Success=$playbackSuccess)")
        Log.i(TAG, "==================================================")

        return PlaybackRunResult(
            runIndex = runIndex,
            text = text,
            language = language,
            initSuccess = true,
            synthesisSuccess = true,
            playbackSuccess = playbackSuccess,
            sampleCount = audio.sampleCount,
            sampleRate = audio.sampleRate,
            channelCount = audio.channelCount,
            audioDurationSeconds = durationSeconds,
            initLatencyMs = initTimeMs,
            synthesisLatencyMs = synthesisTimeMs,
            playbackDurationMs = playbackDurationMs,
            rtf = rtf,
            errorMessage = playbackError,
            memoryBefore = memBefore,
            memoryAfterInit = memAfterInit,
            memoryAfterSynthesis = memAfterSynth
        )
    }

    /**
     * Runs the complete physical TTS validation suite (3 repeated runs of prompt 1 + 1 run of prompt 2).
     */
    fun runFullPhysicalValidationSuite(context: Context): PhysicalTtsSuiteReport {
        Log.i(TAG, "##################################################")
        Log.i(TAG, "STARTING FULL PHYSICAL TTS VALIDATION SUITE")
        Log.i(TAG, "##################################################")

        val repeatedRuns = mutableListOf<PlaybackRunResult>()

        // 3 repeated runs of prompt 1
        for (i in 1..3) {
            val res = runSinglePlaybackTest(
                context = context,
                runIndex = i,
                text = TEST_PROMPT_1,
                language = "en",
                speed = 1.0f
            )
            repeatedRuns.add(res)
            Thread.sleep(300)
        }

        // 1 run of prompt 2
        val secondSentenceRun = runSinglePlaybackTest(
            context = context,
            runIndex = 4,
            text = TEST_PROMPT_2,
            language = "en",
            speed = 1.0f
        )

        val allRepeatedOk = repeatedRuns.all { it.initSuccess && it.synthesisSuccess && it.playbackSuccess }
        val secondOk = secondSentenceRun.initSuccess && secondSentenceRun.synthesisSuccess && secondSentenceRun.playbackSuccess
        val allPassed = allRepeatedOk && secondOk

        Log.i(TAG, "##################################################")
        Log.i(TAG, "PHYSICAL TTS VALIDATION SUITE COMPLETE")
        Log.i(TAG, "  Repeated Runs (Prompt 1): ${if (allRepeatedOk) "ALL PASS" else "FAIL"}")
        Log.i(TAG, "  Second Sentence (Prompt 2): ${if (secondOk) "PASS" else "FAIL"}")
        Log.i(TAG, "  Overall Status: ${if (allPassed) "PHYSICAL TTS VALIDATION PASS" else "FAIL"}")
        Log.i(TAG, "##################################################")

        return PhysicalTtsSuiteReport(
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            androidVersion = "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
            repeatedRuns = repeatedRuns,
            secondSentenceRun = secondSentenceRun,
            allPassed = allPassed,
            errorMessage = if (!allPassed) "One or more runs failed" else null
        )
    }

    /**
     * Legacy single-call diagnostic helper for backward compatibility.
     */
    fun runDiagnostic(
        context: Context,
        text: String = TEST_PROMPT_1,
        language: String = "en-US",
        config: SherpaOfflineTtsConfig = DEFAULT_CONFIG
    ): DiagnosticResult {
        val result = runSinglePlaybackTest(context, 1, text, language, 1.0f, config)
        return DiagnosticResult(
            initializationSuccess = result.initSuccess,
            synthesisSuccess = result.synthesisSuccess,
            sampleCount = result.sampleCount,
            sampleRate = result.sampleRate,
            channelCount = result.channelCount,
            audioDurationSeconds = result.audioDurationSeconds,
            initLatencyMs = result.initLatencyMs,
            synthesisLatencyMs = result.synthesisLatencyMs,
            rtf = result.rtf,
            errorMessage = result.errorMessage
        )
    }

    data class DiagnosticResult(
        val initializationSuccess: Boolean,
        val synthesisSuccess: Boolean,
        val sampleCount: Int = 0,
        val sampleRate: Int = 0,
        val channelCount: Int = 0,
        val audioDurationSeconds: Float = 0.0f,
        val initLatencyMs: Long = 0L,
        val synthesisLatencyMs: Long = 0L,
        val rtf: Double = 0.0,
        val errorMessage: String? = null
    )
}
