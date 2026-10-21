package org.itantra.core.tts.playback

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic unit tests for [PcmAudioConverter] float-to-PCM16 conversion logic.
 */
class PcmAudioConverterTest {

    @Test
    fun `empty input array returns empty short array`() {
        val input = FloatArray(0)
        val output = PcmAudioConverter.floatToPcm16(input)
        assertEquals(0, output.size)
    }

    @Test
    fun `zero float maps to zero short`() {
        val input = floatArrayOf(0.0f, -0.0f)
        val output = PcmAudioConverter.floatToPcm16(input)
        assertEquals(2, output.size)
        assertEquals(0.toShort(), output[0])
        assertEquals(0.toShort(), output[1])
    }

    @Test
    fun `positive one maps to Short MAX_VALUE 32767`() {
        val input = floatArrayOf(1.0f)
        val output = PcmAudioConverter.floatToPcm16(input)
        assertEquals(1, output.size)
        assertEquals(Short.MAX_VALUE, output[0]) // 32767
    }

    @Test
    fun `negative one maps to Short MIN_VALUE -32768`() {
        val input = floatArrayOf(-1.0f)
        val output = PcmAudioConverter.floatToPcm16(input)
        assertEquals(1, output.size)
        assertEquals(Short.MIN_VALUE, output[0]) // -32768
    }

    @Test
    fun `positive values greater than one are clamped to 32767`() {
        val input = floatArrayOf(1.1f, 2.5f, 100.0f, Float.MAX_VALUE)
        val output = PcmAudioConverter.floatToPcm16(input)
        for (sample in output) {
            assertEquals(Short.MAX_VALUE, sample)
        }
    }

    @Test
    fun `negative values less than negative one are clamped to -32768`() {
        val input = floatArrayOf(-1.1f, -2.5f, -100.0f, -Float.MAX_VALUE)
        val output = PcmAudioConverter.floatToPcm16(input)
        for (sample in output) {
            assertEquals(Short.MIN_VALUE, sample)
        }
    }

    @Test
    fun `NaN values map safely to zero`() {
        val input = floatArrayOf(Float.NaN)
        val output = PcmAudioConverter.floatToPcm16(input)
        assertEquals(0.toShort(), output[0])
    }

    @Test
    fun `intermediate values scale accurately`() {
        val input = floatArrayOf(0.5f, -0.5f, 0.25f, -0.25f)
        val output = PcmAudioConverter.floatToPcm16(input)

        // 0.5 * 32767 = 16383.5 -> round to 16384
        assertEquals(16384.toShort(), output[0])
        // -0.5 * 32767 = -16383.5 -> round to -16384
        assertEquals((-16384).toShort(), output[1])
        // 0.25 * 32767 = 8191.75 -> round to 8192
        assertEquals(8192.toShort(), output[2])
        // -0.25 * 32767 = -8191.75 -> round to -8192
        assertEquals((-8192).toShort(), output[3])
    }

    @Test
    fun `sample ordering is strictly preserved`() {
        val input = floatArrayOf(-1.0f, -0.5f, 0.0f, 0.5f, 1.0f)
        val output = PcmAudioConverter.floatToPcm16(input)

        assertEquals(5, output.size)
        assertEquals(Short.MIN_VALUE, output[0])
        assertEquals((-16384).toShort(), output[1])
        assertEquals(0.toShort(), output[2])
        assertEquals(16384.toShort(), output[3])
        assertEquals(Short.MAX_VALUE, output[4])
    }
}
