package org.itantra.core.audio.dsp

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmAudioConverterTest {

    @Test
    fun floatToPcm16_convertsBoundaryValuesCorrectly() {
        val input = floatArrayOf(0.0f, 1.0f, -1.0f, 0.5f, -0.5f)
        val output = PcmAudioConverter.floatToPcm16(input)

        assertEquals(0.toShort(), output[0])
        assertEquals(32767.toShort(), output[1])
        assertEquals((-32767).toShort(), output[2])
        assertEquals(16383.toShort(), output[3])
        assertEquals((-16383).toShort(), output[4])
    }

    @Test
    fun floatToPcm16_clampsOutOfRangeValues() {
        val input = floatArrayOf(1.5f, 2.0f, -1.5f, -10.0f)
        val output = PcmAudioConverter.floatToPcm16(input)

        assertEquals(32767.toShort(), output[0])
        assertEquals(32767.toShort(), output[1])
        assertEquals((-32767).toShort(), output[2])
        assertEquals((-32767).toShort(), output[3])
    }

    @Test
    fun floatToPcm16_handlesEmptyArray() {
        val input = floatArrayOf()
        val output = PcmAudioConverter.floatToPcm16(input)

        assertEquals(0, output.size)
    }
}
