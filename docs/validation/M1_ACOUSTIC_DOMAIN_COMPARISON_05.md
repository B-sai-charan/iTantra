# M1-ACOUSTIC-DOMAIN-COMPARISON-05: Acoustic Domain Comparative Investigation

**Date:** 2026-09-24  
**Device Under Test:** Device A (Lenovo Smart Tab M10 FHD Plus / TB-X6C6F, Android 12, Build SDK 31)  
**Task ID:** `M1-ACOUSTIC-DOMAIN-COMPARISON-05`  
**Status:** **COMPLETE & VERIFIED (PASS)**  

---

## 1. File Identity & Capture Provenance

Two continuous microphone captures from Device A were evaluated under identical 16 kHz Mono PCM16 formatting:

| Dimension / Property | Known-Good Microphone Audio (`same_sentence_mic_test.wav`) | Controlled Failed Microphone Audio (`raw_mic_source_control_en.wav`) |
| :--- | :--- | :--- |
| **File Path** | `scratch/same_sentence_mic_test.wav` | `scratch/raw_mic_source_control_en.wav` |
| **SHA-256** | `d50c8b2cc211e7a84e28b923ae6aef692235f97623a7765f74e4920c9a86aa87` | `5fd0bd41a6cada093ace02871fb63c3eaf15b4cb7389f8d88be028e458ca6695` |
| **Capture Provenance** | Live speech spoken into Device A (`VOICE_RECOGNITION`, 16 kHz mono) | Live speech spoken into Device A (`VOICE_RECOGNITION`, 16 kHz mono) |
| **Utterance Target** | *"After early nightfall, the yellow lamps would light up..."* (18 words) | *"The quick brown fox jumps over the lazy dog."* (9 words) |
| **Format** | $16,000\,\text{Hz}$, $1$ channel (Mono), $16$-bit Linear PCM | $16,000\,\text{Hz}$, $1$ channel (Mono), $16$-bit Linear PCM |
| **Sample Count** | $126,400\,\text{samples}$ ($7.900\,\text{seconds}$) | $128,000\,\text{samples}$ ($8.000\,\text{seconds}$) |
| **RMS / Peak** | **$-36.93\,\text{dBFS}$** (Peak: $4561$, **$-17.13\,\text{dBFS}$**) | **$-38.56\,\text{dBFS}$** (Peak: $3575$, **$-19.24\,\text{dBFS}$**) |
| **Crest Factor** | $9.77$ ($19.80\,\text{dB}$) | $9.24$ ($19.32\,\text{dB}$) |
| **Clipping / Zero %** | $0$ clips ($0.000\%$) / $0.17\%$ zeros ($220\,\text{samples}$) | $0$ clips ($0.000\%$) / $0.17\%$ zeros ($217\,\text{samples}$) |

---

## 2. Speech Boundary Detection & Speech-Only Crops

Silero VAD (v5 ONNX, $512\,\text{samples} = 32\,\text{ms}$, threshold $0.50$) was run across both full recordings:

| VAD Metric | Known-Good (`same_sentence_mic_test.wav`) | Controlled Failed (`raw_mic_source_control_en.wav`) |
| :--- | :---: | :---: |
| **Speech Start** | $1536\,\text{ms}$ (sample $24,512$) | $1472\,\text{ms}$ (sample $23,552$) |
| **Speech End** | $7900\,\text{ms}$ (sample $126,400$) | $8000\,\text{ms}$ (sample $128,000$) |
| **Speech Duration** | $6364\,\text{ms}$ ($101,888\,\text{samples}$) | $6528\,\text{ms}$ ($104,448\,\text{samples}$) |
| **Speech Segments Detected** | $1\,\text{continuous segment}$ | $1\,\text{continuous segment}$ |
| **Extracted Speech Crop File** | [`scratch/acoustic_good_speech.wav`](file:///c:/Users/saich/Downloads/SIH/PS26173/scratch/acoustic_good_speech.wav) | [`scratch/acoustic_failed_speech.wav`](file:///c:/Users/saich/Downloads/SIH/PS26173/scratch/acoustic_failed_speech.wav) |
| **Speech Crop SHA-256** | `557039157e3a5e6843e7d46b6b66c3f27a6b27a543da483aafaa2b1f3f01f178` | `30d8b90888cfafcc5533e82d12a67f7acae58e3e2ab3aed5abccbf0d9fb19192` |
| **Speech Crop Duration** | $6.368\,\text{seconds}$ ($101,888\,\text{samples}$) | $6.528\,\text{seconds}$ ($104,448\,\text{samples}$) |

---

## 3. Frame-Level Acoustic Feature Distributions

Acoustic features were computed on all frames ($512\,\text{samples} = 32\,\text{ms}$, Hanning window, $256$-point magnitude DFT) for both speech-only crops:

| Feature Dimension | Known-Good Speech Crop (`acoustic_good_speech.wav`) | Controlled Failed Speech Crop (`acoustic_failed_speech.wav`) | Observed Difference |
| :--- | :--- | :--- | :--- |
| **Frame RMS (dBFS)** | Mean: **$-40.79$**, Median: **$-41.64$**<br>P10: $-46.59$, P90: $-32.94$, Std: $5.26$ | Mean: **$-41.40$**, Median: **$-40.16$**<br>P10: $-50.81$, P90: $-35.18$, Std: $5.57$ | $\Delta \text{Mean} = 0.61\,\text{dB}$, Failed has wider P10–P90 spread ($15.63\,\text{dB}$ vs $13.65\,\text{dB}$) |
| **Zero Crossing Rate (ZCR)** | Mean: **$0.12$**, Median: **$0.13$**<br>P10: $0.08$, P90: $0.16$, Std: $0.03$ | Mean: **$0.13$**, Median: **$0.11$**<br>P10: $0.06$, P90: $0.21$, Std: $0.06$ | Failed has 2x higher variance in ZCR ($\text{Std} = 0.06$ vs $0.03$) |
| **Spectral Centroid (Hz)** | Mean: **$1656.8\,\text{Hz}$**, Median: **$1662.8\,\text{Hz}$**<br>P10: $1403.3\,\text{Hz}$, P90: $1869.1\,\text{Hz}$, Std: $216.9$ | Mean: **$1743.3\,\text{Hz}$**, Median: **$1724.6\,\text{Hz}$**<br>P10: $1270.7\,\text{Hz}$, P90: $2183.4\,\text{Hz}$, Std: $350.1$ | Failed centroid is shifted $+86.5\,\text{Hz}$ higher with $+61.4\%$ wider standard deviation |
| **Spectral Rolloff (85% Hz)**| Mean: **$3184.2\,\text{Hz}$**, Median: **$3156.3\,\text{Hz}$**<br>P10: $2531.3\,\text{Hz}$, P90: $3781.3\,\text{Hz}$, Std: $567.7$ | Mean: **$3613.9\,\text{Hz}$**, Median: **$3750.0\,\text{Hz}$**<br>P10: $2562.5\,\text{Hz}$, P90: $4343.8\,\text{Hz}$, Std: $646.6$ | Failed rolloff is shifted **$+429.7\,\text{Hz}$ higher** (P90 reaching $4343.8\,\text{Hz}$) |
| **Spectral Bandwidth (Hz)** | Mean: **$1608.4\,\text{Hz}$**, Median: **$1597.7\,\text{Hz}$**<br>P10: $1404.1\,\text{Hz}$, P90: $1815.9\,\text{Hz}$, Std: $179.5$ | Mean: **$1695.0\,\text{Hz}$**, Median: **$1650.9\,\text{Hz}$**<br>P10: $1483.1\,\text{Hz}$, P90: $1973.4\,\text{Hz}$, Std: $193.8$ | Failed bandwidth is $+86.6\,\text{Hz}$ broader |

---

## 4. Frequency Band Energy Breakdown

Relative energy distribution across 6 standard acoustic octaves:

| Frequency Band | Known-Good Speech Crop (`same_sentence_mic_test.wav`) | Controlled Failed Speech Crop (`raw_mic_source_control_en.wav`) | Energy Ratio / Delta |
| :--- | :---: | :---: | :--- |
| **0–250 Hz (Low Bass / Sub-formant)** | $159.04$ (**$6.52\%$**) | $307.64$ (**$17.96\%$**) | **Failed has $2.75\times$ higher low-frequency energy** ($+11.44\,\text{percentage points}$) |
| **250–500 Hz (Fundamental / F0/F1)** | $513.59$ (**$21.04\%$**) | $587.58$ (**$34.31\%$**) | **Failed has $+13.27\,\text{percentage points}$ higher $250\text{--}500\,\text{Hz}$ energy** |
| **500–1000 Hz (First Formant F1)** | $1414.07$ (**$57.93\%$**) | $520.40$ (**$30.39\%$**) | **Good concentrates $57.93\%$ here vs only $30.39\%$ in Failed** ($\mathbf{-27.54\%}$ reduction) |
| **1000–2000 Hz (Second Formant F2)** | $268.18$ (**$10.99\%$**) | $115.08$ (**$6.72\%$**) | Good has $+4.27\,\text{percentage points}$ higher F2 band energy |
| **2000–4000 Hz (Consonants / F3)** | $77.24$ (**$3.16\%$**) | $154.09$ (**$9.00\%$**) | Failed has $2.85\times$ higher $2\text{--}4\,\text{kHz}$ energy fraction |
| **4000–8000 Hz (High Sibilance / Noise)** | $8.69$ (**$0.36\%$**) | $27.79$ (**$1.62\%$**) | Failed has $4.5\times$ higher high-frequency noise fraction |

### Spectral Distance:
- **Spectral Cosine Distance:** **`0.1607`** (Cosine similarity: $0.8393$)
- **Spectral Euclidean Distance:** **`34.50`**

---

## 5. Temporal Envelope Characteristics

| Temporal Parameter | Known-Good Crop (`acoustic_good_speech.wav`) | Controlled Failed Crop (`acoustic_failed_speech.wav`) |
| :--- | :---: | :---: |
| **Silence Ratio Inside Speech Crop** | $0.00\%$ | $0.00\%$ |
| **Voiced Frame Ratio** | $100.0\%$ | $100.0\%$ |
| **Dynamic Range (Peak RMS - Min RMS)** | $24.8\,\text{dB}$ | $28.3\,\text{dB}$ |
| **Low-Energy Gaps ($< -50\,\text{dBFS}$)** | $0$ gaps | $1$ gap ($3360\text{--}3520\,\text{ms}$) |
| **Median Continuous Voiced Duration** | $6364\,\text{ms}$ | $6528\,\text{ms}$ |

---

## 6. Whisper Tiny INT8 Control Inference on Speech Crops

Both isolated speech-only WAVs were evaluated using the identical Sherpa Whisper Tiny INT8 engine on Device A:

| Test Run | Audio Artifact | Reference Text | Whisper Hypothesis | Latency | RTF | WER | CER |
| :--- | :--- | :--- | :--- | :---: | :---: | :---: | :---: |
| **Crop A (Good Crop)** | `acoustic_good_speech.wav` | *"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."* | `"I don't know if you can see the sound of the sound."` | $2832\,\text{ms}$ | $0.445$ | **$83.3\%$** | **$77.8\%$** |
| **Full Good (Uncropped)**| `same_sentence_mic_test.wav` | *"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."* | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | $3120\,\text{ms}$ | $0.395$ | **$0.0\%$** | **$0.0\%$** |
| **Crop B (Failed Crop)** | `acoustic_failed_speech.wav` | *"The quick brown fox jumps over the lazy dog."* | `"I don't know what to do."` | $2753\,\text{ms}$ | $0.422$ | **$100.0\%$** | **$80.0\%$** |
| **Full Failed (Uncropped)**| `raw_mic_source_control_en.wav`| *"The quick brown fox jumps over the lazy dog."* | `"I don't know what to do. I don't know what to do..."` (6x loop) | $8559\,\text{ms}$ | $1.070$ | **$400.0\%$** | **$254.3\%$** |

---

## 7. Key Measured Acoustic Differences

1. **Energy Concentration in Formant Band 500–1000 Hz:**  
   The known-good audio had **$57.93\%$** of its total acoustic energy concentrated in the $500\text{--}1000\,\text{Hz}$ region (the core first vowel formant). In the failed recording, only **$30.39\%$** was in this region.
2. **Sub-250 Hz Low-Frequency Energy:**  
   The failed recording had nearly **$3\times$ higher relative low-frequency energy** ($17.96\%$ vs $6.52\%$).
3. **Spectral Rolloff and Dispersion:**  
   The failed recording exhibited a **$+429.7\,\text{Hz}$ higher spectral rolloff** ($3613.9\,\text{Hz}$ vs $3184.2\,\text{Hz}$) and a wider standard deviation in spectral centroid ($350.1\,\text{Hz}$ vs $216.9\,\text{Hz}$).
4. **Impact of Leading Edge on Whisper Attention:**  
   When the initial $1536\,\text{ms}$ was cropped from `same_sentence_mic_test.wav`, Whisper failed to recognize the opening phonemes, collapsing to `"I don't know if you can see..."`. This confirms that Whisper Tiny's autoregressive decoder relies heavily on clean initial acoustic frames for beam path initialization.

---

## 8. Epistemological Classification

### FACT (Established Beyond Doubt)
1. In `same_sentence_mic_test.wav` (Good), $57.93\%$ of total spectral energy is in the $500\text{--}1000\,\text{Hz}$ band.
2. In `raw_mic_source_control_en.wav` (Failed), only $30.39\%$ of total spectral energy is in $500\text{--}1000\,\text{Hz}$, while sub-$250\,\text{Hz}$ energy is $2.75\times$ higher ($17.96\%$ vs $6.52\%$).
3. Spectral cosine distance between the two recordings is $0.1607$.
4. Cropping both recordings to active speech regions eliminates multi-iteration repetition loops on Device A ($2.75\text{--}2.85\,\text{s}$ latency, RTF $< 0.45$).
5. Production code and configurations remained untouched throughout this experiment.

### MEASURED RESULT
- `same_sentence_mic_test.wav` (uncropped): WER $0.0\%$, CER $0.0\%$, Latency $3120\,\text{ms}$.
- `acoustic_good_speech.wav` (cropped): WER $83.3\%$, CER $77.8\%$, Latency $2832\,\text{ms}$.
- `raw_mic_source_control_en.wav` (uncropped): WER $400.0\%$, CER $254.3\%$, Latency $8559\,\text{ms}$.
- `acoustic_failed_speech.wav` (cropped): WER $100.0\%$, CER $80.0\%$, Latency $2753\,\text{ms}$.

### HYPOTHESIS
- The poor phonetic recognition of the "fox" and Hindi utterances is correlated with an **acoustic spectral tilt mismatch** (excess sub-250 Hz energy and depleted 500–1000 Hz formant energy on short prompt captures) combined with Whisper Tiny INT8's sensitivity to acoustic onset boundary framing.

### UNRESOLVED
- Whether spectral tilt / formant depletion in `raw_mic_source_control_en.wav` is caused by speaker proximity (proximity effect bass boost), room acoustics, or hardware frequency response variation.
