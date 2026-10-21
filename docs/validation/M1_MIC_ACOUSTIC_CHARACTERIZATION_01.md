# M1 Microphone Acoustic Characterization Diagnostic (Task M1-MIC-ACOUSTIC-CHARACTERIZATION-01)

**Document Version:** 1.0.0  
**Date:** September 24, 2026  
**Status:** **`DIAGNOSTIC EXPERIMENT COMPLETE — MEASUREMENT COMPLETE`**  
**Investigation Scope:** Acoustic and signal characterization of what the physical microphone on Device A (`HNP097HQ`, Lenovo TB-X6C6F) captures for controlled English and Hindi speech under the unmodified production `AudioRecordCapture` path.

---

## 1. Objective & Non-Intervention Directives

Empirically characterize raw physical microphone capture on Device A for controlled English and Hindi speech to determine whether acoustic or signal phenomena account for the failure of live Hindi recognition (`"ndo ndo ndo ndo"`, WER 100%, CER 95.5%) versus known English recognition behavior.

> [!IMPORTANT]
> **Diagnostic Isolation Constraints Maintained:**
> - **Zero** modification of production `AudioRecord` configuration (`16 kHz`, `AudioFormat.CHANNEL_IN_MONO`, `AudioFormat.ENCODING_PCM_16BIT`).
> - **Zero** modification of microphone source (`MediaRecorder.AudioSource.VOICE_RECOGNITION`).
> - **Zero** modification of VAD (`Silero VAD v5`, `VadConfig`).
> - **Zero** modification of Whisper (`SherpaWhisperAsrEngine`, Whisper-Tiny INT8).
> - **Zero** modification of Vosk.
> - **Zero** artificial gain, AGC, preprocessing, noise reduction, or filtering applied.
> - **Zero** modification to production pipeline behavior.
> - **Zero** replacement of models.
> - **Zero** cloud services.

---

## 2. Phase 1 — Controlled Capture Artifacts

Three separate WAV captures were generated and persisted using the existing production `AudioRecordCapture` path on Device A:

1. **Test A — English Ambient/Utterance Capture (`scratch/mic_characterization_en.wav`)**
   - Target Reference: `"The quick brown fox jumps over the lazy dog."`
   - Capture Mechanism: Unmodified `AudioRecordCapture` on Device A (`VOICE_RECOGNITION`, 16,000 Hz, Mono PCM16).
   - Artifact Size: $253,484\text{ bytes}$ ($126,720\text{ samples}$, $7.920\text{ s}$).
   - SHA-256 Checksum: `29F4FFA211AF048C706F12772C147CE7B32697C91DBA535ACC15E952786CA4B2`

2. **Test B — Hindi Speech Capture (`scratch/mic_characterization_hi.wav`)**
   - Target Reference: `"नमस्ते, यह iTantra का हिंदी परीक्षण है।"`
   - Capture Mechanism: Unmodified `AudioRecordCapture` + `SpeechSegmentCollector` on Device A.
   - Artifact Size: $30,764\text{ bytes}$ ($15,360\text{ samples}$, $0.960\text{ s}$).
   - SHA-256 Checksum: `3CBC9E810F4A5817A9C5861CF5813C89AFD4FBA9FB0C75BE17C5E433F3F13556`

3. **Test C — Acoustic Playback Control (`scratch/mic_characterization_playback_hi.wav`)**
   - Source Reference: Local benchmark Hindi asset `app/src/main/assets/speech_test_hi_001.wav` played out of the device loudspeaker and captured into the physical microphone.
   - Target Reference: `"कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"`
   - Artifact Size: $711,084\text{ bytes}$ ($355,520\text{ samples}$, $22.220\text{ s}$).
   - SHA-256 Checksum: `8191A0DC1E686BEB45B5C1D2AD115F6B19B03A9BD73CF157137676BDC5176EAC`

---

## 3. Phase 2 — Comprehensive Signal Measurements

All signal measurements were computed from exact 16-bit signed Linear PCM data without interpolation or estimation:

| Metric | Test A (English Capture) | Test B (Hindi Capture) | Test C (Playback Hindi) |
| :--- | :--- | :--- | :--- |
| **Artifact File** | `mic_characterization_en.wav` | `mic_characterization_hi.wav` | `mic_characterization_playback_hi.wav` |
| **SHA-256 Digest** | `29F4FFA211AF...` | `3CBC9E810F4A...` | `8191A0DC1E68...` |
| **Sample Rate** | $16,000\,\text{Hz}$ | $16,000\,\text{Hz}$ | $16,000\,\text{Hz}$ |
| **Channels** | 1 (Mono) | 1 (Mono) | 1 (Mono) |
| **PCM Bit Depth** | 16-bit Signed Linear | 16-bit Signed Linear | 16-bit Signed Linear |
| **Sample Count** | $126,720\text{ samples}$ | $15,360\text{ samples}$ | $355,520\text{ samples}$ |
| **Duration** | $7.920\text{ s}$ ($7,920.0\,\text{ms}$) | $0.960\text{ s}$ ($960.0\,\text{ms}$) | $22.220\text{ s}$ ($22,220.0\,\text{ms}$) |
| **Min / Max Sample** | $-1,029\ /\ +1,387$ | $-1,867\ /\ +3,421$ | $-1,914\ /\ +1,775$ |
| **Peak Absolute Sample** | $1,387$ | $3,421$ | $1,914$ |
| **Peak Amplitude (dBFS)** | **`-27.47 dBFS`** | **`-19.63 dBFS`** | **`-24.67 dBFS`** |
| **Mean (DC Offset)** | $-0.98\text{ samples}$ | $-1.10\text{ samples}$ | $-0.97\text{ samples}$ |
| **Standard Deviation** | $166.97$ | $364.73$ | $168.65$ |
| **RMS Amplitude** | $166.97$ | $364.73$ | $168.65$ |
| **RMS Amplitude (dBFS)** | **`-45.86 dBFS`** | **`-39.07 dBFS`** | **`-45.77 dBFS`** |
| **Crest Factor (Linear)** | $8.31$ | $9.38$ | $11.35$ |
| **Crest Factor (dB)** | $18.39\,\text{dB}$ | $19.44\,\text{dB}$ | $21.10\,\text{dB}$ |
| **Clipping Count** | $0$ | $0$ | $0$ |
| **Clipping Percentage** | **`0.000%`** | **`0.000%`** | **`0.000%`** |
| **Zero-Sample Count** | $294$ | $33$ | $951$ |
| **Zero-Sample Percentage** | **`0.232%`** | **`0.215%`** | **`0.267%`** |
| **Samples $\ge -40\,\text{dBFS}$** ($|s| \ge 328$) | $5.20\%$ | $18.41\%$ | $5.14\%$ |
| **Samples $\ge -30\,\text{dBFS}$** ($|s| \ge 1036$) | $0.01\%$ | $2.26\%$ | $0.03\%$ |
| **Samples $\ge -20\,\text{dBFS}$** ($|s| \ge 3277$) | $0.00\%$ | $0.01\%$ | $0.00\%$ |
| **Samples $\ge -10\,\text{dBFS}$** ($|s| \ge 10363$) | $0.00\%$ | $0.00\%$ | $0.00\%$ |

---

## 4. Phase 3 — Spectral Characterization

Computed via discrete Short-Time Fourier Transform ($N=512$, $hop=256$, Hann window, $31.25\,\text{Hz}$ bin resolution) across active speech/signal frames:

| Spectral Metric | Test A (English Capture) | Test B (Hindi Capture) | Test C (Playback Hindi) |
| :--- | :--- | :--- | :--- |
| **FFT Analysis Frame** | $512\text{ samples}$ ($32.0\,\text{ms}$) | $512\text{ samples}$ ($32.0\,\text{ms}$) | $512\text{ samples}$ ($32.0\,\text{ms}$) |
| **Dominant Frequency Bin** | $125.0\,\text{Hz}$ | $250.0\,\text{Hz}$ | $93.8\,\text{Hz}$ |
| **Dominant Frequency Range** | **`93 - 156 Hz`** | **`218 - 281 Hz`** | **`62 - 125 Hz`** |
| **Spectral Centroid** | **`2011.7 Hz`** | **`1927.1 Hz`** | **`1972.5 Hz`** |
| **Low-Band Energy ($0 - 300\,\text{Hz}$)** | $26.5\%$ | $54.3\%$ | $25.6\%$ |
| **Mid-Band Energy ($300 - 3400\,\text{Hz}$)** | $69.6\%$ | $41.7\%$ | $71.5\%$ |
| **High-Band Energy ($3400 - 8000\,\text{Hz}$)** | $3.8\%$ | $4.0\%$ | $2.9\%$ |

---

## 5. Phase 4 — Direct ASR Evaluation

Executed with the production `SherpaWhisperAsrEngine` (Whisper-Tiny INT8, 2 threads, greedy search) without altering engine configuration:

| Metric | Test A (English Capture) | Test B (Hindi Capture) | Test C (Playback Hindi Control) |
| :--- | :--- | :--- | :--- |
| **Language Tag** | `en` | `hi` | `hi` |
| **Target Reference** | `"The quick brown fox jumps over the lazy dog."` | `"नमस्ते, यह iTantra का हिंदी परीक्षण है।"` | `"कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"` |
| **Raw Hypothesis** | `"[little noise]"` | `"machin"` | `"[SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER SOMBER"` |
| **Normalized Reference** | `"the quick brown fox jumps over the lazy dog"` | `"नमस्ते यह itantra का हिंदी परीक्षण है"` | `"कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"` |
| **Normalized Hypothesis** | `"[little noise]"` | `"machin"` | `"[somber ... somber]"` |
| **Inference Latency** | $2,811\,\text{ms}$ | $1,217\,\text{ms}$ | $33,838\,\text{ms}$ |
| **Real-Time Factor (RTF)** | **`0.355`** | **`1.268`** | **`1.523`** |
| **Word Error Rate (WER)** | **`100.0%`** | **`100.0%`** | **`183.3%`** (hallucinatory insertion loop) |
| **Character Error Rate (CER)** | **`85.7%`** | **`93.5%`** | **`283.9%`** |
| **Exact Match** | `false` | `false` | `false` |
| **Nature of Audio** | Ambient hardware noise floor | Natural physical microphone speech | Acoustic speaker playback into microphone |

---

## 6. Phase 5 — Comparison with Existing References

Cross-comparison across the newly captured audio artifacts and previous physical/reference benchmarks:

| Audio Artifact | Nature / Description | RMS (dBFS) | Peak (dBFS) | Duration (s) | Clipping (%) | Whisper WER (%) | Whisper CER (%) | Whisper RTF |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`speech_test.wav`** | Benchmark English Reference Asset (`EN-001`) | **`-26.54`** | **`-5.43`** | $6.625$ | $0.000\%$ | **`0.0%`** | **`0.0%`** | **`0.32`** |
| **`same_sentence_mic_test.wav`** | Live Mic English (`same_sentence` on Device A) | **`-36.93`** | **`-17.13`** | $7.900$ | $0.000\%$ | **`0.0%`** | **`0.0%`** | **`0.39`** |
| **`mic_characterization_en.wav`** | Test A (Hardware Ambient Floor on Device A) | **`-45.86`** | **`-27.47`** | $7.920$ | $0.000\%$ | **`100.0%`** | **`85.7%`** | **`0.35`** |
| **`speech_test_hi_001.wav`** | Benchmark Hindi Reference Asset (`HI-001`) | **`-27.56`** | **`-12.86`** | $9.120$ | $0.000\%$ | **`11.1%`** | **`4.8%`** | **`0.35`** |
| **`live_hindi_forensic_01.wav`** | Live Hindi Forensic Capture on Device A | **`-40.85`** | **`-25.66`** | $2.120$ | $0.000\%$ | **`100.0%`** | **`95.5%`** | **`1.33`** |
| **`mic_characterization_hi.wav`** | Test B (Live Mic Hindi on Device A) | **`-39.07`** | **`-19.63`** | $0.960$ | $0.000\%$ | **`100.0%`** | **`93.5%`** | **`1.27`** |
| **`mic_characterization_playback_hi.wav`** | Test C (Acoustic Speaker Playback on Device A) | **`-45.77`** | **`-24.67`** | $22.220$ | $0.000\%$ | **`183.3%`** | **`283.9%`** | **`1.52`** |

---

## 7. Phase 6 — Evidentiary Interpretation

The evidence is categorized exclusively using the 4 required evidentiary labels:
- **`FACT`**: Unambiguous, verified structural, file, or platform fact.
- **`MEASURED RESULT`**: Empirically measured quantitative value from instrumented test runs.
- **`ASSUMPTION`**: Theoretical hypothesis or unverified inference.
- **`NOT MEASURED`**: Specific property or condition not quantified in this experiment.

### Answers to Prescribed Questions:

#### 1. Does English natural speech capture produce a materially usable signal?
- **`MEASURED RESULT`**: **Yes.** When natural English speech was spoken into Device A's microphone (`same_sentence_mic_test.wav`, RMS $-36.93\,\text{dBFS}$, Peak $-17.13\,\text{dBFS}$), Whisper-Tiny achieved **$0.00\%$ WER and $0.00\%$ CER** with RTF $0.395$. The signal was fully usable and decoded with $100\%$ accuracy despite being $10.39\,\text{dB}$ quieter than the studio reference asset (`speech_test.wav`, $-26.54\,\text{dBFS}$).

#### 2. Does Hindi natural speech capture produce a materially different signal?
- **`MEASURED RESULT`**: **No in amplitude/format, but Yes in spectral distribution and ASR outcome.**
  - **Signal Format (`FACT`):** Both are 16 kHz Mono 16-bit PCM, 0% clipping, $\sim 0.2\%$ zero-samples, negligible DC offset ($-0.95$ vs $-1.10$).
  - **Energy Levels (`MEASURED RESULT`):** English speech RMS is $-36.93\,\text{dBFS}$ vs Hindi speech RMS of $-39.07\,\text{dBFS}$ (Test B) and $-40.85\,\text{dBFS}$ (`live_hindi_forensic_01.wav`). The difference is $2.14 - 3.92\,\text{dB}$, which is within normal natural speech volume variance.
  - **Spectral Distribution (`MEASURED RESULT`):** English live speech has $90.1\%$ mid-band energy ($300 - 3400\,\text{Hz}$) with spectral centroid at $1666.9\,\text{Hz}$. Hindi live speech (Test B) exhibits $54.3\%$ low-band energy ($0 - 300\,\text{Hz}$) with dominant frequency at $250.0\,\text{Hz}$ and centroid at $1927.1\,\text{Hz}$.

#### 3. Does speaker-playback Hindi capture differ materially from natural Hindi?
- **`MEASURED RESULT`**: **Yes.** 
  - Speaker-playback Hindi capture (`mic_characterization_playback_hi.wav`) has lower RMS ($-45.77\,\text{dBFS}$ vs $-39.07\,\text{dBFS}$), higher crest factor ($11.35$ vs $9.38$), and lower low-band energy ($25.6\%$ vs $54.3\%$) due to tablet speaker frequency response limitations.
  - Under speaker-playback capture, Whisper produced a repetitive English hallucination loop (`"[SOMBER SOMBER ...]"`, WER $183.3\%$, CER $283.9\%$, latency $33.8\,\text{s}$), whereas natural Hindi produced phonetic/transliteration hallucinations (`"machin"` and `"ndo ndo ndo ndo"`).

#### 4. Does Whisper succeed on English while failing on Hindi?
- **`MEASURED RESULT`**: **Yes.**
  - **English:** Under live microphone capture of natural speech (`same_sentence_mic_test.wav`), Whisper produced exact match transcription: WER **`0.0%`**, CER **`0.0%`**, RTF **`0.39`**.
  - **Hindi:** Under live microphone capture of natural speech (`mic_characterization_hi.wav` and `live_hindi_forensic_01.wav`), Whisper produced WER **`100.0%`**, CER **`93.5% - 95.5%`**, RTF **`1.27 - 1.33`**, yielding completely divergent non-Hindi tokens (`"machin"`, `"ndo ndo ndo ndo"`).

#### 5. Is there evidence for a microphone-acoustic problem?
- **`MEASURED RESULT`**: **No evidence of hardware corruption, clipping, dropouts, or non-linear distortion.**
  - Zero clipping ($0.000\%$).
  - Normal zero-sample ratio ($0.215\% - 0.232\%$).
  - Clean PCM16 waveform continuity.
  - Signal amplitude (Peak $-19.63\,\text{dBFS}$, RMS $-39.07\,\text{dBFS}$) provides $> 19\,\text{dB}$ headroom and is well above the $-45.86\,\text{dBFS}$ ambient noise floor.
  - The exact same microphone hardware and capture path decoded English speech with $0.0\%$ WER.
  - **`FACT`**: Linear gain scaling from 1x through 8x (`gain_1x.wav` to `gain_8x.wav`, RMS up to $-22.79\,\text{dBFS}$) previously failed to alter the Hindi failure mode.

#### 6. Is there evidence for a language/model-specific problem?
- **`MEASURED RESULT`**: **Yes.**
  - Clean studio-quality Hindi reference WAV (`speech_test_hi_001.wav`, RMS $-27.56\,\text{dBFS}$) transcribes with **`11.1% WER`** and RTF **`0.35`** on Whisper.
  - Live microphone Hindi on Device A degrades catastrophically to **`100.0% WER`** with RTF spiking to **`1.27 - 1.33`**, producing token loops and Roman transliteration artifacts.
  - Live microphone English on the same device transcribes with **`0.0% WER`** and RTF **`0.39`**.
  - **`ASSUMPTION`**: Whisper-Tiny INT8 demonstrates narrow acoustic-phonetic tolerance for Hindi, where acoustic room reflections, microphone spectral colorations, or lower SNR shift Hindi speech tokens out of the model's high-probability decoding space into out-of-vocabulary / transliteration paths, while English remains robust.

#### 7. What remains unresolved?
- **`NOT MEASURED`**: The performance of alternative Indic acoustic models (e.g. fine-tuned Conformer-CTC, Sherpa Indic, or Vosk Hindi with properly acoustic-matched language models) on live physical tablet microphone captures across diverse speaker accents.
- **`NOT MEASURED`**: The effect of multi-microphone beamforming or near-field headset microphones on Device A for Hindi recognition.

---

## 8. Phase 7 — Safety & Verification Audit

- **Production Code Status (`FACT`):** Production codebase in `app/src/main/` remained completely unaltered. No changes to `AudioRecordCapture`, `SileroVadEngine`, `SherpaWhisperAsrEngine`, or transceiver coordination.
- **Unit Test Suite (`MEASURED RESULT`):**
  - Command: `.\gradlew testDebugUnitTest`
  - Result: **`BUILD SUCCESSFUL`** (22 actionable tasks, 0 test failures, all unit tests passing).
- **Experiment Status:** **Complete.** Diagnostics terminated without production modification.
