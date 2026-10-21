package org.itantra.core.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class PcmAudioNormalizerTest {

    private fun generateSineWave(sampleCount: Int, frequencyHz: Float, sampleRate: Int, peakAmplitude: Short): ShortArray {
        val samples = ShortArray(sampleCount)
        for (i in 0 until sampleCount) {
            val angle = 2.0 * Math.PI * frequencyHz * i / sampleRate
            samples[i] = (sin(angle) * peakAmplitude).toInt().coerceIn(-32768, 32767).toShort()
        }
        return samples
    }

    /**
     * Requirement a): Quiet waveform is amplified safely.
     */
    @Test
    fun normalize_quietWaveform_amplifiesSafely() {
        // Sine wave with peak 1500, RMS ~1060 (quiet speech)
        val quiet = generateSineWave(sampleCount = 1000, frequencyHz = 440f, sampleRate = 22050, peakAmplitude = 1500)
        val initialPeak = PcmAudioNormalizer.calculatePeak(quiet)
        val initialRms = PcmAudioNormalizer.calculateRms(quiet)

        val result = PcmAudioNormalizer.normalize(quiet)

        assertTrue("Applied gain must be > 1.0 for quiet audio", result.appliedGain > 1.0f)
        assertTrue("Normalized RMS must be higher than initial RMS", result.normalizedRms > initialRms)
        assertTrue("Normalized peak must not exceed safe headroom", result.normalizedPeak <= PcmAudioNormalizer.DEFAULT_MAX_ALLOWED_PEAK)
        assertFalse("Clipping protection should not trigger for clean quiet sine", result.isClippingProtectionTriggered)
        assertEquals("Sample count must be preserved", quiet.size, result.samples.size)
    }

    /**
     * Requirement b): Loud waveform is attenuated safely.
     */
    @Test
    fun normalize_loudWaveform_attenuatesSafely() {
        // Sine wave with peak 31000, RMS ~21920 (excessively hot audio)
        val loud = generateSineWave(sampleCount = 1000, frequencyHz = 440f, sampleRate = 22050, peakAmplitude = 31000)
        val initialPeak = PcmAudioNormalizer.calculatePeak(loud)
        val initialRms = PcmAudioNormalizer.calculateRms(loud)

        val result = PcmAudioNormalizer.normalize(loud)

        assertTrue("Applied gain must be < 1.0 for loud audio", result.appliedGain < 1.0f)
        assertTrue("Normalized RMS must be lower than initial RMS", result.normalizedRms < initialRms)
        assertTrue("Normalized peak must be safely attenuated", result.normalizedPeak <= PcmAudioNormalizer.DEFAULT_MAX_ALLOWED_PEAK)
        assertEquals("Sample count must be preserved", loud.size, result.samples.size)
    }

    /**
     * Requirement c): Silent and near-silent waveforms do not produce invalid gain.
     */
    @Test
    fun normalize_silentWaveform_doesNotProduceInvalidGain() {
        // Exactly zero
        val zeros = ShortArray(500) { 0 }
        val resultZeros = PcmAudioNormalizer.normalize(zeros)

        assertEquals("Applied gain for absolute silence must be 1.0", 1.0f, resultZeros.appliedGain, 0.001f)
        assertEquals(0, resultZeros.normalizedPeak)
        assertEquals(0.0f, resultZeros.normalizedRms, 0.001f)
        assertFalse(resultZeros.isClippingProtectionTriggered)

        // Near-silent low-level noise floor (peak 10, well below SILENCE_PEAK_THRESHOLD)
        val nearSilent = ShortArray(500) { ((it % 5) - 2).toShort() }
        val resultNearSilent = PcmAudioNormalizer.normalize(nearSilent)

        assertEquals("Applied gain for near-silent audio must be 1.0", 1.0f, resultNearSilent.appliedGain, 0.001f)
        assertFalse(resultNearSilent.isClippingProtectionTriggered)
        assertEquals(nearSilent.size, resultNearSilent.samples.size)
    }

    /**
     * Requirement d): Output never exceeds PCM16 range [-32768, 32767].
     */
    @Test
    fun normalize_outputNeverExceedsPcm16Range() {
        // High crest-factor waveform with sharp transient spike
        val samples = ShortArray(1000) { 100 }
        samples[100] = 25000
        samples[200] = -25000
        samples[500] = 32767
        samples[600] = -32768

        val result = PcmAudioNormalizer.normalize(samples)

        for (sample in result.samples) {
            assertTrue("Sample $sample must be >= -32768", sample >= -32768)
            assertTrue("Sample $sample must be <= 32767", sample <= 32767)
        }
        assertTrue("Normalized peak must be <= MAX_PCM16_VALUE", result.normalizedPeak <= PcmAudioNormalizer.MAX_PCM16_VALUE)
    }

    /**
     * Requirement e): Normalization does not change sample count.
     */
    @Test
    fun normalize_preservesExactSampleCount() {
        val testSizes = listOf(0, 1, 5, 128, 22050, 44100)
        for (size in testSizes) {
            val input = ShortArray(size) { (it % 1000).toShort() }
            val output = PcmAudioNormalizer.normalize(input).samples
            assertEquals("Sample count must match input size $size", size, output.size)
        }
    }

    /**
     * Safety & Headroom strategy test:
     * When RMS gain would cause peak to clip past headroom, clipping protection limits gain.
     */
    @Test
    fun normalize_clippingProtection_enforcesPeakHeadroom() {
        // Sparse clicks: low RMS (~1000), but large peak (20000)
        val samples = ShortArray(2000) { 0 }
        samples[100] = 20000
        samples[500] = -20000
        for (i in 1000 until 1200) {
            samples[i] = 2500
        }

        val result = PcmAudioNormalizer.normalize(samples)

        assertTrue("Clipping protection must be triggered when peak would exceed headroom", result.isClippingProtectionTriggered)
        assertTrue("Normalized peak must not exceed DEFAULT_MAX_ALLOWED_PEAK", result.normalizedPeak <= PcmAudioNormalizer.DEFAULT_MAX_ALLOWED_PEAK)
        assertEquals("Normalized peak must equal DEFAULT_MAX_ALLOWED_PEAK within rounding", PcmAudioNormalizer.DEFAULT_MAX_ALLOWED_PEAK.toFloat(), result.normalizedPeak.toFloat(), 2.0f)
    }
}
