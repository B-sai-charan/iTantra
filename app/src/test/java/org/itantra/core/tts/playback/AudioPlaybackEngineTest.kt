package org.itantra.core.tts.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.itantra.core.tts.TtsAudio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AudioPlaybackEngineTest {

    private fun createSampleAudio(
        sampleCount: Int = 16000,
        sampleRate: Int = 16000,
        value: Float = 0.5f
    ): TtsAudio {
        return TtsAudio(
            samples = FloatArray(sampleCount) { value },
            sampleRate = sampleRate,
            channelCount = 1,
            sampleFormat = "PCM_FLOAT"
        )
    }

    // 1. Initial State
    @Test
    fun `initial state is IDLE and not playing or released`() {
        val engine = FakeAudioPlaybackEngine()
        assertEquals(AudioPlaybackState.IDLE, engine.state)
        assertFalse(engine.isPlaying)
        assertFalse(engine.isReleased)
        assertEquals(0, engine.playCount)
    }

    // 2. Play Audio
    @Test
    fun `play audio transitions state to PLAYING and back to IDLE upon completion`() = runBlocking {
        val observedStateInPlayback = CompletableDeferred<AudioPlaybackState>()
        val unblockPlayback = CompletableDeferred<Unit>()

        val engine = FakeAudioPlaybackEngine {
            observedStateInPlayback.complete(it.let { AudioPlaybackState.PLAYING })
            unblockPlayback.await()
        }

        val audio = createSampleAudio()

        val playJob = launch(Dispatchers.Default) {
            engine.play(audio)
        }

        // Wait until playback hook is entered
        val stateDuringPlay = observedStateInPlayback.await()
        assertEquals(AudioPlaybackState.PLAYING, stateDuringPlay)

        // Unblock and complete playback
        unblockPlayback.complete(Unit)
        playJob.join()

        assertEquals(AudioPlaybackState.IDLE, engine.state)
        assertEquals(1, engine.playCount)
        assertEquals(1, engine.playedAudios.size)
        assertEquals(audio, engine.playedAudios.first())
    }

    // 3. Stop
    @Test
    fun `stop while playing transitions state back to IDLE`() = runBlocking {
        val engine = FakeAudioPlaybackEngine()
        val audio = createSampleAudio()

        engine.play(audio)
        assertEquals(1, engine.playCount)

        engine.stop()
        assertEquals(AudioPlaybackState.IDLE, engine.state)
        assertEquals(1, engine.stopCount)
    }

    // 4. Release
    @Test
    fun `release permanently transitions state to RELEASED`() {
        val engine = FakeAudioPlaybackEngine()
        engine.release()
        assertEquals(AudioPlaybackState.RELEASED, engine.state)
        assertTrue(engine.isReleased)
        assertFalse(engine.isPlaying)
        assertEquals(1, engine.releaseCount)
    }

    // 5. Release twice (Idempotency)
    @Test
    fun `release called multiple times is idempotent`() {
        val engine = FakeAudioPlaybackEngine()
        engine.release()
        assertEquals(1, engine.releaseCount)

        engine.release()
        assertEquals(1, engine.releaseCount)
        assertEquals(AudioPlaybackState.RELEASED, engine.state)
    }

    // 6. Play after release
    @Test
    fun `play after release throws AudioPlaybackReleasedException`() = runBlocking {
        val engine = FakeAudioPlaybackEngine()
        engine.release()

        val audio = createSampleAudio()
        try {
            engine.play(audio)
            fail("Expected AudioPlaybackReleasedException when playing after release")
        } catch (e: AudioPlaybackReleasedException) {
            assertTrue(e.message?.contains("released") == true)
        }
    }

    // 7. Stop while idle
    @Test
    fun `stop while idle is a safe no-op leaving state IDLE`() {
        val engine = FakeAudioPlaybackEngine()
        assertEquals(AudioPlaybackState.IDLE, engine.state)

        engine.stop()
        assertEquals(AudioPlaybackState.IDLE, engine.state)
        assertEquals(1, engine.stopCount)
    }

    // 8. Repeated sequential play
    @Test
    fun `repeated sequential play requests are recorded in order`() = runBlocking {
        val engine = FakeAudioPlaybackEngine()
        val audio1 = createSampleAudio(sampleCount = 8000, value = 0.1f)
        val audio2 = createSampleAudio(sampleCount = 16000, value = 0.2f)
        val audio3 = createSampleAudio(sampleCount = 24000, value = 0.3f)

        engine.play(audio1)
        engine.play(audio2)
        engine.play(audio3)

        assertEquals(3, engine.playCount)
        assertEquals(3, engine.playedAudios.size)
        assertEquals(audio1, engine.playedAudios[0])
        assertEquals(audio2, engine.playedAudios[1])
        assertEquals(audio3, engine.playedAudios[2])
    }

    // 9. Concurrent play requests (Serialized policy)
    @Test
    fun `concurrent play requests are serialized and all execute completely`() = runBlocking {
        val engine = FakeAudioPlaybackEngine()
        val count = 10

        val jobs = (1..count).map { index ->
            async(Dispatchers.Default) {
                val audio = createSampleAudio(sampleCount = 1000 * index, value = index * 0.1f)
                engine.play(audio)
            }
        }

        jobs.awaitAll()

        assertEquals(count, engine.playCount)
        assertEquals(count, engine.playedAudios.size)
        assertEquals(AudioPlaybackState.IDLE, engine.state)
    }

    // 10. Lifecycle transition correctness
    @Test
    fun `verifies valid state transitions`() = runBlocking {
        val engine = FakeAudioPlaybackEngine()
        assertEquals(AudioPlaybackState.IDLE, engine.state)

        // IDLE -> PLAYING -> IDLE
        val audio = createSampleAudio()
        engine.play(audio)
        assertEquals(AudioPlaybackState.IDLE, engine.state)

        // IDLE -> RELEASED
        engine.release()
        assertEquals(AudioPlaybackState.RELEASED, engine.state)
    }

    // 11. Empty sample array rejection
    @Test
    fun `play rejects audio with zero samples`() = runBlocking {
        val engine = FakeAudioPlaybackEngine()
        val emptyAudio = TtsAudio(samples = FloatArray(0), sampleRate = 16000)

        try {
            engine.play(emptyAudio)
            fail("Expected IllegalArgumentException for empty audio")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("sample") == true)
        }
    }

    // 12. Release during active playback
    @Test
    fun `release during active playback marks state RELEASED and subsequent play fails`() = runBlocking {
        val enteredPlayback = CompletableDeferred<Unit>()
        val unblockPlayback = CompletableDeferred<Unit>()

        val engine = FakeAudioPlaybackEngine {
            enteredPlayback.complete(Unit)
            unblockPlayback.await()
        }

        val playJob = launch(Dispatchers.Default) {
            engine.play(createSampleAudio())
        }

        // Wait until active playback starts
        enteredPlayback.await()

        // Release the engine while in-flight
        engine.release()
        assertEquals(AudioPlaybackState.RELEASED, engine.state)

        // Unblock playback
        unblockPlayback.complete(Unit)
        playJob.join()

        // State remains RELEASED
        assertEquals(AudioPlaybackState.RELEASED, engine.state)

        // Next play fails
        try {
            engine.play(createSampleAudio())
            fail("Expected AudioPlaybackReleasedException after release")
        } catch (e: AudioPlaybackReleasedException) {
            assertTrue(e.message?.contains("released") == true)
        }
    }
}
