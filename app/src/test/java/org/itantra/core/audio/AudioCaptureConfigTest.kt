package org.itantra.core.audio

import android.media.AudioFormat
import android.media.MediaRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AudioCaptureConfigTest {

    @Test
    fun testDefaultConfigurationValues() {
        val config = AudioCaptureConfig()

        assertEquals(16000, config.sampleRateInHz)
        assertEquals(AudioFormat.CHANNEL_IN_MONO, config.channelConfig)
        assertEquals(AudioFormat.ENCODING_PCM_16BIT, config.audioFormat)
        assertEquals(MediaRecorder.AudioSource.VOICE_RECOGNITION, config.audioSource)
        assertEquals(20, config.chunkDurationMs)

        // 16000 samples/sec * 0.020 sec = 320 samples
        assertEquals(320, config.chunkSampleCount)

        // 320 samples * 2 bytes/sample * 1 channel = 640 bytes
        assertEquals(640, config.chunkSizeBytes)
    }

    @Test
    fun testCustomDurationChunkCalculations() {
        val config30ms = AudioCaptureConfig(chunkDurationMs = 30)
        assertEquals(480, config30ms.chunkSampleCount)
        assertEquals(960, config30ms.chunkSizeBytes)

        val config10ms = AudioCaptureConfig(chunkDurationMs = 10)
        assertEquals(160, config10ms.chunkSampleCount)
        assertEquals(320, config10ms.chunkSizeBytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidSampleRateThrowsException() {
        AudioCaptureConfig(sampleRateInHz = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidChunkDurationThrowsException() {
        AudioCaptureConfig(chunkDurationMs = 0)
    }

    @Test
    fun testMetricsReset() {
        val metrics = AudioCaptureMetrics(
            sampleRate = 16000,
            minBufferSize = 1280,
            allocatedBufferSize = 2560,
            chunkSampleSize = 320,
            totalSamplesCaptured = 16000L,
            totalFramesRead = 50L,
            captureDurationMs = 1000L,
            errorCount = 2L,
            lastError = "test error"
        )

        metrics.resetSession()

        assertEquals(0L, metrics.totalSamplesCaptured)
        assertEquals(0L, metrics.totalFramesRead)
        assertEquals(0L, metrics.captureDurationMs)
        assertEquals(0L, metrics.errorCount)
        assertEquals(null, metrics.lastError)
        assertEquals(16000, metrics.sampleRate)
        assertEquals(1280, metrics.minBufferSize)
    }
}
