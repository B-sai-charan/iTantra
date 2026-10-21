# M1 AudioSource A/B Isolation Diagnostic (Task m1-audiosource-ab-isolation-01)

## Objective

Determine whether Android `AudioRecord` audio-source selection (`MediaRecorder.AudioSource.VOICE_RECOGNITION` vs `MediaRecorder.AudioSource.MIC`) materially affects the recorded microphone waveform and Whisper ASR recognition on Device A (`HNP097HQ`).

This is an **OFFLINE/DIAGNOSTIC capture experiment only**.

---

## Controlled Reference Sentence

Both AudioSource capture conditions evaluated the identical spoken sentence:

```text
"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
```

---

## Capture Configurations & Artifact Verification

| Parameter | Source A (`VOICE_RECOGNITION`) | Source B (`MIC`) |
|---|---|---|
| **Android AudioSource** | `MediaRecorder.AudioSource.VOICE_RECOGNITION` (6) | `MediaRecorder.AudioSource.MIC` (1) |
| **Output File** | `scratch_audio_source_voice_recognition.wav` | `scratch_audio_source_mic.wav` |
| **SHA-256 Checksum** | `f5f9b6cdb9b8727d3c27fcde042bf44d19bcfc0c73b21b73f4ec5260becb4cfe` | `4bdf19425dade99955b643d8ade101b473a623aeefee565c144139369d933ccd` |
| **File Size** | 253,484 bytes | 253,484 bytes |
| **Sample Rate** | 16,000 Hz | 16,000 Hz |
| **Channels** | 1 (Mono) | 1 (Mono) |
| **PCM Encoding** | 16-bit Signed Linear PCM | 16-bit Signed Linear PCM |
| **Sample Count** | 126,720 samples | 126,720 samples |
| **Duration** | 7,920 ms (7.920 s) | 7,920 ms (7.920 s) |
| **Buffer / Read Discipline** | Production `AudioRecordCapture` (20 ms / 320 samples per frame) | Production `AudioRecordCapture` (20 ms / 320 samples per frame) |
| **Device & Environment** | Device A (`HNP097HQ`, Lenovo TB-X6C6F, Android 12, API 31) | Device A (`HNP097HQ`, Lenovo TB-X6C6F, Android 12, API 31) |

---

## Audio Statistics Verification

| Metric | Source A: `VOICE_RECOGNITION` | Source B: `MIC` | Difference / Ratio |
|---|---|---|---|
| **Min / Max Sample** | -2,292 / +2,031 | -8,668 / +8,290 | MIC peak amplitude is $3.78\times$ higher |
| **Peak Absolute Amplitude** | 2,292 (6.99%) | 8,668 (26.45%) | +19.46% of full scale |
| **Mean / DC Offset** | -0.9781 | -1.4850 | Negligible DC offset in both |
| **RMS Amplitude** | 274.64 | 1,465.50 | MIC RMS is $5.34\times$ higher |
| **RMS dBFS** | **-41.53 dBFS** | **-26.99 dBFS** | **+14.54 dB higher gain on MIC** |
| **Zero Sample Percentage** | 0.21% (272 zeros) | 0.04% (55 zeros) | Both low zero counts |
| **Clipping Count** | 0 (0 min, 0 max) | 0 (0 min, 0 max) | 0 clipping on both |

---

## ASR Inference Results (3 Exact Repetitions per Source)

* **ASR Engine**: Production `SherpaWhisperAsrEngine` (Sherpa-ONNX Whisper-Tiny INT8, greedy search, `lang = "en"`)
* **Reference Text**: `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`

### Source A — `VOICE_RECOGNITION`

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | Audio Duration | RTF | Errors |
|---|---|---|---:|---:|---|---:|---:|---:|---|
| **Pass 1** | `"(whistling)"` | `"whistling"` | 100.00% | 94.25% | `false` | 2,507 ms | 7,920 ms | 0.3165 | None |
| **Pass 2** | `"(whistling)"` | `"whistling"` | 100.00% | 94.25% | `false` | 2,461 ms | 7,920 ms | 0.3107 | None |
| **Pass 3** | `"(whistling)"` | `"whistling"` | 100.00% | 94.25% | `false` | 2,442 ms | 7,920 ms | 0.3083 | None |
| **Mean** | — | — | **100.00%** | **94.25%** | **0 / 3 (0%)** | **2,470.0 ms** | **7,920 ms** | **0.3118** | **0 errors** |

---

### Source B — `MIC`

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | Audio Duration | RTF | Errors |
|---|---|---|---:|---:|---|---:|---:|---:|---|
| **Pass 1** | `"I am going to the Indian side."` | `"i am going to the indian side"` | 94.44% | 83.91% | `false` | 3,001 ms | 7,920 ms | 0.3789 | None |
| **Pass 2** | `"I am going to the Indian side."` | `"i am going to the indian side"` | 94.44% | 83.91% | `false` | 3,006 ms | 7,920 ms | 0.3795 | None |
| **Pass 3** | `"I am going to the Indian side."` | `"i am going to the indian side"` | 94.44% | 83.91% | `false` | 3,368 ms | 7,920 ms | 0.4253 | None |
| **Mean** | — | — | **94.44%** | **83.91%** | **0 / 3 (0%)** | **3,125.0 ms** | **7,920 ms** | **0.3946** | **0 errors** |

---

## Detailed Waveform Characteristics Comparison

Computed using identical analysis parameters (100 ms sliding windows / 1,600 samples; 512-point FFT, 256-sample hop):

| Waveform Metric | `AudioSource.VOICE_RECOGNITION` | `AudioSource.MIC` | Delta / Ratio |
|---|---|---|---|
| **Overall RMS** | 274.64 | 1,465.50 | $5.34\times$ ($+14.54\text{ dB}$) |
| **RMS dBFS** | **-41.53 dBFS** | **-26.99 dBFS** | **+14.54 dBFS** |
| **Peak Absolute Amplitude** | 2,292 (6.99%) | 8,668 (26.45%) | $3.78\times$ |
| **Clipping Count** | 0 | 0 | Clean |
| **Mean / DC Offset** | -0.9781 | -1.4850 | Minimal |
| **100 ms RMS Distribution** | | | |
| * Minimum Window RMS | 137.56 (-47.54 dBFS) | 754.72 (-32.75 dBFS) | +14.79 dB |
| * Median (P50) Window RMS | 208.95 (-43.91 dBFS) | 1,045.15 (-29.93 dBFS) | +13.98 dB |
| * Maximum Window RMS | 638.10 (-34.21 dBFS) | 2,875.08 (-21.14 dBFS) | +13.07 dB |
| * 10th Percentile (P10) RMS | 170.72 (-45.66 dBFS) | 820.36 (-32.03 dBFS) | +13.63 dB |
| * 90th Percentile (P90) RMS | 432.18 (-37.60 dBFS) | 2,182.19 (-23.53 dBFS) | +14.07 dB |
| * Dynamic Contrast (Max - Min dBFS) | **13.33 dB** | **11.62 dB** | -1.71 dB |
| **Zero Crossing Rate (ZCR)** | | | |
| * Mean ZCR | 0.2155 | 0.1749 | VR has higher zero-crossings |
| * Median ZCR | 0.1814 | 0.1789 | Comparable |
| **Spectral Properties** | | | |
| * Spectral Centroid | **1,458.86 Hz** | **1,120.06 Hz** | **-338.80 Hz lower on MIC** |
| * Spectral Bandwidth | 1,152.73 Hz | 1,036.78 Hz | -115.95 Hz narrower on MIC |
| * 85% Rolloff Frequency | 2,461.84 Hz | 2,036.26 Hz | -425.58 Hz lower on MIC |
| **FFT Band Energy Percentages** | | | |
| * 0 – 300 Hz | 9.80% | **28.19%** | **$2.88\times$ higher low-frequency content on MIC** |
| * 300 – 1,000 Hz | 38.37% | 42.18% | +3.81% |
| * 1,000 – 3,000 Hz | 27.54% | 23.20% | -4.34% |
| * 3,000 – 6,000 Hz | 20.95% | 6.11% | **$0.29\times$ (significantly attenuated on MIC)** |
| * 6,000 – 8,000 Hz | 3.34% | 0.32% | $0.10\times$ |

---

## Comparison Table

| AudioSource | RMS dBFS | Dynamic Contrast | Spectral Centroid | WER | CER | Exact | Mean RTF |
|---|---:|---:|---:|---:|---:|---|---:|
| **VOICE_RECOGNITION** | -41.53 dBFS | 13.33 dB | 1,458.86 Hz | 100.00% | 94.25% | 0 / 3 (0%) | 0.3118 |
| **MIC** | -26.99 dBFS | 11.62 dB | 1,120.06 Hz | 94.44% | 83.91% | 0 / 3 (0%) | 0.3946 |

---

## Interpretation

"A measurable AudioRecord audio-source-dependent difference was observed."

### Key Observations:
1. **Signal Gain / Level**: `AudioSource.MIC` captures audio with a +14.54 dB higher overall RMS level (-26.99 dBFS vs -41.53 dBFS) and $3.78\times$ higher peak amplitude (8,668 vs 2,292) under identical physical speaking conditions.
2. **Frequency Response**:
   - `VOICE_RECOGNITION` applies hardware/OEM processing (likely high-pass filtering or noise suppression) that drastically cuts low frequencies (0–300 Hz represents only 9.80% of energy) and boosts high frequencies (spectral centroid at 1,458.86 Hz; 3–6 kHz contains 20.95% of energy).
   - `MIC` retains $2.88\times$ higher low-frequency energy (28.19% in 0–300 Hz) and has a lower spectral centroid (1,120.06 Hz), matching natural voice fundamental characteristics more closely.
3. **ASR Decoding**:
   - `VOICE_RECOGNITION` produced `"(whistling)"` across all 3 passes (100.00% WER), indicating that Whisper's encoder perceived the thin, high-frequency-biased signal as non-speech/whistling audio.
   - `MIC` produced speech words (`"I am going to the Indian side."`, 94.44% WER), detecting actual phonemes though still failing exact recognition.

### Mandatory Diagnostic Rules:
* Do NOT claim that one source is inherently better.
* Do NOT infer hardware failure.
* This experiment does not authorize a production `AudioSource` change.

---

## Rigorous Fact / Inference / Unknown Breakdown

### 1. FACT (Directly Measured & Verified)
1. `AudioSource.VOICE_RECOGNITION` and `AudioSource.MIC` were recorded sequentially on Device A with the identical spoken sentence.
2. The raw WAVs were verified with distinct SHA-256 hashes:
   - `scratch_audio_source_voice_recognition.wav`: `f5f9b6cdb9b8727d3c27fcde042bf44d19bcfc0c73b21b73f4ec5260becb4cfe`
   - `scratch_audio_source_mic.wav`: `4bdf19425dade99955b643d8ade101b473a623aeefee565c144139369d933ccd`
3. `MIC` produced +14.54 dB higher RMS amplitude (-26.99 dBFS vs -41.53 dBFS) and a spectral centroid 338.80 Hz lower than `VOICE_RECOGNITION`.
4. Low-end energy (0–300 Hz) was 28.19% on `MIC` versus 9.80% on `VOICE_RECOGNITION`.
5. High-frequency energy (3000–6000 Hz) was 20.95% on `VOICE_RECOGNITION` versus 6.11% on `MIC`.
6. Whisper decoding on `VOICE_RECOGNITION` output `"(whistling)"` (WER 100.00%, CER 94.25%) for all 3 passes.
7. Whisper decoding on `MIC` output `"I am going to the Indian side."` (WER 94.44%, CER 83.91%) for all 3 passes.

### 2. INFERENCE (Logically Derived from Facts)
1. On this device/OEM HAL implementation (Lenovo TB-X6C6F), `AudioSource.VOICE_RECOGNITION` applies an aggressive OEM high-pass / pre-emphasis filter or noise suppression profile that substantially attenuates low frequencies and lowers the overall gain.
2. `AudioSource.MIC` delivers a raw, higher-gain, low-frequency-rich signal directly from the primary microphone hardware.
3. The acoustic feature profile presented to Whisper varies significantly depending on the selected Android `AudioSource`.

### 3. UNKNOWN (Not Proven / Outside Scope)
1. Whether OEM preprocessing on `VOICE_RECOGNITION` includes active echo cancellation or non-linear noise suppression that suppresses vocal formants.
2. Whether other `AudioSource` options (e.g. `UNPROCESSED`, `CAMCORDER`, `DEFAULT`) exhibit different frequency transfer functions on this HAL.
3. Whether Whisper recognition on `MIC` audio can be improved with calibrated front-end conditioning.

---

## Limitations

1. Captures were performed sequentially in a real physical room by a single physical speaker.
2. Only `VOICE_RECOGNITION` (6) and `MIC` (1) were evaluated in this isolation task.

---

## Classification

**AUDIOSOURCE-DEPENDENT DIFFERENCE OBSERVED**
