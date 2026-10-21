package org.itantra.core.audio.diagnostic.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtsBenchmarkMetricsTest {

    @Test
    fun testUnmeasuredMetricsReportNullAndNotMeasuredYet() {
        val metrics = TtsBenchmarkMetrics.empty()

        assertNull("TTS processing time must be null before measurement", metrics.ttsProcessingTimeMs)
        assertNull("Playback start latency must be null before measurement", metrics.playbackStartLatencyMs)
        assertNull("Total pipeline time must be null before measurement", metrics.totalPipelineTimeMs)

        assertEquals("Not measured yet", metrics.formatDuration(metrics.ttsProcessingTimeMs))
        assertEquals("Not measured yet", metrics.formatDuration(metrics.playbackStartLatencyMs))
        assertEquals("Not measured yet", metrics.formatTimestamp(metrics.t0RequestReceivedMs))
    }

    @Test
    fun testAccurateLatencyCalculationsWhenTimestampsAreRecorded() {
        // T0 = 1000 ms, T1 = 1020 ms, T2 = 1180 ms, T3 = 1210 ms, T4 = 2500 ms
        val metrics = TtsBenchmarkMetrics(
            t0RequestReceivedMs = 1000L,
            t1ProcessingStartedMs = 1020L,
            t2AudioAvailableMs = 1180L,
            t3PlaybackStartedMs = 1210L,
            t4PlaybackCompletedMs = 2500L,
            activeEngineName = "Android System TTS (Offline)"
        )

        // TTS processing time = T2 - T1 = 1180 - 1020 = 160 ms
        assertEquals(160L, metrics.ttsProcessingTimeMs)
        assertEquals("160 ms", metrics.formatDuration(metrics.ttsProcessingTimeMs))

        // Playback start latency = T3 - T0 = 1210 - 1000 = 210 ms
        assertEquals(210L, metrics.playbackStartLatencyMs)
        assertEquals("210 ms", metrics.formatDuration(metrics.playbackStartLatencyMs))

        // Total pipeline time = T4 - T0 = 2500 - 1000 = 1500 ms
        assertEquals(1500L, metrics.totalPipelineTimeMs)
        assertEquals("1500 ms", metrics.formatDuration(metrics.totalPipelineTimeMs))
    }
}
