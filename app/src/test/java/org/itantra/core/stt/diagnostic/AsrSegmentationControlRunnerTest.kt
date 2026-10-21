package org.itantra.core.stt.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrSegmentationControlRunnerTest {

    @Test
    fun testConstants() {
        assertEquals(16000, AsrSegmentationControlRunner.SAMPLE_RATE)
        assertEquals(128000, AsrSegmentationControlRunner.EXPECTED_SAMPLE_COUNT)
        assertEquals("5fd0bd41a6cada093ace02871fb63c3eaf15b4cb7389f8d88be028e458ca6695", AsrSegmentationControlRunner.EXPECTED_EN_SHA256)
    }

    @Test
    fun testSpeechBoundaryDataStructure() {
        val boundary = AsrSegmentationControlRunner.SpeechBoundary(
            speechStartSample = 9600,
            speechEndSample = 48000,
            speechStartMs = 600,
            speechEndMs = 3000,
            speechDurationMs = 2400,
            leadingSilenceMs = 600,
            trailingSilenceMs = 5000,
            activeFrameCount = 75,
            totalFrameCount = 250
        )

        assertEquals(9600, boundary.speechStartSample)
        assertEquals(48000, boundary.speechEndSample)
        assertEquals(600L, boundary.speechStartMs)
        assertEquals(3000L, boundary.speechEndMs)
        assertEquals(2400L, boundary.speechDurationMs)
        assertEquals(600L, boundary.leadingSilenceMs)
        assertEquals(5000L, boundary.trailingSilenceMs)
        assertEquals(75, boundary.activeFrameCount)
        assertEquals(250, boundary.totalFrameCount)
    }

    @Test
    fun testDerivedSegmentResultDataStructure() {
        val res = AsrSegmentationControlRunner.DerivedSegmentResult(
            label = "B. Speech Only",
            fileName = "m1_seg_speech_only.wav",
            startSample = 9600,
            endSample = 48000,
            sampleCount = 38400,
            durationMs = 2400,
            durationSeconds = 2.4,
            sha256 = "dummy_sha",
            rmsDbfs = -35.0,
            peakDbfs = -18.0,
            rawHypothesis = "The quick brown fox jumps over the lazy dog.",
            normalizedHypothesis = "the quick brown fox jumps over the lazy dog",
            wer = 0.0,
            cer = 0.0,
            isExactMatch = true,
            inferenceTimeMs = 1200,
            rtf = 0.5
        )

        assertEquals("B. Speech Only", res.label)
        assertEquals(0.0, res.wer, 0.001)
        assertTrue(res.isExactMatch)
        assertEquals(2.4, res.durationSeconds, 0.001)
    }
}
