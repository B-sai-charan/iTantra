# M1 Microphone Audio Signal Characteristics Diagnostic (Task m1-mic-signal-characteristics-diagnostic-01)

## Objective

Compare measurable time-domain, spectral, and energy distribution characteristics of the known-good English reference fixture (`speech_test.wav`) and the production microphone recording (`production_capture_source_test.wav`) to evaluate whether the physical microphone audio possesses an anomalous signal distribution.

---

### Input Artifacts & SHA-256 Verification

| Input | File Name | Origin / Description | Expected SHA-256 | Measured SHA-256 | Status |
|---|---|---|---|---|---|
| **Input A (Reference)** | `speech_test.wav` | Frozen LibriSpeech benchmark asset (`EN-001`) | `6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f` | `6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f` | **Verified** |
| **Input B (Device Mic)** | `production_capture_source_test.wav` | Production `AudioRecordCapture` WAV | `26298c77438f0491ee172c810a9c328dc8628866612a41310ff6d37c4c0684d2` | `26298c77438f0491ee172c810a9c328dc8628866612a41310ff6d37c4c0684d2` | **Verified** |

---

### Step 1: Basic Waveform Statistics

| Metric | Known-Good Reference (`speech_test.wav`) | Device Microphone (`production_capture_source_test.wav`) |
|---|---:|---:|
| **Sample Rate** | 16,000 Hz | 16,000 Hz |
| **Channel Count** | 1 (Mono) | 1 (Mono) |
| **PCM Format** | 16-bit Signed Linear PCM | 16-bit Signed Linear PCM |
| **Sample Count** | 106,000 samples | 126,400 samples |
| **Duration** | 6.625 s | 7.900 s |
| **Min Sample** | -9,631 | -10,312 |
| **Max Sample** | +17,529 | +10,871 |
| **Peak Absolute Amplitude** | 17,529 (53.50%) | 10,871 (33.18%) |
| **Mean Sample (DC Offset)** | -3.23 samples | -0.99 samples |
| **RMS Amplitude** | 1,543.09 | 558.42 |
| **RMS dBFS** | -26.54 dBFS | -35.37 dBFS |
| **Zero Sample Percentage** | 0.25% (266 zero samples) | 0.12% (155 zero samples) |
| **Clipping Count (-32768)** | 0 | 0 |
| **Clipping Count (+32767)** | 0 | 0 |

---

### Step 2: Time-Domain Energy Analysis (100 ms Windows)

Calculated across non-overlapping 100 ms analysis frames (1,600 samples per window):

| Metric | Known-Good Reference | Device Microphone | Difference |
|---|---:|---:|---:|
| **Minimum Window RMS** | 23.16 (-63.01 dBFS) | 162.58 (-46.09 dBFS) | +16.92 dB higher noise floor |
| **Maximum Window RMS** | 3,975.60 (-18.32 dBFS) | 1,814.57 (-25.13 dBFS) | -6.81 dB lower peak frame |
| **Mean Window RMS** | 1,236.13 (-33.39 dBFS) | 470.11 (-38.14 dBFS) | -4.75 dB |
| **Median Window RMS** | 1,040.64 (-29.96 dBFS) | 394.15 (-38.40 dBFS) | -8.44 dB |
| **10th Percentile RMS** | 32.15 (-60.17 dBFS) | 210.73 (-43.83 dBFS) | +16.34 dB |
| **90th Percentile RMS** | 2,505.53 (-22.33 dBFS) | 866.83 (-31.55 dBFS) | -9.22 dB |
| **Dynamic Contrast (P90 - P10)** | **37.84 dB** | **12.28 dB** | **25.56 dB narrower dynamic range** |

#### Window Distribution Bins (Descriptive)
| Energy Bin | Known-Good Reference | Device Microphone |
|---|---:|---:|
| **Windows < -50 dBFS** | 13.64% (quiet pauses present) | 0.00% (elevated baseline floor) |
| **Windows < -45 dBFS** | 16.67% | 3.80% |
| **Windows < -40 dBFS** | 19.70% | 35.44% |
| **Windows < -35 dBFS** | 27.27% | 79.75% |

---

### Step 3: Zero-Crossing Rate (ZCR) Analysis (100 ms Windows)

| Metric | Known-Good Reference | Device Microphone |
|---|---:|---:|
| **Mean ZCR** | 0.0879 (8.79%) | 0.1410 (14.10%) |
| **Median ZCR** | 0.0638 (6.38%) | 0.1401 (14.01%) |
| **Minimum ZCR** | 0.0313 (3.13%) | 0.0788 (7.88%) |
| **Maximum ZCR** | 0.4459 (44.59%) | 0.2120 (21.20%) |

---

### Step 4 & 5: Frequency-Band Energy & Spectral Summary

Computed via discrete short-time Fourier transform (512-point FFT, 256-sample hop, Hann window):

| Metric / Band | Known-Good Reference | Device Microphone | Observation |
|---|---:|---:|---|
| **0–300 Hz Energy** | 26.02% | 20.45% | Relative low-end presence |
| **300–1000 Hz Energy** | 61.30% | 51.01% | Primary vowel formant band |
| **1000–3000 Hz Energy** | 12.06% | **25.61%** | **2.12x higher proportional energy in mic** |
| **3000–6000 Hz Energy** | 0.58% | **2.65%** | **4.56x higher proportional energy in mic** |
| **6000–8000 Hz Energy** | 0.04% | 0.28% | High-frequency content |
| **Spectral Centroid** | **657.46 Hz** | **938.01 Hz** | +280.55 Hz upward spectral shift |
| **Spectral Bandwidth** | **571.73 Hz** | **787.66 Hz** | +215.93 Hz wider dispersion |
| **Spectral Rolloff (85%)**| **1,009.10 Hz** | **1,549.52 Hz** | +540.42 Hz higher rolloff boundary |

---

### Step 7: Master Comparison Summary

| Metric | Known-Good Reference | Device Microphone | Status |
|---|---:|---:|---|
| **RMS dBFS** | -26.54 dBFS | -35.37 dBFS | Reference is +8.83 dB louder |
| **Peak Amplitude** | 17,529 (53.50%) | 10,871 (33.18%) | Healthy headroom in both |
| **DC Offset** | -3.23 samples | -0.99 samples | Negligible DC in both |
| **Dynamic Contrast (P90 - P10)**| 37.84 dB | 12.28 dB | Microphone has narrower dynamic contrast |
| **Mean ZCR** | 8.79% | 14.10% | Microphone has higher average ZCR |
| **Spectral Centroid** | 657.46 Hz | 938.01 Hz | Upward spectral tilt in microphone |
| **Spectral Bandwidth** | 571.73 Hz | 787.66 Hz | Broader spectral spread in microphone |
| **0–300 Hz Energy** | 26.02% | 20.45% | Proportional |
| **300–1000 Hz Energy** | 61.30% | 51.01% | Lower in microphone |
| **1000–3000 Hz Energy** | 12.06% | 25.61% | Higher in microphone |
| **3000–6000 Hz Energy** | 0.58% | 2.65% | Higher in microphone |
| **6000–8000 Hz Energy** | 0.04% | 0.28% | Proportional |

---

### Step 8: Interpretation Discipline

#### FACT
1. The device microphone recording has a higher ambient noise floor (-46.09 dBFS min vs. -63.01 dBFS in studio fixture) and a narrower time-domain dynamic contrast (12.28 dB vs. 37.84 dB).
2. The device microphone recording exhibits an upward spectral centroid shift (938.01 Hz vs. 657.46 Hz) and double the proportional energy concentration in the 1,000–3,000 Hz band (25.61% vs. 12.06%).
3. The device microphone recording maintains valid 16 kHz mono PCM integrity, 0 clipping, and minimal DC bias (-0.99).

#### INFERENCE
1. The narrower dynamic contrast and elevated minimum window level reflect real-world physical tablet microphone acoustics and room ambient background noise compared to clean studio LibriSpeech data.
2. The upward spectral centroid and enhanced 1–3 kHz energy distribution reflect physical microphone capsule frequency response and room acoustic resonance/formant coloration.

#### UNKNOWN
1. Whether Whisper-Tiny INT8's failure on the microphone recording is driven by acoustic reverberation, signal-to-noise ratio in specific mel-frequency filterbanks, or tokenizer language model priors under conversational acoustic profiles.
2. What specific acoustic environment or input conditions allow Whisper to transition from hallucination to accurate decoding.

---

### Limitations

* Evaluates one studio benchmark fixture against one device microphone recording.
* Spectral metrics were computed with 512-point DFT and 100 ms time windows.

---

### Verification of Production Discipline

* No changes to `AudioRecord`, `AudioCaptureConfig`, VAD, Whisper, model files, decoding, language configuration, transport, or TTS.

---

### Test Suite & Build Status

* `./gradlew testDebugUnitTest`: **343 / 343 tests passed (0 failures, 0 ignored)**
* `./gradlew assembleDebug`: **BUILD SUCCESSFUL**

---

### Conclusion

```text
SIGNAL CHARACTERISTIC DIFFERENCE OBSERVED
```
