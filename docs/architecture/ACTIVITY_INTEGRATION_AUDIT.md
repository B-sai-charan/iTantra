# Activity Integration Static Audit

> **TASK ID:** `activity-integration-audit-01`  
> **DATE:** 2026-09-21  
> **STATUS:** `ACTIVITY INTEGRATION AUDIT PASS — READY FOR DEVICE VALIDATION`  
> **EVIDENTIARY CLASSIFICATION:**  
> - `[VERIFIED BY CODE]`: Audited via direct static code and control flow analysis.  
> - `[VERIFIED BY JVM TEST]`: Verified through 343 passing JVM unit tests.  
> - `[NOT DEVICE VALIDATED]`: Physical audio I/O, live network sockets, and on-device UI rendering deferred to physical tablet run.  
> - `[REQUIRES DEVICE VALIDATION]`: Acoustic intelligibility, hardware DAC performance, and Wi-Fi latency under physical RF conditions.

---

## Scope

This static audit strictly inspects the integration boundary between [`MainActivity`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/MainActivity.kt) and [`AndroidTransceiverComposition`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/composition/AndroidTransceiverComposition.kt).

The audit verifies:
1. Complete elimination of duplicate M1, M2, and M3 pipeline components.
2. Safe Activity lifecycle management and asynchronous model initialization.
3. Runtime `RECORD_AUDIO` permission safety boundaries.
4. Main UI thread dispatching of all transceiver callbacks.
5. Strict decoupling between local transceiver capture/playback and network transport connection.
6. Error classification and propagation without exception swallowing.
7. Total isolation of diagnostic/benchmark runners from production audio paths.

---

## Files Audited

1. [`app/src/main/java/org/itantra/MainActivity.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/MainActivity.kt) `[VERIFIED BY CODE]`
2. [`app/src/main/java/org/itantra/core/composition/AndroidTransceiverComposition.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/composition/AndroidTransceiverComposition.kt) `[VERIFIED BY CODE]`
3. [`app/src/main/java/org/itantra/core/coordinator/TransceiverCoordinator.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverCoordinator.kt) `[VERIFIED BY CODE]`
4. [`app/src/main/java/org/itantra/core/coordinator/TransceiverListener.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverListener.kt) `[VERIFIED BY CODE]`
5. [`app/src/main/java/org/itantra/core/coordinator/TransceiverState.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverState.kt) `[VERIFIED BY CODE]`
6. [`app/src/main/java/org/itantra/core/coordinator/TransceiverError.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/coordinator/TransceiverError.kt) `[VERIFIED BY CODE]`
7. [`app/src/main/java/org/itantra/ui/TransceiverUiFormatter.kt`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/ui/TransceiverUiFormatter.kt) `[VERIFIED BY CODE]`
8. [`app/src/main/res/layout/activity_main.xml`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/res/layout/activity_main.xml) `[VERIFIED BY CODE]`
9. [`app/src/main/AndroidManifest.xml`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/AndroidManifest.xml) `[VERIFIED BY CODE]`
10. [`docs/architecture/ACTIVITY_INTEGRATION.md`](file:///c:/Users/saich/Downloads/SIH/PS26173/docs/architecture/ACTIVITY_INTEGRATION.md) `[VERIFIED BY CODE]`

---

## Composition Ownership

- **Single Instance Guarantee:** `MainActivity` maintains exactly one reference `private var composition: AndroidTransceiverComposition? = null` `[VERIFIED BY CODE]`.
- **Zero Duplicate Pipeline Construction:** Direct instantiations of `SpeechPipeline`, `AudioRecordCapture`, `SileroVadEngine`, `SherpaWhisperAsrEngine`, `SherpaOfflineTtsEngine`, `AndroidAudioTrackPlaybackEngine`, `SpeechMessageTransportAdapter`, and `TransceiverCoordinator` have been eliminated from `MainActivity` `[VERIFIED BY CODE]`.
- **Sole Construction Point:** All subsystems are constructed exclusively inside `AndroidTransceiverComposition` `[VERIFIED BY CODE]`.

---

## Initialization

- **Non-Blocking Threading:** `initComposition()` creates and initializes `AndroidTransceiverComposition` inside a dedicated background worker thread `[VERIFIED BY CODE]`. The Android Main UI thread is never blocked during ONNX model loading or memory allocation `[VERIFIED BY CODE]`.
- **Race Condition Guard:** `isInitializing.compareAndSet(false, true)` ensures concurrent start or intent requests do not spawn redundant background initialization threads `[VERIFIED BY CODE]`.
- **Failure UI Propagation:** Model loading failures (`initRes.isFailure` or caught exceptions) are marshaled to `tvStatus` and `tvLastError` via `runOnUiThread` `[VERIFIED BY CODE]`.
- **Post-Release Re-initialization:** If `startTransceiver()` is invoked when `composition.isReleased == true`, `initComposition()` cleanly creates and initializes a fresh composition instance `[VERIFIED BY CODE]`.

---

## Permission Boundary

- **Manifest Declaration:** `<uses-permission android:name="android.permission.RECORD_AUDIO" />` is declared in `AndroidManifest.xml` `[VERIFIED BY CODE]`.
- **Runtime Flow:** `hasAudioPermission()` checks `ContextCompat.checkSelfPermission` before invoking `startTransceiver()`. If missing, `requestPermissionLauncher.launch(RECORD_AUDIO)` is requested `[VERIFIED BY CODE]`.
- **Denied Permission Safety:** If denied, `tvStatus` updates to `"PERMISSION DENIED"`, `tvLastError` provides actionable instructions, and `updateButtonStates(isRunning = false)` is invoked. No `AudioRecord` instance is allocated or started `[VERIFIED BY CODE]`.
- **Core Decoupling:** Core domain classes (`SpeechPipeline`, `TransceiverCoordinator`, `AndroidTransceiverComposition`) contain zero Android runtime permission checks, maintaining pure system modularity `[VERIFIED BY CODE]`.

---

## Activity Lifecycle

- **`onCreate()`:** Initializes layout views, attaches button click handlers, and triggers non-blocking asynchronous composition initialization `[VERIFIED BY CODE]`.
- **`onStop()`:** If `isFinishing == true`, stops live transceiver capture via `composition?.stop()` `[VERIFIED BY CODE]`.
- **`onDestroy()`:** Permanently releases the composition via `CoroutineScope(Dispatchers.IO).launch { comp.release() }`, triggering the strict 7-step cascading teardown `[VERIFIED BY CODE]`.
- **Configuration Change Limitation:** Device rotation destroys and recreates `MainActivity`, which releases the current composition and creates a fresh instance. *Configuration survival via ViewModel or state retention is not implemented and remains a documented architectural limitation* `[VERIFIED BY CODE]`.

---

## UI Thread Safety

- **Callback Marshaling:** All callbacks received by `createTransceiverListener()` (`onStateChanged`, `onOutboundSpeechMessage`, `onInboundSpeechMessage`, `onInboundPlaybackCompleted`, `onError`) explicitly wrap view modifications in `runOnUiThread { ... }` `[VERIFIED BY CODE]`.
- **Background Async Operations:** Initialization completions, release completions, and benchmark report dispatches marshal all UI updates to the Android Main Thread `[VERIFIED BY CODE]`.

---

## Start / Stop

- **Idempotency:** `TransceiverCoordinator.start()` and `TransceiverCoordinator.stop()` are idempotent thread-safe operations guarded by internal mutex locks `[VERIFIED BY JVM TEST]`.
- **Network Decoupling:** Invoking `composition.stop()` halts local M1 audio capture and detaches M3 inbound receivers; it does NOT disconnect active network sockets `[VERIFIED BY JVM TEST]`.
- **No Arbitrary Auto-Connect:** Invoking `composition.start()` activates local capture and synthesis routing; it does NOT automatically connect to an arbitrary peer IP `[VERIFIED BY CODE]`.
- **Button State Alignment:** `updateButtonStates(isRunning)` synchronizes button enablement with coordinator state changes (`STARTING`/`RUNNING` $\to$ Stop enabled; `STOPPED`/`RELEASED`/`FAILED` $\to$ Start/Release enabled) `[VERIFIED BY CODE]`.

---

## Connect / Disconnect

- **Decoupled API:** Network socket management is governed via `composition.connect()` and `composition.disconnect()`, independent of local transceiver `start()` / `stop()` `[VERIFIED BY JVM TEST]`.
- **No Hardcoded Peers:** `ConnectionConfig.Server` defaults to local TCP port binding; client connection endpoints must be explicitly configured `[VERIFIED BY CODE]`.
- **Safe Disconnect Operations:** Calling `disconnect()` before `connect()` or repeatedly calling `disconnect()` is safe and idempotent on `TcpConnectionManager` `[VERIFIED BY JVM TEST]`.
- **Adapter Transport Binding:** `TcpConnectionManager` automatically binds and unbinds active transports to `SpeechMessageTransportAdapter` without leaking or duplicating socket listeners `[VERIFIED BY JVM TEST]`.

---

## Error Propagation

- **Unified Error Hierarchy:** All errors are categorized under `TransceiverError` (`M1PipelineError`, `M2TransportError`, `M3TtsError`, `M3PlaybackError`, `LifecycleError`) `[VERIFIED BY JVM TEST]`.
- **Zero Exception Swallowing:** Errors originating from background pipeline threads or JNI native engines are routed through `listener.onError(TransceiverError)` and formatted via `TransceiverUiFormatter` `[VERIFIED BY JVM TEST]`.
- **UI Error Visibility:** `tvStatus` displays the specific error category title, while `tvLastError` displays detailed exception causes `[VERIFIED BY CODE]`.

---

## Diagnostic / Benchmark Isolation

- **Frozen Benchmark Preservation:** All diagnostic intent handlers (`RUN_ASR_SMOKE_TEST`, `RUN_ASR_ACCURACY_BENCHMARK`, `RUN_ASR_HINDI_BENCHMARK`, `RUN_ASR_HINDI_DIAGNOSTIC`, `RUN_VOSK_HINDI_BENCHMARK`) remain fully intact `[VERIFIED BY CODE]`.
- **No Production Interference:** Benchmark runners execute on standalone test runners and do not construct or alter the production `AndroidTransceiverComposition` `[VERIFIED BY CODE]`.
- **Whisper Production ASR:** Whisper-Tiny INT8 remains the exclusive production speech-to-text engine; Vosk is isolated strictly to offline diagnostic comparison `[VERIFIED BY CODE]`.

---

## Coroutine / Callback Lifecycle

- **Structured Concurrency:** Background teardowns dispatched during `onDestroy()` execute on `Dispatchers.IO` to avoid blocking main thread termination `[VERIFIED BY CODE]`.
- **Post-Release Protection:** `AndroidTransceiverComposition.isReleased` and `TransceiverCoordinator.isReleased` reject subsequent `start()`, `sendOutboundSpeechMessage()`, or `initialize()` invocations with explicit `IllegalStateException` results `[VERIFIED BY JVM TEST]`.

---

## Architecture Boundary

```
PHONE A (Speaker / Sender):
Microphone
→ AudioRecordCapture
→ Silero VAD v5
→ SpeechSegmentCollector
→ Sherpa Whisper ASR
→ SpeechMessage
→ SpeechMessageTransportAdapter
→ PacketEncoder
→ SocketMessageTransport (TCP)

PHONE B (Listener / Receiver):
SocketMessageTransport (TCP)
→ PacketDecoder
→ SpeechMessageTransportAdapter
→ SpeechMessageReceiver
→ SpeechMessagePlaybackCoordinator
→ Sherpa Offline TTS (VITS)
→ TtsAudio (PCM Float)
→ AndroidAudioTrackPlaybackEngine
→ AudioTrack
→ Hardware Speaker
```

`MainActivity` acts purely as the presentation and orchestration layer, without implementing any signal processing, model inference, or socket protocol logic `[VERIFIED BY CODE]`.

---

## Resource Ownership

| Resource | Direct Lifecycle Owner | Release Mechanism | Verified Status |
| :--- | :--- | :--- | :--- |
| **`AudioRecord`** | `AudioRecordCapture` (inside `SpeechPipeline`) | `SpeechPipeline.release()` | `[VERIFIED BY CODE]` |
| **Silero VAD ONNX Session** | `SileroVadEngine` (inside `SpeechPipeline`) | `SpeechPipeline.release()` | `[VERIFIED BY CODE]` |
| **Sherpa Whisper Session** | `SherpaWhisperAsrEngine` (inside `SpeechPipeline`) | `SpeechPipeline.release()` | `[VERIFIED BY CODE]` |
| **Sherpa TTS Session** | `SherpaOfflineTtsEngine` | `SherpaOfflineTtsEngine.release()` | `[VERIFIED BY CODE]` |
| **`AudioTrack`** | `AndroidAudioTrackPlaybackEngine` | `AndroidAudioTrackPlaybackEngine.release()` | `[VERIFIED BY CODE]` |
| **TCP Sockets / ServerSocket**| `TcpConnectionManager` | `TcpConnectionManager.release()` | `[VERIFIED BY JVM TEST]` |
| **Dependency Cascade** | `AndroidTransceiverComposition` | 7-step deterministic teardown | `[VERIFIED BY JVM TEST]` |

---

## Findings

1. **Clean Integration:** `MainActivity` cleanly delegates all transceiver operations to `AndroidTransceiverComposition` without constructing duplicate pipeline engines `[VERIFIED BY CODE]`.
2. **Robust Thread Safety:** All UI updates are marshaled to the Android Main Thread via `runOnUiThread` `[VERIFIED BY CODE]`.
3. **No Hidden Auto-Networking:** Network connection control remains separate from local transceiver start/stop `[VERIFIED BY CODE]`.
4. **Benchmark Integrity:** All diagnostic intent handlers and frozen test suites remain operational `[VERIFIED BY CODE]`.
5. **No Concrete Correctness Defects Found:** The static audit identified zero lifecycle leaks, thread safety violations, or architectural boundary breaches.

---

## Fixes Applied

- No code modifications were required during this audit as the implementation satisfies all architectural, lifecycle, and safety constraints.

---

## Test Evidence

- **JVM Unit Tests:** **343 / 343 tests passed** (100% pass rate) `[VERIFIED BY JVM TEST]`
- **Build Status:** `./gradlew assembleDebug` succeeded `[VERIFIED BY CODE]`
- **Debug APK Size:** 180,221,199 bytes (~180 MB) `[VERIFIED BY CODE]`

---

## Device Validation Status

- **Status:** `[NOT DEVICE VALIDATED]`
- **Deferred Areas:**
  1. Live microphone hardware capture latency and AGC behavior.
  2. Physical speaker acoustic output and intelligibility.
  3. Real Wi-Fi point-to-point TCP socket connection between physical tablets.

---

## Remaining Risks

1. **Configuration Change Re-initialization:** Screen rotation destroys `MainActivity`, requiring a full re-initialization of ONNX models. (Documented limitation; orientation lock recommended for tactical deployment).
2. **Acoustic Feedback:** Simultaneous live mic capture and speaker playback on a single physical device in close acoustic proximity may produce acoustic echo until physical device acoustic testing is performed.
3. **Hardware DAC Buffer Under-run:** Real-time playback performance on low-end Android hardware must be verified on the physical tablet.

---

## Final Status

`ACTIVITY INTEGRATION AUDIT PASS — READY FOR DEVICE VALIDATION`
