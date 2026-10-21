# M3 AudioTrack Playback Backend — Static Architecture & Code Audit

> **TASK ID:** `m3-audiotrack-static-audit-01`  
> **DATE:** 2026-09-21  
> **AUDIT STATUS:** `M3 AUDIOTRACK STATIC AUDIT PASS — DEVICE VALIDATION PENDING`  
> **AUDITED TARGETS:**  
> - [`AndroidAudioTrackPlaybackEngine.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AndroidAudioTrackPlaybackEngine.kt)
> - [`PcmAudioConverter.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/PcmAudioConverter.kt)
> - [`AudioPlaybackEngine.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AudioPlaybackEngine.kt)
> - [`AudioPlaybackState.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AudioPlaybackState.kt)
> - [`SpeechMessagePlaybackCoordinator.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/SpeechMessagePlaybackCoordinator.kt)
> - [`TransceiverCoordinator.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverCoordinator.kt)

---

## 1. Executive Summary

This static code and architectural audit systematically verified the implementation of the Android `AudioTrack` playback backend for Module 3 (M3).

### Key Audit Conclusions:
1. **Lifecycle & State Machine:** State transitions strictly adhere to `IDLE -> PLAYING -> (drain) -> IDLE`, with proper handling of `STOPPING` and terminal `RELEASED` states `[VERIFIED BY CODE]`.
2. **Buffer Drain & Tail Integrity:** Enhanced completion handling ensures `audioTrack.stop()` initiates buffer draining and polls `playbackHeadPosition` before releasing native resources `[VERIFIED BY CODE]`.
3. **Write Semantics & Error Codes:** Partial writes are correctly looped until completed, zero-return writes yield coroutine execution (`delay(10)`), and negative error codes immediately raise domain exceptions `[VERIFIED BY CODE]`.
4. **PCM Scaling & Clamping:** Normalized 32-bit floating-point audio samples are deterministically converted to 16-bit signed shorts with boundary clamping, NaN protection, and zero DSP/resampling/filtering `[VERIFIED BY JVM TEST]`.
5. **Concurrency & Threading:** Offloaded to `Dispatchers.IO`, serialized via coroutine `Mutex`, and protected against races via synchronized lifecycle locks `[VERIFIED BY CODE]`.
6. **Physical Validation Boundary:** Hardware speaker output, DAC scheduling, audio latency, underruns, and device-specific audio routing are not validated and remain deferred `[NOT DEVICE VALIDATED]`.

---

## 2. Play Completion & Teardown Verification

| Check | Result | Verification Evidence |
| :--- | :--- | :--- |
| **Write Loop Completion** | Complete buffer consumption | `[VERIFIED BY CODE]` Loop executes until `offset == totalSamples` or `stopRequested`. |
| **Hardware Buffer Drain** | AudioTrack drains before release | `[VERIFIED BY CODE]` Post-write loop calls `audioTrack.stop()` and waits for `playbackHeadPosition >= totalSamples` with bounded timeout. |
| **State Reset to IDLE** | Guaranteed return to `IDLE` | `[VERIFIED BY CODE]` Handled in `finally` block of `play()` if engine is not released. |
| **Mutex Release** | No deadlock on exception | `[VERIFIED BY CODE]` `playbackMutex.withLock` ensures mutex release on any exception or normal exit. |
| **Resource Cleanup** | Native track freed | `[VERIFIED BY CODE]` `renderAudioTrack` `finally` block invokes `audioTrack.release()` and clears `currentAudioTrack`. |

---

## 3. Stop & Release Semantics

### Stop Semantics (`PLAYING -> stop()`):
- `stop()` acquires `lifecycleLock`, transitions `_state` to `STOPPING`, and sets `stopRequested = true`.
- Calls `currentAudioTrack?.pause()` and `currentAudioTrack?.flush()` to immediately halt speaker output and clear native audio queues `[VERIFIED BY CODE]`.
- Resets `_state = IDLE`.
- In-flight `renderAudioTrack` loop on `Dispatchers.IO` observes `stopRequested` and terminates promptly `[VERIFIED BY CODE]`.

### Release Semantics:
- **During IDLE:** Idempotently sets `_state = RELEASED` `[VERIFIED BY CODE]`.
- **During PLAYING / STOPPING:** Sets `_state = RELEASED`, sets `stopRequested = true`, calls `pause()`, `flush()`, and `release()` on active track `[VERIFIED BY CODE]`.
- **During Failed Initialization:** Cleans up uninitialized native `AudioTrack` instance before throwing `AudioPlaybackException` `[VERIFIED BY CODE]`.
- **Post-Release Invariant:** Any call to `play()` throws `AudioPlaybackReleasedException` immediately `[VERIFIED BY CODE]`.

---

## 4. Concurrency & Race Analysis

| Scenario | Classification | Behavior & Mechanism |
| :--- | :--- | :--- |
| `play(A) + play(B)` | **SAFE** | Serialized via `playbackMutex.withLock`. `A` drains completely before `B` starts `[VERIFIED BY CODE]`. |
| `play(A) + stop()` | **SAFE** | `stop()` sets `stopRequested`, pauses/flushes active track, unblocking and terminating `play(A)` `[VERIFIED BY CODE]`. |
| `play(A) + release()` | **SAFE** | `release()` halts playback, frees native resources, and leaves engine in terminal `RELEASED` state `[VERIFIED BY CODE]`. |
| `stop() + release()` | **SAFE** | Synchronized under `lifecycleLock`; terminal `RELEASED` state takes precedence `[VERIFIED BY CODE]`. |
| `play() + immediate release()` | **SAFE** | Post-creation and pre-write state checks ensure unreleased/orphaned `AudioTrack` cannot leak `[VERIFIED BY CODE]`. |
| `repeated stop()` | **SAFE** | Idempotent safe no-op when already idle or stopping `[VERIFIED BY CODE]`. |
| `repeated release()` | **SAFE** | Idempotent safe no-op when `_state == RELEASED` `[VERIFIED BY CODE]`. |

---

## 5. PCM Conversion & Audio Format Verification

- **Source Contract:** `TtsAudio` (`samples: FloatArray`, `sampleRate: Int`, `channelCount: 1`, `sampleFormat: "PCM_FLOAT"`).
- **Conversion Utility:** `PcmAudioConverter.floatToPcm16`:
  - **Positive Range:** $[0.0f, +1.0f] \to [0, 32767]$ (`Short.MAX_VALUE`) `[VERIFIED BY JVM TEST]`.
  - **Negative Range:** $[-1.0f, 0.0f) \to [-32768, 0]$ (`Short.MIN_VALUE`) `[VERIFIED BY JVM TEST]`.
  - **Boundary Clamping:** Samples $> 1.0f$ clamped to `32767`; samples $< -1.0f$ clamped to `-32768` `[VERIFIED BY JVM TEST]`.
  - **NaN Protection:** `Float.NaN` safely mapped to `0` `[VERIFIED BY JVM TEST]`.
  - **Ordering & Topology:** Strictly preserves sample indexing and Mono channel layout `[VERIFIED BY JVM TEST]`.
  - **DSP Preservation:** Zero resampling, zero filtering, zero normalization, zero volume manipulation `[VERIFIED BY CODE]`.

---

## 6. Buffer Configuration & Write Semantics

- **Buffer Size Calculation:**
  - Calls `AudioTrack.getMinBufferSize(sampleRate, CHANNEL_OUT_MONO, ENCODING_PCM_16BIT)`.
  - Safely validates `minBufferSize > 0` (catches `ERROR` (-1) and `ERROR_BAD_VALUE` (-2)), throwing domain `AudioPlaybackException` on invalid platform return `[VERIFIED BY CODE]`.
  - Buffer allocation: `maxOf(minBufferSize * 2, 4096)` `[VERIFIED BY CODE]`.
- **Write Loop Handling:**
  - Chunk size: `minOf(DEFAULT_CHUNK_SIZE_SHORTS, bufferSizeInBytes / 2)` (1024 shorts max).
  - Handles partial writes by tracking `offset += written` `[VERIFIED BY CODE]`.
  - Handles zero-byte return via `kotlinx.coroutines.delay(10)` `[VERIFIED BY CODE]`.
  - Negative return codes trigger `AudioPlaybackException` with returned error code `[VERIFIED BY CODE]`.

---

## 7. Coordinator Error Boundary

- **Hierarchy Isolation:**
  - TTS synthesis failures $\to$ `UnsupportedTtsLanguageException` / `TtsEngineNotInitializedException` $\to$ `TransceiverError.M3TtsError` `[VERIFIED BY JVM TEST]`.
  - Playback driver failures $\to$ `AudioPlaybackException` / `AudioPlaybackReleasedException` $\to$ `TransceiverError.M3PlaybackError` `[VERIFIED BY JVM TEST]`.
  - Transport resilience: Playback errors notify `listener.onError()` and do not close or crash the underlying M2 TCP transport `[VERIFIED BY JVM TEST]`.

---

## 8. Evidentiary Classification & Verification Summary

| Item | Classification | Evidence Basis |
| :--- | :--- | :--- |
| `PcmAudioConverter` conversion & clamping | `[VERIFIED BY JVM TEST]` | 9 unit tests in `PcmAudioConverterTest` |
| `AudioPlaybackEngine` contract & state transitions | `[VERIFIED BY JVM TEST]` | 12 unit tests in `AudioPlaybackEngineTest` |
| `SpeechMessagePlaybackCoordinator` message flow & errors | `[VERIFIED BY JVM TEST]` | 11 unit tests in `SpeechMessagePlaybackCoordinatorTest` |
| `TransceiverCoordinator` M3 error mapping | `[VERIFIED BY JVM TEST]` | Transceiver unit test suite |
| `AndroidAudioTrackPlaybackEngine` code structure & safety | `[VERIFIED BY CODE]` | Static code analysis & Kotlin compiler / Android dexer |
| Physical speaker audio intelligibility | `[NOT DEVICE VALIDATED]` | Physical tablet hardware unavailable |
| Hardware audio latency & DAC jitter | `[NOT DEVICE VALIDATED]` | Requires oscilloscope / hardware audio analyzer harness |
| Hardware buffer underruns | `[REQUIRES DEVICE VALIDATION]` | Requires real-time runtime logging on physical device |
| Device-specific audio routing & output modes | `[REQUIRES DEVICE VALIDATION]` | Requires physical Android device test execution |
