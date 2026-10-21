package org.itantra.core.tts.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.itantra.core.tts.TtsAudio
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic in-memory fake implementation of [AudioPlaybackEngine] for unit testing.
 *
 * Implements the iTantra serialized playback policy where incoming playback requests
 * are executed sequentially without dropping audio buffers.
 *
 * Exposes test hooks allowing unit tests to simulate playback latencies, observe recorded audio buffers,
 * and test cancellation/stop behavior deterministically.
 */
class FakeAudioPlaybackEngine(
    var playbackHook: (suspend (TtsAudio) -> Unit)? = null
) : AudioPlaybackEngine {

    private val lock = Any()
    private val playbackMutex = Mutex()

    @Volatile
    private var _state: AudioPlaybackState = AudioPlaybackState.IDLE

    @Volatile
    private var stopRequested: Boolean = false

    private val _playedAudios = Collections.synchronizedList(mutableListOf<TtsAudio>())
    private val _playCount = AtomicInteger(0)
    private val _stopCount = AtomicInteger(0)
    private val _releaseCount = AtomicInteger(0)

    override val state: AudioPlaybackState
        get() = _state

    /**
     * Immutable list of all [TtsAudio] buffers received and processed for playback.
     */
    val playedAudios: List<TtsAudio>
        get() = synchronized(_playedAudios) { _playedAudios.toList() }

    /** Total number of successful play invocations. */
    val playCount: Int
        get() = _playCount.get()

    /** Total number of stop invocations. */
    val stopCount: Int
        get() = _stopCount.get()

    /** Total number of release invocations. */
    val releaseCount: Int
        get() = _releaseCount.get()

    override suspend fun play(audio: TtsAudio) {
        require(audio.sampleCount > 0) { "Audio buffer must contain at least one sample" }

        // Fast-path release check
        synchronized(lock) {
            if (_state == AudioPlaybackState.RELEASED) {
                throw AudioPlaybackReleasedException("Cannot play audio: playback engine is released")
            }
        }

        // Serialize playback across concurrent callers
        playbackMutex.withLock {
            synchronized(lock) {
                if (_state == AudioPlaybackState.RELEASED) {
                    throw AudioPlaybackReleasedException("Cannot play audio: playback engine is released")
                }
                _state = AudioPlaybackState.PLAYING
                stopRequested = false
            }

            try {
                // Defensive copy or recorded reference
                _playedAudios.add(audio)
                _playCount.incrementAndGet()

                // Execute custom test hook if provided (e.g. for simulated playback duration)
                playbackHook?.invoke(audio)

                // Check for coroutine cancellation or explicit stop
                currentCoroutineContext().ensureActive()
            } catch (e: CancellationException) {
                throw e
            } finally {
                synchronized(lock) {
                    if (_state == AudioPlaybackState.PLAYING || _state == AudioPlaybackState.STOPPING) {
                        _state = AudioPlaybackState.IDLE
                    }
                }
            }
        }
    }

    override fun stop() {
        _stopCount.incrementAndGet()
        synchronized(lock) {
            if (_state == AudioPlaybackState.PLAYING) {
                _state = AudioPlaybackState.STOPPING
                stopRequested = true
                _state = AudioPlaybackState.IDLE
            }
        }
    }

    override fun release() {
        val firstRelease: Boolean
        synchronized(lock) {
            firstRelease = (_state != AudioPlaybackState.RELEASED)
            _state = AudioPlaybackState.RELEASED
            stopRequested = true
        }
        if (firstRelease) {
            _releaseCount.incrementAndGet()
        }
    }

    /**
     * Clears recorded test metrics and playback history.
     */
    fun resetMetrics() {
        synchronized(_playedAudios) {
            _playedAudios.clear()
        }
        _playCount.set(0)
        _stopCount.set(0)
        _releaseCount.set(0)
        stopRequested = false
    }
}
