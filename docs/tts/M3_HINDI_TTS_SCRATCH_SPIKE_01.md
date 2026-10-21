# M3-HINDI-TTS-SCRATCH-SPIKE-01: Isolated Hindi Neural TTS Model Feasibility Report

**Document Version:** 1.0.0  
**Date:** September 23, 2026  
**Status:** COMPLETED — FEASIBILITY PROVEN  
**Target Model:** `vits-piper-hi_IN-priyamvada-medium`  
**Execution Environment:** Isolated `scratch/` staging + Physical Android ARM64 Device (`HNP097HQ`, Lenovo TB-X6C6F, Android 12 / API 31)  

---

## 1. FACT

### 1.1 Archive Verification
- **Archive Source URL:** `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-priyamvada-medium.tar.bz2`
- **Official Documentation Page:** `https://k2-fsa.github.io/sherpa/onnx/tts/all/Hindi/vits-piper-hi_IN-priyamvada-medium.html`
- **Staged Archive Location:** `scratch/tts_model/vits-piper-hi_IN-priyamvada-medium.tar.bz2`
- **Archive Byte Size:** `67,240,610` bytes (~64.12 MB)
- **Archive SHA-256:** `399D91CC97EB288725633261F26B715F9A971E3BF7EC4FA1D7910CD0080D37EB`

### 1.2 Extracted Files & Verification
- **Extraction Destination:** `scratch/tts_model/vits-piper-hi_IN-priyamvada-medium/`
- **Total Extracted Size:** `81,143,049` bytes (~77.38 MB across 114 files)
- **File Breakdown & Hashes:**
  | File / Directory | Size (Bytes) | SHA-256 Checksum |
  | :--- | :--- | :--- |
  | `hi_IN-priyamvada-medium.onnx` | `63,145,178` | `8871F3E07ADB6CA490F8DBCD3956A8647C53C35B5D0A1C2A8D097B3BF721A31B` |
  | `hi_IN-priyamvada-medium.onnx.json` | `4,973` | `5EFC0CCF7529F3528996D46E0FAC1F969F681D44A8E55BFA6236FF8841B5D52D` |
  | `MODEL_CARD` | `307` | `8CE0324466010A59127FE8CAB16416BEFCCA136ABF4B57EBD8AD5A2033C2021E` |
  | `tokens.txt` | `940` | `EF3A7E4A8D1AF0C9D4DC45AAAE1A6242EBE24A7ED6F3D025A49EB29682784C6D` |
  | `espeak-ng-data/` (Directory) | `17,991,651` | *(Contains 110 dictionary and phoneme tables)* |

### 1.3 Model Metadata (from `hi_IN-priyamvada-medium.onnx.json` & `MODEL_CARD`)
- **Language Code:** `hi_IN` (Hindi, India)
- **Language Native Name:** `"हिन्दी"`
- **Architecture:** VITS (`piper` ONNX computation graph)
- **Sample Rate:** `22,050 Hz`
- **Quality Tier:** `medium`
- **Speaker Count:** `1` (`num_speakers: 1`)
- **Speaker ID:** `0` (Voice: "Priyamvada", female)
- **Phoneme/Data Directory Requirements:** Requires `espeak-ng-data` directory containing eSpeak-NG phoneme tables.
- **Quantization Status:** Float32 (not quantized to int8; ONNX model size `63,145,178` bytes).

### 1.4 License & Provenance Verification
- **Model License:** Piper voice model published by PravalX under **CC-BY-NC-SA 4.0** (Non-Commercial, Attribution, Share-Alike).
- **Training Dataset:** AI4Bharat `indicnlp_corpus` (Indic TTS Hindi dataset).
- **Runtime / Engine License:** Sherpa-ONNX is licensed under **Apache 2.0**.
- **eSpeak-NG Data License:** GPLv3.

### 1.5 Existing Sherpa API Compatibility
Inspected `app/libs/sherpa-onnx-static-link-onnxruntime-1.13.7-arm64-sanitized.aar`:
- **`com.k2fsa.sherpa.onnx.OfflineTts`**: Present and fully functional.
- **`com.k2fsa.sherpa.onnx.OfflineTtsConfig`**: Present.
- **`com.k2fsa.sherpa.onnx.OfflineTtsModelConfig`**: Present.
- **`com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig`**: Present (supports `model`, `tokens`, `dataDir`, `noiseScale`, `noiseScaleW`, `lengthScale`).
- **No changes to the AAR or additional runtime downloads were needed or performed.**

---

## 2. MEASURED RESULT

All measurements below were experimentally obtained on the physical Android ARM64 hardware target:
- **Device Model:** Lenovo TB-X6C6F (`HNP097HQ`)
- **Hardware Architecture:** `aarch64` (arm64-v8a)
- **OS / API:** Android 12 (API Level 31)
- **Model Path:** `/data/local/tmp/vits-piper-hi_IN-priyamvada-medium`

### 2.1 Initialization Latency & Memory Profile
- **Native `OfflineTts` Initialization Time:** `6,963 ms`
- **Process Memory Profile (PSS):**
  - **PSS Before Initialization:** `50,007 KB` (~48.83 MB) | Native Alloc: `5.93 MB`
  - **PSS After Initialization:** `182,735 KB` (~178.45 MB) | Native Alloc: `89.45 MB`
  - **PSS Growth during TTS Model Load:** `+132,728 KB` (~129.62 MB)
  - **PSS After All Synthesis & Idle:** `141,889 KB` (~138.56 MB)

### 2.2 Prompt Synthesis & Performance Metrics

| Prompt # | Input Prompt Text | Sample Count | Sample Rate | Duration (s) | Synthesis Time (ms) | RTF (Real-Time Factor) | Finite / Bounds Check | AudioTrack Playback Status | Playback Duration (ms) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Prompt 1** | `"नमस्ते। यह iTantra का हिंदी टेक्स्ट टू स्पीच परीक्षण है।"` | `103,680` | `22,050 Hz` | `4.702 s` | `3,155 ms` | **`0.671`** | Valid (Min: `-0.7882`, Max: `+0.8056`, no NaN/Inf) | PASS (Audible) | `5,318 ms` |
| **Prompt 2** | `"यह एक ऑफलाइन संचार प्रणाली है जो कम बैंडविड्थ वाले नेटवर्क पर काम करती है।"` | `145,920` | `22,050 Hz` | `6.618 s` | `4,269 ms` | **`0.645`** | Valid (Min: `-0.8049`, Max: `+0.8230`, no NaN/Inf) | PASS (Audible) | `6,653 ms` |
| **Prompt 3** | `"आपातकालीन संदेश को हिंदी में स्पष्ट और विश्वसनीय रूप से सुनाया जाना चाहिए।"` | `141,568` | `22,050 Hz` | `6.420 s` | `4,172 ms` | **`0.650`** | Valid (Min: `-0.8103`, Max: `+0.8313`, no NaN/Inf) | PASS (Audible) | `6,457 ms` |

### 2.3 Audio Validation
- **Sample Validity:** 100% of samples are finite (`Float.isFinite == true`), with zero `NaN` and zero `Infinity`.
- **Sample Range:** Float values lie strictly within `[-1.0, 1.0]`. Peak amplitude ~`0.8313`.
- **Sample Rate:** `22,050 Hz` reported and produced natively.
- **AudioTrack Integration:** Successfully played through `AudioTrack` configured at `22,050 Hz` mono PCM 16-bit (`ENCODING_PCM_16BIT`). Clear, audible output was confirmed on device speakers.

---

## 3. NOT MEASURED

- **Formal Perceptual Voice Quality Score (MOS):** Mean Opinion Score testing was not formally evaluated across a human listening panel.
- **Formal Character/Word Error Rate of Synthesized Audio:** ASR-loopback WER was not benchmarked in this isolated spike.
- **Thermal Throttling & Sustained Multi-Hour Load:** Multi-hour continuous synthesis was not tested.

---

## 4. NOT VERIFIED

- **Subjective Pronunciation Quality:** Hindi pronunciation of domain-specific tactical/military acronyms (e.g. "iTantra") requires future human assessment.
- **Commercial Redistribution Rights:** Upstream dataset is licensed under **CC-BY-NC-SA 4.0** (Non-Commercial). Commercial distribution rights are **NOT VERIFIED** without clarification or custom training.

---

## 5. BLOCKED

- **None.** The isolated model spike executed from start to finish with 100% test completion and verified hardware playback.

---

## 6. PRODUCTION IMPACT CHECK

- **Production TTS Code Modified (`SherpaOfflineTtsEngine`, `TtsAudio`, `AudioPlaybackEngine`):** **NONE**
- **Production Asset Directory (`app/src/main/assets`):** **NO MODIFICATIONS**
- **Gradle Dependencies & `build.gradle.kts`:** **NO MODIFICATIONS**
- **Sherpa-ONNX Static AAR:** **NO MODIFICATIONS**
- **M1 STT Modules (`SherpaWhisperAsrEngine`, `SpeechPipeline`):** **NO MODIFICATIONS**
- **M2 Transport Modules (`TcpSocketTransport`, `SpeechMessageTransportAdapter`):** **NO MODIFICATIONS**
- **Existing English TTS (`vits-piper-en_US-lessac-low`):** **NO MODIFICATIONS** (English pipeline intact)
- **Unit Test Suite:** **343 / 343 PASSED** (`testDebugUnitTest` successful)
- **Build State:** **`assembleDebug` BUILD SUCCESSFUL**
