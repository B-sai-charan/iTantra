# FINAL SIH Approved Metrics & Presentation Guidelines

**Document ID:** `FINAL_SIH_APPROVED_METRICS_02`  
**Date:** 2026-09-28  
**Scope:** Authoritative metric reference sheet and presentation guardrails for SIH judging  
**Production Code Changed:** **0 (NO CODE CHANGES)**  

---

## 1. Executive Summary & Audit Log

This document serves as the single source of truth for all quantitative claims, performance benchmarks, and architectural specifications presented during the Smart India Hackathon (SIH) evaluation of **iTantra**.

All metrics contained herein have been verified against physical on-device execution reports in `docs/validation/`. Unsubstantiated claims, diagnostic scratch artifacts, and unmeasured assumptions have been strictly pruned or classified.

### Audit Summary:
- **Source Reports Consulted:**
  - `M1_ASR_MODEL_SUITABILITY_14.md` (Vosk vs Whisper 5-case and live-mic comparison)
  - `PHYSICAL_HINDI_TTS_VALIDATION_01.md` (Production Hindi TTS on Lenovo TB-X6C6F)
  - `PHYSICAL_TTS_VALIDATION_01.md` (Production English TTS on Lenovo TB-X6C6F)
  - `DEVICE_VALIDATION_01.md` (Local speech pipeline & 6-case English benchmark)
  - `M1_LIVE_MIC_SPEECH_INTEGRITY_15.md` (Forensic listening and acoustic analysis)
  - `M1_ANDROID_AUDIO_PATH_CONTROL_16.md` (Android AudioRecord and AudioMode validation)
  - `M1_FINAL_TWO_PHONE_E2E_REHEARSAL_02.md` & `M1_TWO_PHONE_CONNECTIVITY_RECHECK_03.md` (Two-phone networking)
  - `PHYSICAL_TCP_VALIDATION_01.md` (M2 packet framing and TCP socket layer)
  - `UI_UX_IMPLEMENTATION_02.md` (Tactical UI layout & 365/365 unit test verification)

- **Metrics Retained:**
  - English STT Clean Reference: WER 0.00%, CER 0.00%, RTF 0.69–0.75 (`speech_test.wav`).
  - Unit Test Status: 365 tests executed, 0 failures.
  - APK Byte Size: 315,167,039 bytes (315.17 MB decimal / 300.57 MiB binary).
  - Production English TTS RTF: 0.92–1.11.

- **Metrics Corrected:**
  - **English STT Benchmark:** Replaced single clean-file 0.0% claim with the controlled 6-case physical-device benchmark (**WER: 8.29%**, **CER: 6.12%**, **Mean RTF: 0.8350**).
  - **Hindi STT Whisper Benchmark:** Formalized controlled 5-case FLEURS physical-device benchmark (**WER: 116.87%**, **CER: 119.39%**, **Mean RTF: 1.2294**).
  - **Hindi STT Vosk Benchmark:** Corrected erroneous "0% WER / RTF 0.125" presentation brief claim to verified diagnostic benchmark (**WER: 18.07%**, **CER: 8.79%**, **Mean RTF: 1.2397**), explicitly classified as a **diagnostic comparison**, NOT the production ASR selection.
  - **Production Hindi TTS:** Corrected uncontextualized scratch spike (RTF ~0.65) to physical production measurements: **P1: RTF 2.464** (including initial model asset staging), **P2: RTF 1.175**, **P3: RTF 1.316**.
  - **APK Size Notation:** Explicitly labeled as **315,167,039 bytes** (315.17 MB decimal / 300.57 MiB binary).

- **Metrics Removed / Excluded Due to Insufficient Evidence:**
  - Removed unsupported claims of "0% WER on Hindi Vosk".
  - Removed presentation of "120–250 bytes" as a measured network-bandwidth result (clarified as protocol framing size depending on UTF-8 payload length).
  - Removed unmeasured bandwidth reduction percentages and compression ratios.
  - Removed unmeasured Packet Delivery Ratio (PDR).
  - Removed claims of "zero latency" or "instantaneous speech".
  - Removed unverified hardware-root-cause assumptions.

---

## 2. Definitive Accuracy Comparison Table

| Test Condition | WER | CER | RTF | Exact Match | Evidentiary Meaning & Scope |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **Clean English Reference Fixture** (`speech_test.wav`) | **0.00%** | **0.00%** | **0.69 – 0.75** | 1/1 (100%) | **Clean reference fixture — not live microphone accuracy.** Validates Sherpa-ONNX C++ runtime, Mel filterbank, and INT8 decoder on studio digital PCM. |
| **Physical-Device Benchmark — Controlled English Fixtures** | **8.29%** | **6.12%** | **0.8350** | 1/6 (16.7%) | **Controlled physical-device benchmark** across 6 standardized LibriSpeech/TED audio cases executed on Lenovo TB-X6C6F. |
| **Controlled Physical-Device Hindi Benchmark (Whisper Tiny INT8)** | **116.87%** | **119.39%** | **1.2294** | 0/5 (0%) | **Controlled physical-device Hindi benchmark** across 5 FLEURS audio cases. Exhibits known INT8 Indic subword transliteration drift. |
| **Hindi Vosk Diagnostic Benchmark** (`vosk-model-small-hi-0.22`) | **18.07%** | **8.79%** | **1.2397** | 0/5 (0%) | **Diagnostic/model comparison, NOT the production ASR selection.** Proves Kaldi acoustic model operates cleanly on digital audio. |
| **Live Microphone Observations** (Lenovo TB-X6C6F Hardware) | **94.4% – 100.0%** | **75.4% – 98.5%** | **0.78 – 1.32** | 0/3 (0%) | **Live microphone observation in untreated room.** Hardware microphone attenuation and low capture gain (-40.8 dBFS) cause severe acoustic mismatch. |

---

## 3. Physical Subsystem Performance Summary

### A. Speech-to-Text (M1 STT)
- **Production Engine:** Sherpa-ONNX Whisper Tiny INT8 (`sherpa-onnx-whisper-tiny`).
- **Model Topology:** 39M parameter transformer quantized to INT8 (Encoder: 12.94 MB, Decoder: 89.86 MB).
- **Execution Mode:** 100% on-device CPU inference via ONNX Runtime.
- **Inference Latency:** 1,600 ms – 4,648 ms (depending on input utterance length; RTF ~0.70–0.84 on English, ~1.23 on Hindi).

### B. Voice Activity Detection (M1 VAD)
- **Production Engine:** Silero VAD v5 ONNX (`silero_vad.onnx`).
- **Frame Size:** 30 ms (512 samples at 16 kHz).
- **Processing Time:** < 1.5 ms per 30 ms audio chunk (< 5% CPU budget).
- **State Machine:** Deterministic speech onset detection with 400 ms trailing silence window.

### C. Text-to-Speech (M3 TTS)
- **Production Engine:** Sherpa-ONNX Piper VITS (Single-speaker neural models).
- **English Model:** `vits-piper-en_US-lessac-low` / `medium` (16 kHz mono output).
  - Synthesis Latency: 2,484 ms – 4,116 ms (RTF: **0.92 – 1.11**).
- **Hindi Model:** `vits-piper-hi_IN-priyamvada-medium` (22.05 kHz mono output).
  - P1 (First Run / Asset Staging): 12,071 ms (RTF: **2.464**).
  - P2 (Steady-State 6.8s speech): 8,009 ms (RTF: **1.175**).
  - P3 (Steady-State 6.2s speech): 8,142 ms (RTF: **1.316**).
- **Audio Output:** Android `AudioTrack` (`STREAM_MUSIC`, direct PCM streaming, complete lifecycle buffer drainage).

### D. Data Transport & Framing (M2 Transport)
- **Protocol:** Length-prefixed binary framing over local TCP sockets (default port: `42173`).
- **Protocol Frame Size:** Depends on UTF-8 payload length (~120–250 bytes typical per utterance).
- **Transport Reliability:** Validated across single-device loopback and automated test suites; physical two-device connection requires receiver application running in foreground on destination IP.
- **RF Bandwidth Utilization:** **Not measured** (requires RF instrumentation).

### E. Binary Footprint & Memory Profile
- **APK Exact Size:** `315,167,039 bytes` (315.17 MB decimal / 300.57 MiB binary).
- **Active Process Memory (PSS):** ~250 MB – 350 MB PSS on physical Android 12 hardware (`HNP097HQ`).
- **Native Heap Allocation:** ~90 MB baseline (Whisper + Silero loaded), ~150–160 MB peak during neural TTS synthesis.
- **Teardown Reclamation:** Verified clean deallocation of native JNI sessions upon `release()`.

---

## 4. Disaggregated Latency Breakdown

Do **NOT** add individual component latencies into a single aggregate number without an explicit synchronized stopwatch run.

| Pipeline Stage | Subsystem / Operation | Latency Range | Determinant Factor |
| :--- | :--- | :--- | :--- |
| **Stage 1: Capture & Segmentation** | `AudioRecord` + `Silero VAD` | Utterance length + 400 ms | Speaker speaking duration + silence tail |
| **Stage 2: STT Neural Inference** | `SherpaWhisperAsrEngine` | 1,600 ms – 4,648 ms | Audio duration ($\text{RTF} \approx 0.70 - 1.23$) |
| **Stage 3: M2 Framing & Transmission** | `SpeechMessage` + TCP Socket | < 50 ms (Local Wi-Fi) | Network link latency |
| **Stage 4: TTS Neural Synthesis** | `SherpaOfflineTtsEngine` | 2,484 ms – 4,116 ms (EN)<br>8,009 ms – 8,142 ms (HI) | Text length ($\text{RTF} \approx 0.92 - 1.32$) |
| **Stage 5: AudioTrack Playback** | `AndroidAudioTrack` | 2,624 ms – 6,868 ms | Synthesized audio duration |

---

## 5. Presentation Safety Guardrails

```
====================================================================================================
                                      APPROVED FOR PPT
====================================================================================================
✔ English STT Clean Reference: WER 0.00%, CER 0.00%, RTF 0.69–0.75
  (Explicitly labeled: "Clean reference fixture — not live microphone accuracy")
✔ English STT Controlled Benchmark: WER 8.29%, CER 6.12%, Mean RTF 0.8350 (6 test cases)
✔ Hindi STT Whisper Controlled Benchmark: Aggregate WER 116.87%, CER 119.39%, Mean RTF 1.2294 (5 cases)
✔ Hindi STT Vosk Diagnostic Comparison: Aggregate WER 18.07%, CER 8.79%, Mean RTF 1.2397
  (Explicitly labeled: "Diagnostic model comparison, not production engine")
✔ English Production TTS: Synthesis latency 2484–4116 ms, RTF 0.92–1.11
✔ Hindi Production TTS: P1 RTF 2.464 (model staging), P2 RTF 1.175, P3 RTF 1.316
✔ APK Exact Size: 315,167,039 bytes (315.17 MB decimal / 300.57 MiB binary)
✔ Protocol Frame Size: Depends on UTF-8 payload length (~120–250 bytes typical)
✔ System Architecture: 100% Offline, On-Device Edge Neural AI
✔ Automated Test Suite: 365 tests executed, 0 failures (100% pass rate)
====================================================================================================
```

```
====================================================================================================
                                    DO NOT PUT ON PPT
====================================================================================================
❌ DO NOT claim 0% WER for Hindi Vosk (Vosk achieved 18.07% WER on clean benchmarks and 100% on live mic).
❌ DO NOT claim Vosk is the production STT engine (Whisper Tiny INT8 is the production engine).
❌ DO NOT present "120–250 bytes" as measured RF bandwidth or claim bandwidth reduction percentages without a baseline.
❌ DO NOT claim measured Packet Delivery Ratio (PDR) or RF throughput (unmeasured).
❌ DO NOT claim "zero latency" or "instantaneous speech turnaround".
❌ DO NOT claim "invented custom neural compression codecs" (we transmit text representations).
❌ DO NOT present unverified hardware root causes as proven facts.
====================================================================================================
```

---

## 6. Definitive Project Claim

> **"The prototype demonstrates the complete offline AI and communication architecture, while controlled physical-device testing shows that live microphone STT accuracy remains the principal engineering limitation."**
