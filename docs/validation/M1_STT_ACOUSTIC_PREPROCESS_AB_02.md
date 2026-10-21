# M1-STT-ACOUSTIC-PREPROCESS-AB-02: Acoustic Preprocessing A/B Diagnostic Report

**Document Version:** 1.0.0  
**Date:** September 28, 2026  
**Task ID:** `M1-STT-ACOUSTIC-PREPROCESS-AB-02`  
**Device Under Test:** Device A (Lenovo Smart Tab M10 FHD Plus / `TB-X6C6F` / `HNP097HQ`, Android 12, API 31, `arm64-v8a`)  
**Evaluation Mode:** Strict Diagnostic-Only A/B Evaluation (Zero Production / Runtime Modifications)  
**Status:** **COMPLETE & VERIFIED (FAIL — PREPROCESSING DOES NOT SOLVE MISRECOGNITION)**

---

## 1. Objective & Methodological Design

This diagnostic tests whether applying controlled digital acoustic preprocessing to existing captured microphone recordings can improve Whisper-Tiny STT accuracy without modifying production code, VAD, or model architectures.

### Target Linguistic Reference Sentence:
```text
"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
```
*(18 reference words, 87 non-space characters, LibriSpeech EN-001 reference text)*

### Source WAV Files Evaluated:
1. **Source 1:** `same_sentence_mic_test.wav` ($16,000\,\text{Hz}$, Mono PCM16, $126,400\,\text{samples} = 7.900\,\text{s}$)
2. **Source 2:** `mic_loopback_ref_test.wav` ($16,000\,\text{Hz}$, Mono PCM16, $126,720\,\text{samples} = 7.920\,\text{s}$)

---

## 2. Preprocessing Filter Specifications

All filter variants were generated in `scratch/` using 64-bit floating point digital IIR biquad filters before conversion back to 16-bit linear PCM:

1. **Variant A (Original):**
   - Exact raw PCM16 samples as captured on hardware.
2. **Variant B (150 Hz High-Pass Filter):**
   - Filter: 2nd-order Butterworth High-Pass Filter.
   - Cutoff Frequency ($f_c$): $150.0\,\text{Hz}$ ($Q = 0.7071$).
   - Purpose: Attenuate sub-bass mechanical/environmental rumble below vocal fundamental range.
3. **Variant C (150 Hz High-Pass + 800 Hz Parametric Formant Boost):**
   - Cascaded 150 Hz 2nd-order High-Pass + 2nd-order Peaking / Parametric Biquad Equalizer.
   - Center Frequency ($f_0$): $800.0\,\text{Hz}$.
   - Quality Factor ($Q$): $1.0$ (Bandwidth $\approx 800\,\text{Hz}$, covering $500\text{--}1200\,\text{Hz}$ first formant band).
   - Peak Gain: $+6.0\,\text{dB}$ ($A = 10^{6/40} \approx 1.995$).
   - Purpose: Compensate for measured $500\text{--}1000\,\text{Hz}$ formant energy depletion without clipping.

---

## 3. On-Device Empirical Results Matrix

Every variant was transcribed using the production [`SherpaWhisperAsrEngine`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/stt/SherpaWhisperAsrEngine.kt) (Whisper Tiny INT8, greedy search, `lang = "en"`) on Device A:

| Source Audio | Variant | RMS (dBFS) | Peak (dBFS) | Clipping Count | Actual Whisper Hypothesis | WER (%) | CER (%) | Latency (ms) | RTF |
| :--- | :--- | :---: | :---: | :---: | :--- | :---: | :---: | :---: | :---: |
| **Source 1** (`same_sentence`) | **A (Original)** | $-36.93$ | $-17.13$ | $0$ | `"I need to get the information."` | **$94.44\%$** | **$83.91\%$** | $2846\,\text{ms}$ | $0.360$ |
| **Source 1** (`same_sentence`) | **B (HP 150Hz)** | $-37.03$ | $-17.28$ | $0$ | `"I need to be a better patient."` | **$100.00\%$** | **$82.76\%$** | $3036\,\text{ms}$ | $0.384$ |
| **Source 1** (`same_sentence`) | **C (HP + Boost 800Hz)** | $-32.81$ | $-12.31$ | $0$ | `"I need to start the presentation."` | **$94.44\%$** | **$82.76\%$** | $3004\,\text{ms}$ | $0.380$ |
| **Source 2** (`mic_loopback`) | **A (Original)** | $-39.78$ | $-20.03$ | $0$ | `"I'm just using it."` | **$100.00\%$** | **$90.80\%$** | $2724\,\text{ms}$ | $0.344$ |
| **Source 2** (`mic_loopback`) | **B (HP 150Hz)** | $-39.87$ | $-19.87$ | $0$ | `"I'm going to talk to you in the kitchen."` | **$94.44\%$** | **$80.46\%$** | $3241\,\text{ms}$ | $0.409$ |
| **Source 2** (`mic_loopback`) | **C (HP + Boost 800Hz)** | $-36.68$ | $-15.29$ | $0$ | `"I'm just gonna go to the bathroom"` | **$94.44\%$** | **$83.91\%$** | $2975\,\text{ms}$ | $0.376$ |

---

## 4. Quantitative Analysis

### ORIGINAL BASELINE:
- **Source 1 (Variant A):** WER **$94.44\%$**, CER **$83.91\%$**, Transcript: `"I need to get the information."`
- **Source 2 (Variant A):** WER **$100.00\%$**, CER **$90.80\%$**, Transcript: `"I'm just using it."`

### BEST VARIANT:
- **Source 1:** Variant A / Variant C (WER **$94.44\%$**, CER **$82.76\text{--}83.91\%$**)
- **Source 2:** Variant B (WER **$94.44\%$**, CER **$80.46\%$**)

### WER CHANGE:
- **Source 1:** $94.44\% \rightarrow 100.00\% \rightarrow 94.44\%$ ($\mathbf{0.00\%}$ net improvement; $+5.56\%$ regression on HP)
- **Source 2:** $100.00\% \rightarrow 94.44\% \rightarrow 94.44\%$ ($\mathbf{-5.56\%}$ trivial shift from 1 deletion substitution, 0 reference words recovered)

### CER CHANGE:
- **Source 1:** $83.91\% \rightarrow 82.76\%$ ($\mathbf{-1.15\%}$ delta)
- **Source 2:** $90.80\% \rightarrow 80.46\%$ ($\mathbf{-10.34\%}$ delta)

### REPRODUCIBILITY:
- **Did the same preprocessing improve BOTH source recordings?**  
  **`NO.`** On Source 1, High-Pass filtering regressed WER ($94.44\% \rightarrow 100.00\%$), while adding the 800 Hz boost returned it to baseline ($94.44\%$). On Source 2, filtering shifted the hallucinated sentence without decoding a single word from the actual reference text.

---

## 5. INTERPRETATION

### MEASURED RESULT:
1. Digital 150 Hz high-pass filtering and 800 Hz parametric equalization changed the acoustic energy distribution (raising RMS by $+4.12\,\text{dB}$ and $+3.10\,\text{dB}$ in Variant C) with zero clipping ($0$ clipped samples).
2. The acoustic filtering altered the specific hallucinated phrase emitted by Whisper (e.g. `"I'm just using it"` $\rightarrow$ `"I'm going to talk to you in the kitchen"` $\rightarrow$ `"I'm just gonna go to the bathroom"`), but **failed to recover any words from the reference sentence** (*"After early nightfall, the yellow lamps..."*).
3. Across all 6 evaluations, WER remained between **$94.44\%$ and $100.00\%$**, proving that linear digital filtering alone does not restore speech intelligibility for degraded microphone audio on Whisper-Tiny.

### HYPOTHESIS:
1. Linear post-capture filtering (equalization/high-pass) cannot reconstruct missing phonemic information or improve the effective Signal-to-Noise Ratio (SNR) when the fundamental acoustic capture is compromised by acoustic path distance / ambient reverberation.
2. Whisper-Tiny's log-Mel filterbank and self-attention encoder require genuine high-SNR acoustic speech onsets rather than digitally boosted noise/formant shapes.

### UNRESOLVED:
1. Whether physical speaker proximity ($5\text{--}10\,\text{cm}$ direct speech into the tablet microphone port) or non-linear multi-band spectral subtraction (noise suppression DSP) is required to cross Whisper-Tiny's recognition threshold.

---

## 6. DECISION RULE EVALUATION

| Criterion | Requirement | Result | Evaluation |
| :--- | :--- | :--- | :---: |
| **1. WER Improvement** | Must improve materially | WER remained $94.44\%\text{--}100.00\%$ (0 reference words recovered) | **FAIL** |
| **2. CER Improvement** | Must provide supporting evidence | Minor fluctuations ($80\%\text{--}83\%$) on hallucinated text | **FAIL** |
| **3. Clipping Check** | No clipping introduced | $0$ clipped samples across all variants | **PASS** |
| **4. Reproducibility** | Must improve both source recordings | Inconsistent behavior across recordings | **FAIL** |

### Decision:
$$\mathbf{DO\ NOT\ CHANGE\ PRODUCTION\ CODE}$$

---

## 7. Epistemological Summary

- **FACT:** All 6 variants were processed with identical mathematical parameters, zero clipping, and evaluated on Device A's native Whisper-Tiny INT8 runtime.
- **FACT:** Digital filtering does not close the WER gap on live microphone captures.
- **CONCLUSION:** Preprocessing must NOT be added to the production audio pipeline.
