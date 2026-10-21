package org.itantra.core.audio.diagnostic.benchmark

/**
 * Message priority level defined by the M2 communication layer / benchmark categorization.
 *
 * EMERGENCY messages indicate elevated urgency (tactical alerts, SOS, flood warnings).
 */
enum class MessagePriority {
    NORMAL,
    EMERGENCY
}

/**
 * Single benchmark test item with operational domain metadata and priority tagging.
 */
data class BenchmarkSentence(
    val id: Int,
    val text: String,
    val domain: String,
    val priority: MessagePriority = MessagePriority.NORMAL
) {
    val charCount: Int = text.length
}

/**
 * Detailed hardware measurement record for a single synthesized sentence.
 */
data class SentenceBenchmarkRecord(
    val sentenceId: Int,
    val text: String,
    val charCount: Int,
    val t0RequestReceivedMs: Long,
    val t1SynthesisStartedMs: Long,
    val t2AudioAvailableMs: Long,
    val t3PlaybackStartedMs: Long,
    val t4PlaybackCompletedMs: Long,
    val audioDurationMs: Long,
    val sampleRate: Int = 22050,
    val processMemoryMb: Float? = null
) {
    /**
     * T2 - T1: Neural synthesis latency (time taken by ONNX model to generate PCM samples).
     */
    val synthesisLatencyMs: Long
        get() = (t2AudioAvailableMs - t1SynthesisStartedMs).coerceAtLeast(0)

    /**
     * T3 - T0: Time to playback start (from message arrival to speaker output).
     */
    val playbackStartLatencyMs: Long
        get() = (t3PlaybackStartedMs - t0RequestReceivedMs).coerceAtLeast(0)

    /**
     * T4 - T0: Total end-to-end completion time.
     */
    val totalCompletionLatencyMs: Long
        get() = (t4PlaybackCompletedMs - t0RequestReceivedMs).coerceAtLeast(0)

    /**
     * Real-Time Factor (RTF) = synthesisLatency / audioDuration.
     * RTF < 1.0 means faster than real-time.
     */
    val realTimeFactor: Float?
        get() = if (audioDurationMs > 0) {
            synthesisLatencyMs.toFloat() / audioDurationMs.toFloat()
        } else null
}

/**
 * Comprehensive device hardware and engine metadata.
 */
data class DeviceHardwareInfo(
    val deviceModel: String,
    val manufacturer: String,
    val androidVersion: String,
    val apiLevel: Int,
    val cpuArchitecture: String,
    val modelName: String = "vits-piper-hi_IN-rohan-medium",
    val modelSizeBytes: Long = 62946428L,
    val sherpaThreads: Int = 2,
    val sampleRateHz: Int = 22050,
    val isNetworkConnected: Boolean,
    val networkType: String,
    val processRamMb: Float?,
    val cpuUtilization: String = NOT_MEASURED
) {
    val modelSizeMb: Float = modelSizeBytes.toFloat() / (1024f * 1024f)

    companion object {
        const val NOT_MEASURED = "NOT MEASURED"
    }
}

/**
 * Aggregated summary statistics across completed benchmark runs.
 */
data class BenchmarkSummaryStats(
    val totalSentences: Int,
    val avgSynthesisLatencyMs: Long,
    val minSynthesisLatencyMs: Long,
    val maxSynthesisLatencyMs: Long,
    val avgPlaybackStartLatencyMs: Long,
    val avgTotalLatencyMs: Long,
    val avgRealTimeFactor: Float,
    val totalAudioDurationMs: Long,
    val avgProcessRamMb: Float?
)

/**
 * TTS Benchmarking Instrumentation Container.
 *
 * Timestamps (millisecond precision):
 * - T0: TTS request received
 * - T1: TTS processing / normalization started
 * - T2: Audio buffer became available (synthesis complete)
 * - T3: Audio playback started (speaker output begins)
 * - T4: Audio playback completed
 *
 * Calculations:
 * - TTS processing time = T2 - T1
 * - Playback start latency = T3 - T0
 * - Total pipeline time = T4 - T0
 *
 * Rule:
 * NEVER invent benchmark values. If something has not been measured,
 * formatValue() displays "Not measured yet".
 */
data class TtsBenchmarkMetrics(
    val t0RequestReceivedMs: Long? = null,
    val t1ProcessingStartedMs: Long? = null,
    val t2AudioAvailableMs: Long? = null,
    val t3PlaybackStartedMs: Long? = null,
    val t4PlaybackCompletedMs: Long? = null,
    val activeEngineName: String = "None"
) {
    /**
     * TTS processing duration (T2 - T1) in milliseconds.
     */
    val ttsProcessingTimeMs: Long?
        get() = if (t1ProcessingStartedMs != null && t2AudioAvailableMs != null) {
            (t2AudioAvailableMs - t1ProcessingStartedMs).coerceAtLeast(0)
        } else null

    /**
     * Playback start latency (T3 - T0) in milliseconds.
     */
    val playbackStartLatencyMs: Long?
        get() = if (t0RequestReceivedMs != null && t3PlaybackStartedMs != null) {
            (t3PlaybackStartedMs - t0RequestReceivedMs).coerceAtLeast(0)
        } else null

    /**
     * Total end-to-end latency (T4 - T0) in milliseconds.
     */
    val totalPipelineTimeMs: Long?
        get() = if (t0RequestReceivedMs != null && t4PlaybackCompletedMs != null) {
            (t4PlaybackCompletedMs - t0RequestReceivedMs).coerceAtLeast(0)
        } else null

    /**
     * Formats a millisecond duration or returns "Not measured yet".
     */
    fun formatDuration(duration: Long?): String {
        return if (duration != null) "${duration} ms" else NOT_MEASURED
    }

    /**
     * Formats a raw timestamp or returns "Not measured yet".
     */
    fun formatTimestamp(timestamp: Long?): String {
        return if (timestamp != null) "${timestamp} ms" else NOT_MEASURED
    }

    companion object {
        const val NOT_MEASURED = "Not measured yet"
        const val TARGET_PROCESSING_LATENCY = "Target: < 250 ms"
        const val TARGET_PLAYBACK_START_LATENCY = "Target: < 300 ms"

        fun empty(): TtsBenchmarkMetrics = TtsBenchmarkMetrics()
    }
}
