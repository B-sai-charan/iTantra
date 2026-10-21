package org.itantra.core.audio.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

/**
 * Unit tests for signal and spectral calculation math in MicAcousticCharacterizationRunner.
 */
class MicAcousticCharacterizationRunnerTest {

    @Test
    fun testSignalStatisticsCalculations() {
        // Test with known deterministic samples
        val sampleRate = 16000
        val sampleCount = 16000 // 1 second
        val samples = ShortArray(sampleCount)

        // Generate 1 kHz sine wave with peak 16384 (-6.02 dBFS)
        for (i in 0 until sampleCount) {
            val s = (16384.0 * sin(2.0 * Math.PI * 1000.0 * i / sampleRate)).toInt().toShort()
            samples[i] = s
        }

        val stats = MicAcousticCharacterizationRunner.computeSignalStats(samples, sampleRate)

        assertEquals(sampleCount, stats.sampleCount)
        assertEquals(1000L, stats.durationMs)
        assertEquals(16000, stats.sampleRate)
        assertEquals(1, stats.channelCount)
        assertEquals(16, stats.bitDepth)

        // For sine wave with peak A, RMS = A / sqrt(2) = 16384 / 1.4142 = 11585
        // RMS dBFS = 20 * log10(11585 / 32768) = -9.03 dBFS
        assertEquals(-9.03, stats.rmsDbfs, 0.2)
        assertEquals(-6.02, stats.peakDbfs, 0.2)
        assertEquals(0L, stats.clippingCount)
        assertEquals(0.0, stats.clippingPercentage, 1e-4)

        // Sine wave peak is > -10 dBFS
        assertTrue(stats.pctAboveMinus10Dbfs > 0.0)
        assertTrue(stats.pctAboveMinus20Dbfs > 0.0)
        assertTrue(stats.pctAboveMinus30Dbfs > 0.0)
        assertTrue(stats.pctAboveMinus40Dbfs > 0.0)
    }

    @Test
    fun testSpectralStatsCalculations() {
        val sampleRate = 16000
        val sampleCount = 16000
        val samples = ShortArray(sampleCount)

        // Generate 1000 Hz tone (mid-band)
        for (i in 0 until sampleCount) {
            val s = (10000.0 * sin(2.0 * Math.PI * 1000.0 * i / sampleRate)).toInt().toShort()
            samples[i] = s
        }

        val spectral = MicAcousticCharacterizationRunner.computeSpectralStats(samples, sampleRate)

        // Dominant frequency should be close to 1000 Hz (31.25 Hz bin resolution)
        assertEquals(1000.0, spectral.dominantFrequencyHz, 50.0)
        // Mid-band (300-3400 Hz) energy should dominate (> 90%)
        assertTrue(spectral.midBandEnergyPct > 90.0)
        assertTrue(spectral.lowBandEnergyPct < 10.0)
        assertTrue(spectral.highBandEnergyPct < 10.0)
    }

    @Test
    fun testReferences() {
        assertEquals("The quick brown fox jumps over the lazy dog.", MicAcousticCharacterizationRunner.ENGLISH_REFERENCE)
        assertEquals("नमस्ते, यह iTantra का हिंदी परीक्षण है।", MicAcousticCharacterizationRunner.HINDI_REFERENCE)
    }
}
