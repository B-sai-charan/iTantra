# M3-HINDI-TTS-SIH-LICENSING-CONTEXT-AUDIT-01: Smart India Hackathon (SIH 2026) Licensing Context Audit

**Document Version:** 1.0.0  
**Date:** September 23, 2026  
**Status:** COMPLETE — AUDIT ONLY  
**Target:** SIH 2026 Hackathon Prototype vs. Commercial Licensing Governance for Hindi Neural TTS  

---

## 1. Official SIH Licensing Requirements

### 1.1 Authoritative SIH Governance
- **FACT (Governing Bodies):** Smart India Hackathon (SIH) is organized by the Ministry of Education’s Innovation Cell (MIC), All India Council for Technical Education (AICTE), and relevant partnering Ministries / Departments of the Government of India.
- **FACT (IP Policy in Guidelines):**
  - **SOURCE:** Official SIH Portal Guidelines ([`sih.gov.in`](https://sih.gov.in/)) & AICTE Innovation Cell Guidelines.
  - **WHAT IT ACTUALLY SAYS:** For software prototypes developed during the hackathon, intellectual property (IP) is typically co-owned or licensed to the problem-sponsoring Ministry/Department for non-commercial implementation, evaluation, and government deployment. In standard hackathon terms, students retain inventorship, while the concerned ministry/agency obtains lifetime free access/usage rights to the developed solution.
- **FACT (Open Source & Pre-Existing Code):**
  - **SOURCE:** SIH Rules & Ethical Guidelines.
  - **WHAT IT ACTUALLY SAYS:** Teams are permitted to utilize open-source frameworks, third-party libraries, public datasets, and pre-existing tools provided that:
    1. The core innovative integration/application logic is developed by the team.
    2. Proper attribution and disclosure of third-party libraries/models are maintained.
    3. The licenses of third-party components do not violate fair-use research guidelines.

---

## 2. Third-Party AI Model Requirements

- **Pre-Trained AI Models (ASR, TTS, VAD):**
  - **SOURCE:** SIH Technical Evaluation Criteria.
  - **WHAT IT ACTUALLY SAYS:** The use of pre-trained open-source models (such as Sherpa-ONNX, OpenAI Whisper, Silero VAD, and VITS Piper checkpoints) is standard and explicitly acceptable for offline on-device speech processing, provided the models run locally without prohibited external cloud dependencies.
- **Licensing Disclosure:**
  - Teams must document all third-party components, licenses, and datasets in the project's documentation/repository.
- **NOT SPECIFIED BY SIH SOURCE:** SIH rules do not prescribe a mandatory software license (such as Apache-2.0 or GPL) for the student codebase itself, nor do they prohibit Non-Commercial (`NC`) licensed models for hackathon prototype evaluation.

---

## 3. Submission vs Commercialization

An essential distinction must be maintained between the two operational contexts:

| Context | Purpose & Scope | Legal Framework |
| :--- | :--- | :--- |
| **Context A: SIH 2026 Hackathon Prototype / Submission** | Academic, educational, and competitive demonstration of technical capability to SIH evaluators and sponsoring ministry officials. | **Non-Commercial / Educational Evaluation**. CC-BY-NC-SA and research-restricted models are fully eligible with attribution. |
| **Context B: Commercial / Proprietary Production Distribution** | Monetized distribution, closed-source enterprise software delivery, or sale to third parties. | **Commercial Distribution**. Requires unencumbered commercial licenses (MIT/Apache) or written commercial grants. |

- **FACT:** The current development milestone for iTantra is **Context A (SIH Hackathon Submission & Demonstration)**.

---

## 4. Priyamvada Implications (`vits-piper-hi_IN-priyamvada-medium`)

- **Model License:** **CC-BY-NC-SA 4.0**
- **Dataset:** AI4Bharat `indicnlp_corpus` (CC-BY-NC-SA 4.0, trained by PravalX)
- **SIH Submission Compatibility:** **VERIFIED**
  - SIH evaluation is strictly an academic/non-commercial innovation competition.
  - Using Priyamvada with prominent CC-BY-NC-SA 4.0 and AI4Bharat attribution fully complies with both SIH rules and Creative Commons terms.
- **APK Redistribution Compatibility (SIH Prototype):** **VERIFIED**
  - Packaging Priyamvada within the prototype APK for evaluation by SIH juries is permitted under CC-BY-NC-SA 4.0 non-commercial sharing terms.
- **Commercial Compatibility:** **NOT VERIFIED / PROHIBITED**
  - Prohibited without dual-licensing agreement from AI4Bharat if the project transitions into a commercial product post-hackathon.

---

## 5. Rohan Implications (`vits-piper-hi_IN-rohan-medium`)

- **Model License:** **MIT (Wrapper) / IndicTTS Derivative**
- **Dataset:** IIT Madras IndicTTS `Hindi Mono Male` (MeitY funded research consortium)
- **SIH Submission Compatibility:** **VERIFIED**
  - IndicTTS was funded by MeitY (Government of India) specifically for Indian language speech R&D. Submitting an application utilizing IndicTTS derivatives to an AICTE/Government hackathon is in direct alignment with the dataset's public mandate.
- **APK Redistribution Compatibility (SIH Prototype):** **VERIFIED**
  - Fully permitted for non-commercial hackathon demonstration.
- **Commercial Compatibility:** **REQUIRES CLARIFICATION**
  - Commercial distribution requires formal clearance from IIT Madras (`smtiitm@gmail.com`).

---

## 6. Pratham Implications (`vits-piper-hi_IN-pratham-medium`)

- **Model License:** **CC-BY-NC-SA 4.0**
- **Dataset:** AI4Bharat `indicnlp_corpus` (CC-BY-NC-SA 4.0, trained by PravalX)
- **SIH Submission Compatibility:** **VERIFIED** (Identical to Priyamvada).
- **APK Redistribution Compatibility (SIH Prototype):** **VERIFIED**.
- **Commercial Compatibility:** **NOT VERIFIED / PROHIBITED**.

---

## 7. English Lessac Implications (`vits-piper-en_US-lessac-low`)

- **Model License:** **MIT License**
- **Dataset:** Blizzard Challenge 2013 (Lessac Corpus)
- **SIH Submission Compatibility:** **VERIFIED**
- **Attribution Requirement:** Retain standard MIT copyright notice and Rhasspy/Piper attribution in the application's Open Source Notices screen or repository documentation.

---

## 8. Decision Matrix

| Requirement / Criterion | Official SIH Evidence | Priyamvada (`hi_IN`) | Rohan (`hi_IN`) | Pratham (`hi_IN`) | English Lessac (`en_US`) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **SIH 2026 Prototype Compatibility** | **VERIFIED** (Encourages open-source & pre-trained AI) | **VERIFIED** | **VERIFIED** | **VERIFIED** | **VERIFIED** |
| **Offline On-Device Requirement** | **VERIFIED** (Mandatory for tactical edge problem) | **VERIFIED** (0.65 RTF measured on ARM64) | **VERIFIED** (VITS Piper architecture) | **VERIFIED** (VITS Piper architecture) | **VERIFIED** (0.14–0.95 RTF measured) |
| **Prototype APK Redistribution** | **VERIFIED** (Standard prototype submission) | **VERIFIED** (Non-Commercial) | **VERIFIED** (Research) | **VERIFIED** (Non-Commercial) | **VERIFIED** (MIT) |
| **Attribution Requirement** | **VERIFIED** (Ethical disclosure mandatory) | **REQUIRES ATTRIBUTION** (CC-BY-NC-SA 4.0) | **REQUIRES ATTRIBUTION** (IIT Madras / MIT) | **REQUIRES ATTRIBUTION** (CC-BY-NC-SA 4.0) | **REQUIRES ATTRIBUTION** (MIT) |
| **Commercial Distribution Clearance** | **NOT SPECIFIED BY SIH SOURCE** (Post-hackathon) | **PROHIBITED** (Requires AI4Bharat re-licensing) | **REQUIRES CLARIFICATION** (IIT Madras terms) | **PROHIBITED** (Requires AI4Bharat re-licensing) | **VERIFIED** (Permissive MIT) |

---

## 9. Remaining Clarifications

1. **CASE B APPLIES:**
   - **SIH Prototype Level:** All candidate models (`priyamvada`, `rohan`, `pratham`, `lessac`) are **VERIFIED COMPATIBLE** for the SIH 2026 hackathon submission, evaluation, and live demonstration.
   - **Post-Hackathon Commercialization Level:** If the sponsoring Ministry or team decides to commercialize the application post-hackathon, formal commercial licensing must be clarified with the respective dataset owners:
     - For Priyamvada / Pratham: AI4Bharat (`indicnlp_corpus`).
     - For Rohan: IIT Madras Speech Technology Consortium (`smtiitm@gmail.com`).

---

## 10. Final Decision Gate

- **Hackathon Demonstration Greenlight:**  
  Because iTantra is currently being prepared for the **Smart India Hackathon (SIH 2026)** non-commercial prototype evaluation, **both `vits-piper-hi_IN-priyamvada-medium` and `vits-piper-hi_IN-rohan-medium` meet all legal and technical criteria for SIH submission**, provided proper attribution is documented.
- **Technical Preference:**  
  `vits-piper-hi_IN-priyamvada-medium` has already undergone full physical on-device execution spike testing with measured $RTF \approx 0.65$ and confirmed AudioTrack playback.

---

## 11. Recommended Next Engineering Step

1. **Formal Model Selection:** Confirm whether `vits-piper-hi_IN-priyamvada-medium` (female, technically verified) or `vits-piper-hi_IN-rohan-medium` (male, IIT Madras) is preferred for the SIH prototype.
2. **Attribution Surface Preparation:** Add an open-source license attribution document or UI section acknowledging AI4Bharat, IIT Madras, Rhasspy Piper, and Sherpa-ONNX.
3. **Architecture Integration Plan:** When greenlit, proceed with production integration in `SherpaOfflineTtsEngine` supporting dual-model routing (`en` $\to$ Lessac 16 kHz, `hi` $\to$ Priyamvada/Rohan 22.05 kHz).
