# M3 TTS Audio Playback Architecture

> **TASK ID:** `m3-audiotrack-01`  
> **DATE:** 2026-09-21  
> **STATUS:** `M3 AUDIOTRACK BACKEND IMPLEMENTED — DEVICE VALIDATION PENDING`  
> **EVIDENTIARY CLASSIFICATION:**  
> - `[DOCUMENTED FACT]`: Codebase contracts, types, Android AudioTrack backend implementation, and JVM unit test verifications.  
> - `[ENGINEERING DECISION]`: Architectural policies selected for the iTantra transceiver MVP.  
> - `[NOT DEVICE VALIDATED]`: Physical device speaker audio output, hardware AudioTrack latency, buffer underruns, and hardware DAC behavior.  

---

## 1. Executive Summary & Pipeline Boundary

In the iTantra offline speech transceiver, Module 3 (M3) is responsible for converting received [`SpeechMessage`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/domain/SpeechMessage.kt) domain entities into synthesized acoustic speech output.

To maintain clean separation of concerns, the presentation pipeline is partitioned into distinct, decoupled abstraction layers:

```
[ MODULE 2: COMMUNICATION SUBSYSTEM ]
       │  (Reconstructed SpeechMessage: text, language, timestamp, confidence)
       ▼
[ M2 -> M3 BOUNDARY: SpeechMessageReceiver ]
       │
       ▼
[ TTS SYNTHESIS LAYER: TtsEngine / SherpaOfflineTtsEngine ]
       │  (Neural synthesis from text -> raw PCM samples)
       ▼
[ TTS AUDIO DOMAIN OBJECT: TtsAudio ]
       │  (Raw PCM FloatArray, 16 kHz Mono, sample count, duration)
       ▼
[ AUDIO PLAYBACK ABSTRACTION: AudioPlaybackEngine ]
       │  (Serialized playback, state machine, lifecycle management)
       ▼
[ HARDWARE SINK: AndroidAudioTrackPlaybackEngine ]
       │  (PCM conversion via PcmAudioConverter -> android.media.AudioTrack MODE_STREAM)
       ▼
[ PHYSICAL SPEAKER (PENDING HARDWARE VALIDATION) ]
```

---

## 2. Responsibilities by Layer

| Component | Primary Responsibility | Dependency Constraints |
| :--- | :--- | :--- |
| [`TtsEngine`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/TtsEngine.kt) | Neural synthesis from text and language tag into raw PCM float samples. Manages offline model lifecycle. | Zero dependency on AudioTrack, Android UI, or playback sinks. |
| [`TtsAudio`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/TtsAudio.kt) | Immutable domain carrier for synthesized PCM audio buffers, sample rate, channel count, and duration. | Pure Kotlin data structure. Zero Android framework dependencies. |
| [`PcmAudioConverter`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/PcmAudioConverter.kt) | Deterministic pure-Kotlin float-to-PCM16 short converter with clamping and asymmetric DSP scaling. | Pure Kotlin object. 100% JVM unit testable. |
| [`AudioPlaybackEngine`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AudioPlaybackEngine.kt) | Decoupled audio playback contract defining playback lifecycle, serialized queueing, stop, and release. | Pure Kotlin interface. Decoupled from `AudioTrack`, `Activity`, and `Context`. |
| [`AndroidAudioTrackPlaybackEngine`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AndroidAudioTrackPlaybackEngine.kt) | Concrete production Android driver writing PCM buffers to `android.media.AudioTrack` in streaming mode on `Dispatchers.IO`. | Encapsulates all Android hardware audio APIs. |

---

## 3. PCM Format & Conversion Details

The playback layer operates on the [`TtsAudio`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/TtsAudio.kt) specification:

- **Channel Count:** Mono (`channelCount = 1`) `[DOCUMENTED FACT]`
- **Sample Format:** Raw PCM Float (`sampleFormat = "PCM_FLOAT"`, normalized range $[-1.0, +1.0]$) `[DOCUMENTED FACT]`
- **Sample Rate:** Determined dynamically by the synthesized `TtsAudio` instance (e.g. `16,000 Hz` for `vits-piper-en_US-lessac-low` or `22,050 Hz` for high-rate models) `[DOCUMENTED FACT]`
- **Sample Count:** Explicitly provided via `audio.sampleCount` (`audio.samples.size`) `[DOCUMENTED FACT]`
- **Duration:** Explicitly calculated as $\text{durationSeconds} = \frac{\text{sampleCount}}{\text{sampleRate}}$ `[DOCUMENTED FACT]`

### Deterministic Conversion (`PcmAudioConverter`):
- Full scale positive: $+1.0f \to 32767$ (`Short.MAX_VALUE`).
- Full scale negative: $-1.0f \to -32768$ (`Short.MIN_VALUE`).
- Values outside $[-1.0f, +1.0f]$ clamped strictly to boundary limits.
- `NaN` samples safely mapped to `0`.
- Ordering, channel count, and sample count strictly preserved with zero DSP/resampling/filtering.

---

## 4. Playback State Machine & Lifecycle

```
              ┌────────────────────────────────────────┐
              │                  IDLE                  │◄───────────────┐
              └───────────────────┬────────────────────┘                │
                                  │                                     │
               ┌──────────────────┴──────────────────┐                  │
               │ play(audio)                         │ release()        │
               ▼                                     ▼                  │
   ┌───────────────────────┐             ┌───────────────────────┐      │
   │        PLAYING        │             │        RELEASED       │      │
   └───────────┬───────────┘             └───────────────────────┘      │
               │                                     ▲                  │
        ┌──────┴──────┐                              │                  │
        │ stop()      │ normal completion            │ release()        │
        ▼             ▼                              │                  │
   ┌─────────┐   ┌─────────┐                         │                  │
   │ STOPPING│   │  (done) ├─────────────────────────┴──────────────────┘
   └────┬────┘   └─────────┘                         │
        │                                            │
        └────────────────────────────────────────────┘
```

### Valid State Transitions:
1. `IDLE -> PLAYING`: Initiated by `play(audio)`.
2. `PLAYING -> IDLE`: Reached upon successful drain/completion of the audio buffer.
3. `PLAYING -> STOPPING -> IDLE`: Initiated by calling `stop()`, which cancels the active buffer playback.
4. `IDLE -> RELEASED`: Initiated by `release()` when idle.
5. `PLAYING -> RELEASED`: Initiated by `release()` while playing, cancelling active playback and freeing hardware sinks.
6. `RELEASED -> RELEASED`: Idempotent no-op.

---

## 5. Concurrent Playback & Threading Architecture

### Concurrency Policy: `SERIALIZED (Sequential Execution)` `[ENGINEERING DECISION]`
- In a tactical push-to-talk (PTT) transceiver environment, incoming speech messages represent discrete, critical voice transmissions.
- All incoming `play()` requests are queued/serialized using coroutine synchronization (`Mutex`). Every received speech message is completely spoken in order without overlap or buffer collisions.
- If preemption or emergency interruption is required, the operator or controller can explicitly invoke `stop()`.

### Threading & Offloading:
- `AudioTrack.write()` and rendering execute strictly on `Dispatchers.IO`.
- Android Main/UI thread is never blocked during buffer write operations.
- AudioTrack instances are allocated and released deterministically per playback session or upon engine `release()`.

---

## 6. Error Semantics

- Invalid buffer sizes or failed `AudioTrack` initialization throw [`AudioPlaybackException`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AudioPlaybackException.kt).
- Invocations of `play()` after engine release throw [`AudioPlaybackReleasedException`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AudioPlaybackReleasedException.kt).
- Negative return codes from `AudioTrack.write()` are caught and propagated as domain exceptions.
- Upstream `SpeechMessagePlaybackCoordinator` maps all playback errors to `TransceiverError.M3PlaybackError`.

---

## 7. Evidentiary Status & Verification Summary

| Component / Property | Status | Evidence Classification |
| :--- | :--- | :--- |
| **Domain Interface (`AudioPlaybackEngine`)** | `[IMPLEMENTED & TESTED]` | `[DOCUMENTED FACT]` |
| **Playback State Machine (`AudioPlaybackState`)** | `[IMPLEMENTED & TESTED]` | `[DOCUMENTED FACT]` |
| **Serialized Concurrent Playback Policy** | `[IMPLEMENTED & TESTED]` | `[DOCUMENTED FACT]` |
| **Deterministic Fake Double (`FakeAudioPlaybackEngine`)** | `[IMPLEMENTED & TESTED]` | `[DOCUMENTED FACT]` |
| **PCM Conversion Logic (`PcmAudioConverter`)** | `[IMPLEMENTED & TESTED]` | `[DOCUMENTED FACT]` (9 unit tests, 100% pass) |
| **Android AudioTrack Engine (`AndroidAudioTrackPlaybackEngine`)** | `[CODE IMPLEMENTED & BUILT]` | `[DOCUMENTED FACT]` (Compiles into debug APK) |
| **Physical Speaker Output & Intelligibility** | `NOT DEVICE VALIDATED` | `[NOT MEASURED]` (Tablet hardware unavailable) |
| **Hardware Latency / Underrun Measurements** | `NOT DEVICE VALIDATED` | `[NOT MEASURED]` (Requires physical audio harness) |

---

## 8. Test Double (`FakeAudioPlaybackEngine`)

A deterministic in-memory test double [`FakeAudioPlaybackEngine`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/FakeAudioPlaybackEngine.kt) is provided for unit testing:

- Implements `AudioPlaybackEngine`.
- Enforces the `SERIALIZED` playback policy using coroutine `Mutex`.
- Exposes deterministic test hooks (`playbackHook`, `playedAudios`, `playCount`, `stopCount`, `releaseCount`).
- Uses zero arbitrary `Thread.sleep()` in test assertions (relying on `CompletableDeferred` and coroutine synchronization).
- Enables 100% JVM-only unit testing for all TTS/transceiver coordinators without requiring Android hardware mocks.

