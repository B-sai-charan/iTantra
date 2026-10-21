# M3 Speech-to-Playback Integration Architecture

> **TASK ID:** `m3-integration-01`  
> **DATE:** 2026-09-21  
> **STATUS:** `M3 SPEECH-TO-PLAYBACK INTEGRATION COMPLETE — DEVICE VALIDATION PENDING`  
> **EVIDENTIARY CLASSIFICATION:**  
> - `[DOCUMENTED FACT]`: Codebase contracts, types, and JVM unit test verifications.  
> - `[ENGINEERING DECISION]`: Architectural policies selected for the iTantra transceiver MVP.  
> - `[NOT MEASURED]`: Physical device speaker audio output and real hardware JNI synthesis.  

---

## 1. Responsibility

[`SpeechMessagePlaybackCoordinator`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/SpeechMessagePlaybackCoordinator.kt) serves as the top-level Module 3 (M3) consumer bridging incoming verified [`SpeechMessage`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/domain/SpeechMessage.kt) instances from the Module 2 (M2) communication boundary to neural speech synthesis and audio playback.

### Scope of Responsibility:
1. Implements [`SpeechMessageReceiver`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/SpeechMessageReceiver.kt) to consume incoming messages from M2.
2. Passes [`SpeechMessage.text`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/domain/SpeechMessage.kt) and [`SpeechMessage.language`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/domain/SpeechMessage.kt) to [`TtsEngine.synthesize`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/TtsEngine.kt) without mutation.
3. Forwards synthesized [`TtsAudio`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/TtsAudio.kt) to [`AudioPlaybackEngine.play`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AudioPlaybackEngine.kt).
4. Propagates domain exceptions cleanly.
5. Manages coordinator lifecycle and optional dependency teardown.

### Explicit Non-Responsibilities:
- Zero knowledge of TCP sockets, networking threads, wire codecs, or CRC checksums.
- Zero knowledge of Android UI, Activities, or Views.
- Zero knowledge of low-level `AudioTrack` hardware drivers.

---

## 2. Dependencies & Ownership

```kotlin
class SpeechMessagePlaybackCoordinator(
    private val ttsEngine: TtsEngine,
    private val playbackEngine: AudioPlaybackEngine,
    private val ownsDependencies: Boolean = false
) : SpeechMessageReceiver
```

### Lifecycle Ownership Policy `[ENGINEERING DECISION]`:
- **Explicit Injected Dependencies:** Dependencies (`TtsEngine`, `AudioPlaybackEngine`) are passed explicitly via constructor injection. No hidden singletons or global static state.
- **Ownership Flag (`ownsDependencies`):**
  - When `ownsDependencies = false` (default): Releasing the coordinator leaves the injected `TtsEngine` and `AudioPlaybackEngine` alive for reuse by other components.
  - When `ownsDependencies = true`: Calling `coordinator.release()` cascades teardown to `playbackEngine.release()` and `ttsEngine.release()`.

---

## 3. Data Flow

```
[ MODULE 2 (M2) — COMMUNICATION LAYER ]
                 │
                 │ SpeechMessage(text, language, timestamp, confidence)
                 ▼
[ M2 -> M3 BOUNDARY ]
   SpeechMessageReceiver.onSpeechMessageReceived(message)
                 │
                 ▼
[ COORDINATOR: SpeechMessagePlaybackCoordinator ]
                 │
                 │ ttsEngine.synthesize(message.text, message.language)
                 ▼
[ NEURAL SYNTHESIS: TtsEngine ]
                 │
                 │ TtsAudio(samples: FloatArray, sampleRate: 16000, channels: 1, format: "PCM_FLOAT")
                 ▼
[ PLAYBACK LAYER: AudioPlaybackEngine ]
                 │
                 │ playbackEngine.play(audio)  [Serialized Execution]
                 ▼
[ HARDWARE SINK (FUTURE): Android AudioTrack ]
```

---

## 4. Language & Text Behavior

- **Text Immutability `[DOCUMENTED FACT]`:** `SpeechMessage.text` is forwarded exactly as received. No punctuation insertion, normalization, spelling alteration, translation, or ITN is applied.
- **Language Routing `[DOCUMENTED FACT]`:**
  - `"en"`, `"en-US"`, `"en-us"`, `"en_US"` $\to$ Routed to the English VITS neural model.
  - `"hi"`, `"hi-IN"`, or unsupported tags $\to$ Immediately rejected with `UnsupportedTtsLanguageException` (no silent fallback or cross-lingual hallucination).

---

## 5. Error Boundaries & Exceptions

| Error Scenario | Propagated Exception | Coordinator State |
| :--- | :--- | :--- |
| **Unsupported Language** (e.g. `"hi"`) | [`UnsupportedTtsLanguageException`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/TtsAudio.kt) | Operational (Playback uninvoked) |
| **Blank / Whitespace Text** | `IllegalArgumentException` | Operational (Playback uninvoked) |
| **TTS Engine Not Initialized / Released** | [`TtsEngineNotInitializedException`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/TtsAudio.kt) | Operational |
| **TTS Native Synthesis Failure** | Propagated as thrown (e.g. `RuntimeException`) | Operational (Playback uninvoked) |
| **Audio Playback Failure** | [`AudioPlaybackException`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AudioPlaybackEngine.kt) | Operational |
| **Message Received After Release** | `IllegalStateException` | Permanently Released |

---

## 6. Concurrency Policy

- The coordinator does **not** introduce a redundant second queue.
- Concurrency serialization is fully delegated to [`AudioPlaybackEngine`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AudioPlaybackEngine.kt)'s `SERIALIZED` policy.
- Concurrent incoming `SpeechMessage` dispatches are synthesized and played sequentially without packet loss or audio collisions.

---

## 7. Evidentiary Status

> **Actual TTS synthesis and Android audio playback remain device-validation tasks.**

| Component / Layer | Status |
| :--- | :--- |
| **M3 Coordinator (`SpeechMessagePlaybackCoordinator`)** | `[IMPLEMENTED & TESTED]` |
| **M2 $\to$ M3 Boundary Compatibility (`SpeechMessageReceiver`)** | `[IMPLEMENTED & TESTED]` |
| **Unit Test Coverage (`SpeechMessagePlaybackCoordinatorTest`)** | `[IMPLEMENTED & TESTED]` (12 unit tests, 100% pass) |
| **Physical Hardware AudioTrack Output** | `NOT MEASURED (Deferred until device availability)` |
| **Real Device Neural Synthesis** | `NOT MEASURED (Device Unavailable)` |
