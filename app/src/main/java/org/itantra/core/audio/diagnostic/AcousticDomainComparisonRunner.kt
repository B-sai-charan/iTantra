package org.itantra.core.audio.diagnostic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.itantra.core.stt.SherpaWhisperAsrEngine
import org.itantra.core.stt.benchmark.TextNormalizer
import org.itantra.core.stt.benchmark.WerCalculator
import org.itantra.core.vad.SileroVadEngine
import org.itantra.core.vad.VadConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Diagnostic runner for Task M1-ACOUSTIC-DOMAIN-COMPARISON-05.
 *
 * Compares measurable acoustic domain characteristics between the known-good
 * microphone recording (`same_sentence_mic_test.wav`) and the controlled failed
 * microphone recording (`raw_mic_source_control_en.wav`).
 */
object AcousticDomainComparisonRunner {

    private const val TAG = "ACOUSTIC_COMPARISON"

    const val KNOWN_GOOD_FILE = "same_sentence_mic_test.wav"
    const val KNOWN_FAILED_FILE = "raw_mic_source_control_en.wav"

    const val GOOD_REF = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
    const val FAILED_REF = "The quick brown fox jumps over the lazy dog."

    const val SAMPLE_RATE = 16000
    const val FRAME_SIZE = 512 // 32 ms frame

    data class FileVerification(
        val fileName: String,
        val filePath: String,
        val sha256: String,
        val sampleRate: Int,
        val channelCount: Int,
        val sampleCount: Int,
        val durationMs: Long,
        val durationSeconds: Double,
        val rms: Double,
        val rmsDbfs: Double,
        val peak: Int,
        val peakDbfs: Double,
        val crestFactor: Double,
        val clippingCount: Long,
        val zeroPercentage: Double
    )

    data class VadSpeechRegion(
        val startSample: Int,
        val endSample: Int,
        val startMs: Long,
        val endMs: Long,
        val durationMs: Long,
        val segmentCount: Int,
        val totalSpeechSamples: Int,
        val totalSpeechDurationMs: Long,
        val silenceRatio: Double
    )

    data class FeatureDistribution(
        val mean: Double,
        val median: Double,
        val p10: Double,
        val p90: Double,
        val stdDev: Double
    )

    data class FrameAcousticFeatures(
        val frameRmsDbfs: FeatureDistribution,
        val peakDbfs: FeatureDistribution,
        val crestFactor: FeatureDistribution,
        val zeroCrossingRate: FeatureDistribution,
        val spectralCentroidHz: FeatureDistribution,
        val spectralRolloffHz: FeatureDistribution,
        val spectralBandwidthHz: FeatureDistribution,
        val spectralFlux: FeatureDistribution
    )

    data class FrequencyBandEnergy(
        val bandName: String,
        val lowFreqHz: Double,
        val highFreqHz: Double,
        val absoluteEnergy: Double,
        val percentageOfTotal: Double
    )

    data class TemporalEnvelope(
        val silenceRatioInsideCrop: Double,
        val voicedFrameRatio: Double,
        val dynamicRangeDb: Double,
        val lowEnergyGapCount: Int,
        val medianVoicedFrameDurationMs: Double
    )

    data class WhisperTestResult(
        val label: String,
        val fileName: String,
        val referenceText: String,
        val rawHypothesis: String,
        val normalizedHypothesis: String,
        val wer: Double,
        val cer: Double,
        val isExactMatch: Boolean,
        val inferenceTimeMs: Long,
        val rtf: Double
    )

    data class AudioComparisonProfile(
        val label: String,
        val sourceVerification: FileVerification,
        val vadRegion: VadSpeechRegion,
        val speechOnlyFile: String,
        val speechOnlySha256: String,
        val speechOnlySamples: Int,
        val speechOnlyDurationSeconds: Double,
        val acousticFeatures: FrameAcousticFeatures,
        val bandEnergies: List<FrequencyBandEnergy>,
        val temporalEnvelope: TemporalEnvelope,
        val whisperResult: WhisperTestResult
    )

    data class FullComparisonReport(
        val goodProfile: AudioComparisonProfile,
        val failedProfile: AudioComparisonProfile,
        val spectralCosineDistance: Double,
        val spectralEuclideanDistance: Double
    )

    suspend fun runComparison(context: Context): FullComparisonReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "STARTING TASK M1-ACOUSTIC-DOMAIN-COMPARISON-05")
        Log.i(TAG, "=========================================================")

        val goodFile = File(context.filesDir, KNOWN_GOOD_FILE)
        val failedFile = File(context.filesDir, KNOWN_FAILED_FILE)

        require(goodFile.exists()) { "Missing $KNOWN_GOOD_FILE in ${context.filesDir.absolutePath}" }
        require(failedFile.exists()) { "Missing $KNOWN_FAILED_FILE in ${context.filesDir.absolutePath}" }

        val goodSamples = readPcm16Wav(goodFile)
        val failedSamples = readPcm16Wav(failedFile)

        // 1. File Verifications
        val goodVerif = verifyFile(goodFile, goodSamples, KNOWN_GOOD_FILE)
        val failedVerif = verifyFile(failedFile, failedSamples, KNOWN_FAILED_FILE)

        // 2. VAD Detection
        val goodVad = detectSpeechSegments(context, goodSamples)
        val failedVad = detectSpeechSegments(context, failedSamples)

        // 3. Extract Speech-Only WAVs
        val goodCropSamples = goodSamples.copyOfRange(goodVad.startSample, goodVad.endSample)
        val failedCropSamples = failedSamples.copyOfRange(failedVad.startSample, failedVad.endSample)

        val goodCropFile = File(context.filesDir, "acoustic_good_speech.wav")
        val failedCropFile = File(context.filesDir, "acoustic_failed_speech.wav")

        writePcm16Wav(goodCropFile, goodCropSamples, SAMPLE_RATE)
        writePcm16Wav(failedCropFile, failedCropSamples, SAMPLE_RATE)

        val goodCropSha = calculateSha256(goodCropFile)
        val failedCropSha = calculateSha256(failedCropFile)

        // 4. Acoustic Features Calculation
        val goodFeatures = computeFrameFeatures(goodCropSamples)
        val failedFeatures = computeFrameFeatures(failedCropSamples)

        // 5. Frequency Band Energies
        val goodBands = computeFrequencyBands(goodCropSamples)
        val failedBands = computeFrequencyBands(failedCropSamples)

        // 6. Temporal Envelope
        val goodEnv = computeTemporalEnvelope(goodCropSamples, goodVad)
        val failedEnv = computeTemporalEnvelope(failedCropSamples, failedVad)

        // 7. Whisper Evaluation on Speech-Only Crops
        var whisperEngine: SherpaWhisperAsrEngine? = null
        val goodWhisper: WhisperTestResult
        val failedWhisper: WhisperTestResult

        try {
            whisperEngine = SherpaWhisperAsrEngine(context = context, defaultLanguage = "en")

            // Test Good
            val goodAsr = whisperEngine.transcribe(goodCropSamples, SAMPLE_RATE, "en")
            val normGoodRef = TextNormalizer.normalize(GOOD_REF)
            val normGoodHyp = TextNormalizer.normalize(goodAsr.text)
            val goodWer = WerCalculator.calculate(normGoodRef, normGoodHyp)
            val goodDurMs = (goodCropSamples.size * 1000L) / SAMPLE_RATE
            val goodRtf = if (goodDurMs > 0) goodAsr.inferenceTimeMs.toDouble() / goodDurMs.toDouble() else 0.0

            goodWhisper = WhisperTestResult(
                label = "A: Known-Good Speech Crop",
                fileName = "acoustic_good_speech.wav",
                referenceText = GOOD_REF,
                rawHypothesis = goodAsr.text,
                normalizedHypothesis = normGoodHyp,
                wer = goodWer.wordStats.errorRate,
                cer = goodWer.charStats.errorRate,
                isExactMatch = goodWer.isExactMatch,
                inferenceTimeMs = goodAsr.inferenceTimeMs,
                rtf = goodRtf
            )

            // Test Failed
            val failedAsr = whisperEngine.transcribe(failedCropSamples, SAMPLE_RATE, "en")
            val normFailedRef = TextNormalizer.normalize(FAILED_REF)
            val normFailedHyp = TextNormalizer.normalize(failedAsr.text)
            val failedWer = WerCalculator.calculate(normFailedRef, normFailedHyp)
            val failedDurMs = (failedCropSamples.size * 1000L) / SAMPLE_RATE
            val failedRtf = if (failedDurMs > 0) failedAsr.inferenceTimeMs.toDouble() / failedDurMs.toDouble() else 0.0

            failedWhisper = WhisperTestResult(
                label = "B: Known-Failed Speech Crop",
                fileName = "acoustic_failed_speech.wav",
                referenceText = FAILED_REF,
                rawHypothesis = failedAsr.text,
                normalizedHypothesis = normFailedHyp,
                wer = failedWer.wordStats.errorRate,
                cer = failedWer.charStats.errorRate,
                isExactMatch = failedWer.isExactMatch,
                inferenceTimeMs = failedAsr.inferenceTimeMs,
                rtf = failedRtf
            )
        } finally {
            whisperEngine?.close()
        }

        // 8. Spectral Distance Metric
        val (cosDist, eucDist) = computeSpectralDistance(goodBands, failedBands)

        val goodProfile = AudioComparisonProfile(
            label = "Known-Good Microphone Audio (`same_sentence_mic_test.wav`)",
            sourceVerification = goodVerif,
            vadRegion = goodVad,
            speechOnlyFile = "acoustic_good_speech.wav",
            speechOnlySha256 = goodCropSha,
            speechOnlySamples = goodCropSamples.size,
            speechOnlyDurationSeconds = goodCropSamples.size.toDouble() / SAMPLE_RATE.toDouble(),
            acousticFeatures = goodFeatures,
            bandEnergies = goodBands,
            temporalEnvelope = goodEnv,
            whisperResult = goodWhisper
        )

        val failedProfile = AudioComparisonProfile(
            label = "Controlled Failed Microphone Audio (`raw_mic_source_control_en.wav`)",
            sourceVerification = failedVerif,
            vadRegion = failedVad,
            speechOnlyFile = "acoustic_failed_speech.wav",
            speechOnlySha256 = failedCropSha,
            speechOnlySamples = failedCropSamples.size,
            speechOnlyDurationSeconds = failedCropSamples.size.toDouble() / SAMPLE_RATE.toDouble(),
            acousticFeatures = failedFeatures,
            bandEnergies = failedBands,
            temporalEnvelope = failedEnv,
            whisperResult = failedWhisper
        )

        logReport(goodProfile, failedProfile, cosDist, eucDist)

        FullComparisonReport(
            goodProfile = goodProfile,
            failedProfile = failedProfile,
            spectralCosineDistance = cosDist,
            spectralEuclideanDistance = eucDist
        )
    }

    private fun verifyFile(file: File, samples: ShortArray, fileName: String): FileVerification {
        val sha = calculateSha256(file)
        val n = samples.size
        val durS = n.toDouble() / SAMPLE_RATE.toDouble()
        val durMs = (durS * 1000.0).toLong()

        var sumSq = 0.0
        var peakAbs = 0
        var clipCount = 0L
        var zeroCount = 0L

        for (s in samples) {
            val v = s.toInt()
            val a = abs(v)
            if (a > peakAbs) peakAbs = a
            sumSq += v.toDouble() * v.toDouble()
            if (v == 0) zeroCount++
            if (s == Short.MIN_VALUE || s == Short.MAX_VALUE) clipCount++
        }

        val rms = if (n > 0) sqrt(sumSq / n) else 0.0
        val rmsDbfs = if (rms > 0.0) 20.0 * log10(rms / 32768.0) else -96.0
        val peakDbfs = if (peakAbs > 0) 20.0 * log10(peakAbs / 32768.0) else -96.0
        val crest = if (rms > 0.0) peakAbs.toDouble() / rms else 0.0
        val zeroPct = if (n > 0) (zeroCount.toDouble() / n) * 100.0 else 0.0

        return FileVerification(
            fileName = fileName,
            filePath = file.absolutePath,
            sha256 = sha,
            sampleRate = SAMPLE_RATE,
            channelCount = 1,
            sampleCount = n,
            durationMs = durMs,
            durationSeconds = durS,
            rms = rms,
            rmsDbfs = rmsDbfs,
            peak = peakAbs,
            peakDbfs = peakDbfs,
            crestFactor = crest,
            clippingCount = clipCount,
            zeroPercentage = zeroPct
        )
    }

    private fun detectSpeechSegments(context: Context, samples: ShortArray): VadSpeechRegion {
        val vadConfig = VadConfig(threshold = 0.5f)
        val vadEngine = SileroVadEngine.fromAsset(context, vadConfig)

        val frameSize = VadConfig.SILERO_V5_FRAME_SIZE
        val numFrames = samples.size / frameSize

        var firstSpeechFrame = -1
        var lastSpeechFrame = -1
        var inSpeech = false
        var segmentCount = 0
        var speechFrames = 0

        try {
            for (i in 0 until numFrames) {
                val frame = samples.copyOfRange(i * frameSize, (i + 1) * frameSize)
                val res = vadEngine.process(frame)
                if (res.probability >= 0.5f) {
                    if (firstSpeechFrame == -1) firstSpeechFrame = i
                    lastSpeechFrame = i
                    speechFrames++
                    if (!inSpeech) {
                        inSpeech = true
                        segmentCount++
                    }
                } else {
                    inSpeech = false
                }
            }
        } finally {
            vadEngine.close()
        }

        val startFrame = if (firstSpeechFrame >= 0) firstSpeechFrame else 0
        val endFrame = if (lastSpeechFrame >= 0) lastSpeechFrame + 1 else numFrames

        val startSample = startFrame * frameSize
        val endSample = minOf(samples.size, endFrame * frameSize)
        val speechDurationMs = ((endSample - startSample) * 1000L) / SAMPLE_RATE
        val totalSpeechMs = (speechFrames * frameSize * 1000L) / SAMPLE_RATE
        val totalCropFrames = endFrame - startFrame
        val silenceRatio = if (totalCropFrames > 0) (totalCropFrames - speechFrames).toDouble() / totalCropFrames.toDouble() else 0.0

        return VadSpeechRegion(
            startSample = startSample,
            endSample = endSample,
            startMs = (startSample * 1000L) / SAMPLE_RATE,
            endMs = (endSample * 1000L) / SAMPLE_RATE,
            durationMs = speechDurationMs,
            segmentCount = segmentCount,
            totalSpeechSamples = speechFrames * frameSize,
            totalSpeechDurationMs = totalSpeechMs,
            silenceRatio = silenceRatio
        )
    }

    private fun computeFrameFeatures(samples: ShortArray): FrameAcousticFeatures {
        val numFrames = samples.size / FRAME_SIZE
        val rmsList = mutableListOf<Double>()
        val peakList = mutableListOf<Double>()
        val crestList = mutableListOf<Double>()
        val zcrList = mutableListOf<Double>()
        val centroidList = mutableListOf<Double>()
        val rolloffList = mutableListOf<Double>()
        val bandwidthList = mutableListOf<Double>()
        val fluxList = mutableListOf<Double>()

        var prevSpectrum: DoubleArray? = null

        for (i in 0 until numFrames) {
            val frame = samples.copyOfRange(i * FRAME_SIZE, (i + 1) * FRAME_SIZE)

            // RMS & Peak
            var sumSq = 0.0
            var peakAbs = 0
            var zeroCross = 0
            for (j in 0 until FRAME_SIZE) {
                val v = frame[j].toInt()
                val a = abs(v)
                if (a > peakAbs) peakAbs = a
                sumSq += v.toDouble() * v.toDouble()
                if (j > 0 && ((frame[j] >= 0 && frame[j - 1] < 0) || (frame[j] < 0 && frame[j - 1] >= 0))) {
                    zeroCross++
                }
            }
            val rms = sqrt(sumSq / FRAME_SIZE)
            val rmsDbfs = if (rms > 0.0) 20.0 * log10(rms / 32768.0) else -96.0
            val peakDbfs = if (peakAbs > 0) 20.0 * log10(peakAbs / 32768.0) else -96.0
            val crest = if (rms > 0.0) peakAbs.toDouble() / rms else 0.0
            val zcr = zeroCross.toDouble() / (FRAME_SIZE - 1).toDouble()

            rmsList.add(rmsDbfs)
            peakList.add(peakDbfs)
            crestList.add(crest)
            zcrList.add(zcr)

            // Spectral Features via DFT
            val spectrum = computeMagnitudeSpectrum(frame)
            val numBins = spectrum.size
            val binWidthHz = (SAMPLE_RATE.toDouble() / 2.0) / numBins.toDouble()

            var totalMag = 0.0
            var weightedSum = 0.0
            for (k in 0 until numBins) {
                val f = k * binWidthHz
                val m = spectrum[k]
                totalMag += m
                weightedSum += f * m
            }

            val centroid = if (totalMag > 0.0) weightedSum / totalMag else 0.0
            centroidList.add(centroid)

            // Bandwidth
            var spreadSum = 0.0
            for (k in 0 until numBins) {
                val f = k * binWidthHz
                val diff = f - centroid
                spreadSum += diff * diff * spectrum[k]
            }
            val bandwidth = if (totalMag > 0.0) sqrt(spreadSum / totalMag) else 0.0
            bandwidthList.add(bandwidth)

            // Rolloff 85%
            var cumMag = 0.0
            val rolloffThreshold = totalMag * 0.85
            var rolloffHz = 0.0
            for (k in 0 until numBins) {
                cumMag += spectrum[k]
                if (cumMag >= rolloffThreshold) {
                    rolloffHz = k * binWidthHz
                    break
                }
            }
            rolloffList.add(rolloffHz)

            // Flux
            if (prevSpectrum != null) {
                var fluxSum = 0.0
                for (k in 0 until numBins) {
                    val diff = spectrum[k] - prevSpectrum[k]
                    fluxSum += diff * diff
                }
                fluxList.add(sqrt(fluxSum))
            } else {
                fluxList.add(0.0)
            }
            prevSpectrum = spectrum
        }

        return FrameAcousticFeatures(
            frameRmsDbfs = calcDist(rmsList),
            peakDbfs = calcDist(peakList),
            crestFactor = calcDist(crestList),
            zeroCrossingRate = calcDist(zcrList),
            spectralCentroidHz = calcDist(centroidList),
            spectralRolloffHz = calcDist(rolloffList),
            spectralBandwidthHz = calcDist(bandwidthList),
            spectralFlux = calcDist(fluxList)
        )
    }

    private fun computeFrequencyBands(samples: ShortArray): List<FrequencyBandEnergy> {
        val bands = listOf(
            Triple("0–250 Hz", 0.0, 250.0),
            Triple("250–500 Hz", 250.0, 500.0),
            Triple("500–1000 Hz", 500.0, 1000.0),
            Triple("1000–2000 Hz", 1000.0, 2000.0),
            Triple("2000–4000 Hz", 2000.0, 4000.0),
            Triple("4000–8000 Hz", 4000.0, 8000.0)
        )

        val numFrames = samples.size / FRAME_SIZE
        val bandEnergies = DoubleArray(bands.size)
        var totalEnergy = 0.0

        for (i in 0 until numFrames) {
            val frame = samples.copyOfRange(i * FRAME_SIZE, (i + 1) * FRAME_SIZE)
            val spectrum = computeMagnitudeSpectrum(frame)
            val numBins = spectrum.size
            val binWidthHz = (SAMPLE_RATE.toDouble() / 2.0) / numBins.toDouble()

            for (k in 0 until numBins) {
                val f = k * binWidthHz
                val magSq = spectrum[k] * spectrum[k]
                totalEnergy += magSq

                for (b in bands.indices) {
                    if (f >= bands[b].second && f < bands[b].third) {
                        bandEnergies[b] += magSq
                        break
                    }
                }
            }
        }

        return bands.mapIndexed { index, (name, low, high) ->
            val absE = bandEnergies[index]
            val pct = if (totalEnergy > 0.0) (absE / totalEnergy) * 100.0 else 0.0
            FrequencyBandEnergy(
                bandName = name,
                lowFreqHz = low,
                highFreqHz = high,
                absoluteEnergy = absE,
                percentageOfTotal = pct
            )
        }
    }

    private fun computeTemporalEnvelope(samples: ShortArray, vadRegion: VadSpeechRegion): TemporalEnvelope {
        val numFrames = samples.size / FRAME_SIZE
        val frameRms = DoubleArray(numFrames)
        var maxRmsDbfs = -96.0
        var minRmsDbfs = 0.0
        var lowEnergyGaps = 0
        var inGap = false

        for (i in 0 until numFrames) {
            var sumSq = 0.0
            for (j in 0 until FRAME_SIZE) {
                val s = samples[i * FRAME_SIZE + j].toDouble()
                sumSq += s * s
            }
            val rms = sqrt(sumSq / FRAME_SIZE)
            val db = if (rms > 0.0) 20.0 * log10(rms / 32768.0) else -96.0
            frameRms[i] = db
            if (db > maxRmsDbfs) maxRmsDbfs = db
            if (db < minRmsDbfs) minRmsDbfs = db

            // Low energy gap threshold = -50 dBFS
            if (db < -50.0) {
                if (!inGap) {
                    inGap = true
                    lowEnergyGaps++
                }
            } else {
                inGap = false
            }
        }

        val dynamicRange = maxRmsDbfs - minRmsDbfs
        val voicedRatio = 1.0 - vadRegion.silenceRatio
        val medianVoicedDur = if (vadRegion.segmentCount > 0) vadRegion.totalSpeechDurationMs.toDouble() / vadRegion.segmentCount.toDouble() else 0.0

        return TemporalEnvelope(
            silenceRatioInsideCrop = vadRegion.silenceRatio,
            voicedFrameRatio = voicedRatio,
            dynamicRangeDb = dynamicRange,
            lowEnergyGapCount = lowEnergyGaps,
            medianVoicedFrameDurationMs = medianVoicedDur
        )
    }

    private fun computeMagnitudeSpectrum(frame: ShortArray): DoubleArray {
        val n = FRAME_SIZE
        val half = n / 2
        val real = DoubleArray(n)
        val imag = DoubleArray(n)

        // Apply Hanning Window
        for (i in 0 until n) {
            val w = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            real[i] = (frame[i] / 32768.0) * w
            imag[i] = 0.0
        }

        // Radix-2 Cooley-Tukey In-Place FFT (n = 512)
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]; real[i] = real[j]; real[j] = tempR
                val tempI = imag[i]; imag[i] = imag[j]; imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val halfLen = len / 2
            val angle = -2.0 * PI / len
            val wStepR = cos(angle)
            val wStepI = sin(angle)
            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0
                for (k in 0 until halfLen) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + halfLen] * wR - imag[i + k + halfLen] * wI
                    val vI = real[i + k + halfLen] * wI + imag[i + k + halfLen] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + halfLen] = uR - vR
                    imag[i + k + halfLen] = uI - vI
                    val nextWR = wR * wStepR - wI * wStepI
                    wI = wR * wStepI + wI * wStepR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }

        val mag = DoubleArray(half)
        for (k in 0 until half) {
            mag[k] = sqrt(real[k] * real[k] + imag[k] * imag[k])
        }
        return mag
    }

    private fun computeSpectralDistance(
        bandsA: List<FrequencyBandEnergy>,
        bandsB: List<FrequencyBandEnergy>
    ): Pair<Double, Double> {
        require(bandsA.size == bandsB.size)
        val vecA = bandsA.map { it.percentageOfTotal }.toDoubleArray()
        val vecB = bandsB.map { it.percentageOfTotal }.toDoubleArray()

        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        var eucSq = 0.0

        for (i in vecA.indices) {
            dot += vecA[i] * vecB[i]
            normA += vecA[i] * vecA[i]
            normB += vecB[i] * vecB[i]
            val diff = vecA[i] - vecB[i]
            eucSq += diff * diff
        }

        val cosSim = if (normA > 0.0 && normB > 0.0) dot / (sqrt(normA) * sqrt(normB)) else 0.0
        val cosDist = 1.0 - cosSim
        val eucDist = sqrt(eucSq)

        return Pair(cosDist, eucDist)
    }

    private fun calcDist(values: List<Double>): FeatureDistribution {
        if (values.isEmpty()) return FeatureDistribution(0.0, 0.0, 0.0, 0.0, 0.0)
        val sorted = values.sorted()
        val n = sorted.size
        val mean = sorted.average()
        val median = sorted[n / 2]
        val p10 = sorted[(n * 0.10).toInt().coerceIn(0, n - 1)]
        val p90 = sorted[(n * 0.90).toInt().coerceIn(0, n - 1)]
        val varSum = sorted.sumOf { (it - mean) * (it - mean) }
        val stdDev = sqrt(varSum / n)
        return FeatureDistribution(mean, median, p10, p90, stdDev)
    }

    private fun readPcm16Wav(file: File): ShortArray {
        val bytes = file.readBytes()
        require(bytes.size >= 44) { "WAV file too small (${bytes.size} bytes)" }
        val pcmByteCount = bytes.size - 44
        val shortCount = pcmByteCount / 2
        val shorts = ShortArray(shortCount)
        val buf = ByteBuffer.wrap(bytes, 44, pcmByteCount).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until shortCount) {
            shorts[i] = buf.short
        }
        return shorts
    }

    private fun writePcm16Wav(file: File, samples: ShortArray, sampleRate: Int) {
        val totalAudioLen = samples.size.toLong() * 2L
        val totalDataLen = totalAudioLen + 36L
        val byteRate = sampleRate * 1 * 2

        val header = ByteBuffer.allocate(44).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            put("RIFF".toByteArray())
            putInt(totalDataLen.toInt())
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray())
            putInt(totalAudioLen.toInt())
        }

        FileOutputStream(file).use { fos ->
            fos.write(header.array())
            val pcmBytes = ByteBuffer.allocate(samples.size * 2).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                for (s in samples) {
                    putShort(s)
                }
            }
            fos.write(pcmBytes.array())
            fos.flush()
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun logReport(good: AudioComparisonProfile, failed: AudioComparisonProfile, cosDist: Double, eucDist: Double) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [ACOUSTIC DOMAIN COMPARISON REPORT] ===")
        Log.i(TAG, "GOOD:   ${good.label} -> RMS=${"%.2f".format(good.sourceVerification.rmsDbfs)}dBFS, WER=${"%.1f".format(good.whisperResult.wer * 100)}%")
        Log.i(TAG, "FAILED: ${failed.label} -> RMS=${"%.2f".format(failed.sourceVerification.rmsDbfs)}dBFS, WER=${"%.1f".format(failed.whisperResult.wer * 100)}%")
        Log.i(TAG, "Spectral Cosine Distance:    ${"%.4f".format(cosDist)}")
        Log.i(TAG, "Spectral Euclidean Distance: ${"%.4f".format(eucDist)}")
        Log.i(TAG, "WHISPER GOOD HYP:   \"${good.whisperResult.rawHypothesis}\"")
        Log.i(TAG, "WHISPER FAILED HYP: \"${failed.whisperResult.rawHypothesis}\"")
        Log.i(TAG, "=========================================================")
    }
}
