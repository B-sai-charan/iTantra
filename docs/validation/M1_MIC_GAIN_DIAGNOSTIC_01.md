# M1 Microphone Audio Gain Diagnostic (Task m1-mic-gain-diagnostic-01)

## Objective

Determine whether the low recorded signal level is materially associated with the Whisper ASR recognition failure by creating linear PCM digital gain variants (1.0x, 2.0x, 4.0x) strictly offline from the newly captured production microphone audio (`production_capture_source_test.wav`) and evaluating them through the production `SherpaWhisperAsrEngine`.

---

### Input WAV & SHA-256 Verification

| Property | Value | Status |
|---|---|---|
| **Original File Name** | `production_capture_source_test.wav` | Verified |
| **Expected SHA-256** | `26298c77438f0491ee172c810a9c328dc8628866612a41310ff6d37c4c0684d2` | Expected |
| **Measured SHA-256** | `26298c77438f0491ee172c810a9c328dc8628866612a41310ff6d37c4c0684d2` | **EXACT MATCH** |
| **Integrity Preservation**| Original file kept unperturbed in storage | Verified |

---

### Gain Calculation & Variant Artifacts

For each PCM16 short sample $s_i \in [-32768, 32767]$ and gain factor $g \in \{1.0, 2.0, 4.0\}$:
$$\text{scaled}_i = \text{clamp}\Big(\text{round}(s_i \cdot g), -32768, 32767\Big)$$

* **No other DSP applied**: No normalization, AGC, filtering, compression, EQ, noise reduction, or silence removal.
* **Storage**: Output WAV files saved exclusively in private diagnostic app storage.

| Variant | Multiplier | File Name | File Size | SHA-256 Checksum |
|---|---:|---|---:|---|
| **1.0x (Original)** | $1.0\times$ | `production_capture_source_test.wav` | 252,844 bytes | `26298c77438f0491ee172c810a9c328dc8628866612a41310ff6d37c4c0684d2` |
| **2.0x Gain** | $2.0\times$ | `production_capture_source_test_gain2x.wav` | 252,844 bytes | `629b590612e7864147c6377963a551eb7072b146c4131179f8c323a438baf3d5` |
| **4.0x Gain** | $4.0\times$ | `production_capture_source_test_gain4x.wav` | 252,844 bytes | `1e68882d3b601a264379afa775f303b89cfac8994b92ccdc81a8b31a236f045a` |

---

### Signal Statistics Across Gain Variants

| Metric | 1.0x (Original) | 2.0x Gain | 4.0x Gain |
|---|---|---|---|
| **Sample Count** | 126,400 samples | 126,400 samples | 126,400 samples |
| **Duration** | 7,900 ms (7.900 s) | 7,900 ms (7.900 s) | 7,900 ms (7.900 s) |
| **Sample Rate / Format** | 16 kHz Mono PCM16 | 16 kHz Mono PCM16 | 16 kHz Mono PCM16 |
| **Min / Max Sample** | -10,312 / +10,871 | -20,624 / +21,742 | -32,768 / +32,767 |
| **Peak Absolute Amplitude** | 10,871 (33.18%) | 21,742 (66.35%) | 32,768 (100.00%) |
| **RMS Amplitude** | 558.42 | 1,116.84 | 2,229.89 |
| **RMS dBFS** | -35.37 dBFS | -29.35 dBFS | -23.34 dBFS |
| **Zero Sample Percentage** | 0.12% (155 zero samples) | 0.12% (155 zero samples) | 0.12% (155 zero samples) |
| **Clipping Min (-32768)** | 0 | 0 | 2 samples |
| **Clipping Max (+32767)** | 0 | 0 | 5 samples |
| **Total Clipped Samples** | 0 (0.00%) | 0 (0.00%) | 7 (0.0055%) |

---

### Three Inference Runs per Variant

* **ASR Engine**: Production `SherpaWhisperAsrEngine` (Whisper-Tiny INT8, greedy search, `lang = "en"`)
* **Reference Text**: `"Hello from iTantra. This is a production capture source isolation test."`
* **Normalized Reference**: `"hello from itantra this is a production capture source isolation test"` (11 words, 59 characters)

#### 1.0x Variant (Original)
| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | RTF |
|---|---|---|---:|---:|---|---:|---:|
| **Pass 1** | `"[inaudible]"` | `"inaudible"` | 100.00% | 88.14% | `false` | 2,356 ms | 0.2982 |
| **Pass 2** | `"[inaudible]"` | `"inaudible"` | 100.00% | 88.14% | `false` | 2,274 ms | 0.2878 |
| **Pass 3** | `"[inaudible]"` | `"inaudible"` | 100.00% | 88.14% | `false` | 2,334 ms | 0.2954 |
| **Mean** | — | — | **100.00%** | **88.14%** | **0 / 3** | **2,321.3 ms** | **0.2938** |

#### 2.0x Gain Variant
| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | RTF |
|---|---|---|---:|---:|---|---:|---:|
| **Pass 1** | `"[inaudible]"` | `"inaudible"` | 100.00% | 88.14% | `false` | 2,287 ms | 0.2895 |
| **Pass 2** | `"[inaudible]"` | `"inaudible"` | 100.00% | 88.14% | `false` | 2,264 ms | 0.2866 |
| **Pass 3** | `"[inaudible]"` | `"inaudible"` | 100.00% | 88.14% | `false` | 2,242 ms | 0.2838 |
| **Mean** | — | — | **100.00%** | **88.14%** | **0 / 3** | **2,264.3 ms** | **0.2866** |

#### 4.0x Gain Variant
| Run | Raw Hypothesis | Normalized Hypothesis | WER | CER | Exact Match | Inference Time | RTF |
|---|---|---|---:|---:|---|---:|---:|
| **Pass 1** | `"[speaking in foreign language]"` | `"speaking in foreign language"` | 100.00% | 81.36% | `false` | 2,654 ms | 0.3359 |
| **Pass 2** | `"[speaking in foreign language]"` | `"speaking in foreign language"` | 100.00% | 81.36% | `false` | 2,606 ms | 0.3299 |
| **Pass 3** | `"[speaking in foreign language]"` | `"speaking in foreign language"` | 100.00% | 81.36% | `false` | 2,668 ms | 0.3377 |
| **Mean** | — | — | **100.00%** | **88.14%** | **0 / 3** | **2,642.7 ms** | **0.3345** |

---

### Compact Gain Comparison Table

| Variant | Gain | RMS dBFS | Peak | Clipping | WER | CER | Exact | Mean RTF |
|---|---:|---:|---:|---:|---:|---:|---|---:|
| **1.0x (Original)** | $1.0\times$ | -35.37 dBFS | 10,871 (33.2%) | 0 | **100.00%** | **88.14%** | 0 / 3 | **0.2938** |
| **2.0x Gain** | $2.0\times$ | -29.35 dBFS | 21,742 (66.4%) | 0 | **100.00%** | **88.14%** | 0 / 3 | **0.2866** |
| **4.0x Gain** | $4.0\times$ | -23.34 dBFS | 32,768 (100.0%) | 7 | **100.00%** | **81.36%** | 0 / 3 | **0.3345** |

---

### Clipping Observations

* At **1.0x** and **2.0x**, exactly 0 samples reached the integer clipping limits.
* At **4.0x**, peak dynamic range reached 100% full scale, with 7 samples clipping at extreme boundaries (2 at -32768, 5 at +32767), representing 0.0055% of the total 126,400 samples.

---

### Interpretation

> Gain-only offline processing changed ASR behavior.

Key observations:
1. Increasing digital gain from $1.0\times$ (RMS -35.37 dBFS) to $2.0\times$ (RMS -29.35 dBFS) produced identical token output (`"[inaudible]"`).
2. Increasing digital gain to $4.0\times$ (RMS -23.34 dBFS) altered Whisper's decoder hypothesis from `"[inaudible]"` to `"[speaking in foreign language]"` across all 3 passes.
3. However, gain-only offline processing did not resolve the recognition failure, as all variants remained at 100.00% WER with 0/3 exact matches.
4. This diagnostic does not support concluding that production microphone gain should be changed.

---

### Limitations

* Evaluates one prescribed microphone recording across three fixed linear digital gain multipliers ($1.0\times, 2.0\times, 4.0\times$) in offline inference.
* Does not evaluate non-linear dynamic range compression, spectral filtering, or hardware analog pre-amplification.

---

### Verification of Production Discipline

* No modifications were made to production `AudioRecord`, `AudioCaptureConfig`, VAD, Whisper, model files, decoding, language configuration, transport, or TTS.

---

### Test Suite & Build Status

* `./gradlew testDebugUnitTest`: **343 / 343 tests passed (0 failures, 0 ignored)**
* `./gradlew assembleDebug`: **BUILD SUCCESSFUL**

---

### Conclusion

```text
GAIN-ASSOCIATED ASR CHANGE OBSERVED
```
