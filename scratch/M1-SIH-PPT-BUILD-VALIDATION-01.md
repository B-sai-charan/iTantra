# PPT Build Validation

**Document:** SIH 2026 Presentation Build Validation Report  
**Artifact Target:** `scratch/M1-SIH-PPT-BUILD-VALIDATION-01.md`  
**Date:** 2026-09-30  

---

## 1. File Identity & Physical Attributes
- **PPTX Path:** `scratch/iTantra-SIH-2026-Final-Presentation.pptx`
- **File Size:** `67586 bytes` (~66.00 KB)
- **SHA-256 Checksum:** `9a8001b3945ebb755d11702c8b17f31aae44d884b7499dd0be5c1f20f2c56ae1`
- **Slide Dimensions:** 16:9 Widescreen (13.333" x 7.5")

---

## 2. Structural & Content Verification
- **Total Slide Count:** `10` (Target: Exactly 10)
- **Slides with Content:** `10 / 10` (100% populated)
- **Slides with Presenter Notes:** `10 / 10` (100% populated with What to say, Evidence, Likely question, Safe answer, and Claim to avoid)

---

## 3. Quantitative Value Verification
All 12 audited metrics from Slide 7 match verified disk artifacts and test outputs:
1. **Automated Unit Tests:** `408 / 408 Passed` (100% pass rate)
2. **Production APK File Size:** `315,427,692 bytes` (SHA-256 verified)
3. **Physical Audio Capture:** `14,400 samples` (900 ms @ 16kHz)
4. **On-Device STT Inference:** `1,132 ms` (RTF: `1.2578`)
5. **Transmitted Wire Frame:** `29 bytes` for *"Hello"*
6. **On-Device TTS Synthesis:** `196 ms` (RTF: `0.422`)
7. **Synthesized Audio Duration:** `464 ms` (7,424 samples)
8. **AudioTrack Playback Time:** `480 ms`
9. **Silero VAD Model Size:** `2,327,524 bytes`
10. **Whisper INT8 Total Size:** `102,793,173 bytes`
11. **English Piper Model Size:** `63,149,198 bytes`
12. **Hindi Piper Model Size:** `62,946,428 bytes`

---

## 4. Prohibited-Claim Safety Scan
- **On-Slide Content Scan:** `0 prohibited claims detected` (100% compliant).
- **Presenter Notes Scan:** All sensitive concepts appear exclusively within explicit *"Claims to avoid"* caution headers or limitations notes.
- **Specific Patterns Scanned:**
  - *"99% bandwidth reduction"* — **ABSENT / PASSED**
  - *"zero network"* — **ABSENT / PASSED** (Explicitly specifies local TCP/Wi-Fi transport)
  - *"perfect STT / 100% accuracy"* — **ABSENT / PASSED** (Acoustic variability documented)
  - *"all Indian languages"* — **ABSENT / PASSED** (Explicitly scoped to English & Hindi)
  - *"military-grade encryption"* — **ABSENT / PASSED** (Framing identified as CRC32)
  - *"universal real-time"* — **ABSENT / PASSED** (RTF reported transparently per SoC)
  - *"guaranteed low latency"* — **ABSENT / PASSED** (Presented as single empirical run)
  - *"proven AP firewall"* — **ABSENT / PASSED** (Safe asymmetric observation wording used)

---

## 5. Source Code & Repository Integrity
- **Modified Source Code Files:** `0` (Zero changes to `app/src/`, Gradle, assets, XML, manifests, or production APK)
- **New Files Confined To:** `scratch/` directory only.

---

## 6. Final Validation Verdict

### FINAL STATUS: **PASS**

**Summary:** The presentation deck `scratch/iTantra-SIH-2026-Final-Presentation.pptx` opens cleanly, contains exactly 10 fully-rendered slides with embedded presenter notes, strictly adheres to all quantitative evidence from the production freeze, contains zero prohibited claims, and leaves the codebase completely untouched.
