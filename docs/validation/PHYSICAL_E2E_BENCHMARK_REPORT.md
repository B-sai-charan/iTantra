# Physical End-to-End Voice-to-Voice Controlled Benchmark Report

**Project**: iTantra (PS26173)  
**Date**: September 29, 2026  
**Status**: `INSTRUMENTATION READY — BASELINE FROZEN`  
**Network Medium**: Dedicated Subnet Wi-Fi (`10.79.175.0/24`)  

---

## 1. Physical Hardware & Topology Setup

```mermaid
flowchart LR
    subgraph DeviceA ["Device A: Transmitter (iQOO / vivo I2304)"]
        direction TB
        Mic["Acoustic Voice Input"] --> AudioRecord["PCM16 16kHz Capture"]
        AudioRecord --> SileroVAD["Silero VAD v5 (250ms/400ms)"]
        SileroVAD --> WhisperASR["Sherpa Whisper STT Engine"]
        WhisperASR --> PacketMapper["TransceiverPacketMapper"]
        PacketMapper --> TcpClient["SocketMessageTransport (TCP Client)"]
    end

    subgraph Network ["Wi-Fi Subnet 10.79.175.0/24"]
        TcpClient -->|Framed Binary Packets (Port 42173)| TcpServer
    end

    subgraph DeviceB ["Device B: Receiver (Lenovo Tablet TB-X6C6F)"]
        direction TB
        TcpServer["TcpServerTransport (Port 42173)"] --> FrameDecoder["PacketDecoder (CRC32 Check)"]
        FrameDecoder --> InboundCoord["TransceiverCoordinator"]
        InboundCoord --> SherpaTTS["Sherpa Offline Piper VITS"]
        SherpaTTS --> AudioTrack["AndroidAudioTrackPlaybackEngine"]
        AudioTrack --> Speaker["Physical Speaker Output"]
    end
```

### Hardware Endpoints
- **Device A (Transmitter)**: vivo / iQOO I2304 (`10BF4R1B38001A7`), IP: `10.79.175.74`
- **Device B (Receiver)**: Lenovo Tab M10 HD TB-X6C6F (`HNP097HQ`), IP: `10.79.175.26`, TCP Port: `42173`

---

## 2. Classification Legend

| Classification | Definition |
|---|---|
| **`MEASURED`** | Empirical values recorded directly from physical hardware, logcat timestamps, or AudioTrack buffers. |
| **`IMPLEMENTED`** | Deterministic values derived from the active production codebase, wire protocol specifications, and byte layouts. |
| **`NOT VALIDATED`** | Metrics awaiting acoustic audio execution in the upcoming physical trial run. |

---

## 3. Wire Protocol & Packet Framing Math

All messages use the M2 binary packet framing format:
- **Length Prefix**: 4 bytes (`UInt32` big-endian payload length)
- **Magic Bytes**: 4 bytes (`0x53 0x49 0x48 0x30` / ASCII `"SIH0"`)
- **Version**: 1 byte (`0x01`)
- **Sequence Number**: 4 bytes (`UInt32` big-endian)
- **Timestamp**: 8 bytes (`UInt64` big-endian Unix epoch ms)
- **Language Code**: 2 bytes (`UTF-8` ASCII `"en"` or `"hi"`)
- **Text Length**: 2 bytes (`UInt16` big-endian payload byte count)
- **Text Payload**: $N$ bytes (`UTF-8` encoded string)
- **Audio Duration**: 4 bytes (`UInt32` ms)
- **Reserved**: 4 bytes (`0x00000000`)
- **CRC-32**: 4 bytes (`IEEE 802.3 CRC-32` over all preceding bytes)

$$\text{Total Packet Payload} = 33 + N \text{ bytes}$$
$$\text{Total On-The-Wire Frame} = 4 + (33 + N) = 37 + N \text{ bytes}$$

---

## 4. Controlled Benchmark Trial Matrix

### English Test Suite (5 Utterances)

| Trial ID | Status | Original Sentence | Text Bytes ($N$) | Complete Packet | Wire Frame | STT Latency | Transit Latency | TTS Latency | Total E2E | Recog. Correct | Playback |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **EN-01** | `IMPLEMENTED` | *Hello, this is a communication test.* | 37 B | 70 B | 74 B | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |
| **EN-02** | `IMPLEMENTED` | *The mission starts at nine in the morning.* | 42 B | 75 B | 79 B | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |
| **EN-03** | `IMPLEMENTED` | *Send the emergency message immediately.* | 39 B | 72 B | 76 B | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |
| **EN-04** | `IMPLEMENTED` | *The system works without internet connectivity.* | 47 B | 80 B | 84 B | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |
| **EN-05** | `IMPLEMENTED` | *This message is transmitted as semantic text.* | 45 B | 78 B | 82 B | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |

---

### Hindi Test Suite (5 Utterances)

| Trial ID | Status | Original Sentence | Text Bytes ($N$) | Complete Packet | Wire Frame | STT Latency | Transit Latency | TTS Latency | Total E2E | Recog. Correct | Playback |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **HI-01** | `IMPLEMENTED` | *नमस्ते, यह एक संचार परीक्षण है।* | 78 B | 111 B | 115 B | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |
| **HI-02** | `IMPLEMENTED` | *अभियान सुबह नौ बजे शुरू होगा।* | 74 B | 107 B | 111 B | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |
| **HI-03** | `IMPLEMENTED` | *आपातकालीन संदेश तुरंत भेजें।* | 74 B | 107 B | 111 B | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |
| **HI-04** | `IMPLEMENTED` | *यह प्रणाली बिना इंटरनेट के काम करती है।* | 101 B | 134 B | 138 B | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |
| **HI-05** | `IMPLEMENTED` | *यह संदेश अर्थपूर्ण पाठ के रूप में भेजा गया है।* | 116 B | 149 B | 153 B | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* | *Pending* |

---

## 5. Step-by-Step Physical Trial Execution Protocol

Follow these exact steps to collect empirical measurements for each trial:

### Step 1: Initialize Receiver on Device B (Lenovo Tablet)
```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s HNP097HQ shell am force-stop org.itantra.app
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s HNP097HQ shell am start -n org.itantra.app/org.itantra.MainActivity -a org.itantra.action.START_PIPELINE
```

### Step 2: Initialize Transmitter on Device A (Phone)
- For English Suite (`targetLanguage = en`):
  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 10BF4R1B38001A7 shell am force-stop org.itantra.app
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 10BF4R1B38001A7 shell am start -n org.itantra.app/org.itantra.MainActivity -a org.itantra.action.START_E2E_TRANSMITTER -e serverHost 10.79.175.26 -e targetLanguage en
  ```
- For Hindi Suite (`targetLanguage = hi`):
  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 10BF4R1B38001A7 shell am force-stop org.itantra.app
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 10BF4R1B38001A7 shell am start -n org.itantra.app/org.itantra.MainActivity -a org.itantra.action.START_E2E_TRANSMITTER -e serverHost 10.79.175.26 -e targetLanguage hi
  ```

### Step 3: Speak Trial Sentence into Device A Microphone
Speak each utterance clearly, waiting 3–5 seconds between trials for TTS synthesis and AudioTrack playback on Device B.

### Step 4: Capture Benchmark Logcat Telemetry
- On Device A (Transmitter Timings):
  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s 10BF4R1B38001A7 logcat -d | Select-String -Pattern "PHYSICAL_E2E|SpeechPipeline|ASR" | Select-Object -Last 30
  ```
- On Device B (Receiver & TTS Timings):
  ```powershell
  & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s HNP097HQ logcat -d | Select-String -Pattern "PHYSICAL_E2E|M2_M3_TEST|TTS" | Select-Object -Last 30
  ```
