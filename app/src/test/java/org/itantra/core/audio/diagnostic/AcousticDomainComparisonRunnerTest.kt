package org.itantra.core.audio.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcousticDomainComparisonRunnerTest {

    @Test
    fun testConstants() {
        assertEquals(16000, AcousticDomainComparisonRunner.SAMPLE_RATE)
        assertEquals(512, AcousticDomainComparisonRunner.FRAME_SIZE)
        assertEquals("same_sentence_mic_test.wav", AcousticDomainComparisonRunner.KNOWN_GOOD_FILE)
        assertEquals("raw_mic_source_control_en.wav", AcousticDomainComparisonRunner.KNOWN_FAILED_FILE)
    }

    @Test
    fun testFrequencyBandsStructure() {
        val band = AcousticDomainComparisonRunner.FrequencyBandEnergy(
            bandName = "0–250 Hz",
            lowFreqHz = 0.0,
            highFreqHz = 250.0,
            absoluteEnergy = 12345.0,
            percentageOfTotal = 15.5
        )
        assertEquals("0–250 Hz", band.bandName)
        assertEquals(15.5, band.percentageOfTotal, 0.01)
    }

    @Test
    fun testFeatureDistributionCalculations() {
        val dist = AcousticDomainComparisonRunner.FeatureDistribution(
            mean = 100.0,
            median = 95.0,
            p10 = 50.0,
            p90 = 150.0,
            stdDev = 25.0
        )
        assertEquals(100.0, dist.mean, 0.01)
        assertEquals(95.0, dist.median, 0.01)
        assertEquals(50.0, dist.p10, 0.01)
        assertEquals(150.0, dist.p90, 0.01)
        assertEquals(25.0, dist.stdDev, 0.01)
    }
}
