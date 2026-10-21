package org.itantra.core.audio.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Unit tests for [RawMicCaptureControlRunner].
 */
class RawMicCaptureControlRunnerTest {

    @Test
    fun testExactSampleCountConstant() {
        assertEquals(128000, RawMicCaptureControlRunner.EXACT_SAMPLE_COUNT)
        assertEquals(16000, RawMicCaptureControlRunner.SAMPLE_RATE)
        assertEquals(8.0, RawMicCaptureControlRunner.EXACT_SAMPLE_COUNT.toDouble() / RawMicCaptureControlRunner.SAMPLE_RATE.toDouble(), 1e-6)
    }

    @Test
    fun testSignalMetricsMath() {
        val sampleRate = 16000
        val sampleCount = 128000 // 8 seconds
        val samples = ShortArray(sampleCount)

        // 1 kHz sine wave with peak amplitude 16384 (-6.02 dBFS)
        for (i in 0 until sampleCount) {
            val s = (16384.0 * sin(2.0 * Math.PI * 1000.0 * i / sampleRate)).toInt().toShort()
            samples[i] = s
        }

        val metrics = RawMicCaptureControlRunner.computeSignalMetrics(
            samples = samples,
            sampleRate = sampleRate,
            testLabel = "Test Sine",
            outputFileName = "test_sine.wav",
            wavFilePath = "/tmp/test_sine.wav",
            wavFileSize = 256044L,
            sha256 = "DETERMINISTIC"
        )

        assertEquals(128000, metrics.sampleCount)
        assertEquals(8000L, metrics.durationMs)
        assertEquals(8.0, metrics.durationSeconds, 1e-4)
        assertEquals(16000, metrics.sampleRate)
        assertEquals(1, metrics.channelCount)
        assertEquals(16, metrics.bitDepth)

        assertEquals(-9.03, metrics.rmsDbfs, 0.2)
        assertEquals(-6.02, metrics.peakDbfs, 0.2)
        assertEquals(0L, metrics.clippingCount)
        assertEquals(0.0, metrics.clippingPercentage, 1e-4)

        assertTrue(metrics.pctAboveMinus10Dbfs > 0.0)
        assertTrue(metrics.pctAboveMinus20Dbfs > 0.0)
        assertTrue(metrics.pctAboveMinus30Dbfs > 0.0)
        assertTrue(metrics.pctAboveMinus40Dbfs > 0.0)
    }

    @Test
    fun testReferences() {
        assertEquals("The quick brown fox jumps over the lazy dog.", RawMicCaptureControlRunner.ENGLISH_REFERENCE)
        assertEquals("नमस्ते, यह iTantra का हिंदी परीक्षण है।", RawMicCaptureControlRunner.HINDI_REFERENCE)
    }
}
