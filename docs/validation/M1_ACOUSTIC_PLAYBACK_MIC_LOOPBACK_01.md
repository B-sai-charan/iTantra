# M1 Acoustic Playback Microphone Loopback Diagnostic (Task m1-acoustic-playback-mic-loopback-01)

## 1. Experimental Setup

This diagnostic evaluates whether the live microphone ASR degradation on Device A (`HNP097HQ`) persists when the **exact known-good EN-001 speech recording** (`speech_test.wav`) is reproduced through an external acoustic speaker and captured by the tablet's unmodified production `AudioRecordCapture` path.

This isolates human speaker variance (pronunciation, speaking rate, pitch) while preserving the physical acoustic transmission channel (speaker transducer $\rightarrow$ air medium $\rightarrow$ tablet microphone port $\rightarrow$ Android AudioRecord $\rightarrow$ Sherpa Whisper-Tiny).

---

## 2. Device & Software Configuration

* **Target Device**: Device A (`HNP097HQ`, Lenovo TB-X6C6F, Android 12, API 31, arm64-v8a)
* **Production AudioCapture Configuration**:
  - `sampleRateInHz`: 16,000 Hz
  - `channelConfig`: `AudioFormat.CHANNEL_IN_MONO` (Mono)
  - `audioFormat`: `AudioFormat.ENCODING_PCM_16BIT`
  - `audioSource`: `MediaRecorder.AudioSource.VOICE_RECOGNITION` (value 6)
  - `chunkDurationMs`: 20 ms (320 samples / frame)
* **Thread Priority**: `Process.THREAD_PRIORITY_URGENT_AUDIO`
* **ASR Engine**: Production `SherpaWhisperAsrEngine` (Sherpa-ONNX Whisper-Tiny INT8, greedy search, `lang = "en"`)

---

## 3. Reference Asset Integrity

* **Asset Path**: `app/src/main/assets/speech_test.wav`
* **Expected SHA-256 Checksum**:
  ```text
  6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f
  ```
* **Measured SHA-256 Checksum**:
  ```text
  6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f
  ```
* **Verification Status**: **VERIFIED MATCH** (106,000 samples, 6,625 ms, 212,044 bytes, 16 kHz Mono PCM16).

---

## 4. Physical Playback Setup

* **Playback Source**: External speaker transducer reproducing `speech_test.wav` at normal conversational speech level (~65–70 dBA SPL).
* **Playback Speaker Distance**: Approximately 18 cm from Device A's primary top-edge microphone port.
* **Orientation**: Direct line-of-sight facing Device A microphone port at 0-degree incident angle.
* **Acoustic Environment**: Real indoor room with standard ambient background noise (~35–40 dBA).
* **Playback Mechanism**: Exact raw PCM playback of the frozen `speech_test.wav` reference audio.

---

## 5. Captured WAV Integrity Verification

Three consecutive acoustic playback captures were recorded on Device A:

| Repetition | Captured WAV File Name | SHA-256 Checksum | File Size | Sample Count / Duration | Format |
|---|---|---|---|---|---|
| **Rep 1** | `acoustic_loopback_rep_1.wav` | `983c11dc374aa9d84813c5a4d488adce0c13f81e2b03849381eeb80398bd2424` | 253,484 B | 126,720 (7.920 s) | 16 kHz Mono PCM16 |
| **Rep 2** | `acoustic_loopback_rep_2.wav` | `92adfda9d3e610318f4ff469c15f3fc4eaafeb4d4c19feedda3f0a9fce713ff1` | 253,484 B | 126,720 (7.920 s) | 16 kHz Mono PCM16 |
| **Rep 3** | `acoustic_loopback_rep_3.wav` | `f011c0a37b46f9c5e9d11e3427cfbe104c9776d4a4ef6a620ded679862f49c74` | 253,484 B | 126,720 (7.920 s) | 16 kHz Mono PCM16 |

---

## 6. Signal Statistics (Identical Analysis Parameters)

Computed across 100 ms sliding windows (1,600 samples) and 512-point FFT (256-sample hop):

| Signal Dimension | Frozen Reference (`speech_test.wav`) | Loopback Rep 1 | Loopback Rep 2 | Loopback Rep 3 | Loopback Mean |
|---|---|---|---|---|---|
| **Duration** | 6,625 ms | 7,920 ms | 7,920 ms | 7,920 ms | 7,920 ms |
| **Sample Count** | 106,000 | 126,720 | 126,720 | 126,720 | 126,720 |
| **Min / Max Sample** | -9,631 / +17,529 | -3,741 / +3,390 | -3,566 / +3,111 | -5,313 / +6,201 | — |
| **Peak Amplitude** | 17,529 (53.50%) | 3,741 (11.42%) | 3,566 (10.88%) | 6,201 (18.92%) | 4,502.7 (13.74%) |
| **Overall RMS Amplitude** | 1,543.09 | 344.04 | 324.51 | 453.55 | 374.03 |
| **RMS dBFS** | **-26.54 dBFS** | **-39.58 dBFS** | **-40.08 dBFS** | **-37.18 dBFS** | **-38.95 dBFS** |
| **Clipping Count** | 0 | 0 | 0 | 0 | 0 |
| **Mean DC Offset** | -3.2328 | -0.9999 | -0.9713 | -0.9793 | -0.9835 |
| **Dynamic Contrast (100 ms)** | **44.69 dB** | **16.20 dB** | **16.15 dB** | **20.51 dB** | **17.62 dB** |
| **Zero Crossing Rate (Mean)** | 0.0879 | 0.1847 | 0.1660 | 0.2085 | 0.1864 |
| **Spectral Centroid** | **657.46 Hz** | **1,234.91 Hz** | **1,092.77 Hz** | **1,433.54 Hz** | **1,253.74 Hz** |
| **Spectral Bandwidth** | 571.73 Hz | 1,083.37 Hz | 922.40 Hz | 1,132.55 Hz | 1,046.11 Hz |
| **85% Rolloff Frequency** | 1,009.10 Hz | 2,096.16 Hz | 1,851.17 Hz | 2,359.22 Hz | 2,102.18 Hz |
| **FFT Band Energy Distributions** | | | | | |
| * 0 – 300 Hz | **26.02%** | **6.56%** | **9.16%** | **4.65%** | **6.79%** |
| * 300 – 1,000 Hz | 61.30% | 61.78% | 60.80% | 60.59% | 61.06% |
| * 1,000 – 3,000 Hz | 12.06% | 23.53% | 25.98% | 28.92% | 26.14% |
| * 3,000 – 6,000 Hz | 0.58% | 6.66% | 3.70% | 5.24% | 5.20% |
| * 6,000 – 8,000 Hz | 0.04% | 1.48% | 0.36% | 0.60% | 0.81% |

---

## 7. Whisper ASR Results (Baseline vs. Loopback)

* **Reference Sentence**: `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`

### Baseline Reference Evaluation (Direct File Input, 3 Repetitions)

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | RTF | Errors |
|---|---|---|---:|---:|---|---:|---:|---|
| **Pass 1** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 4,746 ms | 0.7164 | None |
| **Pass 2** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 4,899 ms | 0.7395 | None |
| **Pass 3** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 4,881 ms | 0.7368 | None |
| **Mean** | — | — | **0.00%** | **0.00%** | **3 / 3 (100%)** | **4,842.0 ms** | **0.7309** | **0 errors** |

---

### Acoustic Playback Loopback Evaluation (3 Physical Captures)

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | RTF | Errors |
|---|---|---|---:|---:|---|---:|---:|---|
| **Rep 1** | `"[inaudible]"` | `"inaudible"` | 100.00% | 91.95% | `false` | 2,295 ms | 0.2898 | None |
| **Rep 2** | `"(speaking in foreign language)"` | `"speaking in foreign language"` | 100.00% | 85.06% | `false` | 2,801 ms | 0.3537 | None |
| **Rep 3** | `"Thank you very much."` | `"thank you very much"` | 100.00% | 88.51% | `false` | 2,720 ms | 0.3434 | None |
| **Mean** | — | — | **100.00%** | **88.51%** | **0 / 3 (0%)** | **2,605.3 ms** | **0.3290** | **0 errors** |

---

## 8. Baseline vs. Loopback Comparison Table

| Condition | RMS dBFS | Dynamic Contrast | Spectral Centroid | 0–300 Hz Energy % | WER | CER | Exact Match | Mean RTF |
|---|---:|---:|---:|---:|---:|---:|---|---:|
| **Baseline Reference** | -26.54 dBFS | 44.69 dB | 657.46 Hz | 26.02% | **0.00%** | **0.00%** | **3 / 3 (100%)** | **0.7309** |
| **Acoustic Loopback (Rep 1)** | -39.58 dBFS | 16.20 dB | 1,234.91 Hz | 6.56% | **100.00%** | **91.95%** | **0 / 3 (0%)** | **0.2898** |
| **Acoustic Loopback (Rep 2)** | -40.08 dBFS | 16.15 dB | 1,092.77 Hz | 9.16% | **100.00%** | **85.06%** | **0 / 3 (0%)** | **0.3537** |
| **Acoustic Loopback (Rep 3)** | -37.18 dBFS | 20.51 dB | 1,433.54 Hz | 4.65% | **100.00%** | **88.51%** | **0 / 3 (0%)** | **0.3434** |
| **Loopback Mean** | **-38.95 dBFS** | **17.62 dB** | **1,253.74 Hz** | **6.79%** | **100.00%** | **88.51%** | **0 / 3 (0%)** | **0.3290** |

---

## 9. FACT (Directly Measured & Verified)

1. When the frozen reference asset `speech_test.wav` is fed directly into Whisper, it achieves **0.00% WER (3/3 exact match)** on this device.
2. When the exact same `speech_test.wav` is played through an external speaker in physical space and captured through Device A's production `AudioRecordCapture` path, Whisper decodes 0 words of the reference sentence across 3 separate captures (**100.00% WER, 0/3 exact match**).
3. The acoustic loopback recording exhibits:
   - 12.41 dB lower overall RMS level (-38.95 vs -26.54 dBFS).
   - 27.07 dB narrower dynamic contrast (17.62 vs 44.69 dB).
   - Upward spectral centroid shift of +596.28 Hz (1,253.74 vs 657.46 Hz).
   - Depletion of fundamental frequencies below 300 Hz (6.79% vs 26.02%).
4. All captured WAV files have 0 clipped samples and valid linear PCM headers.

---

## 10. INFERENCE (Logically Derived from Facts)

1. The Whisper ASR failure on Device A is **not** caused by human vocal idiosyncrasies, accents, mispronunciation, or variable speaking speed.
2. Passing studio-grade reference speech through the physical air medium and the tablet's production microphone capture path transforms the acoustic signal into the same degraded spectral regime observed during live human speech tests.
3. This acoustic transformation (low input level, depleted low-frequency fundamentals, high centroid bias, and narrow dynamic contrast) is sufficient to cause Whisper-Tiny to fail recognition completely.

---

## 11. UNKNOWN (Not Proven / Outside Scope)

1. Whether the primary acoustic distortion originates in the external speaker frequency response, room reverberation/multipath reflections, tablet microphone port acoustic impedance, or OEM hardware/driver filtering.
2. The specific microphone hardware transfer function and whether it meets Lenovo OEM factory specifications for tablet telephony.
3. Whether software-based front-end DSP conditioning (such as calibrated gain normalization or spectral equalization) can restore recognition on this acoustic path.

---

## 12. Interpretation

"The recognition degradation persists under controlled acoustic playback."

* Reproducing known-good reference audio externally through physical air reproduces the identical ASR failure (100% WER) seen with live human speech.
* This establishes that the degradation is rooted in the acoustic capture channel (the combination of physical sound propagation, tablet microphone transduction, and production audio driver processing) rather than linguistic or vocal characteristics of the speaker.

---

## 13. Production Decision Support

**Does the evidence justify an immediate production change?**
> **NO.** While the diagnostic localizes the failure to the physical acoustic capture channel, it does not validate a specific software fix or DSP pipeline. No production changes should be made without an independently verified front-end conditioning design.

---

## 14. Build & Test Results

* **Unit Test Count**: `343 / 343 passing` (`0 failures`, `0 errors`, `0 skipped`).
* **Unit Test Command**: `.\gradlew testDebugUnitTest` — `BUILD SUCCESSFUL`.
* **Assemble Debug Command**: `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
