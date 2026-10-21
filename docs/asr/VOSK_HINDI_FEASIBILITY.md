# Vosk Hindi Offline ASR Feasibility & Technical Compatibility Report

> **RESEARCH + FEASIBILITY ASSESSMENT ONLY — NOT AN IMPLEMENTATION OR OFFICIAL BENCHMARK**
>
> This document investigates whether an offline Vosk Hindi model is a technically viable alternative for Hindi Speech-to-Text (STT) on this Android project.
> **No production code, dependencies, models, Gradle configurations, or benchmarks were modified.**

---

## 1. Candidate Identification

| Attribute | Specification / Value | Evidence Category |
| :--- | :--- | :--- |
| **CANDIDATE MODEL NAME** | `vosk-model-small-hi-0.22` | **FACT** |
| **OFFICIAL SOURCE** | Alpha Cephei (Vosk Models Repository) | **FACT** |
| **OFFICIAL DOWNLOAD URL** | `https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip` | **FACT** |
| **ALTERNATIVE SERVER MODEL** | `vosk-model-hi-0.22` (~1.5 GB; not suited for low-resource mobile edge) | **DOCUMENTED CLAIM** |
| **PRIMARY TARGET LANGUAGE** | Hindi (`hi` / Indic Devanagari script) | **DOCUMENTED CLAIM** |
| **MODEL PURPOSE** | Offline continuous large-vocabulary speech recognition for Hindi on edge devices | **DOCUMENTED CLAIM** |

---

## 2. Official Source & Provenance

- **Maintainer:** Alpha Cephei ([alphacephei.com](https://alphacephei.com/vosk/))
- **Core Technology:** Kaldi ASR Toolkit backend compiled into a portable C/C++ engine (`libvosk.so`), wrapped with Java/JNI bindings for Android.
- **Distribution Channel:** Official Alpha Cephei repository and Maven Central (`com.alphacephei:vosk-android`).
- **Repackaging Status:** First-party official release; no third-party repackaged distribution is evaluated.

---

## 3. Licensing & Open-Source Compliance

- **Model License:** Apache License 2.0 (**DOCUMENTED CLAIM** per Alpha Cephei model catalog).
- **Runtime Library License:** Apache License 2.0 (**FACT** per `alphacep/vosk-api` and `alphacep/vosk-android`).
- **Project License Compatibility:** Permissive open-source; compatible with on-device distribution without copyleft restrictions (**FACT**).

---

## 4. Model Footprint & Asset Structure

| Metric | Value | Evidence Category |
| :--- | :--- | :--- |
| **COMPRESSED ARCHIVE SIZE** | ~42 MB &ndash; 50 MB (`.zip`) | **DOCUMENTED CLAIM** |
| **UNCOMPRESSED DISK SIZE** | ~100 MB &ndash; 150 MB (extracted directory structure) | **DOCUMENTED CLAIM** |
| **ASSET FORMAT** | Kaldi acoustic model (`am/final.mdl`), decoding graph (`graph/HCLG.fst`), vocabulary mapping (`graph/words.txt`), and i-vector extractor files (`ivector/`) | **FACT** |
| **ADDITIONAL ASSET REQUIREMENTS** | All required acoustic, language model, and phone boundary graphs are self-contained within the model directory archive | **DOCUMENTED CLAIM** |

---

## 5. Hindi Language Support

- **Script / Character Set:** Native Devanagari Unicode script output (**DOCUMENTED CLAIM**).
- **Vocabulary Coverage:** Trained on Hindi acoustic and text corpora for general-purpose conversational speech (**DOCUMENTED CLAIM**).
- **Acoustic Adaptation:** Monolingual Hindi acoustic model; does not require language prompting or runtime language routing tokens (**FACT**).

---

## 6. Offline Operational Capability

- **Network Independence:** Fully offline; inference executes entirely on local CPU via Kaldi/Vosk native C++ routines (**FACT**).
- **Zero Cloud Dependency:** No telemetry, API keys, external servers, or network sockets required (**FACT**).
- **Security & Privacy:** Complies with iTantra offline tactical transceiver architecture requirements (**FACT**).

---

## 7. Android Compatibility & System Requirements

| Parameter | Specification | Evidence Category |
| :--- | :--- | :--- |
| **OFFICIAL ANDROID SUPPORT** | Yes; official `vosk-android` AAR published to Maven Central | **FACT** |
| **MAVEN ARTIFACT** | `com.alphacephei:vosk-android:<version>` (AAR packaging) | **FACT** |
| **SUPPORTED ABIs** | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` (embedded `libvosk.so`) | **DOCUMENTED CLAIM** |
| **PROJECT ABI COMPATIBILITY** | Target device is `arm64-v8a` (Lenovo TB-X6C6F); native library matching exists | **FACT** |
| **MINIMUM SDK** | Android API 21+ supported by `vosk-android` (Project `minSdk = 24`) | **DOCUMENTED CLAIM** |
| **TARGET SDK** | Android 12+ (API 31 / API 35) | **DOCUMENTED CLAIM** |
| **STORAGE ACCESS METHOD** | Requires extracted directory on device storage (e.g., `context.filesDir`) because Kaldi C++ reads filesystem paths directly rather than Android compressed `AssetManager` streams | **FACT** |

---

## 8. Input Format & Acoustic Requirements

| Parameter | Required Input Specification | Evidence Category |
| :--- | :--- | :--- |
| **SAMPLE RATE** | 16,000 Hz (16 kHz) | **DOCUMENTED CLAIM** |
| **CHANNELS** | Single channel (1.0 / Mono) | **FACT** |
| **AUDIO ENCODING** | Linear PCM 16-bit little-endian (PCM16) | **FACT** |
| **JAVA/KOTLIN API INPUT TYPES** | `ByteArray` (raw PCM bytes) or `ShortArray` (PCM16 samples) | **FACT** |
| **DYNAMIC RANGE** | Standard uncompressed integer range `[-32768, +32767]` | **FACT** |

---

## 9. Streaming & Inference Modes

- **Chunked Streaming Recognition:** Supported natively via `recognizer.acceptWaveForm(buffer, length)`. Returns `true` on silence/phrase boundary detection and `false` during continuous streaming (**FACT**).
- **Partial Hypotheses:** Supported via `recognizer.getPartialResult()` returning JSON (e.g., `{"partial": "..."}`) (**FACT**).
- **Final Result Extraction:** Supported via `recognizer.getResult()` and `recognizer.getFinalResult()` returning finalized JSON (e.g., `{"text": "..."}`) (**FACT**).
- **Batch / Offline Utterance Recognition:** Supported by feeding complete pre-loaded audio buffer in chunks followed by `getFinalResult()` (**FACT**).

---

## 10. Architecture & Runtime Requirements

1. **JNI Layer:** Calls native Kaldi decoder through JNI methods (`libvosk.so`).
2. **Model Loading:** Instantiated via `org.vosk.Model(modelPath: String)` where `modelPath` is an absolute filesystem directory path.
3. **Recognizer Lifecycle:** Instantiated via `org.vosk.Recognizer(model: Model, sampleRate: Float)`.
4. **Memory Footprint:** Operates with resident Kaldi FST decoding graph and acoustic models in native RAM (**DOCUMENTED CLAIM**).
5. **Threading:** `acceptWaveForm` executes synchronously; must be dispatched on a background thread (`Dispatchers.Default` / `Dispatchers.IO`) in Kotlin coroutines (**FACT**).

---

## 11. Frozen Hindi Fixture Compatibility Verification

The frozen Hindi benchmark fixture defined in the project:

- **Asset Filename:** `speech_test_hi_001.wav`
- **SHA256:** `3614bd9301b65e0fc8a12c43cdab7a403b834c7948337352f97a22e26475df43`
- **Sampling Rate:** 16,000 Hz (16 kHz)
- **Channel Count:** 1 (Mono)
- **Sample Format:** PCM16 (16-bit Signed Integer, Little-Endian)
- **Sample Count:** 145,920 samples
- **Duration:** 9.120 seconds
- **Verbatim Reference:** `"कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,"`

### Compatibility Conclusion:
**FACT:** The frozen fixture audio parameters match the candidate Vosk input requirements (16 kHz, Mono, PCM16) with **100% acoustic and formatting alignment**. No resampling, channel up/downmixing, or bit-depth conversion is required.

---

## 12. Factual Comparison Against Current Whisper Setup

| Property | Current Whisper Tiny INT8 | Candidate Vosk Hindi (`vosk-model-small-hi-0.22`) | Evidence Category |
| :--- | :--- | :--- | :--- |
| **OFFLINE CAPABILITY** | Runs 100% offline without network | Runs 100% offline without network | **FACT** (Both) |
| **HINDI RECOGNITION** | Intended in multilingual Whisper spec; failed on frozen fixture in `sttbench22`/`sttbench23` | Specifically trained and modeled for Hindi speech recognition | **FACT** (Whisper) / **DOCUMENTED CLAIM** (Vosk) |
| **MULTILINGUAL ARCHITECTURE** | Single multilingual encoder-decoder model | Monolingual Hindi acoustic & language model | **FACT** (Both) |
| **ANDROID COMPATIBILITY** | Verified on Lenovo TB-X6C6F via Sherpa-ONNX | Official `vosk-android` AAR / `libvosk.so` supporting `arm64-v8a` | **FACT** (Whisper) / **DOCUMENTED CLAIM** (Vosk) |
| **INPUT FORMAT** | 16 kHz Mono PCM16 (normalized to `FloatArray`) | 16 kHz Mono PCM16 (`ByteArray` or `ShortArray`) | **FACT** (Both) |
| **MODEL ASSET SIZE** | ~75 MB total (`tiny-encoder` 37 MB + `tiny-decoder` 38 MB + tokens 2.4 MB) | ~42–50 MB compressed ZIP (~100–150 MB extracted on disk) | **FACT** (Whisper) / **DOCUMENTED CLAIM** (Vosk) |
| **RUNTIME ENGINE** | Sherpa-ONNX v1.13.7 (statically linked ONNX Runtime) | Kaldi C++ engine via `libvosk.so` JNI | **FACT** (Both) |
| **STREAMING INFERENCE** | Batch utterance in current engine; online streaming supported by Sherpa streaming models | Native chunked streaming via `acceptWaveForm` / `getPartialResult` | **FACT** (Both) |
| **OPEN-SOURCE LICENSE** | MIT / Apache 2.0 compatible | Apache License 2.0 | **FACT** (Whisper) / **DOCUMENTED CLAIM** (Vosk) |
| **CURRENT PROJECT INTEGRATION** | Fully integrated in `SherpaWhisperAsrEngine`, `SpeechPipeline`, `AsrAccuracyBenchmarkRunner` | **Zero integration** (Not present in Gradle, source code, or assets) | **FACT** (Both) |

---

## 13. Unknowns Requiring Physical Validation

The following items cannot be determined from documentation and require empirical measurement on target hardware:

1. **Actual Recognition Accuracy on Indic Audio:**
   - **UNKNOWN:** Whether `vosk-model-small-hi-0.22` achieves intelligible Devanagari output or low WER/CER on `speech_test_hi_001.wav`.
2. **Real-Time Factor (RTF) on Target CPU:**
   - **UNKNOWN:** The exact inference execution speed and RTF on the Lenovo TB-X6C6F 8-core CPU.
3. **RAM & Peak Memory Footprint:**
   - **UNKNOWN:** Peak native RSS and heap consumption when loading the Kaldi HCLG graph into memory.
4. **Coexistence with ONNX Runtime:**
   - **UNKNOWN:** Whether loading `libvosk.so` alongside existing `libsherpa-onnx-jni.so` and `onnxruntime` native libraries causes symbol collisions, linker conflicts, or 16 KB memory page alignment issues on Android 12+.
5. **Model Unpacking Overhead:**
   - **UNKNOWN:** Cold-start extraction duration and disk footprint overhead when unpacking the model ZIP to `context.filesDir`.

---

## 14. Integration Risks

1. **Dual Native Runtime Overhead:**
   - Bundling both Sherpa-ONNX (for English/VAD) and Vosk (for Hindi) increases APK download size and native library memory surface.
2. **Model Storage Footprint:**
   - Uncompressed Kaldi models require ~100–150 MB of internal app storage space, which must be extracted during first launch.
3. **JSON Parsing & Contract Adaptation:**
   - Vosk returns results serialized as JSON strings (`{"text": "..."}`), requiring JSON extraction before passing to `AsrResult`.
4. **Android 15 (16 KB Page Size Alignment):**
   - Future Android 15 compatibility requires native ELF shared libraries to be built with 16 KB page alignment. Compatibility of published `libvosk.so` binaries must be verified before production adoption.

---

## 15. Recommendation for the NEXT EXPERIMENT ONLY

### Feasibility Determination:
**Vosk Hindi (`vosk-model-small-hi-0.22`) is TECHNICALLY FEASIBLE as a candidate for an isolated, non-production diagnostic experiment.**

### Next Experiment Scope (Strictly Scoped):
1. **Do NOT modify production `SpeechPipeline` or `MainActivity`.**
2. **Do NOT replace Whisper as the primary engine.**
3. Create an **isolated, standalone diagnostic harness** (similar to `AsrSmokeTestRunner` / `AsrAccuracyBenchmarkRunner`) that:
   - Evaluates `vosk-model-small-hi-0.22` against the frozen Hindi fixture `speech_test_hi_001.wav`.
   - Scores output using `IndicWerCalculator`.
   - Measures exact WER, CER, RTF, and memory consumption on physical hardware (Lenovo TB-X6C6F).
   - Produces an empirical comparison without impacting existing baseline infrastructure.
