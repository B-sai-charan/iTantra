# Official STT Benchmark Evidence Record & Baseline Audit Report

---

## 1. Project & System Metadata

| Parameter | Specification / Value |
| :--- | :--- |
| **PROJECT** | **iTantra** — Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links |
| **COMPONENT** | Offline STT / Whisper Tiny INT8 / Sherpa-ONNX |
| **BENCHMARK TYPE** | Offline WAV accuracy benchmark |
| **TARGET DEVICE** | Lenovo TB-X6C6F |
| **ANDROID OS** | Android 12 / API 31 |
| **ABI** | arm64-v8a |
| **SAMPLE RATE** | 16 kHz |
| **AUDIO FORMAT** | Mono PCM16 |
| **CORPUS** | Frozen 6-case English corpus |
| **EXECUTION ENVIRONMENT** | On-device physical execution (APK: `org.itantra`) |

---

## 2. Official Corpus Definition

The frozen benchmark corpus comprises 6 verified deterministic audio cases sourced from standard open evaluation corpora (LibriSpeech and TED-LIUM 3).

| Case ID | Dataset / Source | Asset Filename | Reference Transcript | Ref Words | Ref Chars | Exact Match | Strict WER | Strict CER | Inference Time | Audio Duration | RTF |
| :--- | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **EN-001** | LibriSpeech (`test-clean`) | `speech_test.wav` | "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels." | 18 | 87 | `true` | 0.00% | 0.00% | 4,807 ms | 6,625 ms | 0.7256 |
| **EN-002** | LibriSpeech (`test-clean`) | `speech_test_002.wav` | "GOD AS A DIRECT CONSEQUENCE OF THE SIN WHICH MAN THUS PUNISHED HAD GIVEN HER A LOVELY CHILD WHOSE PLACE WAS ON THAT SAME DISHONOURED BOSOM TO CONNECT HER PARENT FOR EVER WITH THE RACE AND DESCENT OF MORTALS AND TO BE FINALLY A BLESSED SOUL IN HEAVEN" | 48 | 202 | `false` | 8.33% | 1.49% | 12,178 ms | 16,715 ms | 0.7286 |
| **EN-003** | LibriSpeech (`test-clean`) | `speech_test_003.wav` | "YET THESE THOUGHTS AFFECTED HESTER PRYNNE LESS WITH HOPE THAN APPREHENSION" | 11 | 64 | `false` | 9.09% | 4.69% | 3,637 ms | 4,825 ms | 0.7538 |
| **EN-004** | TED-LIUM 3 (`DanBarber_2010`) | `speech_test_004.wav` | "well the last year this property had six hundred thousand birds on it" | 13 | 57 | `false` | 38.46% | 47.37% | 3,618 ms | 3,652 ms | 0.9907 |
| **EN-005** | TED-LIUM 3 (`DanielKahneman_2010`) | `speech_test_005.wav` | "goes very different ways depending on how you think and whether you think of the remembering self or you think of the experiencing self this is going to influence policy i think in years to come in the united states efforts are being made" | 44 | 195 | `false` | 9.09% | 5.64% | 11,615 ms | 12,610 ms | 0.9211 |
| **EN-006** | TED-LIUM 3 (`RobertGupta_2010U`) | `speech_test_006.wav` | "and he was talking about invisible demons and smoke and how someone was poisoning him in his sleep and i was afraid not for myself but i was afraid that i was going to lose him that he was going to sink into one of his states" | 47 | 179 | `false` | 2.13% | 2.23% | 15,427 ms | 17,330 ms | 0.8902 |

---

## 3. Official Aggregate Results

> ### OFFICIAL FROZEN BASELINE
>
> All values below are recorded from the physical on-device execution on Lenovo TB-X6C6F under strict normalization rules:
>
> - **Reference Words:** 181
> - **Reference Characters:** 784
> - **Total Audio Duration:** 61.757 s (61,757 ms)
> - **Total Inference Time:** 51.282 s (51,282 ms)
> - **Exact Matches:** 1 / 6
> - **Exact Match Rate:** 16.67%
> - **Corpus WER:** **8.29%** (15 errors / 181 words)
> - **Corpus CER:** **6.12%** (48 errors / 784 characters)
> - **Mean RTF:** **0.8350**
> - **Runtime Failures:** 0

---

## 4. Per-Case Results Detail

### Case Breakdown

1. **EN-001**
   - **Audio Duration:** 6,625 ms
   - **Inference Time:** 4,807 ms
   - **Real-Time Factor (RTF):** 0.7256
   - **Word Error Rate (WER):** 0.00% (0 / 18)
   - **Character Error Rate (CER):** 0.00% (0 / 87)
   - **Exact Match:** `true`
   - **Alignment:** S=0, D=0, I=0, H=18

2. **EN-002**
   - **Audio Duration:** 16,715 ms
   - **Inference Time:** 12,178 ms
   - **Real-Time Factor (RTF):** 0.7286
   - **Word Error Rate (WER):** 8.33% (4 / 48)
   - **Character Error Rate (CER):** 1.49% (3 / 202)
   - **Exact Match:** `false`
   - **Alignment:** S=2, D=1, I=1, H=45

3. **EN-003**
   - **Audio Duration:** 4,825 ms
   - **Inference Time:** 3,637 ms
   - **Real-Time Factor (RTF):** 0.7538
   - **Word Error Rate (WER):** 9.09% (1 / 11)
   - **Character Error Rate (CER):** 4.69% (3 / 64)
   - **Exact Match:** `false`
   - **Alignment:** S=1, D=0, I=0, H=10

4. **EN-004**
   - **Audio Duration:** 3,652 ms
   - **Inference Time:** 3,618 ms
   - **Real-Time Factor (RTF):** 0.9907
   - **Word Error Rate (WER):** 38.46% (5 / 13)
   - **Character Error Rate (CER):** 47.37% (27 / 57)
   - **Exact Match:** `false`
   - **Alignment:** S=2, D=1, I=2, H=10

5. **EN-005**
   - **Audio Duration:** 12,610 ms
   - **Inference Time:** 11,615 ms
   - **Real-Time Factor (RTF):** 0.9211
   - **Word Error Rate (WER):** 9.09% (4 / 44)
   - **Character Error Rate (CER):** 5.64% (11 / 195)
   - **Exact Match:** `false`
   - **Alignment:** S=4, D=0, I=0, H=40

6. **EN-006**
   - **Audio Duration:** 17,330 ms
   - **Inference Time:** 15,427 ms
   - **Real-Time Factor (RTF):** 0.8902
   - **Word Error Rate (WER):** 2.13% (1 / 47)
   - **Character Error Rate (CER):** 2.23% (4 / 179)
   - **Exact Match:** `false`
   - **Alignment:** S=1, D=0, I=0, H=46

---

## 5. Secondary ITN Diagnostic Analysis

> ### SECONDARY DIAGNOSTIC — NOT OFFICIAL
>
> The metrics presented in this section are derived from an Inverse Text Normalization (ITN) diagnostic pipeline (`ItnTextNormalizer` and `ItnWerCalculator`).
> **These values are purely diagnostic and DO NOT represent the official baseline WER/CER.**

### Diagnostic Metrics

- **ITN-Aware Corpus WER:** 6.63% (12 errors / 181 words)
- **ITN-Aware Corpus CER:** 3.57% (28 errors / 784 characters)
- **Mean ITN-Aware Per-Case WER:** 7.34%
- **Mean ITN-Aware Per-Case CER:** 4.39%

### EN-004 Numeric Representation Diagnostic

- **EN-004 Strict WER:** 38.46% &rarr; **EN-004 ITN-Aware WER:** 15.38%
- **EN-004 Strict CER:** 47.37% &rarr; **EN-004 ITN-Aware CER:** 12.28%

### Root Cause Explanation
In case **EN-004**, the acoustic utterance contains the spoken number:
- **Reference Transcription (Verbatim Words):** `"six hundred thousand"` (3 words, 20 characters)
- **ASR Acoustic Hypothesis (Digit Form):** `"600,000"` normalized to `"600000"` (1 token, 6 characters)

Under strict word-level Levenshtein alignment without ITN, this surface-form mismatch causes 2 substitutions and 1 deletion at the word level, plus 20 character mismatches. ITN canonicalizes both representations to numeric digits (`"600000"`), isolating genuine acoustic recognition errors from text formatting differences.

**Notice:** 6.63% is retained as a diagnostic metric to understand surface-form variance; the official baseline remains **8.29% WER**.

---

## 6. Benchmark Limitations

The benchmark report explicitly acknowledges the following scope limitations:

1. **Six English cases only:** The evaluation suite contains exactly six deterministic test cases.
2. **Not representative of all English speech:** The dataset does not capture diverse dialects, speaking registers, colloquialisms, or specialized terminology.
3. **Not a multilingual accuracy benchmark:** The evaluation is exclusively English; it does not measure accuracy for Indian multilingual speech (Hindi, Tamil, Telugu, Marathi, Bengali, Kannada, etc.).
4. **Not a noisy-environment benchmark:** The WAV fixtures are clean studio/presentation recordings and do not evaluate performance under low SNR, acoustic reverberation, or tactical field noise.
5. **Not a microphone/VAD end-to-end benchmark:** This suite benchmarks the ASR model on pre-recorded PCM16 audio buffers; it does not measure live microphone capture distortion or VAD clipping.
6. **WAV accuracy tests bypass the live microphone/VAD path:** The end-to-end pipeline includes microphone buffering and Silero VAD segmentation which are not exercised during WAV accuracy runs.
7. **No statistical confidence interval is claimed:** The sample size (N=6, 181 words) is insufficient for statistical confidence bounds or hypothesis testing.
8. **No generalization claim is made from six cases:** These results reflect performance on the frozen benchmark suite and do not imply universal model accuracy.
9. **ITN-aware metrics are diagnostic only:** ITN scoring is maintained purely as an explanatory analysis for numeric formatting and is not substituted for standard benchmark scoring.

---

## 7. Evidence Status Taxonomy

| Evidence Category | Scope & Status | Verified Items |
| :--- | :--- | :--- |
| **FACT** | Architectural & System Configuration | - Offline Whisper Tiny INT8 on Sherpa-ONNX runtime<br>- Lenovo TB-X6C6F (Android 12 / API 31, arm64-v8a)<br>- 16 kHz Mono PCM16 audio format<br>- Frozen 6-case corpus structure (181 words, 784 chars)<br>- Strict WER calculation methodology |
| **MEASURED RESULT** | Physical On-Device Execution Outputs | - Total audio duration: 61.757 s<br>- Total inference time: 51.282 s<br>- Mean RTF: 0.8350<br>- Official Corpus WER: 8.29%<br>- Official Corpus CER: 6.12%<br>- Exact match rate: 16.67% (1/6 cases)<br>- Zero runtime crashes / zero failures |
| **DIAGNOSTIC** | Secondary Analytical Metrics | - Secondary ITN-aware Corpus WER: 6.63%<br>- Secondary ITN-aware Corpus CER: 3.57%<br>- EN-004 representation effect analysis ("600,000" vs "six hundred thousand") |
| **NOT MEASURED** | Explicitly Excluded / Future Work | - Live microphone-to-ASR end-to-end acoustic latency: *Not measured yet.*<br>- Multilingual Indian language WER/CER across Indic datasets: *Not measured yet.*<br>- Bit-error-rate (BER) channel degradation impact on transcribed audio: *Not measured yet.*<br>- End-to-end transceiver radio packet roundtrip time: *Not measured yet.* |

---

## 8. Evaluator Summary

This document establishes the official frozen STT baseline for the **iTantra** offline neural transceiver platform:

- The project features a deterministic, reproducible offline STT benchmark harness integrated directly within the Android APK test and debug infrastructure.
- The benchmark was physically executed on the target **Lenovo TB-X6C6F** hardware running Android 12 (API 31, arm64-v8a).
- Across the frozen six-case English benchmark corpus (181 reference words, 784 reference characters, 61.757 seconds total audio), the physical benchmark achieved an official **Corpus WER of 8.29%** and **Corpus CER of 6.12%**.
- Real-time performance on-device demonstrated a **Mean Real-Time Factor (RTF) of 0.8350** (total inference execution time: 51.282 seconds).
- During the benchmark execution, runtime failures were zero, and engine memory management operated within stability boundaries.
- These results are strictly scoped to the frozen six-case English evaluation dataset and do not generalize across multilingual or tactical acoustic environments without further empirical evaluation.

---

## 9. Baseline Integrity Verification

- [x] Official metrics match physical `sttbench10` run exactly (Corpus WER: 8.29%, Corpus CER: 6.12%).
- [x] Reference counts verified: 181 words, 784 characters.
- [x] Per-case WER values verified: EN-001 (0.00%), EN-002 (8.33%), EN-003 (9.09%), EN-004 (38.46%), EN-005 (9.09%), EN-006 (2.13%).
- [x] Per-case CER values verified: EN-001 (0.00%), EN-002 (1.49%), EN-003 (4.69%), EN-004 (47.37%), EN-005 (5.64%), EN-006 (2.23%).
- [x] Total audio duration verified: 61.757 s (61,757 ms).
- [x] Total inference time verified: 51.282 s (51,282 ms).
- [x] Mean RTF verified: 0.8350.
- [x] No production source files modified.
