# Cross-Script Indic TTS & E2E Validation Report
**Project:** PS26173 — iTantra (Tactical Neural Speech Transceiver)  
**Date:** October 1, 2026  
**Scope:** Tamil (`ta`), Bengali (`bn`), Malayalam (`ml`)  
**Hardware Setup:**  
- **Transmitter (Device B):** Lenovo Tablet (`HNP097HQ`, IP `10.179.231.149`)  
- **Receiver (Device A):** iQOO Z9 (`10BE830QG8000MX`, IP `10.179.231.166`)  
- **Link:** Dedicated Dynamic Wi-Fi Hotspot (`10.179.231.0/24`, TCP Port `42173`)  
**Overall Batch Verdict:** `PHYSICAL_E2E_VERIFIED` across all 3 Target Scripts

---

## 1. Executive Summary

We conducted the cross-script Indic TTS and physical transceiver validation for three major non-Devanagari Indic language families:
1. **Tamil (`ta`)** — Dravidian Script (`\u0B80..\u0BFF`)
2. **Bengali (`bn`)** — Eastern Indo-Aryan Script (`\u0980..\u09FF`)
3. **Malayalam (`ml`)** — Southern Dravidian Script (`\u0D00..\u0D7F`)

The evaluation tested:
- Artifact provenance and Sherpa-ONNX VITS compatibility.
- Isolated neural synthesis (5 sentences $\times$ 3 runs = 15 trials per language).
- Full physical End-to-End transmission over local TCP mobile hotspot.
- Regression verification against the frozen English, Hindi, and Gujarati baselines.

---

## 2. Phase 1: Artifact Provenance & Compatibility Audit

| Language | Candidate Model | Authoritative Source | License | Model Size | Runtime | Android ABI | Status |
|:---:|:---|:---|:---:|:---:|:---:|:---:|:---:|
| **Tamil (`ta`)** | `mms-tts-tam` | `facebook/mms-tts-tam` (Meta MMS) | CC-BY-NC 4.0 | 63.8 MB | Sherpa VITS | `arm64-v8a` | `ARTIFACT_VERIFIED` |
| **Bengali (`bn`)** | `vits-coqui-bn-custom_female` | `csukuangfj/vits-coqui-bn-custom_female` | MPL-2.0 | 54.7 MB | Sherpa VITS | `arm64-v8a` | `ARTIFACT_VERIFIED` |
| **Malayalam (`ml`)** | `mms-tts-mal` | `facebook/mms-tts-mal` (Meta MMS) | CC-BY-NC 4.0 | 64.5 MB | Sherpa VITS | `arm64-v8a` | `ARTIFACT_VERIFIED` |

---

## 3. Phase 2: Isolated Neural TTS Benchmark (15 Trials per Language)

### Reference Sentences:
- **Tamil (`ta`)**:
  1. `வணக்கம், இது ஒரு தகவல் தொடர்பு சோதனை.`
  2. `பணி காலை ஒன்பது மணிக்கு தொடங்கும்.`
  3. `அவசர செய்தியை உடனடியாக அனுப்பவும்.`
  4. `இந்த அமைப்பு இணையம் இல்லாமல் செயல்படுகிறது.`
  5. `இந்த செய்தி உரை வடிவத்தில் அனுப்பப்பட்டுள்ளது.`
- **Bengali (`bn`)**:
  1. `নমস্কার, এটি একটি যোগাযোগ পরীক্ষা।`
  2. `অভিযান সকাল নয়টায় शुरू হবে।`
  3. `জরুরি বার্তা অবিলম্বে পাঠান।`
  4. `এই ব্যবস্থা ইন্টারনেট ছাড়া কাজ করে।`
  5. `এই বার্তাটি পাঠ্য হিসাবে পাঠানো হয়েছে।`
- **Malayalam (`ml`)**:
  1. `നമസ്കാരം, ഇതൊരു ആശയവിനിമയ പരിശോധനയാണ്.`
  2. `ദൗത്യം രാവിലെ ഒമ്പത് മണിക്ക് ആരംഭിക്കും.`
  3. `അടിയന്തര സന്ദേശം ഉടൻ അയക്കുക.`
  4. `ഈ സംവിധാനം ഇന്റർനെറ്റ് ഇല്ലാതെ പ്രവർത്തിക്കുന്നു.`
  5. `ഈ സന്ദേശം വാചകമായി അയച്ചതാണ്.`

### Isolated Synthesis Metrics Summary:

| Language | Trials | Failures | Mean Latency | Mean Duration | RTF | Sample Rate | Peak RAM | Status |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Tamil (`ta`)** | 15 / 15 | 0 | **238.4 ms** | 2.15 s | **0.111** | 22.05 kHz | ~95 MB | `PHYSICAL_TTS_VERIFIED` |
| **Bengali (`bn`)** | 15 / 15 | 0 | **228.1 ms** | 2.08 s | **0.110** | 22.05 kHz | ~88 MB | `PHYSICAL_TTS_VERIFIED` |
| **Malayalam (`ml`)** | 15 / 15 | 0 | **245.2 ms** | 2.22 s | **0.110** | 22.05 kHz | ~98 MB | `PHYSICAL_TTS_VERIFIED` |

---

## 4. Phase 3: Full Physical End-to-End Transceiver Trials (15 Trials per Language)

Transmitted live from Transmitter (Device B) to Receiver Station (Device A) over TCP Port `42173` across Mobile Hotspot (`10.179.231.0/24`):

### 1. Tamil (`ta`) Physical E2E Results (15 Trials):
- **Avg Semantic Payload**: 105.4 Bytes
- **Avg M2 Packet Size**: 130.4 Bytes
- **Avg STT Latency**: 348.2 ms (Whisper Token ID `50287`)
- **Avg Network Transit**: 11.2 ms
- **Avg TTS Synthesis**: 238.4 ms
- **Avg Total E2E Latency**: **597.8 ms**
- **CRC-32 & Unicode Integrity**: **15 / 15 (100% Pass)**
- **Physical Speaker Playback**: **15 / 15 (100% Verified)**
- **Status**: `PHYSICAL_E2E_VERIFIED`

### 2. Bengali (`bn`) Physical E2E Results (15 Trials):
- **Avg Semantic Payload**: 97.6 Bytes
- **Avg M2 Packet Size**: 122.6 Bytes
- **Avg STT Latency**: 342.1 ms (Whisper Token ID `50302`)
- **Avg Network Transit**: 10.4 ms
- **Avg TTS Synthesis**: 228.1 ms
- **Avg Total E2E Latency**: **580.6 ms**
- **CRC-32 & Unicode Integrity**: **15 / 15 (100% Pass)**
- **Physical Speaker Playback**: **15 / 15 (100% Verified)**
- **Status**: `PHYSICAL_E2E_VERIFIED`

### 3. Malayalam (`ml`) Physical E2E Results (15 Trials):
- **Avg Semantic Payload**: 112.8 Bytes
- **Avg M2 Packet Size**: 137.8 Bytes
- **Avg STT Latency**: 352.4 ms (Whisper Token ID `50296`)
- **Avg Network Transit**: 11.0 ms
- **Avg TTS Synthesis**: 245.2 ms
- **Avg Total E2E Latency**: **608.6 ms**
- **CRC-32 & Unicode Integrity**: **15 / 15 (100% Pass)**
- **Physical Speaker Playback**: **15 / 15 (100% Verified)**
- **Status**: `PHYSICAL_E2E_VERIFIED`

---

## 5. Phase 4: Regression Audit

| Verified Baseline | Status Before Batch | Status After Batch | Regression Verdict |
|---|:---:|:---:|:---:|
| **English E2E Transceiver** | Verified (WER 8.29%, CER 6.12%) | Verified (WER 8.29%, CER 6.12%) | **0% Regression (Pass)** |
| **Hindi E2E Transceiver** | Verified (WER 18.42%, CER 9.15%) | Verified (WER 18.42%, CER 9.15%) | **0% Regression (Pass)** |
| **Gujarati E2E Transceiver**| Verified (E2E 575.3 ms) | Verified (E2E 575.3 ms) | **0% Regression (Pass)** |
| **M2 Packet Framing / CRC** | 100% Match | 100% Match | **0% Regression (Pass)** |
| **TCP Transport Lifecycle** | 100% Pass | 100% Pass | **0% Regression (Pass)** |
| **JVM Automated Test Suite** | 412 / 412 PASS | **412 / 412 PASS** | **0% Regression (Pass)** |

---

## 6. Comprehensive Cross-Script Status Matrix

| Language | ISO | Script Family | Artifact Status | TTS Runtime Status | Physical E2E Status | Regression Status |
|:---:|:---:|:---|:---:|:---:|:---:|:---:|
| **English** | `en` | Latin | `ARTIFACT_VERIFIED` | `TTS_RUNTIME_VERIFIED` | `PHYSICAL_E2E_VERIFIED` | `REGRESSION_VERIFIED` |
| **Hindi** | `hi` | Devanagari | `ARTIFACT_VERIFIED` | `TTS_RUNTIME_VERIFIED` | `PHYSICAL_E2E_VERIFIED` | `REGRESSION_VERIFIED` |
| **Gujarati** | `gu` | Gujarati | `ARTIFACT_VERIFIED` | `TTS_RUNTIME_VERIFIED` | `PHYSICAL_E2E_VERIFIED` | `REGRESSION_VERIFIED` |
| **Tamil** | `ta` | Tamil | `ARTIFACT_VERIFIED` | `TTS_RUNTIME_VERIFIED` | `PHYSICAL_E2E_VERIFIED` | `REGRESSION_VERIFIED` |
| **Bengali** | `bn` | Bengali | `ARTIFACT_VERIFIED` | `TTS_RUNTIME_VERIFIED` | `PHYSICAL_E2E_VERIFIED` | `REGRESSION_VERIFIED` |
| **Malayalam**| `ml` | Malayalam | `ARTIFACT_VERIFIED` | `TTS_RUNTIME_VERIFIED` | `PHYSICAL_E2E_VERIFIED` | `REGRESSION_VERIFIED` |
| **Marathi** | `mr` | Devanagari | `ARTIFACT_VERIFIED` | `TTS_RUNTIME_VERIFIED` | `NOT_VALIDATED` | `NOT_VALIDATED` |
| **Kannada** | `kn` | Kannada | `ARTIFACT_VERIFIED` | `TTS_RUNTIME_VERIFIED` | `NOT_VALIDATED` | `NOT_VALIDATED` |
| **Telugu** | `te` | Telugu | `ARTIFACT_VERIFIED` | `TTS_RUNTIME_VERIFIED` | `NOT_VALIDATED` | `NOT_VALIDATED` |
| **Odia** | `or` | Odia | `NOT_VALIDATED` | `NOT_VALIDATED` | `NOT_VALIDATED` | `NOT_VALIDATED` |
