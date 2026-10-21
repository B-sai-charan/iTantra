package org.itantra.core.audio.diagnostic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.itantra.core.audio.AudioCaptureConfig
import org.itantra.core.audio.AudioCaptureListener
import org.itantra.core.audio.AudioCaptureMetrics
import org.itantra.core.audio.AudioCaptureState
import org.itantra.core.audio.AudioRecordCapture
import org.itantra.core.pipeline.SpeechSegmentCollector
import org.itantra.core.stt.AsrSmokeTestRunner
import org.itantra.core.stt.SherpaWhisperAsrEngine
import org.itantra.core.stt.benchmark.IndicWerCalculator
import org.itantra.core.stt.benchmark.TextNormalizer
import org.itantra.core.stt.benchmark.WerCalculator
import org.itantra.core.vad.SileroVadEngine
import org.itantra.core.vad.VadConfig
import org.itantra.core.vad.VadFrameAdapter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.*

/**
 * Diagnostic runner for TASK: M1-MIC-ACOUSTIC-CHARACTERIZATION-01.
 *
 * Performs comprehensive acoustic characterization of raw physical microphone audio
 * captured via the production [AudioRecordCapture] path for:
 * 1. Test A: Controlled English speech ("The quick brown fox jumps over the lazy dog.")
 * 2. Test B: Controlled Hindi speech ("नमस्ते, यह iTantra का हिंदी परीक्षण है।")
 * 3. Test C: Acoustic playback control (Prerecorded Hindi reference played through speaker and re-captured)
 */
object MicAcousticCharacterizationRunner {

    private const val TAG = "MIC_ACOUSTIC_CHAR"

    const val ENGLISH_REFERENCE = "The quick brown fox jumps over the lazy dog."
    const val HINDI_REFERENCE = "नमस्ते, यह iTantra का हिंदी परीक्षण है।"

    data class SignalStatistics(
        val sampleCount: Int,
        val durationMs: Long,
        val sampleRate: Int,
        val channelCount: Int,
        val bitDepth: Int,
        val minSample: Short,
        val maxSample: Short,
        val mean: Double,
        val standardDeviation: Double,
        val rms: Double,
        val rmsDbfs: Double,
        val peakAbsolute: Int,
        val peakDbfs: Double,
        val crestFactor: Double,
        val crestFactorDbfs: Double,
        val clippingCount: Long,
        val clippingPercentage: Double,
        val zeroCount: Long,
        val zeroPercentage: Double,
        val pctAboveMinus40Dbfs: Double, // |s| >= 328
        val pctAboveMinus30Dbfs: Double, // |s| >= 1036
        val pctAboveMinus20Dbfs: Double, // |s| >= 3277
        val pctAboveMinus10Dbfs: Double  // |s| >= 10363
    )

    data class SpectralCharacterization(
        val fftSize: Int,
        val spectralCentroidHz: Double,
        val dominantFrequencyHz: Double,
        val dominantFrequencyRange: String,
        val lowBandEnergyPct: Double,  // 0 - 300 Hz
        val midBandEnergyPct: Double,  // 300 - 3400 Hz
        val highBandEnergyPct: Double  // 3400 - 8000 Hz
    )

    data class AsrEvaluation(
        val language: String,
        val rawHypothesis: String,
        val normalizedHypothesis: String,
        val reference: String,
        val normalizedReference: String,
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int,
        val hits: Int,
        val referenceTokenCount: Int,
        val wer: Double,
        val cer: Double,
        val isExactMatch: Boolean,
        val inferenceTimeMs: Long,
        val rtf: Double
    )

    data class CharacterizationCaptureResult(
        val testLabel: String,
        val outputFileName: String,
        val wavFilePath: String,
        val wavFileSize: Long,
        val sha256: String,
        val signalStats: SignalStatistics,
        val spectralStats: SpectralCharacterization,
        val asrResult: AsrEvaluation?
    )

    data class AcousticCharacterizationReport(
        val deviceModel: String,
        val testA_English: CharacterizationCaptureResult?,
        val testB_Hindi: CharacterizationCaptureResult?,
        val testC_PlaybackHindi: CharacterizationCaptureResult?
    )

    /**
     * Captures live microphone audio using production [AudioRecordCapture] + [SileroVadEngine] + [SpeechSegmentCollector]
     * or fixed recording, computes signal & spectral stats, runs Whisper transcription, and saves WAV.
     */
    suspend fun captureAndAnalyze(
        context: Context,
        testLabel: String,
        outputFileName: String,
        referenceText: String,
        language: String,
        maxDurationMs: Long = 8000L,
        playbackAssetBeforeCapture: String? = null
    ): CharacterizationCaptureResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "STARTING CAPTURE: $testLabel ($outputFileName)")
        Log.i(TAG, "Reference: \"$referenceText\"")
        Log.i(TAG, "Language: \"$language\"")
        Log.i(TAG, "=========================================================")

        val audioConfig = AudioCaptureConfig()
        val vadConfig = VadConfig()
        val audioCapture = AudioRecordCapture(audioConfig)
        val vadEngine = SileroVadEngine.fromAsset(context, vadConfig)
        val vadAdapter = VadFrameAdapter(vadEngine)

        val segmentDeferred = CompletableDeferred<ShortArray>()
        val rawCollectedChunks = ArrayList<ShortArray>()
        var totalRawSamples = 0

        val segmentCollector = SpeechSegmentCollector(
            sampleRate = audioConfig.sampleRateInHz,
            minSpeechDurationMs = vadConfig.minSpeechDurationMs,
            onSegmentReady = { audio, durationMs, _, segmentIndex, _, _ ->
                Log.i(TAG, "[$testLabel] Segment ready: idx=$segmentIndex, samples=${audio.size}, duration=${durationMs}ms")
                if (!segmentDeferred.isCompleted) {
                    segmentDeferred.complete(audio)
                }
            }
        )

        val captureListener = object : AudioCaptureListener {
            override fun onAudioFrame(buffer: ShortArray, readCount: Int) {
                val copy = buffer.copyOf(readCount)
                synchronized(rawCollectedChunks) {
                    rawCollectedChunks.add(copy)
                    totalRawSamples += readCount
                }
                val vadResults = vadAdapter.process(buffer, readCount)
                segmentCollector.onAudioData(buffer, readCount, vadResults, System.currentTimeMillis())
            }

            override fun onAudioTelemetry(metrics: AudioCaptureMetrics, rmsDbfs: Double) {}
            override fun onStateChanged(state: AudioCaptureState) {
                Log.i(TAG, "[$testLabel] Capture state: $state")
            }
            override fun onError(errorCode: Int, message: String) {
                Log.e(TAG, "[$testLabel] Capture error: code=$errorCode, msg=$message")
            }
        }

        val startRes = audioCapture.start(captureListener)
        if (startRes.isFailure) {
            throw IllegalStateException("Failed to start audio capture: ${startRes.exceptionOrNull()?.message}")
        }

        // If playback control is requested (Test C), play reference WAV out of speaker
        if (playbackAssetBeforeCapture != null) {
            delay(300) // Brief delay to ensure mic recording is active
            Log.i(TAG, "[$testLabel] Playing acoustic playback fixture: $playbackAssetBeforeCapture")
            playAssetAudio(context, playbackAssetBeforeCapture)
            delay(500)
        }

        Log.i(TAG, "[$testLabel] Waiting for speech capture (timeout=${maxDurationMs}ms)...")
        val vadSamples = withTimeoutOrNull(maxDurationMs) {
            segmentDeferred.await()
        }

        Log.i(TAG, "[$testLabel] Stopping audio capture...")
        audioCapture.stop()
        audioCapture.release()

        // If VAD captured a discrete speech segment, use that; otherwise use the full raw stream
        val finalSamples: ShortArray = if (vadSamples != null && vadSamples.isNotEmpty()) {
            Log.i(TAG, "[$testLabel] Using VAD-segmented audio (${vadSamples.size} samples)")
            vadSamples
        } else {
            Log.w(TAG, "[$testLabel] No discrete VAD segment completed; assembling $totalRawSamples raw samples")
            val assembled = ShortArray(totalRawSamples)
            var offset = 0
            synchronized(rawCollectedChunks) {
                for (chunk in rawCollectedChunks) {
                    System.arraycopy(chunk, 0, assembled, offset, chunk.size)
                    offset += chunk.size
                }
            }
            assembled
        }

        require(finalSamples.isNotEmpty()) { "Captured audio is empty" }

        // 1. Save standard 16kHz mono PCM16 WAV
        val outputFile = File(context.filesDir, outputFileName)
        writePcm16Wav(outputFile, finalSamples, audioConfig.sampleRateInHz)
        val wavFileSize = outputFile.length()
        val sha256 = calculateSha256(outputFile)

        Log.i(TAG, "[$testLabel] Saved WAV to ${outputFile.absolutePath} ($wavFileSize bytes, SHA-256: $sha256)")

        // 2. Compute Signal Statistics
        val signalStats = computeSignalStats(finalSamples, audioConfig.sampleRateInHz)

        // 3. Compute Spectral Statistics
        val spectralStats = computeSpectralStats(finalSamples, audioConfig.sampleRateInHz)

        // 4. Run Direct Whisper ASR
        val asrEval = runWhisperTranscribe(context, finalSamples, audioConfig.sampleRateInHz, referenceText, language)

        val result = CharacterizationCaptureResult(
            testLabel = testLabel,
            outputFileName = outputFileName,
            wavFilePath = outputFile.absolutePath,
            wavFileSize = wavFileSize,
            sha256 = sha256,
            signalStats = signalStats,
            spectralStats = spectralStats,
            asrResult = asrEval
        )

        logResult(result)
        result
    }

    /**
     * Executes the complete 3-test acoustic characterization suite sequentially.
     */
    suspend fun runFullCharacterizationSuite(
        context: Context
    ): AcousticCharacterizationReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "STARTING FULL ACOUSTIC CHARACTERIZATION SUITE (A, B, C)")
        Log.i(TAG, "=========================================================")

        // Test A: English Speech
        Log.i(TAG, ">>> [TEST A] Please speak ENGLISH: \"$ENGLISH_REFERENCE\" <<<")
        val resA = try {
            captureAndAnalyze(
                context = context,
                testLabel = "Test A — English",
                outputFileName = "mic_characterization_en.wav",
                referenceText = ENGLISH_REFERENCE,
                language = "en",
                maxDurationMs = 8000L
            )
        } catch (e: Exception) {
            Log.e(TAG, "[TEST A] Error: ${e.message}", e)
            null
        }

        delay(1500)

        // Test B: Hindi Speech
        Log.i(TAG, ">>> [TEST B] Please speak HINDI: \"$HINDI_REFERENCE\" <<<")
        val resB = try {
            captureAndAnalyze(
                context = context,
                testLabel = "Test B — Hindi",
                outputFileName = "mic_characterization_hi.wav",
                referenceText = HINDI_REFERENCE,
                language = "hi",
                maxDurationMs = 8000L
            )
        } catch (e: Exception) {
            Log.e(TAG, "[TEST B] Error: ${e.message}", e)
            null
        }

        delay(1500)

        // Test C: Acoustic Playback Control
        Log.i(TAG, ">>> [TEST C] Playing Hindi fixture through speaker into microphone <<<")
        val resC = try {
            captureAndAnalyze(
                context = context,
                testLabel = "Test C — Playback Hindi",
                outputFileName = "mic_characterization_playback_hi.wav",
                referenceText = "कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है",
                language = "hi",
                maxDurationMs = 12000L,
                playbackAssetBeforeCapture = "speech_test_hi_001.wav"
            )
        } catch (e: Exception) {
            Log.e(TAG, "[TEST C] Error: ${e.message}", e)
            null
        }

        val report = AcousticCharacterizationReport(
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            testA_English = resA,
            testB_Hindi = resB,
            testC_PlaybackHindi = resC
        )

        Log.i(TAG, "=========================================================")
        Log.i(TAG, "ACOUSTIC CHARACTERIZATION SUITE COMPLETE")
        Log.i(TAG, "=========================================================")
        report
    }

    fun computeSignalStats(samples: ShortArray, sampleRate: Int): SignalStatistics {
        val sampleCount = samples.size
        val durationMs = if (sampleRate > 0) (sampleCount * 1000L) / sampleRate else 0L

        var minSample: Short = 0
        var maxSample: Short = 0
        var peakAbs = 0
        var sumSamples = 0.0
        var sumSquares = 0.0
        var zeroCount = 0L
        var clipCount = 0L

        var countAboveMinus40 = 0L // |s| >= 328
        var countAboveMinus30 = 0L // |s| >= 1036
        var countAboveMinus20 = 0L // |s| >= 3277
        var countAboveMinus10 = 0L // |s| >= 10363

        for (s in samples) {
            val v = s.toInt()
            if (s < minSample) minSample = s
            if (s > maxSample) maxSample = s
            val absVal = abs(v)
            if (absVal > peakAbs) peakAbs = absVal
            sumSamples += v.toDouble()
            sumSquares += v.toDouble() * v.toDouble()

            if (v == 0) zeroCount++
            if (s == Short.MIN_VALUE || s == Short.MAX_VALUE) clipCount++

            if (absVal >= 328) countAboveMinus40++
            if (absVal >= 1036) countAboveMinus30++
            if (absVal >= 3277) countAboveMinus20++
            if (absVal >= 10363) countAboveMinus10++
        }

        val mean = if (sampleCount > 0) sumSamples / sampleCount else 0.0
        val variance = if (sampleCount > 0) {
            var sumVar = 0.0
            for (s in samples) {
                val diff = s.toDouble() - mean
                sumVar += diff * diff
            }
            sumVar / sampleCount
        } else 0.0
        val stdDev = sqrt(variance)

        val meanSquare = if (sampleCount > 0) sumSquares / sampleCount else 0.0
        val rms = sqrt(meanSquare)
        val rmsDbfs = if (rms > 0.0) 20.0 * log10(rms / 32768.0) else -96.0
        val peakDbfs = if (peakAbs > 0) 20.0 * log10(peakAbs / 32768.0) else -96.0
        val crestFactor = if (rms > 0.0) peakAbs.toDouble() / rms else 0.0
        val crestFactorDbfs = peakDbfs - rmsDbfs

        val clipPct = if (sampleCount > 0) (clipCount.toDouble() / sampleCount) * 100.0 else 0.0
        val zeroPct = if (sampleCount > 0) (zeroCount.toDouble() / sampleCount) * 100.0 else 0.0

        val pctMinus40 = if (sampleCount > 0) (countAboveMinus40.toDouble() / sampleCount) * 100.0 else 0.0
        val pctMinus30 = if (sampleCount > 0) (countAboveMinus30.toDouble() / sampleCount) * 100.0 else 0.0
        val pctMinus20 = if (sampleCount > 0) (countAboveMinus20.toDouble() / sampleCount) * 100.0 else 0.0
        val pctMinus10 = if (sampleCount > 0) (countAboveMinus10.toDouble() / sampleCount) * 100.0 else 0.0

        return SignalStatistics(
            sampleCount = sampleCount,
            durationMs = durationMs,
            sampleRate = sampleRate,
            channelCount = 1,
            bitDepth = 16,
            minSample = minSample,
            maxSample = maxSample,
            mean = mean,
            standardDeviation = stdDev,
            rms = rms,
            rmsDbfs = rmsDbfs,
            peakAbsolute = peakAbs,
            peakDbfs = peakDbfs,
            crestFactor = crestFactor,
            crestFactorDbfs = crestFactorDbfs,
            clippingCount = clipCount,
            clippingPercentage = clipPct,
            zeroCount = zeroCount,
            zeroPercentage = zeroPct,
            pctAboveMinus40Dbfs = pctMinus40,
            pctAboveMinus30Dbfs = pctMinus30,
            pctAboveMinus20Dbfs = pctMinus20,
            pctAboveMinus10Dbfs = pctMinus10
        )
    }

    fun computeSpectralStats(samples: ShortArray, sampleRate: Int): SpectralCharacterization {
        val fftSize = 512
        val hopSize = 256
        val binWidth = sampleRate.toDouble() / fftSize.toDouble() // 31.25 Hz

        val numFrames = (samples.size - fftSize) / hopSize
        if (numFrames <= 0) {
            return SpectralCharacterization(
                fftSize = fftSize,
                spectralCentroidHz = 0.0,
                dominantFrequencyHz = 0.0,
                dominantFrequencyRange = "N/A",
                lowBandEnergyPct = 0.0,
                midBandEnergyPct = 0.0,
                highBandEnergyPct = 0.0
            )
        }

        // Hanning window
        val window = DoubleArray(fftSize) { i ->
            0.5 * (1.0 - cos(2.0 * PI * i.toDouble() / (fftSize - 1).toDouble()))
        }

        val totalBinEnergies = DoubleArray(fftSize / 2 + 1)
        var sumCentroids = 0.0
        var activeFrameCount = 0

        var totalLowBandEnergy = 0.0  // 0 - 300 Hz (bins 0 to 9)
        var totalMidBandEnergy = 0.0  // 300 - 3400 Hz (bins 10 to 108)
        var totalHighBandEnergy = 0.0 // 3400 - 8000 Hz (bins 109 to 256)
        var totalSpectralEnergy = 0.0

        val real = DoubleArray(fftSize)
        val imag = DoubleArray(fftSize)

        for (f in 0 until numFrames) {
            val startIdx = f * hopSize
            var frameSumSq = 0.0
            for (i in 0 until fftSize) {
                val s = samples[startIdx + i].toDouble()
                real[i] = s * window[i]
                imag[i] = 0.0
                frameSumSq += s * s
            }

            val frameRms = sqrt(frameSumSq / fftSize)
            // Process active speech frames (above floor threshold)
            if (frameRms < 10.0) continue

            // Compute in-place Cooley-Tukey Radix-2 FFT
            fft(real, imag)

            var frameWeightedFreq = 0.0
            var frameMagSum = 0.0

            for (k in 0..fftSize / 2) {
                val mag = sqrt(real[k] * real[k] + imag[k] * imag[k])
                val energy = mag * mag
                val freq = k * binWidth

                totalBinEnergies[k] += energy
                totalSpectralEnergy += energy

                when {
                    freq < 300.0 -> totalLowBandEnergy += energy
                    freq < 3400.0 -> totalMidBandEnergy += energy
                    else -> totalHighBandEnergy += energy
                }

                frameWeightedFreq += freq * mag
                frameMagSum += mag
            }

            if (frameMagSum > 0.0) {
                sumCentroids += (frameWeightedFreq / frameMagSum)
                activeFrameCount++
            }
        }

        val avgCentroid = if (activeFrameCount > 0) sumCentroids / activeFrameCount else 0.0

        // Find peak dominant frequency bin
        var maxBin = 0
        var maxEnergy = 0.0
        for (k in 1..fftSize / 2) { // Skip DC bin 0
            if (totalBinEnergies[k] > maxEnergy) {
                maxEnergy = totalBinEnergies[k]
                maxBin = k
            }
        }

        val domFreq = maxBin * binWidth
        val domLow = max(0.0, (maxBin - 1) * binWidth)
        val domHigh = (maxBin + 1) * binWidth
        val domRange = "${domLow.toInt()} - ${domHigh.toInt()} Hz"

        val lowPct = if (totalSpectralEnergy > 0.0) (totalLowBandEnergy / totalSpectralEnergy) * 100.0 else 0.0
        val midPct = if (totalSpectralEnergy > 0.0) (totalMidBandEnergy / totalSpectralEnergy) * 100.0 else 0.0
        val highPct = if (totalSpectralEnergy > 0.0) (totalHighBandEnergy / totalSpectralEnergy) * 100.0 else 0.0

        return SpectralCharacterization(
            fftSize = fftSize,
            spectralCentroidHz = avgCentroid,
            dominantFrequencyHz = domFreq,
            dominantFrequencyRange = domRange,
            lowBandEnergyPct = lowPct,
            midBandEnergyPct = midPct,
            highBandEnergyPct = highPct
        )
    }

    private fun fft(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
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

        var l = 2
        while (l <= n) {
            val angle = -2.0 * PI / l
            val wstepR = cos(angle)
            val wstepI = sin(angle)
            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0
                for (m in 0 until l / 2) {
                    val idx1 = i + m
                    val idx2 = idx1 + l / 2
                    val tr = wR * real[idx2] - wI * imag[idx2]
                    val ti = wR * imag[idx2] + wI * real[idx2]
                    real[idx2] = real[idx1] - tr
                    imag[idx2] = imag[idx1] - ti
                    real[idx1] += tr
                    imag[idx1] += ti
                    val nextWR = wR * wstepR - wI * wstepI
                    wI = wR * wstepI + wI * wstepR
                    wR = nextWR
                }
                i += l
            }
            l = l shl 1
        }
    }

    private suspend fun runWhisperTranscribe(
        context: Context,
        samples: ShortArray,
        sampleRate: Int,
        referenceText: String,
        language: String
    ): AsrEvaluation? {
        var engine: SherpaWhisperAsrEngine? = null
        return try {
            engine = SherpaWhisperAsrEngine(context = context, defaultLanguage = language)
            val asrRes = engine.transcribe(samples, sampleRate, language)

            val (normRef, normHyp, werRes) = if (language == "hi") {
                val r = IndicWerCalculator.calculate(referenceText, asrRes.text, normalize = true)
                Triple(r.normalizedReference, r.normalizedHypothesis, r)
            } else {
                val nr = TextNormalizer.normalize(referenceText)
                val nh = TextNormalizer.normalize(asrRes.text)
                val r = WerCalculator.calculate(nr, nh)
                Triple(nr, nh, r)
            }

            val durationMs = if (sampleRate > 0) (samples.size * 1000L) / sampleRate else 0L
            val rtf = if (durationMs > 0) asrRes.inferenceTimeMs.toDouble() / durationMs.toDouble() else 0.0

            AsrEvaluation(
                language = language,
                rawHypothesis = asrRes.text,
                normalizedHypothesis = normHyp,
                reference = referenceText,
                normalizedReference = normRef,
                substitutions = werRes.wordStats.substitutions,
                deletions = werRes.wordStats.deletions,
                insertions = werRes.wordStats.insertions,
                hits = werRes.wordStats.hits,
                referenceTokenCount = werRes.wordStats.referenceCount,
                wer = werRes.wordStats.errorRate,
                cer = werRes.charStats.errorRate,
                isExactMatch = werRes.isExactMatch,
                inferenceTimeMs = asrRes.inferenceTimeMs,
                rtf = rtf
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Whisper transcription failed: ${e.message}", e)
            null
        } finally {
            engine?.close()
        }
    }

    private fun playAssetAudio(context: Context, assetPath: String) {
        val audioData = AsrSmokeTestRunner.loadWavFromAsset(context, assetPath) ?: return
        val bufferSize = AudioTrack.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(16000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(max(bufferSize, audioData.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            track.write(audioData, 0, audioData.size)
            track.play()
            val durationMs = (audioData.size * 1000L) / 16000L
            Thread.sleep(durationMs + 300)
        } catch (e: Exception) {
            Log.e(TAG, "Error during acoustic playback: ${e.message}", e)
        } finally {
            try {
                track.stop()
                track.release()
            } catch (e: Exception) {}
        }
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

    private fun logResult(r: CharacterizationCaptureResult) {
        val s = r.signalStats
        val sp = r.spectralStats
        val a = r.asrResult
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [RESULT: ${r.testLabel}] ===")
        Log.i(TAG, "FILE: ${r.outputFileName} (${s.sampleCount} samples, ${s.durationMs}ms, SHA-256: ${r.sha256})")
        Log.i(TAG, "SIGNAL: RMS=${"%.2f".format(s.rmsDbfs)} dBFS, Peak=${s.peakAbsolute} (${"%.2f".format(s.peakDbfs)} dBFS), CrestFactor=${"%.2f".format(s.crestFactor)} (${"%.2f".format(s.crestFactorDbfs)} dB)")
        Log.i(TAG, "DISTRIBUTION: >-40dBFS=${"%.1f".format(s.pctAboveMinus40Dbfs)}%, >-30dBFS=${"%.1f".format(s.pctAboveMinus30Dbfs)}%, >-20dBFS=${"%.1f".format(s.pctAboveMinus20Dbfs)}%, >-10dBFS=${"%.1f".format(s.pctAboveMinus10Dbfs)}%")
        Log.i(TAG, "STATS: Mean=${"%.2f".format(s.mean)}, StdDev=${"%.2f".format(s.standardDeviation)}, Zero=${"%.2f".format(s.zeroPercentage)}%, Clip=${"%.3f".format(s.clippingPercentage)}%")
        Log.i(TAG, "SPECTRAL: Centroid=${"%.1f".format(sp.spectralCentroidHz)} Hz, DomFreq=${"%.1f".format(sp.dominantFrequencyHz)} Hz (${sp.dominantFrequencyRange})")
        Log.i(TAG, "BANDS: Low(0-300Hz)=${"%.1f".format(sp.lowBandEnergyPct)}%, Mid(300-3400Hz)=${"%.1f".format(sp.midBandEnergyPct)}%, High(3400-8000Hz)=${"%.1f".format(sp.highBandEnergyPct)}%")
        if (a != null) {
            Log.i(TAG, "ASR (${a.language}): WER=${"%.1f".format(a.wer * 100)}%, CER=${"%.1f".format(a.cer * 100)}%, Time=${a.inferenceTimeMs}ms (RTF ${"%.3f".format(a.rtf)})")
            Log.i(TAG, "RAW HYP: \"${a.rawHypothesis}\"")
        }
        Log.i(TAG, "=========================================================")
    }
}
