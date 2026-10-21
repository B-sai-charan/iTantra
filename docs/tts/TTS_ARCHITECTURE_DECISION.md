# M3 Offline Android Text-to-Speech (TTS) Architecture Decision Record

> **STATUS:** `PROPOSED (SUBJECT TO ENGINE SPIKE VALIDATION)`  
> **TASK ID:** `tts-design-01`  
> **DECISION TOPIC:** Offline Text-to-Speech Engine Architecture & M3 Boundary for iTantra MVP  
> **DATE:** 2026-09-21  
> **EVIDENTIARY CLASSIFICATION LABELS:**  
> - **[DOCUMENTED FACT]:** Directly verifiable against official Android documentation, SDK source code, sherpa-onnx/ONNX Runtime binaries, or codebase.  
> - **[ENGINEERING INFERENCE]:** Architectural deduction based on project constraints and system boundaries.  
> - **[TARGET]:** Desired engineering objective for implementation.  
> - **[NOT MEASURED]:** Quantitative metric not yet benchmarked on hardware.  
> - **[REQUIRES VALIDATION]:** Property requiring physical engine spike execution and profiling.  
> - **[ASSUMPTION]:** Working hypothesis to be verified during prototyping.  

---

## 1. Executive Summary & Selected Architecture

**Selected Primary TTS Engine for MVP Validation:**  
**`sherpa-onnx` Offline Neural TTS (`com.k2fsa.sherpa.onnx.OfflineTts`) with VITS / Piper ONNX Models**

### Rationale:
1. **Zero Native Dependency Overhead:** The project's existing sanitized AAR ([`libs/sherpa-onnx-static-link-onnxruntime-1.13.7-arm64-sanitized.aar`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/libs/sherpa-onnx-static-link-onnxruntime-1.13.7-arm64-sanitized.aar)) **already compiles and statically links** the full `OfflineTts` JNI symbols and Java/Kotlin classes (`com.k2fsa.sherpa.onnx.OfflineTts`, `OfflineTtsConfig`, `OfflineTtsVitsModelConfig`, `OfflineTtsKokoroModelConfig`, `OfflineTtsMatchaModelConfig`) (**[DOCUMENTED FACT]**).
2. **Zero Native Collision Risk:** Eliminates all `.so` symbol collisions with `com.microsoft.onnxruntime:onnxruntime-android` because Sherpa and its internal ONNX runtime are statically embedded inside `libsherpa-onnx-jni.so` (**[DOCUMENTED FACT]**).
3. **100% Offline & Self-Contained:** Zero reliance on Google Play Services, external system TTS apps, or runtime network downloads (**[DOCUMENTED FACT]**).
4. **Multilingual VITS/Piper Model Availability:** High-quality offline VITS ONNX models exist for both English (`en_US`) and Hindi (`hi_IN`) (**[DOCUMENTED FACT]**).
5. **Decoupled Audio Generation:** Produces clean, raw PCM float sample buffers suitable for streaming or static playback via Android `AudioTrack` (**[DOCUMENTED FACT]**).

---

## 2. Context & Problem Statement

The iTantra tactical speech transceiver operates in offline field environments. Module 2 (M2) receives serialized speech packets over local Wi-Fi TCP sockets and reconstructs them into [`SpeechMessage`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/domain/SpeechMessage.kt) domain objects delivered across the [`SpeechMessageReceiver`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/SpeechMessageReceiver.kt) boundary (**[DOCUMENTED FACT]**).

Module 3 (M3) is responsible for converting these received text transcripts back into intelligible spoken audio output in real-time.

```
[ MODULE 2 — COMMUNICATION SUBSYSTEM ]
  Received TCP Wire Bytes
        │
        ▼
  PacketDecoder.decode() -> TransceiverPacket (CRC-32 Verified)
        │
        ▼
  TransceiverPacketMapper.toSpeechMessage() -> SpeechMessage (text, language, timestamp, confidence)
        │
        ▼
[ M2 -> M3 BOUNDARY ]
  SpeechMessageReceiver.onSpeechMessageReceived(message)
        │
        ▼
[ MODULE 3 — PRESENTATION & TTS SUBSYSTEM ]
  TtsEngine.synthesize(message.text, message.language)
        │
        ▼
  TtsAudio (Raw PCM Audio Samples @ SampleRate)
        │
        ▼
  AudioTrack (Android Hardware Speaker Output)
```

---

## 3. Candidate Technology Evaluation

| Candidate Engine | Offline Self-Containment | License | Android ARM64 Compatibility | Hindi Support | English Support | Model Footprint | Native Conflict Risk | Decision |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **A. Android Platform `TextToSpeech`** | **FAIL** (Requires system voice download) | Apache 2.0 (API) / Proprietary (Engine) | Native OS | Unreliable (Often missing offline on clean ROMs) | Generally pre-installed | 0 MB (APK) | None | **DISQUALIFIED AS PRIMARY** (Unreliable offline Hindi) |
| **B. Standalone Piper C++ JNI** | **PASS** (100% offline) | MIT | Requires custom JNI build | Available (`hi_IN` ONNX) | Extensive (`en_US` ONNX) | ~15–25 MB / model | High (ORT symbol collisions) | **DEFERRED** (Superseded by Sherpa-ONNX) |
| **C. `sherpa-onnx` Offline TTS** | **PASS** (100% offline) | Apache 2.0 | **ALREADY EMBEDDED IN AAR** | Available (VITS ONNX) | Available (VITS / Piper ONNX) | ~15–25 MB / model | **ZERO** (Static in existing AAR) | **SELECTED FOR MVP** |
| **D. eSpeak NG (Formant)** | **PASS** (100% offline) | GPL v3 | Requires custom JNI | Formant synthesis (Low quality) | Formant synthesis (Robotic) | < 5 MB | Low | **REJECTED** (Unacceptable voice quality) |

### Detailed Evaluation of Candidates:

### Candidate A: Android Platform `android.speech.tts.TextToSpeech`
- **Mechanism:** IPC bridge to system default engine (e.g., Google Speech Services, Samsung TTS, Pico) (**[DOCUMENTED FACT]**).
- **Critical Flaw:** On AOSP, custom ROMs, or stock devices where Hindi voice data was not downloaded over Wi-Fi beforehand, `isLanguageAvailable(Locale("hi", "IN"))` returns `LANG_MISSING_DATA` or `LANG_NOT_SUPPORTED` (**[DOCUMENTED FACT]**).
- **Evidentiary Finding:** Cannot guarantee zero-configuration offline Hindi vocalization in an emergency/tactical field demo (**[ENGINEERING INFERENCE]**).

### Candidate B: Standalone Piper C++ JNI
- **Mechanism:** Fast neural VITS inference via ONNX Runtime (**[DOCUMENTED FACT]**).
- **Evidentiary Finding:** High voice quality and low latency, but requires separate CMake compilation and introduces duplicate `libonnxruntime.so` linking conflicts with the existing Sherpa AAR (**[DOCUMENTED FACT]**).

### Candidate C: `sherpa-onnx` Offline TTS (`com.k2fsa.sherpa.onnx.OfflineTts`) — SELECTED
- **Mechanism:** Direct embedded neural TTS in the existing Sherpa-ONNX library supporting VITS, Piper, Matcha, and Kokoro ONNX model architectures (**[DOCUMENTED FACT]**).
- **Evidentiary Finding:** Uses the exact same `libsherpa-onnx-jni.so` and `classes.jar` already integrated for Whisper STT. Requires **zero Gradle changes, zero new JNI shared libraries, and zero duplicate symbol risks** (**[DOCUMENTED FACT]**).

### Candidate D: eSpeak NG
- **Mechanism:** Formant synthesis without neural acoustic models (**[DOCUMENTED FACT]**).
- **Evidentiary Finding:** Robotic, unnatural phoneme rendering with poor intelligibility compared to modern VITS neural synthesis (**[DOCUMENTED FACT]**).

---

## 4. Native Library Collision & Dependency Audit

Inspection of [`app/build.gradle.kts`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/build.gradle.kts) and [`app/libs/sherpa-onnx-static-link-onnxruntime-1.13.7-arm64-sanitized.aar`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/libs/sherpa-onnx-static-link-onnxruntime-1.13.7-arm64-sanitized.aar) reveals:

1. **Existing Embedded Native Symbols:**
   `classes.jar` inside the sanitized Sherpa AAR already contains:
   - `com.k2fsa.sherpa.onnx.OfflineTts`
   - `com.k2fsa.sherpa.onnx.OfflineTtsConfig`
   - `com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig`
   - `com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig`
   - `com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig`
   - `com.k2fsa.sherpa.onnx.TtsKt` (**[DOCUMENTED FACT]**)
2. **Native Shared Object:**  
   `jni/arm64-v8a/libsherpa-onnx-jni.so` already contains the compiled C++ `OfflineTts` implementation with statically linked internal ONNX Runtime routines (**[DOCUMENTED FACT]**).
3. **Collision Risk:**  
   **ZERO.** No new AARs, no external `.so` files, and no additional JNI libraries are required (**[DOCUMENTED FACT]**).

---

## 5. Model Strategy & Footprint Targets

### Model Selection for MVP:
1. **English Model:** `vits-piper-en_US-lessac-low` or `vits-piper-en_US-lessac-medium` (**[TARGET]**).
   - `model.onnx` (~15 MB - 25 MB low / ~60 MB medium)
   - `tokens.txt` (~4 KB)
   - `espeak-ng-data/` phoneme dictionary (~3 MB)
   - License: MIT / Public Domain
2. **Hindi Model:** `vits-piper-hi_IN-cora-low` or `vits-piper-hi_IN-cora-medium` (**[TARGET]**).
   - `model.onnx` (~15 MB - 25 MB low)
   - `tokens.txt` (~5 KB)
   - `espeak-ng-data/` phoneme dictionary
   - License: Open-source (Apache 2.0 / CC-BY)

### Storage & Packaging Policy:
- **Location:** Models will be packaged in `app/src/main/assets/models/tts/` or staged into app private storage (`context.filesDir/models/tts/`) on first boot (**[TARGET]**).
- **APK Size Impact:** Target $\le 45\text{ MB}$ total for initial English + Hindi low-quantized models combined (**[TARGET]**).
- *Strict Rule:* No model files are committed to production assets during this design task (**[DOCUMENTED FACT]**).

---

## 6. Proposed M3 Abstraction Design (`TtsEngine`)

The M3 subsystem will expose a clean, decoupled abstraction:

```kotlin
package org.itantra.core.tts

/**
 * Minimal offline Text-To-Speech engine abstraction for the M3 subsystem.
 */
interface TtsEngine : AutoCloseable {

    /**
     * Initializes the underlying neural TTS model and phonemizer.
     *
     * @return [Result.success] if ready for synthesis, or [Result.failure] with error.
     */
    fun initialize(): Result<Unit>

    /**
     * True if the engine is initialized and ready to synthesize speech.
     */
    val isInitialized: Boolean

    /**
     * Synthesizes input text into a raw PCM audio buffer.
     *
     * @param text The plain text to synthesize.
     * @param language Optional target language code (e.g. "en", "hi").
     * @param speakerId Speaker identifier for multi-speaker models (default: 0).
     * @param speed Playback speed multiplier (default: 1.0f).
     * @return [TtsAudio] containing raw PCM samples, sample rate, and duration.
     */
    suspend fun synthesize(
        text: String,
        language: String? = null,
        speakerId: Int = 0,
        speed: Float = 1.0f
    ): TtsAudio

    /**
     * Releases native runtimes and model memory.
     */
    override fun close()
}

/**
 * Container for synthesized raw PCM audio output.
 *
 * @property samples Raw 32-bit float or 16-bit PCM audio samples.
 * @property sampleRate Sampling rate in Hz (e.g., 22050 Hz or 16000 Hz).
 * @property durationMs Duration of synthesized speech in milliseconds.
 * @property synthesisTimeMs Wall-clock inference time in milliseconds.
 */
data class TtsAudio(
    val samples: FloatArray,
    val sampleRate: Int,
    val durationMs: Long,
    val synthesisTimeMs: Long
)
```

### Rationale for Buffer Synthesis over Direct Playback:
1. **Decoupled Hardware Layer:** Separating audio generation from audio playback enables deterministic unit testing on standard JVM test runners without requiring a physical Android audio device (**[ENGINEERING INFERENCE]**).
2. **Audio Post-Processing & Telemetry:** Enables volume scaling, telemetry latency logging, and visual waveform rendering in future UI tasks (**[ENGINEERING INFERENCE]**).

---

## 7. Audio Output Architecture (`AudioTrack`)

```
TtsAudio (FloatArray @ sampleRate)
      │
      ▼
AudioTrack (Android Hardware Speaker Output)
  - AudioAttributes: USAGE_MEDIA / USAGE_VOICE_COMMUNICATION, CONTENT_TYPE_SPEECH
  - AudioFormat: ENCODING_PCM_FLOAT, CHANNEL_OUT_MONO
  - Sample Rate: Matching TtsAudio.sampleRate (typically 22050 Hz or 16000 Hz)
  - Mode: AudioTrack.MODE_STATIC (for discrete utterances) or MODE_STREAM
```

### Playback & Interruption Policy:
- **Status:** **`NOT DEFINED YET — TTS PLAYBACK POLICY TO BE DECIDED DURING M3 IMPLEMENTATION.`**  
- Inbound messages arriving while audio is actively playing may either be queued sequentially or interrupt current speech depending on future emergency priority specifications (**[ENGINEERING INFERENCE]**).

---

## 8. Language Routing Strategy

When [`SpeechMessageReceiver.onSpeechMessageReceived(message)`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/SpeechMessageReceiver.kt) is invoked:

1. **`"en"`, `"en-US"`, `"en-GB"`:** Routes to English VITS voice model (`en_US`) (**[TARGET]**).
2. **`"hi"`, `"hi-IN"`:** Routes to Hindi VITS voice model (`hi_IN`) (**[TARGET]**).
3. **Unsupported / Unknown Languages (`"und"`, `"unknown"`, etc.):**  
   - Policy: Do **not** silently mispronounce with wrong phoneme tables (**[ENGINEERING INFERENCE]**).
   - In MVP, emit an error log / telemetry event and bypass synthesis until an appropriate model is installed (**[TARGET]**).

---

## 9. Local TTS Validation Plan (Spike Test Battery)

When executing the subsequent implementation spike, the following deterministic test fixtures will be evaluated:

### Test Fixtures:
- **English Test Utterance:** `"Tactical command acknowledged, standing by for coordinates."`
- **Hindi Test Utterance:** `"सामरिक संदेश प्राप्त हुआ, अगले आदेश की प्रतीक्षा है।"`

### Metrics to Benchmark & Report:
1. **Model Load Time:** Milliseconds to initialize `OfflineTts` on device boot (**[NOT MEASURED]**).
2. **Synthesis Latency:** Time from `synthesize()` call to `TtsAudio` buffer emission (**[NOT MEASURED]**).
3. **Real-Time Factor (RTF):** $\text{RTF} = \frac{\text{Synthesis Time (ms)}}{\text{Audio Duration (ms)}}$ (Target: $\text{RTF} \le 0.30$ on ARM64) (**[TARGET]**).
4. **Memory Footprint:** Heap and native RSS allocation while models are loaded (**[NOT MEASURED]**).
5. **Intelligibility & Pronunciation:** Qualitative assessment of Hindi and English audio output (**[REQUIRES VALIDATION]**).

---

## 10. Summary of Architectural Decisions & Invariants

1. **Primary Engine:** `sherpa-onnx` Offline Neural TTS (`com.k2fsa.sherpa.onnx.OfflineTts`).
2. **Model Format:** VITS / Piper ONNX models (`model.onnx` + `tokens.txt` + `espeak-ng-data`).
3. **Zero Dependency Changes:** Reuses existing embedded native library `libsherpa-onnx-jni.so`.
4. **Pure Boundary:** Consumes frozen [`SpeechMessage`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/domain/SpeechMessage.kt) over [`SpeechMessageReceiver`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/SpeechMessageReceiver.kt).
5. **Decoupled Synthesis:** Produces `TtsAudio` buffers before passing to `AudioTrack`.
6. **Playback Policy:** Explicitly deferred to M3 implementation task.
