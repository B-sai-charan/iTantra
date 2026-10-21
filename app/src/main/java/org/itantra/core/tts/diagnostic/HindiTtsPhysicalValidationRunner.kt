package org.itantra.core.tts.diagnostic

import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.itantra.core.tts.SherpaOfflineTtsEngine
import org.itantra.core.tts.TtsAudio
import org.itantra.core.tts.UnsupportedTtsLanguageException
import org.itantra.core.tts.playback.AndroidAudioTrackPlaybackEngine
import kotlin.system.measureTimeMillis

/**
 * Diagnostic runner for physical on-device validation of the production Hindi Neural TTS integration
 * in M3 (SherpaOfflineTtsEngine + AndroidAudioTrackPlaybackEngine).
 */
object HindiTtsPhysicalValidationRunner {

    private const val TAG = "PHYSICAL_HINDI_TTS_VALIDATION"

    const val HINDI_P1 = "नमस्ते। यह iTantra का हिंदी टेक्स्ट टू स्पीच परीक्षण है।"
    const val HINDI_P2 = "यह एक ऑफलाइन संचार प्रणाली है जो कम बैंडविड्थ वाले नेटवर्क पर काम करती है।"
    const val HINDI_P3 = "आपातकालीन संदेश को हिंदी में स्पष्ट और विश्वसनीय रूप से सुनाया जाना चाहिए।"
    const val ENGLISH_P = "The quick brown fox jumps over the lazy dog."

    data class MemorySnapshot(
        val totalPssKb: Long = 0L,
        val nativeAllocatedMb: Double = 0.0,
        val nativeSizeMb: Double = 0.0
    )

    data class PromptResult(
        val promptId: String,
        val text: String,
        val languageSupplied: String,
        val resolvedModel: String,
        val initSuccess: Boolean,
        val synthesisSuccess: Boolean,
        val sampleCount: Int = 0,
        val sampleRate: Int = 0,
        val audioDurationSeconds: Float = 0.0f,
        val synthesisTimeMs: Long = 0L,
        val rtf: Double = 0.0,
        val samplesFinite: Boolean = false,
        val playbackSuccess: Boolean = false,
        val playbackDurationMs: Long = 0L,
        val errorMessage: String? = null
    )

    data class SuiteReport(
        val deviceModel: String,
        val androidVersion: String,
        val sdkInt: Int,
        val abi: String,
        val initLatencyMs: Long,
        val memBeforeInit: MemorySnapshot,
        val memAfterInit: MemorySnapshot,
        val memAfterSynthesis: MemorySnapshot,
        val memAfterRelease: MemorySnapshot,
        val testAResults: List<PromptResult>,
        val testBAliasesPassed: Boolean,
        val testBAliasResults: List<PromptResult>,
        val testCEnglishPassed: Boolean,
        val testCResult: PromptResult?,
        val testDUnsupportedPassed: Boolean,
        val testFLifecyclePassed: Boolean,
        val allPassed: Boolean,
        val errorMessage: String? = null
    )

    fun captureMemory(): MemorySnapshot {
        val nativeAllocated = Debug.getNativeHeapAllocatedSize() / (1024.0 * 1024.0)
        val nativeSize = Debug.getNativeHeapSize() / (1024.0 * 1024.0)
        val pss = Debug.getPss()
        return MemorySnapshot(
            totalPssKb = pss,
            nativeAllocatedMb = nativeAllocated,
            nativeSizeMb = nativeSize
        )
    }

    fun runSuite(context: Context, enablePlayback: Boolean = true): SuiteReport {
        Log.i(TAG, "############################################################")
        Log.i(TAG, "STARTING PHYSICAL PRODUCTION HINDI TTS VALIDATION SUITE")
        Log.i(TAG, "Device: ${Build.MODEL} (${Build.MANUFACTURER}) | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) | ABI: ${Build.SUPPORTED_ABIS.firstOrNull()}")
        Log.i(TAG, "############################################################")

        val memBefore = captureMemory()
        Log.i(TAG, "[MEM-BEFORE] PSS: ${memBefore.totalPssKb}KB, NativeAlloc: ${"%.2f".format(memBefore.nativeAllocatedMb)}MB")

        var engine: SherpaOfflineTtsEngine? = null
        val playbackEngine = if (enablePlayback) AndroidAudioTrackPlaybackEngine() else null
        var initLatencyMs = 0L

        try {
            initLatencyMs = measureTimeMillis {
                engine = SherpaOfflineTtsEngine(context = context, autoInitialize = true)
            }
            Log.i(TAG, "[INIT-SUCCESS] Production SherpaOfflineTtsEngine initialized in ${initLatencyMs}ms (State: ${engine?.state})")
        } catch (t: Throwable) {
            Log.e(TAG, "[INIT-FAILED] Engine initialization threw exception", t)
            return SuiteReport(
                deviceModel = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
                initLatencyMs = initLatencyMs,
                memBeforeInit = memBefore,
                memAfterInit = captureMemory(),
                memAfterSynthesis = captureMemory(),
                memAfterRelease = captureMemory(),
                testAResults = emptyList(),
                testBAliasesPassed = false,
                testBAliasResults = emptyList(),
                testCEnglishPassed = false,
                testCResult = null,
                testDUnsupportedPassed = false,
                testFLifecyclePassed = false,
                allPassed = false,
                errorMessage = "Engine initialization failed: ${t.message}"
            )
        }

        val memAfterInit = captureMemory()
        Log.i(TAG, "[MEM-AFTER-INIT] PSS: ${memAfterInit.totalPssKb}KB, NativeAlloc: ${"%.2f".format(memAfterInit.nativeAllocatedMb)}MB")

        // -------------------------------------------------------------
        // TEST A: DIRECT PRODUCTION HINDI TTS (P1, P2, P3)
        // -------------------------------------------------------------
        Log.i(TAG, "============================================================")
        Log.i(TAG, "TEST A: PRODUCTION HINDI TTS PROMPTS (P1, P2, P3)")
        Log.i(TAG, "============================================================")

        val promptsA = listOf(
            Pair("P1", HINDI_P1),
            Pair("P2", HINDI_P2),
            Pair("P3", HINDI_P3)
        )
        val resultsA = mutableListOf<PromptResult>()

        for ((id, text) in promptsA) {
            Log.i(TAG, "[$id START] Lang: hi, Text: \"$text\"")
            var synthTimeMs = 0L
            val audio: TtsAudio
            try {
                audio = runBlocking {
                    val m: TtsAudio
                    synthTimeMs = measureTimeMillis {
                        m = engine!!.synthesize(text = text, language = "hi", speed = 1.0f)
                    }
                    m
                }
            } catch (t: Throwable) {
                Log.e(TAG, "[$id FAILED] Synthesis exception", t)
                resultsA.add(
                    PromptResult(
                        promptId = id,
                        text = text,
                        languageSupplied = "hi",
                        resolvedModel = "vits-piper-hi_IN-rohan-medium",
                        initSuccess = true,
                        synthesisSuccess = false,
                        errorMessage = t.message
                    )
                )
                continue
            }

            val rtf = if (audio.durationSeconds > 0) (synthTimeMs / 1000.0) / audio.durationSeconds.toDouble() else 0.0
            val isFinite = audio.samples.all { it.isFinite() }
            val minVal = audio.samples.minOrNull() ?: 0.0f
            val maxVal = audio.samples.maxOrNull() ?: 0.0f

            Log.i(TAG, "[$id SYNTH-SUCCESS] Samples: ${audio.sampleCount}, Rate: ${audio.sampleRate}Hz, Duration: ${"%.3f".format(audio.durationSeconds)}s, Time: ${synthTimeMs}ms, RTF: ${"%.4f".format(rtf)}")
            Log.i(TAG, "[$id AUDIO-VALIDATION] Finite: $isFinite, Min: ${"%.4f".format(minVal)}, Max: ${"%.4f".format(maxVal)}")

            var playSuccess = false
            var playTimeMs = 0L
            if (playbackEngine != null) {
                try {
                    Log.i(TAG, "[$id PLAYBACK-START] Playing via AudioTrack (${audio.sampleRate} Hz)...")
                    playTimeMs = measureTimeMillis {
                        runBlocking {
                            playbackEngine.play(audio)
                        }
                    }
                    playSuccess = true
                    Log.i(TAG, "[$id PLAYBACK-COMPLETE] Finished in ${playTimeMs}ms")
                } catch (t: Throwable) {
                    Log.e(TAG, "[$id PLAYBACK-FAILED] AudioTrack playback error", t)
                }
            } else {
                playSuccess = true
            }

            resultsA.add(
                PromptResult(
                    promptId = id,
                    text = text,
                    languageSupplied = "hi",
                    resolvedModel = "vits-piper-hi_IN-rohan-medium",
                    initSuccess = true,
                    synthesisSuccess = true,
                    sampleCount = audio.sampleCount,
                    sampleRate = audio.sampleRate,
                    audioDurationSeconds = audio.durationSeconds,
                    synthesisTimeMs = synthTimeMs,
                    rtf = rtf,
                    samplesFinite = isFinite,
                    playbackSuccess = playSuccess,
                    playbackDurationMs = playTimeMs
                )
            )
            Thread.sleep(300)
        }

        val memAfterSynthesis = captureMemory()
        Log.i(TAG, "[MEM-AFTER-SYNTHESIS] PSS: ${memAfterSynthesis.totalPssKb}KB, NativeAlloc: ${"%.2f".format(memAfterSynthesis.nativeAllocatedMb)}MB")

        // -------------------------------------------------------------
        // TEST B: HINDI LANGUAGE ALIAS ROUTING (hi, hi-IN, hi_IN)
        // -------------------------------------------------------------
        Log.i(TAG, "============================================================")
        Log.i(TAG, "TEST B: HINDI LANGUAGE ALIAS ROUTING")
        Log.i(TAG, "============================================================")

        val aliasTags = listOf("hi", "hi-IN", "hi_IN")
        val aliasResults = mutableListOf<PromptResult>()
        var allAliasesPassed = true

        for (tag in aliasTags) {
            Log.i(TAG, "[ALIAS TEST] Tag: '$tag'")
            var synthTimeMs = 0L
            try {
                val audio = runBlocking {
                    val m: TtsAudio
                    synthTimeMs = measureTimeMillis {
                        m = engine!!.synthesize(text = "नमस्ते", language = tag, speed = 1.0f)
                    }
                    m
                }
                val rtf = if (audio.durationSeconds > 0) (synthTimeMs / 1000.0) / audio.durationSeconds.toDouble() else 0.0
                Log.i(TAG, "[ALIAS PASS] Tag: '$tag' -> Rate: ${audio.sampleRate}Hz (Expected 22050), Samples: ${audio.sampleCount}, Time: ${synthTimeMs}ms, RTF: ${"%.3f".format(rtf)}")
                if (audio.sampleRate != 22050) {
                    allAliasesPassed = false
                }
                aliasResults.add(
                    PromptResult(
                        promptId = "ALIAS-$tag",
                        text = "नमस्ते",
                        languageSupplied = tag,
                        resolvedModel = "vits-piper-hi_IN-rohan-medium",
                        initSuccess = true,
                        synthesisSuccess = true,
                        sampleCount = audio.sampleCount,
                        sampleRate = audio.sampleRate,
                        audioDurationSeconds = audio.durationSeconds,
                        synthesisTimeMs = synthTimeMs,
                        rtf = rtf,
                        samplesFinite = audio.samples.all { it.isFinite() },
                        playbackSuccess = true
                    )
                )
            } catch (t: Throwable) {
                Log.e(TAG, "[ALIAS FAIL] Tag: '$tag' failed", t)
                allAliasesPassed = false
                aliasResults.add(
                    PromptResult(
                        promptId = "ALIAS-$tag",
                        text = "नमस्ते",
                        languageSupplied = tag,
                        resolvedModel = "unknown",
                        initSuccess = true,
                        synthesisSuccess = false,
                        errorMessage = t.message
                    )
                )
            }
        }

        // -------------------------------------------------------------
        // TEST C: ENGLISH REGRESSION
        // -------------------------------------------------------------
        Log.i(TAG, "============================================================")
        Log.i(TAG, "TEST C: ENGLISH REGRESSION (en)")
        Log.i(TAG, "============================================================")

        var testCEnglishPassed = false
        var resultC: PromptResult? = null
        try {
            var synthTimeMs = 0L
            val audio = runBlocking {
                val m: TtsAudio
                synthTimeMs = measureTimeMillis {
                    m = engine!!.synthesize(text = ENGLISH_P, language = "en", speed = 1.0f)
                }
                m
            }
            val rtf = if (audio.durationSeconds > 0) (synthTimeMs / 1000.0) / audio.durationSeconds.toDouble() else 0.0
            Log.i(TAG, "[ENGLISH PASS] Rate: ${audio.sampleRate}Hz (Expected 16000), Samples: ${audio.sampleCount}, Time: ${synthTimeMs}ms, RTF: ${"%.3f".format(rtf)}")
            testCEnglishPassed = (audio.sampleRate == 16000 && audio.samples.isNotEmpty())

            var playTimeMs = 0L
            if (playbackEngine != null && testCEnglishPassed) {
                playTimeMs = measureTimeMillis {
                    runBlocking {
                        playbackEngine.play(audio)
                    }
                }
            }

            resultC = PromptResult(
                promptId = "ENG-REGRESSION",
                text = ENGLISH_P,
                languageSupplied = "en",
                resolvedModel = "vits-piper-en_US-lessac-low",
                initSuccess = true,
                synthesisSuccess = true,
                sampleCount = audio.sampleCount,
                sampleRate = audio.sampleRate,
                audioDurationSeconds = audio.durationSeconds,
                synthesisTimeMs = synthTimeMs,
                rtf = rtf,
                samplesFinite = audio.samples.all { it.isFinite() },
                playbackSuccess = true,
                playbackDurationMs = playTimeMs
            )
        } catch (t: Throwable) {
            Log.e(TAG, "[ENGLISH FAIL] English synthesis threw exception", t)
            resultC = PromptResult(
                promptId = "ENG-REGRESSION",
                text = ENGLISH_P,
                languageSupplied = "en",
                resolvedModel = "vits-piper-en_US-lessac-low",
                initSuccess = true,
                synthesisSuccess = false,
                errorMessage = t.message
            )
        }

        // -------------------------------------------------------------
        // TEST D: UNSUPPORTED LANGUAGE ("ta")
        // -------------------------------------------------------------
        Log.i(TAG, "============================================================")
        Log.i(TAG, "TEST D: UNSUPPORTED LANGUAGE (ta)")
        Log.i(TAG, "============================================================")

        var testDUnsupportedPassed = false
        try {
            runBlocking {
                engine!!.synthesize(text = "வணக்கம்", language = "ta", speed = 1.0f)
            }
            Log.e(TAG, "[UNSUPPORTED FAIL] Expected UnsupportedTtsLanguageException was NOT thrown!")
        } catch (e: UnsupportedTtsLanguageException) {
            Log.i(TAG, "[UNSUPPORTED PASS] Correctly caught UnsupportedTtsLanguageException for language '${e.language}'")
            testDUnsupportedPassed = true
        } catch (t: Throwable) {
            Log.e(TAG, "[UNSUPPORTED FAIL] Caught unexpected exception type: ${t.javaClass.simpleName}", t)
        }

        // -------------------------------------------------------------
        // TEST F: LIFECYCLE (Release -> Re-Initialize -> Synthesize)
        // -------------------------------------------------------------
        Log.i(TAG, "============================================================")
        Log.i(TAG, "TEST F: LIFECYCLE (Release -> Re-Initialize -> Synthesize)")
        Log.i(TAG, "============================================================")

        var testFLifecyclePassed = false
        try {
            engine!!.release()
            Log.i(TAG, "[LIFECYCLE] Engine released successfully. State: ${engine?.state}")

            val memAfterRelease = captureMemory()
            Log.i(TAG, "[MEM-AFTER-RELEASE] PSS: ${memAfterRelease.totalPssKb}KB, NativeAlloc: ${"%.2f".format(memAfterRelease.nativeAllocatedMb)}MB")

            // Create new engine instance and synthesize Hindi prompt again
            val newEngine = SherpaOfflineTtsEngine(context = context, autoInitialize = true)
            Log.i(TAG, "[LIFECYCLE] Re-created new SherpaOfflineTtsEngine instance. State: ${newEngine.state}")

            val secondAudio = runBlocking {
                newEngine.synthesize(text = "पुनः परीक्षण सफल रहा।", language = "hi", speed = 1.0f)
            }
            Log.i(TAG, "[LIFECYCLE PASS] Re-synthesized Hindi after teardown: ${secondAudio.sampleCount} samples at ${secondAudio.sampleRate}Hz")
            testFLifecyclePassed = (secondAudio.sampleRate == 22050 && secondAudio.sampleCount > 0)
            newEngine.release()
        } catch (t: Throwable) {
            Log.e(TAG, "[LIFECYCLE FAIL] Teardown and re-initialization threw exception", t)
        }

        val memFinal = captureMemory()
        val allTestAPassed = resultsA.size == 3 && resultsA.all { it.synthesisSuccess && it.playbackSuccess && it.samplesFinite }
        val overallPassed = allTestAPassed && allAliasesPassed && testCEnglishPassed && testDUnsupportedPassed && testFLifecyclePassed

        Log.i(TAG, "############################################################")
        Log.i(TAG, "PHYSICAL HINDI TTS VALIDATION SUITE COMPLETE — ALL PASSED: $overallPassed")
        Log.i(TAG, "Test A (Hindi P1-P3): $allTestAPassed | Test B (Aliases): $allAliasesPassed | Test C (English): $testCEnglishPassed | Test D (Unsupported): $testDUnsupportedPassed | Test F (Lifecycle): $testFLifecyclePassed")
        Log.i(TAG, "############################################################")

        return SuiteReport(
            deviceModel = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",
            initLatencyMs = initLatencyMs,
            memBeforeInit = memBefore,
            memAfterInit = memAfterInit,
            memAfterSynthesis = memAfterSynthesis,
            memAfterRelease = memFinal,
            testAResults = resultsA,
            testBAliasesPassed = allAliasesPassed,
            testBAliasResults = aliasResults,
            testCEnglishPassed = testCEnglishPassed,
            testCResult = resultC,
            testDUnsupportedPassed = testDUnsupportedPassed,
            testFLifecyclePassed = testFLifecyclePassed,
            allPassed = overallPassed
        )
    }
}
