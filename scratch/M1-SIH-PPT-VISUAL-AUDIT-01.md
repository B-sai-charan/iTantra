# PPT Visual Audit

**Document Title:** iTantra SIH 2026 Presentation Visual and Content Audit  
**Artifact Target:** `scratch/M1-SIH-PPT-VISUAL-AUDIT-01.md`  
**Inspected File:** `scratch/iTantra-SIH-2026-Final-Presentation.pptx`  
**Date of Audit:** 2026-09-30  
**Inspection Scope:** 10 Slides (Visual Layout, Readability, Hierarchy, Evidence Accuracy, Claim Safety, Presenter Notes)  

---

## Overall Status
**READY**

---

## Slide-by-Slide Findings

### Slide 1 — Title
- **Layout & Structure:** 16:9 widescreen layout with left accent bar and clear visual hierarchy.
- **Readability:** Title (*"iTantra"*) at 46pt bold, subtitle at 18pt, value proposition at 14pt, platform specifications in emerald green at 12pt.
- **Evidence Grounding:** References native Android implementation (API 26–35) and production build `v1.0-debug`.
- **Readiness:** Excellent. Instantly communicates project identity within 3 seconds.

### Slide 2 — Problem: Constraints of Traditional Audio Transmission
- **Layout & Structure:** Dual-card side-by-side comparative layout.
- **Visual Distinction:** Left card (*"Traditional Audio Streaming"*) highlighted with amber header; Right card (*"iTantra Semantic Approach"*) highlighted with emerald header.
- **Readability:** Contrasts ~256 kbps continuous audio stream against the 29-byte semantic frame without inventing ungrounded percentage claims.
- **Readiness:** High visual clarity and immediate conceptual impact.

### Slide 3 — Proposed Solution: End-to-End Edge AI Pipeline
- **Layout & Structure:** 3-column architecture (Transmitter Edge AI, Local Transport, Receiver Edge AI) with a bottom boundary banner.
- **Visual Boundaries:** Visually separates on-device neural processing (VAD, Whisper ASR, Piper TTS) from local TCP wire transmission.
- **Readability:** Step-by-step bullet points with high color contrast against dark card backgrounds (`#1E293B`).
- **Readiness:** Evaluator can parse the entire system architecture in under 5 seconds.

### Slide 4 — Innovation: Edge-Native Neural Architecture
- **Layout & Structure:** 4-quadrant card layout detailing On-Device Execution, Model Optimization, Binary Wire Framing, and Native Android Engineering.
- **Technical Precision:** References exact native assets (`libsherpa-onnx-jni.so`), quantized INT8 Whisper Tiny models (~98 MB), Silero VAD, and CRC32 framing.
- **Readiness:** Clean, professional technical density suitable for engineering judges.

### Slide 5 — Architecture: Frozen Module Interfaces
- **Layout & Structure:** 3 horizontal modular cards (M1, M2, M3) topped with distinct category accents, anchored by a bottom frozen contract banner.
- **Interface Clarity:** Explicitly presents `SpeechMessage(sequenceNumber: Int, timestamp: Long, language: String, text: String)` as the immutable interface between modules.
- **Readiness:** Clear modular decoupling showing that transport can be swapped without altering AI modules.

### Slide 6 — Working Prototype: Physical Hardware Validation
- **Layout & Structure:** Two-column evidence layout: Left card presents Canonical Demo Topology; Right card presents the Empirical Execution Timeline.
- **Immediate Evidence Prominence:**
  - Hardware serials (`HNP097HQ`, `10BE830QG8000MX`), network (`BOYS AP-2`), and port (`42173`) are prominent.
  - Spoken utterance (*"Hello"*), STT output (*"Hello"*), STT inference (`1,132 ms`, RTF `1.2578`), frame size (`29 bytes`), TTS synthesis (`196 ms`, RTF `0.422`), and playback (`480 ms`) are prominently displayed.
  - Clearly labeled as a single physical validation run.
- **Readiness:** Extremely strong physical hardware credibility.

### Slide 7 — Quantitative Evidence: Measured Metrics & Assets
- **Layout & Structure:** High-density 13-row by 4-column structured table with dark slate header and alternating row shading.
- **Data Precision:** All 12 audited metrics (408 tests, 315 MB APK, 14,400 samples, 1,132 ms STT, 29B frame, 196 ms TTS, 464 ms audio, 480 ms playback, 2.3 MB VAD, 102.8 MB Whisper INT8, 63.1 MB English TTS, 62.9 MB Hindi TTS) are formatted with clear context labels.
- **Legibility:** 10pt/11pt Segoe UI text with high contrast against dark cells ensures readability from presentation distance.
- **Readiness:** Clean, authoritative, and audit-compliant.

### Slide 8 — Current Scope & Documented Limitations
- **Layout & Structure:** 6-card grid (2 columns x 3 rows) with amber accent headers.
- **Engineering Honesty:** Openly documents live-microphone acoustic variability, campus AP asymmetric routing, manual IP entry, hardware processing disparity, CRC32 vs cryptographic encryption, and unmeasured continuous metrics.
- **Readiness:** Demonstrates mature, transparent engineering rigor.

### Slide 9 — Live Demonstration Sequence & Protocol
- **Layout & Structure:** Dual-card layout: Left card outlines the 8-step live demonstration sequence (30–60s); Right card presents the strict Live Fallback Protocol.
- **Anti-Faking Policy:** Explicitly instructs presenters never to fake transcripts or type text if live microphone clipping occurs, providing a professional fallback to verified logcat traces.
- **Readiness:** Comprehensive operational guide for live stage execution.

### Slide 10 — Conclusion & Engineering Roadmap
- **Layout & Structure:** Two balanced cards comparing Currently Demonstrated Production Freeze against Future Engineering Roadmap, anchored by a bottom closing position banner.
- **Scope Separation:** Future work (mesh networking, regional languages, noise suppression, encryption) is strictly separated from existing capabilities.
- **Readiness:** Strong, defensible closing pitch.

---

## Visual Problems
- **Clipped Text:** 0 instances detected.
- **Overlapping Elements:** 0 instances detected.
- **Text Outside Slide Boundaries:** 0 instances detected (all 90 shapes conform to `13.333" x 7.5"` widescreen boundaries).
- **Poor Contrast:** 0 instances detected (dark slate backgrounds paired with high-luminance white, cyan, emerald, and amber text).
- **Blank / Incomplete Slides:** 0 instances detected (10 of 10 slides fully rendered).
- **Total Visual Problems:** **0**

---

## Evidence Problems
- **Metric Inconsistencies:** 0 detected. All values (408 unit tests, 315,427,692 byte APK, 1,132 ms STT, RTF 1.2578, 29-byte frame, 196 ms TTS, RTF 0.422, 480 ms playback, Rohan Hindi model) match physical logs and repository artifacts.
- **Total Evidence Problems:** **0**

---

## Claim-Safety Problems
- **Prohibited Superlatives & Unbacked Claims:** 0 detected. No claims of "99% bandwidth reduction", "zero network", "perfect STT", "all 22 languages", "military encryption", "universal real-time", or "guaranteed latency".
- **Total Claim-Safety Problems:** **0**

---

## Presenter-Note Problems
- **Notes Completeness:** 10 of 10 slides have complete presenter notes embedded directly in the PPTX notes stream.
- **Notes Structure:** Every slide note includes: (1) What to say, (2) Evidence supporting it, (3) Likely judge question, (4) Safe answer, and (5) Claim to avoid.
- **Total Presenter-Note Problems:** **0**

---

## Required Fixes
NO FIXES REQUIRED.

---

## Final Readiness
**READY**
