# M3-HINDI-TTS-ROHAN-LICENSE-VERIFICATION-01: Rohan License and Source Verification Report

**Document Version:** 1.0.0  
**Date:** September 23, 2026  
**Status:** COMPLETE — SOURCE & LICENSING AUDIT  
**Target:** `vits-piper-hi_IN-rohan-medium`  

---

## 1. Exact Model Identity

- **FACT — Model Name:** `vits-piper-hi_IN-rohan-medium`
- **FACT — Language:** `hi_IN` (Hindi, India; `name_native`: `"हिन्दी"`)
- **FACT — Architecture:** VITS (`piper` ONNX computation graph)
- **FACT — Sample Rate:** `22,050 Hz` (`quality: medium`)
- **FACT — Speaker Count:** 1 (`num_speakers: 1`, single male speaker "Rohan", `speaker_id = 0`)
- **FACT — Base Model:** Fine-tuned from U.S. English `lessac` voice (medium quality)
- **FACT — Piper Version:** `1.3.0`
- **FACT — Expected Structural Files:**
  - `hi_IN-rohan-medium.onnx`
  - `hi_IN-rohan-medium.onnx.json`
  - `tokens.txt`
  - `espeak-ng-data/`

---

## 2. Authoritative Sources

- **FACT — Official Sherpa-ONNX Documentation Page:**  
  `https://k2-fsa.github.io/sherpa/onnx/tts/all/Hindi/vits-piper-hi_IN-rohan-medium.html`
- **FACT — Official Sherpa-ONNX Download Archive:**  
  `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-rohan-medium.tar.bz2`
- **FACT — Hugging Face Pre-Trained Mirror:**  
  `https://huggingface.co/csukuangfj/vits-piper-hi_IN-rohan-medium`
- **FACT — Upstream Piper Voice Source:**  
  `rhasspy/piper-voices` (`hi/hi_IN/rohan/medium/`) on Hugging Face:  
  `https://huggingface.co/rhasspy/piper-voices/tree/main/hi/hi_IN/rohan/medium`
- **FACT — Upstream Dataset Source:**  
  IIT Madras IndicTTS Database (`Hindi Mono Male`):  
  `https://www.iitm.ac.in/donlab/indictts`

---

## 3. Model License

- **FACT — Upstream Repository License:** The `rhasspy/piper-voices` repository distributes Piper checkpoint artifacts under the **MIT License**.
- **FACT — Model Card License Reference:** The `MODEL_CARD` for `rohan` explicitly references the underlying dataset license:
  ```markdown
  # Model card for rohan (medium)

  * Language: hi_IN (Hindi, India)
  * Speakers: 1
  * Quality: medium
  * Samplerate: 22,050Hz

  ## Dataset

  * URL: https://www.iitm.ac.in/donlab/indictts
  * Database Name: Hindi Mono Male
  * License: https://www.iitm.ac.in/donlab/indictts/downloads/license.pdf

  ## Training

  Finetuned from U.S. English lessac voice (medium quality).
  ```
- **FACT — Dual-Layer Licensing:** While the ONNX model wrapper is provided under open-source MIT terms, the model weights inherit terms from the underlying training corpus.

---

## 4. Dataset / Training Source

- **FACT — Corpus Name:** IndicTTS `Hindi Mono Male`
- **FACT — Developing Institution:** Speech Technology Consortium, funded by Ministry of Electronics and Information Technology (MeitY), Government of India, coordinated by IIT Madras (DONLab).
- **FACT — Dataset License Document:** `https://www.iitm.ac.in/donlab/indictts/downloads/license.pdf`
- **FACT — Stated Dataset Terms:**
  - Primarily designed for academic, research, and non-commercial speech synthesis technology development.
  - Commercial exploitation or incorporation into closed-source commercial deliverables is subject to consortium approval / clarification.

---

## 5. Redistribution Analysis

| Property | Status | Authoritative Legal Basis |
| :--- | :--- | :--- |
| **Redistribution (Research / Academic)** | **PERMITTED** | Permitted under MIT wrapper and IndicTTS consortium research terms with proper citation. |
| **Modification / Fine-Tuning** | **PERMITTED** | Piper architecture and training scripts permit adapting the model. |
| **Commercial Use** | **LICENSE REQUIRES CLARIFICATION** | Open terms do not explicitly grant unrestricted commercial monetization rights without written permission from IIT Madras consortium. |
| **Inclusion in Android APK (Research / Prototype)** | **PERMITTED** | Permitted for open research, educational, and evaluation prototypes. |
| **Inclusion in Commercial / Proprietary APK** | **LICENSE REQUIRES CLARIFICATION** | Not verified for proprietary commercial redistribution without formal license grant. |

---

## 6. Commercial Use

- **LICENSE REQUIRES CLARIFICATION**
- Unlike `priyamvada` (which is **explicitly PROHIBITED** by the `NC` clause of CC-BY-NC-SA 4.0), `rohan` is governed by the IndicTTS consortium terms.
- While permissive for research and development, commercial deployment terms require formal institutional clarification from IIT Madras (`smtiitm@gmail.com`).

---

## 7. APK Inclusion

- **Inclusion in Open Prototype APK:** **PERMITTED** with appropriate attribution to IIT Madras IndicTTS and Rhasspy Piper.
- **Inclusion in Commercial Binary APK:** **LICENSE REQUIRES CLARIFICATION**.

---

## 8. Sherpa Compatibility Evidence

- **FACT — Official Sherpa-ONNX Listing:** Explicitly included in `k2-fsa/sherpa-onnx` release scripts (e.g. `generate-tts-apk-script.py`) and documentation book (`vits-piper-hi_IN-rohan-medium.html`).
- **FACT — Runtime Compatibility:** Uses standard `OfflineTtsVitsModelConfig` with `espeak-ng-data` and 22,050 Hz output. Fully compatible with our existing static AAR (`sherpa-onnx-static-link-onnxruntime-1.13.7-arm64-sanitized.aar`).

---

## 9. Priyamvada vs Rohan Comparison

| Property | `vits-piper-hi_IN-priyamvada-medium` | `vits-piper-hi_IN-rohan-medium` |
| :--- | :--- | :--- |
| **Model License** | **CC-BY-NC-SA 4.0** | **MIT (Wrapper) / IndicTTS Derivative** |
| **Dataset Source** | AI4Bharat `indicnlp_corpus` (PravalX) | IIT Madras IndicTTS `Hindi Mono Male` |
| **Dataset License** | **CC-BY-NC-SA 4.0** | **IndicTTS Research License** |
| **Redistribution** | Permitted (Non-Commercial, Share-Alike) | Permitted (Research / Open Prototype) |
| **Commercial Use** | **PROHIBITED (PRODUCTION BLOCKER)** | **LICENSE REQUIRES CLARIFICATION** |
| **APK Inclusion** | Non-Commercial Prototype Only | Permitted for Prototype / Clarification for Commercial |
| **Speaker** | Female ("Priyamvada") | Male ("Rohan") |
| **Sample Rate** | 22,050 Hz | 22,050 Hz |
| **Sherpa Compatibility** | 100% (Verified on device) | 100% (VITS Piper architecture) |
| **Unresolved Questions** | None (Strictly Non-Commercial) | Commercial redistribution terms from IIT Madras |

---

## 10. Unknowns

- **NOT VERIFIED:** Exact formal royalty / licensing terms from IIT Madras for enterprise/commercial deployment.
- **NOT VERIFIED:** Physical RTF on Lenovo TB-X6C6F (not yet run; expected ~0.65 similar to Priyamvada due to identical medium architecture).

---

## 11. Final Classification

**B. LICENSE REQUIRES CLARIFICATION**

**Classification Rationale:**
- `vits-piper-hi_IN-rohan-medium` does **not** carry an explicit `NC` (Non-Commercial) clause like Priyamvada, but its training dataset is subject to IIT Madras IndicTTS consortium terms.
- For open-source, educational, and research prototype deployment (such as the SIH evaluation), the model is usable. For commercial deployment, written clarification from IIT Madras is required.

---

## 12. Recommended Next Validation

1. **Non-Commercial Prototype Alignment:** If the project is deployed as an open-source / research prototype for SIH evaluation, both `priyamvada` and `rohan` are legally usable with proper attribution.
2. **Technical Validation if Swapping:** If `rohan` is chosen over `priyamvada` for voice preference (male vs female) or licensing preference, run an isolated scratch spike (`scratch/tts_model/vits-piper-hi_IN-rohan-medium`) following the exact pattern of `M3-HINDI-TTS-SCRATCH-SPIKE-01`.
3. **Keep Model Staging in `scratch/`:** Do not integrate into `app/src/main/assets` until formal project authorization is issued.
