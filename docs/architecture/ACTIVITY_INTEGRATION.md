# MainActivity Transceiver Integration Architecture

> **TASK ID:** `activity-integration-01`  
> **DATE:** 2026-09-21  
> **STATUS:** `MAINACTIVITY PRODUCTION COMPOSITION INTEGRATED — DEVICE VALIDATION PENDING`  
> **EVIDENTIARY CLASSIFICATION:**  
> - `[VERIFIED BY CODE]`: MainActivity source structure, single composition ownership, listener wiring, lifecycle safety.  
> - `[VERIFIED BY JVM TEST]`: Presentation and state mapping in `TransceiverUiFormatterTest` (5 tests) & `AndroidTransceiverCompositionTest` (10 tests). Total test suite: 343 tests passing.  
> - `[NOT DEVICE VALIDATED]`: Physical microphone capture, live speaker output, and physical TCP network links.  
> - `[NOT IMPLEMENTED]`: Full network settings UI, discovery, Wi-Fi Direct / BLE auto-pairing.  
> - `[REQUIRES DEVICE VALIDATION]`: Live on-device audio latency, acoustic intelligibility, and physical Wi-Fi connection.

---

## 1. Executive Summary

`MainActivity` has been migrated from a direct M1 diagnostic harness to an application-level control surface governing a single `AndroidTransceiverComposition` instance.

### Key Architectural Enhancements:
1. **Single Composition Ownership:** `MainActivity` owns exactly one `AndroidTransceiverComposition` instance across its Activity lifecycle `[VERIFIED BY CODE]`.
2. **Zero Duplicate Pipeline Construction:** Direct construction of `SpeechPipeline`, `AudioRecordCapture`, `SileroVadEngine`, `SherpaWhisperAsrEngine`, `SherpaOfflineTtsEngine`, `AndroidAudioTrackPlaybackEngine`, `SpeechMessageTransportAdapter`, and `TransceiverCoordinator` has been eliminated from `MainActivity` `[VERIFIED BY CODE]`.
3. **Preserved Permission Model:** Android `RECORD_AUDIO` permission remains strictly governed by `MainActivity` via `ActivityResultLauncher` `[VERIFIED BY CODE]`.
4. **Unified Error Classification:** Wire all coordinator errors (`M1PipelineError`, `M2TransportError`, `M3TtsError`, `M3PlaybackError`, `LifecycleError`) through `TransceiverUiFormatter` to user-facing diagnostic banners `[VERIFIED BY JVM TEST]`.
5. **Frozen Benchmark Compatibility:** Diagnostic intent handlers (`RUN_ASR_SMOKE_TEST`, `RUN_ASR_ACCURACY_BENCHMARK`, `RUN_ASR_HINDI_BENCHMARK`, `RUN_ASR_HINDI_DIAGNOSTIC`, `RUN_VOSK_HINDI_BENCHMARK`) remain fully operational and untouched `[VERIFIED BY CODE]`.

---

## 2. Migration Mapping

| Subsystem / Resource | Old Direct Object in `MainActivity` | New Composition Owner |
| :--- | :--- | :--- |
| **M1 Pipeline** | `private var speechPipeline: SpeechPipeline?` | `AndroidTransceiverComposition.speechPipeline` / `pipelineControl` |
| **M1 Initialization** | Direct background `pipeline.initialize()` | `AndroidTransceiverComposition.initialize()` |
| **M2 Transport Adapter** | None (Orphaned / Unwired) | `AndroidTransceiverComposition.transportAdapter` |
| **M2 Connection Manager** | None (Orphaned / Unwired) | `AndroidTransceiverComposition.connectionManager` |
| **M3 TTS Synthesis** | None (Orphaned / Unwired) | `AndroidTransceiverComposition.ttsEngine` |
| **M3 AudioTrack Playback** | None (Orphaned / Unwired) | `AndroidTransceiverComposition.playbackEngine` |
| **M3 Coordinator** | None (Orphaned / Unwired) | `AndroidTransceiverComposition.playbackCoordinator` |
| **Top-Level Coordinator** | None (Unwired) | `AndroidTransceiverComposition.coordinator` |
| **Event / Error Listener** | `SpeechPipelineListener` | `TransceiverListener` via `TransceiverUiFormatter` |
| **Teardown / Release** | `speechPipeline?.release()` | `AndroidTransceiverComposition.release()` (7-step cascading order) |

---

## 3. Boundary & Ownership Model

```
┌────────────────────────────────────────────────────────────────────────┐
│                              MainActivity                              │
│  - Owns UI Views (tvStatus, tvVadState, tvAsrTranscript, tvLastError)  │
│  - Owns Permission Request Contract (RECORD_AUDIO)                    │
│  - Owns Android Lifecycle Callbacks (onCreate, onStop, onDestroy)      │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (owns 1 instance)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     AndroidTransceiverComposition                      │
│                                                                        │
│   ┌───────────────────────────┐    ┌──────────────────────────────┐    │
│   │   M1 Speech Pipeline      │    │    M2 Transport Subsystem    │    │
│   │   - AudioRecordCapture    │    │    - TcpConnectionManager    │    │
│   │   - Silero VAD v5         │    │    - MessageTransport        │    │
│   │   - Whisper-Tiny INT8 ASR │    │    - TransportAdapter        │    │
│   └─────────────┬─────────────┘    └──────────────┬───────────────┘    │
│                 │                                 │                    │
│                 └───────────────┬─────────────────┘                    │
│                                 │                                      │
│                                 ▼                                      │
│                  ┌─────────────────────────────┐                       │
│                  │   TransceiverCoordinator    │                       │
│                  └──────────────┬──────────────┘                       │
│                                 │                                      │
│                                 ▼                                      │
│                  ┌─────────────────────────────┐                       │
│                  │    M3 Playback Subsystem    │                       │
│                  │    - PlaybackCoordinator    │                       │
│                  │    - Sherpa Offline TTS     │                       │
│                  │    - AudioTrack Engine      │                       │
│                  └─────────────────────────────┘                       │
└────────────────────────────────────────────────────────────────────────┘
```

### Architectural Boundaries:
1. **Permission Boundary:**
   - Android runtime permission `RECORD_AUDIO` is checked in `MainActivity`.
   - Core composition classes have zero Android runtime permission awareness.
2. **Local Transceiver vs. Network Boundary:**
   - `composition.start()` and `composition.stop()` manage local M1 audio capture and M3 synthesis presentation.
   - `composition.connect()` and `composition.disconnect()` manage network socket state independently.
   - Starting the transceiver does NOT auto-connect to arbitrary IP peers `[ENGINEERING DECISION]`.
3. **Error Boundary:**
   - Subsystem errors are categorized into `TransceiverError` (`M1PipelineError`, `M2TransportError`, `M3TtsError`, `M3PlaybackError`, `LifecycleError`).
   - `TransceiverUiFormatter` maps error types into status headers and descriptions without silencing exceptions `[VERIFIED BY JVM TEST]`.
4. **Release Boundary:**
   - `onDestroy()` executes asynchronous release via background scope, executing the strict 7-step teardown order `[VERIFIED BY CODE]`.

---

## 4. Lifecycle & Safety Analysis

| State / Trigger | Activity Action | Composition Response | Safety Status |
| :--- | :--- | :--- | :--- |
| **`onCreate()`** | Initializes view references; handles intents | Triggers async `initComposition()` | `[SAFE]` |
| **Start Button (No Permission)** | Launches `RECORD_AUDIO` permission contract | Composition is not touched | `[SAFE]` |
| **Permission Granted** | Invokes `startTransceiver()` | Starts `TransceiverCoordinator` & `SpeechPipeline` | `[SAFE]` |
| **Permission Denied** | Displays explanation in `tvLastError` | Does not start audio capture; UI reset | `[SAFE]` |
| **Start Button (Permission Granted)**| Invokes `startTransceiver()` | `composition.start()` $\to$ state becomes `RUNNING` | `[SAFE]` |
| **Stop Button** | Invokes `stopTransceiver()` | `composition.stop()` $\to$ state becomes `STOPPED` | `[SAFE]` |
| **Release Button** | Dispatches async release thread | `composition.release()` $\to$ state becomes `RELEASED` | `[SAFE]` |
| **Repeated Stop** | `stopTransceiver()` | Idempotent no-op | `[SAFE]` |
| **Repeated Release** | Multiple release triggers | `AtomicBoolean.compareAndSet` guards teardown | `[SAFE]` |
| **Start after Release** | User taps Start | `initComposition(autoStartOnReady = true)` cleanly re-instantiates | `[SAFE]` |
| **`onDestroy()`** | Activity destruction | Launches async `composition.release()` teardown | `[SAFE]` |

---

## 5. Verification Matrix

| Verification Aspect | Result / Metric | Classification |
| :--- | :--- | :--- |
| **JVM Unit Tests** | 343 / 343 passed | `[VERIFIED BY JVM TEST]` |
| **Gradle assembleDebug** | Build Succeeded | `[VERIFIED BY CODE]` |
| **Debug APK Size** | 180,221,199 bytes (~180 MB) | `[VERIFIED BY CODE]` |
| **Benchmark Preservation** | All 5 frozen benchmark runners intact | `[VERIFIED BY CODE]` |
| **Physical Hardware DAC** | AudioTrack instantiation | `[NOT DEVICE VALIDATED]` |
| **Physical Wi-Fi Sockets** | Point-to-Point TCP | `[NOT DEVICE VALIDATED]` |
| **Physical Mic Input** | AudioRecord capture | `[NOT DEVICE VALIDATED]` |
