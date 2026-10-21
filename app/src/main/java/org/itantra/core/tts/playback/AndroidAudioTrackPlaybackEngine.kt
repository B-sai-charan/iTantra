package org.itantra.core.tts.playback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.itantra.core.tts.TtsAudio

/**
 * Production Android [AudioPlaybackEngine] implementation rendering synthesized [TtsAudio]
 * through native Android [AudioTrack] in streaming mode.
 *
 * Design Constraints:
 * - Operates in Mono output format at the exact sample rate specified by incoming [TtsAudio].
 * - Performs deterministic conversion from normalized float samples to 16-bit PCM shorts via [PcmAudioConverter].
 * - Streaming mode with dynamic buffer allocation via [AudioTrack.getMinBufferSize].
 * - Offloads all blocking AudioTrack calls to [Dispatchers.IO] (never blocks Android Main/UI thread).
 * - Implements serialized playback concurrency policy using [Mutex] so incoming speech messages are played sequentially.
 * - Thread-safe lifecycle transitions across [AudioPlaybackState.IDLE], [AudioPlaybackState.PLAYING],
 *   [AudioPlaybackState.STOPPING], and [AudioPlaybackState.RELEASED].
 */
class AndroidAudioTrackPlaybackEngine(
    private val audioUsage: Int = AudioAttributes.USAGE_MEDIA,
    private val audioContentType: Int = AudioAttributes.CONTENT_TYPE_SPEECH
) : AudioPlaybackEngine {

    companion object {
        private const val TAG = "iTantra-AudioTrack"
        private const val DEFAULT_CHUNK_SIZE_SHORTS = 1024
    }

    private val lifecycleLock = Any()
    private val playbackMutex = Mutex()

    @Volatile
    private var _state: AudioPlaybackState = AudioPlaybackState.IDLE

    override val state: AudioPlaybackState
        get() = _state

    @Volatile
    private var stopRequested = false

    private var currentAudioTrack: AudioTrack? = null

    override suspend fun play(audio: TtsAudio) {
        require(audio.sampleCount > 0) { "Audio buffer must contain at least one sample" }

        // 1. Validate lifecycle state before acquiring playback lock
        synchronized(lifecycleLock) {
            if (_state == AudioPlaybackState.RELEASED) {
                throw AudioPlaybackReleasedException("Cannot play audio: playback engine is released")
            }
        }

        // 2. Serialize playback across concurrent callers
        playbackMutex.withLock {
            synchronized(lifecycleLock) {
                if (_state == AudioPlaybackState.RELEASED) {
                    throw AudioPlaybackReleasedException("Cannot play audio: playback engine is released")
                }
                _state = AudioPlaybackState.PLAYING
                stopRequested = false
            }

            try {
                // Convert normalized float samples to 16-bit PCM shorts
                val pcmShorts = PcmAudioConverter.floatToPcm16(audio.samples)

                // Render samples on IO dispatcher
                withContext(Dispatchers.IO) {
                    renderAudioTrack(pcmShorts, audio.sampleRate)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AudioPlaybackReleasedException) {
                throw e
            } catch (e: AudioPlaybackException) {
                throw e
            } catch (e: Exception) {
                throw AudioPlaybackException("AudioTrack playback failed: ${e.message}", e)
            } finally {
                synchronized(lifecycleLock) {
                    if (_state == AudioPlaybackState.PLAYING || _state == AudioPlaybackState.STOPPING) {
                        _state = AudioPlaybackState.IDLE
                    }
                }
            }
        }
    }

    override fun stop() {
        synchronized(lifecycleLock) {
            if (_state == AudioPlaybackState.PLAYING) {
                _state = AudioPlaybackState.STOPPING
                stopRequested = true

                try {
                    currentAudioTrack?.pause()
                    currentAudioTrack?.flush()
                } catch (e: Exception) {
                    Log.w(TAG, "Exception stopping AudioTrack: ${e.message}")
                }

                _state = AudioPlaybackState.IDLE
            }
        }
    }

    override fun release() {
        synchronized(lifecycleLock) {
            if (_state == AudioPlaybackState.RELEASED) return

            _state = AudioPlaybackState.RELEASED
            stopRequested = true

            val trackToRelease = currentAudioTrack
            currentAudioTrack = null

            trackToRelease?.let { track ->
                try {
                    track.pause()
                    track.flush()
                    track.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Exception releasing AudioTrack: ${e.message}")
                }
            }
        }
    }

    private suspend fun renderAudioTrack(pcmSamples: ShortArray, sampleRate: Int) {
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufferSize <= 0) {
            throw AudioPlaybackException(
                "AudioTrack.getMinBufferSize returned invalid size: $minBufferSize for sampleRate=$sampleRate"
            )
        }

        val bufferSizeInBytes = maxOf(minBufferSize * 2, DEFAULT_CHUNK_SIZE_SHORTS * 4)

        val audioTrack = createAudioTrack(sampleRate, channelConfig, audioFormat, bufferSizeInBytes)
        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            try { audioTrack.release() } catch (_: Exception) {}
            throw AudioPlaybackException("Failed to initialize native AudioTrack (state=${audioTrack.state})")
        }

        synchronized(lifecycleLock) {
            if (_state == AudioPlaybackState.RELEASED || stopRequested) {
                try { audioTrack.release() } catch (_: Exception) {}
                if (_state == AudioPlaybackState.RELEASED) {
                    throw AudioPlaybackReleasedException("Engine released before audio playback started")
                }
                return
            }
            currentAudioTrack = audioTrack
        }

        try {
            audioTrack.play()

            var offset = 0
            val totalSamples = pcmSamples.size
            val chunkSize = minOf(DEFAULT_CHUNK_SIZE_SHORTS, bufferSizeInBytes / 2)

            while (offset < totalSamples && !stopRequested) {
                currentCoroutineContext().ensureActive()

                synchronized(lifecycleLock) {
                    if (_state == AudioPlaybackState.RELEASED || stopRequested) {
                        return
                    }
                }

                val countToWrite = minOf(chunkSize, totalSamples - offset)
                val written = audioTrack.write(pcmSamples, offset, countToWrite)

                if (written < 0) {
                    throw AudioPlaybackException("AudioTrack.write returned error code: $written")
                } else if (written == 0) {
                    kotlinx.coroutines.delay(10)
                }

                offset += written
            }

            // After all samples are written to AudioTrack buffer, allow hardware to drain
            if (!stopRequested) {
                synchronized(lifecycleLock) {
                    if (_state == AudioPlaybackState.RELEASED || stopRequested) {
                        return
                    }
                }
                try {
                    audioTrack.stop()
                    val maxDrainWaitMs = ((bufferSizeInBytes / (sampleRate * 2.0)) * 1000).toLong() + 200L
                    val startTime = System.currentTimeMillis()
                    while (!stopRequested &&
                        audioTrack.playbackHeadPosition < totalSamples &&
                        audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING &&
                        (System.currentTimeMillis() - startTime) < maxDrainWaitMs
                    ) {
                        synchronized(lifecycleLock) {
                            if (_state == AudioPlaybackState.RELEASED || stopRequested) {
                                return
                            }
                        }
                        kotlinx.coroutines.delay(20)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Drain timeout or track state transition exception - proceed to release in finally
                }
            }
        } finally {
            synchronized(lifecycleLock) {
                if (currentAudioTrack === audioTrack) {
                    currentAudioTrack = null
                }
            }
            try {
                audioTrack.stop()
            } catch (_: Exception) {}
            try {
                audioTrack.release()
            } catch (_: Exception) {}
        }
    }

    private fun createAudioTrack(
        sampleRate: Int,
        channelConfig: Int,
        audioFormat: Int,
        bufferSizeInBytes: Int
    ): AudioTrack {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(audioUsage)
            .setContentType(audioContentType)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .setEncoding(audioFormat)
            .build()

        return AudioTrack(
            audioAttributes,
            format,
            bufferSizeInBytes,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }
}
