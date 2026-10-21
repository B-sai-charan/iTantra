# M1 Android Audio Path Control Diagnostic (Task M1-ANDROID-AUDIO-PATH-CONTROL-16)

**Document ID:** `M1-ANDROID-AUDIO-PATH-CONTROL-16`  
**Experiment Date:** 2026-09-28  
**Device Under Test:** Device A (Lenovo Smart Tab M10 FHD Plus / TB-X6C6F, Android 12, Build API 31, arm64-v8a)  
**Status:** **COMPLETE & VERIFIED (DIAGNOSTIC-ONLY)**  

---

## 1. Objective

The objective of this diagnostic experiment is to perform a controlled repeatability evaluation of Android's hardware audio capture path across three core `AudioRecord.AudioSource` input presets under standard `AudioManager.MODE_NORMAL`, determining whether input source routing configuration materially impacts speech capture and offline Whisper ASR performance.

> [!IMPORTANT]
> **Diagnostic Isolation Directives Maintained:**
> - Zero modification to production code (`app/src/main/`).
> - Zero modification to production `AudioRecordCapture` configuration (`VOICE_RECOGNITION`).
> - Zero modification to `SileroVadEngine`, `SherpaWhisperAsrEngine`, Vosk, M2/M3 transport, or TTS.
> - Zero DSP / audio processing applied (no gain, EQ, filtering, normalization, resampling, trimming, or padding).
> - Three fixed 8.000s unsegmented raw captures (128,000 samples @ 16 kHz Mono PCM16).

---

## 2. Test Device & Platform Configuration

- **Device Model:** Lenovo Smart Tab M10 FHD Plus (`Lenovo TB-X6C6F`)
- **Serial / Device ID:** `HNP097HQ`
- **Platform OS:** Android 12 (API Level 31)
- **Architecture:** `arm64-v8a`
- **Audio Mode:** `AudioManager.MODE_NORMAL` (0)
- **Routing Endpoints:** Built-in dual microphone array (`TYPE_BUILTIN_MIC`), speakerphone disabled, Bluetooth SCO/A2DP disabled, wired headset disconnected.

---

## 3. Prescribed Test Configurations & Audio Files

Spoken Reference Utterance:
> `"After early nightfall, the yellow lamps would light up here and there."` (12 words, 57 characters normalized)

| Test Profile | AudioRecord Source Parameter | AudioManager Mode | Output WAV File | File Size | Sample Count | SHA-256 Checksum |
| :--- | :--- | :--- | :--- | :---: | :---: | :--- |
| **Test A: VOICE_RECOGNITION** | `MediaRecorder.AudioSource.VOICE_RECOGNITION` (6) | `MODE_NORMAL` | `scratch/audio_path_control_voice_recognition.wav` | 256,044 B | 128,000 (8.000s) | `f9d7328dc9c63ada0a632e966121a4827a6f430a684b3c7a9d27d7ab7e9d5548` |
| **Test B: MIC** | `MediaRecorder.AudioSource.MIC` (1) | `MODE_NORMAL` | `scratch/audio_path_control_mic.wav` | 256,044 B | 128,000 (8.000s) | `d2eead0f2c1ea14e4945e611801239369802acfe5bd54223cef44b85edecd9a2` |
| **Test C: DEFAULT** | `MediaRecorder.AudioSource.DEFAULT` (0) | `MODE_NORMAL` | `scratch/audio_path_control_default.wav` | 256,044 B | 128,000 (8.000s) | `5fd0bd41a6cada093ace02871fb63c3eaf15b4cb7389f8d88be028e458ca6695` |
| **Clean Reference Fixture** | Benchmark Digital Asset | N/A (Direct Audio) | `scratch/speech_test.wav` | 212,044 B | 106,000 (6.625s) | `6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f` |

---

## 4. Signal & Spectral Measurements

Detailed acoustic and spectral measurements across all evaluated audio streams (25 ms Hann window, 10 ms hop):

| Metric | Test A: `VOICE_RECOGNITION` | Test B: `MIC` | Test C: `DEFAULT` | Clean Reference Fixture |
| :--- | :---: | :---: | :---: | :---: |
| **RMS Energy (dBFS)** | **-39.79 dBFS** | **-23.27 dBFS** | **-38.56 dBFS** | **-26.54 dBFS** |
| **Peak Amplitude (dBFS)** | -26.25 dBFS (1,595) | -8.75 dBFS (11,967) | -19.24 dBFS (3,576) | -5.43 dBFS (17,508) |
| **Crest Factor** | 4.75 (13.54 dB) | 5.32 (14.52 dB) | 9.24 (19.32 dB) | 5.38 (14.61 dB) |
| **Clipping Count / %** | 0 (0.00%) | 0 (0.00%) | 0 (0.00%) | 0 (0.00%) |
| **Zero Sample %** | 0.28% | 0.03% | 0.17% | 0.05% |
| **Zero Crossing Rate (ZCR)** | 3,777.4 /s | 2,239.0 /s | 2,003.8 /s | 1,407.9 /s |
| **Spectral Centroid** | 2,844.5 Hz | 1,957.1 Hz | 1,725.0 Hz | 1,311.4 Hz |
| **Spectral Flatness** | 0.1668 | 0.0521 | 0.0354 | 0.0289 |
| **10–90% Frame Energy DynRange** | **6.45 dB** | **7.90 dB** | **15.52 dB** | **37.87 dB** |
| **Band Energy 0–250 Hz** | 23.1% | **62.3%** | 20.6% | 23.3% |
| **Band Energy 250–500 Hz** | 33.6% | 17.8% | 34.8% | 32.0% |
| **Band Energy 500–1000 Hz** | 8.2% | 5.3% | 26.5% | 31.8% |
| **Band Energy 1000–2000 Hz** | 15.2% | 7.9% | 7.2% | 10.8% |
| **Band Energy 2000–4000 Hz** | 8.2% | 4.3% | 9.1% | 1.8% |
| **Band Energy 4000–8000 Hz** | 11.6% | 2.4% | 1.8% | 0.2% |

---

## 5. Whisper Tiny INT8 Inference Results

Evaluations conducted on Device A using `SherpaWhisperAsrEngine` (`language = "en"`, task = `"transcribe"`, greedy search):

| Audio Source Profile | Raw Hypothesis | Normalized Hypothesis | WER | CER | Latency | RTF | Status |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **Test A: `VOICE_RECOGNITION`** | `"[music]"` | `""` | **100.00%** | **94.74%** | 2,380 ms | 0.2975 | Success |
| **Test B: `MIC`** | `"(music playing)"` | `""` | **100.00%** | **91.23%** | 2,485 ms | 0.3106 | Success |
| **Test C: `DEFAULT`** | `"I don't know what to do. I don't know what to do. I don't know what to do. I don't know what to do. I don't know what to do. I don't know what to do."` | `"i don t know what to do i don t know what to do i don t know what to do i don t know what to do i don t know what to do i don t know what to do"` | **350.00%** | **150.88%** | 8,698 ms | 1.0873 | Success |
| **Clean Reference Fixture** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | **0.00%** | **0.00%** | 4,907 ms | 0.7407 | Success |

---

## 6. Primary Comparison Table

| Source Profile | RMS (dBFS) | Peak (dBFS) | Dynamic Range | Centroid (Hz) | Flatness | Whisper WER | CER | Latency / RTF |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **A. `VOICE_RECOGNITION`** | -39.79 | -26.25 | 6.45 dB | 2,844.5 | 0.1668 | **100.00%** | **94.74%** | 2,380 ms (0.298) |
| **B. `MIC`** | -23.27 | -8.75 | 7.90 dB | 1,957.1 | 0.0521 | **100.00%** | **91.23%** | 2,485 ms (0.311) |
| **C. `DEFAULT`** | -38.56 | -19.24 | 15.52 dB | 1,725.0 | 0.0354 | **350.00%** | **150.88%** | 8,698 ms (1.087) |
| **Clean Reference** | -26.54 | -5.43 | 37.87 dB | 1,311.4 | 0.0289 | **0.00%** | **0.00%** | 4,907 ms (0.741) |

---

## 7. Comparative Assessment Across Audio Sources

1. **`AudioRecord.AudioSource.MIC` vs `VOICE_RECOGNITION`:**
   - Selecting `MIC` increases raw broad-band capture gain (+16.5 dB RMS, -23.27 dBFS vs -39.79 dBFS on `VOICE_RECOGNITION`), but concentrates **62.3%** of its spectral energy in the low-frequency sub-band ($0\text{--}250\text{ Hz}$).
   - Despite the higher amplitude, Whisper Tiny INT8 fails completely (**100.00% WER**, transcribing `"(music playing)"`), indicating that the low-frequency rumble obscures formant intelligibility.
2. **`VOICE_RECOGNITION` Profile:**
   - Applies high-pass filtering (attenuating $0\text{--}250\text{ Hz}$ to 23.1%), but leaves the speech signal severely attenuated ($-39.79\text{ dBFS}$ RMS, dynamic range only $6.45\text{ dB}$), causing Whisper to output hallucinated non-speech tokens (`"[music]"`).
3. **`DEFAULT` Profile:**
   - Produces an intermediate acoustic profile ($-38.56\text{ dBFS}$ RMS, $15.52\text{ dB}$ dynamic range) with elevated 500–1000 Hz energy (26.5%), triggering a degenerate repetitive hallucination loop (**350.00% WER**).
4. **Clean Digital Reference Contrast:**
   - On `speech_test.wav`, Whisper achieves **0.00% WER** with a **37.87 dB dynamic range**, confirming that the Whisper decoder path itself operates with 100% accuracy when supplied with clean, well-modulated speech audio.

---

## 8. Prescribed Interpretation & Classification

In accordance with the diagnostic evaluation criteria:

> **Classification: Category B**  
> **"If source characteristics change but all sources remain near-catastrophic for ASR, source selection alone is insufficient."**

### Technical Rationale:
- Switching between `VOICE_RECOGNITION`, `MIC`, and `DEFAULT` materially alters physical signal metrics (shifting RMS from $-39.8\text{ dBFS}$ to $-23.3\text{ dBFS}$ and low-band energy from $23.1\%$ to $62.3\%$).
- However, **all three source presets fail near-catastrophically on ASR recognition** ($100.0\%\text{--}350.0\%\text{ WER}$ and 0 recognized words).
- Therefore, simply changing the `AudioRecord` source preset in Android software does not resolve the physical speech recognition failure.

---

## 9. Limitations

1. Measurements reflect Android 12 HAL audio processing behavior on the Lenovo TB-X6C6F hardware platform.
2. No auxiliary DSP (noise suppression, beamforming, AGC, or speech enhancement) was layered on top of the raw capture profiles.
3. Tests were conducted in standard room acoustics at normal conversational distance.

---

## 10. Build & System Integrity

### Production Code Status
```
Production code changed: NO
```
*(Zero modifications committed to `app/src/main/`, Gradle configurations, dependencies, models, or pipeline logic).*

### Unit Test Verification
```
Command: .\gradlew.bat testDebugUnitTest
Result: BUILD SUCCESSFUL
Test Count: 365 tests executed, 0 failures, 0 errors, 0 skipped
```
