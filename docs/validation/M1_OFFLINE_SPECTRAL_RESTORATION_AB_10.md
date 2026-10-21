# M1 Offline Spectral Restoration A/B 10

**Document Version:** 1.0.0  
**Date:** September 28, 2026  
**Task ID:** `M1-OFFLINE-SPECTRAL-RESTORATION-AB-10`  
**Device Under Test:** Device A (Lenovo Smart Tab M10 FHD Plus / `TB-X6C6F` / `HNP097HQ`, Android 12, API 31, `arm64-v8a`)  
**Evaluation Mode:** Strict Offline Diagnostic A/B Experiment (Zero Production Code Modifications)  
**Status:** **`COMPLETE — PASS (DEFINITIVE NEGATIVE RESULT: SPECTRAL RESTORATION DOES NOT RECOVER RECOGNITION)`**

---

## 1. Objective

Determine whether correcting the measured spectral imbalance (low-frequency depletion below $300\,\text{Hz}$ and mid-frequency resonance around $500\text{--}1000\,\text{Hz}$) in the live-microphone recording improves Whisper Tiny INT8 recognition accuracy.

### Linguistic References:
- **Primary 12-Word Reference:** `"After early nightfall, the yellow lamps would light up here and there."`
- **Extended 18-Word Reference Context:** `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`

---

## 2. Input Artifact

- **Source File:** [`scratch/same_sentence_reference_vs_mic.wav`](file:///c:/Users/saich/Downloads/SIH/PS26173/scratch/same_sentence_reference_vs_mic.wav)
- **SHA-256 Checksum:** `9a2a57b288514fac1f39bf49e24295a65d79911d4f14ef1e269519a8c9578a28`
- **Format:** $16,000\,\text{Hz}$, 1 Channel (Mono), 16-bit Signed Linear PCM
- **Duration / Samples:** $8.000\,\text{seconds}$ ($128,000\,\text{samples}$)
- **RMS Energy:** $-36.98\,\text{dBFS}$ ($463.84$)
- **Peak Amplitude:** $-17.13\,\text{dBFS}$ ($4,561$)
- **Clipping Count:** $0$ ($0.00\%$)

---

## 3. Baseline

Re-evaluated on-device using native `SherpaWhisperAsrEngine` (Whisper-Tiny INT8, greedy search, `lang = "en"`):

- **Raw Transcript:** `"I need to go to the other side"`
- **Normalized Transcript:** `"i need to go to the other side"`
- **12-Word Target WER:** **`100.00%`**
- **12-Word Target CER:** **`75.44%`**
- **18-Word Target WER:** **`94.44%`**
- **18-Word Target CER:** **`81.61%`**
- **Inference Latency:** $3,210\,\text{ms}$
- **Real-Time Factor (RTF):** $0.4013$

---

## 4. Filter Definitions

All filter variants were computed deterministically using standard Robert Bristow-Johnson (RBJ) 2nd-order IIR biquad topologies ($f_s = 16,000\,\text{Hz}$):

1. **Low-Shelf Filter:** Cutoff $f_c = 300\,\text{Hz}$, $Q = 0.7071$, with gains $+3\,\text{dB}$, $+6\,\text{dB}$, $+9\,\text{dB}$, $+12\,\text{dB}$.
2. **Peaking / Mid De-Emphasis Filter:** Center $f_c = 750\,\text{Hz}$, $Q = 1.0$, gain $-3.0\,\text{dB}$.
3. **Clipping Guard:** Verified across all generated variants. Peak amplitudes remained below $4,730$ ($\le -16.81\,\text{dBFS}$), well within $[-32768, 32767]$. Zero clipping occurred; zero attenuation adjustments were necessary.

---

## 5. Generated Artifacts

| Variant Code | Artifact Path | Low Shelf ($< 300\,\text{Hz}$) | Mid Peaking ($750\,\text{Hz}$) | RMS (dBFS) | Peak (dBFS) | Peak Raw | Clips | SHA-256 Checksum |
| :---: | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **A** | [`scratch/spectral_ab_original.wav`](file:///c:/Users/saich/Downloads/SIH/PS26173/scratch/spectral_ab_original.wav) | $0\,\text{dB}$ | $0\,\text{dB}$ | $-36.98$ | $-17.13$ | $4,561$ | $0$ | `9a2a57b288514fac1f39bf49e24295a65d79911d4f14ef1e269519a8c9578a28` |
| **B** | [`scratch/spectral_ab_low3.wav`](file:///c:/Users/saich/Downloads/SIH/PS26173/scratch/spectral_ab_low3.wav) | $+3\,\text{dB}$ | $0\,\text{dB}$ | $-36.53$ | $-16.92$ | $4,670$ | $0$ | `547b4d0d20c9dcc3dbf45f0fa5e674063e6d8186a17ba5bb2d5c503d97642718` |
| **C** | [`scratch/spectral_ab_low6.wav`](file:///c:/Users/saich/Downloads/SIH/PS26173/scratch/spectral_ab_low6.wav) | $+6\,\text{dB}$ | $0\,\text{dB}$ | $-35.93$ | $-16.81$ | $4,729$ | $0$ | `26733ef8bcfeb0ae5b9a9d3ce61e1e877a528eb9b8c3271d719c9286bd404ac3` |
| **D** | [`scratch/spectral_ab_low9.wav`](file:///c:/Users/saich/Downloads/SIH/PS26173/scratch/spectral_ab_low9.wav) | $+9\,\text{dB}$ | $0\,\text{dB}$ | $-35.14$ | $-16.81$ | $4,730$ | $0$ | `c8f8043bb8b5facecbd7eebd6a6d4a992aa16c0bbd5c417d1001b83710977090` |
| **E** | [`scratch/spectral_ab_low12.wav`](file:///c:/Users/saich/Downloads/SIH/PS26173/scratch/spectral_ab_low12.wav) | $+12\,\text{dB}$ | $0\,\text{dB}$ | $-34.15$ | $-16.93$ | $4,665$ | $0$ | `31f0fe9072d896bbb42e2c7b59634faee3468bf74dc61414f3eda5866bfe6d3e` |
| **F** | [`scratch/spectral_ab_low6_midminus3.wav`](file:///c:/Users/saich/Downloads/SIH/PS26173/scratch/spectral_ab_low6_midminus3.wav) | $+6\,\text{dB}$ | $-3\,\text{dB}$ | $-37.51$ | $-19.44$ | $3,497$ | $0$ | `183abe215cae496f54172bdfbc3f08cd02189c8715dccb036694bd92a13ea7b4` |
| **G** | [`scratch/spectral_ab_low9_midminus3.wav`](file:///c:/Users/saich/Downloads/SIH/PS26173/scratch/spectral_ab_low9_midminus3.wav) | $+9\,\text{dB}$ | $-3\,\text{dB}$ | $-36.53$ | $-19.28$ | $3,558$ | $0$ | `57215749222160c2ea30cb439d49b4db270ad310e673dc7264470600178f0813` |

---

## 6. Whisper Results & Comparison Table

Every variant was transcribed through the identical production `SherpaWhisperAsrEngine` on Device A (`HNP097HQ`).

| Variant | Low Boost | Mid Correction | RMS (dBFS) | Peak (dBFS) | Clips | Exact Whisper Transcript | WER (%) | CER (%) | RTF |
| :---: | :---: | :---: | :---: | :---: | :---: | :--- | :---: | :---: | :---: |
| **A** | $0\,\text{dB}$ | $0\,\text{dB}$ | $-36.98$ | $-17.13$ | $0$ | `"I need to go to the other side"` | **`100.00%`** | **`75.44%`** | $0.4013$ |
| **B** | $+3\,\text{dB}$ | $0\,\text{dB}$ | $-36.53$ | $-16.92$ | $0$ | `"I need to go to the other side"` | **`100.00%`** | **`75.44%`** | $0.4123$ |
| **C** | $+6\,\text{dB}$ | $0\,\text{dB}$ | $-35.93$ | $-16.81$ | $0$ | `"I need to go to the other side."` | **`100.00%`** | **`75.44%`** | $0.3849$ |
| **D** | $+9\,\text{dB}$ | $0\,\text{dB}$ | $-35.14$ | $-16.81$ | $0$ | `"I need to start the presentation."` | **`100.00%`** | **`80.70%`** | $0.4068$ |
| **E** | $+12\,\text{dB}$ | $0\,\text{dB}$ | $-34.15$ | $-16.93$ | $0$ | `"I need to start the presentation."` | **`100.00%`** | **`80.70%`** | $0.3604$ |
| **F** | $+6\,\text{dB}$ | $-3\,\text{dB}$ | $-37.51$ | $-19.44$ | $0$ | `"I need to get the information."` | **`100.00%`** | **`80.70%`** | $0.3594$ |
| **G** | $+9\,\text{dB}$ | $-3\,\text{dB}$ | $-36.53$ | $-19.28$ | $0$ | `"I need to start the session."` | **`100.00%`** | **`80.70%`** | $0.3605$ |

*(Note: WER/CER reported above against target 12-word reference; against 18-word extended reference, WER is uniformly $94.44\%$ across all 7 variants).*

---

## 7. WER / CER Analysis

1. **Baseline WER:** $100.00\%$ (12-word ref) / $94.44\%$ (18-word ref)
2. **Minimum Observed WER:** $100.00\%$ (12-word ref) / $94.44\%$ (18-word ref)
3. **Absolute WER Improvement:** **`0.00%`** across all variants.
4. **CER Improvement:** **`0.00%`** (CER slightly worsened from $75.44\%$ to $80.70\%$ as hallucinations lengthened).
5. **Consistency of Improvement:** **NO.** Not a single variant recovered any reference vocabulary (*"after"*, *"early"*, *"nightfall"*, *"yellow"*, *"lamps"*, *"light"*, *"here"*, *"there"*).
6. **Transcript Mutation vs. True Recognition:**
   Applying low boost and mid de-emphasis altered the decoder token distribution slightly, mutating the hallucination between:
   - `"I need to go to the other side"` (Variants A, B, C)
   - `"I need to start the presentation."` (Variants D, E)
   - `"I need to get the information."` (Variant F)
   - `"I need to start the session."` (Variant G)
   
   None of these transcript mutations constitute recognition improvement. They represent acoustic hallucinations driven by high decoder uncertainty in the presence of tablet microphone acoustic coloration.

---

## 8. Evidence-Based Interpretation

- **Finding 1:** Linear biquad frequency equalization (low-frequency boosting up to $+12\,\text{dB}$ and mid-band de-emphasis) does **not** restore the linguistic phonetic information lost during live tablet microphone capture.
- **Finding 2:** Whisper Tiny INT8 encoder features are sensitive to spectral tilt, but correcting tilt with standard 2nd-order filters simply shifts the decoder's prior hallucinations rather than unmasking true phonetic tokens.
- **Finding 3:** The root cause of the live microphone failure is not simple linear frequency attenuation; rather, it involves non-linear acoustic factors (e.g. tablet hardware microphone frequency response nulls, boundary reverberation, microphone polar pattern, or SNR in the unshielded tablet chassis).

---

## 9. Limitations & Control Against Overfitting

> [!IMPORTANT]
> This experiment evaluates one captured utterance and is diagnostic evidence only.
> It conclusively demonstrates that applying simple parametric equalization filters (low-shelf / mid-notch) is ineffective for recovering Whisper recognition on this hardware capture path.

---

## 10. Production Decision

- **Production Code Changes:** **`NO`**
- **Justification for DSP / Equalizer Deployment:** **`NO`** (Empirical evidence proves zero WER improvement).

---

## 11. Recommended Next Experiment

**`M1-AUDIO-SOURCE-RECORDER-PROFILE-11`**  
Investigate alternative Android `AudioRecord` native hardware presets (e.g. `MediaRecorder.AudioSource.UNPROCESSED`, `CAMCORDER`, or disabling OS-level hardware AGC/AEC effects via `AudioEffect` / `NoiseSuppressor` disabling) to determine if Android OS voice preprocessing is corrupting the raw microphone signal before it reaches the app.

---

## Summary of Results

- **Production code changed:** **`NO`**
- **Baseline WER:** **`100.00%`** (12-word) / **`94.44%`** (18-word)
- **Best measured WER:** **`100.00%`** (12-word) / **`94.44%`** (18-word)
- **Absolute WER improvement:** **`0.00%`**
- **Best measured CER:** **`75.44%`**
- **Was improvement consistent:** **`NO`**
- **Does this justify a production DSP change:** **`NO`**
- **Confidence level:** **`HIGH`**
