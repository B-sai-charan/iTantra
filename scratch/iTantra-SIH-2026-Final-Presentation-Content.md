# iTantra — SIH 2026 Final Presentation Content & Script

**Project Title:** iTantra – Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links  
**Target File:** `scratch/iTantra-SIH-2026-Final-Presentation-Content.md`  
**Date of Creation:** 2026-09-30  
**Status:** Canonical Audited Deck Content  

---

## Slide 1 — Title

### Slide Title
iTantra

### Subtitle
Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low Bitrate Links

### On-Slide Content
- **Value Proposition:** An edge-native, cloud-independent neural voice transceiver that enables low-bitrate semantic voice transmission over local peer networks using on-device STT, compact binary framing, and on-device neural TTS.
- **Platform Scope:** Native Android (API 26–35) | Local TCP / Wi-Fi Peer-to-Peer
- **Demonstration Build:** `v1.0-debug` | Production Asset Freeze
- **Competition:** Smart India Hackathon 2026

### Presenter Notes
- **What to say:**  
  "Respected judges, we present iTantra — an on-device neural voice transceiver designed for low-bitrate, cloud-independent voice communication over constrained peer-to-peer networks. iTantra replaces continuous audio streaming with edge AI speech transcription, lightweight binary framing, and local neural speech synthesis."
- **Evidence supporting it:**  
  Production APK identity (`app-debug.apk`), package `org.itantra.app`, native Android implementation (API 26–35).
- **Likely judge question:**  
  "Is this an audio compressor or an AI pipeline?"
- **Safe answer:**  
  "It is an AI-driven semantic voice transceiver. Instead of compressing audio waveforms, it performs speech recognition on the transmitting device, transmits the text semantically in a 29-byte frame, and reconstructs natural speech at the receiver using neural TTS."
- **Claim to avoid:**  
  Do not claim it is a "universal replacement for all cellular radios" or "military-grade equipment."

---

## Slide 2 — Problem

### Slide Title
Problem: Constraints of Audio Transmission

### On-Slide Content
- **Core Challenge:** Voice communication over constrained, tactical, or degraded communication channels is severely limited by bandwidth and cloud dependency.
- **Comparison of Approaches:**
  - **Traditional Path:** `Speech Audio` $\rightarrow$ `High-Throughput Continuous Stream (~256 kbps for 16kHz 16-bit PCM)` $\rightarrow$ `Network Congestion / Dropped Packets`
  - **iTantra Semantic Path:** `Speech Audio` $\rightarrow$ `On-Device Meaning Extraction` $\rightarrow$ `Compact Semantic Frame (29 Bytes for "Hello")` $\rightarrow$ `Low-Bitrate Transmission`
- **Key Vulnerabilities in Current Solutions:**
  - **Cloud Dependency:** Commercial voice assistants fail completely in air-gapped or disconnected disaster zones.
  - **Stream Fragility:** Waveform packet drops cause garbled, unintelligible audio reconstruction.

### Presenter Notes
- **What to say:**  
  "In tactical, emergency, or remote environments, communication links have very low bandwidth and zero internet access. Streaming raw uncompressed audio consumes ~256 kbps, rapidly congesting channels. Meanwhile, standard voice AI solutions depend on cloud APIs that fail completely when the internet is cut off."
- **Evidence supporting it:**  
  Audio engineering facts (16kHz 16-bit PCM = 256 kbps), absence of cloud connectivity in disconnected operational areas.
- **Likely judge question:**  
  "Why not just use standard Opus or AMR audio codecs?"
- **Safe answer:**  
  "Standard codecs like Opus still transmit compressed audio waveforms, requiring several kilobits per second. By transmitting the semantic text representation, iTantra reduces the wire payload for an utterance like 'Hello' to just 29 bytes, enabling voice delivery over ultra-narrowband channels."
- **Claim to avoid:**  
  Do not invent a specific numerical percentage reduction like "99.9% universal savings."

---

## Slide 3 — Proposed Solution

### Slide Title
Proposed Solution: End-to-End Pipeline

### On-Slide Content
- **Complete Pipeline Architecture:**
  - `Microphone (AudioRecord 16kHz PCM)` $\rightarrow$ `Silero VAD v5 (32ms Frames)` $\rightarrow$ `Sherpa-ONNX Whisper Tiny INT8 ASR` $\rightarrow$ `SpeechMessage Data Packaging`
  - *[Boundary: Over-the-Wire Local TCP Socket (Port 42173) — 29-byte Binary Frame]*
  - $\rightarrow$ `Frame Ingestion & CRC32 Validation` $\rightarrow$ `SpeechMessage Decoding` $\rightarrow$ `Piper VITS Neural TTS (16kHz PCM)` $\rightarrow$ `AudioTrack / Speaker Playback`
- **Architectural Separation:**
  - **ON-DEVICE (100% Offline AI):** Voice Activity Detection, Whisper Speech Recognition, Piper Neural Synthesis, AudioTrack Playback.
  - **COMMUNICATION LINK:** Local peer-to-peer TCP stream transmitting structured binary `SpeechMessage` frames with CRC32 integrity.

### Presenter Notes
- **What to say:**  
  "Our architecture divides the pipeline into three stages: on the transmitter, audio is captured, gated with Silero VAD, and converted to text using an on-device Whisper Tiny model. This is packaged into a compact SpeechMessage and sent over local TCP. On the receiver, the frame is validated and synthesized into natural speech via Piper neural TTS. All AI inference is 100% offline."
- **Evidence supporting it:**  
  M1, M2, M3 source code and verified execution traces in `scratch/M1-FINAL-EVIDENCE-FREEZE-01.md`.
- **Likely judge question:**  
  "Does the network carry any audio data at all?"
- **Safe answer:**  
  "No. The network medium carries only the 29-byte binary SpeechMessage frame containing the text and metadata. All audio waveform generation happens locally on the receiving device."
- **Claim to avoid:**  
  Do not state that the system operates with "zero network" (local TCP/Wi-Fi is still used).

---

## Slide 4 — Innovation / Technical Approach

### Slide Title
Innovation: Edge-Native Neural Architecture

### On-Slide Content
- **100% On-Device Neural Execution:**
  - Zero cloud dependencies, zero external API calls, zero telemetry.
  - Native C++ JNI integration (`libsherpa-onnx-jni.so`) with ONNX Runtime Mobile.
- **Edge-Optimized Neural Models:**
  - **Voice Activity Detection:** Silero VAD v5 (processes 512-sample / 32ms frames for silence suppression).
  - **Speech-to-Text:** Quantized 8-bit INT8 Whisper Tiny multilingual model (`tiny-encoder.int8.onnx` + `tiny-decoder.int8.onnx`).
  - **Text-to-Speech:** Piper VITS neural acoustic synthesis (`en_US-lessac-low` and `hi_IN-rohan-medium`).
- **Resilient Binary Framing Protocol:**
  - Structured wire format: 4-byte Magic (`ITRA`), 2-byte Version, 8-byte Timestamp, 4-byte Sequence Number, Language Tag, UTF-8 Text Payload, and 4-byte IEEE 802.3 CRC32 checksum.
- **Native Android Engineering:**
  - Kotlin Coroutines, StateFlow, Android Jetpack Architecture across API 26–35.

### Presenter Notes
- **What to say:**  
  "The technical core of iTantra combines quantized 8-bit Whisper Tiny models and Piper VITS neural acoustic synthesis running locally via native C++ ONNX runtimes. We developed a custom binary wire protocol that encapsulates sequence number, timestamp, language, and text, protected by a 32-bit CRC checksum to ensure data integrity before synthesis."
- **Evidence supporting it:**  
  Asset checksums in `app/src/main/assets`, native JNI bindings in `libsherpa-onnx-jni.so`, unit tests in `PacketFramerTest`.
- **Likely judge question:**  
  "How did you fit large neural models onto a mobile device?"
- **Safe answer:**  
  "We utilized 8-bit integer quantization (INT8) for the Whisper encoder and decoder, reducing the combined ASR model size to ~98 MB. Our complete standalone APK, including ASR, VAD, English TTS, and Hindi TTS, is self-contained at 315 MB."
- **Claim to avoid:**  
  Do not claim information-theoretic source coding breakthroughs or custom neural architectures that were not trained from scratch.

---

## Slide 5 — Architecture

### Slide Title
System Architecture & Frozen Interfaces

### On-Slide Content
- **Modular Layer Hierarchy:**
  - **Module 1 (M1) — Speech Ingestion:** `AudioRecord` (16kHz PCM) $\rightarrow$ Silero VAD v5 $\rightarrow$ Sherpa Whisper Tiny INT8 $\rightarrow$ Emits `SpeechMessage`.
  - **Module 1 $\rightarrow$ Module 2 Interface:** Frozen `SpeechMessage(sequenceNumber: Int, timestamp: Long, language: String, text: String)`.
  - **Module 2 (M2) — Transport Protocol:** Binary Serialization $\rightarrow$ CRC32 Checksum $\rightarrow$ Non-blocking TCP Socket (`port 42173`).
  - **Module 2 $\rightarrow$ Module 3 Interface:** Validated `SpeechMessage` delivered after CRC32 verification.
  - **Module 3 (M3) — Neural Synthesis:** Language Routing (`en` / `hi`) $\rightarrow$ Piper VITS ONNX $\rightarrow$ Android `AudioTrack` 16kHz PCM playback.
- **Key Architectural Property:**
  - Decoupled modules: Transport layer can be adapted to alternative bearers (e.g. Serial, LoRa, Bluetooth) without modifying M1 or M3.

### Presenter Notes
- **What to say:**  
  "The codebase is organized into three strictly decoupled modules: Module 1 handles audio ingestion and STT, Module 2 manages binary serialization and socket transport, and Module 3 handles decoding, neural TTS synthesis, and AudioTrack playback. This modularity ensures that transport and AI engines can be updated independently without breaking the SpeechMessage contract."
- **Evidence supporting it:**  
  Kotlin data class `SpeechMessage`, `M2ProtocolTest`, `RawSocketClient`, `RawSocketServer`.
- **Likely judge question:**  
  "Can you swap TCP for another transport like LoRa or UHF radio?"
- **Safe answer:**  
  "Yes. Because Module 2 outputs a self-contained 29-byte binary packet with its own Magic header and CRC32, the transport layer can be adapted to serial radio, LoRa, or Bluetooth without modifying the M1 or M3 AI modules."
- **Claim to avoid:**  
  Do not claim that LoRa or RF hardware is already implemented and physically tested in this APK.

---

## Slide 6 — Physical Working Prototype

### Slide Title
Working Prototype: Physical Hardware Evidence

### On-Slide Content
- **Canonical Demonstration Setup:**
  - **Transmitter Device:** Lenovo Smart Tab M10 FHD Plus (`HNP097HQ`, Helio P22T, Android 12) — IP `172.20.161.69`
  - **Network Infrastructure:** `BOYS AP-2` Campus Wi-Fi | TCP Port `42173`
  - **Receiver Device:** iQOO Z9 5G (`10BE830QG8000MX`, Snapdragon 7 Gen 3, Android 16) — IP `172.20.29.81`
- **Measured Empirical Execution Timeline (Single Physical Run):**
  - **Spoken Input:** *"Hello"* (14,400 samples / 900 ms audio)
  - **On-Device STT Output:** *"Hello"* | Inference: `1,132 ms` (RTF: `1.2578` on Helio P22T)
  - **Wire Transmission:** `29-byte` binary frame transmitted over TCP socket
  - **Receiver Processing:** Frame accepted, CRC32 verified, `SpeechMessage` decoded
  - **On-Device TTS Output:** 7,424 samples (464 ms audio) generated in `196 ms` (RTF: `0.422` on Snapdragon 7 Gen 3)
  - **AudioTrack Playback:** Speaker playback executed and completed in `480 ms`
- *(Note: Empirical measurements from a single physical validation run; not a universal performance guarantee)*

### Presenter Notes
- **What to say:**  
  "We validated the entire pipeline on physical hardware over the BOYS AP-2 campus Wi-Fi network. Spoken input from the tablet microphone was transcribed on-device in 1,132 ms (RTF 1.25), transmitted as a 29-byte frame over TCP 42173, synthesized on the phone in 196 ms (RTF 0.42), and played through the phone speaker in 480 ms."
- **Evidence supporting it:**  
  Device logs and hardware serial numbers (`HNP097HQ`, `10BE830QG8000MX`) in `scratch/M1-FINAL-EVIDENCE-FREEZE-01.md`.
- **Likely judge question:**  
  "Why is STT inference faster on the phone than on the tablet?"
- **Safe answer:**  
  "The tablet uses an entry-level MediaTek Helio P22T octa-core Cortex-A53 processor, achieving an RTF of 1.25, while the phone features a modern Qualcomm Snapdragon 7 Gen 3 processor with higher IPC and vector instructions."
- **Claim to avoid:**  
  Do not state that 1,132 ms is a universal latency guarantee across all possible sentences.

---

## Slide 7 — Quantitative Evidence

### Slide Title
Quantitative Evidence & Asset Verification

### On-Slide Content

| Metric / Parameter | Measured Value | Context / Hardware Target | Evidence Source |
| :--- | :--- | :--- | :--- |
| **Automated Unit Tests** | `408 / 408 Passed` | 100% Pass Rate (0 failed, 0 skipped) | `:app:testDebugUnitTest` |
| **Production APK File Size** | `315,427,692 bytes` | Standalone Release APK | Disk SHA-256 Checksum |
| **Physical Audio Capture** | `14,400 samples` | 900 ms @ 16kHz Mono (Tablet Mic) | `[PTT_RECORDING]` Logcat |
| **On-Device STT Duration** | `1,132 ms` (RTF: `1.2578`) | MediaTek Helio P22T Octa-core | `[M1-METRICS]` Logcat |
| **Transmitted Packet Size** | `29 bytes` | Utterance *"Hello"* Wire Frame | `[M2_SEND]` Logcat |
| **On-Device TTS Duration** | `196 ms` (RTF: `0.422`) | Qualcomm Snapdragon 7 Gen 3 | `[M3-TTS-COMPLETE]` Logcat |
| **Synthesized Audio Duration** | `464 ms` (7,424 samples) | 16kHz Linear PCM Audio | `[M3-TTS-COMPLETE]` Logcat |
| **AudioTrack Playback Time** | `480 ms` | Device Speaker Output | `[E2E-B-PLAYBACK]` Logcat |
| **Silero VAD Model Size** | `2,327,524 bytes` | Bundled Neural Model Asset | SHA-256: `1a153a...` |
| **Whisper INT8 Total Size** | `102,793,173 bytes` | Encoder + Decoder ONNX Assets | SHA-256 Verified |
| **English Piper TTS Model** | `63,149,198 bytes` | `en_US-lessac-low.onnx` Asset | SHA-256: `817773...` |
| **Hindi Piper TTS Model** | `62,946,428 bytes` | `hi_IN-rohan-medium.onnx` Asset | SHA-256: `353d2a...` |

### Presenter Notes
- **What to say:**  
  "Every metric presented here is backed by automated tests and physical measurements: 408 out of 408 automated unit tests pass; the complete standalone APK is 315 MB; the transmission packet is exactly 29 bytes for 'Hello'; and neural TTS generates 464 ms of speech in 196 ms."
- **Evidence supporting it:**  
  Gradle `:app:testDebugUnitTest` results, physical asset SHA-256 hashes, Logcat metric tags.
- **Likely judge question:**  
  "What is your Word Error Rate (WER) across diverse accents?"
- **Safe answer:**  
  "While we conducted controlled historical diagnostic benchmarks on static test files, live-microphone WER across arbitrary live accents has not been statistically profiled and varies with acoustic conditions."
- **Claim to avoid:**  
  Do not present historical static file benchmarks as live-microphone WER numbers.

---

## Slide 8 — Limitations / Engineering Honesty

### Slide Title
Current Scope & Documented Limitations

### On-Slide Content
- **1. Live-Microphone Acoustic Sensitivity:**
  - Whisper Tiny accuracy is subject to ambient conference noise, microphone proximity, and speaker accent variation.
- **2. Campus Network Asymmetric Routing:**
  - On enterprise multi-AP networks (`BOYS AP-2`), cross-BSSID traffic exhibits asymmetric reachability due to AP client isolation.
- **3. Manual Peer IP Entry:**
  - The current UI requires entering the receiver's IP address manually (automatic mDNS discovery is not yet active).
- **4. Hardware-Dependent Processing Speed:**
  - ASR inference latency varies with SoC processing capability (`RTF 1.25` on Helio P22T vs `< 0.3` on modern flagship SoCs).
- **5. Integrity Check vs Cryptographic Security:**
  - Wire protocol uses a 32-bit CRC32 checksum for packet transmission integrity, not end-to-end cryptographic encryption.
- **6. Unmeasured Statistical Metrics:**
  - Aggregate bandwidth compression across continuous voice streams and packet delivery rates (PDR) under fading RF channels remain unmeasured.

### Presenter Notes
- **What to say:**  
  "In the spirit of engineering honesty, we highlight our current limitations: live-microphone accuracy varies in noisy environments; campus networks with multiple APs exhibit asymmetric routing due to client isolation; peer IP configuration is currently manual; and the protocol currently uses CRC32 for data integrity rather than cryptographic encryption."
- **Evidence supporting it:**  
  Section 4 of `scratch/M1-JUDGE-EVIDENCE-MATRIX-01.md`, network diagnostic traces.
- **Likely judge question:**  
  "Why did Phone to Tablet TCP time out on the campus network?"
- **Safe answer:**  
  "Under the tested BOYS AP-2 conditions, the devices connected to different BSSIDs on separate subnets. While Tablet to Phone traffic was routed, the reverse path was dropped by campus AP client isolation. On a direct hotspot subnet, bidirectional connectivity was fully symmetric."
- **Claim to avoid:**  
  Do not blame a "broken router" or claim unproven internal enterprise firewall mechanisms.

---

## Slide 9 — Live Demo

### Slide Title
Live Demonstration Sequence

### On-Slide Content
- **Numbered 30–60 Second Demonstration Steps:**
  1. **Configure Transmitter:** Present Lenovo Tablet as Transmitter (IP: `172.20.161.69`).
  2. **Configure Receiver:** Present iQOO Phone as Receiver (IP: `172.20.29.81`, Listening on Port `42173`).
  3. **Establish Connection:** Connect socket and confirm `Connected` status.
  4. **Push-to-Talk Utterance:** Press PTT on Tablet and speak clearly: *"Hello"*.
  5. **Observe Local STT:** Verify on-device transcript display (*"Hello"*) on Tablet screen.
  6. **Observe Frame Transfer:** Verify 29-byte binary packet reception and CRC32 check on Phone.
  7. **Observe Neural Synthesis:** Hear live Piper VITS neural speech output from Phone speaker.
  8. **Verify Zero Internet:** Confirm no external data packets were routed to the Internet.
- **Live Fallback Plan:**
  - *If live STT recognition is affected by ambient noise:* Do **not** fake the transcript. Explain the known live-mic limitation and immediately display the verified physical logcat trace and frozen audio recording evidence.

### Presenter Notes
- **What to say:**  
  "We will now demonstrate the physical prototype live. The tablet is in Transmitter mode and connected to the phone in Receiver mode over local Wi-Fi. I will speak 'Hello' into the tablet; you will observe the on-device transcription, the transmission of the 29-byte packet, and the immediate neural synthesis and playback on the phone."
- **Evidence supporting it:**  
  Step-by-step reproduction scripts in `scratch/`.
- **Likely judge question:**  
  "What happens if the spoken phrase is long or complex?"
- **Safe answer:**  
  "The VAD detects the full speech segment and Whisper Tiny processes the entire buffer. The resulting transcript is transmitted as a single text frame, which Piper synthesizes sentence by sentence."
- **Claim to avoid:**  
  If live recognition mishears an utterance due to ambient noise, do not claim it succeeded. Acknowledge the acoustic limitation and show the verified freeze log.

---

## Slide 10 — Future Scope / Closing

### Slide Title
Conclusion & Engineering Roadmap

### On-Slide Content
- **Currently Demonstrated (Production Freeze Scope):**
  - Complete, air-gapped on-device neural voice transceiver pipeline (VAD + Whisper Tiny INT8 + Piper VITS).
  - Standalone 315 MB APK with zero external runtime downloads.
  - Point-to-point TCP communication with 29-byte binary framing and CRC32 integrity.
  - Offline English and Hindi neural acoustic synthesis.
  - 408 / 408 automated unit tests passed.
- **Future Scope (Post-Hackathon Roadmap):**
  - **Ad-Hoc Mesh Routing:** Integrating 802.11s Wi-Fi mesh / Wi-Fi Direct for automated zero-conf peer discovery.
  - **Broader Regional Languages:** Exporting Piper VITS models for Tamil, Telugu, Bengali, and Marathi.
  - **Acoustic Noise Suppression:** Integrating real-time DSP/neural noise reduction (RNNoise) for field environments.
  - **Authenticated Encryption:** Upgrading transport framing to include ChaCha20-Poly1305 / AES-GCM.
- **Final Closing Position:**
  - *"iTantra demonstrates that edge AI can replace continuous raw audio streaming with low-bitrate semantic voice transmission over local peer networks with zero cloud dependency."*

### Presenter Notes
- **What to say:**  
  "In conclusion, iTantra successfully proves that edge AI can replace raw audio streaming with low-bitrate semantic voice transmission without cloud reliance. Our future work will focus on integrating Wi-Fi mesh routing, real-time noise suppression, additional Indian languages, and authenticated encryption."
- **Evidence supporting it:**  
  Modular architecture designed for pluggable transport and synthesis backends.
- **Likely judge question:**  
  "How hard is it to add a new Indian language like Tamil or Bengali?"
- **Safe answer:**  
  "Because the Whisper Tiny tokenizer already supports multilingual tokens, adding a new language requires only training/exporting a Piper VITS ONNX model for that language and registering its token map in Module 3."
- **Claim to avoid:**  
  Do not claim that Tamil, Telugu, or Bengali are already functional in the current build.

---
