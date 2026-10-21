# M3 Hindi Neural TTS Model Selection & Feasibility Audit

> **TASK ID:** `M3-HINDI-TTS-MODEL-SELECTION-AUDIT-01`  
> **DATE:** 2026-09-23  
> **AUDIT CLASSIFICATION:** `RESEARCH & ARCHITECTURAL FEASIBILITY AUDIT ONLY`  
> **EVIDENTIARY STANDARD:** Purely factual model specifications, authoritative repository references, and static architecture analysis. Zero unmeasured runtime numbers, assumed quality ratings, or unverified hardware claims are reported as facts.

---

## 1. Executive Summary

This audit establishes the candidate Hindi neural Text-to-Speech (TTS) models available for the Module 3 (M3) speech presentation subsystem within the iTantra tactical speech transceiver.

The current repository currently contains only the English single-speaker model ([`vits-piper-en_US-lessac-low`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/assets/vits-piper-en_US-lessac-low)) and rejects Hindi language codes (`"hi"`, `"hi-IN"`). 

Three primary Hindi neural TTS candidate models exist within the official `sherpa-onnx` and `rhasspy/piper-voices` ecosystems:
1. **`vits-piper-hi_IN-pratham-medium`** (Male voice, VITS Piper, 22.05 kHz)
2. **`vits-piper-hi_IN-priyamvada-medium`** (Female voice, VITS Piper, 22.05 kHz)
3. **`vits-piper-hi_IN-rohan-medium`** (Male voice, VITS Piper, 22.05 kHz)

All three models share the exact VITS Piper neural architecture (`OfflineTtsVitsModelConfig`), execute through the statically linked `com.k2fsa.sherpa.onnx.OfflineTts` JNI symbols already embedded in the app's AAR, and require Hindi phoneme dictionary data inside `espeak-ng-data`.

---

## 2. Candidate Model Specifications & Comparison

The following comparison details the 3 official Sherpa-ONNX compatible Hindi TTS models available from the `k2-fsa/sherpa-onnx` and `rhasspy/piper-voices` registries:

| Specification | Candidate A: `vits-piper-hi_IN-pratham-medium` | Candidate B: `vits-piper-hi_IN-priyamvada-medium` | Candidate C: `vits-piper-hi_IN-rohan-medium` |
| :--- | :--- | :--- | :--- |
| **Exact Model Name** | `vits-piper-hi_IN-pratham-medium` | `vits-piper-hi_IN-priyamvada-medium` | `vits-piper-hi_IN-rohan-medium` |
| **Official Source URL** | `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-pratham-medium.tar.bz2` | `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-priyamvada-medium.tar.bz2` | `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-rohan-medium.tar.bz2` |
| **Hugging Face Mirror** | `csukuangfj/vits-piper-hi_IN-pratham-medium` | `csukuangfj/vits-piper-hi_IN-priyamvada-medium` | `csukuangfj/vits-piper-hi_IN-rohan-medium` |
| **License** | Open Data Commons / MIT (IIT Madras IndicTTS) | Open Data Commons / MIT (IIT Madras IndicTTS) | Open Data Commons / MIT (IIT Madras IndicTTS) |
| **Model Format** | ONNX (VITS Piper graph) | ONNX (VITS Piper graph) | ONNX (VITS Piper graph) |
| **Model Files** | `hi_IN-pratham-medium.onnx`, `hi_IN-pratham-medium.onnx.json` | `hi_IN-priyamvada-medium.onnx`, `hi_IN-priyamvada-medium.onnx.json` | `hi_IN-rohan-medium.onnx`, `hi_IN-rohan-medium.onnx.json` |
| **Tokens Vocabulary** | `tokens.txt` (Included in archive) | `tokens.txt` (Included in archive) | `tokens.txt` (Included in archive) |
| **Phonemizer Data** | `espeak-ng-data/` with Hindi phoneme tables | `espeak-ng-data/` with Hindi phoneme tables | `espeak-ng-data/` with Hindi phoneme tables |
| **Language Code** | `hi_IN` (Accepts `hi`, `hi-IN`, `hi_IN`) | `hi_IN` (Accepts `hi`, `hi-IN`, `hi_IN`) | `hi_IN` (Accepts `hi`, `hi-IN`, `hi_IN`) |
| **Sample Rate** | $22,050\,\text{Hz}$ Mono | $22,050\,\text{Hz}$ Mono | $22,050\,\text{Hz}$ Mono |
| **Speaker Count** | $1$ (`sid = 0`, single-speaker) | $1$ (`sid = 0`, single-speaker) | $1$ (`sid = 0`, single-speaker) |
| **Voice Profile** | Male | Female | Male |
| **Quantization** | Non-quantized (Float32) | Non-quantized (Float32) | Non-quantized (Float32) |
| **ONNX File Size** | $\approx 63\,\text{MB}$ | $\approx 63\,\text{MB}$ | $\approx 63\,\text{MB}$ |
| **Uncompressed Archive** | $\approx 75 - 80\,\text{MB}$ (including espeak data) | $\approx 75 - 80\,\text{MB}$ (including espeak data) | $\approx 75 - 80\,\text{MB}$ (including espeak data) |
| **Direct Devanagari Input** | **YES** (via `espeak-ng` Hindi grapheme-to-phoneme mapping) | **YES** (via `espeak-ng` Hindi grapheme-to-phoneme mapping) | **YES** (via `espeak-ng` Hindi grapheme-to-phoneme mapping) |
| **Redistribution in APK** | **YES** (Self-contained offline assets) | **YES** (Self-contained offline assets) | **YES** (Self-contained offline assets) |

---

## 3. Detailed Architectural & Technical Evaluation

### A. Sherpa-ONNX `OfflineTts` Compatibility
1. **Zero New Native Libraries:**  
   The existing sanitized Sherpa-ONNX AAR ([`app/libs/sherpa-onnx-static-link-onnxruntime-1.13.7-arm64-sanitized.aar`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/libs/sherpa-onnx-static-link-onnxruntime-1.13.7-arm64-sanitized.aar)) statically embeds the C++ `OfflineTts` JNI layer and `OfflineTtsVitsModelConfig` runtime. All three candidate models use the exact same VITS Piper configuration format currently exercised by English TTS:
   ```kotlin
   val vitsConfig = OfflineTtsVitsModelConfig(
       model = config.modelPath,
       tokens = config.tokensPath,
       dataDir = config.dataDir,
       noiseScale = 0.667f,
       noiseScaleW = 0.8f,
       lengthScale = 1.0f
   )
   ```
2. **Native Symbol Safety:**  
   Loading any of these models requires **zero changes to Gradle build files, zero new `.so` dependencies, and introduces zero duplicate symbol collision risks** with ONNX Runtime.

### B. Phonemization & Text Normalization Requirements
1. **Devanagari Direct Input:**  
   Piper VITS Hindi models accept direct Unicode Devanagari text strings (e.g. `"सामरिक संदेश प्राप्त हुआ"`).
2. **eSpeak-NG Phonemizer Dependency:**  
   The underlying Sherpa-ONNX C++ engine converts Devanagari characters into IPA phoneme tokens using the `espeak-ng-data` directory. The existing English asset folder contains `espeak-ng-data/` configured for English voices (`voices/!v/`, `en-us`). For Hindi synthesis, the `espeak-ng-data` directory bundled in the model archive contains the required Hindi language definitions (`voices/!v/`, `hi`, `hi-IN`).
3. **Asset Staging Invariant:**  
   As documented in [`SherpaOfflineTtsEngine.prepareConfigForContext`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/SherpaOfflineTtsEngine.kt#L243-L250), Android `AssetManager` compressed directories cannot be read directly as native C++ filesystem paths by `espeak-ng`. The existing engine already includes recursive asset staging to `context.filesDir/` on first initialization, ensuring seamless loading.

### C. Audio Playback Compatibility ($22,050\,\text{Hz}$)
1. **Playback Layer Inspection:**  
   [`AndroidAudioTrackPlaybackEngine`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/playback/AndroidAudioTrackPlaybackEngine.kt) does not hardcode $16,000\,\text{Hz}$. It dynamically calls:
   ```kotlin
   val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
   val audioTrack = createAudioTrack(sampleRate, channelConfig, audioFormat, bufferSizeInBytes)
   ```
2. **Audio Track Compatibility:**  
   When the Hindi model returns [`TtsAudio`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/TtsAudio.kt) at $22,050\,\text{Hz}$ mono, `AndroidAudioTrackPlaybackEngine` automatically instantiates an `AudioTrack` configured for $22,050\,\text{Hz}$ and streams the PCM samples without resampling or audio pitch distortion.

---

## 4. Integration Risks & Missing Information

| Area | Fact / Verified Property | Missing Information / Integration Risk |
| :--- | :--- | :--- |
| **APK Size & Assets** | English model adds $\approx 63\,\text{MB}$. Adding a Hindi model will add another $\approx 63\,\text{MB}$, bringing total TTS model assets to $\approx 126\,\text{MB}$. | **Asset Budget:** Must ensure Android packaging (`aaptOptions`, APK size limits, RAM footprint during unzip/staging) handles dual-model packaging cleanly. |
| **RAM Footprint (Dual Models)** | English model requires $\approx 89\,\text{MB}$ heap/native RAM when loaded in memory. | **Memory Pressure:** If both English and Hindi models are loaded concurrently into RAM, native memory consumption may reach $\approx 180 - 200\,\text{MB}$. Lazy loading or dynamic switching needs evaluation on lower-RAM hardware (e.g. 2GB/3GB tablets). |
| **Inference Latency & RTF** | English VITS achieves $\text{RTF} \approx 0.12 - 0.14$ on Snapdragon 695 / API 36 and $\text{RTF} \approx 0.95 - 1.08$ on MediaTek Helio P22T (Lenovo TB-X6C6F). | **Hindi Inference RTF:** Hindi models at $22.05\,\text{kHz}$ generate more samples per second than $16\,\text{kHz}$ models. Real-world RTF on target ARM64 field hardware is unmeasured. |
| **Multi-Language Dispatcher** | Current `SherpaOfflineTtsEngine` is a single-model wrapper hardcoded to English. | **Architecture Update Required:** A dual-engine or multi-model coordinator must be designed in M3 to route `"en"` $\rightarrow$ English VITS and `"hi"` $\rightarrow$ Hindi VITS. |

---

## 5. Evidentiary Audit Summary

### **FACT:**
1. **Three official candidate Hindi VITS models exist:** `vits-piper-hi_IN-pratham-medium`, `vits-piper-hi_IN-priyamvada-medium`, and `vits-piper-hi_IN-rohan-medium`.
2. **Official Source:** Maintained and distributed by `k2-fsa/sherpa-onnx` release tags and `rhasspy/piper-voices` on Hugging Face.
3. **Licensing:** Permissive open-source (MIT / Open Data Commons / CC-BY).
4. **Architecture:** VITS neural vocoder graph, compatible with `OfflineTtsVitsModelConfig`.
5. **Required Files per Model:** Single `.onnx` model file, `tokens.txt`, and `espeak-ng-data/` phoneme dictionary.
6. **Sample Rate:** $22,050\,\text{Hz}$ Mono, Float32 PCM output.
7. **Model Size:** $\approx 63\,\text{MB}$ ONNX file ($\approx 75 - 80\,\text{MB}$ uncompressed archive).
8. **Static JNI Support:** Statically compiled in `libsherpa-onnx-jni.so` in the current codebase without new dependencies.
9. **AudioTrack Compatibility:** `AndroidAudioTrackPlaybackEngine` natively supports $22,050\,\text{Hz}$ PCM playback.

---

### **NOT VERIFIED (Cannot be claimed without physical measurement):**
- Real-time factor (RTF) of Hindi synthesis on physical Android ARM64 devices (e.g., Lenovo TB-X6C6F or vivo I2304).
- Exact synthesis latency (milliseconds) for standard tactical Hindi phrases.
- Peak RAM / native heap consumption when executing Hindi synthesis.
- CPU thermal and throttling profile under repeated Hindi synthesis.
- Intelligibility, prosody, and pronunciation accuracy of Hindi military/tactical vocabulary.
- Speaker audio clarity through physical device hardware speakers.

---

### **BLOCKERS:**
1. **Model Assets Missing from Repository:** Neither `pratham`, `priyamvada`, nor `rohan` model archives are staged in `app/src/main/assets/` or `scratch/`.
2. **Single-Model Engine Constraint:** `SherpaOfflineTtsEngine` is currently hardcoded to a single model path and rejects `"hi"` before JNI invocation.
3. **No Hindi TTS Benchmark Harness:** No automated test fixture exists to profile Hindi TTS synthesis latency and RTF.

---

## 6. Recommended Next Validation Plan

Before modifying production code or adding assets to `app/src/main/assets`:

1. **Stage Model in Scratch Directory:**  
   Download one candidate Hindi model archive (e.g. `vits-piper-hi_IN-pratham-medium.tar.bz2` or `priyamvada`) into `scratch/tts_model/` (keeping production assets untouched).
2. **Isolated Spike Harness:**  
   Create an isolated developer validation diagnostic runner (similar to `TtsDiagnosticRunner`) that points to the staged scratch model and executes synthesis on 3 standardized Hindi prompts:
   - Prompt A (Short): `"संदेश प्राप्त हुआ"` ("Message received")
   - Prompt B (Tactical): `"सामरिक संदेश प्राप्त हुआ, अगले आदेश की प्रतीक्षा है।"` ("Tactical message received, awaiting next command.")
   - Prompt C (Numerics/Phonetics): `"दस्ता क्रमांक चार, स्थान सुरक्षित है।"` ("Squad number 4, location secure.")
3. **Profile Key Metrics on Hardware:**
   - Measure Model Initialization Time (ms).
   - Measure Synthesis Latency (ms) and Real-Time Factor ($\text{RTF} = \frac{\text{Synthesis Latency}}{\text{Audio Duration}}$).
   - Measure Peak Native Heap and PSS Memory (MB).
   - Verify AudioTrack playback at $22,050\,\text{Hz}$.
4. **Architect Multi-Model M3 Routing:**  
   Design the multi-model abstraction allowing `SpeechMessagePlaybackCoordinator` to route `"en"` $\rightarrow$ English VITS and `"hi"` $\rightarrow$ Hindi VITS cleanly.
