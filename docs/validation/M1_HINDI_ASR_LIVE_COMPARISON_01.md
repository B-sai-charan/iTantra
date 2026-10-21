# Diagnostic Model Comparison Report: Vosk Hindi vs Whisper Tiny INT8 on Live Microphone Audio

> **DIAGNOSTIC REPLAY COMPARISON ONLY — NOT A PRODUCTION ASR CHANGE**
>
> This document records the empirical results of comparing the existing offline Vosk Hindi ASR engine against the reference Whisper Tiny INT8 engine on the exact captured live Hindi microphone audio WAV (`scratch/live_hindi_forensic_01.wav`).
> **Production ASR engine, Whisper models, Silero VAD, AudioRecord configuration, SpeechSegmentCollector, and frozen benchmarks remain completely untouched.**

---

## 1. Objective

To determine whether the existing Vosk Hindi ASR implementation can recognize the **SAME** captured live Hindi microphone WAV (`scratch/live_hindi_forensic_01.wav`) that Whisper Tiny INT8 failed on (`"ndo ndo ndo ndo"`).

---

## 2. Existing Vosk Configuration

The project's offline Vosk Hindi implementation was inspected prior to replay execution:

| Property | Value | Evidence Category |
| :--- | :--- | :--- |
| **MODEL IDENTITY** | `vosk-model-small-hi-0.22` | **FACT** |
| **OFFICIAL SOURCE** | Alpha Cephei (`https://alphacephei.com/vosk/models`) | **FACT** |
| **PHYSICAL PATH ON DEVICE** | `/storage/emulated/0/Android/data/org.itantra.app/files/models/vosk-model-small-hi-0.22` | **FACT** |
| **EXTRACTED MODEL SIZE** | `82,084,166` bytes (82.08 MB across 15 files in 5 directories) | **FACT** |
| **TARGET LANGUAGE** | Hindi (`hi` / Native Devanagari script) | **FACT** |
| **SAMPLE RATE** | 16,000 Hz (16 kHz) | **FACT** |
| **INPUT FORMAT** | Linear PCM 16-bit little-endian (PCM16), Mono (1 channel) | **FACT** |
| **INITIALIZATION METHOD** | `org.vosk.Model(modelPath)` & `org.vosk.Recognizer(model, 16000.0f)` | **FACT** |
| **DECODING CONFIGURATION** | Kaldi HCLG FST decoder, `recognizer.setWords(true)`, `recognizer.finalResult` | **FACT** |
| **ANDROID INTEGRATION** | `vosk-android-0.3.75.aar` (`app/libs/vosk-android-0.3.75.aar`, SHA-256: `AB2F8B91AC8051561AA325546B35FED9A68B36B8121BAC5C6FB927525C4ADFAD`) | **FACT** |
| **DIRECT PCM16 ACCEPTANCE** | Yes (`recognizer.acceptWaveForm(ShortArray, Int)` natively accepts PCM16 samples) | **FACT** |

---

## 3. Input Audio

The exact forensic audio segment captured during physical live microphone test on Lenovo TB-X6C6F (`HNP097HQ`) was used as the single input:

| Parameter | Value | Evidence Category |
| :--- | :--- | :--- |
| **SOURCE WAV FILE** | `scratch/live_hindi_forensic_01.wav` (and `files/live_hindi_forensic_01.wav` on device) | **FACT** |
| **SHA-256 HASH** | `E6DB9B8542930B8737AA671C109A972EF361DCCD4EA249E8D78CC51E69EC13E8` | **FACT** |
| **PRESCRIBED REFERENCE** | `"नमस्ते, यह iTantra का हिंदी परीक्षण है।"` | **FACT** |
| **NORMALIZED REFERENCE** | `"नमस्ते यह itantra का हिंदी परीक्षण है"` (7 words, 31 characters) | **FACT** |
| **SAMPLING RATE** | 16,000 Hz | **FACT** |
| **CHANNELS** | 1 (Mono) | **FACT** |
| **AUDIO ENCODING** | Signed 16-bit Linear PCM (PCM16, Little-Endian) | **FACT** |
| **SAMPLE COUNT** | 33,920 samples | **FACT** |
| **AUDIO DURATION** | 2,120 ms (2.120 seconds) | **FACT** |
| **FILE SIZE** | 67,884 bytes (44-byte RIFF header + 67,840 PCM bytes) | **FACT** |
| **RMS LEVEL** | -40.85 dBFS (RMS: 295.34 / 32768) | **MEASURED RESULT** |
| **PEAK AMPLITUDE** | 1,707 (-25.67 dBFS) | **MEASURED RESULT** |
| **CLIPPING SAMPLES** | 0 samples (0.000%) | **MEASURED RESULT** |
| **ZERO SAMPLES** | 1,849 samples (5.451%) | **MEASURED RESULT** |
| **GAIN / FILTERING APPLIED** | None (1.0x raw audio fed directly) | **FACT** |

---

## 4. Whisper Reference

Reference performance of the production Whisper Tiny INT8 engine on this exact WAV (from `M1-LIVE-MIC-FORENSIC-01` and `M1-LIVE-MIC-GAIN-AB-01`):

| Parameter | Value | Evidence Category |
| :--- | :--- | :--- |
| **ENGINE / MODEL** | `SherpaWhisperAsrEngine` / `sherpa-onnx-whisper-tiny` (INT8 quantized) | **FACT** |
| **RAW HYPOTHESIS** | `"ndo ndo ndo ndo"` | **MEASURED RESULT** |
| **NORMALIZED HYPOTHESIS** | `"ndo ndo ndo ndo"` | **MEASURED RESULT** |
| **WORD ERROR RATE (WER)** | **100.0%** (S=4, D=3, I=0, H=0, N=7) | **MEASURED RESULT** |
| **CHARACTER ERROR RATE (CER)** | **95.5%** | **MEASURED RESULT** |
| **EXACT MATCH** | `false` | **MEASURED RESULT** |
| **INFERENCE TIME** | 2,714 ms &ndash; 2,813 ms | **MEASURED RESULT** |
| **REAL-TIME FACTOR (RTF)** | 1.280 &ndash; 1.327 | **MEASURED RESULT** |
| **RUNTIME STATUS** | Success (Clean execution, hallucinated token repetition) | **MEASURED RESULT** |

---

## 5. Vosk Result

Empirical result of feeding the exact same WAV into the offline Vosk Hindi model on physical hardware (Lenovo TB-X6C6F, Android 12, `arm64-v8a`):

| Parameter | Value | Evidence Category |
| :--- | :--- | :--- |
| **ENGINE / MODEL** | `VoskBenchmarkRunner` / `vosk-model-small-hi-0.22` | **FACT** |
| **MODEL LOAD TIME** | **3,439 ms** (3.439 seconds) | **MEASURED RESULT** |
| **INFERENCE TIME** | **2,853 ms** (2.853 seconds) | **MEASURED RESULT** |
| **REAL-TIME FACTOR (RTF)** | **1.3458** | **MEASURED RESULT** |
| **RAW JSON OUTPUT** | `{\n  "text" : ""\n}` | **MEASURED RESULT** |
| **RAW HYPOTHESIS** | `""` (Empty string / 0 words decoded) | **MEASURED RESULT** |
| **NORMALIZED HYPOTHESIS** | `""` | **MEASURED RESULT** |
| **EXACT MATCH** | `false` | **MEASURED RESULT** |
| **WORD ALIGNMENT** | S=0, D=7, I=0, H=0, Reference Words ($N$)=7 | **MEASURED RESULT** |
| **WORD ERROR RATE (WER)** | **100.00%** (7 deletions / 7 reference words) | **MEASURED RESULT** |
| **CHAR ALIGNMENT** | S=0, D=31, I=0, H=0, Reference Characters ($N$)=31 | **MEASURED RESULT** |
| **CHARACTER ERROR RATE (CER)** | **100.00%** (31 char deletions / 31 reference characters) | **MEASURED RESULT** |
| **PEAK / STEADY MEMORY** | Total JVM heap used: ~4.8 MB; Native Kaldi resident memory: unprofiled | **NOT MEASURED** |
| **RUNTIME STATUS** | Success (Clean execution, 0 crashes, 0 JNI exceptions, decoded empty text) | **MEASURED RESULT** |

---

## 6. Direct Comparison

| Engine | Model | Hypothesis | WER | CER | RTF | Runtime Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Sherpa ONNX Whisper** | `sherpa-onnx-whisper-tiny` (INT8) | `"ndo ndo ndo ndo"` | **100.0%** | **95.5%** | **1.280** | Success (Hallucination) |
| **Vosk Hindi** | `vosk-model-small-hi-0.22` | `""` | **100.0%** | **100.0%** | **1.346** | Success (Empty transcript) |

---

## 7. Interpretation

### Classification:
**B. Vosk does not materially improve recognition.**

### Required Determination:
**"Evidence does not support Vosk as the immediate remedy."**

### Technical Root-Cause Analysis:
1. **Acoustic Level Floor:** The captured live audio has an extremely low amplitude level (-40.85 dBFS RMS, peak sample 1707 out of 32767).
2. **Failure Modes Differ by Architecture:**
   - **Whisper (Attention-based Encoder-Decoder):** When presented with low-energy, near-floor speech frames, Whisper hallucinates repetitive non-Devanagari subword tokens (`"ndo ndo ndo ndo"`).
   - **Vosk / Kaldi (HMM-GMM / DNN-HCLG Acoustic Model):** When acoustic feature frames fall below energy/likelihood beam thresholds, Kaldi prunes all active decoding paths and outputs an empty transcript (`""`).
3. **Conclusion:** Swapping Whisper for Vosk does not solve live microphone Hindi recognition because both engines fail on this captured live microphone audio signal.

---

## 8. Production Changes

| Component | Production State | Evidence Category |
| :--- | :--- | :--- |
| **WHISPER PRODUCTION PATH** | **UNCHANGED** (`SherpaWhisperAsrEngine` remains active) | **FACT** |
| **PRODUCTION ASR ENGINE** | **UNCHANGED** | **FACT** |
| **VAD ENGINE & THRESHOLDS** | **UNCHANGED** (Silero VAD v5 active) | **FACT** |
| **AUDIORECORD CONFIGURATION** | **UNCHANGED** | **FACT** |
| **SPEECH SEGMENT COLLECTOR** | **UNCHANGED** | **FACT** |
| **PRODUCTION PREPROCESSING / GAIN** | **NONE ADDED** | **FACT** |
| **FROZEN BENCHMARK SUITES** | **UNCHANGED** | **FACT** |

---

## 9. Test Results

- **Unit Test Suite:** **348 / 348 unit tests passing** cleanly (`.\gradlew testDebugUnitTest`).
- **Physical Device Execution:** Executed on Lenovo TB-X6C6F (`HNP097HQ`) via diagnostic intent `org.itantra.action.RUN_VOSK_LIVE_COMPARISON`.
- **Runtime Exceptions:** 0 exceptions, 0 native crashes.

---

## 10. Next Investigation

Since neither Whisper nor Vosk can recognize the captured audio in its raw state (-40.85 dBFS RMS), the next diagnostic investigations should examine the **physical capture chain**:
1. Investigate the Android `AudioRecord` input source configuration (e.g. `MediaRecorder.AudioSource.MIC` vs `VOICE_RECOGNITION` vs `VOICE_COMMUNICATION` vs `UNPROCESSED`).
2. Investigate hardware microphone gain, AGC capabilities, or device-specific capture level calibration on target tablets.
