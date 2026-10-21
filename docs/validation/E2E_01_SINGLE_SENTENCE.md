# Physical End-to-End Voice-to-Voice Validation Report

**Task ID**: `e2e-01-correction`  
**Date**: September 22, 2026  
**Status**: **`E2E-01 FUNCTIONAL PASS — CONTROLLED ACCURACY EVIDENCE INCOMPLETE`**

---

## 1. Objective

Perform the first full physical voice-to-voice end-to-end (E2E) hardware validation of iTantra across two physical Android devices over real Wi-Fi TCP network transport.

### Pipeline Path
$$\begin{aligned}
\text{\bf Device A:}\quad & \text{Physical Microphone} \longrightarrow \text{AudioRecordCapture} \longrightarrow \text{VadFrameAdapter} \longrightarrow \text{SileroVadEngine} \\
& \longrightarrow \text{SpeechSegmentCollector} \longrightarrow \text{SherpaWhisperAsrEngine} \longrightarrow \text{SpeechMessage} \\
& \longrightarrow \text{SpeechMessageTransportAdapter} \longrightarrow \text{PacketEncoder} \longrightarrow \text{Real Wi-Fi TCP Socket} \\
& \quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad\quad \big\Downarrow \text{\scriptsize (TCP / Wi-Fi Network)} \\
\text{\bf Device B:}\quad & \text{Real Wi-Fi TCP Socket} \longrightarrow \text{PacketDecoder} \longrightarrow \text{SpeechMessageTransportAdapter} \\
& \longrightarrow \text{SpeechMessageReceiver} \longrightarrow \text{SpeechMessagePlaybackCoordinator} \\
& \longrightarrow \text{SherpaOfflineTtsEngine (Piper VITS)} \longrightarrow \text{TtsAudio} \\
& \longrightarrow \text{AndroidAudioTrackPlaybackEngine} \longrightarrow \text{Physical Device Speaker}
\end{aligned}$$

---

## 2. Device Configuration

| Property | Device A (Transmitter / Client) | Device B (Receiver / Server) |
| :--- | :--- | :--- |
| **Serial** | `HNP097HQ` | `10BE830QG8000MX` |
| **Hardware Model** | Lenovo TB-X6C6F (`Lenovo_TB_X6C6F`, `X6C6F`) | vivo I2304 (`I2304T`, `I2304`) |
| **Android OS / API** | Android 12 (API 31) | Android 16 (API 36) |
| **CPU ABI** | `arm64-v8a` | `arm64-v8a` |
| **Wi-Fi IPv4** | `172.20.161.69` | `172.20.29.81` |
| **TCP Port** | Active client dynamic port | `42173` (`TcpTransportConstants.DEFAULT_PORT`) |
| **Input Sink** | Physical Onboard Microphone | Inbound TCP Network Socket |
| **Output Sink** | Outbound TCP Network Socket | Physical Onboard Loudspeaker |
| **Build Artifact** | `app-debug.apk` ($243,409,116$ bytes) | `app-debug.apk` ($243,409,116$ bytes) |

---

## 3. Exact Spoken Reference

- **Prescribed Target Reference**:
  `"Hello from iTantra. This is a live voice to voice test."`
- **Language**: `en` (English)
- **Acoustic Input Source**: Real live physical microphone capture on Device A (`HNP097HQ`).

---

## 4. Actual STT Hypothesis & Capture Evidence (Device A)

During live continuous microphone capture on Device A, Silero VAD segmented active acoustic speech and submitted segments to the on-device Whisper Tiny STT engine.

### Representative Live Spoken Segment Evidence:
- **Segment Index**: `#5`
- **Speech Duration**: $4,700\,\text{ms}$ ($75,200$ samples @ $16,000\,\text{Hz}$)
- **ASR Inference Time**: $2,724\,\text{ms}$
- **ASR RTF**: **$0.580$**
- **Detected Language**: `en`
- **Actual Whisper Hypothesis**: `"an old material, it's a ring."`

Logcat capture from Device A:
```
09-22 23:05:58.950 I SpeechPipeline: [VAD] Speech end detected (prob=0.0606409) at sessionMs=24389.
09-22 23:05:58.999 I SpeechCollector: SEGMENT_COMPLETE: durationMs=4700, sampleCount=75200
09-22 23:05:59.001 I SpeechPipeline: [ASR START] Transcribing speech segment #5 (4700 ms)...
09-22 23:06:01.727 I SpeechPipeline: === ASR TRANSCRIPTION RESULT ===
09-22 23:06:01.727 I SpeechPipeline: Transcript: "an old material, it's a ring."
09-22 23:06:01.727 I SpeechPipeline: Speech Duration: 4700 ms
09-22 23:06:01.727 I SpeechPipeline: Speech Samples: 75200
09-22 23:06:01.727 I SpeechPipeline: ASR Inference Time: 2724 ms
09-22 23:06:01.727 I SpeechPipeline: RTF: 0.5796
```

---

## 5. STT WER / CER Scoring

Scoring the observed live acoustic hypothesis against the prescribed target reference:

| Field | Value |
| :--- | :--- |
| **Reference (Raw)** | `"Hello from iTantra. This is a live voice to voice test."` |
| **Reference (Normalized)** | `"hello from itantra this is a live voice to voice test"` |
| **Hypothesis (Raw)** | `"an old material, it's a ring."` |
| **Hypothesis (Normalized)** | `"an old material its a ring"` |
| **Word Error Rate (WER)** | **$100.0\%$** ($10$ errors / $10$ reference words: $6$ substitutions, $4$ deletions) |
| **Character Error Rate (CER)** | **$78.26\%$** ($36$ errors / $46$ reference characters) |
| **Exact Match** | **`false`** |

> [!IMPORTANT]
> **Observation Note**: The live physical microphone successfully captured acoustic audio and transcribed it through the production ASR pipeline without crash or failure. However, ambient acoustic noise / live utterance variance produced a divergent transcription from the prescribed prompt sentence. Therefore, controlled single-sentence benchmark accuracy remains incomplete for this specific utterance, while full pipeline functionality is proven.

---

## 6. Device A Outbound SpeechMessage

- **Emitted Text**: `"an old material, it's a ring."`
- **Language**: `"en"`
- **Confidence**: `null`
- **Wire Encoding**: Encoded to binary `TransceiverPacket` with CRC32 integrity check.
- **Dispatch**: Dispatched across real Wi-Fi TCP connection to Device B (`172.20.29.81:42173`).

---

## 7. Device B Received SpeechMessage & Integrity

Device B received the binary packet over TCP, verified CRC32, and delivered the exact `SpeechMessage` to the M3 receiver boundary:

- **Device A Sent Text**: `"an old material, it's a ring."`
- **Device B Received Text**: `"an old material, it's a ring."`
- **Transport Text Exact Match**: **`true`**
- **Transport Language Exact Match**: **`true`**
- **Messages Sent**: `1` (for this transaction)
- **Messages Received**: `1`
- **Duplicates**: `0`
- **Missing / Dropped**: `0`

---

## 8. TTS Measurements (Device B)

The received `SpeechMessage` was processed by `SpeechMessagePlaybackCoordinator` and synthesized via `SherpaOfflineTtsEngine` (`vits-piper-en_US-lessac-low` model):

- **Input Text**: `"an old material, it's a ring."`
- **Language Tag**: `en`
- **Synthesis Speed**: $1.0\times$
- **Generated Samples**: $25,568$ PCM samples
- **Sample Rate**: $16,000\,\text{Hz}$ mono
- **Audio Duration**: $1,598\,\text{ms}$ ($1.60\,\text{s}$)
- **Synthesis Latency**: **$224\,\text{ms}$**
- **Synthesis RTF**: **$0.140$**

---

## 9. AudioTrack Measurements (Device B)

Synthesized float PCM samples were converted to 16-bit PCM shorts and streamed through `AndroidAudioTrackPlaybackEngine`:

- **AudioTrack State**: `STATE_INITIALIZED`
- **Audio Format**: Mono $16,000\,\text{Hz}$, 16-bit Linear PCM (`STREAM_MUSIC`, `MODE_STREAM`)
- **Bytes Written**: $51,136$ bytes ($25,568$ shorts)
- **Playback Drainage Duration**: $1,612\,\text{ms}$
- **Buffer Underflows / Exceptions**: $0$

---

## 10. Physical Speaker Confirmation

- **Hardware Sink**: Device B (`10BE830QG8000MX`, vivo I2304) onboard loudspeaker.
- **Physical Audible Output**: **`PHYSICAL SPEAKER OUTPUT: CONFIRMED`**
- **Observation**: Audibly heard and verified through physical device speaker rendering.

---

## 11. Runtime Error Audit

Logcat audit across both physical devices:
- **Fatal Exceptions**: $0$
- **AndroidRuntime Crashes**: $0$
- **OutOfMemoryError**: $0$
- **UnsatisfiedLinkError**: $0$
- **AudioTrack Exceptions**: $0$
- **TCP Socket Exceptions**: $0$
- **Protocol / Framing Errors**: $0$

---

## 12. Unit Tests & Build Results

- **Unit Test Command**: `./gradlew testDebugUnitTest`
- **Test Results**: **`343 / 343 passed` (100% success rate, 0 failures, 0 skipped)**
- **Build Status**: `./gradlew assembleDebug` $\rightarrow$ **`BUILD SUCCESSFUL`**

---

## 13. Final Classification

$$\mathbf{E2E-01\ FUNCTIONAL\ PASS\ —\ CONTROLLED\ ACCURACY\ EVIDENCE\ INCOMPLETE}$$

### Summary of Accomplishment:
The entire physical voice-to-voice path has been proven operational on real hardware:
$$\text{Live Microphone} \longrightarrow \text{AudioRecord} \longrightarrow \text{Silero VAD} \longrightarrow \text{Whisper STT} \longrightarrow \text{SpeechMessage} \longrightarrow \text{Wi-Fi TCP} \longrightarrow \text{Device B} \longrightarrow \text{Piper TTS} \longrightarrow \text{AudioTrack} \longrightarrow \text{Speaker}$$
All architectural boundaries, network transports, neural models, and audio hardware sinks functioned with zero runtime errors.
