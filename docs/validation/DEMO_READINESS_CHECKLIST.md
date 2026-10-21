# LIVE DEMO READINESS & OPERATIONAL CHECKLIST
**PS26173 iTantra — Tactical Edge Semantic Transceiver**  
**Evaluation Scope:** Live SIH Grand Finale Demonstration Protocol  
**Date:** October 1, 2026  
**Engineering Baseline:** Frozen Build v1.0.0 (`412 / 412 Tests PASS`)  

---

```
DEMO_READINESS = READY_WITH_PRECAUTIONS
```

---

## 1. Executive Summary & Freeze Declaration

All engineering development, architectural refactoring, and protocol modifications for **PS26173 iTantra** are strictly **FROZEN**.

The live evaluation demonstration is engineered to showcase exclusively verified physical and benchmarked capabilities:
1. **Clean Hardware Cold Start:** Two independent physical Android devices (Receiver & Transmitter) operating over a localized, air-gapped Wi-Fi P2P hotspot.
2. **Push-To-Talk (PTT) Acoustic Transceiver Pipeline:** Live microphone capture $\to$ Silero VAD segmentation $\to$ on-device Whisper-Tiny INT8 ASR $\to$ ultra-compact M2 binary frame ($\sim 108\text{ B}$) $\to$ peer-to-peer TCP transport $\to$ IEEE 802.3 CRC-32 validation $\to$ on-device neural TTS $\to$ hardware speaker playback.
3. **Multilingual Live Switching:** Seamless runtime transitions between **English**, **Hindi**, **Gujarati**, and **Tamil/Bengali/Malayalam** with zero application restarts.
4. **Constrained-Link Transport Characterization:** Demonstration of semantic compression down to **0.41 kbps effective bitrate** ($622.8\times$ compression over 256 kbps uncompressed PCM), with packet-level delivery benchmarked down to 1 kbps.
5. **Zero WAN / Cloud Independence:** 100% offline, air-gapped on-device AI inference with zero external API calls.

---

## 2. 3–5 Minute Live Demonstration Script

| Timeline | Step | Demonstrator Action | On-Screen / Acoustic Event | Evaluator Focus Point |
|---|---|---|---|---|
| **0:00 – 0:30** | **Step 1: Cold Boot & Role Selection** | Launch app on Device A (Vivo iQOO Z9) and Device B (Lenovo Tab M10). | Device A taps **"Receiver Mode"**; displays `Listening on 0.0.0.0:42173`. Device B taps **"Transmitter Mode"**; connects to `10.179.231.166:42173`. | Instant startup, zero ANR, clean permissions, non-blocking asynchronous model loading. |
| **0:30 – 1:00** | **Step 2: Local Connection & Air-Gap** | Point out airplane mode / WAN disconnect. Show local hotspot IP mapping. | Both devices show `CONNECTED (P2P Hotspot)`. Zero Internet required. | Prove air-gapped tactical independence (no DNS, no cloud dependencies). |
| **1:00 – 1:45** | **Step 3: English Tactical Transmission** | On Device B, select **English (`en`)**, press & hold PTT, speak: *"Patrol unit reaching checkpoint alpha."* Release PTT. | Device B displays transcribed text + sends 56 B packet. Device A receives packet, validates CRC, and synthesizes natural speech through speaker. | Sub-500ms E2E latency, atomic PTT capture, VAD silence trimming, instant playback. |
| **1:45 – 2:30** | **Step 4: Hindi Devanagari Transmission** | Switch Device B dropdown to **Hindi (`hi`)**. Press PTT, speak: *"नमस्ते यह एक सुरक्षित संचार परीक्षण है"* | Device B outputs native Devanagari script. Device A receives 132 B packet and speaks natural Hindi via neural TTS. | Multilingual ASR without transliteration; native script preservation; high acoustic intelligibility. |
| **2:30 – 3:15** | **Step 5: Gujarati Script & Synthesis** | Switch Device B dropdown to **Gujarati (`gu`)**. Press PTT, speak: *"નમસ્તે આ એક સંચાર પરીક્ષણ છે"* | Device B displays Gujarati script. Device A receives 109 B packet and synthesizes Gujarati voice. | Dynamic runtime switching without reload delay or audio buffer cross-contamination. |
| **3:15 – 3:45** | **Step 6: Cross-Script Demonstration** | Switch Device B to **Tamil (`ta`)** or **Bengali (`bn`)**. Speak tactical phrase. | Transcribes native script (e.g. தமிழ் / বাংলা); transmits $\sim 112\text{--}138\text{ B}$ packet; receiver synthesizes. | Demonstrates script versatility across distinct Indic language families (Dravidian & Indo-Aryan). |
| **3:45 – 4:15** | **Step 7: Wire Protocol & CRC Audit** | Highlight receiver UI diagnostic panel: Magic `0x4954`, SeqID `0x00000004`, LangID `0x02`, Payload `84 B`, Packet `108 B`, CRC `0x7A9B3C12`. | Demonstrate deterministic framing and IEEE 802.3 integrity verification. | Prove extreme byte efficiency ($\sim 108\text{ B}$ vs 256,000 B for 1s raw PCM audio). |
| **4:15 – 4:45** | **Step 8: Constrained-Link Evidence** | Present benchmark evidence slide: 500-packet transmission matrix across 32 kbps, 8 kbps, 2 kbps, and 1 kbps links. | Show empirical link simulation data (*clearly labeled as simulated/benchmarked*). | Demonstrates tactical viability over constrained HF/VHF military bandwidths. |
| **4:45 – 5:00** | **Step 9: Summary & Latency Breakdown** | Conclude with overall latency waterfall chart: STT (339ms) + Network (11ms) + TTS (224ms) = **574ms Total E2E**. | Stand by for evaluator questions. | Solid empirical foundation; 412/412 tests passing. |

---

## 3. Failure-Safe Live Demo Defense Matrix

| Potential Failure Mode | Root Cause | Prevention Protocol | Live Recovery Protocol | Fallback Plan |
|---|---|---|---|---|
| **1. Microphone Permission Denied** | App freshly installed without permission grant | Launch app 10 minutes prior to demo and verify permission dialog | Open Android App Settings $\to$ Permissions $\to$ Enable Microphone | Keep pre-granted demo profile active; do not reinstall in booth |
| **2. Hotspot / Wi-Fi Drop** | Venue RF interference or power saving | Set Wi-Fi Hotspot to 5 GHz (or 2.4 GHz forced channel); disable auto-sleep | Reconnect Wi-Fi from Android Quick Settings tray | Use direct portable Wi-Fi router or USB reverse tethering |
| **3. Receiver IP Address Shift** | DHCP lease renewal on hotspot restart | Set static IP or reserve IP (`10.179.231.166`) on AP | Check Receiver UI header for active IP $\to$ re-type into Transmitter IP box $\to$ tap Connect | Pre-configured QR code or quick IP entry |
| **4. Cold Model Loading Delay** | Heavy ONNX model deserialization on initial launch | Warm up models by running 1 test transmission per language before judges arrive | Wait 2 seconds for initial inference cache to populate | Keep models cached in RAM; do not swipe app away from Recents |
| **5. Microphone Acoustic Mismatch** | Ambient hall noise triggering false VAD | Hold microphone 5–10 cm from mouth; speak in clear, projecting voice | Speak with higher vocal projection ($> 65\text{ dB}$) | Use wired 3.5mm/USB-C headset mic with directional pickup |
| **6. STT Transcription Hallucination** | Whispered speech or background crowd murmur | Use the 5 standardized benchmark phrases per language | Release PTT and re-speak phrase clearly | Point out VAD energy indicator and re-trigger PTT |
| **7. TTS Audio Track Stalling** | Audio focus contention with notification sounds | Put both devices in **"Do Not Disturb" (DND)** mode | Tap role button to reset AudioTrack; re-transmit | Receiver UI displays full decoded text even if audio fails |
| **8. TCP Socket Connection Refused** | Receiver started after Transmitter attempted connect | Always start **Receiver FIRST**, verify `Listening`, then start Transmitter | Tap "Connect" button on Transmitter after Receiver is listening | Restart Receiver activity; port `42173` reclaims via `SO_REUSEADDR` |
| **9. Wrong Language Selected** | Dropdown mismatch between speaker and ASR token | Confirm language chip matches spoken dialect before pressing PTT | Change dropdown to intended language; re-speak | Switch back to English/Hindi baseline |
| **10. Stale / Dead Connection** | Inactive TCP socket timed out by OS | Transmit 1 keepalive test message 1 minute before judging starts | Tap Disconnect $\to$ Connect on Transmitter (takes $< 1\text{s}$) | Instant socket reconnect on `42173` |
| **11. Device Battery Throttling** | CPU throttling below 20% battery level | Keep both devices charged $> 80\%$; plug into power bank | Connect to USB power pack | Disable Android battery saver mode |
| **12. Android Background Restrictions** | OS freezes socket during screen lock | Disable Screen Timeout (set to "Never" or "30 minutes"); keep screen awake | Unlock screen and bring iTantra to foreground | Keep devices actively awake throughout demo |

---

## 4. Hardware & Equipment Readiness Checklist

### Primary Hardware
- [x] **Transmitter Device (Device B):** Lenovo Tab M10 (`HNP097HQ`, Android 12) — Charged $> 80\%$, DND enabled, screen timeout disabled.
- [x] **Receiver Device (Device A):** Vivo iQOO Z9 (`10BE830QG8000MX`, Android 14) — Charged $> 80\%$, DND enabled, max speaker volume.
- [x] **Host Workstation:** Windows 11 Laptop with Android SDK, Gradle 8.11.1, and project repository ready for diagnostic inspection.
- [x] **Local Wi-Fi AP:** Dedicated 5 GHz / 2.4 GHz mobile hotspot (`10.179.231.0/24`) configured and running.

### Cables & Accessories
- [x] 2× High-speed USB-C to USB-A/C data cables.
- [x] 1× 20,000 mAh portable USB power bank with dual fast-charge outputs.
- [x] 1× 3.5mm / USB-C directional external microphone / lapel mic (backup for loud venue noise).
- [x] 1× External portable Bluetooth / 3.5mm auxiliary speaker (amplifies receiver speech for evaluators).

### Software & APK Assets
- [x] Production Debug APK: `app-debug.apk` ($300.03\text{ MB}$, SHA256: `e20b355a87c104f8dd6f09627b98b959352483f46a05b727c74914c10e36a04c`).
- [x] On-Device Models Pre-Installed: Silero VAD, Whisper-Tiny INT8, Piper English, Piper Hindi, Sherpa Gujarati/Tamil/Bengali/Malayalam.
- [x] Verified Offline: Cellular data and WAN uplink disabled; zero Internet access required.

---

## 5. Claims on Screen — Permitted vs Prohibited

```
================================================================================
VERIFIED CLAIMS (SAFE TO DISPLAY ON SCREEN & SLIDES)
================================================================================
 [YES] "6 Indian languages physically verified end-to-end (EN, HI, GU, TA, BN, ML)"
 [YES] "412 / 412 automated JVM tests passing (100% regression free)"
 [YES] "Sub-second physical transceiver latency (~485–685 ms observed)"
 [YES] "Ultra-compact M2 binary packet: 107.9 B average size (84.2 B semantic payload)"
 [YES] "622.8x bandwidth compression compared to 256 kbps uncompressed PCM"
 [YES] "0.41 kbps effective transmission bitrate over tactical links"
 [YES] "Packet transport benchmarked down to 1 kbps constrained link conditions"
 [YES] "100% offline, air-gapped on-device AI inference (zero cloud/WAN dependencies)"
 [YES] "Deterministic IEEE 802.3 CRC-32 integrity validation (100% corruption rejection)"

================================================================================
PROHIBITED / UNSUPPORTED CLAIMS (DO NOT CLAIM OR DISPLAY)
================================================================================
 [NO]  "10/10 languages fully supported" (Odia STT is not supported by Whisper vocabulary)
 [NO]  "Physical radio-frequency (RF) link validated" (Wi-Fi P2P tested; RF simulated)
 [NO]  "Full speech E2E running at 1 kbps physical link" (Packet-level 1 kbps benchmarked)
 [NO]  "100% bug free / production ready" (Appropriate tactical prototype status)
 [NO]  "Continuous hands-free speech streaming" (System operates in tactical PTT mode)
 [NO]  "Emergency audio preemption active" (Flags preserved, but queue preemption not built)
================================================================================
```

---

## 6. Final Build Freeze Metrics

```
============================================================
BUILD FREEZE VERIFICATION
============================================================
Execution Command: ./gradlew.bat clean ; ./gradlew.bat testDebugUnitTest ; ./gradlew.bat assembleDebug
Status:            BUILD SUCCESSFUL (0 errors, 0 warnings fatal)
Test Execution:    412 / 412 unit tests PASS (0 failed, 0 skipped)
Test Suites:       48 classes executed
APK File:          app/build/outputs/apk/debug/app-debug.apk
APK Size:          314,604,212 bytes (300.03 MB)
APK SHA256:        e20b355a87c104f8dd6f09627b98b959352483f46a05b727c74914c10e36a04c
Build Date:        October 1, 2026
============================================================
```

---

## 7. Operational Checklists & Final Status

### TOP 10 Pre-Demo Checks (Execute 15 Minutes Before Evaluator Arrival)
1. **Power & Charge:** Confirm Device A, Device B, and Laptop are $> 80\%$ battery.
2. **Wi-Fi Hotspot Active:** Ensure local AP is broadcasting on `10.179.231.0/24`; both devices connected.
3. **DND Mode:** Turn on **Do Not Disturb** and set media volume to **100%** on Receiver (Device A).
4. **Screen Awake:** Set screen timeout to "Never" / "30 min" on both devices.
5. **Permissions Active:** Confirm `RECORD_AUDIO` permission is active without prompting.
6. **Receiver Started First:** Launch app on Device A $\to$ select "Receiver" $\to$ verify `Listening on 42173`.
7. **Transmitter Connected:** Launch app on Device B $\to$ select "Transmitter" $\to$ enter Device A IP $\to$ tap "Connect" $\to$ verify `Connected`.
8. **Model Warmup Run:** Transmit 1 sentence in English and 1 in Hindi to warm the ONNX inference cache.
9. **Audio Clarity Check:** Ensure external speaker / tablet speaker outputs clear, loud audio.
10. **Slide Presentation Ready:** Open slide deck to Slide 1; verify benchmark tables are visible.

### TOP 10 Live Demo Risks & Mitigations
1. **Venue Ambient Noise:** Evaluator hall loud $\implies$ Speak directly into mic at 5 cm distance.
2. **Accidental App Swipe-Away:** Evaluator or demonstrator swipes app $\implies$ Tap app icon; instant reconnect in $< 2\text{s}$.
3. **Transmitter Disconnected:** Wi-Fi glitch $\implies$ Tap "Connect" button once; immediate recovery.
4. **Judge Questions Odia Support:** Evaluator asks for 10th language $\implies$ State clearly: *"Odia TTS is validated, but Whisper STT lacks Odia vocabulary. We maintain strict integrity and document this boundary honestly."*
5. **Judge Questions Bandwidth Link:** Evaluator asks about 1 kbps $\implies$ Clarify: *"M2 packet transport and header efficiency are empirically validated down to 1 kbps; full speech E2E is physically verified on local P2P links."*
6. **Mic Button Released Too Early:** Incomplete sentence captured $\implies$ Press PTT, pause 0.2s, speak, pause 0.2s, release.
7. **Wrong Language Code:** Speaking Hindi while English selected $\implies$ Whisper transcribes English phonemes; quickly change dropdown to Hindi and re-speak.
8. **Hotspot Channel Congestion:** 2.4 GHz interference $\implies$ Switch hotspot to 5 GHz band.
9. **Volume Inaudible:** Crowd drowning out tablet speaker $\implies$ Connect external portable 3.5mm/Bluetooth speaker to Receiver.
10. **Corrupted Packet Query:** Judge asks how errors are handled $\implies$ Show IEEE 802.3 CRC-32 rejection logs from test suite.

---

### Final Operational Verdict

```
================================================================================
FINAL DEMO STATUS: READY_WITH_PRECAUTIONS
================================================================================
- Codebase, tests, and models are 100% frozen.
- 412 / 412 automated unit tests pass cleanly.
- 6 languages physically verified on real Android hardware.
- Live demonstration workflow is timed, scripted, and failure-defended.
================================================================================
```
