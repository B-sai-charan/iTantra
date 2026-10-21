package org.itantra.core.stt.diagnostic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VadExperimentAbcdRunnerTest {

    @Test
    fun testBenchmarkSentencesCountAndStructure() {
        val sentences = VadExperimentAbcdRunner.BENCHMARK_SENTENCES
        assertEquals("Must contain exactly 10 fixed sentences", 10, sentences.size)
        assertEquals("EN-01", sentences[0].id)
        assertEquals("EN-10", sentences[9].id)
        assertEquals("Hello, this is a communication test.", sentences[0].text)
        assertEquals("Good morning. How are you today?", sentences[9].text)
    }

    @Test
    fun testVadConfigurationsCountAndValues() {
        val configs = VadExperimentAbcdRunner.CONFIGURATIONS
        assertEquals("Must contain 4 configurations (A, B, C, D)", 4, configs.size)
        assertEquals("A", configs[0].name)
        assertEquals(400, configs[0].minSilenceMs)
        assertEquals("B", configs[1].name)
        assertEquals(600, configs[1].minSilenceMs)
        assertEquals("C", configs[2].name)
        assertEquals(800, configs[2].minSilenceMs)
        assertEquals("D", configs[3].name)
        assertEquals(1000, configs[3].minSilenceMs)
    }

    @Test
    fun testPcmCharacteristicsComputation() {
        // Create 1 second of 16kHz sine wave at ~ -6 dBFS (amplitude = 16384)
        val sampleRate = 16000
        val audio = ShortArray(sampleRate) { i ->
            (16384.0 * Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate)).toInt().toShort()
        }

        val pcm = VadExperimentAbcdRunner.computePcmCharacteristics("test_sine", audio, sampleRate)
        assertEquals("test_sine", pcm.trialId)
        assertEquals(16000, pcm.sampleCount)
        assertEquals(1.0, pcm.durationSec, 0.001)
        assertTrue(pcm.rmsDbfs < -5.0 && pcm.rmsDbfs > -12.0)
        assertEquals(0, pcm.clippingCount)
        assertEquals(0.0, pcm.clippingPct, 0.001)
    }

    @Test
    fun testConfigurationSummarization() {
        val mockTrials = listOf(
            VadExperimentAbcdRunner.TrialResult(
                configName = "A",
                minSilenceMs = 400,
                sentenceId = "EN-01",
                repIndex = 1,
                expectedTranscript = "Hello, this is a communication test.",
                actualTranscript = "Hello, this is a communication test.",
                normalizedReference = "hello this is a communication test",
                normalizedHypothesis = "hello this is a communication test",
                wer = 0.0,
                cer = 0.0,
                isExactMatch = true,
                speechDurationMs = 2500,
                vadSegmentDurationMs = 2900,
                sttLatencyMs = 800,
                isFragmented = false,
                segmentCount = 1,
                trialSuccess = true
            ),
            VadExperimentAbcdRunner.TrialResult(
                configName = "A",
                minSilenceMs = 400,
                sentenceId = "EN-01",
                repIndex = 2,
                expectedTranscript = "Hello, this is a communication test.",
                actualTranscript = "Hello, this is test.",
                normalizedReference = "hello this is a communication test",
                normalizedHypothesis = "hello this is test",
                wer = 0.333,
                cer = 0.35,
                isExactMatch = false,
                speechDurationMs = 2400,
                vadSegmentDurationMs = 2800,
                sttLatencyMs = 750,
                isFragmented = false,
                segmentCount = 1,
                trialSuccess = true
            )
        )

        val summary = VadExperimentAbcdRunner.summarizeConfiguration("A", 400, mockTrials)
        assertEquals("A", summary.configName)
        assertEquals(400, summary.minSilenceMs)
        assertEquals(2, summary.totalTrials)
        assertEquals(1, summary.exactMatchCount)
        assertEquals(0.5, summary.exactMatchRate, 0.001)
        assertEquals(0, summary.fragmentationCount)
        assertEquals(0.0, summary.fragmentationRate, 0.001)
    }
}
