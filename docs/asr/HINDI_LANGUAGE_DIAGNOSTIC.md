# Diagnostic Report: Hindi Language Token Handling Controlled A/B Experiment

> **DIAGNOSTIC — NOT AN OFFICIAL BENCHMARK**
>
> This document records an isolated diagnostic investigation to determine whether the Hindi transcription failure observed in `sttbench22` is caused by the explicit `language="hi"` parameter path or the underlying Whisper Tiny INT8 model/runtime behavior.
> **No production code, decoding parameters, models, or official English baselines were modified.**

---

## 1. Objective

To conduct a strict, controlled A/B diagnostic on physical target hardware comparing:
- **Case A:** Explicit Hindi language specification (`language = "hi"`)
- **Case B:** Null / default language specification (`language = null`)

using the identical frozen Hindi audio fixture, without VAD, without microphone capture, and scoring with the Unicode-safe `IndicWerCalculator`.

---

## 2. Execution Device & Environment

| Parameter | Specification / Measured Value |
| :--- | :--- |
| **DEVICE MODEL** | Lenovo TB-X6C6F |
| **ANDROID OS** | Android 12 / API 31 |
| **TARGET ABI** | `arm64-v8a` |
| **EXECUTION APK** | `org.itantra.app` (Debug build) |
| **THREAD COUNT** | 2 CPU threads |
| **EXECUTION METHOD** | Direct memory PCM16 WAV injection (no microphone, no VAD) |

---

## 3. Model & Runtime Specification

| Component | Specification |
| :--- | :--- |
| **ASR ENGINE** | `SherpaWhisperAsrEngine` (backing `AsrEngine`) |
| **RUNTIME BACKEND** | Sherpa-ONNX v1.13.7 (Statically linked ONNX Runtime in `libsherpa-onnx-jni.so`) |
| **ENCODER ASSET** | `models/whisper-tiny/tiny-encoder.int8.onnx` |
| **DECODER ASSET** | `models/whisper-tiny/tiny-decoder.int8.onnx` |
| **TOKENS ASSET** | `models/whisper-tiny/tiny-tokens.txt` |
| **DECODING METHOD** | `greedy_search` |
| **FEATURE CONFIG** | 80 filterbanks, 16 kHz sample rate |

---

## 4. Frozen Audio Input Fixture

| Parameter | Value |
| :--- | :--- |
| **CASE ID** | `HI-001` |
| **SOURCE DATASET** | Google FLEURS (`google/fleurs`, config `hi_in`, test split, row ID 1766) |
| **ASSET FILENAME** | `speech_test_hi_001.wav` |
| **SHA256 CHECKSUM** | `3614bd9301b65e0fc8a12c43cdab7a403b834c7948337352f97a22e26475df43` |
| **AUDIO FORMAT** | 16 kHz, Mono, PCM16 |
| **SAMPLE COUNT** | 145,920 samples |
| **AUDIO DURATION** | 9,120 ms (9.120 s) |
| **VERBATIM REFERENCE** | `"कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,"` |
| **REFERENCE WORD COUNT** | 24 words |
| **REFERENCE CHAR COUNT** | 93 characters |

---

## 5. Sherpa-ONNX Language Path Inspection

Inspection of `SherpaWhisperAsrEngine.kt` reveals the architectural mechanism for language handling:

1. **Kotlin Call Site & Construction:**
   - The engine constructor accepts `defaultLanguage: String = "en"`.
   - In initialization (`init`), the language configuration is passed directly to `OfflineWhisperModelConfig`:
     ```kotlin
     val whisperConfig = OfflineWhisperModelConfig(
         encoder = encoderPath,
         decoder = decoderPath,
         language = defaultLanguage,
         task = "transcribe",
         tailPaddings = -1
     )
     ```
   - This `whisperConfig` is passed into `OfflineModelConfig` and `OfflineRecognizerConfig`, which instantiates `OfflineRecognizer(assetManager, config)`.

2. **Sherpa Configuration Field:**
   - Field `OfflineWhisperModelConfig.language` receives the language string and transmits it to the native C++ Sherpa-ONNX core via JNI.

3. **Value Differentiation (`"hi"` vs `null`):**
   - When configured with `language = "hi"`, Sherpa-ONNX injects the `<|hi|>` prompt token during autoregressive decoder initialization.
   - When configured with `defaultLanguage = "en"` or `language = ""` / `null`, Sherpa-ONNX defaults to English prompt token (`<|en|>`) or automatic language detection.

4. **Lifecycle & Scope:**
   - The language configuration is applied **ONCE during recognizer creation**.
   - The stream-level API `recognizer.createStream()` and `stream.acceptWaveform(...)` do not accept a dynamic per-utterance language parameter. The `transcribe(..., language: String?)` method uses its parameter solely as a fallback metadata field for `AsrResult.language` if `result.lang` is empty.

---

## 6. Case A Results (`language = "hi"`)

| Metric | Result |
| :--- | :--- |
| **CONFIGURED LANGUAGE** | `"hi"` |
| **DETECTED LANGUAGE** | `hi` |
| **RAW HYPOTHESIS** | `"ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrako"` |
| **NORMALIZED REFERENCE** | `"कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"` |
| **NORMALIZED HYPOTHESIS** | `"ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrako"` |
| **EXACT MATCH** | `false` |
| **WORD ALIGNMENT** | S=11, D=13, I=0, H=0, N=24 |
| **CHAR ALIGNMENT** | S=93, D=0, I=3, H=0, N=93 |
| **WORD ERROR RATE (WER)** | **100.00%** (24 errors / 24 words) |
| **CHAR ERROR RATE (CER)** | **103.23%** (96 errors / 93 characters) |
| **AUDIO DURATION** | 9,120 ms |
| **INFERENCE TIME** | 9,728 ms |
| **REAL-TIME FACTOR (RTF)** | 1.0667 |
| **EXCEPTIONS / CRASHES** | None (0 errors, 0 ANRs, 0 OOMs, 0 JNI errors) |

---

## 7. Case B Results (`language = null`)

| Metric | Result |
| :--- | :--- |
| **CONFIGURED LANGUAGE** | `null` (Engine initialized with default `"en"`) |
| **DETECTED LANGUAGE** | `en` |
| **RAW HYPOTHESIS** | `"Some people have a sense of the language of the language that they have a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of"` |
| **NORMALIZED REFERENCE** | `"कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"` |
| **NORMALIZED HYPOTHESIS** | `"some people have a sense of the language of the language that they have a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of"` |
| **EXACT MATCH** | `false` |
| **WORD ALIGNMENT** | S=24, D=0, I=30, H=0, N=24 |
| **CHAR ALIGNMENT** | S=93, D=0, I=85, H=0, N=93 |
| **WORD ERROR RATE (WER)** | **225.00%** (54 errors / 24 words) |
| **CHAR ERROR RATE (CER)** | **191.40%** (178 errors / 93 characters) |
| **AUDIO DURATION** | 9,120 ms |
| **INFERENCE TIME** | 9,620 ms |
| **REAL-TIME FACTOR (RTF)** | 1.0548 |
| **EXCEPTIONS / CRASHES** | None (0 errors, 0 ANRs, 0 OOMs, 0 JNI errors) |

---

## 8. Side-by-Side Controlled Comparison

| Case | Language | Detected Lang | Raw Hypothesis | WER | CER | RTF | Status |
| :--- | :--- | :--- | :--- | ---: | ---: | ---: | :--- |
| **A** | `hi` | `hi` | `"ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrako"` | 100.00% | 103.23% | 1.0667 | Clean execution |
| **B** | `null` | `en` | `"Some people have a sense of the language of the language that they have a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of a little bit of"` | 225.00% | 191.40% | 1.0548 | Clean execution |

---

## 9. Runtime Stability Assessment

Across both Case A and Case B on physical hardware:
- **Crash Count:** 0
- **Native JNI Errors:** 0
- **Uncaught Kotlin Exceptions:** 0
- **Application Not Responding (ANR):** 0
- **Out of Memory (OOM):** 0
- **Memory / Allocations:** Whisper engine allocated and released within normal working boundaries (~180 MB heap / native RSS).

---

## 10. Official English Baseline Integrity Verification

The official 6-case English LibriSpeech + TED-LIUM 3 benchmark suite was executed on the same hardware immediately adjacent to the diagnostic to verify baseline integrity:

- **Official Corpus WER:** **8.29%** (15 errors / 181 reference words) &mdash; **UNCHANGED & VERIFIED**
- **Official Corpus CER:** **6.12%** (48 errors / 784 reference characters) &mdash; **UNCHANGED & VERIFIED**
- **Official Exact Match Rate:** **16.67%** (1 / 6 cases) &mdash; **UNCHANGED & VERIFIED**
- **Per-Case Word Accuracy:**
  - EN-001: 0.00% WER
  - EN-002: 8.33% WER
  - EN-003: 9.09% WER
  - EN-004: 38.46% WER (ITN diagnostic: 15.38%)
  - EN-005: 9.09% WER
  - EN-006: 2.13% WER

The frozen baseline in `docs/asr/STT_BENCHMARK_BASELINE.md` remains intact and unaffected.

---

## 11. Evidence-Based Interpretation

### Classification Under Decision Rules:
**Classification: Rule A &mdash; Explicit language parameter materially changes output**
*(Language handling requires further investigation).*

### Detailed Evidence Rationale:
1. **Material Output Divergence:**
   - Setting `language = "hi"` directs the decoder to enter the Hindi language prompt subspace (`<|hi|>`), resulting in detected language `hi` and emitting pseudo-phonetic / romanized token repetition (`"ndrakoeta ndrakoeta..."`).
   - Setting `language = null` allows the decoder to default to English (`<|en|>`), resulting in detected language `en` and emitting an English lexical hallucination repetition loop (`"Some people have a sense of the language of the language that they have a little bit of..."`).
2. **Failure Characteristics:**
   - Neither configuration produces valid Devanagari Hindi text.
   - Both configurations fall into an autoregressive decoding repetition loop on Hindi acoustic input, indicating that greedy search decoding without repetition penalty / temperature fallback encounters high entropy or degenerate token probabilities on Indic audio.
3. **Implication:**
   - The explicit language parameter does actively reach the native Sherpa Whisper configuration and steers language detection and initial prompt tokens.
   - However, the underlying quantized Whisper Tiny asset fails to produce intelligible Devanagari Hindi under both explicit and default language routing.

---

## 12. What Remains Unproven

The diagnostic evidence bounds what is proven vs unproven:

1. **Unproven whether the quantized Whisper Tiny model contains full multilingual Indic vocabulary:**
   - It is not yet proven whether `models/whisper-tiny/tiny-tokens.txt` and `tiny-decoder.int8.onnx` represent the multilingual Whisper Tiny model (`openai/whisper-tiny`) or the English-only variant (`openai/whisper-tiny.en`).
2. **Unproven whether advanced decoding strategies resolve repetition loops:**
   - Parameters such as temperature fallback, repetition penalties, or beam search are disabled in greedy decoding and were not tested.
3. **Unproven across broader Indic audio samples:**
   - Only frozen case `HI-001` (FLEURS) was evaluated in this diagnostic.
4. **No model change is recommended at this stage:**
   - In accordance with the diagnostic constraint, model changes and parameter optimizations remain subjects for subsequent controlled investigations.
