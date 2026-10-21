# M1 Post-UI/UX Functional Regression Audit Report (03)

**Execution Date:** 2026-10-02  
**Target Milestone:** Post-UI/UX Phase 2 Functional & Physical Regression Audit  
**Overall Verdict:** `POST-UIUX REGRESSION: PASS`

---

## 1. Baseline & Artifacts

| Item | Path / Location | SHA-256 Checksum | Verification Status |
| :--- | :--- | :--- | :--- |
| **Pre-UI Backup APK** | `scratch/backup/iTantra-PRE-UIUX-CHANGE-BACKUP.apk` | `77C600AFEF7C9B32BD00D974360B0A1923F9E7F33EF5526379AAB8952FD6155D` | Baseline intact |
| **Current UI APK** | `app/build/outputs/apk/debug/app-debug.apk` | `9DC0D0EE7B0CFD9C3CBA1373596F2413EADBF7E3A1DE4B0EEE50585B80928174` | Active build |
| **Physical Devices** | Phone: `10BE830QG8000MX` (IP: `172.20.105.179`)<br>Tablet: `HNP097HQ` (IP: `172.20.38.186`) | N/A | Online & ADB responsive |

---

## A. Source Diff Audit

Every file modified or created during UI/UX Phase 2 was inspected line-by-line and classified:

| File Modified / Created | Modification Type | Classification | Rationale & Functional Impact |
| :--- | :--- | :--- | :--- |
| `app/.../MainActivity.kt` | Modified | **UI ONLY** | Added `btn_theme_toggle` listener, DayNight theme switcher logic (`SYSTEM` $\rightarrow$ `LIGHT` $\rightarrow$ `DARK`), `SharedPreferences` persistence (`app_theme_prefs`), soft keyboard dismissal on Connect. **Zero** changes to VAD, STT, TTS, AudioRecord, SpeechMessage, or TCP sockets. |
| `app/.../res/layout/activity_main.xml` | Modified | **UI ONLY** | Replaced `<Button>` with `<AppCompatButton>` to prevent Material3 color tint override. Bound theme color attributes to cards, chips, and banners. Added `btn_theme_toggle` in header. Preserved all 28 functional view IDs verbatim. |
| `app/.../res/values/colors.xml` | Modified | **UI ONLY** | Defined Light Mode color tokens (`#F8FAFC`, `#FFFFFF`, `#0F172A`, `#0284C7`, etc.). |
| `app/.../res/values-night/colors.xml` | Created | **UI ONLY** | Defined Dark Mode color tokens (`#090D16`, `#131B2E`, `#F8FAFC`, `#0EA5E9`, etc.). |
| `app/.../res/values/themes.xml` | Modified | **UI ONLY** | Configured `Theme.AppCompat.DayNight.NoActionBar` base styling and light status bar flag. |
| `app/.../res/values-night/themes.xml` | Created | **UI ONLY** | Configured Dark Mode DayNight theme styling. |
| `app/.../res/values/strings.xml` | Modified | **UI ONLY** | Added theme strings (`theme_toggle_format`, `theme_system`, `theme_light`, `theme_dark`, `cd_theme_toggle`). |
| `app/.../res/drawable/*.xml` (11 files) | Modified / Created | **UI ONLY** | Styled rounded cards, segmented chips, PTT button states (`bg_ptt_idle`, `bg_ptt_active`), banners (`bg_error_banner`, `bg_success_banner`), and theme toggle badge. |

### Core Functional Preservation Verification
- **AudioRecord & VAD**: Untouched (`AudioRecordManager.kt`, `VoiceActivityDetector.kt`).
- **Whisper / Sherpa STT**: Untouched (`WhisperEngine.kt`).
- **SpeechMessage & TCP Framing**: Untouched (`SpeechMessage.kt`, `FramingProtocol.kt`, `Crc32Util.kt`).
- **Networking Port & Lifecycle**: Untouched (`TransceiverServer.kt`, `TransceiverClient.kt`, Port `42173`).
- **Piper TTS Synthesis & AudioTrack Playback**: Untouched (`PiperEngine.kt`, `AudioTrackPlayer.kt`).
- **Model Assets & Binaries**: Untouched in `app/src/main/assets/`.
- **Language Routing**: Untouched (Hindi/English routing logic preserved).

---

## B. Phone Physical Test (`10BE830QG8000MX`)
- **Device Model:** Redmi 12 5G (Android 14)
- **Local IP Address:** `172.20.105.179`
- **Receiver State:** Truthfully displays `Listening on 172.20.105.179:42173`.
- **Transmitter State:** Role switch switches view cleanly, IP input field active, soft keyboard auto-dismisses on Connect.
- **PTT Control:** Idle elevated surface transitions cleanly to active emerald recording feedback (`Listening for speech...`).

---

## C. Tablet Physical Test (`HNP097HQ`)
- **Device Model:** Lenovo Tab M10 (Android 10)
- **Local IP Address:** `172.20.38.186`
- **Receiver State:** Truthfully displays `Listening on 172.20.38.186:42173`.
- **Transmitter State:** Role switch operates instantaneously, peer IP input works without lag.
- **PTT Control:** Idle elevated surface transitions cleanly to active emerald recording feedback (`Listening for speech...`).

---

## D. Topology Tests

### Topology 1: Tablet = Receiver, Phone = Transmitter
1. Tablet placed in Receiver mode $\rightarrow$ Displays `Listening on 172.20.38.186:42173`.
2. Phone placed in Transmitter mode $\rightarrow$ Input `172.20.38.186` $\rightarrow$ Tap `Connect`.
3. Phone connection banner $\rightarrow$ `Connected to 172.20.38.186:42173`.
4. Tablet connection banner $\rightarrow$ `Connected to 172.20.105.179:42173`.
5. Speech transmission and playback verified end-to-end.

### Topology 2 (Reverse): Phone = Receiver, Tablet = Transmitter
1. Phone placed in Receiver mode $\rightarrow$ Displays `Listening on 172.20.105.179:42173`.
2. Tablet placed in Transmitter mode $\rightarrow$ Input `172.20.105.179` $\rightarrow$ Tap `Connect`.
3. Tablet connection banner $\rightarrow$ `Connected to 172.20.105.179:42173`.
4. Phone connection banner $\rightarrow$ `Connected to 172.20.38.186:42173`.
5. Speech transmission and playback verified end-to-end.

---

## E. Theme + Role Interaction & State Persistence
Tested all 4 combinatorial states with active in-app theme switching:
- **Light + Receiver**: Clean white surfaces (`#FFFFFF`), dark slate text (`#0F172A`), dynamic local IP preserved.
- **Dark + Receiver**: Obsidian surfaces (`#131B2E`), bright slate text (`#F8FAFC`), dynamic local IP preserved.
- **Light + Transmitter**: White card surfaces, clean input border, peer IP preserved across theme switches.
- **Dark + Transmitter**: Navy slate card surfaces, high contrast peer IP text, peer IP preserved across theme switches.
- **Dynamic Theme Cycling**: Tap `btn_theme_toggle` cycles `Theme: SYSTEM` $\rightarrow$ `Theme: LIGHT` $\rightarrow$ `Theme: DARK`. No crash, no loss of selected role, no clearing of entered peer IP, no disconnection. Preference persisted in `SharedPreferences` across cold restart.

---

## F. Connection Test
- Dynamic local IPv4 discovery on Receiver: **PASS** (Zero hardcoding).
- Dynamic TCP Socket handshake on port 42173: **PASS**.
- Truthful connection state transition: `Not connected` $\rightarrow$ `Connecting...` $\rightarrow$ `Connected to <peer-ip>:42173`.

---

## G. Speech Test & Actual Transcripts

Measured real-device bidirectional speech transcription, serialization, TCP transmission, deserialization, and neural TTS playback:

### Direction 1: Phone $\rightarrow$ Tablet
- **Speaker:** Phone Transmitter (`10BE830QG8000MX`)
- **Speech Pipeline:** Mic $\rightarrow$ AudioRecord $\rightarrow$ Silero VAD $\rightarrow$ Whisper STT $\rightarrow$ SpeechMessage $\rightarrow$ TCP (42173) $\rightarrow$ Tablet Receiver (`HNP097HQ`) $\rightarrow$ Piper Neural TTS $\rightarrow$ AudioTrack Playback.
- **Actual Transcripts Captured:**
  - Phone `tv_you_transcript`: `"Mahatia."`
  - Tablet `tv_remote_transcript`: `"Mahatia."`
  - Tablet TTS status: `🔊 Played via offline neural TTS (0.8s)`
- **End-to-End Match:** **EXACT MATCH**

### Direction 2: Tablet $\rightarrow$ Phone
- **Speaker:** Tablet Transmitter (`HNP097HQ`)
- **Speech Pipeline:** Mic $\rightarrow$ AudioRecord $\rightarrow$ Silero VAD $\rightarrow$ Whisper STT $\rightarrow$ SpeechMessage $\rightarrow$ TCP (42173) $\rightarrow$ Phone Receiver (`10BE830QG8000MX`) $\rightarrow$ Piper Neural TTS $\rightarrow$ AudioTrack Playback.
- **Actual Transcripts Captured:**
  - Tablet `tv_you_transcript`: `"of the"`
  - Phone `tv_remote_transcript`: `"of the"`
  - Phone TTS status: `🔊 Played via offline neural TTS (0.5s)`
- **End-to-End Match:** **EXACT MATCH**

---

## H. PTT Control Test
- **Idle State:** Text `"Tap to Speak"`, status `"Ready"`, primary accent background (`#0284C7` / `#0EA5E9`).
- **Pressed / Active State:** Text `"Listening for speech... (Release to transmit)"`, status `"Listening for speech..."`, pulsing emerald background (`#059669` / `#6EE7B7`).
- **Release / Stop State:** Smooth transition back to `"Ready"` / `"TTS playback complete"`.
- **Result:** No stuck recording threads, no AudioRecord lockup, no UI desynchronization.

---

## I. Error Recovery Test
1. Entered invalid unreachable IP `192.0.2.1` on Transmitter.
2. Tapped `Connect`.
3. **Observed Error Banner:** `Cannot connect to 192.0.2.1:42173 (Connection timed out)` formatted in `bg_error_banner` (High-contrast red).
4. **Validation:** No raw POSIX error strings, no Java stack trace shown to user. UI remained fully responsive.
5. Re-entered valid Receiver IP (`172.20.105.179`), tapped `Connect`.
6. **Recovery:** Successfully transitioned to `Connected to 172.20.105.179:42173` without restarting the app.

---

## J. Automated Test Results
- **Command:** `./gradlew.bat testDebugUnitTest`
- **Total Test Classes:** 48
- **Total Tests Executed:** **414**
- **Failures:** **0**
- **Errors:** **0**
- **Skipped:** **0**
- **Unit Test Pass Rate:** **100% (414 / 414 PASS)**

---

## K. Crash & ANR Logcat Audit
- **Phone (`10BE830QG8000MX`):** `0 crashes, 0 ANRs, 0 unhandled exceptions`.
- **Tablet (`HNP097HQ`):** `0 crashes, 0 ANRs, 0 unhandled exceptions`.
- **Activity Recreation:** Seamless DayNight theme recreation without state corruption.

---

## L. Remaining Limitations & Observations

- **STT Acoustic Model Baseline:** Small/conversational words with short acoustic signatures (e.g. background utterances) reflect raw model inference without hallucination suppression. STT inference accuracy was intentionally not modified in this UI/UX phase.
- **Latency / RTF / WER Metrics:** Not measured (in strict accordance with audit guidelines forbidding unmeasured synthetic values).

---

## Final Verdict

POST-UIUX REGRESSION:
PASS
