package org.itantra.core.audio.diagnostic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.itantra.core.audio.AudioCaptureConfig
import org.itantra.core.audio.AudioCaptureListener
import org.itantra.core.audio.AudioCaptureMetrics
import org.itantra.core.audio.AudioCaptureState
import org.itantra.core.audio.AudioRecordCapture
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.ArrayList

/**
 * Diagnostic runner for analyzing the integrity, amplitude, and signal characteristics
 * of raw audio captured from the physical device microphone via [AudioRecordCapture].
 */
object MicAudioIntegrityRunner {

    private const val TAG = "MIC_INTEGRITY_TEST"

    data class AudioSignalStats(
        val sampleCount: Long,
        val durationMs: Long,
        val sampleRate: Int,
        val channelCount: Int,
        val minSample: Short,
        val maxSample: Short,
        val rms: Double,
        val rmsDbfs: Double,
        val peakAbsoluteAmplitude: Int,
        val zeroSampleCount: Long,
        val zeroSamplePercentage: Double,
        val clippingMinCount: Long, // samples == -32768
        val clippingMaxCount: Long, // samples == 32767
        val wavFilePath: String,
        val wavFileSize: Long,
        val wavSha256: String
    )

    /**
     * Records audio from the physical microphone for [durationMs] and computes full signal statistics.
     */
    suspend fun recordAndAnalyze(
        context: Context,
        durationMs: Long = 7000L,
        outputFileName: String = "mic_integrity_test.wav"
    ): AudioSignalStats = withContext(Dispatchers.IO) {
        Log.i(TAG, "==================================================")
        Log.i(TAG, "STARTING RAW MICROPHONE INTEGRITY RECORDING ($durationMs ms)")
        Log.i(TAG, "Prompt: 'Hello from iTantra. This is a microphone recording integrity test.'")
        Log.i(TAG, "==================================================")

        val config = AudioCaptureConfig()
        val capture = AudioRecordCapture(config)
        val collectedChunks = ArrayList<ShortArray>()
        var totalSamplesCaptured = 0L

        val captureFinished = CompletableDeferred<Unit>()

        val listener = object : AudioCaptureListener {
            override fun onAudioFrame(audioData: ShortArray, frameSize: Int) {
                val copy = audioData.copyOf(frameSize)
                synchronized(collectedChunks) {
                    collectedChunks.add(copy)
                    totalSamplesCaptured += frameSize
                }
            }

            override fun onAudioTelemetry(metrics: AudioCaptureMetrics, rmsDbfs: Double) {}
            override fun onStateChanged(state: AudioCaptureState) {
                Log.i(TAG, "[CAPTURE-STATE] $state")
            }
            override fun onError(errorCode: Int, message: String) {
                Log.e(TAG, "[CAPTURE-ERROR] Code $errorCode: $message")
            }
        }

        val startRes = capture.start(listener)
        if (startRes.isFailure) {
            throw IllegalStateException("Failed to start AudioRecordCapture: ${startRes.exceptionOrNull()?.message}")
        }

        Log.i(TAG, "Recording live audio for $durationMs ms...")
        delay(durationMs)

        Log.i(TAG, "Stopping audio capture...")
        capture.stop()
        capture.release()

        // Flatten all chunks into a single contiguous ShortArray
        val totalCount = totalSamplesCaptured.toInt()
        val allSamples = ShortArray(totalCount)
        var offset = 0
        synchronized(collectedChunks) {
            for (chunk in collectedChunks) {
                System.arraycopy(chunk, 0, allSamples, offset, chunk.size)
                offset += chunk.size
            }
        }

        Log.i(TAG, "Captured $totalCount total samples (${allSamples.size * 2} bytes)")

        // 1. Calculate Signal Statistics
        var minSample: Short = 0
        var maxSample: Short = 0
        var peakAbs = 0
        var sumSquares = 0.0
        var zeroCount = 0L
        var clipMinCount = 0L
        var clipMaxCount = 0L

        for (s in allSamples) {
            if (s < minSample) minSample = s
            if (s > maxSample) maxSample = s
            val absVal = kotlin.math.abs(s.toInt())
            if (absVal > peakAbs) peakAbs = absVal
            sumSquares += s.toDouble() * s.toDouble()
            if (s.toInt() == 0) zeroCount++
            if (s == Short.MIN_VALUE) clipMinCount++
            if (s == Short.MAX_VALUE) clipMaxCount++
        }

        val meanSquare = if (totalCount > 0) sumSquares / totalCount else 0.0
        val rms = kotlin.math.sqrt(meanSquare)
        val rmsDbfs = if (rms > 0.0) 20.0 * kotlin.math.log10(rms / 32768.0) else -96.0
        val zeroPercentage = if (totalCount > 0) (zeroCount.toDouble() / totalCount) * 100.0 else 0.0
        val durationCalculatedMs = if (config.sampleRateInHz > 0) (totalCount * 1000L) / config.sampleRateInHz else 0L

        // 2. Write standard 16kHz mono 16-bit PCM WAV file
        val outputFile = File(context.filesDir, outputFileName)
        writePcmToWav(outputFile, allSamples, config.sampleRateInHz, 1)
        val wavFileSize = outputFile.length()
        val sha256 = calculateSha256(outputFile)

        Log.i(TAG, "==================================================")
        Log.i(TAG, "RAW MICROPHONE INTEGRITY RESULTS")
        Log.i(TAG, "  Sample Count: $totalCount")
        Log.i(TAG, "  Duration: ${durationCalculatedMs}ms")
        Log.i(TAG, "  Sample Rate: ${config.sampleRateInHz} Hz")
        Log.i(TAG, "  Channels: 1 (Mono)")
        Log.i(TAG, "  Min Sample: $minSample")
        Log.i(TAG, "  Max Sample: $maxSample")
        Log.i(TAG, "  Peak Absolute Amplitude: $peakAbs / 32767")
        Log.i(TAG, "  RMS Amplitude: ${"%.2f".format(rms)} (${"%.2f".format(rmsDbfs)} dBFS)")
        Log.i(TAG, "  Zero Samples: $zeroCount (${"%.2f".format(zeroPercentage)}%)")
        Log.i(TAG, "  Clipping Min (-32768): $clipMinCount")
        Log.i(TAG, "  Clipping Max (+32767): $clipMaxCount")
        Log.i(TAG, "  WAV File: ${outputFile.absolutePath} ($wavFileSize bytes)")
        Log.i(TAG, "  WAV SHA-256: $sha256")
        Log.i(TAG, "==================================================")

        AudioSignalStats(
            sampleCount = totalCount.toLong(),
            durationMs = durationCalculatedMs,
            sampleRate = config.sampleRateInHz,
            channelCount = 1,
            minSample = minSample,
            maxSample = maxSample,
            rms = rms,
            rmsDbfs = rmsDbfs,
            peakAbsoluteAmplitude = peakAbs,
            zeroSampleCount = zeroCount,
            zeroSamplePercentage = zeroPercentage,
            clippingMinCount = clipMinCount,
            clippingMaxCount = clipMaxCount,
            wavFilePath = outputFile.absolutePath,
            wavFileSize = wavFileSize,
            wavSha256 = sha256
        )
    }

    /**
     * Writes raw 16-bit linear PCM short samples to a canonical RIFF WAV file.
     */
    fun writePcmToWav(outputFile: File, samples: ShortArray, sampleRate: Int, channels: Int) {
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2
        val dataSize = samples.size * 2
        val totalSize = 36 + dataSize

        FileOutputStream(outputFile).use { fos ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            header.put("RIFF".toByteArray())
            header.putInt(totalSize)
            header.put("WAVE".toByteArray())

            // "fmt " sub-chunk
            header.put("fmt ".toByteArray())
            header.putInt(16) // SubChunk1Size (16 for PCM)
            header.putShort(1) // AudioFormat (1 for PCM)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(byteRate)
            header.putShort(blockAlign.toShort())
            header.putShort(16) // BitsPerSample

            // "data" sub-chunk
            header.put("data".toByteArray())
            header.putInt(dataSize)

            fos.write(header.array())

            // Write PCM data bytes (little-endian shorts)
            val pcmBytes = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (sample in samples) {
                pcmBytes.putShort(sample)
            }
            fos.write(pcmBytes.array())
            fos.flush()
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
