# M3 Hindi Neural TTS Integration Readiness Audit

> **TASK ID:** `M1-M3-HINDI-TTS-INTEGRATION-READINESS-AUDIT-01`  
> **DATE:** 2026-09-23  
> **CLASSIFICATION:** `HINDI TTS MODEL NOT PRESENT — BLOCKED FROM INTEGRATION`  
> **EVIDENTIARY PRINCIPLE:** Facts are derived strictly from physical file inspection, static analysis, and unit test execution in the active repository. No unmeasured claims or assumed assets are reported as facts.

---

## 1. Executive Summary

This audit evaluated the current state of the repository to verify claims regarding M3 Hindi neural TTS implementation readiness.

**Key Finding:**  
**Hindi neural TTS is NOT implemented and NO Hindi TTS model assets exist in the codebase.**  
The repository currently contains **only an English Piper VITS neural model** (`vits-piper-en_US-lessac-low`). When a message with `language = "hi"` or `language = "hi-IN"` reaches the M3 TTS subsystem, `SherpaOfflineTtsEngine` immediately rejects the request with `UnsupportedTtsLanguageException` as designed.

However, the surrounding infrastructure is clean:
1. The **M2 $\to$ M3 boundary** ([`SpeechMessageReceiver`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/SpeechMessageReceiver.kt)) and coordinator ([`SpeechMessagePlaybackCoordinator`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/SpeechMessagePlaybackCoordinator.kt)) propagate language tags (`"hi"`, `"hi-IN"`, Devanagari text) verbatim without data corruption or mutation.
2. The playback layer ([`AndroidAudioTrackPlaybackEngine`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AndroidAudioTrackPlaybackEngine.kt)) is completely language-agnostic and ready to render PCM audio at arbitrary sample rates once a Hindi model is loaded.
3. The underlying Sherpa-ONNX native library statically supports VITS Hindi models without requiring new AARs or native compilation.

---

## 2. TTS Model Assets Audit

Inspection of `app/src/main/assets` and the entire workspace:

### A. Existing Model Assets (`app/src/main/assets/vits-piper-en_US-lessac-low/`)
| File | Size (Bytes) | SHA-256 Checksum | Language / Purpose |
| :--- | :--- | :--- | :--- |
| `en_US-lessac-low.onnx` | 63,149,198 | `8177731223C47C72764E7021866F549DC72B894ABC6B252C7F1CA0F8F65AD659` | English (US) VITS neural acoustic model |
| `tokens.txt` | 921 | `87C8EF66EAE5473ED0CC0366B3964C736CA6C5F676C979522EA31234E47430B9` | English phoneme token vocabulary |
| `en_US-lessac-low.onnx.json` | 4,882 | — | Piper model metadata (sample rate: 16000 Hz) |
| `MODEL_CARD` | 345 | — | English Lessac dataset provenance |
| `espeak-ng-data/` | Directory | — | eSpeak-NG phoneme mappings & dictionaries |

### B. Hindi Model Assets
- **Status:** **`NOT PRESENT`**
- **Files found under `app/src/main/assets`:** `0` Hindi TTS files.
- **Files found under `scratch/`:** `0` Hindi TTS files.
- **Files found in whole repository:** `0` Hindi TTS models (`.onnx`), token files, or Hindi espeak dictionaries.

---

## 3. Sherpa TTS Implementation & Language Routing

### A. Accepted Language Codes
In [`SherpaOfflineTtsEngine.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/SherpaOfflineTtsEngine.kt#L119):
```kotlin
override val supportedLanguages: Set<String> = setOf("en", "en-US", "en-us", "en_US")
```
- **Accepted:** `"en"`, `"en-US"`, `"en-us"`, `"en_US"`.
- **Rejected:** All other language codes, including `"hi"`, `"hi-IN"`, `"hi_IN"`.

### B. Language Routing Logic
In [`SherpaOfflineTtsEngine.synthesize`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/SherpaOfflineTtsEngine.kt#L157-L175):
```kotlin
if (!isLanguageSupported(language)) {
    throw UnsupportedTtsLanguageException(
        language = language,
        message = "Language '$language' is not supported by this English VITS TTS engine instance"
    )
}
```
- There is **no multi-model routing** or dynamic Hindi model selection logic implemented in `SherpaOfflineTtsEngine`.
- The engine binds exclusively to a single `SherpaOfflineTtsConfig` pointing to the English ONNX model path.

---

## 4. TTS Engine Configuration & Runtime Behavior

| Parameter | Current Configuration | Handling / Behavior |
| :--- | :--- | :--- |
| **Model Type** | VITS / Piper | Configured via `OfflineTtsVitsModelConfig` |
| **Target Sample Rate** | $16,000\,\text{Hz}$ | Set by `en_US-lessac-low.onnx`; extracted dynamically via `session.sampleRate()` |
| **Channel Count** | $1$ (Mono) | Configured in `TtsAudio` output |
| **Quantization** | Float32 ONNX | Standard VITS low-tier Float32 graph (~60.2 MB) |
| **Speaker ID** | `sid = 0` | Hardcoded single-speaker ID in `activeSession.generate(text, sid = 0, speed)` |
| **Speed Control** | `speed: Float = 1.0f` | Validated for finite positive float, passed directly to native JNI |
| **Lifecycle State Machine** | `UNINITIALIZED` $\rightarrow$ `READY` $\rightarrow$ `RELEASED` / `FAILED` | Thread-safe, synchronized, and verified with 18 unit tests |
| **JNI Concurrency** | Serialized via `Mutex` | `synthesisMutex.withLock` guards non-reentrant native Sherpa session |
| **Release Drain Guard** | Active synthesis wait | Waits for `activeSynthesisCount == 0` before freeing native pointers |

---

## 5. Existing M3 Benchmark Infrastructure

| Benchmark Component | Status | Implementation Details |
| :--- | :--- | :--- |
| **English TTS Benchmark Runner** | `PRESENT` | [`TtsDiagnosticRunner`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/diagnostic/TtsDiagnosticRunner.kt) — Profiles memory, init latency, synthesis latency, audio duration, and RTF over repeated English prompts. |
| **Hindi TTS Benchmark Runner** | `NOT PRESENT` | No Hindi TTS benchmark harness exists. (Note: Hindi benchmark runners exist for **M1 ASR / STT** in `AsrAccuracyBenchmarkRunner`, but **not for M3 TTS**). |
| **Hindi TTS Benchmark Corpus** | `NOT PRESENT` | No standardized Hindi TTS phrase evaluation dataset or phonetically balanced Hindi prompts are configured. |
| **Timing Instrumentation** | `PRESENT` | Wall-clock nanosecond/millisecond timing for synthesis and AudioTrack playback is instrumented in `TtsDiagnosticRunner` and `SpeechMessagePlaybackCoordinator`. |
| **Runnable Status** | `BLOCKED FOR HINDI` | English TTS diagnostic is runnable; Hindi TTS diagnostic cannot run due to missing model assets and engine support. |

---

## 6. Playback Engine ([`AndroidAudioTrackPlaybackEngine`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AndroidAudioTrackPlaybackEngine.kt))

- **AudioTrack Mode:** `AudioTrack.MODE_STREAM` with `AudioAttributes.USAGE_MEDIA` and `AudioAttributes.CONTENT_TYPE_SPEECH`.
- **Expected PCM Format:** Normalized `FloatArray` in range $[-1.0, +1.0]$, converted deterministically to 16-bit signed PCM shorts via [`PcmAudioConverter.floatToPcm16`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/PcmAudioConverter.kt).
- **Sample Rate Adaptability:** Creates native `AudioTrack` dynamically using `audio.sampleRate` ($16,000\,\text{Hz}$ or $22,050\,\text{Hz}$).
- **Hindi Compatibility:** **100% compatible.** The playback engine processes raw PCM samples and has zero language-specific dependencies. Any audio generated by a future Hindi TTS model will play without modification.

---

## 7. M2 $\to$ M3 Integration & Hindi Failure Behavior

### A. Data Path Integrity
```
SpeechMessage(text="सामरिक संदेश", language="hi", timestamp=1726850002000L, confidence=0.88f)
  │
  ▼
SpeechMessageReceiver.onSpeechMessageReceived(message)  [Verbatim language & text passed]
  │
  ▼
SpeechMessagePlaybackCoordinator.processMessage(message) [Verbatim language & text passed]
  │
  ▼
SherpaOfflineTtsEngine.synthesize(text="सामरिक संदेश", language="hi")
```

### B. Verified Behavioral Scenarios

| Scenario | Input Language | Engine Execution | Result / Behavior |
| :--- | :--- | :--- | :--- |
| **English Message** | `"en"`, `"en-US"` | `isLanguageSupported` returns `true`. Synthesizes PCM float samples. | **Success:** Returns `TtsAudio`, streams to `AudioTrack`, plays through physical speaker. |
| **Hindi Message** | `"hi"`, `"hi-IN"` | `isLanguageSupported` returns `false`. Throws `UnsupportedTtsLanguageException("hi")`. | **Clean Rejection:** Playback engine is not invoked; error is caught and routed to `TransceiverListener.onError(TransceiverError.M3TtsError)`. Zero audio corruption. |

---

## 8. Build & Test State

### A. Unit Tests Execution
```powershell
.\gradlew testDebugUnitTest --rerun-tasks
```
- **Total Test Suites:** 32
- **Total Unit Tests Executed:** **343**
- **Passed:** **343 (100%)**
- **Failures:** **0**
- **Errors:** **0**
- **Skipped:** **0**

### B. Debug APK Compilation
```powershell
.\gradlew assembleDebug
```
- **Status:** **`BUILD SUCCESSFUL`** (35 actionable tasks up-to-date)

---

## 9. Evidentiary Audit Checklist

### FACT:
- English TTS model asset `vits-piper-en_US-lessac-low` is present in `app/src/main/assets/` (63,149,198 bytes, SHA-256: `8177731223C47C72764E7021866F549DC72B894ABC6B252C7F1CA0F8F65AD659`).
- **Hindi TTS model assets are NOT present in the codebase.**
- `SherpaOfflineTtsEngine` only accepts `"en"`, `"en-US"`, `"en-us"`, `"en_US"`.
- `SpeechMessagePlaybackCoordinator` passes received `text` and `language` verbatim to `TtsEngine`.
- `AndroidAudioTrackPlaybackEngine` converts PCM float samples to 16-bit PCM shorts and streams to Android `AudioTrack`.
- No Hindi TTS benchmark harness or Hindi TTS corpus exists in the repository.

### VERIFIED:
- The M2 $\to$ M3 boundary ([`SpeechMessageReceiverBoundaryTest`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/test/java/org/itantra/core/tts/SpeechMessageReceiverBoundaryTest.kt)) passes Devanagari Hindi text and `"hi"` language tag without alteration.
- Calling `synthesize(..., "hi")` deterministically throws [`UnsupportedTtsLanguageException`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/test/java/org/itantra/core/tts/SherpaOfflineTtsEngineTest.kt#L230).
- English TTS end-to-end execution on physical hardware passes with $\text{RTF} \approx 0.12 - 0.14$ on Device B (vivo I2304) and $\text{RTF} \approx 0.95 - 1.08$ on Device A (Lenovo TB-X6C6F) as documented in [`PHYSICAL_TTS_VALIDATION_01.md`](file:///c:/Users/saich/Downloads/SIH/PS26173/docs/validation/PHYSICAL_TTS_VALIDATION_01.md) and [`PHYSICAL_M2_M3_VALIDATION_01.md`](file:///c:/Users/saich/Downloads/SIH/PS26173/docs/validation/PHYSICAL_M2_M3_VALIDATION_01.md).

### NOT VERIFIED:
- Physical Hindi neural TTS speech synthesis.
- Physical Hindi speaker audio playback.
- Hindi TTS synthesis latency and RTF.
- Hindi pronunciation quality, intelligibility, or Devanagari text-to-phoneme accuracy.
- Complete end-to-end Hindi ASR $\rightarrow$ M2 $\rightarrow$ Hindi TTS pipeline.

---

## 10. Items Blocking Physical Hindi TTS Integration

1. **Missing Hindi Model Assets:** No Hindi Piper/VITS ONNX model (`model.onnx`, `tokens.txt`, `espeak-ng-data` with Hindi phoneme tables) exists in `app/src/main/assets/`.
2. **Missing Multi-Model Engine Routing:** `SherpaOfflineTtsEngine` does not support loading multiple language models or switching between English and Hindi based on the incoming language tag.
3. **Missing Hindi TTS Benchmark Harness:** No standardized Hindi prompt evaluation runner exists to measure initialization latency, synthesis latency, and RTF.
