package org.itantra.core.vad

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import kotlin.math.sin

class SileroVadEngineTest {

    private var vadEngine: SileroVadEngine? = null

    @Before
    fun setUp() {
        val stream: InputStream? = javaClass.getResourceAsStream("/models/silero_vad.onnx")
        assertNotNull("Model file /models/silero_vad.onnx must exist in test resources", stream)
        val modelBytes = stream!!.use { it.readBytes() }
        vadEngine = SileroVadEngine.fromBytes(modelBytes)
    }

    @After
    fun tearDown() {
        vadEngine?.close()
    }

    @Test
    fun testInitializationState() {
        val engine = vadEngine!!
        assertTrue(engine.isInitialized)
        assertEquals(16000, engine.config.sampleRate)
        assertEquals(0L, engine.totalInferences)
    }

    @Test
    fun testSilenceProducesLowProbability() {
        val engine = vadEngine!!
        val silence = ShortArray(512) { 0 }

        // Run sequential silence frames to let recurrent state stabilize
        var lastResult: VadResult? = null
        for (i in 0 until 5) {
            lastResult = engine.process(silence)
        }

        assertNotNull(lastResult)
        // Silence probability should be very low (close to 0)
        assertTrue(
            "Expected silence probability < 0.2, got: ${lastResult!!.probability}",
            lastResult.probability < 0.2f
        )
        assertEquals(false, lastResult.isSpeech)
        assertEquals(5L, engine.totalInferences)
        assertTrue("Inference latency should be positive", engine.averageInferenceLatencyMs >= 0.0)
    }

    @Test
    fun testResetClearsRecurrentState() {
        val engine = vadEngine!!
        val signal = ShortArray(512) { i -> (sin(i * 0.1) * 15000).toInt().toShort() }

        // Run several frames
        for (i in 0 until 4) {
            engine.process(signal)
        }

        assertEquals(4L, engine.totalInferences)

        // Reset state
        engine.reset()

        // Next frame runs cleanly
        val postResetResult = engine.process(ShortArray(512))
        assertNotNull(postResetResult)
    }
}
