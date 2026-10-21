package org.itantra.core.stt.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrSilenceControlRunnerTest {

    @Test
    fun testConstantsAndVariantsCount() {
        assertEquals(16000, AsrSilenceControlRunner.SAMPLE_RATE)
        assertEquals(23552, AsrSilenceControlRunner.BASE_START_SAMPLE)
        assertEquals(128000, AsrSilenceControlRunner.BASE_END_SAMPLE)
        assertEquals(104448, AsrSilenceControlRunner.BASE_SAMPLE_COUNT)
        assertEquals(10, AsrSilenceControlRunner.VARIANTS.size)
    }

    @Test
    fun testVariantSampleCalculations() {
        // Variant A: 0ms leading, 0ms trailing
        val vA = AsrSilenceControlRunner.VARIANTS.first { it.variantId == "A" }
        assertEquals(0, vA.leadingSilenceMs)
        assertEquals(0, vA.trailingSilenceMs)

        // Variant D: 1500ms leading, 0ms trailing
        val vD = AsrSilenceControlRunner.VARIANTS.first { it.variantId == "D" }
        assertEquals(1500, vD.leadingSilenceMs)
        val vDSamples = (vD.leadingSilenceMs * 16000) / 1000 + 104448
        assertEquals(128448, vDSamples)

        // Variant I: 0ms leading, 2000ms trailing
        val vI = AsrSilenceControlRunner.VARIANTS.first { it.variantId == "I" }
        assertEquals(2000, vI.trailingSilenceMs)
        val vISamples = (vI.trailingSilenceMs * 16000) / 1000 + 104448
        assertEquals(136448, vISamples)

        // Variant J: 1500ms leading, 1000ms trailing
        val vJ = AsrSilenceControlRunner.VARIANTS.first { it.variantId == "J" }
        assertEquals(1500, vJ.leadingSilenceMs)
        assertEquals(1000, vJ.trailingSilenceMs)
        val vJSamples = (1500 * 16) + 104448 + (1000 * 16)
        assertEquals(144448, vJSamples)
    }
}
