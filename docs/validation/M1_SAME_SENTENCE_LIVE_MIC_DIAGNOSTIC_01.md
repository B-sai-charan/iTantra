# M1 Same-Sentence Live Microphone Diagnostic (Task m1-same-sentence-live-mic-diagnostic-01)

## Objective

Determine whether the microphone-recording ASR gap persists when the live microphone recording contains the **EXACT SAME** spoken sentence as the known-good `EN-001` reference benchmark fixture (`speech_test.wav`).

This experiment eliminates the speech-content / linguistic confound present in earlier diagnostics where different sentences were spoken into the microphone.

---

## Controlled Reference Sentence

Both the reference fixture and the live microphone recording evaluate the exact sentence:

```text
"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
```

---

## Artifact & Execution Verification

| Input | File Name | Origin / Description | SHA-256 Checksum | Verification Status |
|---|---|---|---|---|
| **Control Fixture (EN-001)** | `speech_test.wav` | Frozen LibriSpeech benchmark asset (`app/src/main/assets/speech_test.wav`) | `6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f` | **Verified Match** |
| **Live Microphone Capture** | `same_sentence_mic_test.wav` | Live speech captured via production `AudioRecordCapture` (`VOICE_RECOGNITION`) on Device A | `d50c8b2cc211e7a84e28b923ae6aef692235f97623a7765f74e4920c9a86aa87` | **Captured & Verified** |

### Execution Environment
* **Device**: Device A (`HNP097HQ`, Lenovo TB-X6C6F, Android 12, API 31, arm64-v8a)
* **Capture Path**: Production `AudioRecordCapture` (`AudioSource.VOICE_RECOGNITION`, 16,000 Hz, Mono, 16-bit Signed Linear PCM)
* **ASR Engine**: Exact production `SherpaWhisperAsrEngine` (Sherpa-ONNX Whisper-Tiny INT8, greedy search, `lang = "en"`)

---

## Audio Statistics Verification

| Metric | Frozen Control Fixture (`speech_test.wav`) | Live Microphone Capture (`same_sentence_mic_test.wav`) |
|---|---|---|
| **Spoken Sentence** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` |
| **Sample Rate** | 16,000 Hz | 16,000 Hz |
| **Channels** | 1 (Mono) | 1 (Mono) |
| **PCM Encoding** | 16-bit Signed Linear PCM | 16-bit Signed Linear PCM |
| **Sample Count** | 106,000 samples | 126,400 samples |
| **Duration** | 6,625 ms (6.625 s) | 7,900 ms (7.900 s) |
| **File Size** | 212,044 bytes | 252,844 bytes |
| **Min / Max Sample** | -9,631 / +17,529 | -4,561 / +3,975 |
| **Peak Amplitude** | 17,529 (53.50%) | 4,561 (13.92%) |
| **Mean / DC Offset** | -3.2328 | -0.9464 |
| **RMS Amplitude** | 1,543.09 | 466.77 |
| **RMS dBFS** | -26.54 dBFS | -36.93 dBFS |
| **Zero Sample Percentage** | 0.25% (266 zeros) | 0.17% (220 zeros) |
| **Clipping Count** | 0 (0 min, 0 max) | 0 (0 min, 0 max) |

---

## ASR Inference Results (3 Exact Repetitions)

### Condition A — Frozen EN-001 Benchmark Fixture

* **Reference**: `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | Audio Duration | RTF | Runtime Errors |
|---|---|---|---:|---:|---|---:|---:|---:|---|
| **Pass 1** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 5,042 ms | 6,625 ms | 0.7611 | None |
| **Pass 2** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 5,026 ms | 6,625 ms | 0.7586 | None |
| **Pass 3** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 0.00% | 0.00% | `true` | 5,004 ms | 6,625 ms | 0.7553 | None |
| **Mean** | — | — | **0.00%** | **0.00%** | **3 / 3 (100%)** | **5,024.0 ms** | **6,625 ms** | **0.7583** | **0 errors** |

---

### Condition B — Same-Sentence Live Microphone Recording

* **Reference**: `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`

| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | Audio Duration | RTF | Runtime Errors |
|---|---|---|---:|---:|---|---:|---:|---:|---|
| **Pass 1** | `"I need to get the information."` | `"i need to get the information"` | 94.44% | 83.91% | `false` | 3,034 ms | 7,900 ms | 0.3841 | None |
| **Pass 2** | `"I need to get the information."` | `"i need to get the information"` | 94.44% | 83.91% | `false` | 2,974 ms | 7,900 ms | 0.3765 | None |
| **Pass 3** | `"I need to get the information."` | `"i need to get the information"` | 94.44% | 83.91% | `false` | 3,023 ms | 7,900 ms | 0.3827 | None |
| **Mean** | — | — | **94.44%** | **83.91%** | **0 / 3 (0%)** | **3,010.3 ms** | **7,900 ms** | **0.3811** | **0 errors** |

---

## Signal Comparison (Identical Analysis Parameters)

Comparison computed across time domain (100 ms sliding windows, 1,600 samples) and spectral domain (512-point FFT, 256-sample hop, Hann window):

| Analysis Dimension / Parameter | Frozen EN-001 Fixture (`speech_test.wav`) | Same-Sentence Live Mic (`same_sentence_mic_test.wav`) | Comparison / Ratio |
|---|---|---|---|
| **Spoken Linguistic Content** | Prescribed sentence | **Exact same** prescribed sentence | Identical linguistic target |
| **Overall RMS dBFS** | -26.54 dBFS | -36.93 dBFS | Mic is 10.39 dB lower overall |
| **Peak Absolute Amplitude** | 17,529 (53.50%) | 4,561 (13.92%) | Mic peak is $0.26\times$ reference |
| **Clipping Count** | 0 | 0 | Both clean / 0 clips |
| **100 ms RMS Distribution (Min / Median / Max)** | | | |
| * Minimum Window RMS | 23.16 (-63.01 dBFS) | 141.65 (-47.28 dBFS) | Mic noise floor is +15.73 dB higher |
| * Median (P50) Window RMS | 1,040.64 (-29.96 dBFS) | 301.92 (-40.71 dBFS) | Mic median is -10.75 dB lower |
| * Maximum Window RMS | 3,975.60 (-18.32 dBFS) | 1,279.56 (-28.17 dBFS) | Mic speech peak window is -9.85 dB lower |
| * Dynamic Contrast (Max - Min dBFS) | **44.69 dB** | **19.11 dB** | Mic dynamic contrast is 25.58 dB narrower |
| * Windows < -45 dBFS | 16.67% (deep silence gaps) | 25.32% | |
| * Windows < -40 dBFS | 19.70% | 56.96% | 57% of mic audio is below -40 dBFS |
| * Windows < -35 dBFS | 27.27% | 81.01% | 81% of mic audio is below -35 dBFS |
| **Spectral Characteristics** | | | |
| * Spectral Centroid | 657.46 Hz | 812.82 Hz | +155.36 Hz shift upward |
| * Spectral Bandwidth | 571.73 Hz | 725.26 Hz | +153.53 Hz broader dispersion |
| * Spectral Rolloff (85%) | 1,009.10 Hz | 1,239.88 Hz | +230.78 Hz higher rolloff |
| **FFT Band Energy Percentages** | | | |
| * 0 – 300 Hz | 26.02% | 9.21% | Low-end energy significantly reduced ($0.35\times$) |
| * 300 – 1,000 Hz | 61.30% | 75.63% | Dominant band |
| * 1,000 – 3,000 Hz | 12.06% | 14.23% | Slightly higher high-mid concentration |
| * 3,000 – 6,000 Hz | 0.58% | 0.82% | |
| * 6,000 – 8,000 Hz | 0.04% | 0.11% | |

---

## Controlled Comparison Summary

```
====================================================================================================
CONDITION A: Frozen EN-001 Reference Benchmark Fixture
  Sentence: "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
  Audio: Pre-recorded reference (speech_test.wav) | RMS: -26.54 dBFS
  ASR Output (3/3): "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
  Exact Matches: 3 / 3 (100.0%) | Mean WER: 0.00% | Mean CER: 0.00% | Mean RTF: 0.7583
----------------------------------------------------------------------------------------------------
CONDITION B: Same-Sentence Live Microphone Recording
  Sentence: "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
  Audio: Production AudioRecordCapture (same_sentence_mic_test.wav) | RMS: -36.93 dBFS
  ASR Output (3/3): "I need to get the information."
  Exact Matches: 0 / 3 (0.0%) | Mean WER: 94.44% | Mean CER: 83.91% | Mean RTF: 0.3811
====================================================================================================
```

---

## Interpretation

"The ASR gap persists when the spoken linguistic content is held constant."

* When the exact reference sentence (`"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`) is supplied as the benchmark audio fixture, Whisper decodes it with **0.00% WER (100% exact match across all 3 passes)**.
* When the exact same sentence is spoken into Device A's microphone and captured via the unmodified production `AudioRecordCapture` path, Whisper fails to recognize the sentence, producing the hallucinated/degraded hypothesis `"I need to get the information."` across all 3 passes (**94.44% WER, 0/3 exact matches**).
* This removes the linguistic/sentence-content confound: the ASR failure is not caused by unusual sentence phrasing or OOV vocabulary in the live mic prompts.

---

## Rigorous Fact / Inference / Unknown Breakdown

### 1. FACT (Directly Measured & Verified)
1. Both conditions evaluated the exact identical target sentence: `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`.
2. The reference fixture `speech_test.wav` matched its expected SHA-256 (`6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f`) and decoded with 0.00% WER (3/3 exact matches).
3. The live microphone recording `same_sentence_mic_test.wav` (`d50c8b2cc211e7a84e28b923ae6aef692235f97623a7765f74e4920c9a86aa87`) was captured using the unmodified production `AudioRecordCapture` configuration (`VOICE_RECOGNITION`, 16 kHz mono PCM16).
4. Whisper decoding on the microphone WAV produced `"I need to get the information."` deterministically across all 3 repetitions (94.44% WER, 83.91% CER, 0 exact matches).
5. Both WAVs have 0 clipping samples and 0 NaN/corrupted values.
6. The microphone signal exhibits 10.39 dB lower overall RMS level (-36.93 vs -26.54 dBFS), a 15.73 dB higher minimum noise floor (-47.28 vs -63.01 dBFS), 25.58 dB narrower dynamic contrast (19.11 vs 44.69 dB), and an upward spectral centroid shift (812.8 Hz vs 657.5 Hz).

### 2. INFERENCE (Logically Derived from Facts)
1. The microphone-recording ASR failure is not an artifact of sentence choice, linguistic complexity, or lexical content.
2. Holding linguistic content strictly identical reproduces the exact same ASR breakdown observed with earlier microphone utterances.
3. Acoustic differences between the clean reference recording environment and the live tablet microphone capture (e.g. ambient noise floor, room acoustics, microphone frequency response, signal-to-noise ratio) remain the primary domain differentiating the two inputs.

### 3. UNKNOWN (Not Proven / Outside Scope)
1. Whether the microphone frequency response, room reverberation, or tablet acoustic enclosure is the dominant contributor to the feature distortion.
2. Whether software-level front-end processing (e.g., standard pre-emphasis, calibrated digital gain, spectral subtraction, or voice enhancement) is sufficient to close the recognition gap on this device.

---

## Limitations

1. Recording was conducted in a real physical room on Device A (Lenovo TB-X6C6F); room impulse response and speaker vocal characteristics were not synthetically calibrated.
2. Single physical speaker evaluated for this diagnostic run.
3. Diagnostic was conducted under `AudioSource.VOICE_RECOGNITION` without additional software preprocessing or post-filtering.

---

## Classification

**SAME-SENTENCE MICROPHONE ASR GAP OBSERVED**
