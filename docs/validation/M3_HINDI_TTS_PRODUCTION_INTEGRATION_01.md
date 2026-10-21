# M3-HINDI-TTS-PRODUCTION-INTEGRATION-01: Production Hindi Neural TTS Integration Report

**Document Version:** 1.0.0  
**Date:** September 23, 2026  
**Status:** COMPLETE — INTEGRATION & UNIT VALIDATION PASSED  
**Target:** Production Integration of `vits-piper-hi_IN-priyamvada-medium` into M3 TTS Subsystem  

---

## A. Changes Made

1. **Model Asset Staging (`app/src/main/assets/vits-piper-hi_IN-priyamvada-medium/`):**
   - Transferred verified model files from `scratch/tts_model/vits-piper-hi_IN-priyamvada-medium/` into production asset directory.
   - 100% bit-for-bit cryptographic match verified.
2. **Third-Party Attribution Document (`docs/THIRD_PARTY_NOTICES.md`):**
   - Documented explicit attribution for AI4Bharat (`indicnlp_corpus`), PravalX, Rhasspy Piper, Blizzard Challenge, eSpeak-NG, Silero VAD, OpenAI Whisper, and Sherpa-ONNX under SIH 2026 prototype scope.
3. **Multi-Language TTS Engine Routing (`SherpaOfflineTtsEngine.kt`):**
   - Enhanced `SherpaOfflineTtsEngine` to support dual-model configurations for English (`vits-piper-en_US-lessac-low`, 16 kHz) and Hindi (`vits-piper-hi_IN-priyamvada-medium`, 22.05 kHz).
   - Added dynamic language tag routing recognizing `"hi"`, `"hi-IN"`, and `"hi_IN"`.
   - Implemented per-language lazy native session initialization to avoid redundant dual-model memory consumption.
   - Maintained full backwards compatibility with single-config constructors.
4. **Android Transceiver Composition Wiring (`AndroidTransceiverComposition.kt`):**
   - Configured `SherpaOfflineTtsEngine` with default English and Hindi model presets with `autoInitialize = false` (lazy startup).
5. **Unit Test Suite Expansion (`SherpaOfflineTtsEngineTest.kt` & `SpeechMessagePlaybackCoordinatorTest.kt`):**
   - Added unit tests covering Hindi routing, speed preservation, unsupported language rejection, model initialization failure handling, and end-to-end coordinator dispatching.

---

## B. Model Asset Verification

### Cryptographic Verification: Source vs. Production Assets
| File | Byte Size | Source Hash (`scratch/`) | Destination Hash (`app/src/main/assets/`) | Verification |
| :--- | :--- | :--- | :--- | :--- |
| `hi_IN-priyamvada-medium.onnx` | `63,145,178` | `8871F3E07ADB6CA490F8DBCD3956A8647C53C35B5D0A1C2A8D097B3BF721A31B` | `8871F3E07ADB6CA490F8DBCD3956A8647C53C35B5D0A1C2A8D097B3BF721A31B` | **FACT: EXACT MATCH** |
| `hi_IN-priyamvada-medium.onnx.json` | `4,973` | `5EFC0CCF7529F3528996D46E0FAC1F969F681D44A8E55BFA6236FF8841B5D52D` | `5EFC0CCF7529F3528996D46E0FAC1F969F681D44A8E55BFA6236FF8841B5D52D` | **FACT: EXACT MATCH** |
| `tokens.txt` | `940` | `EF3A7E4A8D1AF0C9D4DC45AAAE1A6242EBE24A7ED6F3D025A49EB29682784C6D` | `EF3A7E4A8D1AF0C9D4DC45AAAE1A6242EBE24A7ED6F3D025A49EB29682784C6D` | **FACT: EXACT MATCH** |
| `MODEL_CARD` | `307` | `8CE0324466010A59127FE8CAB16416BEFCCA136ABF4B57EBD8AD5A2033C2021E` | `8CE0324466010A59127FE8CAB16416BEFCCA136ABF4B57EBD8AD5A2033C2021E` | **FACT: EXACT MATCH** |
| `espeak-ng-data/` | `17,991,651` | *(110 dictionary tables)* | *(110 dictionary tables)* | **FACT: EXACT MATCH** |

---

## C. License & Attribution

- **Model Checkpoint:** `vits-piper-hi_IN-priyamvada-medium`
- **Model License:** **CC-BY-NC-SA 4.0** (Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International)
- **Dataset:** AI4Bharat `indicnlp_corpus`
- **Contributor:** PravalX ([`https://github.com/PravalX`](https://github.com/PravalX))
- **Attribution Location:** [`docs/THIRD_PARTY_NOTICES.md`](file:///c:/Users/saich/Downloads/SIH/PS26173/docs/THIRD_PARTY_NOTICES.md)
- **Scope of Use:** Non-commercial prototype submission and evaluation for Smart India Hackathon (SIH 2026). Commercial distribution rights are not claimed.

---

## D. Language Routing

### Routing Matrix in `SherpaOfflineTtsEngine`:
| Incoming Language Tag | Resolved Language Group | Target Model Asset | Native Output Sample Rate |
| :--- | :--- | :--- | :--- |
| `"en"`, `"en-US"`, `"en-us"`, `"en_US"` | `LanguageGroup.ENGLISH` | `vits-piper-en_US-lessac-low` | `16,000 Hz` mono PCM |
| `"hi"`, `"hi-IN"`, `"hi_IN"` | `LanguageGroup.HINDI` | `vits-piper-hi_IN-priyamvada-medium` | `22,050 Hz` mono PCM |
| Any other language code (`"es"`, `"fr"`, etc.) | `UNSUPPORTED` | *None* | Throws `UnsupportedTtsLanguageException` |

- **FACT:** The incoming language tag is evaluated without mutating the original `SpeechMessage.language` field across M2 or M3.

---

## E. Lifecycle & Memory Management

- **Engine State Machine:** `UNINITIALIZED` $\rightarrow$ `INITIALIZING` $\rightarrow$ `READY` $\rightarrow$ `RELEASED` / `FAILED`.
- **Per-Language Lazy Loading:**
  - Upon calling `initialize()`, the engine transitions to `READY`.
  - The native JNI session for English is created when the first English request is processed (or during eager init).
  - The native JNI session for Hindi is created on first Hindi synthesis, preventing unneeded memory consumption (~89 MB native heap) if only English is used.
- **Serialized Synthesis:** All synthesis requests across threads/coroutines are serialized through a Kotlin `Mutex`.
- **Idempotent Teardown:** Calling `release()` waits for active in-flight JNI generations to complete and destroys both English and Hindi native sessions cleanly.

---

## F. Unit Tests

All 346 unit tests across the entire repository pass with zero failures:
- **`SherpaOfflineTtsEngineTest`:** 20 tests passed
  - English routing and speech synthesis
  - Hindi (`hi`, `hi-IN`, `hi_IN`) routing and speech synthesis
  - Unsupported language rejection
  - Missing Hindi configuration rejection
  - Speed multiplier validation and preservation
  - Concurrent synthesis serialization
  - In-flight release synchronization
  - JNI failure handling & state transition
- **`SpeechMessagePlaybackCoordinatorTest`:** 13 tests passed
  - Hindi Devanagari text propagation without mutation
  - Playback engine delivery
- **`SpeechMessageReceiverBoundaryTest`:** 10 tests passed
- **`SpeechPipelineIntegrationTest`:** 7 tests passed
- **`TransceiverCoordinatorTest`:** 24 tests passed

---

## G. Build Result

- **CURRENT BUILD RESULT — Unit Tests:** `346 / 346 PASSED` (`.\gradlew testDebugUnitTest`)
- **CURRENT BUILD RESULT — Build Output:** `assembleDebug BUILD SUCCESSFUL`
- **CURRENT BUILD RESULT — APK Path:** `app/build/outputs/apk/debug/app-debug.apk`
- **CURRENT BUILD RESULT — APK Size:** `313,727,131` bytes (~299.19 MB)

---

## H. Static Integration Path

The complete production pipeline is verified statically and through unit tests:

$$\begin{aligned}
\text{Audio Input} &\longrightarrow \text{SpeechPipeline (M1)} \\
&\longrightarrow \text{SpeechMessage}(\text{text}=\text{"..."}, \text{language}=\text{"hi"}, \text{confidence}=0.98, \text{timestamp}=T) \\
&\longrightarrow \text{SpeechMessageTransportAdapter (M2)} \\
&\longrightarrow \text{TransceiverPacket (TCP)} \\
&\longrightarrow \text{SpeechMessageReceiver (M2/M3)} \\
&\longrightarrow \text{SpeechMessagePlaybackCoordinator (M3)} \\
&\longrightarrow \text{SherpaOfflineTtsEngine (M3) [Routes to Priyamvada]} \\
&\longrightarrow \text{TtsAudio}(22050\text{ Hz}, \text{mono FloatArray}) \\
&\longrightarrow \text{AndroidAudioTrackPlaybackEngine (M3)} \\
&\longrightarrow \text{Hardware AudioTrack Playback}
\end{aligned}$$

- **Verification:** Text, language tag, confidence score, and timestamps remain strictly immutable across all module boundaries.

---

## I. Historical Scratch Validation (Preserved Baseline)

**HISTORICAL MEASURED RESULT — SCRATCH SPIKE**  
Obtained on physical ARM64 test hardware (Lenovo TB-X6C6F, Android 12, API 31):
- **Initialization Latency:** `6,963 ms`
- **Process Memory Profile (PSS):** `50.0 MB` (before init) $\rightarrow$ `182.7 MB` (after init) $\rightarrow$ `141.9 MB` (idle)
- **Prompt 1 (`"नमस्ते। यह iTantra का हिंदी टेक्स्ट टू स्पीच परीक्षण है।"`, 4.702s):** Synthesis `3,155 ms` | **RTF: `0.671`** | AudioTrack: **PASS**
- **Prompt 2 (`"यह एक ऑफलाइन संचार प्रणाली है जो कम बैंडविड्थ वाले नेटवर्क पर काम करती है।"`, 6.618s):** Synthesis `4,269 ms` | **RTF: `0.645`** | AudioTrack: **PASS**
- **Prompt 3 (`"आपातकालीन संदेश को हिंदी में स्पष्ट और विश्वसनीय रूप से सुनाया जाना चाहिए।"`, 6.420s):** Synthesis `4,172 ms` | **RTF: `0.650`** | AudioTrack: **PASS**

---

## J. NOT YET VERIFIED / NOT MEASURED

- **NOT MEASURED:** Production memory footprint of APK bundled with both English and Hindi assets during simultaneous multi-hour capture and playback.
- **NOT VERIFIED:** Production-build on-device physical AudioTrack playback of newly bundled asset paths (requires on-device test).

---

## K. Remaining Physical Validation

1. Install production `app-debug.apk` onto physical ARM64 devices (Device A & Device B).
2. Execute physical Hindi TTS verification via `AndroidTransceiverComposition` to validate APK asset extraction and playback on hardware.
3. Validate complete physical end-to-end A $\to$ B pipeline (M1 Hindi STT $\to$ M2 TCP $\to$ M3 Hindi TTS $\to$ Speaker).
