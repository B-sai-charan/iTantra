package org.itantra.core.stt.diagnostic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.itantra.core.pipeline.SpeechSegmentCollector
import org.itantra.core.stt.SherpaWhisperAsrEngine
import org.itantra.core.stt.benchmark.TextNormalizer
import org.itantra.core.stt.benchmark.WerCalculator
import org.itantra.core.vad.SileroVadEngine
import org.itantra.core.vad.VadConfig
import org.itantra.core.vad.VadFrameAdapter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Diagnostic runner for the M1 Controlled Live-Mic STT Accuracy Sprint (VAD A-B-C-D).
 *
 * Runs a controlled 120-trial matrix:
 * 4 VAD configurations (minSilence = 400ms, 600ms, 800ms, 1000ms)
 * x 10 fixed English benchmark sentences
 * x 3 repetitions
 * = 120 physical trials.
 */
object VadExperimentAbcdRunner {

    private const val TAG = "VAD_ABCD_EXPERIMENT"

    data class BenchmarkSentence(
        val id: String,
        val text: String,
        val audioAssetPath: String? = null
    )

    val BENCHMARK_SENTENCES: List<BenchmarkSentence> = listOf(
        BenchmarkSentence("EN-01", "Hello, this is a communication test."),
        BenchmarkSentence("EN-02", "The mission starts at nine in the morning."),
        BenchmarkSentence("EN-03", "Send the emergency message immediately."),
        BenchmarkSentence("EN-04", "The system works without internet connectivity."),
        BenchmarkSentence("EN-05", "This message is transmitted as semantic text."),
        BenchmarkSentence("EN-06", "The quick brown fox jumps over the lazy dog."),
        BenchmarkSentence("EN-07", "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels.", "speech_test.wav"),
        BenchmarkSentence("EN-08", "YET THESE THOUGHTS AFFECTED HESTER PRYNNE LESS WITH HOPE THAN APPREHENSION", "speech_test_003.wav"),
        BenchmarkSentence("EN-09", "well the last year this property had six hundred thousand birds on it", "speech_test_004.wav"),
        BenchmarkSentence("EN-10", "Good morning. How are you today?")
    )

    data class VadConfiguration(
        val name: String,
        val minSilenceMs: Int,
        val config: VadConfig
    )

    val CONFIGURATIONS: List<VadConfiguration> = listOf(
        VadConfiguration("A", 400, VadConfig(minSilenceDurationMs = 400)),
        VadConfiguration("B", 600, VadConfig(minSilenceDurationMs = 600)),
        VadConfiguration("C", 800, VadConfig(minSilenceDurationMs = 800)),
        VadConfiguration("D", 1000, VadConfig(minSilenceDurationMs = 1000))
    )

    data class TrialResult(
        val configName: String,
        val minSilenceMs: Int,
        val sentenceId: String,
        val repIndex: Int,
        val expectedTranscript: String,
        val actualTranscript: String,
        val normalizedReference: String,
        val normalizedHypothesis: String,
        val wer: Double,
        val cer: Double,
        val isExactMatch: Boolean,
        val speechDurationMs: Long,
        val vadSegmentDurationMs: Long,
        val sttLatencyMs: Long,
        val isFragmented: Boolean,
        val segmentCount: Int,
        val trialSuccess: Boolean,
        val rawPcmRmsDbfs: Double = 0.0,
        val rawPcmPeakDbfs: Double = 0.0
    )

    data class ConfigSummary(
        val configName: String,
        val minSilenceMs: Int,
        val totalTrials: Int,
        val successTrials: Int,
        val exactMatchCount: Int,
        val exactMatchRate: Double,
        val fragmentationCount: Int,
        val fragmentationRate: Double,
        val arithmeticMeanWer: Double,
        val arithmeticMeanCer: Double,
        val corpusWer: Double,
        val corpusCer: Double,
        val meanSpeechDurationMs: Double,
        val meanVadSegmentDurationMs: Double,
        val meanSttLatencyMs: Double
    )

    data class PcmCharacteristics(
        val trialId: String,
        val sampleCount: Int,
        val durationSec: Double,
        val rmsLinear: Double,
        val rmsDbfs: Double,
        val peakAbsolute: Int,
        val peakDbfs: Double,
        val clippingCount: Int,
        val clippingPct: Double,
        val silenceFloorDbfs: Double,
        val speechLevelDbfs: Double,
        val dynamicContrastDb: Double,
        val crestFactor: Double
    )

    data class ExperimentReport(
        val deviceModel: String,
        val deviceSerial: String,
        val totalTrials: Int,
        val trials: List<TrialResult>,
        val summaries: List<ConfigSummary>,
        val pcmProfiles: List<PcmCharacteristics>
    )

    /**
     * Analyzes PCM16 audio array and computes physical signal characteristics.
     */
    fun computePcmCharacteristics(
        label: String,
        audio: ShortArray,
        sampleRate: Int = 16000
    ): PcmCharacteristics {
        val n = audio.size
        if (n == 0) {
            return PcmCharacteristics(label, 0, 0.0, 0.0, -100.0, 0, -100.0, 0, 0.0, -100.0, -100.0, 0.0, 0.0)
        }

        var sumSq = 0.0
        var maxAbs = 0
        var clipCount = 0

        for (s in audio) {
            val v = s.toInt()
            val absV = abs(v)
            if (absV > maxAbs) maxAbs = absV
            if (absV >= 32767) clipCount++
            sumSq += (v.toDouble() * v.toDouble())
        }

        val rms = sqrt(sumSq / n)
        val rmsDbfs = if (rms > 0.0) 20.0 * log10(rms / 32768.0) else -100.0
        val peakDbfs = if (maxAbs > 0) 20.0 * log10(maxAbs.toDouble() / 32768.0) else -100.0
        val clipPct = (clipCount.toDouble() / n) * 100.0
        val crestFactor = if (rms > 0.0) maxAbs.toDouble() / rms else 0.0

        // Calculate frame energies (512-sample frames = 32ms) to separate silence floor and speech level
        val frameSize = 512
        val numFrames = n / frameSize
        val frameEnergies = ArrayList<Double>()

        for (i in 0 until numFrames) {
            var fSumSq = 0.0
            val offset = i * frameSize
            for (j in 0 until frameSize) {
                val sample = audio[offset + j].toDouble()
                fSumSq += sample * sample
            }
            val fRms = sqrt(fSumSq / frameSize)
            val fDbfs = if (fRms > 0.0) 20.0 * log10(fRms / 32768.0) else -100.0
            frameEnergies.add(fDbfs)
        }

        // Silence floor: average energy of lowest 20% of frames
        // Speech level: average energy of frames exceeding -45 dBFS (or top 40% if high noise)
        frameEnergies.sort()
        val silenceFrameCount = max(1, (numFrames * 0.20).toInt())
        val silenceSublist = frameEnergies.take(silenceFrameCount)
        val silenceFloorDbfs = silenceSublist.average()

        val speechFrames = frameEnergies.filter { it > -45.0 }
        val speechLevelDbfs = if (speechFrames.isNotEmpty()) {
            speechFrames.average()
        } else {
            val top40Count = max(1, (numFrames * 0.40).toInt())
            frameEnergies.takeLast(top40Count).average()
        }

        val dynamicContrast = speechLevelDbfs - silenceFloorDbfs

        return PcmCharacteristics(
            trialId = label,
            sampleCount = n,
            durationSec = n.toDouble() / sampleRate,
            rmsLinear = rms,
            rmsDbfs = rmsDbfs,
            peakAbsolute = maxAbs,
            peakDbfs = peakDbfs,
            clippingCount = clipCount,
            clippingPct = clipPct,
            silenceFloorDbfs = silenceFloorDbfs,
            speechLevelDbfs = speechLevelDbfs,
            dynamicContrastDb = dynamicContrast,
            crestFactor = crestFactor
        )
    }

    /**
     * Aggregates trial results by VAD configuration.
     */
    fun summarizeConfiguration(
        configName: String,
        minSilenceMs: Int,
        trials: List<TrialResult>
    ): ConfigSummary {
        val configTrials = trials.filter { it.configName == configName }
        val n = configTrials.size
        if (n == 0) {
            return ConfigSummary(configName, minSilenceMs, 0, 0, 0, 0.0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        }

        val exactMatches = configTrials.count { it.isExactMatch }
        val fragmentedCount = configTrials.count { it.isFragmented }
        val successCount = configTrials.count { it.trialSuccess }

        val meanWer = configTrials.map { it.wer }.average()
        val meanCer = configTrials.map { it.cer }.average()

        var totalWordErrors = 0
        var totalRefWords = 0
        var totalCharErrors = 0
        var totalRefChars = 0

        for (t in configTrials) {
            val werRes = WerCalculator.calculate(t.expectedTranscript, t.actualTranscript, true)
            totalWordErrors += werRes.wordStats.errorCount
            totalRefWords += werRes.wordStats.referenceCount
            totalCharErrors += werRes.charStats.errorCount
            totalRefChars += werRes.charStats.referenceCount
        }

        val corpusWer = if (totalRefWords > 0) totalWordErrors.toDouble() / totalRefWords else 0.0
        val corpusCer = if (totalRefChars > 0) totalCharErrors.toDouble() / totalRefChars else 0.0

        val meanSpeechDur = configTrials.map { it.speechDurationMs }.average()
        val meanVadSegDur = configTrials.map { it.vadSegmentDurationMs }.average()
        val meanLatency = configTrials.map { it.sttLatencyMs }.average()

        return ConfigSummary(
            configName = configName,
            minSilenceMs = minSilenceMs,
            totalTrials = n,
            successTrials = successCount,
            exactMatchCount = exactMatches,
            exactMatchRate = exactMatches.toDouble() / n,
            fragmentationCount = fragmentedCount,
            fragmentationRate = fragmentedCount.toDouble() / n,
            arithmeticMeanWer = meanWer,
            arithmeticMeanCer = meanCer,
            corpusWer = corpusWer,
            corpusCer = corpusCer,
            meanSpeechDurationMs = meanSpeechDur,
            meanVadSegmentDurationMs = meanVadSegDur,
            meanSttLatencyMs = meanLatency
        )
    }
}
