package org.itantra.core.vad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VadConfigTest {

    @Test
    fun testDefaultVadConfig() {
        val config = VadConfig()
        assertEquals(16000, config.sampleRate)
        assertEquals(0.5f, config.threshold, 0.001f)
        assertEquals(250, config.minSpeechDurationMs)
        assertEquals(400, config.minSilenceDurationMs)
        assertEquals(30, config.speechPadMs)
        assertEquals("models/silero_vad.onnx", config.modelAssetPath)
        assertEquals(512, VadConfig.SILERO_V5_FRAME_SIZE)
        assertEquals(64, VadConfig.SILERO_V5_CONTEXT_SIZE)
        assertEquals(576, VadConfig.SILERO_V5_INPUT_SIZE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidSampleRateThrows() {
        VadConfig(sampleRate = 44100)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidThresholdThrows() {
        VadConfig(threshold = 1.5f)
    }
}
