# M1 Live Microphone Speech Integrity Diagnostic (Task M1-LIVE-MIC-SPEECH-INTEGRITY-15)

**Document ID:** `M1-LIVE-MIC-SPEECH-INTEGRITY-15`  
**Experiment Date:** 2026-09-28  
**Device Under Test:** Device A (Lenovo Smart Tab M10 FHD Plus / TB-X6C6F, Android 12, Build API 31, arm64-v8a)  
**Status:** **COMPLETE & VERIFIED (DIAGNOSTIC-ONLY)**  

---

## 1. Objective

The objective of this diagnostic experiment is to determine whether the existing live-microphone WAV recordings contain intelligible/recoverable human speech despite failing both Sherpa-ONNX Whisper Tiny INT8 and offline Hindi Vosk, or whether the captured physical microphone signal itself is degraded to the point that speech phonetic structure is not reliably preserved.

> [!IMPORTANT]
> **Diagnostic Isolation Directives Maintained:**
> - Zero modification to production code (`app/src/main/`).
> - Zero modification to `AudioRecordCapture`, `SileroVadEngine`, `SherpaWhisperAsrEngine`, or Vosk.
> - Zero modification to M2 transport, SpeechMessage, or TTS.
> - Zero new dependencies or models added.
> - Zero recording of new audio (evaluated strictly on existing captured WAV artifacts).
> - Zero DSP / audio processing applied (no gain, filtering, EQ, normalization, resampling, trimming, or padding).

---

## 2. Input Audio Files

Four existing WAV files from the repository were evaluated:

1. **`scratch/raw_mic_source_control_hi.wav`** — Fixed 8.000s unsegmented raw microphone capture from `M1-MIC-SOURCE-CONTROL-02`.
2. **`scratch/live_hindi_forensic_01.wav`** — VAD-segmented 2.120s forensic capture from `M1-LIVE-MIC-FORENSIC-01`.
3. **`scratch/raw_mic_hi_control.wav`** — Fixed 8.000s unsegmented raw microphone capture from `M1-RAW-MIC-CAPTURE-CONTROL-01`.
4. **`scratch/speech_test_hi_001.wav`** — Clean digital studio reference fixture (`HI-001`, Google FLEURS `hi_in` test set, row ID 1766).

---

## 3. File Integrity & Format Verification

| File | Duration | Sample Rate | Channels | Format | Sample Count | File Size | Clipping Count | SHA-256 Checksum |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **`raw_mic_source_control_hi.wav`** | 8.000 s | 16,000 Hz | 1 (Mono) | PCM16 | 128,000 | 256,044 B | 0 (0.00%) | `da26fd1c83fc75ac52aae79e5b36c5121f503cff7741f428c09a9edfd879e640` |
| **`live_hindi_forensic_01.wav`** | 2.120 s | 16,000 Hz | 1 (Mono) | PCM16 | 33,920 | 67,884 B | 0 (0.00%) | `e6db9b8542930b8737aa671c109a972ef361dccd4ea249e8d78cc51e69ec13e8` |
| **`raw_mic_hi_control.wav`** | 8.000 s | 16,000 Hz | 1 (Mono) | PCM16 | 128,000 | 256,044 B | 0 (0.00%) | `1e77501fdadb99e029952df71aa8983ca8e0fc9ed6c4f9f8c74571ed51eb4841` |
| **`speech_test_hi_001.wav`** | 9.120 s | 16,000 Hz | 1 (Mono) | PCM16 | 145,920 | 291,884 B | 0 (0.00%) | `3614bd9301b65e0fc8a12c43cdab7a403b834c7948337352f97a22e26475df43` |

All four files are valid, uncorrupted, standard 16 kHz Mono Signed 16-bit Linear PCM WAV files with standard 44-byte RIFF headers. Zero clipping (0.000%) was detected across all samples.

---

## 4. Signal & Phonetic Structure Measurements

To characterize the acoustic and phonetic information content present in each signal, windowed short-time frame analysis (25 ms Hann window, 10 ms hop) and spectral analysis were computed:

| Metric | `raw_mic_source_control_hi.wav` | `live_hindi_forensic_01.wav` | `raw_mic_hi_control.wav` | `speech_test_hi_001.wav` *(Clean Ref)* |
| :--- | :---: | :---: | :---: | :---: |
| **RMS Energy (dBFS)** | **-33.23 dBFS** | **-40.85 dBFS** | **-42.80 dBFS** | **-27.56 dBFS** |
| **Peak Amplitude (dBFS)** | -12.13 dBFS (8,107) | -25.66 dBFS (1,707) | -28.43 dBFS (1,241) | -12.86 dBFS (7,453) |
| **Crest Factor** | 11.34 (21.09 dB) | 5.74 (15.18 dB) | 5.23 (14.37 dB) | 5.43 (14.70 dB) |
| **Zero Crossing Rate (ZCR)** | 0.1423 (2,277.0 /s) | 0.1190 (1,903.8 /s) | **0.3222** (5,155.9 /s) | **0.0581** (929.7 /s) |
| **Spectral Centroid** | 1,780.4 Hz | 1,596.8 Hz | **2,968.9 Hz** | **906.4 Hz** |
| **Spectral Flatness (0=tonal, 1=noise)** | 0.0528 | 0.0355 | **0.2981** | **0.0418** |
| **Mean Short-Time Frame Energy** | -41.66 dBFS | -42.87 dBFS | -43.01 dBFS | -38.99 dBFS |
| **Frame Energy StdDev** | 8.56 dB | 4.28 dB | **1.59 dB** | **19.72 dB** |
| **10th–90th Energy Dyn. Range** | 23.06 dB | 11.52 dB | **3.52 dB** | **38.11 dB** |
| **Active Frame Ratio (> -45 dBFS)** | 57.9% | 66.7% | 90.4% *(noise floor)* | 71.6% *(speech)* |

---

## 5. Existing ASR Model Results

Evaluations conducted in `M1-ASR-MODEL-SUITABILITY-14` on Device A:

| Audio Input | Target Hindi Reference | Whisper Tiny INT8 Transcript | Whisper WER | Vosk Hindi Small Transcript | Vosk WER |
| :--- | :--- | :--- | :---: | :--- | :---: |
| **`raw_mic_source_control_hi.wav`** | `"नमस्ते, यह iTantra का हिंदी परीक्षण है।"` | `"- Hello!"` | **100.00%** | `"हैलो"` *(1 word decoded)* | **100.00%** |
| **`live_hindi_forensic_01.wav`** | `"नमस्ते, यह iTantra का हिंदी परीक्षण है।"` | `"ndo ndo ndo ndo"` | **100.00%** | `""` *(empty)* | **100.00%** |
| **`raw_mic_hi_control.wav`** | `"नमस्ते, यह iTantra का हिंदी परीक्षण है।"` | `"[ -染 -染 -染 -染...]"` | **157.14%** | `""` *(empty)* | **100.00%** |
| **`speech_test_hi_001.wav`** | `"कुछ अणुओं में अस्थिर केंद्रक होता है..."` | `"ndrakoeta ndrakoeta..."` | **100.00%** | `"कुछ अरु में स्थित केंद्रक होता है..."` | **12.50%** |

---

## 6. Human Listening Verification Availability

> **"Human listening verification unavailable in this environment."**

As an autonomous agent operating within an automated execution container, physical acoustic playback to human ears cannot be conducted directly. Signal integrity must be assessed via empirical physical signal measurements and deterministic decoder behavior.

---

## 7. Independent Offline ASR Reference Availability

> **"No independent ASR reference available."**

The repository contains two on-device ASR models: Sherpa-ONNX Whisper Tiny INT8 and Vosk Hindi Small 0.22. In accordance with task constraints prohibiting internet/cloud APIs or introducing new dependencies/downloads, no third offline ASR engine is present.

---

## 8. Cross-Domain Comparison Table

| File | Duration | RMS Energy | Peak Amplitude | Clipping | Whisper WER | Vosk WER | Independent Evidence |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **`raw_mic_source_control_hi.wav`** | 8.000 s | -33.23 dBFS | -12.13 dBFS | 0 | 100.00% | 100.00% | Transient energy present; Vosk decodes single token (`"हैलो"` @ 0.81 conf) |
| **`live_hindi_forensic_01.wav`** | 2.120 s | -40.85 dBFS | -25.66 dBFS | 0 | 100.00% | 100.00% | Low SNR; peak < 5.2% full scale; Vosk decodes 0 words (`""`) |
| **`raw_mic_hi_control.wav`** | 8.000 s | -42.80 dBFS | -28.43 dBFS | 0 | 157.14% | 100.00% | Flat stationary noise floor (DynRange 3.52 dB, Flatness 0.298); Vosk decodes 0 words (`""`) |
| **`speech_test_hi_001.wav`** *(Clean)* | 9.120 s | -27.56 dBFS | -12.86 dBFS | 0 | 100.00% | 12.50% | High SNR; 38.11 dB dynamic range; Vosk achieves 21/24 word hits |

---

## 9. Evidence Assessment

1. **Signal Energy and Dynamic Range Discrepancy:**
   - The clean reference speech fixture (`speech_test_hi_001.wav`) exhibits a **38.11 dB dynamic range** across frames ($\text{StdDev} = 19.72\text{ dB}$), reflecting distinct syllable bursts, voiced vowel formants, and inter-word acoustic boundaries.
   - In contrast, `raw_mic_hi_control.wav` has an energy dynamic range of only **3.52 dB** ($\text{StdDev} = 1.59\text{ dB}$) at an overall level of $-42.80\text{ dBFS}$. This indicates that speech syllable modulation is virtually flat and buried within the ambient microphone noise floor.
   - `live_hindi_forensic_01.wav` similarly exhibits a narrow **11.52 dB dynamic range** with a peak amplitude of only $-25.66\text{ dBFS}$ (maximum sample value 1,707 out of 32,768, or $<5.2\%$ full scale).

2. **Spectral Characteristics and Noise Contamination:**
   - The clean reference speech exhibits a low spectral centroid of **906.4 Hz** with low spectral flatness (**0.0418**), characteristic of rich harmonic vocal-tract resonance and vowel formants.
   - `raw_mic_hi_control.wav` exhibits a high spectral centroid of **2,968.9 Hz**, a high zero-crossing rate of **0.3222** (5,155 crossings/sec), and elevated spectral flatness (**0.2981**). This demonstrates high-frequency noise dominance over low-frequency phonetic formants.

3. **ASR Recovery Evidence:**
   - On clean speech, Vosk successfully decodes 21 of 24 Hindi words (**12.50% WER**), demonstrating that the decoder acoustic model functions properly when presented with intelligible speech.
   - On the live microphone recordings, Vosk decodes **0 words** on two captures (`""`) and a single word (`"हैलो"`) on the third, while Whisper hallucinates repetitive tokens.

---

## 10. Limitations

1. Human auditory perceptual evaluation was not conducted due to the automated agent runtime environment.
2. Signal-level metrics (ZCR, spectral centroid, frame energy variance) characterize physical waveform attributes but cannot replace perceptual phoneme intelligibility scoring.
3. No external DSP or neural denoising/enhancement was evaluated in this diagnostic run.

---

## 11. Final Classification & Conclusion

In response to the key question:

> *"Do the existing measurements provide evidence that the live microphone WAVs contain recoverable speech information, or is the signal quality itself insufficient to support recognition?"*

### **Measured Classification: Conclusion B**

> **"Evidence indicates the captured signal is substantially degraded and may not preserve enough speech information for reliable ASR."**

### Technical Rationale:
- In two of the three live microphone recordings (`raw_mic_hi_control.wav` and `live_hindi_forensic_01.wav`), the signal is characterized by extremely low RMS energy ($-41$ to $-43\text{ dBFS}$), peak amplitudes under $5.2\%$ of dynamic range, negligible frame-to-frame syllabic modulation ($3.52\text{ dB}$ to $11.52\text{ dB}$ dynamic range), and elevated noise flatness.
- On these files, both the neural Whisper engine and the HMM/Kaldi Vosk engine produce 0 correct words.
- While `raw_mic_source_control_hi.wav` contains transient energy allowing Vosk to isolate a single greeting token, the full multi-word utterance is not preserved across any of the live recordings.
- The evidence confirms that the captured microphone signals are severely attenuated and degraded, preventing reliable phoneme recovery by either ASR engine.

---

## 12. Build & System Verification

### Production Code Status
```
Production code changed: NO
```
*(Zero modifications to `app/src/main/`, Gradle configurations, dependencies, models, or pipeline logic).*

### Unit Test Verification
```
Command: .\gradlew.bat testDebugUnitTest
Result: BUILD SUCCESSFUL
Test Count: 365 tests executed, 0 failures, 0 errors, 0 skipped
```
