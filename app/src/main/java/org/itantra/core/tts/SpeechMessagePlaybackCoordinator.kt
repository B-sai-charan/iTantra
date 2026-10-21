package org.itantra.core.tts

import org.itantra.core.domain.SpeechMessage
import org.itantra.core.tts.playback.AudioPlaybackEngine
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinator bridging incoming [SpeechMessage] instances from the M2 communication boundary
 * to M3 neural TTS speech synthesis and downstream audio playback.
 *
 * Architecture Flow:
 * [SpeechMessage] -> [TtsEngine.synthesize] -> [TtsAudio] -> [AudioPlaybackEngine.play]
 *
 * Design Invariants:
 * - Decoupled from networking, TCP sockets, packet codecs, Activities, and Android AudioTrack.
 * - Passes [SpeechMessage.text] and [SpeechMessage.language] strictly unchanged without mutation.
 * - Relies on the underlying [AudioPlaybackEngine] for playback queueing/serialization.
 * - Propagates specific domain exceptions ([UnsupportedTtsLanguageException], [TtsEngineNotInitializedException],
 *   [IllegalArgumentException]) directly to the caller.
 *
 * @property ttsEngine The TTS engine responsible for synthesizing text into raw PCM samples.
 * @property playbackEngine The audio playback engine responsible for rendering synthesized samples.
 * @property ownsDependencies If true, releasing this coordinator will also release the injected [ttsEngine] and [playbackEngine].
 */
class SpeechMessagePlaybackCoordinator(
    private val ttsEngine: TtsEngine,
    private val playbackEngine: AudioPlaybackEngine,
    private val ownsDependencies: Boolean = false
) : SpeechMessageReceiver {

    private val released = AtomicBoolean(false)

    /**
     * Whether the coordinator has been released.
     */
    val isReleased: Boolean
        get() = released.get()

    /**
     * Receives and processes an incoming [SpeechMessage] by synthesizing speech via [TtsEngine]
     * and playing the resulting [TtsAudio] via [AudioPlaybackEngine].
     *
     * @param message The verified incoming speech message.
     * @return The synthesized [TtsAudio] instance that was submitted for playback.
     * @throws IllegalStateException if the coordinator has been released.
     * @throws IllegalArgumentException if message text is blank.
     * @throws UnsupportedTtsLanguageException if message language is not supported by the TTS engine.
     * @throws TtsEngineNotInitializedException if the TTS engine is not in the READY state.
     */
    override suspend fun onSpeechMessageReceived(message: SpeechMessage) {
        processMessage(message)
    }

    /**
     * Synthesizes and plays the provided [SpeechMessage], returning the synthesized [TtsAudio] domain object.
     *
     * @param message The incoming speech message.
     * @return The synthesized [TtsAudio] passed to the playback engine.
     */
    suspend fun processMessage(message: SpeechMessage): TtsAudio {
        if (released.get()) {
            throw IllegalStateException("Cannot process speech message: SpeechMessagePlaybackCoordinator has been released")
        }

        android.util.Log.i("M2_M3_TEST", "[M2-M3-INBOUND] SpeechMessage received: text='${message.text}', lang='${message.language}', ts=${message.timestamp}")
        android.util.Log.i("M2_M3_TEST", "[M3-TTS-START] Starting TTS synthesis for text='${message.text}' (lang=${message.language})")
        val synthStartMs = System.currentTimeMillis()

        // Synthesize text using exact received text and language without mutation
        val audio = ttsEngine.synthesize(
            text = message.text,
            language = message.language
        )
        val synthTimeMs = System.currentTimeMillis() - synthStartMs
        val audioDurationMs = if (audio.sampleRate > 0) (audio.sampleCount * 1000L) / audio.sampleRate else 0L
        val rtf = if (audioDurationMs > 0) synthTimeMs.toFloat() / audioDurationMs else 0.0f
        android.util.Log.i("M2_M3_TEST", "[M3-TTS-COMPLETE] TtsAudio generated: ${audio.sampleCount} samples @ ${audio.sampleRate}Hz (duration=${audioDurationMs}ms) in ${synthTimeMs}ms (RTF: ${"%.3f".format(rtf)})")

        android.util.Log.i("M2_M3_TEST", "[M3-PLAYBACK-START] Starting AudioTrack playback for ${audio.sampleCount} samples (${audio.sampleCount * 2} bytes)")
        val playStartMs = System.currentTimeMillis()

        // Render synthesized PCM audio samples through playback engine
        playbackEngine.play(audio)
        val playTimeMs = System.currentTimeMillis() - playStartMs
        android.util.Log.i("M2_M3_TEST", "[M3-PLAYBACK-COMPLETE] Playback completed in ${playTimeMs}ms")

        return audio
    }

    /**
     * Stops active playback and transitions the coordinator to the released state.
     * If [ownsDependencies] is true, also releases [ttsEngine] and [playbackEngine].
     *
     * This operation is idempotent and thread-safe.
     */
    fun release() {
        if (released.compareAndSet(false, true)) {
            if (ownsDependencies) {
                playbackEngine.release()
                ttsEngine.release()
            }
        }
    }
}
