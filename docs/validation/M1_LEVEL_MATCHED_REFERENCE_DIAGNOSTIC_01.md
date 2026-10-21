# M1 Level-Matched Reference Speech Diagnostic (Task m1-level-matched-reference-diagnostic-01)

## Objective

Determine whether the Whisper ASR recognition gap persists when the known-good English reference speech is attenuated by scalar digital gain to match the exact RMS signal level of the device microphone recording.

---

### Input Artifacts & SHA-256 Verification

| Input | File Name | Origin / Description | Expected SHA-256 | Measured SHA-256 | Status |
|---|---|---|---|---|---|
| **Input A (Reference)** | `speech_test.wav` | Frozen LibriSpeech benchmark asset (`EN-001`) | `6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f` | `6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f` | **Verified** |
| **Input B (Device Mic)** | `production_capture_source_test.wav` | Production `AudioRecordCapture` WAV | `26298c77438f0491ee172c810a9c328dc8628866612a41310ff6d37c4c0684d2` | `26298c77438f0491ee172c810a9c328dc8628866612a41310ff6d37c4c0684d2` | **Verified** |

---

### Gain Calculation & Generated Level-Matched WAV

#### Gain Calculation
$$\text{gain} = \frac{\text{Microphone RMS}}{\text{Reference RMS}} = \frac{558.4217}{1543.0855} = 0.36188644091220895$$

* **Transformation**: Applied strictly $\text{scaled} = \text{clamp}(\text{round}(\text{sample} \cdot \text{gain}), -32768, 32767)$ to reference PCM samples.
* **No other DSP applied**: No normalization, compression, EQ, filtering, AGC, or noise suppression.

#### Generated Artifact Details
* **File Name**: `speech_test_level_matched_to_mic.wav`
* **File Path**: `/data/user/0/org.itantra.app/files/speech_test_level_matched_to_mic.wav`
* **File Size**: 212,044 bytes
* **SHA-256 Checksum**:
```text
59bf0bbcdd0e9ca18d909204556bf9660cacf2792d01f3fad1cfc8d6788380f1
```

---

### Signal Statistics Across All Conditions

| Metric | Condition A: Original EN-001 | Condition B: Level-Matched EN-001 | Condition C: Device Microphone |
|---|---|---|---|
| **Audio Source** | LibriSpeech Asset | Gain-scaled Asset ($0.3619\times$) | Production `AudioRecordCapture` |
| **Sample Rate** | 16,000 Hz | 16,000 Hz | 16,000 Hz |
| **Channels** | 1 (Mono) | 1 (Mono) | 1 (Mono) |
| **PCM Format** | 16-bit Signed Linear PCM | 16-bit Signed Linear PCM | 16-bit Signed Linear PCM |
| **Sample Count** | 106,000 samples | 106,000 samples | 126,400 samples |
| **Duration** | 6,625 ms (6.625 s) | 6,625 ms (6.625 s) | 7,900 ms (7.900 s) |
| **Min / Max Sample** | -9,631 / +17,529 | -3,485 / +6,344 | -10,312 / +10,871 |
| **Peak Absolute Amplitude** | 17,529 (53.50%) | 6,344 (19.36%) | 10,871 (33.18%) |
| **RMS Amplitude** | 1,543.09 | 558.42 | 558.42 |
| **RMS dBFS** | -26.54 dBFS | **-35.37 dBFS** | **-35.37 dBFS** |
| **Zero Sample Percentage** | 0.25% (266 zeros) | 0.25% (266 zeros) | 0.12% (155 zeros) |
| **Clipping Count** | 0 | 0 | 0 |

---

### Three Inference Runs per Condition

* **ASR Engine**: Production `SherpaWhisperAsrEngine` (Whisper-Tiny INT8, greedy search, `lang = "en"`)

#### Condition A: Original EN-001 Reference (RMS -26.54 dBFS)
* **Reference**: `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | RTF |
|---|---|---|---:|---:|---|---:|---:|
| **Pass 1** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 5,027 ms | 0.7588 |
| **Pass 2** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 4,965 ms | 0.7494 |
| **Pass 3** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 4,945 ms | 0.7464 |
| **Mean** | — | — | **0.00%** | **0.00%** | **3 / 3 (100%)** | **4,979.0 ms** | **0.7515** |

#### Condition B: Level-Matched EN-001 Reference (RMS -35.37 dBFS)
* **Reference**: `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | RTF |
|---|---|---|---:|---:|---|---:|---:|
| **Pass 1** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 4,939 ms | 0.7455 |
| **Pass 2** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 4,948 ms | 0.7469 |
| **Pass 3** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 4,975 ms | 0.7509 |
| **Mean** | — | — | **0.00%** | **0.00%** | **3 / 3 (100%)** | **4,954.0 ms** | **0.7478** |

#### Condition C: Device Microphone Recording (RMS -35.37 dBFS)
* **Reference**: `"Hello from iTantra. This is a production capture source isolation test."`

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | RTF |
|---|---|---|---:|---:|---|---:|---:|
| **Pass 1** | `"[inaudible]"` | `"inaudible"` | 100.00% | 88.14% | `false` | 2,687 ms | 0.3401 |
| **Pass 2** | `"[inaudible]"` | `"inaudible"` | 100.00% | 88.14% | `false` | 2,699 ms | 0.3416 |
| **Pass 3** | `"[inaudible]"` | `"inaudible"` | 100.00% | 88.14% | `false` | 2,678 ms | 0.3390 |
| **Mean** | — | — | **100.00%** | **88.14%** | **0 / 3 (0%)** | **2,688.0 ms** | **0.3402** |

---

### Comparison Table

| Condition | RMS dBFS | Peak | Clipping | WER | CER | Exact Match | Mean RTF |
|---|---:|---:|---:|---:|---:|---|---:|
| **Original EN-001** | -26.54 dBFS | 17,529 (53.5%) | 0 | **0.00%** | **0.00%** | **3 / 3 (100%)** | **0.7515** |
| **Level-matched EN-001** | -35.37 dBFS | 6,344 (19.4%) | 0 | **0.00%** | **0.00%** | **3 / 3 (100%)** | **0.7478** |
| **Device microphone** | -35.37 dBFS | 10,871 (33.2%) | 0 | **100.00%** | **88.14%** | **0 / 3 (0%)** | **0.3402** |

---

### Interpretation

> The evidence indicates that overall RMS level alone does not explain the observed ASR gap.

Key observations:
1. When the known-good reference audio is attenuated by $0.3619\times$ so that its RMS signal level exactly matches the microphone recording level (-35.37 dBFS), Whisper-Tiny INT8 continues to achieve **100% exact recognition (0.00% WER / 0.00% CER)** across all 3 repetitions.
2. Under the exact same RMS energy level (-35.37 dBFS), the device microphone audio remains completely unrecognized (100.00% WER, `"[inaudible]"`).
3. This establishes that low signal amplitude in itself is not the factor causing Whisper to fail on the microphone recording.

---

### Limitations

* Evaluates one reference speech sample level-matched to one production microphone recording across three repeated passes each.
* Does not isolate spectral tilt, SNR distribution, frequency response, or room reverberation characteristics.

---

### Verification of Production Discipline

* No changes were made to `AudioRecord`, `AudioCaptureConfig`, VAD, Whisper, model files, decoding, language configuration, transport, or TTS.

---

### Test Suite & Build Status

* `./gradlew testDebugUnitTest`: **343 / 343 tests passed (0 failures, 0 ignored)**
* `./gradlew assembleDebug`: **BUILD SUCCESSFUL**

---

### Conclusion

```text
LEVEL-ONLY EXPLANATION WEAKENED
```
