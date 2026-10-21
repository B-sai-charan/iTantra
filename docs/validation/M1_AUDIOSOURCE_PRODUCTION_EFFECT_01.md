# M1 AudioSource Production Effect Diagnostic (Task m1-audiosource-production-effect-01)

## Objective

Determine whether changing **ONLY** the `AudioRecord` `AudioSource` from the current production source (`MediaRecorder.AudioSource.VOICE_RECOGNITION`) to `MediaRecorder.AudioSource.MIC` materially changes live production-path Whisper recognition on the Lenovo TB-X6C6F tablet.

This is a **controlled diagnostic experiment only**.

---

## Controlled Reference Sentence

Both AudioSource conditions evaluated the exact identical spoken sentence:

```text
"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
```

---

## 1. Files Changed / Created

* **Diagnostic Runner**: `app/src/main/java/org/itantra/core/stt/diagnostic/AudioSourceProductionEffectRunner.kt`
* **Activity Control Wire**: `app/src/main/java/org/itantra/MainActivity.kt` (added intent action `org.itantra.action.RUN_AUDIOSOURCE_PRODUCTION_EFFECT`)
* **Documentation**: `docs/validation/M1_AUDIOSOURCE_PRODUCTION_EFFECT_01.md`
* **Production Code**: **0 lines of production code or configuration changed**. `AudioCaptureConfig.kt`, `AudioRecordCapture.kt`, `SpeechPipeline.kt`, VAD, Whisper, model assets, and TTS were strictly preserved.

---

## 2. Experimental Configuration & Path Inspection

### Pre-Run Inspection of Production Capture Implementation:
1. `AudioCaptureConfig.kt`:
   - Default `audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION` (value 6).
   - Sample rate: 16,000 Hz, Channel config: `AudioFormat.CHANNEL_IN_MONO`, Audio format: `AudioFormat.ENCODING_PCM_16BIT`.
   - Frame duration: 20 ms (320 samples / 640 bytes per chunk).
2. `AudioRecordCapture.kt`:
   - Production AudioRecord wrapper operating on dedicated `THREAD_PRIORITY_URGENT_AUDIO` thread.
   - Zero-allocation hot read loop reading 320-short frames and dispatching to `AudioCaptureListener`.

### Controlled Difference Between Conditions:
The **ONLY** difference between Condition A and Condition B is the `audioSource` parameter passed into `AudioCaptureConfig`:
* **Condition A (Production Source)**: `AudioCaptureConfig(audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION)` (value 6)
* **Condition B (Alternate Source)**: `AudioCaptureConfig(audioSource = MediaRecorder.AudioSource.MIC)` (value 1)
* **All other parameters identical**: Sample rate (16 kHz), mono, PCM16, buffer sizing, thread priority, read loop, and Whisper decoding parameters.

---

## 3. WAV Hashes & Audio Signal Statistics

| Parameter | Condition A: Production Source (`VOICE_RECOGNITION`) | Condition B: Alternate Source (`MIC`) | Comparison / Delta |
|---|---|---|---|
| **AudioSource** | `MediaRecorder.AudioSource.VOICE_RECOGNITION` (6) | `MediaRecorder.AudioSource.MIC` (1) | Code difference: single enum integer |
| **Output File** | `production_effect_voice_recognition.wav` | `production_effect_mic.wav` | |
| **File Size** | 253,484 bytes | 253,484 bytes | Identical byte length |
| **SHA-256 Checksum** | `6d60ee01b9ac8505712fc13b384332c059c6295c78be154ba27a77abfac90727` | `b1cc657b559ec08b9e268f8b9bf8e390a449ba9aeb2eb96faa29c4af89fa0451` | Distinct raw captures |
| **Sample Rate** | 16,000 Hz | 16,000 Hz | Identical |
| **Channels / Encoding** | 1 (Mono) / 16-bit Signed Linear PCM | 1 (Mono) / 16-bit Signed Linear PCM | Identical |
| **Sample Count / Duration** | 126,720 samples (7,920 ms / 7.920 s) | 126,720 samples (7,920 ms / 7.920 s) | Identical |
| **Min / Max Sample** | -3,065 / +2,823 | -10,072 / +10,427 | MIC peak is $3.40\times$ higher |
| **Peak Amplitude** | 3,065 (9.35%) | 10,427 (31.82%) | +22.47% full scale |
| **Mean / DC Offset** | -0.9632 | -2.0723 | Negligible DC offset in both |
| **RMS Amplitude** | 425.94 | 1,658.88 | MIC RMS is $3.89\times$ higher |
| **RMS dBFS** | **-37.72 dBFS** | **-25.91 dBFS** | **+11.81 dB higher level on MIC** |
| **Zero Percentage** | 0.13% (162 zeros) | 0.03% (33 zeros) | Minimal |
| **Clipping Count** | 0 (0 min, 0 max) | 0 (0 min, 0 max) | 0 clipping on both |
| **100 ms RMS Distribution** | | | |
| * Min Window RMS | 157.95 (-46.34 dBFS) | 704.04 (-33.36 dBFS) | +12.98 dB |
| * Median Window RMS | 359.57 (-39.19 dBFS) | 1,165.55 (-28.98 dBFS) | +10.21 dB |
| * Max Window RMS | 1,077.82 (-29.66 dBFS) | 3,312.96 (-19.90 dBFS) | +9.76 dB |
| * Dynamic Contrast (Max - Min dBFS) | **16.68 dB** | **13.46 dB** | -3.22 dB |
| **Spectral Characteristics** | | | |
| * Spectral Centroid | **2,673.24 Hz** | **1,210.21 Hz** | **-1,463.03 Hz lower on MIC** |
| * Spectral Bandwidth | 1,745.48 Hz | 1,071.79 Hz | -673.69 Hz narrower on MIC |
| * 85% Rolloff Frequency | 4,518.83 Hz | 2,201.32 Hz | -2,317.51 Hz lower on MIC |
| **FFT Band Energy Percentages** | | | |
| * 0 – 300 Hz | 3.79% | **27.27%** | **$7.19\times$ higher low-frequency content on MIC** |
| * 300 – 1,000 Hz | 35.78% | 43.35% | +7.57% |
| * 1,000 – 3,000 Hz | 27.68% | 22.99% | -4.69% |
| * 3,000 – 6,000 Hz | 26.82% | 6.03% | Attenuated on MIC ($0.22\times$) |
| * 6,000 – 8,000 Hz | 5.93% | 0.37% | Attenuated on MIC ($0.06\times$) |

---

## 4. Raw Results for All 6 Whisper ASR Runs

* **Engine**: Production `SherpaWhisperAsrEngine` (Whisper-Tiny INT8, greedy search, `language = "en"`)
* **Reference Text**: `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`

### Condition A: Production Source (`VOICE_RECOGNITION`)

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | Audio Duration | RTF | Errors |
|---|---|---|---:|---:|---|---:|---:|---:|---|
| **Pass 1** | `"(water running)"` | `"water running"` | 100.00% | 90.80% | `false` | 2,514 ms | 7,920 ms | 0.3174 | None |
| **Pass 2** | `"(water running)"` | `"water running"` | 100.00% | 90.80% | `false` | 2,373 ms | 7,920 ms | 0.2996 | None |
| **Pass 3** | `"(water running)"` | `"water running"` | 100.00% | 90.80% | `false` | 2,378 ms | 7,920 ms | 0.3003 | None |
| **Mean** | — | — | **100.00%** | **90.80%** | **0 / 3 (0%)** | **2,421.7 ms** | **7,920 ms** | **0.3058** | **0 errors** |

---

### Condition B: Alternate Source (`MIC`)

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | Audio Duration | RTF | Errors |
|---|---|---|---:|---:|---|---:|---:|---:|---|
| **Pass 1** | `"Yeah, it's quite."` | `"yeah its quite"` | 100.00% | 87.36% | `false` | 2,534 ms | 7,920 ms | 0.3199 | None |
| **Pass 2** | `"Yeah, it's quite."` | `"yeah its quite"` | 100.00% | 87.36% | `false` | 2,682 ms | 7,920 ms | 0.3386 | None |
| **Pass 3** | `"Yeah, it's quite."` | `"yeah its quite"` | 100.00% | 87.36% | `false` | 2,608 ms | 7,920 ms | 0.3293 | None |
| **Mean** | — | — | **100.00%** | **87.36%** | **0 / 3 (0%)** | **2,608.0 ms** | **7,920 ms** | **0.3293** | **0 errors** |

---

## 5. Summary WER / CER / RTF Comparison Table

| Condition | AudioSource | RMS dBFS | Spectral Centroid | Raw Hypothesis | WER | CER | Exact Match | Mean RTF |
|---|---|---:|---:|---|---:|---:|---|---:|
| **Condition A (Production)** | `VOICE_RECOGNITION` (6) | -37.72 dBFS | 2,673.24 Hz | `"(water running)"` | **100.00%** | **90.80%** | **0 / 3 (0%)** | **0.3058** |
| **Condition B (Alternate)** | `MIC` (1) | -25.91 dBFS | 1,210.21 Hz | `"Yeah, it's quite."` | **100.00%** | **87.36%** | **0 / 3 (0%)** | **0.3293** |

---

## 6. Interpretation & Production Decision

### Does the result support changing production `AudioSource`?
**NO.**

While switching from `VOICE_RECOGNITION` to `MIC` yields a +11.81 dB gain increase and enriches low-frequency vocal energy ($7.19\times$ higher in 0–300 Hz), **both sources remained at 100.00% WER with 0 / 3 exact matches**.

* On `VOICE_RECOGNITION`, Whisper hallucinated a non-speech sound token `"(water running)"` due to high-pass attenuation.
* On `MIC`, Whisper transcribed speech tokens (`"Yeah, it's quite."`), but completely failed to recognize the actual spoken sentence (`"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`).

Therefore, changing `AudioSource` alone is **not** a standalone fix for the live microphone recognition failure on this device.

---

## 7. Fact / Inference / Unknown Breakdown

### FACT (Directly Measured & Verified)
1. Changing ONLY `AudioSource` from `VOICE_RECOGNITION` (6) to `MIC` (1) increased recorded RMS level by +11.81 dB (-25.91 dBFS vs -37.72 dBFS).
2. `MIC` audio contained $27.27\%$ of energy in 0–300 Hz compared to only $3.79\%$ on `VOICE_RECOGNITION`.
3. `VOICE_RECOGNITION` had a spectral centroid of 2,673.24 Hz versus 1,210.21 Hz on `MIC`.
4. Whisper decoding on `VOICE_RECOGNITION` produced `"(water running)"` across all 3 passes (**100.00% WER**).
5. Whisper decoding on `MIC` produced `"Yeah, it's quite."` across all 3 passes (**100.00% WER**).
6. Neither source produced an exact match (0/3 on both conditions).

### INFERENCE (Logically Derived from Facts)
1. The Lenovo OEM HAL applies aggressive high-pass filtering / noise profiling on `VOICE_RECOGNITION` that reduces speech fundamentals.
2. `AudioSource.MIC` provides a higher-gain, frequency-rich raw audio signal.
3. However, raw `MIC` audio without further acoustic or front-end conditioning is insufficient to achieve correct ASR decoding on Whisper-Tiny for this device.

### UNKNOWN (Not Proven / Outside Scope)
1. Whether a combination of `MIC` source with front-end gain normalization or spectral conditioning can achieve acceptable WER.
2. Whether room acoustics, tablet microphone physical enclosure, or speech delivery rate is the primary remaining acoustic gap.

---

## 8. Build & Test Results

* **Unit Test Count**: `343 / 343 passing` (`0 failures`, `0 errors`, `0 skipped`).
* **Unit Test Command**: `.\gradlew testDebugUnitTest` — `BUILD SUCCESSFUL`.
* **Assemble Debug Command**: `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.

---

## Classification

**NO MATERIAL ASR IMPROVEMENT**
