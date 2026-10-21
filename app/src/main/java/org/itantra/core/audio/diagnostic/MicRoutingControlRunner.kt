package org.itantra.core.audio.diagnostic

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.itantra.core.audio.AudioCaptureConfig
import org.itantra.core.audio.AudioCaptureListener
import org.itantra.core.audio.AudioCaptureMetrics
import org.itantra.core.audio.AudioCaptureState
import org.itantra.core.audio.AudioRecordCapture
import org.itantra.core.stt.SherpaWhisperAsrEngine
import org.itantra.core.stt.benchmark.IndicWerCalculator
import org.itantra.core.stt.benchmark.TextNormalizer
import org.itantra.core.stt.benchmark.WerCalculator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Diagnostic runner for Task M1-MIC-SOURCE-CONTROL-02.
 *
 * Captures audio routing and platform configuration snapshot before executing
 * fixed 8.000s (128,000 samples) raw continuous microphone captures across
 * VOICE_RECOGNITION and standard MIC sources without VAD or preprocessing.
 */
object MicRoutingControlRunner {

    private const val TAG = "MIC_ROUTING_CONTROL"

    const val EXACT_SAMPLE_COUNT = 128000 // Exactly 8.000 seconds at 16 kHz
    const val SAMPLE_RATE = 16000

    const val ENGLISH_REFERENCE = "The quick brown fox jumps over the lazy dog."
    const val HINDI_REFERENCE = "नमस्ते, यह iTantra का हिंदी परीक्षण है।"

    data class AudioRoutingSnapshot(
        val deviceManufacturer: String,
        val deviceModel: String,
        val deviceName: String,
        val sdkInt: Int,
        val audioRecordSource: String,
        val audioRecordSourceInt: Int,
        val sampleRate: Int,
        val channelConfig: String,
        val audioFormat: String,
        val audioMode: String,
        val isMicrophoneMute: Boolean,
        val isBluetoothScoOn: Boolean,
        val isBluetoothA2dpOn: Boolean,
        val isSpeakerphoneOn: Boolean,
        val communicationDevice: String?,
        val inputDevices: List<String>,
        val wiredHeadsetConnected: Boolean,
        val bluetoothHeadsetConnected: Boolean
    )

    data class RawCaptureMetrics(
        val testLabel: String,
        val outputFileName: String,
        val wavFilePath: String,
        val wavFileSize: Long,
        val sha256: String,
        val sampleRate: Int,
        val channelCount: Int,
        val bitDepth: Int,
        val sampleCount: Int,
        val durationMs: Long,
        val durationSeconds: Double,
        val minSample: Short,
        val maxSample: Short,
        val peakAbsoluteSample: Int,
        val peakDbfs: Double,
        val rms: Double,
        val rmsDbfs: Double,
        val crestFactor: Double,
        val crestFactorDb: Double,
        val clippingCount: Long,
        val clippingPercentage: Double,
        val zeroCount: Long,
        val zeroPercentage: Double
    )

    data class AsrEvaluationResult(
        val language: String,
        val referenceText: String,
        val rawHypothesis: String,
        val normalizedHypothesis: String,
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

    data class RoutingControlTestResult(
        val routingSnapshot: AudioRoutingSnapshot,
        val metrics: RawCaptureMetrics,
        val asrResult: AsrEvaluationResult?
    )

    fun captureRoutingSnapshot(context: Context, audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION): AudioRoutingSnapshot {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val modeStr = when (am.mode) {
            AudioManager.MODE_NORMAL -> "MODE_NORMAL"
            AudioManager.MODE_RINGTONE -> "MODE_RINGTONE"
            AudioManager.MODE_IN_CALL -> "MODE_IN_CALL"
            AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION"
            AudioManager.MODE_CALL_SCREENING -> "MODE_CALL_SCREENING"
            else -> "MODE_UNKNOWN (${am.mode})"
        }

        val sourceStr = when (audioSource) {
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "VOICE_RECOGNITION ($audioSource)"
            MediaRecorder.AudioSource.MIC -> "MIC ($audioSource)"
            MediaRecorder.AudioSource.DEFAULT -> "DEFAULT ($audioSource)"
            MediaRecorder.AudioSource.CAMCORDER -> "CAMCORDER ($audioSource)"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "VOICE_COMMUNICATION ($audioSource)"
            MediaRecorder.AudioSource.UNPROCESSED -> "UNPROCESSED ($audioSource)"
            else -> "SOURCE_$audioSource"
        }

        val commDeviceStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                am.communicationDevice?.let { dev ->
                    "Type ${dev.type} (${getDeviceTypeName(dev.type)}): ${dev.productName}"
                } ?: "None (default system routing)"
            } catch (e: Throwable) {
                "Error querying: ${e.message}"
            }
        } else {
            "API < 31 (Not available)"
        }

        val inputDeviceList = ArrayList<String>()
        var wiredHeadset = false
        var btHeadset = false

        try {
            val devices = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            for (d in devices) {
                val typeName = getDeviceTypeName(d.type)
                inputDeviceList.add("Type ${d.type} ($typeName): ${d.productName}")
                if (d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) {
                    wiredHeadset = true
                }
                if (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    btHeadset = true
                }
            }
        } catch (e: Throwable) {
            inputDeviceList.add("Error listing devices: ${e.message}")
        }

        return AudioRoutingSnapshot(
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            deviceName = Build.DEVICE,
            sdkInt = Build.VERSION.SDK_INT,
            audioRecordSource = sourceStr,
            audioRecordSourceInt = audioSource,
            sampleRate = SAMPLE_RATE,
            channelConfig = "AudioFormat.CHANNEL_IN_MONO (1 ch)",
            audioFormat = "AudioFormat.ENCODING_PCM_16BIT (16-bit Linear PCM)",
            audioMode = modeStr,
            isMicrophoneMute = am.isMicrophoneMute,
            isBluetoothScoOn = am.isBluetoothScoOn,
            isBluetoothA2dpOn = am.isBluetoothA2dpOn,
            isSpeakerphoneOn = am.isSpeakerphoneOn,
            communicationDevice = commDeviceStr,
            inputDevices = inputDeviceList,
            wiredHeadsetConnected = wiredHeadset,
            bluetoothHeadsetConnected = btHeadset
        )
    }

    private fun getDeviceTypeName(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "TYPE_BUILTIN_EARPIECE"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "TYPE_BUILTIN_SPEAKER"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "TYPE_WIRED_HEADSET"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "TYPE_WIRED_HEADPHONES"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "TYPE_BLUETOOTH_SCO"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "TYPE_BLUETOOTH_A2DP"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "TYPE_BUILTIN_MIC"
            AudioDeviceInfo.TYPE_FM -> "TYPE_FM"
            AudioDeviceInfo.TYPE_AUX_LINE -> "TYPE_AUX_LINE"
            AudioDeviceInfo.TYPE_IP -> "TYPE_IP"
            AudioDeviceInfo.TYPE_BUS -> "TYPE_BUS"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "TYPE_USB_DEVICE"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "TYPE_USB_ACCESSORY"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "TYPE_USB_HEADSET"
            AudioDeviceInfo.TYPE_HEARING_AID -> "TYPE_HEARING_AID"
            else -> "TYPE_UNKNOWN ($type)"
        }
    }

    suspend fun captureAndEvaluate(
        context: Context,
        testLabel: String,
        outputFileName: String,
        referenceText: String,
        language: String,
        audioSource: Int = MediaRecorder.AudioSource.VOICE_RECOGNITION
    ): RoutingControlTestResult = withContext(Dispatchers.IO) {
        val routingSnapshot = captureRoutingSnapshot(context, audioSource)
        logRoutingSnapshot(routingSnapshot)

        Log.i(TAG, "=========================================================")
        Log.i(TAG, "STARTING RAW 8-SECOND CAPTURE: $testLabel ($outputFileName)")
        Log.i(TAG, "Source: ${routingSnapshot.audioRecordSource}")
        Log.i(TAG, "Target: 128,000 samples @ 16kHz Mono PCM16 (8.000 s)")
        Log.i(TAG, "Reference: \"$referenceText\"")
        Log.i(TAG, "Language: \"$language\"")
        Log.i(TAG, "NO VAD, NO TRIMMING, NO PREPROCESSING")
        Log.i(TAG, "=========================================================")

        val audioConfig = AudioCaptureConfig(audioSource = audioSource)
        val audioCapture = AudioRecordCapture(audioConfig)

        val assembledSamples = ShortArray(EXACT_SAMPLE_COUNT)
        var samplesCollected = 0
        val captureFinished = CompletableDeferred<Unit>()

        val captureListener = object : AudioCaptureListener {
            override fun onAudioFrame(buffer: ShortArray, readCount: Int) {
                if (readCount <= 0 || captureFinished.isCompleted) return

                synchronized(assembledSamples) {
                    val remaining = EXACT_SAMPLE_COUNT - samplesCollected
                    if (remaining > 0) {
                        val toCopy = minOf(readCount, remaining)
                        System.arraycopy(buffer, 0, assembledSamples, samplesCollected, toCopy)
                        samplesCollected += toCopy

                        if (samplesCollected >= EXACT_SAMPLE_COUNT && !captureFinished.isCompleted) {
                            Log.i(TAG, "[$testLabel] Captured exact target: $samplesCollected samples")
                            captureFinished.complete(Unit)
                        }
                    }
                }
            }

            override fun onAudioTelemetry(metrics: AudioCaptureMetrics, rmsDbfs: Double) {}
            override fun onStateChanged(state: AudioCaptureState) {
                Log.i(TAG, "[$testLabel] AudioRecord state: $state")
            }
            override fun onError(errorCode: Int, message: String) {
                Log.e(TAG, "[$testLabel] AudioRecord error: code=$errorCode, msg=$message")
            }
        }

        val startRes = audioCapture.start(captureListener)
        if (startRes.isFailure) {
            throw IllegalStateException("Failed to start AudioRecord ($audioSource): ${startRes.exceptionOrNull()?.message}")
        }

        Log.i(TAG, "[$testLabel] AudioRecord started. Capturing exact 8.000 seconds...")

        val completed = withTimeoutOrNull(12000L) {
            captureFinished.await()
        }

        Log.i(TAG, "[$testLabel] Stopping AudioRecord...")
        audioCapture.stop()
        audioCapture.release()

        val finalSamples = if (samplesCollected == EXACT_SAMPLE_COUNT) {
            assembledSamples
        } else {
            Log.w(TAG, "[$testLabel] Collected $samplesCollected / $EXACT_SAMPLE_COUNT samples")
            assembledSamples.copyOf(samplesCollected)
        }

        require(finalSamples.isNotEmpty()) { "Captured audio is empty" }

        val outputFile = File(context.filesDir, outputFileName)
        writePcm16Wav(outputFile, finalSamples, audioConfig.sampleRateInHz)
        val wavFileSize = outputFile.length()
        val sha256 = calculateSha256(outputFile)

        Log.i(TAG, "[$testLabel] Saved WAV to ${outputFile.absolutePath} ($wavFileSize bytes, SHA-256: $sha256)")

        val metrics = computeSignalMetrics(
            samples = finalSamples,
            sampleRate = audioConfig.sampleRateInHz,
            testLabel = testLabel,
            outputFileName = outputFileName,
            wavFilePath = outputFile.absolutePath,
            wavFileSize = wavFileSize,
            sha256 = sha256
        )

        val asrResult = runWhisperEvaluation(
            context = context,
            samples = finalSamples,
            sampleRate = audioConfig.sampleRateInHz,
            referenceText = referenceText,
            language = language
        )

        val result = RoutingControlTestResult(
            routingSnapshot = routingSnapshot,
            metrics = metrics,
            asrResult = asrResult
        )

        logResult(result)
        result
    }

    fun computeSignalMetrics(
        samples: ShortArray,
        sampleRate: Int,
        testLabel: String,
        outputFileName: String,
        wavFilePath: String,
        wavFileSize: Long,
        sha256: String
    ): RawCaptureMetrics {
        val n = samples.size
        val durationS = if (sampleRate > 0) n.toDouble() / sampleRate.toDouble() else 0.0
        val durationMs = (durationS * 1000.0).toLong()

        var minSample: Short = 0
        var maxSample: Short = 0
        var peakAbs = 0
        var sumSquares = 0.0
        var zeroCount = 0L
        var clipCount = 0L

        for (s in samples) {
            val v = s.toInt()
            if (s < minSample) minSample = s
            if (s > maxSample) maxSample = s
            val absVal = abs(v)
            if (absVal > peakAbs) peakAbs = absVal
            sumSquares += v.toDouble() * v.toDouble()

            if (v == 0) zeroCount++
            if (s == Short.MIN_VALUE || s == Short.MAX_VALUE) clipCount++
        }

        val rms = if (n > 0) sqrt(sumSquares / n) else 0.0
        val rmsDbfs = if (rms > 0.0) 20.0 * log10(rms / 32768.0) else -96.0
        val peakDbfs = if (peakAbs > 0) 20.0 * log10(peakAbs / 32768.0) else -96.0
        val crestFactor = if (rms > 0.0) peakAbs.toDouble() / rms else 0.0
        val crestFactorDb = peakDbfs - rmsDbfs

        val clipPct = if (n > 0) (clipCount.toDouble() / n) * 100.0 else 0.0
        val zeroPct = if (n > 0) (zeroCount.toDouble() / n) * 100.0 else 0.0

        return RawCaptureMetrics(
            testLabel = testLabel,
            outputFileName = outputFileName,
            wavFilePath = wavFilePath,
            wavFileSize = wavFileSize,
            sha256 = sha256,
            sampleRate = sampleRate,
            channelCount = 1,
            bitDepth = 16,
            sampleCount = n,
            durationMs = durationMs,
            durationSeconds = durationS,
            minSample = minSample,
            maxSample = maxSample,
            peakAbsoluteSample = peakAbs,
            peakDbfs = peakDbfs,
            rms = rms,
            rmsDbfs = rmsDbfs,
            crestFactor = crestFactor,
            crestFactorDb = crestFactorDb,
            clippingCount = clipCount,
            clippingPercentage = clipPct,
            zeroCount = zeroCount,
            zeroPercentage = zeroPct
        )
    }

    private suspend fun runWhisperEvaluation(
        context: Context,
        samples: ShortArray,
        sampleRate: Int,
        referenceText: String,
        language: String
    ): AsrEvaluationResult? {
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

            AsrEvaluationResult(
                language = language,
                referenceText = referenceText,
                rawHypothesis = asrRes.text,
                normalizedHypothesis = normHyp,
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
            Log.e(TAG, "Whisper evaluation failed: ${e.message}", e)
            null
        } finally {
            engine?.close()
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

    private fun logRoutingSnapshot(s: AudioRoutingSnapshot) {
        Log.i(TAG, "================ [AUDIO ROUTING SNAPSHOT] ================")
        Log.i(TAG, "Device: ${s.deviceManufacturer} ${s.deviceModel} (${s.deviceName}), SDK: ${s.sdkInt}")
        Log.i(TAG, "Source: ${s.audioRecordSource}")
        Log.i(TAG, "Audio Mode: ${s.audioMode}")
        Log.i(TAG, "Mic Mute: ${s.isMicrophoneMute}, BT SCO: ${s.isBluetoothScoOn}, BT A2DP: ${s.isBluetoothA2dpOn}, Speakerphone: ${s.isSpeakerphoneOn}")
        Log.i(TAG, "Comm Device: ${s.communicationDevice}")
        Log.i(TAG, "Input Devices (${s.inputDevices.size}):")
        for (dev in s.inputDevices) {
            Log.i(TAG, "  - $dev")
        }
        Log.i(TAG, "Wired Headset: ${s.wiredHeadsetConnected}, BT Headset: ${s.bluetoothHeadsetConnected}")
        Log.i(TAG, "==========================================================")
    }

    private fun logResult(r: RoutingControlTestResult) {
        val m = r.metrics
        val a = r.asrResult
        Log.i(TAG, "=========================================================")
        Log.i(TAG, "=== [ROUTING CONTROL RESULT: ${m.testLabel}] ===")
        Log.i(TAG, "FILE: ${m.outputFileName} (${m.sampleCount} samples, ${m.durationMs}ms, ${m.durationSeconds}s)")
        Log.i(TAG, "SHA-256: ${m.sha256}")
        Log.i(TAG, "SIGNAL: RMS=${"%.2f".format(m.rmsDbfs)} dBFS, Peak=${m.peakAbsoluteSample} (${"%.2f".format(m.peakDbfs)} dBFS), CrestFactor=${"%.2f".format(m.crestFactor)} (${"%.2f".format(m.crestFactorDb)} dB)")
        Log.i(TAG, "STATS: Zero=${"%.2f".format(m.zeroPercentage)}%, Clip=${"%.3f".format(m.clippingPercentage)}%, Min=${m.minSample}, Max=${m.maxSample}")
        if (a != null) {
            Log.i(TAG, "ASR (${a.language}): WER=${"%.1f".format(a.wer * 100)}%, CER=${"%.1f".format(a.cer * 100)}%, Time=${a.inferenceTimeMs}ms (RTF ${"%.3f".format(a.rtf)})")
            Log.i(TAG, "RAW HYP: \"${a.rawHypothesis}\"")
        }
        Log.i(TAG, "=========================================================")
    }
}
