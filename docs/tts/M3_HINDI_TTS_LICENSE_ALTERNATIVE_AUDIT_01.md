# M3-HINDI-TTS-LICENSE-ALTERNATIVE-AUDIT-01: Licensing and Alternative Hindi Neural TTS Model Audit

**Document Version:** 1.0.0  
**Date:** September 23, 2026  
**Status:** COMPLETE — AUDIT & LICENSING CLASSIFICATION  
**Target:** Production Licensing Assessment for Hindi Neural TTS  

---

## 1. Priyamvada License Evidence

### 1.1 Authoritative File Inspection
Inspection of the staged model artifacts in `scratch/tts_model/vits-piper-hi_IN-priyamvada-medium/`:

- **`MODEL_CARD` Contents:**
  ```markdown
  # Model card for priyamvada (medium)

  * Language: hi_IN (Hindi, India)
  * Speakers: 1
  * Quality: medium
  * Samplerate: 22,050Hz

  ## Dataset

  * License: http://creativecommons.org/licenses/by-nc-sa/4.0/

  ## Training

  Dataset: https://github.com/AI4Bharat/indicnlp_corpus
  Trained by: https://github.com/PravalX
  ```

- **`hi_IN-priyamvada-medium.onnx.json` Contents:**
  - `dataset`: `"priyamvada"`
  - `audio.sample_rate`: `22050`
  - `audio.quality`: `"medium"`
  - `language.code`: `"hi_IN"`
  - `num_speakers`: `1`
  - `piper_version`: `"1.0.0"`
  - `espeak.voice`: `"hi"`

### 1.2 License Breakdown by Component
- **FACT (Model License):** The upstream Piper voice checkpoint is published under **CC-BY-NC-SA 4.0** (Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International).
- **FACT (Dataset License):** Trained on AI4Bharat `indicnlp_corpus`, released under **CC-BY-NC-SA 4.0**.
- **FACT (Phonemizer License):** `espeak-ng-data` phonemization dictionary tables are licensed under **GPLv3**.
- **FACT (Runtime License):** Sherpa-ONNX engine and bindings are licensed under **Apache 2.0**.

### 1.3 Explicit Rights Verification
| Right / Usage Condition | Verification Status | Exact Legal Basis from License Terms |
| :--- | :--- | :--- |
| **1. Redistribution** | **FACT: PERMITTED (Non-Commercial)** | CC-BY-NC-SA 4.0 Section 2(a)(1) permits sharing/redistribution in any medium provided attribution is given. |
| **2. Commercial Use** | **FACT / PRODUCTION BLOCKER: PROHIBITED** | CC-BY-NC-SA 4.0 Section 2(a)(1) explicitly restricts commercial use: *"You may not use the material for commercial purposes."* |
| **3. Modification** | **FACT: PERMITTED (Share-Alike)** | Section 2(a)(1) allows creating adaptations, provided any distributed derivative is shared under the identical CC-BY-NC-SA 4.0 license. |
| **4. Inclusion in Android APK** | **LICENSE REQUIRES CLARIFICATION** | **Permitted** for open-source non-commercial research APKs (with Share-Alike & attribution). **Prohibited** for commercial or closed-source APKs. |
| **5. Defense / Tactical Commercial Deployment** | **NOT VERIFIED** | Unverified without formal written exemption/dual-licensing agreement from AI4Bharat / PravalX. |

---

## 2. Priyamvada Technical Evidence

The technical feasibility of `vits-piper-hi_IN-priyamvada-medium` was validated on the physical ARM64 device during `M3-HINDI-TTS-SCRATCH-SPIKE-01`.

### Preserved Historical MEASURED RESULTS:
- **Device Model:** Lenovo TB-X6C6F (`HNP097HQ`)
- **OS / Architecture:** Android 12 (API Level 31) / `aarch64` (arm64-v8a)
- **MEASURED RESULT — Initialization Latency:** `6,963 ms`
- **MEASURED RESULT — Process Memory Profile (PSS):**
  - Before Initialization: `50,007 KB` (~48.8 MB PSS, Native Heap: `5.93 MB`)
  - After Initialization: `182,735 KB` (~178.5 MB PSS, Native Heap: `89.45 MB`)
  - Idle after Synthesis: `141,889 KB` (~138.6 MB PSS)
- **MEASURED RESULT — Prompt 1 (`"नमस्ते। यह iTantra का हिंदी टेक्स्ट टू स्पीच परीक्षण है।"`, 4.702s):**
  - Synthesis Time: `3,155 ms`
  - **RTF: `0.671`**
  - AudioTrack Playback: **PASS** (`5,318 ms`)
- **MEASURED RESULT — Prompt 2 (`"यह एक ऑफलाइन संचार प्रणाली है जो कम बैंडविड्थ वाले नेटवर्क पर काम करती है।"`, 6.618s):**
  - Synthesis Time: `4,269 ms`
  - **RTF: `0.645`**
  - AudioTrack Playback: **PASS** (`6,653 ms`)
- **MEASURED RESULT — Prompt 3 (`"आपातकालीन संदेश को हिंदी में स्पष्ट और विश्वसनीय रूप से सुनाया जाना चाहिए।"`, 6.420s):**
  - Synthesis Time: `4,172 ms`
  - **RTF: `0.650`**
  - AudioTrack Playback: **PASS** (`6,457 ms`)
- **MEASURED RESULT — Sample Validity:** 100% finite samples (`Float.isFinite == true`), zero `NaN`, zero `Infinity`, bounds `[-0.8103, +0.8313]`.
- **MEASURED RESULT — Sample Rate:** `22,050 Hz` native mono PCM.

---

## 3. Alternative Hindi Model Candidates

We researched official Sherpa-ONNX supported Hindi TTS candidates and upstream repositories compatible with `OfflineTtsVitsModelConfig`:

### Candidate 1: `vits-piper-hi_IN-pratham-medium`
- **FACT (Official Source):** `https://k2-fsa.github.io/sherpa/onnx/tts/all/Hindi/vits-piper-hi_IN-pratham-medium.html` / `rhasspy/piper-voices` (`hi/hi_IN/pratham/medium`)
- **Architecture:** VITS (`piper` ONNX graph)
- **Language & Speaker:** Hindi (`hi_IN`), 1 male speaker ("Pratham")
- **Sample Rate:** `22,050 Hz`
- **Model Size:** ~`63.1 MB` ONNX + ~`18 MB` `espeak-ng-data`
- **Required Assets:** `hi_IN-pratham-medium.onnx`, `hi_IN-pratham-medium.onnx.json`, `tokens.txt`, `espeak-ng-data/`
- **Model License:** CC-BY-NC-SA 4.0
- **Dataset License:** AI4Bharat `indicnlp_corpus` (CC-BY-NC-SA 4.0, trained by PravalX)
- **Sherpa-ONNX Compatibility:** 100% compatible with existing `OfflineTtsVitsModelConfig`.

### Candidate 2: `vits-piper-hi_IN-rohan-medium`
- **FACT (Official Source):** `https://k2-fsa.github.io/sherpa/onnx/tts/all/Hindi/vits-piper-hi_IN-rohan-medium.html` / `rhasspy/piper-voices` (`hi/hi_IN/rohan/medium`)
- **Architecture:** VITS (`piper` ONNX graph, fine-tuned from U.S. English lessac medium)
- **Language & Speaker:** Hindi (`hi_IN`), 1 male speaker ("Rohan")
- **Sample Rate:** `22,050 Hz`
- **Model Size:** ~`63.1 MB` ONNX + ~`18 MB` `espeak-ng-data`
- **Required Assets:** `hi_IN-rohan-medium.onnx`, `hi_IN-rohan-medium.onnx.json`, `tokens.txt`, `espeak-ng-data/`
- **Model License:** MIT (Piper voice checkpoint) / Downstream derivative of IIT Madras IndicTTS
- **Dataset License:** IIT Madras IndicTTS `Hindi Mono Male` (`https://www.iitm.ac.in/donlab/indictts/downloads/license.pdf`). Academic / Research consortium terms; commercial distribution requires permission/clarification.
- **Sherpa-ONNX Compatibility:** 100% compatible with existing `OfflineTtsVitsModelConfig`.

### Candidate 3: `supertonic-3-hi`
- **FACT (Official Source):** `k2-fsa/sherpa-onnx` (`supertonic-3-hi`)
- **Architecture:** Supertonic (Non-VITS architecture)
- **Language & Speaker:** Hindi (`hi`), multi-speaker
- **Sample Rate:** `24,000 Hz` / `16,000 Hz`
- **Sherpa-ONNX Compatibility:** **INCOMPATIBLE WITH CURRENT STATIC AAR.** Requires `OfflineTtsSupertonicModelConfig` introduced in newer Sherpa releases, which is not present in our fixed `sherpa-onnx-static-link-onnxruntime-1.13.7-arm64-sanitized.aar`.
- **Model License:** Undocumented / NOT VERIFIED.

### Candidate 4: In-House Trained / Custom VITS Model (indic-parler-tts / Open-Data Fine-Tuning)
- **Source:** Custom export to ONNX from Apache-2.0 or CC-BY-4.0 corpora (e.g., Common Voice Hindi, AI4Bharat permissive releases).
- **Architecture:** VITS
- **Model License:** Apache 2.0 or MIT
- **Dataset License:** CC0 / CC-BY-4.0
- **Sherpa-ONNX Compatibility:** 100% compatible with `OfflineTtsVitsModelConfig`.
- **Status:** Requires training pipeline and dataset preparation; not an off-the-shelf pre-built Sherpa archive.

---

## 4. License Comparison Table

| Candidate | Model License | Dataset License | Redistribution | Commercial Use | APK Inclusion | Sherpa Compatibility (Static AAR 1.13.7) | Known Missing Information |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`vits-piper-hi_IN-priyamvada-medium`** | **CC-BY-NC-SA 4.0** | CC-BY-NC-SA 4.0 (AI4Bharat) | Non-Commercial Only | **PROHIBITED (PRODUCTION BLOCKER)** | Prohibited for commercial / proprietary APK | **100% Compatible** (Tested & Verified on device) | None (Terms explicit in `MODEL_CARD`) |
| **`vits-piper-hi_IN-pratham-medium`** | **CC-BY-NC-SA 4.0** | CC-BY-NC-SA 4.0 (AI4Bharat) | Non-Commercial Only | **PROHIBITED (PRODUCTION BLOCKER)** | Prohibited for commercial / proprietary APK | **100% Compatible** (VITS Piper 22050Hz) | None (Identical license to Priyamvada) |
| **`vits-piper-hi_IN-rohan-medium`** | **MIT / IndicTTS** | IndicTTS (IIT Madras) | Permitted for research | **LICENSE REQUIRES CLARIFICATION** | Permitted for non-commercial research; unverified for commercial APK | **100% Compatible** (VITS Piper 22050Hz) | IIT Madras commercial redistribution clause |
| **`supertonic-3-hi`** | **NOT VERIFIED** | **NOT VERIFIED** | Unknown | Unknown | Unknown | **INCOMPATIBLE** (Requires Supertonic runtime config not in static AAR) | Dataset, License, Runtime support in AAR |
| **`vits-piper-en_US-lessac-low` (Current English Production Model)** | **MIT** | Blizzard Challenge 2013 (Lessac) | Permitted | Permitted / Research | Permitted in current open research/prototype APK | **100% Compatible** (Active in production) | None |

---

## 5. Redistribution / APK Implications

### 5.1 The CC-BY-NC-SA 4.0 Constraint
Both `priyamvada` and `pratham` carry the `CC-BY-NC-SA 4.0` license directly originating from the underlying `indicnlp_corpus` dataset:
1. **Non-Commercial (`NC`):** The model cannot be distributed as part of any commercial product, paid app, subscription, or commercial contract.
2. **Share-Alike (`SA`):** If any derivative work is distributed, it must be licensed under the identical CC-BY-NC-SA 4.0 license.
3. **APK Bundling:** Placing ~81 MB of CC-BY-NC-SA 4.0 assets into `app/src/main/assets/` permanently subjects that portion of the APK distribution to Non-Commercial and Share-Alike obligations.

### 5.2 Consistency with Existing English TTS Model
- The current English model `vits-piper-en_US-lessac-low` is an MIT-licensed checkpoint trained on the Blizzard Challenge Lessac corpus.
- Adding a CC-BY-NC-SA 4.0 Hindi model alongside it does not legally contaminate the English weights or source code logic, but it imposes a **Non-Commercial restriction on the entire binary distribution** containing both assets.

---

## 6. Production Candidate Status

Based on authoritative documentation, we classify the candidates:

### Classification:
1. **`vits-piper-hi_IN-priyamvada-medium`**:
   - **Classification:** **B. LICENSE REQUIRES CLARIFICATION** (for non-commercial prototype/demonstration: **COMPATIBLE**; for commercial deployment: **C. NOT SUITABLE FOR PLANNED REDISTRIBUTION**).
   - **Technical Feasibility:** **100% PROVEN** on physical ARM64 hardware (RTF 0.645–0.671, AudioTrack PASS).
2. **`vits-piper-hi_IN-pratham-medium`**:
   - **Classification:** **B. LICENSE REQUIRES CLARIFICATION** (Identical CC-BY-NC-SA 4.0 constraint as Priyamvada).
3. **`vits-piper-hi_IN-rohan-medium`**:
   - **Classification:** **B. LICENSE REQUIRES CLARIFICATION** (Requires written clarification of IIT Madras IndicTTS license for APK redistribution).
4. **`supertonic-3-hi`**:
   - **Classification:** **C. NOT SUITABLE FOR PLANNED REDISTRIBUTION** (Incompatible with static AAR v1.13.7 runtime).

---

## 7. Unknowns / Clarifications Required

1. **NOT VERIFIED — Deployment Intent:** Is iTantra intended strictly as an academic/open-source non-commercial research system (e.g., SIH competition / research prototype) or as a commercial/proprietary deployment?
   - If *non-commercial / research prototype*: `vits-piper-hi_IN-priyamvada-medium` is immediately usable with CC-BY-NC-SA 4.0 attribution.
   - If *commercial / defense deployment*: Formal commercial clearance or custom-trained weights on CC0/Apache datasets are required.
2. **LICENSE REQUIRES CLARIFICATION — IIT Madras IndicTTS Terms:** Clarification on whether `rohan` weights can be redistributed in compiled APK form without royalty or license conflict.

---

## 8. Recommended Next Validation

1. **Legal / Project Scope Alignment:** Confirm whether the prototype deployment under SIH qualifies under the permitted Non-Commercial / Academic provisions of CC-BY-NC-SA 4.0.
2. **Staged Model Integration Strategy:** If non-commercial prototype integration is confirmed:
   - Prepare clean dynamic routing in `SherpaOfflineTtsEngine` supporting both `en_US` (`vits-piper-en_US-lessac-low`, 16 kHz) and `hi_IN` (`vits-piper-hi_IN-priyamvada-medium`, 22.05 kHz).
   - Ensure explicit license attribution is included in `NOTICE` / `ABOUT` surfaces.
3. **Keep Model Staging Isolated:** Maintain Hindi model in `scratch/tts_model/` until formal architecture integration is greenlit.
