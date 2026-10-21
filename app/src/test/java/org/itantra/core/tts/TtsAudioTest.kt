package org.itantra.core.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsAudioTest {

    @Test
    fun `calculates sample count and duration correctly`() {
        val samples = FloatArray(32000) { 0.0f }
        val audio = TtsAudio(
            samples = samples,
            sampleRate = 16000,
            channelCount = 1,
            sampleFormat = "PCM_FLOAT"
        )

        assertEquals(32000, audio.sampleCount)
        assertEquals(16000, audio.sampleRate)
        assertEquals(1, audio.channelCount)
        assertEquals("PCM_FLOAT", audio.sampleFormat)
        assertEquals(2.0f, audio.durationSeconds, 0.0001f)
    }

    @Test
    fun `handles fractional duration correctly`() {
        val samples = FloatArray(24000) { 0.0f }
        val audio = TtsAudio(
            samples = samples,
            sampleRate = 16000,
            channelCount = 1
        )

        assertEquals(24000, audio.sampleCount)
        assertEquals(1.5f, audio.durationSeconds, 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-positive sample rate`() {
        TtsAudio(
            samples = FloatArray(10),
            sampleRate = 0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non-positive channel count`() {
        TtsAudio(
            samples = FloatArray(10),
            sampleRate = 16000,
            channelCount = 0
        )
    }

    @Test
    fun `verifies value equality and hashcode based on sample content`() {
        val samples1 = floatArrayOf(0.1f, -0.2f, 0.5f)
        val samples2 = floatArrayOf(0.1f, -0.2f, 0.5f)
        val samples3 = floatArrayOf(0.1f, -0.2f, 0.9f)

        val audio1 = TtsAudio(samples1, 16000)
        val audio2 = TtsAudio(samples2, 16000)
        val audio3 = TtsAudio(samples3, 16000)

        assertEquals(audio1, audio2)
        assertEquals(audio1.hashCode(), audio2.hashCode())
        assertNotEquals(audio1, audio3)
    }

    @Test
    fun `toString formats metadata cleanly`() {
        val audio = TtsAudio(FloatArray(16000), 16000)
        val str = audio.toString()
        assertTrue(str.contains("sampleCount=16000"))
        assertTrue(str.contains("16000 Hz"))
        assertTrue(str.contains("duration=1.000s"))
    }
}
