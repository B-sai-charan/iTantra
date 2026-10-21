# Physical TCP Validation 01

## Objective
Perform the first controlled physical-device validation of the M2 communication layer using two physical Android devices over a local Wi-Fi network, strictly adhering to the production transport architecture:
`SpeechMessage` → `SpeechMessageTransportAdapter` → `PacketEncoder` → `TCP Transport` (`SocketMessageTransport` / `TcpServerTransport`) → `Network` → `TCP Transport` → `PacketDecoder` → `SpeechMessageTransportAdapter` → `SpeechMessage`.

## Device A
- **Role**: TCP Client Node
- **ADB Serial**: `HNP097HQ`
- **Manufacturer**: LENOVO
- **Model**: Lenovo TB-X6C6F
- **Android Version**: 12
- **API Level**: 31
- **ABI**: arm64-v8a
- **Assigned Local IP**: `172.20.161.69` (Subnet: `172.20.0.0/16`)

## Device B
- **Role**: TCP Server Node
- **ADB Serial**: `10BE830QG8000MX`
- **Manufacturer**: vivo
- **Model**: I2304
- **Android Version**: 16 (Preview / SDK 36)
- **API Level**: 36
- **ABI**: arm64-v8a
- **Assigned Local IP**: `172.20.105.179` (Subnet: `172.20.0.0/16`)

## Network Topology
```
Device A (Lenovo TB-X6C6F)
  [172.20.161.69]
       │
       │ Wi-Fi (Subnet: 172.20.0.0/16)
       ▼
Device B (vivo I2304)
  [172.20.105.179:42173]
```
- **ICMP Reachability**: Verified via 3-packet ICMP ping from Device A to Device B (`172.20.105.179`): 3 packets transmitted, 3 received, 0% packet loss, RTT avg = 276.6 ms.
- **Port Used**: `42173` (Default non-privileged M2 TCP port).

## Build / Install
- **Build Command**: `./gradlew assembleDebug`
- **APK Filename**: `app-debug.apk`
- **APK Size**: 180,221,258 bytes (~171.87 MB)
- **Installation on Device A (`HNP097HQ`)**: `Success` (Streamed install)
- **Installation on Device B (`10BE830QG8000MX`)**: `Success` (Streamed install)

## Connection Test
- **Server Launch on Device B**:
  - Bound to `0.0.0.0:42173`
  - Observed Server State Transitions: `DISCONNECTED` → `CONNECTING` → `CONNECTED` (Server listening)
  - Device B Logcat Evidence:
    ```
    09-22 22:12:42.377 I PHYSICAL_TCP_TEST: [SERVER] STARTING M2 TCP SERVER VALIDATION ON PORT 42173
    09-22 22:12:42.384 I PHYSICAL_TCP_TEST: [SERVER-STATE] Connection state changed: CONNECTING
    09-22 22:12:42.386 I PHYSICAL_TCP_TEST: [SERVER-STATE] Connection state changed: CONNECTED
    09-22 22:12:42.386 I PHYSICAL_TCP_TEST: [SERVER-STATE] Server listening / transport active: org.itantra.core.communication.transport.TcpServerTransport@c20b059
    09-22 22:12:42.386 I PHYSICAL_TCP_TEST: [SERVER-READY] TCP Server is listening on port 42173. Ready for client connection.
    ```
- **Client Connection from Device A to Device B (`172.20.105.179:42173`)**:
  - Observed Client State Transitions: `DISCONNECTED` → `CONNECTING` → `CONNECTED`
  - Device A Logcat Evidence:
    ```
    09-22 22:13:07.322 I PHYSICAL_TCP_TEST: [CLIENT] STARTING M2 TCP CLIENT VALIDATION SUITE
    09-22 22:13:07.323 I PHYSICAL_TCP_TEST: [CLIENT] Target Server: 172.20.105.179:42173
    09-22 22:13:07.383 I PHYSICAL_TCP_TEST: [CLIENT-STATE] Connection state transition: CONNECTING
    09-22 22:13:08.435 I PHYSICAL_TCP_TEST: [CLIENT-STATE] Connection state transition: CONNECTED
    09-22 22:13:08.435 I PHYSICAL_TCP_TEST: [CLIENT-STATE] Successfully connected to server: org.itantra.core.communication.transport.SocketMessageTransport@5470478
    09-22 22:13:08.436 I PHYSICAL_TCP_TEST: [CLIENT-PASS] Connection established. State: CONNECTED
    ```

## Test 1 — English SpeechMessage
- **Device A Outbound**:
  ```
  09-22 22:13:08.648 I PHYSICAL_TCP_TEST: [CLIENT-OUTBOUND] Sent Test 1 SpeechMessage: text='Hello from iTantra Device A.', lang='en', ts=1790095388639, seq=0
  ```
- **Device B Inbound Callback**:
  ```
  09-22 22:13:10.281 I PHYSICAL_TCP_TEST: [SERVER-INBOUND] Received SpeechMessage #1: text='Hello from iTantra Device A.', lang='en', ts=1790095388639, conf=null, rxTs=1790095390281
  ```
- **Verification**:
  - Callback Count: Exactly 1
  - Text Preservation: Exact (`"Hello from iTantra Device A."`)
  - Language Tag: Exact (`"en"`)
  - Acquisition Timestamp Preserved: `1790095388639`
  - Confidence Preserved: `null`
  - Duplicates: `0`

## Test 2 — Unicode SpeechMessage
- **Device A Outbound**:
  ```
  09-22 22:13:08.953 I PHYSICAL_TCP_TEST: [CLIENT-OUTBOUND] Sent Test 2 Unicode SpeechMessage: text='नमस्ते from iTantra.', lang='hi', ts=1790095388950, seq=1
  ```
- **Device B Inbound Callback**:
  ```
  09-22 22:13:10.282 I PHYSICAL_TCP_TEST: [SERVER-INBOUND] Received SpeechMessage #2: text='नमस्ते from iTantra.', lang='hi', ts=1790095388950, conf=null, rxTs=1790095390282
  ```
- **Verification**:
  - Callback Count: Exactly 1
  - Exact Unicode Preserved: `"नमस्ते from iTantra."` (zero replacement characters, zero truncation)
  - Language Tag: Exact (`"hi"`)
  - Duplicates: `0`

## Test 3 — Ordered Messages
- **Device A Outbound Sequential Dispatch**:
  ```
  09-22 22:13:09.260 I PHYSICAL_TCP_TEST: [CLIENT-OUTBOUND] Sent Test 3 (Message one): seq=2, ts=1790095389255
  09-22 22:13:09.364 I PHYSICAL_TCP_TEST: [CLIENT-OUTBOUND] Sent Test 3 (Message two): seq=3, ts=1790095389362
  09-22 22:13:09.468 I PHYSICAL_TCP_TEST: [CLIENT-OUTBOUND] Sent Test 3 (Message three): seq=4, ts=1790095389466
  ```
- **Device B Inbound Callbacks**:
  ```
  09-22 22:13:10.284 I PHYSICAL_TCP_TEST: [SERVER-INBOUND] Received SpeechMessage #3: text='Message one', lang='en', ts=1790095389255, conf=0.9, rxTs=1790095390283
  09-22 22:13:10.383 I PHYSICAL_TCP_TEST: [SERVER-INBOUND] Received SpeechMessage #4: text='Message two', lang='en', ts=1790095389362, conf=0.92, rxTs=1790095390383
  09-22 22:13:10.414 I PHYSICAL_TCP_TEST: [SERVER-INBOUND] Received SpeechMessage #5: text='Message three', lang='en', ts=1790095389466, conf=0.94, rxTs=1790095390414
  ```
- **Verification**:
  - Sent Count: 3
  - Received Count: 3
  - Observed Order: 1. `Message one`, 2. `Message two`, 3. `Message three` (Strict FIFO preserved)
  - Duplicates: `0`
  - Missing Messages: `0`

## Test 4 — Explicit Reconnect
- **Pre-Disconnect Message**:
  - Device A Outbound:
    ```
    09-22 22:13:10.074 I PHYSICAL_TCP_TEST: [CLIENT-OUTBOUND] Sent Test 4 pre-disconnect message: text='Before disconnect', seq=5
    ```
  - Device B Inbound:
    ```
    09-22 22:13:11.527 I PHYSICAL_TCP_TEST: [SERVER-INBOUND] Received SpeechMessage #6: text='Before disconnect', lang='en', ts=1790095390072, conf=null, rxTs=1790095391526
    ```
- **Disconnect State Transition (Device A)**:
  ```
  09-22 22:13:10.377 I PHYSICAL_TCP_TEST: [CLIENT-STATE] Connection state transition: DISCONNECTING
  09-22 22:13:10.382 I PHYSICAL_TCP_TEST: [CLIENT-STATE] Connection state transition: DISCONNECTED
  09-22 22:13:10.382 I PHYSICAL_TCP_TEST: [CLIENT-STATE] Disconnected from server
  ```
- **Explicit Reconnect State Transition (Device A)**:
  ```
  09-22 22:13:10.885 I PHYSICAL_TCP_TEST: [CLIENT-STATE] Connection state transition: CONNECTING
  09-22 22:13:11.854 I PHYSICAL_TCP_TEST: [CLIENT-STATE] Connection state transition: CONNECTED
  09-22 22:13:11.855 I PHYSICAL_TCP_TEST: [CLIENT-PASS] Reconnect successful. State: CONNECTED
  ```
- **Post-Reconnect Message**:
  - Device A Outbound:
    ```
    09-22 22:13:12.059 I PHYSICAL_TCP_TEST: [CLIENT-OUTBOUND] Sent Test 4 post-reconnect message: text='After reconnect', seq=6
    ```
  - Device B Inbound:
    ```
    09-22 22:13:13.034 I PHYSICAL_TCP_TEST: [SERVER-INBOUND] Received SpeechMessage #7: text='After reconnect', lang='en', ts=1790095392057, conf=null, rxTs=1790095393034
    ```
- **Verification**:
  - Reconnect Succeeded: Yes
  - New message delivered post-reconnect: Exactly 1 copy
  - Stale duplicates: `0`

## Measurements & Evidence Classification

### Physical Device Evidence
- **Physical Wi-Fi Reachability**: Device A (`172.20.161.69`) $\leftrightarrow$ Device B (`172.20.105.179`) with 0% packet loss.
- **Server Listening**: Real `TcpServerTransport` bound to `0.0.0.0:42173` on physical Device B.
- **Client Connection**: Real `SocketMessageTransport` connected across Wi-Fi from Device A to Device B.
- **Physical Message Delivery**: 7 real physical `SpeechMessage` transmissions from Device A and exactly 7 observed `SpeechMessageReceiver` callbacks on Device B.
- **Physical Unicode Delivery**: Exact UTF-8 multi-byte Hindi glyphs `"नमस्ते from iTantra."` preserved across physical socket.
- **Physical Ordered Delivery**: 3 sequential messages delivered in exact FIFO order.
- **Physical Reconnect**: Clean teardown and reconnect with post-reconnect message delivery.

### Code & JVM Evidence
- **Packet Codecs & CRC**: `PacketEncoder` / `PacketDecoder` with CRC-32 integrity validation (343 JVM tests passing).
- **Framing Invariants**: 4-byte Big-Endian length-prefixed payload framing across `SocketMessageTransport`.
- **Protocol Mappers**: `TransceiverPacketMapper` bidirectional mapping between `SpeechMessage` and `TransceiverPacket`.
- **Sequence Generator**: UInt32 sequence number counter wrapping.

### Not Measured
- Application-level clock-synchronized latency (LATENCY: NOT MEASURED; non-NTP synced device wall clocks).

## Failures & Errors
- Zero exceptions, zero ANRs, zero crashes.
- Logcat checks for `FATAL EXCEPTION`, `AndroidRuntime`, `TransportException`, `ProtocolException` returned 0 runtime errors.

## Final Status
PHYSICAL TCP VALIDATION PASS
