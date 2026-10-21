# M1 Reverse Topology Physical End-to-End Validation Report

**Execution Date:** 2026-10-02  
**Target Scenario:** Physical Reverse Topology (Tablet = Receiver, Phone = Transmitter)  
**Overall Verdict:** `PASS`

---

## 1. Device Configuration & Network Discovery

| Parameter | Tablet Device | Phone Device |
| :--- | :--- | :--- |
| **ADB Serial** | `HNP097HQ` | `10BE830QG8000MX` |
| **Device Model** | Lenovo Tab M10 (Android 10) | Redmi 12 5G (Android 14) |
| **Interface** | `wlan0` | `wlan0` |
| **Detected IPv4 Address** | `172.20.38.186` | `172.20.105.179` |
| **Assigned Role** | **`📡 Receiver`** | **`📤 Transmitter`** |
| **Port** | `42173` | `42173` |
| **Target Peer IP Configured** | N/A (Server listening) | `172.20.38.186` |

---

## 2. Reverse Topology Connection States

- **Tablet (`HNP097HQ`)**:
  - Selected Role: **`📡 Receiver`**
  - Server Role Hint: `Listening on 172.20.38.186:42173`
  - Connection State: `Connected`
  - Active Error Banners: `None (Clean)`
- **Phone (`10BE830QG8000MX`)**:
  - Selected Role: **`📤 Transmitter`**
  - Peer IP Input: `172.20.38.186`
  - Connection State: `Connected to 172.20.38.186:42173`
  - Active Error Banners: `None (Clean)`

---

## 3. Real Physical Speech E2E Test Results

1. **Trigger**: Pressed `btn_ptt` on Phone (`10BE830QG8000MX`).
2. **Spoken Utterance**: `"Hello."`
3. **Pipeline**:
   - Phone Microphone $\rightarrow$ Silero VAD $\rightarrow$ Whisper STT Inference $\rightarrow$ SpeechMessage Serialization $\rightarrow$ TCP (Port `42173`) $\rightarrow$ Tablet Receiver (`HNP097HQ`) $\rightarrow$ SpeechMessage Deserialization $\rightarrow$ Piper Neural TTS Synthesis $\rightarrow$ AudioTrack 16kHz Playback.
4. **Captured UI Transcripts**:
   - **Phone `tv_you_transcript` (Transmitted)**: `"Hello."`
   - **Phone Status**: `Speech recognized & sent`
   - **Tablet `tv_remote_transcript` (Received)**: `"Hello."`
   - **Tablet TTS Playback Confirmation**: `🔊 Played via offline neural TTS (0.5s)`
   - **Tablet Status**: `TTS playback complete`
5. **Accuracy / Match**: **EXACT MATCH (`"Hello."` == `"Hello."`)**

---

## 4. Logcat Stability & Diagnostics

- **Filters Audited**: `MainActivity:E`, `TransceiverCoordinator:E`, `AndroidTransceiverComposition:E`, `Transceiver:E`, `AndroidRuntime:E`
- **Phone Errors / Exceptions**: `0 errors, 0 crashes, 0 ANRs`
- **Tablet Errors / Exceptions**: `0 errors, 0 crashes, 0 ANRs`
- **Source Modifications**: `0 files modified (Read-only validation)`

---

## 5. Visual Artifacts

- **Tablet Receiver Screenshot**: [`scratch/tablet_receiver_connected.png`](file:///c:/Users/saich/Downloads/SIH/PS26173/scratch/tablet_receiver_connected.png)
- **Phone Transmitter Screenshot**: [`scratch/phone_transmitter_connected.png`](file:///c:/Users/saich/Downloads/SIH/PS26173/scratch/phone_transmitter_connected.png)

---

## Final Verdict

FINAL VERDICT:
PASS
