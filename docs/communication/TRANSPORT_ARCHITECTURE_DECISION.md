# M2 Physical Transport Architecture Decision Record

> **STATUS:** `ACCEPTED (SUBJECT TO PHYSICAL DEVICE VALIDATION)`  
> **TASK ID:** `transport-decision-01`  
> **DECISION TOPIC:** Physical Communication Transport for iTantra Offline Transceiver MVP  
> **DECISION DATE:** 2026-09-20  
> **EVIDENTIARY CLASSIFICATION:**  
> - **[DOCUMENTED FACT]:** Verifiable directly against official Android Developer documentation, platform APIs, RFCs, or codebase.  
> - **[ENGINEERING INFERENCE]:** Architectural analysis and engineering deductions based on system constraints.  
> - **[REQUIRES DEVICE VALIDATION]:** Hardware/link-dependent property requiring empirical profiling on physical Android devices.  
> - **[NOT MEASURED]:** Quantitative measurement unavailable without physical device testing.  

---

## 1. Decision

**Selected Physical Transport for MVP Validation:**  
**Local Wi-Fi Network + TCP Sockets (`SocketMessageTransport`)**

We select a client-server TCP socket connection operating over a shared offline local Wi-Fi network (such as a portable Android Wi-Fi hotspot or dedicated offline travel router) as the physical transport mechanism for MVP implementation and empirical device validation.

*Framing Note:* This selection is made strictly based on documented platform API characteristics and minimal implementation complexity. It is **NOT** claimed to be universally superior or empirically proven in real-world tactical performance prior to physical device testing (**[ENGINEERING INFERENCE]**).

---

## 2. Context & Problem Statement

Module 2 (M2) of the iTantra offline speech transceiver is responsible for transmitting recognized speech transcription packets between two Android devices in field environments without internet or cellular connectivity (**[DOCUMENTED FACT]**).

The M1 speech pipeline produces [`SpeechMessage`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/domain/SpeechMessage.kt) domain objects, which are mapped to [`TransceiverPacket`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/protocol/TransceiverPacket.kt) instances and encoded into deterministic binary byte arrays via [`PacketEncoder`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/protocol/PacketEncoder.kt) (**[DOCUMENTED FACT]**).

We require a physical link layer that carries these arbitrary opaque byte arrays across the [`MessageTransport`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/transport/MessageTransport.kt) boundary with high demonstration reliability and minimal setup friction on Android 12+ (**[ENGINEERING INFERENCE]**).

---

## 3. System Requirements

1. **Offline Operation:** Zero dependency on internet access, cloud infrastructure, or external DNS servers (**[DOCUMENTED FACT]**).
2. **Android-to-Android Compatibility:** Must operate reliably between modern Android devices (targeting Android 12+, API 31+) (**[DOCUMENTED FACT]**).
3. **Binary Payload Support:** Must transport opaque byte arrays ranging from 24 bytes to ~70 KB without corruption or character encoding translation (**[DOCUMENTED FACT]**).
4. **Bidirectional Communication:** Once connected, both nodes must be capable of transmitting and receiving speech messages (**[DOCUMENTED FACT]**).
5. **Demonstration Predictability:** Setup and connection establishment must minimize fragile discovery negotiation during hackathon and live evaluation environments (**[ENGINEERING INFERENCE]**).
6. **Minimal Dependency Footprint:** Must rely on standard platform APIs and open-source AOSP components without requiring proprietary Google Play Services (**[DOCUMENTED FACT]**).

---

## 4. Summary of Considered Candidates

As researched and documented in [`docs/communication/PHYSICAL_TRANSPORT_SELECTION.md`](file:///c:/Users/saich/Downloads/SIH/PS26173/docs/communication/PHYSICAL_TRANSPORT_SELECTION.md):

1. **Local Wi-Fi + TCP Sockets (SELECTED FOR MVP):** Standard `java.net.Socket` and `ServerSocket` over shared local Wi-Fi/hotspot.
2. **Bluetooth Classic (RFCOMM / SPP) (SECONDARY CANDIDATE):** `android.bluetooth.BluetoothSocket` using pre-paired devices and RFCOMM stream channels.
3. **Local Wi-Fi + UDP Datagrams:** `java.net.DatagramSocket` without connection handshake, but lacking transport-level delivery/ordering guarantees.
4. **Wi-Fi Direct (P2P) (DEFERRED):** `WifiP2pManager` autonomous group formation; deferred due to multi-step P2P group negotiation overhead and dangerous runtime permission requirements.
5. **Bluetooth Low Energy (BLE GATT) (DEFERRED):** `android.bluetooth.le`; deferred due to MTU exchange negotiation complexity and multi-step asynchronous callback state machines.
6. **Google Nearby Connections (REJECTED):** Disqualified due to proprietary `play-services-nearby` dependency.

---

## 5. Evidence Supporting Selection of Local Wi-Fi + TCP

1. **Standard Platform API:** Built into core Java standard library (`java.net.Socket`, `java.net.ServerSocket`) available on all Android versions without external dependencies (**[DOCUMENTED FACT]**).
2. **Binary Stream Preservation:** Carries raw `ByteArray` streams natively via standard input/output streams (**[DOCUMENTED FACT]**).
3. **In-Order, Reliable Stream Delivery:** Once connected, the operating system kernel TCP stack handles packet sequencing, acknowledgment, flow control, and link-level retransmissions (**[DOCUMENTED FACT]**).
4. **Standard Normal Permissions:** Socket communication requires only `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE`, which are auto-granted at installation time without presenting runtime permission dialogs to the user (**[DOCUMENTED FACT]**).
5. **Bidirectional Full-Duplex Link:** A single established socket supports concurrent sending and receiving via independent `OutputStream` and `InputStream` threads/coroutines (**[DOCUMENTED FACT]**).
6. **Minimal Architectural Surface:** Avoids complex asynchronous callback hierarchies (unlike BLE GATT or Wi-Fi Direct broadcast state machines) (**[ENGINEERING INFERENCE]**).

---

## 6. Known Limitations & Non-Claims

The following limitations are explicitly documented and acknowledged:
1. **Connection Establishment:** TCP provides a reliable byte stream *only after a connection is established*. TCP does **NOT** guarantee that a connection attempt will succeed (**[DOCUMENTED FACT]**).
2. **Link Persistence:** TCP does **NOT** guarantee that a connection will remain alive if physical radio link connectivity is interrupted (**[DOCUMENTED FACT]**).
3. **No Message Boundaries:** TCP is a continuous byte stream. It does **NOT** define application message boundaries and requires transport-level framing (**[DOCUMENTED FACT]**).
4. **No Security Boundary:** Plain TCP sockets over local Wi-Fi provide **ZERO** encryption, authentication, or protection against eavesdropping or tampering (**[DOCUMENTED FACT]**).
5. **Network Setup:** A shared local Wi-Fi network (hotspot or router) must be established before socket connections can occur (**[DOCUMENTED FACT]**).
6. **No Quantitative Claims:** Connection latency, throughput, range, and battery consumption are **NOT MEASURED** and **REQUIRE DEVICE VALIDATION**.

---

## 7. End-to-End Architectural Layering

The complete subsystem layering preserves strict separation of concerns:

```
[ MODULE 1 — SPEECH SUBSYSTEM ]
  MIC -> AudioRecord -> Silero VAD -> SpeechSegmentCollector -> Sherpa Whisper ASR
        │
        ▼
  AsrResult (text, language, confidence, inferenceTimeMs, audioDurationMs)
        │
        ▼
  SpeechMessage (text, language, timestamp = segmentStartTimestamp, confidence)
        │
        ▼
  SpeechMessageSink (Boundary Interface)
        │
        ▼
[ MODULE 2 — COMMUNICATION ADAPTER LAYER ]
  SpeechMessageTransportAdapter (implements SpeechMessageSink)
  └── Outbound: Sequence number allocation -> TransceiverPacketMapper.fromSpeechMessage()
        │
        ▼
[ MODULE 2 — LOGICAL CODEC LAYER ]
  TransceiverPacket (logical packet model)
        │
        ▼
  PacketEncoder.encode() -> Encoded TransceiverPacket (N bytes)
        │
        ▼
[ MODULE 2 — TRANSPORT ADAPTER & FRAMING LAYER ]
  SocketMessageTransport
  └── Framing: [ 4-byte UInt32 BE Length = N ] + [ N Packet Bytes ]
        │
        ▼
[ PHYSICAL LINK / OS KERNEL LAYER ]
  java.net.Socket (TCP / IP Stack over Local 802.11 Wi-Fi)
        │
        ▼ (Physical Wi-Fi Transmission)
        │
  java.net.Socket (Receiver OS TCP Buffer)
        │
        ▼
[ MODULE 2 — TRANSPORT RECEIVER & DEFRAMING LAYER ]
  SocketMessageTransport Receive Loop
  └── Deframing: Reads 4-byte length prefix N -> Reads exactly N bytes
        │
        ▼
[ MODULE 2 — LOGICAL CODEC LAYER ]
  PacketDecoder.decode(N bytes) -> TransceiverPacket (Verified CRC-32)
        │
        ▼
[ MODULE 2 — COMMUNICATION ADAPTER LAYER ]
  SpeechMessageTransportAdapter
  └── Inbound: TransceiverPacketMapper.toSpeechMessage() -> Reconstructed SpeechMessage
        │
        ▼
  SpeechMessageReceiver (M2 -> M3 Boundary Interface)
        │
        ▼
[ MODULE 3 — DOWNSTREAM TEXT-TO-SPEECH LAYER ]
  Received SpeechMessage -> Future TTS Speech Synthesis Engine / Presenter
```

### Communication Boundary Invariants:
1. **M1 Owns `AsrResult` $\to$ `SpeechMessage` Mapping:** For each completed speech segment, [`SpeechPipeline`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/pipeline/SpeechPipeline.kt) maps `AsrResult` to [`SpeechMessage`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/domain/SpeechMessage.kt) using the segment's start acquisition epoch millisecond timestamp.
2. **Local STT Independence:** Local STT functionality remains fully operational when no transport or sink is configured (`messageSink == null`). Emitted speech messages are always dispatched to local [`SpeechPipelineListener.onSpeechMessage`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/pipeline/SpeechPipelineListener.kt).
3. **Transport Failure Isolation:** A transport send exception in [`SpeechMessageSink.emit`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/pipeline/SpeechMessageSink.kt) is logged and reported via `listener.onError()` without mutating the `SpeechMessage` or crashing the speech recognition pipeline.
4. **`SpeechMessage` Contract Integrity:** The M1 [`SpeechMessage`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/domain/SpeechMessage.kt) domain contract remains pure and immutable (`text`, `language`, `timestamp`, `confidence`). No networking, sequence, or transport fields exist on `SpeechMessage`.
5. **Sequence Number Ownership:** UInt32 sequence number allocation is owned exclusively by [`SpeechMessageTransportAdapter`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/adapter/SpeechMessageTransportAdapter.kt) and attached during packetization. Sequence counters wrap from `0xFFFFFFFFL` to `0L`.
6. **Language Preservation:** Registered languages (`"en"`, `"hi"`, etc.) map to compact 1-byte IDs and decode to their canonical tags. Extended BCP-47 tags (`"en-US"`, `"en_GB"`, `"fr"`, etc.) are mapped with `0xFE` and preserved verbatim.
7. **Confidence Preservation:** `Float?` confidence scores scale through [`ConfidenceQuantizer`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/protocol/ConfidenceQuantizer.kt) (`null` remains absent, `0.0f` scales to `0`, `0.88f` scales to `88`, and dequantizes back to exact float values).
8. **M2 $\to$ M3 Boundary Ownership:** M2 owns decoding and reconstructing `SpeechMessage` from incoming wire frames. M2 delivers the verified `SpeechMessage` directly across the [`SpeechMessageReceiver`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/tts/SpeechMessageReceiver.kt) interface to M3. M3 owns speech synthesis and UI rendering.
9. **Downstream M3 Error Isolation:** Exceptions thrown by downstream M3 consumers inside `SpeechMessageReceiver.onSpeechMessageReceived` are caught and isolated by the transport adapter to prevent UI or audio errors from crashing the physical socket receive loop.
10. **Playback Policy Non-Claim:** Queueing, prioritization, interruption, and audio playback policies are **NOT DEFINED YET — TTS PLAYBACK POLICY TO BE DECIDED DURING M3 IMPLEMENTATION**.

**Critical Invariant:**  
TCP and transport framing remain strictly below the packet codec layer. [`PacketEncoder`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/protocol/PacketEncoder.kt) and [`PacketDecoder`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/protocol/PacketDecoder.kt) remain 100% transport-independent and unaware of TCP length prefixes (**[DOCUMENTED FACT]**).

---

## 8. TCP Message Framing Specification

Because TCP delivers a continuous stream of bytes without message boundaries, the `SocketMessageTransport` layer must apply explicit application-level framing.

### Framing Format: 4-Byte Big-Endian Length Prefix
Every transmission over the TCP socket consists of:
```
+------------------------------------+---------------------------------------+
|  TRANSPORT FRAME LENGTH (4 Bytes)  |  ENCODED TRANSCEIVER PACKET (N Bytes) |
+------------------------------------+---------------------------------------+
|  UInt32 (Big-Endian) = N           |  Output of PacketEncoder.encode()     |
+------------------------------------+---------------------------------------+
```

### Framing Invariants:
1. **Length Prefix Semantics:** The 4-byte integer specifies **only the exact byte length $N$ of the following encoded `TransceiverPacket`**. It does **NOT** include the 4-byte prefix itself (**[ENGINEERING INFERENCE]**).
2. **Endianness:** The 4-byte length prefix is serialized in **Big-Endian (Network Byte Order)** (**[DOCUMENTED FACT]**).
3. **Transport-Only Scope:** This length prefix is added by `SocketMessageTransport` when writing to the socket and stripped upon reading. It is **NOT** part of [`M2_PROTOCOL_SPEC.md`](file:///c:/Users/saich/Downloads/SIH/PS26173/docs/communication/M2_PROTOCOL_SPEC.md) and is **NOT** included in the CRC-32 checksum calculation (**[DOCUMENTED FACT]**).

### Concrete Example:
For a 29-byte encoded English packet (`"hello"`, confidence absent):
- Frame Length Header: `0x00, 0x00, 0x00, 0x1D` (29 in decimal)
- Payload: `29 bytes of PacketEncoder output (Magic through CRC-32)`
- Total Wire Bytes Transmitted: `33 bytes`.

---

## 9. Frame Size Safety Limit

To prevent unbounded memory allocation and buffer exhaustion from malformed headers or stream desynchronization, the transport receiver enforces a strict upper bound:

$$\text{MAX\_FRAME\_PAYLOAD\_BYTES} = \mathbf{70{,}000\text{ Bytes}}$$

### Rationale:
- In the frozen protocol, maximum speech text is $65,535$ bytes UTF-8 and maximum extended language tag is $255$ bytes UTF-8. With framing overhead, the maximum valid `TransceiverPacket` is ~65,821 bytes (**[DOCUMENTED FACT]**).
- `70,000` bytes provides a safe buffer above the protocol ceiling while ensuring the receiver never attempts to allocate megabytes/gigabytes of memory if corrupt length bytes arrive (**[ENGINEERING INFERENCE]**).
- **Validation Rules:**
  - If declared `FrameLength == 0`: Reject frame with [`TransportException`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/transport/TransportException.kt).
  - If declared `FrameLength > 70000`: Reject frame immediately before allocating payload buffer and abort/close socket.

---

## 10. Stream Semantics & Partial Read Deframing Strategy

In TCP, a single `InputStream.read()` call does **not** map 1:1 to an application frame (**[DOCUMENTED FACT]**).

The receiver deframer must handle all TCP stream chunking conditions:
1. **Partial Header:** The 4-byte length prefix may arrive split across multiple read calls (e.g. 2 bytes + 2 bytes).
2. **Partial Payload:** A payload of $N$ bytes may arrive in multiple arbitrary fragments.
3. **Multiple Frames in One Read:** A single socket read may contain multiple back-to-back frames (e.g. `[Len1][Payload1][Len2][Payload2]`).
4. **Frame Overflow:** A single socket read may contain a complete frame followed by the partial start of the next frame.

### Receiver Deframing Algorithm:
```
1. Read exactly 4 bytes from InputStream (looping until 4 bytes accumulated).
2. Decode 4-byte Big-Endian UInt32 -> N.
3. Validate: require(N in 1..70000).
4. Allocate byte array of size N.
5. Read exactly N bytes from InputStream (looping until N bytes accumulated).
6. Pass N-byte array to PacketDecoder.decode().
7. Emit decoded TransceiverPacket to listener/receiver callback.
8. Loop back to step 1.
```

---

## 11. Connection State Machine & Lifecycle

The `SocketMessageTransport` state machine defines legal connection transitions:

```
          ┌──────────────────────────────────────────────┐
          │                                              │
          ▼                                              │
      [ CLOSED ] ──(connect/listen)──> [ CONNECTING ]    │
          ▲                                 │            │
          │                        (success)│ (failure)  │
          │                                 ▼            │
          │                            [ CONNECTED ]     │
          │                                 │            │
          │                        (close)  │ (I/O error)│
          │                                 ▼            │
      [ CLOSING ] <──────────────────── [ FAILED ] ──────┘
          │                                 │
          └─────────────> [ CLOSED ] <──────┘
```

### Legal Transitions:
- `CLOSED` $\to$ `CONNECTING`
- `CONNECTING` $\to$ `CONNECTED` (Handshake / accept succeeded)
- `CONNECTING` $\to$ `FAILED` (Connection refused, timeout, or unreachable)
- `CONNECTING` $\to$ `CLOSING` (Close called while connection pending)
- `CONNECTED` $\to$ `CLOSING` (Graceful shutdown initiated by application)
- `CONNECTED` $\to$ `FAILED` (Socket I/O error, EOF, framing violation, or link loss)
- `CLOSING` $\to$ `CLOSED` (Socket closed and coroutine scopes cancelled)
- `FAILED` $\to$ `CLOSED` (Cleanup completed / close invoked)
- `FAILED` $\to$ `CLOSING` (Explicit close called on failed transport)

---

## 12. Client / Server Model, Socket Ownership & Policies

- **Role Assignment:** For MVP demonstrations between two nodes:
  - **Node A (Transceiver Host):** Creates/binds a `ServerSocket` on default port `42173` using [`TcpServerTransport`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/transport/TcpServerTransport.kt).
  - **Node B (Transceiver Peer):** Connects as client using [`SocketMessageTransport`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/transport/SocketMessageTransport.kt).
- **Single-Client Policy (MVP):**
  - `TcpServerTransport` supports exactly one active client connection at a time (**[DOCUMENTED FACT]**).
  - If a second peer attempts to connect while a client is already active in `CONNECTED` state, the server accept loop immediately closes the redundant incoming socket to prevent socket leaks and avoids replacing or interrupting the active client (**[DOCUMENTED FACT]**).
- **Socket Ownership Model:**
  - `SocketMessageTransport` owns the active `Socket`, its `InputStream`, `OutputStream`, its receive loop coroutine, and its send mutex synchronization (**[DOCUMENTED FACT]**).
  - `TcpServerTransport` owns the listening `ServerSocket` and its accept loop coroutine. When `TcpServerTransport.close()` is invoked, it gracefully closes the wrapped active client transport and the listening `ServerSocket` (**[DOCUMENTED FACT]**).
- **Receiver Registration Policy:**
  - Exactly one active receiver callback is permitted at a time. Registering a duplicate non-null receiver without first unregistering (passing `null`) throws an `IllegalStateException` to prevent silent overwrites or hidden integration bugs (**[DOCUMENTED FACT]**).
- **Receiver Callback Failure Policy:**
  - Application-level exceptions thrown inside the receiver callback are caught and isolated from the transport lifecycle. A failure in an application-level message handler will **not** terminate or crash the underlying socket receive loop or transport connection (**[DOCUMENTED FACT]**).
- **Close Semantics & Idempotency:**
  - Calling `close()` multiple times or concurrently on either `SocketMessageTransport` or `TcpServerTransport` is idempotent, thread-safe, and cleanly leads to `CLOSED` state (**[DOCUMENTED FACT]**).
- **Discovery Scope:** Automated peer IP discovery (e.g. via UDP broadcast beacon) is deferred to future work; initial MVP validation uses explicit host/port configuration (**[ENGINEERING INFERENCE]**).

---

## 13. Concurrency & Threading Constraints

To ensure non-blocking operation on Android:
1. **No UI Thread Blocking:** `Socket.connect()`, `ServerSocket.accept()`, `InputStream.read()`, and `OutputStream.write()` must **never** run on the Android Main/UI thread (**[DOCUMENTED FACT]**).
2. **Dedicated Receive Loop:** Exactly one logical coroutine or worker thread runs the blocking/asynchronous read loop per active connection (**[ENGINEERING INFERENCE]**).
3. **Serialized Send Channel:** Concurrent calls to `send()` must be synchronized or channeled to prevent interleaving of length headers and payload bytes on the `OutputStream` (**[ENGINEERING INFERENCE]**).
4. **Clean Cancellation:** Invoking `close()` must immediately close the underlying socket to interrupt blocking read calls and cleanly terminate background coroutines (**[DOCUMENTED FACT]**).

---

## 14. Error Taxonomy & Boundaries

Transport-layer errors must remain strictly segregated from protocol-layer errors:

| Error Category | Exception Class | Causes & Triggers | Handling Strategy |
| :--- | :--- | :--- | :--- |
| **Transport Layer** | [`TransportException`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/transport/TransportException.kt) | Connection refused, socket timeout, link loss, EOF during read, frame length $> 70{,}000$, zero-length frame | Transition state to `FAILED`, close socket, notify transport listener |
| **Protocol Layer** | [`ProtocolException`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/protocol/ProtocolException.kt) | Magic mismatch (`!= 0x4954`), unsupported version, malformed UTF-8 text, CRC-32 mismatch | Discard corrupted frame, log telemetry warning, keep transport connection alive |

---

## 15. Security & Trust Model

- **No Cryptographic Guarantees:** The MVP TCP transport is **unauthenticated and unencrypted** (**[DOCUMENTED FACT]**).
- **Threat Model Scope:** TCP + CRC-32 protects solely against **accidental transmission errors** (bit flips, buffer truncation, RF packet noise). It provides **ZERO** protection against malicious eavesdropping, tampering, man-in-the-middle, or replay attacks (**[DOCUMENTED FACT]**).
- **Future Hardening:** Application-layer encryption (e.g. ChaCha20-Poly1305 / Noise Protocol) or link-layer WPA2/WPA3 hotspot security belong to future production security specifications (**[ENGINEERING INFERENCE]**).

---

## 16. Physical Device Validation Plan

Once the physical Android test hardware (Lenovo TB-X6C6F, Android 12) is available, the following test battery will be executed and recorded:

1. **[REQUIRES DEVICE VALIDATION]** Create portable Wi-Fi hotspot on Device A; associate Device B.
2. **[REQUIRES DEVICE VALIDATION]** Verify IP address reachability between Device A and Device B via ping.
3. **[REQUIRES DEVICE VALIDATION]** Start `ServerSocket` on Device A; connect `Socket` from Device B.
4. **[REQUIRES DEVICE VALIDATION]** Measure connection establishment latency in milliseconds.
5. **[REQUIRES DEVICE VALIDATION]** Transmit single 29-byte encoded `TransceiverPacket` from B to A; verify bit-exact reception and equality.
6. **[REQUIRES DEVICE VALIDATION]** Transmit 100 consecutive speech packets sequentially; verify 100% delivery without packet loss.
7. **[REQUIRES DEVICE VALIDATION]** Transmit packets bidirectionally (simultaneous send from A $\to$ B and B $\to$ A).
8. **[REQUIRES DEVICE VALIDATION]** Measure end-to-end packet transmission latency across physical 802.11 link.
9. **[REQUIRES DEVICE VALIDATION]** Transmit maximum-size speech packet (~65 KB); verify correct chunked deframing.
10. **[REQUIRES DEVICE VALIDATION]** Transmit 10 back-to-back frames in a single burst; verify receiver deframer separation.
11. **[REQUIRES DEVICE VALIDATION]** Inject simulated frame length $> 70{,}000$; verify `TransportException` rejection.
12. **[REQUIRES DEVICE VALIDATION]** Inject single-bit corrupted payload byte; verify `ProtocolException` CRC rejection while connection remains open.
13. **[REQUIRES DEVICE VALIDATION]** Toggle airplane mode during active connection; verify socket detects link drop and transitions to `FAILED`.
14. **[REQUIRES DEVICE VALIDATION]** Test reconnect workflow after link recovery.
15. **[REQUIRES DEVICE VALIDATION]** Verify socket behavior during device screen-off and app backgrounding on Android 12.

---

## 17. Revisit Conditions

This architecture decision will be formally revisited if:
1. Physical device testing reveals that Android hotspot creation or client association is too fragile or restrictive across target devices.
2. Real-world RF congestion causes unacceptable Wi-Fi connection drops in target operating environments.
3. System requirements mandate direct device-to-device communication without creating a local Wi-Fi network (triggering activation of the Bluetooth Classic RFCOMM fallback).
