package org.itantra.core.stt.diagnostic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.itantra.core.stt.SherpaWhisperAsrEngine
import org.itantra.core.stt.benchmark.IndicWerCalculator
import org.itantra.core.tts.SherpaOfflineTtsEngine
import org.itantra.core.tts.playback.PcmAudioConverter

/**
 * Controlled Hindi STT Evaluation Runner for SIH Demonstration Validation.
 *
 * Evaluates the 5 mandatory SIH demonstration sentences (3 runs each, 15 trials total)
 * using the embedded Hindi neural TTS to synthesize precise 16 kHz reference speech waveforms,
 * feeding them into [SherpaWhisperAsrEngine], and measuring:
 * - Exact Match
 * - WER (Word Error Rate via IndicWerCalculator)
 * - CER (Character Error Rate via IndicWerCalculator)
 * - Script Correctness (Validates presence of Devanagari Unicode \u0900..\u097F vs Romanized Latin)
 * - STT Latency & RTF
 */
object ControlledHindiSttRunner {

    private const val TAG = "ControlledHindiStt"

    val CONTROLLED_SENTENCES = listOf(
        "नमस्ते, यह एक संचार परीक्षण है।",
        "अभियान सुबह नौ बजे शुरू होगा।",
        "आपातकालीन संदेश तुरंत भेजें।",
        "यह प्रणाली बिना इंटरनेट के काम करती है।",
        "यह संदेश अर्थपूर्ण पाठ के रूप में भेजा गया है।"
    )

    data class SingleRunResult(
        val sentenceIndex: Int,
        val runIndex: Int,
        val referenceText: String,
        val rawHypothesis: String,
        val normalizedReference: String,
        val normalizedHypothesis: String,
        val isExactMatch: Boolean,
        val isDevanagariScript: Boolean,
        val wer: Double,
        val cer: Double,
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int,
        val hits: Int,
        val inferenceTimeMs: Long,
        val audioDurationMs: Long,
        val rtf: Double
    )

    data class ControlledSuiteReport(
        val totalTrials: Int,
        val exactMatchCount: Int,
        val devanagariCount: Int,
        val meanWer: Double,
        val meanCer: Double,
        val meanRtf: Double,
        val meanInferenceTimeMs: Double,
        val results: List<SingleRunResult>
    )

    /**
     * Executes 3 runs for each of the 5 controlled Hindi sentences.
     */
    suspend fun runEvaluation(context: Context): ControlledSuiteReport = withContext(Dispatchers.Default) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== STARTING CONTROLLED HINDI STT EVALUATION (5 Sentences x 3 Runs = 15 Trials) ===")
        Log.i(TAG, "=========================================================")

        val ttsEngine = SherpaOfflineTtsEngine(context = context, autoInitialize = true)
        val asrEngine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "hi")

        val runResults = mutableListOf<SingleRunResult>()

        try {
            for ((sIdx, sentence) in CONTROLLED_SENTENCES.withIndex()) {
                Log.i(TAG, "\n--- Synthesizing Hindi Reference Audio for Sentence #${sIdx + 1}: \"$sentence\" ---")
                val ttsAudio = ttsEngine.synthesize(text = sentence, language = "hi", speed = 1.0f)
                
                // Convert float samples to 16-bit PCM shorts and resample if needed
                val pcmShorts = PcmAudioConverter.floatToPcm16(ttsAudio.samples)
                val targetShorts = if (ttsAudio.sampleRate == 16000) {
                    pcmShorts
                } else {
                    resamplePcm16(pcmShorts, ttsAudio.sampleRate, 16000)
                }

                val audioDurationMs = (targetShorts.size.toLong() * 1000L) / 16000L

                for (run in 1..3) {
                    Log.i(TAG, "[Sentence ${sIdx + 1}/5, Run $run/3] Transcribing with Whisper-Tiny INT8 (language='hi')...")
                    
                    val asrResult = asrEngine.transcribe(
                        audio = targetShorts,
                        sampleRate = 16000,
                        language = "hi"
                    )

                    val werResult = IndicWerCalculator.calculate(
                        reference = sentence,
                        hypothesis = asrResult.text,
                        normalize = true
                    )

                    val rtf = if (audioDurationMs > 0) asrResult.inferenceTimeMs.toDouble() / audioDurationMs.toDouble() else 0.0
                    val isDevanagari = asrResult.text.any { it in '\u0900'..'\u097F' }

                    val res = SingleRunResult(
                        sentenceIndex = sIdx + 1,
                        runIndex = run,
                        referenceText = sentence,
                        rawHypothesis = asrResult.text,
                        normalizedReference = werResult.normalizedReference,
                        normalizedHypothesis = werResult.normalizedHypothesis,
                        isExactMatch = werResult.isExactMatch,
                        isDevanagariScript = isDevanagari,
                        wer = werResult.wordStats.errorRate,
                        cer = werResult.charStats.errorRate,
                        substitutions = werResult.wordStats.substitutions,
                        deletions = werResult.wordStats.deletions,
                        insertions = werResult.wordStats.insertions,
                        hits = werResult.wordStats.hits,
                        inferenceTimeMs = asrResult.inferenceTimeMs,
                        audioDurationMs = audioDurationMs,
                        rtf = rtf
                    )

                    runResults.add(res)

                    Log.i(
                        TAG,
                        "[S${sIdx + 1}-R$run] REF: \"$sentence\"\n" +
                                "[S${sIdx + 1}-R$run] HYP: \"${asrResult.text}\"\n" +
                                "[S${sIdx + 1}-R$run] SCRIPT: ${if (isDevanagari) "DEVANAGARI" else "ROMANIZED/LATIN"}\n" +
                                "[S${sIdx + 1}-R$run] WER: ${"%.2f".format(res.wer * 100)}% | CER: ${"%.2f".format(res.cer * 100)}% | Exact: ${res.isExactMatch} | Time: ${res.inferenceTimeMs}ms (RTF ${"%.3f".format(rtf)})"
                    )
                }
            }

            val totalTrials = runResults.size
            val exactMatches = runResults.count { it.isExactMatch }
            val devanagariMatches = runResults.count { it.isDevanagariScript }
            val meanWer = runResults.map { it.wer }.average()
            val meanCer = runResults.map { it.cer }.average()
            val meanRtf = runResults.map { it.rtf }.average()
            val meanInference = runResults.map { it.inferenceTimeMs.toDouble() }.average()

            val report = ControlledSuiteReport(
                totalTrials = totalTrials,
                exactMatchCount = exactMatches,
                devanagariCount = devanagariMatches,
                meanWer = meanWer,
                meanCer = meanCer,
                meanRtf = meanRtf,
                meanInferenceTimeMs = meanInference,
                results = runResults
            )

            Log.i(TAG, "=========================================================")
            Log.i(TAG, "=== CONTROLLED HINDI STT SUMMARY REPORT ===")
            Log.i(TAG, "Total Trials: $totalTrials (5 sentences x 3 runs)")
            Log.i(TAG, "Exact Matches: $exactMatches / $totalTrials (${"%.1f".format((exactMatches.toDouble() / totalTrials) * 100)}%)")
            Log.i(TAG, "Devanagari Script Output: $devanagariMatches / $totalTrials (${"%.1f".format((devanagariMatches.toDouble() / totalTrials) * 100)}%)")
            Log.i(TAG, "Mean WER: ${"%.2f".format(meanWer * 100)}%")
            Log.i(TAG, "Mean CER: ${"%.2f".format(meanCer * 100)}%")
            Log.i(TAG, "Mean RTF: ${"%.3f".format(meanRtf)}")
            Log.i(TAG, "Mean Inference Time: ${"%.1f".format(meanInference)} ms")
            Log.i(TAG, "=========================================================")

            report
        } finally {
            asrEngine.close()
            ttsEngine.release()
        }
    }

    private fun resamplePcm16(input: ShortArray, inRate: Int, outRate: Int): ShortArray {
        if (inRate == outRate) return input
        val ratio = outRate.toDouble() / inRate.toDouble()
        val outLen = (input.size * ratio).toInt()
        val output = ShortArray(outLen)
        for (i in 0 until outLen) {
            val srcIndex = (i / ratio).toInt().coerceIn(0, input.size - 1)
            output[i] = input[srcIndex]
        }
        return output
    }
}
