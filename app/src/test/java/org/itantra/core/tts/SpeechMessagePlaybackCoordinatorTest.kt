package org.itantra.core.tts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.itantra.core.domain.SpeechMessage
import org.itantra.core.tts.playback.AudioPlaybackException
import org.itantra.core.tts.playback.FakeAudioPlaybackEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SpeechMessagePlaybackCoordinatorTest {

    private class FakeTtsEngine(
        private val supported: Set<String> = setOf("en", "en-US", "en-us", "en_US")
    ) : TtsEngine {
        var lastSynthesizedText: String? = null
        var lastSynthesizedLanguage: String? = null
        val synthesizeCount = AtomicInteger(0)
        val releaseCount = AtomicInteger(0)
        var shouldThrowOnSynthesize: Throwable? = null

        override val state: TtsEngineState
            get() = if (releaseCount.get() > 0) TtsEngineState.RELEASED else TtsEngineState.READY

        override val supportedLanguages: Set<String> = supported

        override fun isLanguageSupported(language: String): Boolean = language in supported

        override fun initialize() {}

        override suspend fun synthesize(text: String, language: String, speed: Float): TtsAudio {
            if (releaseCount.get() > 0) {
                throw TtsEngineNotInitializedException("TTS engine is released")
            }
            if (text.isBlank()) {
                throw IllegalArgumentException("Text cannot be blank")
            }
            if (!isLanguageSupported(language)) {
                throw UnsupportedTtsLanguageException(language)
            }
            shouldThrowOnSynthesize?.let { throw it }

            synthesizeCount.incrementAndGet()
            lastSynthesizedText = text
            lastSynthesizedLanguage = language

            return TtsAudio(
                samples = FloatArray(16000) { 0.2f },
                sampleRate = 16000,
                channelCount = 1,
                sampleFormat = "PCM_FLOAT"
            )
        }

        override fun release() {
            releaseCount.incrementAndGet()
        }
    }

    private fun createCoordinator(
        ttsEngine: FakeTtsEngine = FakeTtsEngine(),
        playbackEngine: FakeAudioPlaybackEngine = FakeAudioPlaybackEngine(),
        ownsDependencies: Boolean = false
    ): Triple<SpeechMessagePlaybackCoordinator, FakeTtsEngine, FakeAudioPlaybackEngine> {
        val coordinator = SpeechMessagePlaybackCoordinator(
            ttsEngine = ttsEngine,
            playbackEngine = playbackEngine,
            ownsDependencies = ownsDependencies
        )
        return Triple(coordinator, ttsEngine, playbackEngine)
    }

    // 1. English SpeechMessage reaches TTS with exact text
    @Test
    fun `English SpeechMessage reaches TTS with exact text and no mutation`() = runBlocking {
        val (coordinator, ttsEngine, playbackEngine) = createCoordinator()
        val message = SpeechMessage(
            text = "Target coordinates locked at grid sector 4.",
            language = "en-US",
            timestamp = 1700000000000L,
            confidence = 0.95f
        )

        coordinator.onSpeechMessageReceived(message)

        assertEquals("Target coordinates locked at grid sector 4.", ttsEngine.lastSynthesizedText)
        assertEquals("en-US", ttsEngine.lastSynthesizedLanguage)
        assertEquals(1, ttsEngine.synthesizeCount.get())
        assertEquals(1, playbackEngine.playCount)
    }

    // 2. SpeechMessage language is passed unchanged
    @Test
    fun `SpeechMessage language tag is passed directly to TTS engine`() = runBlocking {
        val (coordinator, ttsEngine, _) = createCoordinator()
        val languageTags = listOf("en", "en-US", "en_US")

        for (tag in languageTags) {
            val message = SpeechMessage(
                text = "Operational check",
                language = tag,
                timestamp = System.currentTimeMillis()
            )
            coordinator.onSpeechMessageReceived(message)
            assertEquals(tag, ttsEngine.lastSynthesizedLanguage)
        }
    }

    // 3. Synthesized TtsAudio reaches playback
    @Test
    fun `synthesized TtsAudio is delivered directly to playback engine`() = runBlocking {
        val (coordinator, _, playbackEngine) = createCoordinator()
        val message = SpeechMessage(
            text = "Audio routing verification test",
            language = "en-US",
            timestamp = 1700000000000L
        )

        val audio = coordinator.processMessage(message)

        assertEquals(1, playbackEngine.playedAudios.size)
        assertEquals(audio, playbackEngine.playedAudios.first())
        assertEquals(16000, audio.sampleRate)
        assertEquals(1.0f, audio.durationSeconds, 0.0001f)
    }

    // 4. Hindi SpeechMessage reaches TTS with exact text and language
    @Test
    fun `Hindi SpeechMessage reaches TTS with exact Devanagari text and language`() = runBlocking {
        val ttsEngine = FakeTtsEngine(supported = setOf("en", "en-US", "en_US", "hi", "hi-IN", "hi_IN"))
        val (coordinator, _, playbackEngine) = createCoordinator(ttsEngine = ttsEngine)
        val hindiMessage = SpeechMessage(
            text = "सामरिक संदेश प्राप्त हुआ",
            language = "hi",
            timestamp = 1700000000000L,
            confidence = 0.98f
        )

        coordinator.onSpeechMessageReceived(hindiMessage)

        assertEquals("सामरिक संदेश प्राप्त हुआ", ttsEngine.lastSynthesizedText)
        assertEquals("hi", ttsEngine.lastSynthesizedLanguage)
        assertEquals(1, ttsEngine.synthesizeCount.get())
        assertEquals(1, playbackEngine.playCount)
    }

    // 4b. Unsupported language is propagated
    @Test
    fun `unsupported language is rejected with UnsupportedTtsLanguageException`() = runBlocking {
        val (coordinator, ttsEngine, playbackEngine) = createCoordinator()
        val spanishMessage = SpeechMessage(
            text = "Mensaje no compatible",
            language = "es",
            timestamp = 1700000000000L
        )

        try {
            coordinator.onSpeechMessageReceived(spanishMessage)
            fail("Expected UnsupportedTtsLanguageException for Spanish message")
        } catch (e: UnsupportedTtsLanguageException) {
            assertEquals("es", e.language)
        }

        assertEquals(0, ttsEngine.synthesizeCount.get())
        assertEquals(0, playbackEngine.playCount)
    }

    // 5. Blank text handling
    @Test
    fun `blank or empty SpeechMessage text throws IllegalArgumentException`() = runBlocking {
        val (coordinator, _, playbackEngine) = createCoordinator()
        val blankMessage = SpeechMessage(
            text = "   ",
            language = "en-US",
            timestamp = 1700000000000L
        )

        try {
            coordinator.onSpeechMessageReceived(blankMessage)
            fail("Expected IllegalArgumentException for blank message text")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("blank") == true)
        }

        assertEquals(0, playbackEngine.playCount)
    }

    // 6. TTS failure is observable
    @Test
    fun `TTS synthesis failure propagates directly to caller`() = runBlocking {
        val ttsEngine = FakeTtsEngine()
        ttsEngine.shouldThrowOnSynthesize = IllegalStateException("Native model failure")
        val (coordinator, _, playbackEngine) = createCoordinator(ttsEngine = ttsEngine)

        val message = SpeechMessage(
            text = "Hello world",
            language = "en-US",
            timestamp = 1700000000000L
        )

        try {
            coordinator.onSpeechMessageReceived(message)
            fail("Expected IllegalStateException from TTS engine")
        } catch (e: IllegalStateException) {
            assertEquals("Native model failure", e.message)
        }

        assertEquals(0, playbackEngine.playCount)
    }

    // 7. Playback failure is observable
    @Test
    fun `playback failure propagates directly to caller`() = runBlocking {
        val playbackEngine = FakeAudioPlaybackEngine {
            throw AudioPlaybackException("AudioTrack underrun or hardware disconnected")
        }
        val (coordinator, ttsEngine, _) = createCoordinator(playbackEngine = playbackEngine)

        val message = SpeechMessage(
            text = "Hello world",
            language = "en-US",
            timestamp = 1700000000000L
        )

        try {
            coordinator.onSpeechMessageReceived(message)
            fail("Expected AudioPlaybackException from playback engine")
        } catch (e: AudioPlaybackException) {
            assertTrue(e.message?.contains("AudioTrack") == true)
        }

        assertEquals(1, ttsEngine.synthesizeCount.get())
    }

    // 8. Released coordinator rejects new work
    @Test
    fun `released coordinator rejects new speech messages with IllegalStateException`() = runBlocking {
        val (coordinator, _, _) = createCoordinator()
        coordinator.release()
        assertTrue(coordinator.isReleased)

        val message = SpeechMessage(
            text = "Post-release message",
            language = "en-US",
            timestamp = 1700000000000L
        )

        try {
            coordinator.onSpeechMessageReceived(message)
            fail("Expected IllegalStateException after coordinator release")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("released") == true)
        }
    }

    // 9. Repeated release is deterministic and idempotent
    @Test
    fun `repeated release calls are safe and idempotent`() {
        val (coordinator, ttsEngine, playbackEngine) = createCoordinator(ownsDependencies = true)
        assertFalse(coordinator.isReleased)

        coordinator.release()
        assertTrue(coordinator.isReleased)
        assertEquals(1, ttsEngine.releaseCount.get())
        assertEquals(1, playbackEngine.releaseCount)

        coordinator.release()
        assertTrue(coordinator.isReleased)
        assertEquals(1, ttsEngine.releaseCount.get())
        assertEquals(1, playbackEngine.releaseCount)
    }

    // 10. Dependency ownership policy
    @Test
    fun `ownsDependencies false leaves injected dependencies unreleased`() {
        val (coordinator, ttsEngine, playbackEngine) = createCoordinator(ownsDependencies = false)
        coordinator.release()

        assertTrue(coordinator.isReleased)
        assertEquals(0, ttsEngine.releaseCount.get())
        assertEquals(0, playbackEngine.releaseCount)
    }

    // 11. Concurrent incoming SpeechMessages serialize cleanly
    @Test
    fun `concurrent incoming SpeechMessages are processed and serialized through playback engine`() = runBlocking {
        val (coordinator, ttsEngine, playbackEngine) = createCoordinator()
        val count = 8

        val jobs = (1..count).map { index ->
            async(Dispatchers.Default) {
                val msg = SpeechMessage(
                    text = "Message index $index",
                    language = "en-US",
                    timestamp = System.currentTimeMillis() + index
                )
                coordinator.onSpeechMessageReceived(msg)
            }
        }

        jobs.awaitAll()

        assertEquals(count, ttsEngine.synthesizeCount.get())
        assertEquals(count, playbackEngine.playCount)
        assertEquals(count, playbackEngine.playedAudios.size)
    }

    // 12. M2 boundary interface compliance
    @Test
    fun `coordinator complies with SpeechMessageReceiver interface boundary`() = runBlocking {
        val (coordinator, _, playbackEngine) = createCoordinator()
        val receiver: SpeechMessageReceiver = coordinator

        val message = SpeechMessage(
            text = "M2 to M3 boundary integration test",
            language = "en-US",
            timestamp = 1700000000000L
        )

        receiver.onSpeechMessageReceived(message)
        assertEquals(1, playbackEngine.playCount)
    }
}
