# Network Connection Manager Architecture & Lifecycle Specification

> **TASK ID:** `connection-manager-02` (Audit ConnectionManager ↔ SpeechMessageTransportAdapter Binding)  
> **DATE:** 2026-09-21  
> **STATUS:** `CONNECTION BINDING AUDIT COMPLETE — DEVICE NETWORK VALIDATION PENDING`  
> **EVIDENTIARY CLASSIFICATION:**  
> - `[DOCUMENTED FACT]`: Codebase contracts, types, and JVM unit test verifications.  
> - `[ENGINEERING DECISION]`: Architectural policies selected for the iTantra transceiver MVP.  
> - `[NOT MEASURED]`: Physical device Wi-Fi radio / hotspot throughput, latency, and connection reachability.  

---

## 1. Responsibility

[`ConnectionManager`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/connection/ConnectionManager.kt) is the abstraction governing the physical/virtual network session lifecycle for the iTantra communication subsystem.

```
┌─────────────────────────────────────────────────────────────┐
│                    ConnectionManager                        │
│  - Governs connection session state machine                 │
│  - Connects client or binds server transport                │
│  - Exposes active MessageTransport when CONNECTED           │
│  - Manages graceful teardown & transport cleanup            │
│  - Dispatches connection state & error events to listener   │
└──────────────┬───────────────────────────────┬──────────────┘
               │                               │
               ▼                               ▼
┌─────────────────────────────┐ ┌─────────────────────────────┐
│    SocketMessageTransport   │ │     TcpServerTransport      │
│     (Client TCP Socket)     │ │     (Server TCP Socket)     │
└─────────────────────────────┘ └─────────────────────────────┘
```

### What ConnectionManager OWNS:
1. **Network Session Lifecycle:** Manages states (`DISCONNECTED`, `CONNECTING`, `CONNECTED`, `DISCONNECTING`, `FAILED`).
2. **Role Execution:** Instantiates and establishes client connections (`SocketMessageTransport.connect()`) or starts server listeners (`TcpServerTransport.start()`).
3. **Transport Lifecycle & Exposure:** Exposes `activeTransport: MessageTransport?` and safely closes the transport on disconnect/release.
4. **Observer Notification:** Dispatches `onStateChanged`, `onConnected`, `onDisconnected`, and `onError` to registered [`ConnectionListener`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/connection/ConnectionListener.kt)s.

---

## 2. What ConnectionManager Does NOT Own

1. **Peer Discovery:** Does NOT scan local subnets, broadcast UDP beacons, or perform mDNS/Bonjour discovery.
2. **Wi-Fi / Hotspot Provisioning:** Does NOT configure Android Wi-Fi hotspots, Wi-Fi Direct (P2P), or SSID associations.
3. **Speech / Audio Processing:** Does NOT touch STT, VAD, TTS, or AudioTrack.
4. **Packet Codecs:** Does NOT encode or decode binary transceiver packets or CRC-32 checksums.
5. **UI / Android Activity:** Does NOT reference Android `Activity`, `Context`, or UI view models.

---

## 3. Connection State Machine

```
              ┌────────────────────────────────────────┐
              │              DISCONNECTED              │◄───────────────┐
              └───────────────────┬────────────────────┘                │
                                  │                                     │
               ┌──────────────────┴──────────────────┐                  │
               │ connect()                           │ disconnect()     │
               ▼                                     ▼                  │
   ┌───────────────────────┐             ┌───────────────────────┐      │
   │       CONNECTING      │             │     DISCONNECTING     │──────┘
   └───────────┬───────────┘             └───────────────────────┘
               │                                     ▲
        ┌──────┴──────┐                              │
        │ success     │ failure                      │ disconnect()
        ▼             ▼                              │
   ┌─────────┐   ┌─────────┐                         │
   │CONNECTED│   │  FAILED │                         │
   └────┬────┘   └────┬────┘                         │
        │             │ connect() (retry)            │
        │             └──────────────► (restart)     │
        │                                            │
        └────────────────────────────────────────────┘
```

### Legal Transitions:
- `DISCONNECTED` $\to$ `CONNECTING`
- `CONNECTING` $\to$ `CONNECTED` (Socket connected or server bound)
- `CONNECTING` $\to$ `FAILED` (Connection refused, socket timeout, unreachable host)
- `CONNECTING` $\to$ `DISCONNECTING` / `DISCONNECTED` (Aborted during connection attempt)
- `CONNECTED` $\to$ `DISCONNECTING` (Graceful shutdown initiated)
- `CONNECTED` $\to$ `FAILED` (I/O failure or link loss)
- `DISCONNECTING` $\to$ `DISCONNECTED` (Teardown complete)
- `FAILED` $\to$ `CONNECTING` (Re-attempt connection)
- `FAILED` $\to$ `DISCONNECTED` (Reset connection manager)

---

## 4. Active Transport Binding to SpeechMessageTransportAdapter

[`SpeechMessageTransportAdapter`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/adapter/SpeechMessageTransportAdapter.kt) is dynamically bound to the active transport using the `bindTo()` extension:

```
ConnectionManager.bindTo(adapter)
       │
       ├── onConnected(transport)    ──> adapter.bindTransport(transport)
       └── onDisconnected()          ──> adapter.unbindTransport()
```

### Invariant:
$$\text{ONE ACTIVE CONNECTION} \longrightarrow \text{ONE ACTIVE MESSAGE TRANSPORT} \longrightarrow \text{ONE ACTIVE ADAPTER BINDING}$$

### Interaction Rules:
1. **Binding (`adapter.bindTransport(transport)`):**
   - If an older transport was previously bound, its receiver callback is cleared (`oldTransport.setReceiver(null)`).
   - The adapter registers its incoming frame deframing listener on `newTransport`.
   - `adapter.activeTransport` reflects the new active instance.
2. **Unbinding (`adapter.unbindTransport()`):**
   - Clears receiver from the active transport and sets `activeTransport = null`.
   - Any attempt to send an outbound speech message while unbound throws a clean `TransportException("Cannot send speech message: no active MessageTransport bound...")`.
3. **State Preservation:**
   - The UInt32 monotonic sequence counter (`currentSequenceNumber`) is preserved across unbind/rebind cycles.
   - Downstream M3 receiver callbacks (`SpeechMessageReceiver`) and protocol error listeners remain permanently intact across rebinds.

---

## 5. Reconnection Semantics

When a connection is lost or reset (`CONNECTED` $\to$ `DISCONNECTED` $\to$ `CONNECTING` $\to$ `CONNECTED`):
1. **Stale Transport Invalidation:** Disconnecting clears `activeTransport` from both `ConnectionManager` and `SpeechMessageTransportAdapter`. The old socket is closed and its receiver callback is set to `null`.
2. **Old Frame Quarantine:** Frames delivered on an old closed transport cannot reach M3 because the receiver on the old transport is cleared.
3. **Seamless Transport Replacement:** When `connect()` establishes a new socket, `adapter.bindTransport(newTransport)` attaches the new transport.
4. **No Duplicate Sinks / Receivers:** Exactly one receiver callback is active on the current transport.

---

## 6. Server Mode & Client Acceptance

Configured via [`ConnectionConfig.Server`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/connection/ConnectionConfig.kt):
- **Server Transport:** [`TcpServerTransport`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/transport/TcpServerTransport.kt) itself implements [`MessageTransport`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/transport/MessageTransport.kt).
- **Client Representation:** When a client connects, the background accept loop creates an internal [`SocketMessageTransport`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/transport/SocketMessageTransport.kt) instance and registers the adapter's frame receiver on it.
- **Outbound Sending:** `TcpServerTransport.send()` routes outbound payloads to the active client socket. If no client is connected, it throws `TransportException("Cannot send payload: No active client is connected to server")`.
- **Single-Client Policy (MVP):** If a second peer attempts to connect while a client is already active in `CONNECTED` state, the server accept loop immediately closes the redundant incoming socket to prevent socket leaks and avoid disrupting the active session.

---

## 7. Transport Ownership Model

[`TcpConnectionManager`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/connection/TcpConnectionManager.kt) owns the active [`MessageTransport`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/communication/transport/MessageTransport.kt) instance by default (`ownsTransport = true`):
- When `connect()` succeeds, the connection manager retains ownership of the active transport.
- When `disconnect()` or `release()` is invoked, the connection manager closes the transport and clears `_activeTransport = null`.
- Calling `disconnect()` or `release()` multiple times is idempotent, thread-safe, and guaranteed not to double-close or leak resources.

---

## 8. Failure & Retry Policy

- **Failure Semantics:** If a connection attempt fails or an active socket encounters a fatal I/O error, state transitions to `FAILED`.
- **Automatic Retry Policy:** **No automatic retry / exponential backoff is implemented in the MVP.** Reconnection is explicit: callers must invoke `connect()` after transitioning to `FAILED` or `DISCONNECTED`. This avoids hidden background connection loops and battery drain in tactical offline environments.

---

## 9. Physical Validation Status

> **`Actual Wi-Fi/TCP device-to-device connectivity remains unvalidated until physical hardware is available.`**
