package org.itantra.core.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SherpaOfflineTtsEngineTest {

    private val testConfig = SherpaOfflineTtsConfig(
        modelPath = "models/tts/en_US-lessac-low.onnx",
        tokensPath = "models/tts/tokens.txt",
        dataDir = "models/tts/espeak-ng-data"
    )

    private class FakeNativeSession(
        val generateDelayMs: Long = 0L,
        val shouldThrowOnGenerate: Boolean = false
    ) : OfflineTtsNativeSession {
        var lastSynthesizedText: String? = null
        var lastSid: Int? = null
        var lastSpeed: Float? = null
        val generateCallCount = AtomicInteger(0)
        val releaseCount = AtomicInteger(0)

        val onGenerateEntered = CompletableDeferred<Unit>()
        val unblockGenerate = CompletableDeferred<Unit>()

        override fun generate(text: String, sid: Int, speed: Float): TtsAudio {
            generateCallCount.incrementAndGet()
            lastSynthesizedText = text
            lastSid = sid
            lastSpeed = speed

            onGenerateEntered.complete(Unit)

            if (generateDelayMs > 0) {
                Thread.sleep(generateDelayMs)
            }

            if (unblockGenerate.isCompleted.not()) {
                // If test is using deterministic unblocking, wait
                runBlocking {
                    // Check if unblock was requested
                    if (!unblockGenerate.isCompleted) {
                        unblockGenerate.await()
                    }
                }
            }

            if (shouldThrowOnGenerate) {
                throw RuntimeException("Simulated native JNI generation failure")
            }

            return TtsAudio(
                samples = FloatArray(16000) { 0.1f },
                sampleRate = 16000,
                channelCount = 1,
                sampleFormat = "PCM_FLOAT"
            )
        }

        override fun sampleRate(): Int = 16000

        override fun release() {
            releaseCount.incrementAndGet()
        }
    }

    private fun createEngine(
        session: FakeNativeSession = FakeNativeSession(),
        autoInitialize: Boolean = true
    ): Pair<SherpaOfflineTtsEngine, FakeNativeSession> {
        val engine = SherpaOfflineTtsEngine(
            config = testConfig,
            nativeFactory = { _, _ -> session },
            autoInitialize = autoInitialize
        )
        // By default, allow immediate completion of generation
        session.unblockGenerate.complete(Unit)
        return Pair(engine, session)
    }

    private fun createEngineWithNullHindi(
        session: FakeNativeSession = FakeNativeSession(),
        autoInitialize: Boolean = true
    ): Pair<SherpaOfflineTtsEngine, FakeNativeSession> {
        val engine = SherpaOfflineTtsEngine(
            englishConfig = testConfig,
            hindiConfig = null,
            nativeFactory = { _, _ -> session },
            autoInitialize = autoInitialize
        )
        session.unblockGenerate.complete(Unit)
        return Pair(engine, session)
    }

    // 1. Initial State
    @Test
    fun `initial state is UNINITIALIZED when autoInitialize is false`() {
        val (engine, _) = createEngine(autoInitialize = false)
        assertEquals(TtsEngineState.UNINITIALIZED, engine.state)
        assertFalse(engine.isInitialized)
    }

    @Test
    fun `initial state is READY when autoInitialize is true`() {
        val (engine, _) = createEngine(autoInitialize = true)
        assertEquals(TtsEngineState.READY, engine.state)
        assertTrue(engine.isInitialized)
    }

    // 2. Successful initialize
    @Test
    fun `successful initialize transitions UNINITIALIZED to READY`() {
        val (engine, _) = createEngine(autoInitialize = false)
        assertEquals(TtsEngineState.UNINITIALIZED, engine.state)

        engine.initialize()
        assertEquals(TtsEngineState.READY, engine.state)
        assertTrue(engine.isInitialized)
    }

    // 3. Initialize twice (Idempotency)
    @Test
    fun `initialize called twice is a safe idempotent no-op`() {
        val (engine, _) = createEngine(autoInitialize = false)
        engine.initialize()
        assertEquals(TtsEngineState.READY, engine.state)

        // Second initialize call
        engine.initialize()
        assertEquals(TtsEngineState.READY, engine.state)
    }

    // 4. Synthesize before initialize
    @Test
    fun `synthesize before initialize throws TtsEngineNotInitializedException`() = runBlocking {
        val (engine, _) = createEngine(autoInitialize = false)
        try {
            engine.synthesize("Hello", language = "en-US")
            fail("Expected TtsEngineNotInitializedException when synthesizing before initialize")
        } catch (e: TtsEngineNotInitializedException) {
            assertTrue(e.message?.contains("UNINITIALIZED") == true)
        }
    }

    // 5. Synthesize after release
    @Test
    fun `synthesize after release throws TtsEngineNotInitializedException`() = runBlocking {
        val (engine, _) = createEngine(autoInitialize = true)
        engine.release()
        assertEquals(TtsEngineState.RELEASED, engine.state)

        try {
            engine.synthesize("Hello", language = "en-US")
            fail("Expected TtsEngineNotInitializedException after release")
        } catch (e: TtsEngineNotInitializedException) {
            assertTrue(e.message?.contains("RELEASED") == true)
        }
    }

    // 6. Release before initialize
    @Test
    fun `release before initialize transitions UNINITIALIZED to RELEASED`() {
        val (engine, session) = createEngine(autoInitialize = false)
        assertEquals(TtsEngineState.UNINITIALIZED, engine.state)

        engine.release()
        assertEquals(TtsEngineState.RELEASED, engine.state)
        assertEquals(0, session.releaseCount.get()) // No session was created, nothing to release

        // Subsequent initialize should fail
        try {
            engine.initialize()
            fail("Expected IllegalStateException when initializing a released engine")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("released") == true)
        }
    }

    // 7. Release twice (Idempotency)
    @Test
    fun `release called twice is idempotent and only releases native session once`() {
        val (engine, session) = createEngine(autoInitialize = true)
        assertEquals(0, session.releaseCount.get())

        engine.release()
        assertEquals(TtsEngineState.RELEASED, engine.state)
        assertEquals(1, session.releaseCount.get())

        // Second release call
        engine.release()
        assertEquals(TtsEngineState.RELEASED, engine.state)
        assertEquals(1, session.releaseCount.get())
    }

    // 8. Failed initialization
    @Test
    fun `failed initialization transitions state to FAILED and throws`() {
        val failingFactory = OfflineTtsNativeFactory { _, _ ->
            throw IllegalStateException("Model file missing or corrupted")
        }

        val engine = SherpaOfflineTtsEngine(
            config = testConfig,
            nativeFactory = failingFactory,
            autoInitialize = false
        )

        try {
            engine.initialize()
            fail("Expected exception during failed initialization")
        } catch (e: IllegalStateException) {
            assertEquals("Model file missing or corrupted", e.message)
        }

        assertEquals(TtsEngineState.FAILED, engine.state)
        assertFalse(engine.isInitialized)

        // Synthesize should fail cleanly
        runBlocking {
            try {
                engine.synthesize("Hello", language = "en-US")
                fail("Expected TtsEngineNotInitializedException on FAILED state")
            } catch (e: TtsEngineNotInitializedException) {
                assertTrue(e.message?.contains("FAILED") == true)
            }
        }
    }

    // 9. Multi-language routing and support
    @Test
    fun `Hindi language tags are accepted and routed to Hindi model session`() = runBlocking {
        var createdEnglish = 0
        var createdHindi = 0
        val englishSession = FakeNativeSession()
        val hindiSession = FakeNativeSession()
        hindiSession.unblockGenerate.complete(Unit)

        val factory = OfflineTtsNativeFactory { cfg, _ ->
            if (cfg.modelPath.contains("hi_in") || cfg.modelPath.contains("rohan")) {
                createdHindi++
                hindiSession
            } else {
                createdEnglish++
                englishSession
            }
        }

        val engine = SherpaOfflineTtsEngine(
            englishConfig = SherpaOfflineTtsEngine.defaultEnglishConfig(),
            hindiConfig = SherpaOfflineTtsEngine.defaultHindiConfig(),
            nativeFactory = factory,
            autoInitialize = true
        )
        englishSession.unblockGenerate.complete(Unit)

        val hindiTags = listOf("hi", "hi-IN", "hi_IN")
        for (tag in hindiTags) {
            assertTrue("Expected $tag to be supported", engine.isLanguageSupported(tag))
            val audio = engine.synthesize("नमस्ते", language = tag, speed = 1.25f)
            assertEquals("नमस्ते", hindiSession.lastSynthesizedText)
            assertEquals(0, hindiSession.lastSid)
            assertEquals(1.25f, hindiSession.lastSpeed)
        }

        assertEquals(1, createdHindi)
        // Now test English
        val audioEng = engine.synthesize("Hello", language = "en-US", speed = 0.9f)
        assertEquals("Hello", englishSession.lastSynthesizedText)
        assertEquals(0.9f, englishSession.lastSpeed)

        // Clean release releases both sessions
        engine.release()
        assertEquals(1, englishSession.releaseCount.get())
        assertEquals(1, hindiSession.releaseCount.get())
    }

    @Test
    fun `when Hindi config is null, Hindi requests are rejected with UnsupportedTtsLanguageException`() = runBlocking {
        val (engine, _) = createEngineWithNullHindi()
        val hindiTags = listOf("hi", "hi-IN", "hi_IN")

        for (tag in hindiTags) {
            assertFalse(engine.isLanguageSupported(tag))
            try {
                engine.synthesize("नमस्ते", language = tag)
                fail("Expected UnsupportedTtsLanguageException for language $tag")
            } catch (e: UnsupportedTtsLanguageException) {
                assertEquals(tag, e.language)
            }
        }
    }

    @Test
    fun `unsupported arbitrary languages are rejected with UnsupportedTtsLanguageException`() = runBlocking {
        val (engine, _) = createEngine()
        val otherTags = listOf("es", "fr", "de", "ja", "zh", "ru")

        for (tag in otherTags) {
            assertFalse(engine.isLanguageSupported(tag))
            try {
                engine.synthesize("Test", language = tag)
                fail("Expected UnsupportedTtsLanguageException for language $tag")
            } catch (e: UnsupportedTtsLanguageException) {
                assertEquals(tag, e.language)
            }
        }
    }

    // 10. Blank or invalid text
    @Test
    fun `blank or empty text is rejected with IllegalArgumentException`() = runBlocking {
        val (engine, _) = createEngine()
        val blankInputs = listOf("", "   ", "\t\n")

        for (input in blankInputs) {
            try {
                engine.synthesize(input, language = "en-US")
                fail("Expected IllegalArgumentException for blank input: '$input'")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("blank") == true)
            }
        }
    }

    // 11. Invalid speed
    @Test
    fun `invalid speed multipliers are rejected with IllegalArgumentException`() = runBlocking {
        val (engine, _) = createEngine()
        val invalidSpeeds = listOf(0.0f, -0.5f, -1.0f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)

        for (speed in invalidSpeeds) {
            try {
                engine.synthesize("Hello", language = "en-US", speed = speed)
                fail("Expected IllegalArgumentException for invalid speed: $speed")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("Speed") == true)
            }
        }
    }

    // 12. Concurrent synthesis requests
    @Test
    fun `concurrent synthesis requests are serialized and executed safely`() = runBlocking {
        val session = FakeNativeSession(generateDelayMs = 20L)
        val (engine, _) = createEngine(session = session)

        val deferreds = (1..5).map { index ->
            async(Dispatchers.Default) {
                engine.synthesize("Concurrent message $index", language = "en-US")
            }
        }

        val results = deferreds.awaitAll()
        assertEquals(5, results.size)
        assertEquals(5, session.generateCallCount.get())
        for (res in results) {
            assertEquals(16000, res.sampleRate)
            assertEquals(1.0f, res.durationSeconds, 0.0001f)
        }
    }

    // 13. Lifecycle behavior around concurrent release and synthesis
    @Test
    fun `release during active synthesis waits for in-flight generation before freeing session`() = runBlocking {
        val session = FakeNativeSession()
        val engine = SherpaOfflineTtsEngine(
            config = testConfig,
            nativeFactory = { _, _ -> session },
            autoInitialize = true
        )

        // Launch synthesis in background coroutine
        val synthesisJob = async(Dispatchers.Default) {
            engine.synthesize("In flight test", language = "en-US")
        }

        // Wait until generation is actively entered
        session.onGenerateEntered.await()

        val releaseThreadDone = CountDownLatch(1)
        val releaseThread = Thread {
            engine.release()
            releaseThreadDone.countDown()
        }
        releaseThread.start()

        // Verify release is waiting and session is not yet released while generate is active
        Thread.sleep(50)
        assertEquals(0, session.releaseCount.get())

        // Now allow generation to complete
        session.unblockGenerate.complete(Unit)

        // Synthesis should finish successfully
        val audio = synthesisJob.await()
        assertEquals(16000, audio.sampleRate)

        // Release thread should complete and session should now be released
        assertTrue(releaseThreadDone.await(1, TimeUnit.SECONDS))
        assertEquals(1, session.releaseCount.get())
        assertEquals(TtsEngineState.RELEASED, engine.state)

        // Subsequent synthesis fails immediately
        try {
            engine.synthesize("Subsequent message", language = "en-US")
            fail("Expected TtsEngineNotInitializedException after release")
        } catch (e: TtsEngineNotInitializedException) {
            assertTrue(e.message?.contains("RELEASED") == true)
        }
    }

    // 14. Synthesis backend exception safety
    @Test
    fun `native generation exception does not deadlock engine state`() = runBlocking {
        val session = FakeNativeSession(shouldThrowOnGenerate = true)
        val (engine, _) = createEngine(session = session)

        try {
            engine.synthesize("Failing prompt", language = "en-US")
            fail("Expected RuntimeException from simulated JNI failure")
        } catch (e: RuntimeException) {
            assertEquals("Simulated native JNI generation failure", e.message)
        }

        // Engine should still be in READY state and able to release cleanly
        assertEquals(TtsEngineState.READY, engine.state)
        engine.release()
        assertEquals(TtsEngineState.RELEASED, engine.state)
        assertEquals(1, session.releaseCount.get())
    }

    // 15. Supported English languages verification
    @Test
    fun `supported English language tags are accepted and synthesized`() = runBlocking {
        val (engine, session) = createEngine()
        val englishTags = listOf("en", "en-US", "en-us", "en_US")

        for (tag in englishTags) {
            assertTrue("Expected $tag to be supported", engine.isLanguageSupported(tag))
            val audio = engine.synthesize("Hello world", language = tag)
            assertEquals(16000, audio.sampleRate)
            assertEquals(1.0f, audio.durationSeconds, 0.0001f)
            assertEquals("Hello world", session.lastSynthesizedText)
            assertEquals(0, session.lastSid)
        }
    }

    // 16. Input text immutability
    @Test
    fun `input text is passed to synthesis session without mutation`() = runBlocking {
        val (engine, session) = createEngine()
        val testPrompt = "Hello. This is a test of offline speech synthesis."

        engine.synthesize(testPrompt, language = "en-US")
        assertEquals(testPrompt, session.lastSynthesizedText)
    }

    // 17. Hindi model initialization failure transitions engine state to FAILED
    @Test
    fun `Hindi model initialization failure during lazy load transitions state to FAILED`() = runBlocking {
        val factory = OfflineTtsNativeFactory { cfg, _ ->
            if (cfg.modelPath.contains("hi_in") || cfg.modelPath.contains("rohan")) {
                throw IllegalStateException("Hindi model file missing")
            } else {
                FakeNativeSession().also { it.unblockGenerate.complete(Unit) }
            }
        }

        val engine = SherpaOfflineTtsEngine(
            englishConfig = SherpaOfflineTtsEngine.defaultEnglishConfig(),
            hindiConfig = SherpaOfflineTtsEngine.defaultHindiConfig(),
            nativeFactory = factory,
            autoInitialize = true
        )

        try {
            engine.synthesize("नमस्ते", language = "hi")
            fail("Expected IllegalStateException on missing Hindi model")
        } catch (e: IllegalStateException) {
            assertEquals("Hindi model file missing", e.message)
        }

        assertEquals(TtsEngineState.FAILED, engine.state)
    }

    // 18. Rohan Production Configuration and Asset Integrity
    @Test
    fun `default Hindi configuration routes to verified production Rohan model`() {
        val defaultHindi = SherpaOfflineTtsEngine.defaultHindiConfig()
        assertEquals("models/hi/vits-piper-hi_IN-rohan-medium/hi_IN-rohan-medium.onnx", defaultHindi.modelPath)
        assertEquals("models/hi/vits-piper-hi_IN-rohan-medium/tokens.txt", defaultHindi.tokensPath)
        assertEquals("models/hi/vits-piper-hi_IN-rohan-medium/espeak-ng-data", defaultHindi.dataDir)
    }

    @Test
    fun `staged production Rohan assets exist and match exact verified size and SHA-256`() {
        val onnxFile = java.io.File("src/main/assets/models/hi/vits-piper-hi_IN-rohan-medium/hi_IN-rohan-medium.onnx")
        assertTrue("Rohan ONNX file must exist in production assets: ${onnxFile.absolutePath}", onnxFile.exists())
        assertEquals(62946428L, onnxFile.length())

        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = onnxFile.readBytes()
        val digest = md.digest(bytes)
        val actualSha256 = digest.joinToString("") { "%02x".format(it) }
        assertEquals("353d2a20bdb6dd03b74e1aff82b7a10a1646f677f8474c6c81fd606705536224", actualSha256)

        // Verify accompanying files
        assertTrue(java.io.File("src/main/assets/models/hi/vits-piper-hi_IN-rohan-medium/hi_IN-rohan-medium.onnx.json").exists())
        assertTrue(java.io.File("src/main/assets/models/hi/vits-piper-hi_IN-rohan-medium/tokens.txt").exists())
        assertTrue(java.io.File("src/main/assets/models/hi/vits-piper-hi_IN-rohan-medium/MODEL_CARD").exists())
        assertTrue(java.io.File("src/main/assets/models/hi/vits-piper-hi_IN-rohan-medium/espeak-ng-data").isDirectory)

        // Verify Priyamvada and Pratham are absent from production assets
        assertFalse("Priyamvada production asset directory must be absent", java.io.File("src/main/assets/vits-piper-hi_IN-priyamvada-medium").exists())
        assertFalse("Pratham production asset directory must be absent", java.io.File("src/main/assets/models/hi/vits-piper-hi_IN-pratham-medium").exists())
    }
}
