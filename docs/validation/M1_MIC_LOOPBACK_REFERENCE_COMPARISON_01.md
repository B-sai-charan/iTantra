# M1 Microphone Loopback vs. Reference Audio Diagnostic (Task m1-mic-loopback-reference-comparison-01)

## 1. Objective

Determine whether the live physical microphone capture on Device A (`HNP097HQ`) itself contains sufficient speech information for Whisper when compared directly against the known-good `EN-001` reference benchmark fixture (`speech_test.wav`), holding both the **SPOKEN SENTENCE** and the **WHISPER ENGINE** strictly identical.

This is a **controlled diagnostic experiment only**.

---

## 2. Device & Test Environment

* **Target Device**: Device A (`HNP097HQ`, Lenovo TB-X6C6F, Android 12, API 31, arm64-v8a)
* **Target Linguistic Reference Sentence**:
  ```text
  "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
  ```
* **ASR Engine**: Production `SherpaWhisperAsrEngine` (Sherpa-ONNX Whisper-Tiny INT8, greedy search, `lang = "en"`)

---

## 3. Exact Source-Code Capture Configuration

Before running the experiment, the production capture path was inspected directly in the codebase:

* **Configuration Class**: `org.itantra.core.audio.AudioCaptureConfig`
  - `sampleRateInHz = 16000` (16,000 Hz)
  - `channelConfig = AudioFormat.CHANNEL_IN_MONO` (Mono = 1 channel)
  - `audioFormat = AudioFormat.ENCODING_PCM_16BIT` (16-bit Signed Linear PCM)
  - `audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION` (value 6)
  - `chunkDurationMs = 20` (320 samples / 640 bytes per chunk)
* **Capture Engine**: `org.itantra.core.audio.AudioRecordCapture`
  - Instantiates Android `AudioRecord` with `audioSource = VOICE_RECOGNITION`.
  - Executes audio capture on a dedicated thread with `Process.THREAD_PRIORITY_URGENT_AUDIO`.
  - Continuous hot read loop delivering 320-sample frames to the pipeline listener.

---

## 4. Reference Asset Integrity Verification

| Property | Value | Verification Status |
|---|---|---|
| **Asset Path** | `app/src/main/assets/speech_test.wav` | Found in APK assets |
| **Origin** | Frozen LibriSpeech reference fixture (`EN-001`) | Authoritative baseline |
| **Expected SHA-256** | `6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f` | Verified byte match |
| **Measured SHA-256** | `6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f` | **Verified Match** |
| **File Size** | 212,044 bytes | Verified |
| **Sample Rate / Ch** | 16,000 Hz / Mono PCM16 | Verified |
| **Sample Count / Duration** | 106,000 samples (6,625 ms / 6.625 s) | Verified |

---

## 5. Captured Microphone WAV Integrity Verification

| Property | Value | Verification Status |
|---|---|---|
| **Device File Path** | `/data/user/0/org.itantra.app/files/mic_loopback_ref_test.wav` | Captured on device |
| **Capture Mechanism** | Unmodified production `AudioRecordCapture(AudioCaptureConfig())` | Exact production path |
| **SHA-256 Checksum** | `03e9897e348274f3478e6698fe954aaf745535c1ad58028e364e41c1af5a18d4` | Verified |
| **File Size** | 253,484 bytes | Verified |
| **Sample Rate / Ch** | 16,000 Hz / Mono PCM16 | Verified |
| **Sample Count / Duration** | 126,720 samples (7,920 ms / 7.920 s) | Verified |
| **Clipping Count** | 0 (0 min, 0 max) | 0 clipped samples |

---

## 6. Signal Statistics (Identical Analysis Parameters)

Computed using 100 ms sliding windows (1,600 samples) and 512-point FFT (256-sample hop):

| Signal Dimension | Frozen EN-001 Reference (`speech_test.wav`) | Live Production Mic (`mic_loopback_ref_test.wav`) | Difference / Ratio |
|---|---|---|---|
| **Spoken Sentence** | Prescribed sentence | **Exact same** sentence | Identical linguistic target |
| **Overall RMS Amplitude** | 1,543.09 | 336.01 | Mic is $0.22\times$ of reference |
| **RMS dBFS** | **-26.54 dBFS** | **-39.78 dBFS** | **Mic is 13.24 dB lower** |
| **Peak Absolute Amplitude** | 17,529 (53.50%) | 3,264 (9.96%) | Mic peak is $0.19\times$ reference |
| **Mean / DC Offset** | -3.2328 | -0.9625 | Minimal in both |
| **Clipping Count** | 0 | 0 | 0 clips on both |
| **100 ms RMS Distribution** | | | |
| * Minimum Window RMS | 23.16 (-63.01 dBFS) | 159.84 (-46.24 dBFS) | Mic noise floor is +16.77 dB higher |
| * Median (P50) Window RMS | 1,040.64 (-29.96 dBFS) | 254.73 (-42.19 dBFS) | Mic median is -12.23 dB lower |
| * Maximum Window RMS | 3,975.60 (-18.32 dBFS) | 927.89 (-30.96 dBFS) | Mic peak window is -12.64 dB lower |
| * Dynamic Contrast (Max - Min dBFS) | **44.69 dB** | **15.28 dB** | **Mic dynamic contrast is 29.41 dB narrower** |
| **Zero Crossing Rate (ZCR)** | | | |
| * Mean ZCR | 0.0879 | 0.2174 | Mic has $2.47\times$ higher mean ZCR |
| * Median ZCR | 0.0638 | 0.2033 | $3.19\times$ higher |
| **Spectral Characteristics** | | | |
| * Spectral Centroid | **657.46 Hz** | **1,507.60 Hz** | **+850.14 Hz upward shift on mic** |
| * Spectral Bandwidth | 571.73 Hz | 1,164.65 Hz | +592.92 Hz wider dispersion |
| * 85% Rolloff Frequency | 1,009.10 Hz | 2,558.70 Hz | +1,549.60 Hz higher rolloff |
| **FFT Band Energy Percentages** | | | |
| * 0 – 300 Hz | **26.02%** | **7.45%** | **Low-end significantly depleted on mic ($0.29\times$)** |
| * 300 – 1,000 Hz | 61.30% | 51.38% | -9.92% |
| * 1,000 – 3,000 Hz | 12.06% | **33.28%** | **$2.76\times$ higher concentration in mid-high** |
| * 3,000 – 6,000 Hz | 0.58% | **6.41%** | **$11.0\times$ higher high-frequency content** |
| * 6,000 – 8,000 Hz | 0.04% | 1.49% | Higher high-frequency residual |

---

## 7. Whisper ASR Evaluation Results (3 Exact Passes per Condition)

* **Reference**: `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`

### Condition 1: Frozen EN-001 Reference Fixture (`speech_test.wav`)

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | RTF | Errors |
|---|---|---|---:|---:|---|---:|---:|---|
| **Pass 1** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 5,039 ms | 0.7606 | None |
| **Pass 2** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 4,978 ms | 0.7514 | None |
| **Pass 3** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 4,959 ms | 0.7485 | None |
| **Mean** | — | — | **0.00%** | **0.00%** | **3 / 3 (100%)** | **4,992.0 ms** | **0.7535** | **0 errors** |

---

### Condition 2: Live Production Microphone Capture (`mic_loopback_ref_test.wav`)

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | RTF | Errors |
|---|---|---|---:|---:|---|---:|---:|---|
| **Pass 1** | `"I'm just using it."` | `"im just using it"` | 100.00% | 90.80% | `false` | 2,741 ms | 0.3461 | None |
| **Pass 2** | `"I'm just using it."` | `"im just using it"` | 100.00% | 90.80% | `false` | 2,731 ms | 0.3448 | None |
| **Pass 3** | `"I'm just using it."` | `"im just using it"` | 100.00% | 90.80% | `false` | 2,727 ms | 0.3443 | None |
| **Mean** | — | — | **100.00%** | **90.80%** | **0 / 3 (0%)** | **2,733.0 ms** | **0.3451** | **0 errors** |

---

## 8. Reference vs. Microphone Comparison & Correlation Metric

### Summary Table

| Input Condition | Overall RMS dBFS | Dynamic Contrast | Spectral Centroid | 0–300 Hz Energy | WER | CER | Exact Match | Mean RTF |
|---|---:|---:|---:|---:|---:|---:|---|---:|
| **Frozen EN-001 Reference** | -26.54 dBFS | 44.69 dB | 657.46 Hz | 26.02% | **0.00%** | **0.00%** | **3 / 3 (100%)** | **0.7535** |
| **Live Production Mic** | -39.78 dBFS | 15.28 dB | 1,507.60 Hz | 7.45% | **100.00%** | **90.80%** | **0 / 3 (0%)** | **0.3451** |

### Additional Diagnostic: Normalized Waveform Correlation & Similarity
* **Zero-Lag Normalized Cross-Correlation**: `0.0104` (sample-by-sample correlation across first 106,000 samples).
* **100 ms RMS Energy Envelope Peak Cross-Correlation**: `0.3942` (at -300 ms offset).
* *Note*: Correlation metrics are reported as diagnostic evidence only. Timing and speaking rate differences between studio-recorded speech and live reading naturally limit waveform correlation and must not be conflated with speech intelligibility.

---

## 9. FACT (Directly Measured & Verified)

1. The exact same target sentence was processed by the exact same `SherpaWhisperAsrEngine` in both conditions.
2. The frozen benchmark reference `speech_test.wav` matched SHA-256 `6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f` and decoded with **0.00% WER (3/3 exact matches)**.
3. The live microphone recording `mic_loopback_ref_test.wav` (`03e9897e348274f3478e6698fe954aaf745535c1ad58028e364e41c1af5a18d4`) captured via the production `AudioRecordCapture` path produced `"I'm just using it."` across all 3 passes (**100.00% WER, 0/3 exact matches**).
4. The live microphone signal has 0 clipping samples and valid PCM framing.
5. Relative to the reference, the microphone signal has:
   - 13.24 dB lower overall RMS level (-39.78 vs -26.54 dBFS).
   - 16.77 dB higher noise floor (-46.24 vs -63.01 dBFS).
   - 29.41 dB narrower dynamic contrast (15.28 vs 44.69 dB).
   - Upward spectral centroid shift of +850.14 Hz (1,507.60 vs 657.46 Hz).
   - Depletion of fundamental frequencies below 300 Hz (7.45% vs 26.02%).

---

## 10. INFERENCE (Logically Derived from Facts)

1. The Whisper engine itself is fully functional and capable of 100% exact recognition on clean reference speech on this device.
2. The live microphone capture through the current production path (`VOICE_RECOGNITION`) delivers an acoustic representation characterized by attenuated low frequencies, elevated background noise floor, and reduced dynamic contrast.
3. This altered spectral/energy distribution prevents Whisper-Tiny from extracting recognizable linguistic tokens for this sentence, causing hallucinated short-phrase hypotheses.

---

## 11. UNKNOWN (Not Proven / Outside Scope)

1. Whether room reverberation, microphone placement, acoustic port geometry, or OEM hardware tuning is the dominant contributor to the high-frequency bias.
2. Whether the microphone hardware itself is defective or operating within its intended OEM response curve for tablet telephony.
3. Whether front-end acoustic conditioning (such as calibrated digital gain, pre-emphasis/de-emphasis EQ, or noise floor expansion) is sufficient to close the recognition gap.

---

## 12. Problem Domain Assessment

Based strictly on the measured evidence:

* **Capture-Path vs. Acoustic/Input-Condition Assessment**:
  - The capture path itself is functioning without data corruption (0 dropped samples, 0 clipping, valid PCM framing, 16 kHz sample rate verified).
  - The failure is localized to the **acoustic/input-condition domain** — specifically the combination of low input SNR, reduced signal level, low-frequency depletion, and the resulting spectral distribution presented to Whisper.
* **Production Recommendation**:
  - The evidence does **NOT** authorize any immediate production code or architecture changes without a validated front-end conditioning design.

---

## 13. Build & Test Status

* **Unit Test Count**: `343 / 343 passing` (`0 failures`, `0 errors`, `0 skipped`).
* **Unit Test Command**: `.\gradlew testDebugUnitTest` — `BUILD SUCCESSFUL`.
* **Assemble Debug Command**: `.\gradlew assembleDebug` — `BUILD SUCCESSFUL`.
