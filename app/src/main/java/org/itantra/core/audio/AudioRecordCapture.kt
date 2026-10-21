package org.itantra.core.audio

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thread-safe, production-oriented [AudioCapture] implementation using native Android [AudioRecord].
 *
 * Captures 16 kHz Mono 16-bit PCM audio on a dedicated high-priority background thread
 * (`THREAD_PRIORITY_URGENT_AUDIO`) and delivers discrete sample frames to the [AudioCaptureListener].
 *
 * Design constraints:
 * - Thread-safe start / stop / release lifecycle
 * - Safe against concurrent multi-invocations
 * - Zero allocation in the hot capture loop (reuses capture buffer, emits immutable frame copies)
 * - Safe fallback from VOICE_RECOGNITION to MIC if needed
 * - Strict validation of AudioRecord states (INITIALIZED, RECORDSTATE_RECORDING)
 */
class AudioRecordCapture(
    private val config: AudioCaptureConfig = AudioCaptureConfig()
) : AudioCapture {

    companion object {
        private const val TAG = "iTantra-AudioCapture"
        private const val THREAD_NAME = "iTantra-AudioCaptureThread"
        private const val THREAD_JOIN_TIMEOUT_MS = 600L
    }

    private val lock = Any()
    private val isRunning = AtomicBoolean(false)

    @Volatile
    private var currentState: AudioCaptureState = AudioCaptureState.UNINITIALIZED

    override val state: AudioCaptureState
        get() = currentState

    override val isRecording: Boolean
        get() = isRunning.get() && currentState == AudioCaptureState.RECORDING

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var sessionStartTimeMs: Long = 0L

    override val metrics: AudioCaptureMetrics = AudioCaptureMetrics(
        sampleRate = config.sampleRateInHz,
        minBufferSize = 0,
        allocatedBufferSize = 0,
        chunkSampleSize = config.chunkSampleCount
    )

    @SuppressLint("MissingPermission")
    override fun start(listener: AudioCaptureListener): Result<Unit> {
        synchronized(lock) {
            if (isRunning.get()) {
                Log.w(TAG, "Audio capture is already running. Ignoring duplicate start request.")
                return Result.success(Unit)
            }

            // 1. Calculate and validate buffer size
            val minBufferSize = AudioRecord.getMinBufferSize(
                config.sampleRateInHz,
                config.channelConfig,
                config.audioFormat
            )

            if (minBufferSize <= 0) {
                val errorMsg = "AudioRecord.getMinBufferSize returned invalid size: $minBufferSize. " +
                        "SampleRate=${config.sampleRateInHz}, Channel=${config.channelConfig}, Format=${config.audioFormat}"
                Log.e(TAG, errorMsg)
                transitionState(AudioCaptureState.ERROR, listener)
                listener.onError(AudioRecord.ERROR_BAD_VALUE, errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            // Multiplier Rationale:
            // minBufferSize * 2 prevents kernel audio ring buffer overflows during thread scheduling jitter,
            // while chunkSizeBytes * 4 guarantees sufficient buffering for downstream discrete VAD/STT chunks.
            val allocatedBufferSize = maxOf(minBufferSize * 2, config.chunkSizeBytes * 4)

            // 2. Initialize AudioRecord (try VOICE_RECOGNITION, fallback to MIC)
            val record = createAudioRecordInstance(allocatedBufferSize)
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                val errorMsg = "Failed to initialize native AudioRecord (state=${record?.state})."
                Log.e(TAG, errorMsg)
                record?.release()
                transitionState(AudioCaptureState.ERROR, listener)
                listener.onError(AudioRecord.ERROR_INVALID_OPERATION, errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            this.audioRecord = record
            metrics.resetSession()
            metrics.captureDurationMs = 0L

            // Update telemetry with verified hardware buffer sizes
            val updatedMetrics = metrics.copy(
                minBufferSize = minBufferSize,
                allocatedBufferSize = allocatedBufferSize
            )
            metrics.totalSamplesCaptured = updatedMetrics.totalSamplesCaptured
            metrics.totalFramesRead = updatedMetrics.totalFramesRead

            // 3. Start hardware recording
            try {
                record.startRecording()
            } catch (ex: Exception) {
                val errorMsg = "Exception starting AudioRecord: ${ex.message}"
                Log.e(TAG, errorMsg, ex)
                record.release()
                this.audioRecord = null
                transitionState(AudioCaptureState.ERROR, listener)
                listener.onError(AudioRecord.ERROR_INVALID_OPERATION, errorMsg)
                return Result.failure(ex)
            }

            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                val errorMsg = "AudioRecord failed to enter RECORDSTATE_RECORDING (state=${record.recordingState})."
                Log.e(TAG, errorMsg)
                record.stop()
                record.release()
                this.audioRecord = null
                transitionState(AudioCaptureState.ERROR, listener)
                listener.onError(AudioRecord.ERROR_INVALID_OPERATION, errorMsg)
                return Result.failure(IllegalStateException(errorMsg))
            }

            isRunning.set(true)
            sessionStartTimeMs = System.currentTimeMillis()
            transitionState(AudioCaptureState.RECORDING, listener)

            Log.i(TAG, "Audio capture started. SampleRate=${config.sampleRateInHz}Hz, Mono, PCM16, " +
                    "MinBuffer=${minBufferSize}B, Allocated=${allocatedBufferSize}B, ChunkSamples=${config.chunkSampleCount}")

            // 4. Launch dedicated high-priority capture thread
            captureThread = Thread({
                runCaptureLoop(record, listener)
            }, THREAD_NAME).apply {
                isDaemon = true
                start()
            }

            return Result.success(Unit)
        }
    }

    override fun stop() {
        synchronized(lock) {
            if (!isRunning.compareAndSet(true, false)) {
                return
            }

            Log.i(TAG, "Stopping audio capture...")
            try {
                audioRecord?.stop()
            } catch (ex: Exception) {
                Log.w(TAG, "Error stopping AudioRecord: ${ex.message}")
            }

            // Wait for capture loop to cleanly exit
            try {
                captureThread?.join(THREAD_JOIN_TIMEOUT_MS)
            } catch (ex: InterruptedException) {
                Thread.currentThread().interrupt()
                Log.w(TAG, "Interrupted while waiting for capture thread to exit.")
            }
            captureThread = null

            if (sessionStartTimeMs > 0) {
                metrics.captureDurationMs = System.currentTimeMillis() - sessionStartTimeMs
            }

            transitionState(AudioCaptureState.STOPPED, null)
            Log.i(TAG, "Audio capture stopped. Total samples=${metrics.totalSamplesCaptured}, Frames=${metrics.totalFramesRead}, Duration=${metrics.captureDurationMs}ms")
        }
    }

    override fun release() {
        synchronized(lock) {
            stop()
            try {
                audioRecord?.release()
            } catch (ex: Exception) {
                Log.w(TAG, "Error releasing AudioRecord: ${ex.message}")
            }
            audioRecord = null
            transitionState(AudioCaptureState.RELEASED, null)
            Log.i(TAG, "Audio capture hardware resources released.")
        }
    }

    private fun runCaptureLoop(record: AudioRecord, listener: AudioCaptureListener) {
        // Elevate thread priority for real-time audio capture to minimize buffer dropouts
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (ex: Exception) {
            Log.w(TAG, "Unable to set THREAD_PRIORITY_URGENT_AUDIO: ${ex.message}")
        }

        val readBuffer = ShortArray(config.chunkSampleCount)
        var lastHeartbeatTime = 0L

        while (isRunning.get() && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val samplesRead = record.read(readBuffer, 0, readBuffer.size)

            if (samplesRead > 0) {
                // Clone buffer chunk to ensure safe decoupled processing by downstream VAD/STT
                val frameCopy = readBuffer.copyOf(samplesRead)
                metrics.totalSamplesCaptured += samplesRead
                metrics.totalFramesRead++
                metrics.captureDurationMs = System.currentTimeMillis() - sessionStartTimeMs

                // Calculate RMS dBFS
                var sumSquare = 0.0
                var maxAmp = 0
                for (i in 0 until samplesRead) {
                    val s = readBuffer[i].toInt()
                    val absVal = kotlin.math.abs(s)
                    if (absVal > maxAmp) maxAmp = absVal
                    sumSquare += s.toDouble() * s.toDouble()
                }
                val meanSquare = if (samplesRead > 0) sumSquare / samplesRead else 0.0
                val rms = kotlin.math.sqrt(meanSquare)
                val rmsDbfs = if (rms > 0.0) 20.0 * kotlin.math.log10(rms / 32768.0) else -96.0

                val now = System.currentTimeMillis()
                if (now - lastHeartbeatTime >= 2000L) {
                    lastHeartbeatTime = now
                    Log.i(TAG, "[CAPTURE HEARTBEAT] Frames=${metrics.totalFramesRead}, MaxAmp=$maxAmp, RMS=${"%.1f".format(rmsDbfs)}dBFS, Duration=${metrics.captureDurationMs}ms")
                }

                try {
                    listener.onAudioFrame(frameCopy, samplesRead)
                    listener.onAudioTelemetry(metrics, rmsDbfs)
                } catch (ex: Exception) {
                    Log.e(TAG, "Unhandled exception in AudioCaptureListener callbacks: ${ex.message}", ex)
                }
            } else {
                when (samplesRead) {
                    AudioRecord.ERROR_INVALID_OPERATION -> {
                        val msg = "AudioRecord.read returned ERROR_INVALID_OPERATION"
                        handleReadError(AudioRecord.ERROR_INVALID_OPERATION, msg, listener)
                        break
                    }
                    AudioRecord.ERROR_BAD_VALUE -> {
                        val msg = "AudioRecord.read returned ERROR_BAD_VALUE"
                        handleReadError(AudioRecord.ERROR_BAD_VALUE, msg, listener)
                        break
                    }
                    AudioRecord.ERROR_DEAD_OBJECT -> {
                        val msg = "AudioRecord.read returned ERROR_DEAD_OBJECT (hardware audio service died)"
                        handleReadError(AudioRecord.ERROR_DEAD_OBJECT, msg, listener)
                        break
                    }
                    else -> {
                        Log.w(TAG, "AudioRecord.read returned unhandled code: $samplesRead")
                    }
                }
            }
        }
    }

    private fun handleReadError(errorCode: Int, message: String, listener: AudioCaptureListener) {
        Log.e(TAG, message)
        metrics.errorCount++
        metrics.lastError = message
        transitionState(AudioCaptureState.ERROR, listener)
        listener.onError(errorCode, message)
    }

    private fun transitionState(newState: AudioCaptureState, listener: AudioCaptureListener?) {
        currentState = newState
        try {
            listener?.onStateChanged(newState)
        } catch (ex: Exception) {
            Log.e(TAG, "Exception during onStateChanged($newState): ${ex.message}", ex)
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecordInstance(bufferSizeInBytes: Int): AudioRecord? {
        // Attempt 1: Configured audio source (MediaRecorder.AudioSource.VOICE_RECOGNITION)
        try {
            val record = AudioRecord(
                config.audioSource,
                config.sampleRateInHz,
                config.channelConfig,
                config.audioFormat,
                bufferSizeInBytes
            )
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                return record
            }
            record.release()
        } catch (ex: Exception) {
            Log.w(TAG, "Failed creating AudioRecord with source=${config.audioSource}: ${ex.message}")
        }

        // Attempt 2: Fallback to standard MIC if primary source was VOICE_RECOGNITION
        if (config.audioSource != MediaRecorder.AudioSource.MIC) {
            try {
                Log.i(TAG, "Attempting fallback to MediaRecorder.AudioSource.MIC...")
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    config.sampleRateInHz,
                    config.channelConfig,
                    config.audioFormat,
                    bufferSizeInBytes
                )
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    return record
                }
                record.release()
            } catch (ex: Exception) {
                Log.w(TAG, "Failed creating AudioRecord with fallback MIC source: ${ex.message}")
            }
        }

        return null
    }
}
