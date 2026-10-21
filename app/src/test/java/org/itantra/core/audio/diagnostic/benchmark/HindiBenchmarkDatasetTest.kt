package org.itantra.core.audio.diagnostic.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HindiBenchmarkDatasetTest {

    @Test
    fun datasetContainsAtLeast20Sentences() {
        val sentences = HindiBenchmarkDataset.sentences
        assertTrue("Dataset must contain at least 20 sentences", sentences.size >= 20)
    }

    @Test
    fun allSentencesAreNonEmptyHindiDevanagari() {
        val sentences = HindiBenchmarkDataset.sentences
        for (sentence in sentences) {
            assertTrue("Sentence text must not be blank", sentence.text.isNotBlank())
            assertTrue("Sentence character count must be > 0", sentence.charCount > 0)
            // Verify sentence contains Devanagari Unicode characters (range \u0900-\u097F)
            val hasDevanagari = sentence.text.any { it in '\u0900'..'\u097F' }
            assertTrue("Sentence '${sentence.text}' must contain Devanagari script", hasDevanagari)
        }
    }

    @Test
    fun sentenceBenchmarkRecordCalculatesLatenciesCorrectly() {
        val record = SentenceBenchmarkRecord(
            sentenceId = 1,
            text = "परीक्षण",
            charCount = 7,
            t0RequestReceivedMs = 1000L,
            t1SynthesisStartedMs = 1050L,
            t2AudioAvailableMs = 1250L,
            t3PlaybackStartedMs = 1280L,
            t4PlaybackCompletedMs = 2500L,
            audioDurationMs = 1220L,
            sampleRate = 22050,
            processMemoryMb = 45.5f
        )

        // T2 - T1 = 1250 - 1050 = 200 ms
        assertEquals(200L, record.synthesisLatencyMs)
        // T3 - T0 = 1280 - 1000 = 280 ms
        assertEquals(280L, record.playbackStartLatencyMs)
        // T4 - T0 = 2500 - 1000 = 1500 ms
        assertEquals(1500L, record.totalCompletionLatencyMs)
        // RTF = 200 / 1220 = 0.1639f (< 1.0 means faster than real-time)
        assertNotNull(record.realTimeFactor)
        assertTrue(record.realTimeFactor!! < 1.0f)
    }
}
