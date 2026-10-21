# iTantra End-to-End Static Architecture Audit

> **TASK ID:** `e2e-static-audit-02` (Correct Evidence Classification in E2E Architecture Audit)  
> **DATE:** 2026-09-21  
> **STATUS:** `E2E AUDIT EVIDENCE CLASSIFICATION CORRECTED — DEVICE VALIDATION REQUIRED`  
> **EVIDENTIARY CLASSIFICATION LABELS:**  
> - `[VERIFIED BY CODE]`: Audited directly against active Kotlin source code implementations.  
> - `[VERIFIED BY JVM TEST]`: Verified through 318 deterministic JVM unit tests (using test doubles, loopback, or local in-process TCP sockets).  
> - `[NOT DEVICE VALIDATED]`: Software implementation complete, pending physical Lenovo tablet testing.  
> - `[NOT IMPLEMENTED]`: Intentionally deferred feature (e.g. Android `AudioTrack` hardware sink, PTT, Wi-Fi Direct).  
> - `[REQUIRES DEVICE VALIDATION]`: Hardware-dependent property requiring empirical measurement on physical Android hardware.  

---

## 1. Executive Summary

This static architecture audit evaluates the end-to-end communication flow across the three core decoupled subsystems of the iTantra tactical speech transceiver:
- **Phone A (Transmitter Conceptual Flow):** Microphone $\to$ Silero VAD $\to$ Sherpa Whisper ASR $\to$ `SpeechMessage` $\to$ `PacketEncoder` $\to$ TCP Transport.
- **Phone B (Receiver Conceptual Flow):** TCP Transport $\to$ `PacketDecoder` $\to$ `SpeechMessage` $\to$ Sherpa VITS TTS $\to$ `AudioPlaybackEngine` $\to$ (Future Android `AudioTrack`).

### Audit Verdict:
1. **Static Software Architecture & Boundary Contracts:** `[VERIFIED BY CODE]` & `[VERIFIED BY JVM TEST]`.  
   All domain contracts (`SpeechMessage`, `TransceiverPacket`, `SpeechMessageSink`, `SpeechMessageReceiver`, `MessageTransport`, `ConnectionManager`) are properly connected without orphaned components, leaked sockets, or unhandled exceptions. All 318 JVM unit tests pass deterministically.
2. **Physical Device Validation Status:** `[REQUIRES DEVICE VALIDATION]`.  
   The system has **NOT** been validated on physical Android devices. Physical validation remains required for:
   - Hardware microphone initialization, OS permission handling, and physical acoustic capture stability.
   - On-device Sherpa Whisper ASR and Silero VAD inference latency, memory footprint, and Real-Time Factor (RTF) on ARM64 CPU.
   - Physical Wi-Fi radio / portable hotspot network connectivity, packet loss, and throughput between separate Android devices.
   - Sherpa OfflineTts neural synthesis RTF on physical hardware.
   - Concrete Android `AudioTrack` implementation, hardware DAC audio rendering, and physical speaker output.
   - Real-device thread scheduling, power management, and hardware buffer underrun characteristics.

---

## 2. Phone A Outbound Runtime Trace (Local Speech $\to$ Network)

```
[ MIC (Hardware) ]
        │ 16kHz Mono PCM16 [REQUIRES DEVICE VALIDATION]
        ▼
[ AudioRecordCapture ] ──(Capture Thread: "iTantra-AudioCaptureThread", THREAD_PRIORITY_URGENT_AUDIO)
        │ Discrete ShortArray frames (480 samples / 30ms) [VERIFIED BY CODE]
        ▼
[ VadFrameAdapter -> SileroVadEngine v5 ] ──(In-line on Capture Thread)
        │ Probability & Boundary Events (isSpeechStart, isSpeechEnd) [VERIFIED BY CODE]
        ▼
[ SpeechSegmentCollector ] ──(In-line on Capture Thread)
        │ Completed Utterance Audio Buffer (ShortArray) [VERIFIED BY CODE]
        ▼
[ SpeechPipeline.asrChannel ] ──(Unbounded Channel: trySend())
        │ AsrJob [VERIFIED BY CODE]
        ▼
[ SpeechPipeline.startAsrWorker() ] ──(Coroutine Scope: Dispatchers.Default)
        │ Sherpa Whisper ASR Native Transcription [VERIFIED BY CODE]
        ▼
[ AsrResult ]
        │ Text, Language, Confidence, Latency [VERIFIED BY CODE]
        ▼
[ SpeechMessageMapper.fromAsrResult() ]
        │ SpeechMessage(text, language, timestamp, confidence) [VERIFIED BY JVM TEST]
        ▼
[ SpeechPipelineListener.onSpeechMessage() ] ──> Dispatches to local TransceiverListener [VERIFIED BY JVM TEST]
        │
        ▼
[ SpeechMessageSink.emit() ]
        │
        ▼
[ SpeechMessageTransportAdapter.sendSpeechMessage() ]
        │ Sequence Allocation -> TransceiverPacketMapper.fromSpeechMessage() [VERIFIED BY JVM TEST]
        ▼
[ PacketEncoder.encode() ]
        │ Encoded Binary Payload + CRC-32 (29B - ~65KB) [VERIFIED BY JVM TEST]
        ▼
[ MessageTransport.send() -> SocketMessageTransport.send() ] ──(Dispatchers.IO under sendMutex)
        │ 4-byte Big-Endian Length Prefix + Encoded Packet [VERIFIED BY JVM TEST]
        ▼
[ Local In-Process / Loopback TCP Socket ] ──(JVM Tested)
        │
        ▼
[ Physical Wi-Fi Radio Transmission ] ──(NOT DEVICE VALIDATED)
```

### Outbound Boundary Audit:
| Boundary Stage | Producer | Consumer | Thread Context | Owner | Error Behavior | Evidence Classification |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Audio Capture** | `AudioRecord` | `runCaptureLoop` | Capture Thread (`URGENT_AUDIO`) | `AudioRecordCapture` | Reports error code, transitions to `ERROR` | `[VERIFIED BY CODE]` / `[REQUIRES DEVICE VALIDATION]` |
| **VAD Detection** | Capture Loop | `SileroVadEngine` | Capture Thread | `SpeechPipeline` | Falls back to silence on error | `[VERIFIED BY CODE]` / `[NOT DEVICE VALIDATED]` |
| **Segment Collection**| VAD Events | `SpeechSegmentCollector` | Capture Thread | `SpeechPipeline` | Resets buffer on stop/overflow | `[VERIFIED BY CODE]` / `[VERIFIED BY JVM TEST]` |
| **Whisper STT** | `asrChannel` | `SherpaWhisperAsrEngine` | `Dispatchers.Default` | `SpeechPipeline` | Logs error, calls `onError("ASR Error: ...")` | `[VERIFIED BY CODE]` / `[NOT DEVICE VALIDATED]` |
| **Message Mapping** | STT Result | `SpeechMessageMapper` | `Dispatchers.Default` | `SpeechPipeline` | Bit-exact domain mapping | `[VERIFIED BY JVM TEST]` |
| **Transport Sink** | Pipeline | `SpeechMessageTransportAdapter` | `Dispatchers.Default` | Adapter | Throws `TransportException` if unbound | `[VERIFIED BY JVM TEST]` |
| **Packet Encoding** | `SpeechMessage` | `PacketEncoder` | `Dispatchers.Default` | M2 Codec | Throws `ProtocolException` on invariant violation | `[VERIFIED BY JVM TEST]` |
| **TCP Transmission**| Encoded Bytes | `SocketMessageTransport` | `Dispatchers.IO` (Mutex) | `ConnectionManager` | Throws `TransportException`, marks `FAILED` | `[VERIFIED BY JVM TEST]` (Local sockets) |
| **Physical RF Link**| Sockets | Wi-Fi Radio | OS Kernel / Hardware | OS / Hardware | Physical packet loss / RF fading | `[NOT DEVICE VALIDATED]` |

---

## 3. Phone B Inbound Runtime Trace (Network $\to$ Speaker)

```
[ Physical Wi-Fi Radio Reception ] ──(NOT DEVICE VALIDATED)
        │
        ▼
[ Local In-Process / Loopback TCP Socket ] ──(JVM Tested)
        │ Length-prefixed TCP Stream
        ▼
[ SocketMessageTransport.startReceiveLoop() ] ──(Coroutine Scope: Dispatchers.IO)
        │ TcpFraming.readFrame() (4-byte length header + payload bytes) [VERIFIED BY JVM TEST]
        ▼
[ TransportFrame ]
        │ Payload ByteArray [VERIFIED BY JVM TEST]
        ▼
[ SpeechMessageTransportAdapter.handleIncomingFrame() ] ──(Dispatchers.IO)
        │ PacketDecoder.decode() (Validates magic 0x5458 & CRC-32) [VERIFIED BY JVM TEST]
        ▼
[ TransceiverPacketMapper.toSpeechMessage() ]
        │ Reconstructed SpeechMessage [VERIFIED BY JVM TEST]
        ▼
[ SpeechMessageReceiver.onSpeechMessageReceived() ]
        │
        ▼
[ SpeechMessagePlaybackCoordinator.processMessage() ] ──(Dispatchers.IO)
        │ ttsEngine.synthesize(message.text, message.language) [VERIFIED BY JVM TEST]
        ▼
[ SherpaOfflineTtsEngine (VITS Piper ONNX) ]
        │ TtsAudio(FloatArray PCM samples, 16kHz Mono) [VERIFIED BY CODE] / [NOT DEVICE VALIDATED]
        ▼
[ AudioPlaybackEngine.play() (Abstraction) ] ──(Serialized Playback Execution) [VERIFIED BY JVM TEST]
        │
        ▼
[ Android AudioTrack Hardware Sink ] ──(NOT IMPLEMENTED)
        │
        ▼
[ Physical Speaker Acoustic Waveform ] ──(NOT DEVICE VALIDATED)
```

### Inbound Boundary Audit:
| Boundary Stage | Producer | Consumer | Thread Context | Owner | Error Behavior | Evidence Classification |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Deframing** | TCP Stream | `TcpFraming.readFrame` | `Dispatchers.IO` | `SocketMessageTransport` | Rejects frames $> 70\text{ KB}$ or 0 bytes | `[VERIFIED BY JVM TEST]` (Local stream) |
| **Packet Decoding** | Raw Payload | `PacketDecoder.decode` | `Dispatchers.IO` | M2 Codec | Discards frame on CRC mismatch, notifies protocol listener | `[VERIFIED BY JVM TEST]` |
| **Message Delivery**| Decoder | `SpeechMessageReceiver` | `Dispatchers.IO` | Adapter / M3 | Downstream M3 errors isolated from receive loop | `[VERIFIED BY JVM TEST]` |
| **TTS Synthesis** | `SpeechMessage`| `SherpaOfflineTtsEngine` | Caller / Worker | `PlaybackCoordinator` | Emits `TransceiverError.M3TtsError` on failure | `[VERIFIED BY CODE]` / `[NOT DEVICE VALIDATED]` |
| **Playback Abstraction** | `TtsAudio` | `AudioPlaybackEngine` | Playback Worker | `PlaybackCoordinator` | Emits `TransceiverError.M3PlaybackError` | `[VERIFIED BY JVM TEST]` (Fake engine) |
| **Hardware AudioTrack** | PCM Samples | Android `AudioTrack` | Audio HAL | Deferred | Deferred until hardware available | `[NOT IMPLEMENTED]` |
| **Physical Speaker** | `AudioTrack` | Speaker Hardware | Acoustic DAC | Hardware | DAC underruns, physical audio distortion | `[NOT DEVICE VALIDATED]` |

---

## 4. Connection Lifecycle & Dynamic Binding

```
ConnectionManager.connect()
       │
       ├── 1. Instantiates SocketMessageTransport / TcpServerTransport [VERIFIED BY JVM TEST]
       ├── 2. Transitions state to CONNECTED [VERIFIED BY JVM TEST]
       └── 3. adapter.bindTransport(activeTransport) [VERIFIED BY JVM TEST]
                   │
                   └── Sets activeTransport, clears old receiver, registers new receiver
```

### Verified Software Properties:
1. **Dynamic Transport Binding:** `SpeechMessageTransportAdapter` dynamically binds to the active `MessageTransport` when `ConnectionManager` connects and unbinds when it disconnects `[VERIFIED BY JVM TEST]`.
2. **Quarantine of Disconnected Sockets:** Calling `disconnect()` explicitly unbinds the transport and clears its receiver callback (`oldTransport.setReceiver(null)`). Inbound frames delivered on an old socket after disconnect cannot invoke callbacks on the adapter `[VERIFIED BY JVM TEST]`.
3. **Preservation of Domain State:** Monotonic sequence numbers (`currentSequenceNumber`), M3 playback callbacks (`SpeechMessageReceiver`), and protocol error listeners remain permanently preserved across reconnect cycles `[VERIFIED BY JVM TEST]`.
4. **Physical Networking Boundary:** All connection tests ran using JVM loopback and local in-process TCP sockets (`127.0.0.1`). Real-device Wi-Fi hotspot association and physical network latency are `[NOT DEVICE VALIDATED]`.

---

## 5. Critical Component Initialization & Readiness Table

| Component | Must Initialize Before Start? | Owner | Failure Behavior | Verification Status |
| :--- | :--- | :--- | :--- | :--- |
| **`AudioRecordCapture`** | Yes (on `start()`) | `SpeechPipeline` | Fails start with `IllegalStateException` | `[VERIFIED BY CODE]` / `[REQUIRES DEVICE VALIDATION]` |
| **`SileroVadEngine`** | Yes (`initialize()`) | `SpeechPipeline` | Fails start if ONNX asset missing | `[VERIFIED BY CODE]` / `[NOT DEVICE VALIDATED]` |
| **`SherpaWhisperAsrEngine`** | Yes (`initialize()`) | `SpeechPipeline` | Fails start if model missing | `[VERIFIED BY CODE]` / `[NOT DEVICE VALIDATED]` |
| **`SpeechMessageTransportAdapter`** | Dynamic binding | Transceiver | Throws `TransportException` if unbound | `[VERIFIED BY JVM TEST]` |
| **`TcpConnectionManager`** | On `connect()` | Connection Layer | Transitions to `FAILED`, notifies listener | `[VERIFIED BY JVM TEST]` (Local sockets) |
| **`SherpaOfflineTtsEngine`** | Yes (`initialize()`) | M3 Subsystem | Throws `TtsEngineNotInitializedException` | `[VERIFIED BY CODE]` / `[NOT DEVICE VALIDATED]` |
| **`AudioPlaybackEngine` (Abstraction)**| On creation | M3 Subsystem | Throws `AudioPlaybackException` | `[VERIFIED BY JVM TEST]` (Fake engine) |
| **Android `AudioTrack`** | Deferred | Playback Subsystem | Not implemented | `[NOT IMPLEMENTED]` |

---

## 6. Threading & Synchronization Audit

### Identified Synchronization Mechanisms in Code:
1. **Android Main Thread Policy:** The implementation contains no blocking socket I/O, audio capture, or ONNX inference calls on the Android Main/UI thread `[VERIFIED BY CODE]`.
2. **Audio Capture Thread Configuration:** `AudioRecordCapture` creates a dedicated background thread named `"iTantra-AudioCaptureThread"` and calls `Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)` `[VERIFIED BY CODE]`.  
   *(Note: Actual OS thread scheduling jitter, CPU throttling, and audio ring buffer underruns depend on hardware and are `[REQUIRES DEVICE VALIDATION]`.)*
3. **Decoupled ASR Queue:** Audio capture offloads completed segment buffers into an unbounded channel (`asrChannel`) read by a coroutine on `Dispatchers.Default` `[VERIFIED BY CODE]`.
4. **TCP Send Mutex:** `SocketMessageTransport.send()` uses `sendMutex` on `Dispatchers.IO` to serialize frame transmission and prevent length-prefix / payload byte interleaving `[VERIFIED BY JVM TEST]`.
5. **Transport Receive Loop:** `SocketMessageTransport.startReceiveLoop()` runs as an independent coroutine on `Dispatchers.IO` reading length-prefixed frames `[VERIFIED BY JVM TEST]`.
6. **State Synchronization:** Subsystem state machines (`AudioRecordCapture`, `TransceiverCoordinator`, `SocketMessageTransport`, `TcpConnectionManager`) use `@Volatile` fields and synchronized blocks / mutexes to guard state transitions `[VERIFIED BY JVM TEST]`.  
   *(Note: Real-device multi-core concurrency under OS power-saving and backgrounding is `[REQUIRES DEVICE VALIDATION]`.)*

---

## 7. Error Propagation Audit (Scenarios A through J)

| ID | Failure Scenario | Originating Layer | Code Path Implemented? | JVM Test Verified? | Physical Device Validation Required? |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **A** | Microphone hardware failure | `AudioRecordCapture` | `YES` (`AudioCaptureListener.onError`) | `YES` (`FakeAudioCapture` test) | `YES` (Physical mic failure / permission revocation) |
| **B** | VAD processing error | `SileroVadEngine` | `YES` (`VadFrameAdapter` fallback) | `YES` (`VadFrameAdapterTest`) | `YES` (Native ONNX session corruption) |
| **C** | ASR transcription failure | `SherpaWhisperAsrEngine` | `YES` (`SpeechPipeline.startAsrWorker`) | `YES` (`SpeechPipelineTest`) | `YES` (Native Whisper INT8 crash) |
| **D** | Packet encoding violation | `PacketEncoder` | `YES` (`SpeechMessageTransportAdapter`) | `YES` (`PacketEncoderTest`) | `NO` (Pure algorithmic logic) |
| **E** | TCP socket write failure | `SocketMessageTransport` | `YES` (`sendMutex` / `catch`) | `YES` (`SocketMessageTransportTest`) | `YES` (Physical RF link disconnect) |
| **F** | TCP connection drop during receive | `SocketMessageTransport` | `YES` (`startReceiveLoop` EOF) | `YES` (`SocketMessageTransportTest`) | `YES` (Physical Wi-Fi link drop) |
| **G** | CRC-32 / Magic byte mismatch | `PacketDecoder` | `YES` (`SpeechMessageTransportAdapter`) | `YES` (`PacketDecoderTest`) | `NO` (Pure algorithmic logic) |
| **H** | Unsupported TTS language (`"hi"`) | `SherpaOfflineTtsEngine` | `YES` (`SpeechMessagePlaybackCoordinator`) | `YES` (`TransceiverCoordinatorTest`) | `NO` (Pure domain validation) |
| **I** | TTS synthesis engine error | `SherpaOfflineTtsEngine` | `YES` (`SpeechMessagePlaybackCoordinator`) | `YES` (`TransceiverCoordinatorTest`) | `YES` (Native Sherpa VITS crash on device) |
| **J** | Hardware DAC / AudioTrack failure | `AudioPlaybackEngine` | `PARTIAL` (Abstraction only; `AudioTrack` not written) | `YES` (`FakeAudioPlaybackEngine` in Coordinator test) | `YES` (`AudioTrack` hardware sink implementation & test) |

---

## 8. Resource Ownership & Lifecycle Table

| Resource | Creator | Owner | Cleanup / Release Method | Evidence Classification |
| :--- | :--- | :--- | :--- | :--- |
| **`AudioRecord` Native Handle** | `AudioRecordCapture` | `AudioRecordCapture` | `audioRecord?.release()` | `[VERIFIED BY CODE]` / `[REQUIRES DEVICE VALIDATION]` |
| **Silero VAD ONNX Session** | `SileroVadEngine.fromAsset` | `SpeechPipeline` | `vadDetector?.close()` | `[VERIFIED BY CODE]` / `[NOT DEVICE VALIDATED]` |
| **Sherpa Whisper Recognizer** | `SherpaWhisperAsrEngine` | `SpeechPipeline` | `asrEngine?.close()` | `[VERIFIED BY CODE]` / `[NOT DEVICE VALIDATED]` |
| **TCP Socket & Streams** | `SocketMessageTransport` | `TcpConnectionManager` | `socketTransport.close()` | `[VERIFIED BY JVM TEST]` (Local sockets) |
| **Server Socket** | `TcpServerTransport` | `TcpConnectionManager` | `serverTransport.close()` | `[VERIFIED BY JVM TEST]` (Local sockets) |
| **Sherpa OfflineTts Engine** | Factory / DI | `SpeechMessagePlaybackCoordinator` | `ttsEngine.release()` | `[VERIFIED BY CODE]` / `[NOT DEVICE VALIDATED]` |
| **Audio Playback Engine (Fake)** | Factory / DI | `SpeechMessagePlaybackCoordinator` | `playbackEngine.release()` | `[VERIFIED BY JVM TEST]` |
| **Android `AudioTrack` Handle** | Not created | Not created | Deferred | `[NOT IMPLEMENTED]` |
| **Coroutine Scopes & Channels** | Pipeline / Transport | Subsystems | `scope.cancel()`, `channel.close()` | `[VERIFIED BY JVM TEST]` |

---

## 9. Data Integrity Audit

1. **Text Serialization:** Lossless UTF-8 binary encoding. Verified with ASCII, Devanagari (`"सामरिक संदेश प्राप्त हुआ"`), and empty strings `[VERIFIED BY JVM TEST]`.
2. **Language Tag Mapping:** Canonical language IDs (`"en"`, `"hi"`) map to 1-byte constants. Extended BCP-47 tags (`"en-US"`, `"en_GB"`, `"hi-IN"`, `"fr"`, etc.) map with `0xFE` and preserve strings verbatim `[VERIFIED BY JVM TEST]`.
3. **Confidence Quantization:** Quantized to 1 byte (`0` = $0.0f$, $1..100$ = percentage, absent = $null$). Precision delta $\le 0.01$ `[VERIFIED BY JVM TEST]`.
4. **Timestamp Preservation:** Bit-exact 64-bit Big-Endian epoch millisecond integers `[VERIFIED BY JVM TEST]`.

---

## 10. Failure Scenario Matrix

| Scenario | Software Boundary Status | Physical Hardware / Network Status | System Handling Behavior |
| :--- | :--- | :--- | :--- |
| **1. Phone A starts without network** | `JVM-VERIFIED` | `NOT DEVICE VALIDATED` | Local ASR operates; outbound transport send throws and reports `M2TransportError` without crashing. |
| **2. Phone B starts without Phone A** | `JVM-VERIFIED` | `NOT DEVICE VALIDATED` | Server mode binds and listens; client mode transitions to `FAILED` with descriptive error. |
| **3. Phone A connects after B is listening** | `JVM-VERIFIED` (Local sockets) | `NOT DEVICE VALIDATED` | TCP handshake succeeds, active transport bound, full bidirectional communication active over local TCP. |
| **4. Connection drops during speech** | `JVM-VERIFIED` | `NOT DEVICE VALIDATED` | Local ASR completes transcript; outbound send fails cleanly without crashing ASR pipeline. |
| **5. Connection drops during packet send** | `JVM-VERIFIED` | `NOT DEVICE VALIDATED` | Socket write throws `TransportException`, transitions state to `FAILED`, reports error. |
| **6. Connection drops during packet receive** | `JVM-VERIFIED` | `NOT DEVICE VALIDATED` | Socket read detects EOF/exception, transitions transport to `FAILED`, closes socket. |
| **7. Phone A produces speech while disconnected**| `JVM-VERIFIED` | `NOT DEVICE VALIDATED` | Local transcript emitted; transport throws clean `TransportException` without crashing. |
| **8. Phone B receives unsupported language** | `JVM-VERIFIED` | `N/A (Domain logic)` | `UnsupportedTtsLanguageException` caught, reports `M3TtsError` to listener. |
| **9. Phone B TTS fails** | `JVM-VERIFIED` (Fake TTS) | `NOT DEVICE VALIDATED` | Synthesis error isolated from transport loop, reports `M3TtsError`. |
| **10. Playback hardware fails** | `JVM-VERIFIED` (Fake engine) | `NOT IMPLEMENTED` | Abstraction error caught, reports `M3PlaybackError`; concrete AudioTrack failure handling deferred. |
| **11. User stops transceiver during active STT** | `JVM-VERIFIED` | `NOT DEVICE VALIDATED` | Audio capture stops immediately; in-flight ASR segment finishes or drops cleanly. |
| **12. User stops transceiver during active TTS** | `JVM-VERIFIED` | `NOT DEVICE VALIDATED` | Playback stops immediately and M3 receiver is detached. |
| **13. Reconnect after connection failure** | `JVM-VERIFIED` | `NOT DEVICE VALIDATED` | `ConnectionManager.connect()` establishes new socket and rebinds adapter without stale leaks. |

---

## 11. MVP Completeness Matrix

| Feature / Subsystem | Implementation Component | Software Status | Physical Device Status | Missing / Deferred Items |
| :--- | :--- | :--- | :--- | :--- |
| **Audio Capture** | `AudioRecordCapture` | `[VERIFIED BY CODE]` | `[REQUIRES DEVICE VALIDATION]` | Hardware microphone validation |
| **Voice Activity Detection** | `SileroVadEngine` v5 | `[VERIFIED BY CODE]` | `[REQUIRES DEVICE VALIDATION]` | On-device ONNX inference profiling |
| **Whisper STT** | `SherpaWhisperAsrEngine` | `[VERIFIED BY CODE]` | `[REQUIRES DEVICE VALIDATION]` | Real-time factor (RTF) profiling on device |
| **Domain Contracts** | `SpeechMessage` | `[VERIFIED BY JVM TEST]` | `N/A` | None (contract frozen) |
| **Binary Wire Protocol** | `PacketEncoder` / `PacketDecoder` | `[VERIFIED BY JVM TEST]` | `N/A` | None (protocol frozen) |
| **TCP Transport** | `SocketMessageTransport` / `TcpServerTransport` | `[VERIFIED BY JVM TEST]` (Local) | `[REQUIRES DEVICE VALIDATION]` | Physical Wi-Fi radio streaming |
| **Connection Management** | `TcpConnectionManager` | `[VERIFIED BY JVM TEST]` (Local) | `[NOT DEVICE VALIDATED]` | Manual Wi-Fi Hotspot pairing |
| **TTS Synthesis** | `SherpaOfflineTtsEngine` (VITS) | `[VERIFIED BY CODE]` | `[REQUIRES DEVICE VALIDATION]` | Native VITS synthesis profiling on device |
| **Playback Abstraction** | `SpeechMessagePlaybackCoordinator` | `[VERIFIED BY JVM TEST]` | `N/A` | None (abstraction verified) |
| **Hardware AudioTrack** | Deferred implementation | `[NOT IMPLEMENTED]` | `[NOT IMPLEMENTED]` | Concrete Android `AudioTrack` rendering |
| **Transceiver Coordination** | `TransceiverCoordinator` | `[VERIFIED BY JVM TEST]` | `N/A` | None |
| **User Interface** | `MainActivity` | `[NOT IMPLEMENTED]` | `[NOT IMPLEMENTED]` | Production Transceiver UI (deferred to UI task) |

---

## 12. Physical Device Validation Checklist (Deferred to Hardware Availability)

Upon availability of the physical Android test devices (Lenovo TB-X6C6F):
- [ ] Verify `AudioRecordCapture` initializes on Android 12 with `RECORD_AUDIO` permission.
- [ ] Measure Silero VAD inference latency per 32ms frame on ARM64 CPU.
- [ ] Measure Sherpa-ONNX Whisper-Tiny INT8 transcription RTF on speech segments.
- [ ] Create portable Wi-Fi hotspot on Device A; connect Device B.
- [ ] Establish TCP connection between Device B (Client) and Device A (Server) over Wi-Fi.
- [ ] Transmit speech packets bidirectionally and verify 0% packet loss over RF.
- [ ] Measure Sherpa OfflineTts VITS synthesis RTF on received speech text on device.
- [ ] Implement concrete Android `AudioTrack` hardware sink and verify acoustic speaker rendering.
