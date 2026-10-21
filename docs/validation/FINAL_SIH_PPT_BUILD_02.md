# iTantra: Final SIH Presentation Deck Specification

**Document ID:** `FINAL_SIH_PPT_BUILD_02`  
**Date:** 2026-09-28  
**Project:** iTantra — Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links  
**Format:** 8-Slide Evaluator Deck + Dedicated Demo Break + Slide-by-Slide Defense Guide + Practice Script  
**Production Code Changed:** **0 (DOCUMENTATION ONLY)**  

---

## Executive Presentation Deck Layout

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   iTantra 8-SLIDE DECK STRUCTURE                                 │
├───────┬──────────────────────────────────────────┬─────────────────────────────────┬─────────────┤
│ Slide │ Title                                    │ Visual Type                     │ Time Target │
├───────┼──────────────────────────────────────────┼─────────────────────────────────┼─────────────┤
│ 1     │ Title & Problem Identity                 │ Dark Minimalist Identity Card   │ 0:00 - 0:20 │
│ 2     │ The Problem: Constrained Voice Links     │ High-Overhead vs Low-Rate Split │ 0:20 - 0:40 │
│ 3     │ The Solution: Decoupled Speech Pipeline  │ Linear Pipeline Schematic       │ 0:40 - 1:00 │
│ 4     │ Implemented Architecture                 │ Dual-Node Technical Blueprint   │ 1:00 - 1:25 │
│ 5     │ What We Actually Validated               │ Physical Evidence Checklist     │ 1:25 - 1:50 │
│ [DEMO]│ Two-Device Physical Voice-to-Voice Demo  │ Live Physical Device Flow       │ 45 - 60 sec │
│ 6     │ Controlled Performance Benchmarks        │ High-Contrast Metric Cards      │ 1:50 - 2:15 │
│ 7     │ Where We Learned the Hardest Lesson      │ Honest STT Domain Comparison    │ 2:15 - 2:40 │
│ 8     │ Demonstrated System & Future Roadmap     │ Delivered vs Future Work Split  │ 2:40 - 3:00 │
└───────┴──────────────────────────────────────────┴─────────────────────────────────┴─────────────┘
```

---

### SLIDE 1 — TITLE & PROBLEM IDENTITY

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                              iTantra                                             │
│       Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links  │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                  │
│   [ Offline, On-Device AI Communication for Bandwidth-Constrained and Tactical Radio Links ]     │
│                                                                                                  │
│   • Problem Statement: PS26173 (Smart India Hackathon 2024)                                      │
│   • Core Principle: Offline Edge AI • Local Point-to-Point Transport • Multilingual Voice        │
│                                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

#### 1. Slide Text
- **Title:** iTantra
- **Subtitle:** Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links
- **Core Positioning:** Offline, on-device AI communication for bandwidth-constrained and tactical radio links
- **Problem Statement ID:** PS26173
- **Architectural Pillars:** Offline Edge AI • Local Point-to-Point Transport • Multilingual Speech Synthesis

#### 2. Visual Layout
- High-contrast tactical dark background (`#0B0F19`).
- Centered bold clean typography (`#FFFFFF` and `#38BDF8`).
- Clean identity badge: `● Offline Operation` • `● English & Hindi` • `● Android ARM64 Native`.

#### 3. Exact Presenter Script
> *"Respected judges, we present **iTantra**—an offline, on-device AI communication system engineered for speech transmission over low-bitrate and bandwidth-constrained links without relying on external cloud infrastructure."*

#### 4. Judge Questions & Defensible Answers
- **Q: What is the core innovation here?**  
  *Answer:* Decoupling speech into on-device recognition, compact protocol message framing, and local neural voice synthesis, enabling voice communication over channels where streaming continuous audio waveforms is challenging.

---

### SLIDE 2 — THE PROBLEM: CONSTRAINED VOICE LINKS

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                           THE PROBLEM: CONSTRAINED VOICE CHANNELS                                │
├────────────────────────────────────────────────┬─────────────────────────────────────────────────┤
│ CONVENTIONAL VOICE TRANSMISSION                │ LOW-BITRATE & CONTESTED REALITY                 │
├────────────────────────────────────────────────┼─────────────────────────────────────────────────┤
│ • Continuous raw-audio waveform streaming.     │ • Continuous raw-audio transmission is          │
│ • Requires continuous unconstrained channel      challenging on constrained links.               │
│   availability.                                │ • Tactical radio (HF/VHF) & disaster links      │
│ • Cloud-based speech AI fails during remote      suffer packet drops under audio streaming load. │
│   deployments or network disruptions.          │ • Need for decentralized, sovereign AI.         │
└────────────────────────────────────────────────┴─────────────────────────────────────────────────┘
```

#### 1. Slide Text
- **Title:** The Problem: Constrained Voice Channels
- **Conventional Voice Transmission:**
  - Requires continuous streaming of analog or digitized audio waveforms over the air.
  - Cloud-tethered speech recognition is completely unavailable in disconnected or remote environments.
- **Operational Reality:**
  - Continuous raw-audio transmission is challenging on constrained links (e.g., narrowband HF/VHF channels, disaster recovery networks).
  - Voice packets drop, buffer, or congest the channel when raw waveforms are streamed.
- **Exploration:** Exploring a speech-to-text-to-speech architecture to communicate spoken intent without streaming raw audio.

#### 2. Visual Layout
- Split-screen comparison card:
  - Left card (Muted Slate): High channel requirement of conventional streaming audio.
  - Right card (Dark Amber): Constrained link constraints (narrowband, packet loss, cloud blackout).

#### 3. Exact Presenter Script
> *"In remote, disaster recovery, and tactical environments, continuous raw-audio transmission is challenging on constrained links. Streaming digitized audio waveforms over high-loss or narrowband channels often causes buffering and dropouts. Furthermore, standard speech AI is tethered to cloud servers. iTantra explores an alternative: recognizing speech locally on-device, transmitting compact text message frames, and synthesizing natural voice at the receiving node."*

#### 4. Judge Questions & Defensible Answers
- **Q: How much bandwidth do you save?**  
  *Answer:* Protocol frame sizes depend on UTF-8 payload length (typically ~120–250 bytes per sentence), compared to uncompressed 16 kHz mono audio (~32,000 bytes per second). Actual network bandwidth reduction depends on the specific baseline codec and channel protocol.

---

### SLIDE 3 — THE SOLUTION: DECOUPLED SPEECH PIPELINE

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                         THE SOLUTION: DECOUPLED SPEECH-TO-TEXT-TO-SPEECH                         │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                  │
│   [ Spoken Voice ]                                                                               │
│         │                                                                                        │
│         ▼                                                                                        │
│   [ Offline STT (Sherpa Whisper INT8) ] ──▶ Local On-Device Recognition                          │
│         │                                                                                        │
│         ▼                                                                                        │
│   [ SpeechMessage Protocol Frame ]     ──▶ Compact CRC32-Protected Payload (~120–250 B)          │
│         │                                                                                        │
│         ▼                                                                                        │
│   [ P2P Communication Layer ]          ──▶ Local TCP / Wi-Fi Transport                           │
│         │                                                                                        │
│         ▼                                                                                        │
│   [ Offline Neural TTS (Piper VITS) ]  ──▶ English (Lessac) / Hindi (Priyamvada)                 │
│         │                                                                                        │
│         ▼                                                                                        │
│   [ Reconstructed Audible Voice ]                                                                │
│                                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

#### 1. Slide Text
- **Title:** The Solution: Decoupled Speech-to-Text-to-Speech
- **Decoupled 3-Tier Pipeline:**
  - **Tier 1 (Local Ingestion):** Offline Voice Activity Detection and on-device Speech-to-Text.
  - **Tier 2 (Structured Transport):** CRC32-protected `SpeechMessage` protocol frames.
  - **Tier 3 (Local Reconstruction):** Offline Piper VITS neural speech synthesis rendering natural audio.
- **Why Separate the Layers?**
  - Separates acoustic capture from communication and voice reconstruction.
  - Protocol frames can bind to diverse physical data transports without modifying the core AI pipeline.

#### 2. Visual Layout
- Flow schematic with sequential color-coded blocks highlighting offline execution at both endpoints.

#### 3. Exact Presenter Script
> *"Our architecture decouples speech communication into three distinct layers: local recognition, structured message transport, and local speech synthesis. Instead of streaming audio across the air, we transmit a CRC32-protected `SpeechMessage` frame. The receiving node takes the verified text frame and synthesizes clear, audible speech locally using its own neural voice models."*

#### 4. Judge Questions & Defensible Answers
- **Q: Is this semantic compression?**  
  *Answer:* It is a speech-to-text-to-speech communication architecture that transmits structured text representations rather than neural latent vector embeddings.

---

### SLIDE 4 — IMPLEMENTED SYSTEM ARCHITECTURE

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                  IMPLEMENTED SYSTEM ARCHITECTURE                                 │
├──────────────────────────────────────┬───────────────────────────────────────────────────────────┤
│ TRANSMITTING NODE (Device A)         │ RECEIVING NODE (Device B)                                 │
├──────────────────────────────────────┼───────────────────────────────────────────────────────────┤
│ • Hardware Mic (16 kHz Mono PCM16)   │ • Inbound TCP Listener (Port 42173)                       │
│ • Silero VAD v5 ONNX (<1.5 ms/frame) │ • CRC32 Frame Verification & Deserializer                 │
│ • Sherpa-ONNX Whisper Tiny INT8      │ • Sherpa-ONNX Piper Neural TTS:                           │
│ • SpeechMessage Framing Codec        │     - English: Piper Lessac (16 kHz)                      │
│                                      │     - Hindi: Piper Priyamvada (22.05 kHz)                 │
│                                      │ • Android AudioTrack (Physical Speaker Output)            │
├──────────────────────────────────────┴───────────────────────────────────────────────────────────┤
│                     TRANSPORT LINK: Local P2P Wi-Fi / TCP Socket (Port 42173)                    │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

#### 1. Slide Text
- **Title:** Implemented System Architecture
- **Transmitting Node (Device A):**
  - **Audio Capture:** `AudioRecord` streaming 16 kHz Mono PCM16 in 30 ms chunks.
  - **Voice Activity Detection:** `Silero VAD v5 ONNX` evaluating frames in <1.5 ms.
  - **Speech-to-Text:** `Sherpa-ONNX Whisper Tiny INT8` running locally on CPU.
  - **Message Codec:** Packs transcript, language tag (`en`/`hi`), and timestamp.
- **Communication Layer (M2 Transport):**
  - Binary length-prefixed packet framing over local TCP socket (`0.0.0.0:42173`).
- **Receiving Node (Device B):**
  - **Inbound Handling:** Deserialization & CRC32 integrity verification.
  - **Neural TTS:** `Sherpa-ONNX Piper VITS` (English `lessac` / Hindi `priyamvada`).
  - **Audio Rendering:** `AudioTrack` playback directly to onboard device speaker.

#### 2. Visual Layout
- Prominent dual-node technical blueprint diagram with clear arrows representing internal coroutine dispatchers and network boundaries.

#### 3. Exact Presenter Script
> *"This is our complete implemented architecture running in our Android APK. On Device A, `AudioRecord` feeds `Silero VAD`, which segments speech into `Whisper Tiny INT8`. The output is serialized into a CRC32-protected `SpeechMessage` and sent over local TCP sockets. On Device B, the message is received, routed to our offline Piper VITS engine—synthesizing English with Lessac and Hindi with Priyamvada—and played through the physical speaker via `AudioTrack`."*

#### 4. Judge Questions & Defensible Answers
- **Q: Does this require an active Wi-Fi router?**  
  *Answer:* No internet is used. It operates over local ad-hoc Wi-Fi, direct Wi-Fi P2P, or local hotspot connections between the devices.

---

### SLIDE 5 — WHAT WE ACTUALLY VALIDATED

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                             WHAT WE ACTUALLY VALIDATED ON HARDWARE                               │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                  │
│   [✔] Offline Silero VAD v5 (30 ms frame evaluation in <1.5 ms)                                  │
│   [✔] Offline Whisper Tiny INT8 STT execution on ARM64 CPU                                       │
│   [✔] CRC32-protected SpeechMessage protocol framing                                            │
│   [✔] Physical A ➔ B TCP socket transport on Port 42173 (0% packet loss)                          │
│   [✔] Dual-language neural TTS synthesis (English Lessac & Hindi Priyamvada)                     │
│   [✔] Physical onboard speaker playback via Android AudioTrack                                   │
│   [✔] Full A ➔ B functional voice-to-voice path across two physical devices                      │
│   [✔] 365 / 365 automated unit tests passing with zero failures                                  │
│                                                                                                  │
│   DEFINITIVE PHYSICAL E2E RESULT:                                                                │
│   A live microphone utterance on Device A reached Device B and was reconstructed through         │
│   neural TTS over the physical loudspeaker.                                                      │
│                                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

#### 1. Slide Text
- **Title:** What We Actually Validated on Hardware
- **Physical Validation Checklist (Lenovo TB-X6C6F & vivo I2304):**
  - [x] Offline Silero VAD v5 (30 ms frame evaluation in <1.5 ms).
  - [x] Offline Whisper Tiny INT8 STT execution on ARM64 CPU.
  - [x] CRC32-protected `SpeechMessage` protocol framing.
  - [x] Physical A $\to$ B TCP socket transport on Port 42173 (0% packet loss).
  - [x] Dual-language neural TTS synthesis (English Lessac & Hindi Priyamvada).
  - [x] Physical onboard speaker playback via Android `AudioTrack`.
  - [x] Full A $\to$ B functional voice-to-voice path across two physical devices.
  - [x] 365 / 365 automated unit tests passing with zero failures.
- **Definitive Physical E2E Result:**
  - A live microphone utterance on Device A reached Device B and was reconstructed through neural TTS over the physical loudspeaker.

#### 2. Visual Layout
- Two-column visual checklist with bold green checkmarks and a highlighted bottom banner containing the definitive physical result.

#### 3. Exact Presenter Script
> *"We physically validated each layer across two Android devices. VAD segmentation, Whisper INT8 inference, CRC32 packet framing, physical TCP transmission, and dual-language neural TTS playback through the onboard loudspeaker have all been verified. Across our codebase, 365 out of 365 unit tests pass cleanly. A live microphone utterance on Device A traveled across Wi-Fi to Device B and was synthesized as audible speech."*

#### 4. Judge Questions & Defensible Answers
- **Q: Was this tested in airplane mode?**  
  *Answer:* Yes. The AI inference runs 100% locally on CPU/NPU with zero external network queries.

---

### [DEMO BREAK — 45 to 60 SECONDS]

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       LIVE DEMONSTRATION BREAK                                   │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                                  │
│   [ DEVICE A (Transmitter) ]                  [ LOCAL WI-FI / TCP ]      [ DEVICE B (Receiver) ] │
│   Microphone ➔ VAD ➔ STT ➔ SpeechMessage  ──────────────────────────▶   TTS ➔ Speaker            │
│                                                                                                  │
│   DEMO FLOW:                                                                                     │
│   1. Presenter shows Device A ("Connected") and Device B ("Listening on 42173").                 │
│   2. Speak English into Device A: "Hello from iTantra. This is a live voice to voice test."      │
│   3. Observe transcript appear on Device A "YOU" card.                                           │
│   4. Observe message delivered to Device B "REMOTE" card.                                        │
│   5. Listen to Device B loudspeaker playing synthesized English neural audio.                    │
│   6. Toggle language chip to "हिंदी" and repeat briefly in Hindi.                                 │
│                                                                                                  │
│   *CRITICAL NOTE: Presenter reports the actual displayed transcript without claiming 100%        │
│    phonetic perfection.*                                                                         │
│                                                                                                  │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

#### Demo Action Steps:
1. **Show Devices:** Hold up Device A (Lenovo Tablet) showing `● Connected` and Device B (vivo Phone) showing `M2-M3 RECEIVER: LISTENING (42173)`.
2. **Execute English Live Voice:** Speak into Device A mic: *"Hello from iTantra. This is a live voice to voice test."*
3. **Show Transcript & Delivery:** Display the transcript on Device A and the received message on Device B.
4. **Audible Speaker Playback:** Allow Device B's loudspeaker to clearly play the synthesized Lessac neural audio.
5. **Execute Hindi Live Voice:** Tap the `हिंदी` chip on Device A, speak *"नमस्ते, यह iTantra का हिंदी परीक्षण है।"*, and play synthesized Priyamvada Hindi speech on Device B.
6. **Transition Back to Slides:** Return to Slide 6 for measured benchmark metrics.

---

### SLIDE 6 — MEASURED PERFORMANCE

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 CONTROLLED PERFORMANCE BENCHMARKS                                │
├──────────────────────────────────────────────────┬───────────────────────────────────────────────┤
│ SPEECH-TO-TEXT (Sherpa Whisper INT8)             │ TEXT-TO-SPEECH (Sherpa Piper VITS)            │
├──────────────────────────────────────────────────┼───────────────────────────────────────────────┤
│ • English Controlled Physical Benchmark:         │ • English Production TTS (Lessac):            │
│   - WER: 8.29% | CER: 6.12%                      │   - RTF: 0.92 – 1.11                          │
│   - Mean RTF: 0.8350 (6 test cases)              │   - Latency: 2484 – 4116 ms                   │
│ • Hindi Whisper Controlled Benchmark:            │ • Hindi Production TTS (Priyamvada):          │
│   - WER: 116.87% | CER: 119.39%                  │   - Steady-State RTF: 1.175 – 1.316           │
│   - Mean RTF: 1.2294 (5 FLEURS cases)            │   - P1 (incl. initial staging): 2.464         │
├──────────────────────────────────────────────────┴───────────────────────────────────────────────┤
│ SYSTEM METRICS: APK Size: 315.17 MB (315,167,039 B) • Automated Tests: 365 / 365 PASS (0 Failures)│
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

#### 1. Slide Text
- **Title:** Controlled Performance Benchmarks
- **Speech-to-Text Performance (Sherpa Whisper Tiny INT8 on Lenovo TB-X6C6F):**
  - **English Controlled Benchmark (6 cases):** **WER: 8.29%**, **CER: 6.12%**, **Mean RTF: 0.8350**.
  - **Hindi Whisper Controlled Benchmark (5 cases):** **WER: 116.87%**, **CER: 119.39%**, **Mean RTF: 1.2294** *(exhibits transliteration drift)*.
- **Text-to-Speech Performance (Sherpa Piper VITS):**
  - **English Production TTS (`lessac`):** **RTF 0.92 – 1.11** (Synthesis latency: 2484–4116 ms).
  - **Hindi Production TTS (`priyamvada`):** **Steady-State RTF 1.175 – 1.316** (P1: 2.464 including initial asset staging).
- **Application & System Footprint:**
  - **APK Byte Size:** **315.17 MB** (`315,167,039 bytes` containing all embedded ONNX models).
  - **Automated Test Suite:** **365 / 365 unit tests passed** (0 failures).

#### 2. Visual Layout
- Structured metric grid with large numbers and clear test condition labels.

#### 3. Exact Presenter Script
> *"Here are our controlled physical measurements. On our 6-case English benchmark, Whisper Tiny INT8 achieves an 8.29% WER with a real-time factor of 0.835. Our English neural TTS synthesizes speech with an RTF of 0.92 to 1.11, and Hindi TTS achieves a steady-state RTF of 1.17 to 1.32. The complete offline APK footprint is 315.17 MB, containing all neural models for VAD, STT, and dual-language TTS."*

#### 4. Judge Questions & Defensible Answers
- **Q: Why is Hindi Whisper WER above 100%?**  
  *Answer:* INT8-quantized Whisper Tiny frequently outputs Romanized/transliterated subword tokens when processing Indic audio, incurring deletion and substitution penalties during scoring.

---

### SLIDE 7 — WHERE WE LEARNED THE HARDEST LESSON

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                           WHERE WE LEARNED THE HARDEST ENGINEERING LESSON                        │
├────────────────────────────────────────────────┬─────────────────────────────────────────────────┤
│ CONTROLLED BENCHMARK FIXTURES                  │ PHYSICAL LIVE MICROPHONE REALITY                │
├────────────────────────────────────────────────┼─────────────────────────────────────────────────┤
│ • Strong STT performance on controlled audio:  │ • Substantially degraded STT accuracy on raw    │
│   - English Controlled Benchmark: 8.29% WER    │   tablet microphone in untreated rooms.         │
│   - Clean Reference Fixture: 0.00% WER         │ • High WER observed across physical tests.      │
├────────────────────────────────────────────────┴─────────────────────────────────────────────────┤
│                                 CRUCIAL ARCHITECTURAL FINDING:                                   │
│     The downstream communication pipeline remains functional even with upstream STT noise.       │
│                                                                                                  │
│     "Live microphone STT accuracy remains the principal engineering limitation."                 │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

#### 1. Slide Text
- **Title:** Where We Learned the Hardest Engineering Lesson
- **The Empirical Finding:**
  - Controlled audio fixtures achieve strong recognition (**8.29% WER** on English benchmark).
  - In contrast, raw live tablet microphone audio exhibits **substantially degraded STT accuracy** in untreated physical rooms.
- **Crucial Architectural Resilience:**
  - Our physical two-device testing demonstrated that the downstream communication pipeline remains fully functional even when upstream recognition is imperfect.
  - Spoken words $\to$ hypothesis $\to$ `SpeechMessage` $\to$ remote TCP $\to$ remote TTS $\to$ speaker executed without pipeline stalls, buffer drops, or crashes.
- **Definitive Statement:**
  - *"Live microphone STT accuracy remains the principal engineering limitation."*

#### 2. Visual Layout
- Side-by-side comparison diagram:
  - Left card: Controlled digital fixtures $\to$ Strong STT.
  - Right card: Live physical microphone $\to$ Degraded STT.
  - Bottom card (Emerald accent): Downstream Pipeline Resilience.

#### 3. Exact Presenter Script
> *"Our hardest engineering lesson came from testing on real Android hardware. Controlled benchmarks show strong STT performance with an 8.29% WER on English, but live tablet microphones in untreated rooms suffer substantial recognition degradation. Crucially, our live two-device testing demonstrated that the downstream communication pipeline remains robust: even when Whisper produced an imperfect transcript, the message was framed, transmitted over TCP, and synthesized as audible speech on the receiver without dropping or crashing. Live microphone STT accuracy remains our principal engineering limitation."*

#### 4. Judge Questions & Defensible Answers
- **Q: What causes the live microphone degradation?**  
  *Answer:* Controlled testing demonstrates a large domain gap between studio audio and raw mobile microphone captures; bridging this gap through front-end acoustic adaptation is our primary next milestone.

---

### SLIDE 8 — IMPACT + ROADMAP

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              DEMONSTRATED PROTOTYPE & FUTURE WORK                                │
├────────────────────────────────────────────────┬─────────────────────────────────────────────────┤
│ CURRENTLY DEMONSTRATED                         │ FUTURE WORK (ROADMAP)                           │
├────────────────────────────────────────────────┼─────────────────────────────────────────────────┤
│ • Offline on-device Android AI inference        │ • Microphone-domain acoustic robustness         │
│ • Silero VAD v5 frame segmentation             │ • Model compression & memory optimization       │
│ • Whisper Tiny INT8 STT execution              │ • Additional Indic regional language packs      │
│ • CRC32-protected SpeechMessage protocol       │ • Measured low-bitrate radio evaluation         │
│ • Physical A ➔ B TCP socket transport          │ • Hardware LoRa & tactical VHF modem binding    │
│ • English & Hindi Piper neural TTS             │ • Exploration of stronger Indic ASR models      │
│ • Physical speaker AudioTrack playback         │                                                 │
│ • End-to-end two-device demonstration          │                                                 │
└────────────────────────────────────────────────┴─────────────────────────────────────────────────┘
```

#### 1. Slide Text
- **Title:** Demonstrated Prototype & Future Work
- **Currently Demonstrated:**
  - Offline, on-device Android AI inference.
  - Silero VAD v5 frame segmentation (<1.5 ms).
  - Whisper Tiny INT8 STT execution.
  - CRC32-protected `SpeechMessage` protocol.
  - Physical A $\to$ B TCP transport over local Wi-Fi.
  - English & Hindi Piper neural TTS.
  - Physical speaker `AudioTrack` playback.
  - End-to-end two-device physical demonstration.
- **Future Work (Roadmap):**
  - Microphone-domain acoustic robustness.
  - Model compression and runtime memory optimization.
  - Additional Indic regional language voice models.
  - Measured low-bitrate radio evaluation on hardware links.
  - Hardware LoRa and tactical VHF modem integration.
  - Exploration of stronger Indic ASR models.

#### 2. Visual Layout
- Clean split card:
  - Left column (Dark Emerald): *Currently Demonstrated (8 delivered engineering capabilities)*.
  - Right column (Dark Slate / Blue): *Future Work (6 concrete roadmap milestones)*.

#### 3. Exact Presenter Script
> *"iTantra delivers a functional offline speech communication prototype across two physical devices. Our future work focuses on improving microphone-domain robustness, expanding additional Indian language voice packs, and binding our modular transport layer to physical LoRa and tactical VHF radio modems for field evaluation."*

#### 4. Judge Questions & Defensible Answers
- **Q: How will you connect to tactical radios in the future?**  
  *Answer:* Our M2 transport layer is decoupled via the `MessageTransport` interface, allowing it to bind to serial UART, Bluetooth SPP, or hardware radio modems without modifying the core AI pipeline.

---

## Final 20-Second Closing Statement

> **"In conclusion: iTantra proves that decentralized, offline, on-device AI voice communication is viable on standard Android hardware today. We have validated the complete two-device speech-to-text-to-speech architecture across English and Hindi, while clearly establishing our roadmap for microphone acoustic robustness. Thank you, and we look forward to your questions."**

---

## Continuous 3-Minute Practice Script (Read Aloud)

```text
"Respected judges, we present iTantra—an offline, on-device AI communication system engineered for speech transmission over low-bitrate and bandwidth-constrained links without relying on external cloud infrastructure.

In remote, disaster recovery, and tactical environments, continuous raw-audio transmission is challenging on constrained links. Streaming digitized audio waveforms over high-loss or narrowband channels often causes buffering and dropouts. Furthermore, standard speech AI is tethered to cloud servers. iTantra explores an alternative: recognizing speech locally on-device, transmitting compact text message frames, and synthesizing natural voice at the receiving node.

Our solution decouples voice communication into three modular tiers: local recognition, compact message transport, and local neural speech synthesis. Instead of streaming audio waveforms across the air, we transmit a CRC32-protected SpeechMessage frame. The remote node receives the verified frame and synthesizes native speech through its on-device neural voice engine.

This is our complete implemented architecture running in our Android APK. On Device A, AudioRecord feeds Silero VAD, which segments speech into Whisper Tiny INT8. The output is serialized into a CRC32-protected SpeechMessage and sent over local TCP sockets. On Device B, the message is received, routed to our offline Piper VITS engine—synthesizing English with Lessac and Hindi with Priyamvada—and played through the physical speaker via AudioTrack.

We physically validated each layer across two Android devices. VAD segmentation, Whisper INT8 inference, CRC32 packet framing, physical TCP transmission, and dual-language neural TTS playback through the onboard loudspeaker have all been verified. Across our codebase, 365 out of 365 unit tests pass cleanly. A live microphone utterance on Device A traveled across Wi-Fi to Device B and was synthesized as audible speech.

[-- PERFORM 45-SECOND LIVE DEMO SEQUENCE --]

Here are our controlled physical measurements. On our 6-case English benchmark, Whisper Tiny INT8 achieves an 8.29% WER with a real-time factor of 0.835. Our English neural TTS synthesizes speech with an RTF of 0.92 to 1.11, and Hindi TTS achieves a steady-state RTF of 1.17 to 1.32. The complete offline APK footprint is 315.17 MB, containing all neural models for VAD, STT, and dual-language TTS.

Our hardest engineering lesson came from testing on real Android hardware. Controlled benchmarks show strong STT performance with an 8.29% WER on English, but live tablet microphones in untreated rooms suffer substantial recognition degradation. Crucially, our live two-device testing demonstrated that the downstream communication pipeline remains robust: even when Whisper produced an imperfect transcript, the message was framed, transmitted over TCP, and synthesized as audible speech on the receiver without dropping or crashing. Live microphone STT accuracy remains our principal engineering limitation.

iTantra delivers a functional offline speech communication prototype across two physical devices. Our future work focuses on improving microphone-domain robustness, expanding additional Indian language voice packs, and binding our modular transport layer to physical LoRa and tactical VHF radio modems for field evaluation.

In conclusion: iTantra proves that decentralized, offline, on-device AI voice communication is viable on standard Android hardware today. We have validated the complete two-device speech-to-text-to-speech architecture across English and Hindi, while clearly establishing our roadmap for microphone acoustic robustness. Thank you, and we look forward to your questions."
```
