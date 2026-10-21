# iTantra Top-Level Transceiver Coordinator Architecture & Lifecycle Audit

> **TASK ID:** `system-coordinator-02` (Harden Coordinator-to-Transport Lifecycle & Error Wiring)  
> **DATE:** 2026-09-21  
> **STATUS:** `SYSTEM COORDINATOR LIFECYCLE AUDIT COMPLETE`  
> **EVIDENTIARY CLASSIFICATION:**  
> - `[DOCUMENTED FACT]`: Codebase contracts, types, and JVM unit test verifications.  
> - `[ENGINEERING DECISION]`: Architectural policies selected for the iTantra transceiver MVP.  
> - `[NOT MEASURED]`: Physical device hardware audio capture and physical speaker audio rendering.  

---

## 1. Executive Summary & Component Responsibilities

[`TransceiverCoordinator`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverCoordinator.kt) is the top-level domain orchestrator connecting the three decoupled subsystem modules of the iTantra tactical speech transceiver:

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   TransceiverCoordinator                                        │
│  - Orchestrates subsystem lifecycle (start, stop, release)                                     │
│  - Connects M1 SpeechMessage output -> M2 transport sink                                        │
│  - Connects M2 incoming packets -> M3 TTS playback coordinator                                  │
│  - Dispatches unified state changes and subsystem errors to TransceiverListener                 │
└──────────────┬───────────────────────────────┬──────────────────────────────────┬───────────────┘
               │                               │                                  │
               ▼                               ▼                                  ▼
┌─────────────────────────────┐ ┌─────────────────────────────┐ ┌─────────────────────────────────┐
│     MODULE 1: SPEECH        │ │    MODULE 2: COMMUNICATION  │ │   MODULE 3: TTS & PLAYBACK      │
│  - AudioRecord Capture      │ │  - TransceiverPacket Mapper │ │  - SpeechMessage Receiver       │
│  - Silero VAD v5            │ │  - PacketEncoder (CRC-32)   │ │  - Sherpa OfflineTts Engine     │
│  - SpeechSegmentCollector   │ │  - PacketDecoder (CRC-32)   │ │  - VITS Neural Synthesis        │
│  - Whisper-Tiny INT8 ASR    │ │  - TCP Socket Message       │ │  - Serialized AudioPlayback     │
│  - SpeechMessage Emission   │ │    Transport                │ │    Engine                       │
└─────────────────────────────┘ └─────────────────────────────┘ └─────────────────────────────────┘
```

### Responsibility Boundaries:
- **Module 1 (M1):** Local speech acquisition, voice activity detection, and speech-to-text transcription resulting in [`SpeechMessage`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/domain/SpeechMessage.kt).
- **Module 2 (M2):** Packet mapping, binary serialization, CRC-32 verification, TCP socket framing, and deserialization.
- **Module 3 (M3):** Offline neural speech synthesis (`TtsEngine`) and serialized raw audio sample playback (`AudioPlaybackEngine`).
- **Coordinator:** Pure orchestration and lifecycle wiring. Zero audio DSP, zero socket I/O, zero UI rendering.

---

## 2. End-to-End Data Flow

### A. Local Speech Flow (Outbound: Local Microphone -> Remote Network)

```
[ MIC (Hardware) ]
        │ PCM16 @ 16kHz
        ▼
[ AudioRecordCapture ]
        │ Audio Frames
        ▼
[ SileroVadEngine v5 ]
        │ Speech Probability & Boundaries
        ▼
[ SpeechSegmentCollector ]
        │ Completed Utterance Audio Buffer
        ▼
[ SherpaWhisperAsrEngine ]
        │ AsrResult(text, language, confidence, latency)
        ▼
[ SpeechPipeline ]
        │ SpeechMessage(text, language, timestamp, confidence)
        ▼
[ SpeechMessageTransportAdapter (implements SpeechMessageSink) ]
        │ TransceiverPacket (Header, Sequence, CRC-32)
        ▼
[ PacketEncoder ]
        │ Serialized Byte Payload
        ▼
[ MessageTransport (e.g. SocketMessageTransport) ]
        │ Length-Prefixed TCP Frame
        ▼
[ Local Wi-Fi Network ]
```

### B. Remote Speech Flow (Inbound: Remote Network -> Local Speaker)

```
[ Local Wi-Fi Network ]
        │ Length-Prefixed TCP Frame
        ▼
[ MessageTransport (e.g. SocketMessageTransport) ]
        │ TransportFrame(sender, payload, timestamp)
        ▼
[ SpeechMessageTransportAdapter ]
        │ PacketDecoder.decode() (CRC-32 Verification)
        ▼
[ TransceiverPacketMapper ]
        │ Reconstructed SpeechMessage
        ▼
[ SpeechMessageReceiver Boundary ]
        │ onSpeechMessageReceived(message)
        ▼
[ SpeechMessagePlaybackCoordinator ]
        │ ttsEngine.synthesize(message.text, message.language)
        ▼
[ SherpaOfflineTtsEngine (VITS Piper ONNX) ]
        │ TtsAudio(PCM FloatArray, 16kHz Mono)
        ▼
[ AudioPlaybackEngine (Serialized Execution) ]
        │ PCM Samples
        ▼
[ Android AudioTrack Hardware Sink (Future) ]
        │ Acoustic Waveform
        ▼
[ Speaker Output ]
```

---

## 3. Transport Lifecycle Ownership

In accordance with the M2 transport design ([`TRANSPORT_ARCHITECTURE_DECISION.md`](file:///c:/Users/saich/Downloads/SIH/PS26173/docs/communication/TRANSPORT_ARCHITECTURE_DECISION.md)):

| Operation | Component Owner | Lifecycle Policy & Rationale |
| :--- | :--- | :--- |
| **`connect` / `listen`** | Network / Session Manager | Owned outside the coordinator. Isolates the domain coordinator from peer discovery, IP address resolution, and socket topologies (client vs server vs Wi-Fi Direct). |
| **`send`** | `SpeechMessageTransportAdapter` / Coordinator | Owned by `SpeechMessageTransportAdapter` which acquires sequence numbers, encodes packets, and dispatches byte payloads to `MessageTransport.send()`. |
| **`receive`** | `MessageTransport` background worker | Transport loop continuously reads length-prefixed frames from `InputStream` and dispatches to `TransportFrame` receiver. |
| **`close`** | Dependency Owner (`ownsDependencies`) | If `ownsDependencies = false`, transport channel lifecycle is owned by caller/service. If `ownsDependencies = true`, coordinator closes `SpeechMessageTransportAdapter` and `MessageTransport` upon `release()`. |

---

## 4. Running State & Shutdown Semantics

### A. Running State Semantics
**`TransceiverCoordinator.RUNNING` means:**
- Local M1 `SpeechPipeline` (AudioRecord capture, Silero VAD, Whisper ASR) is operational.
- Local M3 `SpeechMessagePlaybackCoordinator` is actively registered on `SpeechMessageTransportAdapter` and ready to receive incoming packets.

**`ConnectionManager.CONNECTED` means:**
- A physical or virtual network transport session (`MessageTransport`) has been established (client socket connected or server socket bound and listening).

**Crucial Separation of Concerns:**
`TransceiverCoordinator.RUNNING` and `ConnectionManager.CONNECTED` are completely separate states:
- The domain coordinator reaching `RUNNING` does **NOT** imply that the entire transceiver is network-ready.
- If a socket connection drops or is temporarily disconnected (`ConnectionManager` transitions to `FAILED` or `DISCONNECTED`), the coordinator remains in `RUNNING` so that local speech transcription continues uninterrupted.
- Outbound transmission failures over disconnected transport are reported to `TransceiverListener.onError(M2TransportError)` without crashing local speech capture.
- Incoming packets resume automatically when `ConnectionManager` re-establishes `CONNECTED` status.

### B. Shutdown Semantics
When `stop()` or `release()` is invoked:
1. State transitions to `STOPPING`.
2. M1 `SpeechPipeline` audio capture is stopped immediately to halt new speech segment production.
3. Inbound receiver and protocol error listener are detached (`setSpeechMessageReceiver(null)`, `setProtocolErrorListener(null)`).
4. Any incoming network frame arriving after `stop()` is initiated is dropped at the adapter boundary (`handleIncomingFrame` returns immediately without invoking receiver).
5. Active audio playback is stopped.
6. State transitions to `STOPPED` (or `RELEASED`).

---

## 5. Error Wiring & Source-to-Listener Mapping

All domain subsystems route failures into a unified, strongly-typed error hierarchy without swallowing root causes:

```
┌───────────────────────────┐
│     Subsystem Failure     │
└─────────────┬─────────────┘
              ▼
┌───────────────────────────┐
│  Domain Exception Source  │
└─────────────┬─────────────┘
              ▼
┌───────────────────────────┐
│  TransceiverCoordinator   │  Translates to TransceiverError
└─────────────┬─────────────┘
              ▼
┌───────────────────────────┐
│ TransceiverListener.onError│  Dispatches to UI / Observer
└───────────────────────────┘
```

### Comprehensive Error Mapping Matrix:

| Subsystem | Source Component & Exception | Intermediary Handler | Emitted TransceiverError |
| :--- | :--- | :--- | :--- |
| **M1 Speech** | `AudioRecordCapture` audio error / `SileroVad` failure | `SpeechPipelineListener.onError` | [`TransceiverError.M1PipelineError`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverError.kt) |
| **M1 Speech** | `WhisperAsrEngine` transcription error | `SpeechPipeline.startAsrWorker()` | [`TransceiverError.M1PipelineError`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverError.kt) |
| **M2 Transport** | `SocketMessageTransport.send()` / socket write failure | `SpeechPipeline` / `sendOutboundSpeechMessage` | [`TransceiverError.M2TransportError`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverError.kt) |
| **M2 Protocol** | `PacketDecoder.decode()` / CRC-32 mismatch / bad magic | `SpeechMessageTransportAdapter` protocol error listener | [`TransceiverError.M2TransportError`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverError.kt) |
| **M3 TTS** | `SherpaOfflineTtsEngine` unsupported language (`"hi"`) | `SpeechMessagePlaybackCoordinator.processMessage()` | [`TransceiverError.M3TtsError`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverError.kt) |
| **M3 TTS** | `SherpaOfflineTtsEngine` uninitialized engine state | `SpeechMessagePlaybackCoordinator.processMessage()` | [`TransceiverError.M3TtsError`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverError.kt) |
| **M3 Playback** | `AudioPlaybackEngine.play()` hardware DAC error | `SpeechMessagePlaybackCoordinator.processMessage()` | [`TransceiverError.M3PlaybackError`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverError.kt) |
| **Coordinator** | `start()` after `release()` or transition violation | `TransceiverCoordinator` lifecycle state check | [`TransceiverError.LifecycleError`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverError.kt) |

---

## 6. MainActivity Audit & Extraction Recommendation

### Current MainActivity State:
- [`MainActivity.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/MainActivity.kt) currently instantiates `SpeechPipeline` directly in `onCreate()` for standalone ASR verification.
- Control buttons directly invoke `speechPipeline.start()`, `stop()`, and `release()`.

### Recommended Future Extraction:
In a future UI task (`transceiver-ui-01`):
1. `MainActivity` will instantiate `TransceiverCoordinator` (or inject it via ViewModel/Service).
2. UI buttons will bind to `coordinator.start()`, `coordinator.stop()`, and `coordinator.sendOutboundSpeechMessage()`.
3. `MainActivity` will implement `TransceiverListener` to render state changes, outbound transcripts, inbound transcripts, and error banners in the UI.

---

## 7. Evidentiary Status

> **Actual acoustic audio capture, hardware TTS synthesis, and physical speaker playback remain device-validation tasks pending tablet availability.**

| Feature / Layer | Status |
| :--- | :--- |
| **Top-Level Coordinator (`TransceiverCoordinator`)** | `[IMPLEMENTED & TESTED]` |
| **Lifecycle State Machine (`TransceiverState`)** | `[IMPLEMENTED & TESTED]` |
| **Domain Error Hierarchy (`TransceiverError`)** | `[IMPLEMENTED & TESTED]` |
| **Observer Interface (`TransceiverListener`)** | `[IMPLEMENTED & TESTED]` |
| **Protocol Error Wiring & Discard Detection** | `[IMPLEMENTED & TESTED]` |
| **Programmatic Outbound Send API** | `[IMPLEMENTED & TESTED]` |
| **Unit Test Coverage (`TransceiverCoordinatorTest`)** | `[IMPLEMENTED & TESTED]` (23 unit tests, 100% pass) |
| **Physical Hardware AudioTrack Output** | `NOT MEASURED (Deferred)` |
| **Real Device Multi-Node Execution** | `NOT MEASURED (Device Unavailable)` |
