package org.itantra.core.vad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VadFrameAdapterTest {

    private class MockVadDetector : VadDetector {
        override val config: VadConfig = VadConfig()
        override val isInitialized: Boolean = true
        override val totalInferences: Long get() = processedFrames.size.toLong()
        override val averageInferenceLatencyMs: Double = 0.0

        val processedFrames = mutableListOf<ShortArray>()
        var resetCount = 0

        override fun process(samples: ShortArray): VadResult {
            processedFrames.add(samples.clone())
            return VadResult(isSpeech = false, probability = 0.1f)
        }

        override fun reset() {
            resetCount++
        }

        override fun close() {}
    }

    @Test
    fun test320SampleAudioRecordChunksAccumulation() {
        val mockDetector = MockVadDetector()
        val adapter = VadFrameAdapter(mockDetector, targetFrameSize = 512)

        // Chunk 1: 320 samples -> buffer has 320 (< 512) -> 0 frames emitted
        val chunk1 = ShortArray(320) { 100 }
        val results1 = adapter.process(chunk1)
        assertEquals(0, results1.size)
        assertEquals(0, mockDetector.processedFrames.size)
        assertEquals(320, adapter.pendingSampleCount)

        // Chunk 2: 320 samples -> buffer has 320 + 320 = 640 (>= 512) -> 1 frame emitted, 128 left
        val chunk2 = ShortArray(320) { 200 }
        val results2 = adapter.process(chunk2)
        assertEquals(1, results2.size)
        assertEquals(1, mockDetector.processedFrames.size)
        assertEquals(512, mockDetector.processedFrames[0].size)
        assertEquals(128, adapter.pendingSampleCount)

        // Chunk 3: 320 samples -> buffer has 128 + 320 = 448 (< 512) -> 0 frames emitted
        val chunk3 = ShortArray(320) { 300 }
        val results3 = adapter.process(chunk3)
        assertEquals(0, results3.size)
        assertEquals(1, mockDetector.processedFrames.size)
        assertEquals(448, adapter.pendingSampleCount)

        // Chunk 4: 320 samples -> buffer has 448 + 320 = 768 (>= 512) -> 1 frame emitted, 256 left
        val chunk4 = ShortArray(320) { 400 }
        val results4 = adapter.process(chunk4)
        assertEquals(1, results4.size)
        assertEquals(2, mockDetector.processedFrames.size)
        assertEquals(256, adapter.pendingSampleCount)

        // Chunk 5: 320 samples -> buffer has 256 + 320 = 576 (>= 512) -> 1 frame emitted, 64 left
        val chunk5 = ShortArray(320) { 500 }
        val results5 = adapter.process(chunk5)
        assertEquals(1, results5.size)
        assertEquals(3, mockDetector.processedFrames.size)
        assertEquals(64, adapter.pendingSampleCount)
    }

    @Test
    fun testAdapterReset() {
        val mockDetector = MockVadDetector()
        val adapter = VadFrameAdapter(mockDetector, targetFrameSize = 512)

        adapter.process(ShortArray(320))
        assertEquals(320, adapter.pendingSampleCount)

        adapter.reset()
        assertEquals(0, adapter.pendingSampleCount)
        assertEquals(1, mockDetector.resetCount)
    }
}
