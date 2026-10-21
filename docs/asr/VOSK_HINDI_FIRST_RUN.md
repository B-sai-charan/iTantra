# Physical Vosk Hindi ASR First-Run Benchmark Report

> **EXPERIMENTAL BENCHMARK RECORD — NOT PRODUCTION ASR**
>
> This document records the empirical results of the first isolated physical execution of the offline Vosk Hindi ASR model (`vosk-model-small-hi-0.22`) on target hardware (Lenovo TB-X6C6F) evaluated against the frozen Hindi audio fixture (`speech_test_hi_001.wav`).
> **Production `SpeechPipeline`, `SherpaWhisperAsrEngine`, VAD, and official English baselines remain completely unchanged.**

---

## 1. Experiment Status

- **Status:** **SUCCESSFULLY EXECUTED** (**MEASURED RESULT**)
- **Execution Mode:** Isolated diagnostic benchmark runner (`VoskBenchmarkRunner`) triggered via diagnostic intent `org.itantra.action.RUN_VOSK_HINDI_BENCHMARK` on device.
- **Physical Device:** Lenovo TB-X6C6F (Serial: `HNP097HQ`, Android 12 / API 31, `arm64-v8a`).

---

## 2. Baseline Project State

- **Production Speech Pipeline:** Unchanged (**FACT**).
- **Production ASR Engine:** `SherpaWhisperAsrEngine` backing `AsrEngine` remains active for live mic capture and pipeline processing (**FACT**).
- **Silero VAD v5:** Unchanged (**FACT**).
- **Official English Benchmark Suite:** Unchanged (**FACT**).
- **Whisper Hindi Diagnostic:** Unchanged (**FACT**).

---

## 3. AAR Identity & SHA256

| Property | Value | Evidence Category |
| :--- | :--- | :--- |
| **AAR ARTIFACT** | `vosk-android-0.3.75.aar` | **FACT** |
| **AAR LOCATION** | `app/libs/vosk-android-0.3.75.aar` (Copied locally from `scratch/`) | **FACT** |
| **AAR BYTE SIZE** | `13,472,638` bytes | **FACT** |
| **AAR SHA256** | `AB2F8B91AC8051561AA325546B35FED9A68B36B8121BAC5C6FB927525C4ADFAD` | **FACT** |
| **SOURCE** | Maven Central (`com.alphacephei:vosk-android:0.3.75`) verified offline | **FACT** |
| **EMBEDDED SHARED LIBRARIES** | `jni/arm64-v8a/libvosk.so`, `jni/armeabi-v7a/libvosk.so`, `jni/x86/libvosk.so`, `jni/x86_64/libvosk.so` | **FACT** |

---

## 4. Model Identity

- **Model Name:** `vosk-model-small-hi-0.22` (**FACT**)
- **Official Source:** Alpha Cephei ([alphacephei.com/vosk/models](https://alphacephei.com/vosk/models)) (**FACT**)
- **Download URL:** `https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip` (**FACT**)
- **License:** Apache License 2.0 (**DOCUMENTED CLAIM**)

---

## 5. Model ZIP SHA256 & Size

| Property | Value | Evidence Category |
| :--- | :--- | :--- |
| **ARCHIVE FILENAME** | `vosk-model-small-hi-0.22.zip` | **FACT** |
| **ZIP BYTE SIZE** | `44,458,845` bytes | **FACT** |
| **ZIP SHA256** | `7C50A10866889F0AC21D912C20537A055A597ED09FC1D3E5BCD798F9F0017E48` | **FACT** |

---

## 6. Extracted Model Size & Structure

- **Total Extracted Size:** `82,084,166` bytes (82.08 MB) across 15 files in 5 directories (**FACT**).
- **On-Device Storage Path:** `/storage/emulated/0/Android/data/org.itantra.app/files/models/vosk-model-small-hi-0.22` (**FACT**).
- **Model Subcomponents:**
  - `am/final.mdl` (15,957,444 bytes) &mdash; Kaldi acoustic model
  - `graph/Gr.fst` (30,692,365 bytes) &mdash; Grammar FST
  - `graph/HCLr.fst` (25,289,514 bytes) &mdash; HCL decoding graph
  - `graph/phones/word_boundary.int` (2,481 bytes)
  - `graph/disambig_tid.int` (66 bytes)
  - `ivector/final.ie` (9,927,287 bytes) &mdash; i-vector extractor
  - `ivector/final.dubm` (168,048 bytes) &mdash; Diagonal UBM
  - `ivector/final.mat` (44,975 bytes) &mdash; LDA transform matrix
  - `ivector/global_cmvn.stats` (1,074 bytes)
  - `ivector/splice.conf` (35 bytes)
  - `ivector/online_cmvn.conf` (0 bytes)
  - `conf/model.conf` (289 bytes)
  - `conf/mfcc.conf` (128 bytes)
  - `README` (460 bytes)

---

## 7. Device Execution Environment

| Parameter | Value | Evidence Category |
| :--- | :--- | :--- |
| **HARDWARE MODEL** | Lenovo TB-X6C6F | **FACT** |
| **DEVICE SERIAL** | `HNP097HQ` | **FACT** |
| **ANDROID OS** | Android 12 (API level 31) | **FACT** |
| **TARGET ABI** | `arm64-v8a` | **FACT** |
| **NETWORK REQUIREMENT** | 0 bytes transmitted (100% offline local inference) | **MEASURED RESULT** |

---

## 8. Frozen Input Verification

- **Asset Path:** `app/src/main/assets/speech_test_hi_001.wav` (**FACT**)
- **File SHA256:** `3614bd9301b65e0fc8a12c43cdab7a403b834c7948337352f97a22e26475df43` (**FACT**)
- **Sampling Rate:** 16,000 Hz (**FACT**)
- **Channels:** 1 (Mono) (**FACT**)
- **Audio Format:** Linear PCM 16-bit little-endian (PCM16) (**FACT**)
- **Sample Count:** 145,920 samples (**FACT**)
- **Audio Duration:** 9,120 ms (9.120 seconds) (**FACT**)

---

## 9. Recognition Configuration

- **Inference Mode:** Direct WAV memory buffer fed into `Recognizer.acceptWaveForm(ShortArray, length)` (**FACT**).
- **VAD Capture:** Disabled / Bypassed (**FACT**).
- **Microphone:** Disabled / Bypassed (**FACT**).
- **Resampling / Alteration:** None (**FACT**).
- **Recognizer Parameters:** 16,000 Hz, `words = true` (**FACT**).

---

## 10. Raw Vosk JSON Result

```json
{
  "result" : [ {
    "conf" : 1.000000,
    "end" : 1.200000,
    "start" : 0.900000,
    "word" : "कुछ"
  }, {
    "conf" : 0.814674,
    "end" : 1.590000,
    "start" : 1.200000,
    "word" : "अरु"
  }, {
    "conf" : 1.000000,
    "end" : 1.770000,
    "start" : 1.590000,
    "word" : "में"
  }, {
    "conf" : 0.528005,
    "end" : 2.010000,
    "start" : 1.770000,
    "word" : "आज"
  }, {
    "conf" : 0.528005,
    "end" : 2.220000,
    "start" : 2.010000,
    "word" : "तक"
  }, {
    "conf" : 0.999602,
    "end" : 2.820000,
    "start" : 2.220000,
    "word" : "केंद्रक"
  }, {
    "conf" : 1.000000,
    "end" : 3.090000,
    "start" : 2.820000,
    "word" : "होता"
  }, {
    "conf" : 1.000000,
    "end" : 3.390000,
    "start" : 3.090000,
    "word" : "है"
  }, {
    "conf" : 1.000000,
    "end" : 3.840000,
    "start" : 3.390000,
    "word" : "जिसका"
  }, {
    "conf" : 1.000000,
    "end" : 4.170000,
    "start" : 3.840000,
    "word" : "मतलब"
  }, {
    "conf" : 1.000000,
    "end" : 4.350000,
    "start" : 4.170000,
    "word" : "यह"
  }, {
    "conf" : 1.000000,
    "end" : 4.530000,
    "start" : 4.378541,
    "word" : "कि"
  }, {
    "conf" : 1.000000,
    "end" : 4.890000,
    "start" : 4.530000,
    "word" : "उनमें"
  }, {
    "conf" : 0.559482,
    "end" : 5.340000,
    "start" : 4.950000,
    "word" : "थोड़े"
  }, {
    "conf" : 0.939396,
    "end" : 5.580000,
    "start" : 5.400000,
    "word" : "या"
  }, {
    "conf" : 1.000000,
    "end" : 5.850000,
    "start" : 5.581818,
    "word" : "बिना"
  }, {
    "conf" : 1.000000,
    "end" : 6.120000,
    "start" : 5.850000,
    "word" : "किसी"
  }, {
    "conf" : 1.000000,
    "end" : 6.540000,
    "start" : 6.120000,
    "word" : "झटके"
  }, {
    "conf" : 1.000000,
    "end" : 6.690000,
    "start" : 6.540000,
    "word" : "से"
  }, {
    "conf" : 1.000000,
    "end" : 7.080000,
    "start" : 6.690000,
    "word" : "टूटने"
  }, {
    "conf" : 1.000000,
    "end" : 7.260000,
    "start" : 7.080000,
    "word" : "की"
  }, {
    "conf" : 0.911425,
    "end" : 7.740000,
    "start" : 7.260000,
    "word" : "प्रवृत्ति"
  }, {
    "conf" : 1.000000,
    "end" : 8.100000,
    "start" : 7.740000,
    "word" : "होती"
  }, {
    "conf" : 1.000000,
    "end" : 8.340000,
    "start" : 8.100000,
    "word" : "है"
  } ],
  "text" : "कुछ अरु में आज तक केंद्रक होता है जिसका मतलब यह कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"
}
```

---

## 11. Raw Transcript

`"कुछ अरु में आज तक केंद्रक होता है जिसका मतलब यह कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"` (**MEASURED RESULT**)

---

## 12. Normalized Reference Transcript

`"कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"` (**MEASURED RESULT**)

---

## 13. Normalized Hypothesis Transcript

`"कुछ अरु में आज तक केंद्रक होता है जिसका मतलब यह कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"` (**MEASURED RESULT**)

---

## 14. Word & Character Alignment

### Word Alignment:
- **Reference Words ($N$):** 24 (**MEASURED RESULT**)
- **Substitutions ($S$):** 2 (`अणुओं` &rarr; `अरु`, `अस्थिर` &rarr; `आज`) (**MEASURED RESULT**)
- **Deletions ($D$):** 1 (deleted `है` after `यह`) (**MEASURED RESULT**)
- **Insertions ($I$):** 1 (inserted `तक`) (**MEASURED RESULT**)
- **Hits ($H$):** 21 (**MEASURED RESULT**)

### Character Alignment:
- **Reference Characters ($N$):** 93 (**MEASURED RESULT**)
- **Char Substitutions ($S$):** 5 (**MEASURED RESULT**)
- **Char Deletions ($D$):** 6 (**MEASURED RESULT**)
- **Char Insertions ($I$):** 0 (**MEASURED RESULT**)
- **Char Hits ($H$):** 82 (**MEASURED RESULT**)

---

## 15. Word Error Rate (WER)

$$\text{WER} = \frac{S + D + I}{N} = \frac{2 + 1 + 1}{24} = \frac{4}{24} = \mathbf{16.67\%}$$ (**MEASURED RESULT**)

---

## 16. Character Error Rate (CER)

$$\text{CER} = \frac{S + D + I}{N} = \frac{5 + 6 + 0}{93} = \frac{11}{93} = \mathbf{11.83\%}$$ (**MEASURED RESULT**)

---

## 17. Exact Match

`false` (**MEASURED RESULT**)

---

## 18. Model-Load Duration

- **Model Load Time:** **3,350 ms** (3.350 seconds) (**MEASURED RESULT**)

---

## 19. Recognition Execution Duration

- **Recognition Time (Wall-clock):** **9,678 ms** (9.678 seconds) (**MEASURED RESULT**)

---

## 20. Real-Time Factor (RTF)

$$\text{RTF} = \frac{\text{Recognition Time}}{\text{Audio Duration}} = \frac{9678\text{ ms}}{9120\text{ ms}} = \mathbf{1.0612}$$ (**MEASURED RESULT**)

---

## 21. Runtime Stability Assessment

| Telemetry Item | Measured Value | Evidence Category |
| :--- | :--- | :--- |
| **RUNTIME EXCEPTIONS** | 0 | **MEASURED RESULT** |
| **APPLICATION CRASHES** | 0 | **MEASURED RESULT** |
| **APPLICATION NOT RESPONDING (ANR)** | 0 | **MEASURED RESULT** |
| **OUT OF MEMORY (OOM)** | 0 | **MEASURED RESULT** |
| **JNI / NATIVE ERRORS** | 0 | **MEASURED RESULT** |
| **MEMORY CONSUMPTION (RAM)** | Not measured. | **UNKNOWN** |
| **CPU UTILIZATION** | Not measured. | **UNKNOWN** |

---

## 22. Official English Baseline Integrity

The official 6-case English LibriSpeech + TED-LIUM 3 baseline remains verified and completely untouched:

- **Official Corpus WER:** **8.29%** (15 errors / 181 reference words) (**FACT**)
- **Official Corpus CER:** **6.12%** (48 errors / 784 reference characters) (**FACT**)
- **Official Mean RTF:** **0.8350** (**FACT**)
- **Official Exact Match Rate:** **16.67%** (1 / 6 cases) (**FACT**)

---

## 23. Comparison: Vosk Hindi vs Whisper Hindi First Run

Observed on the same frozen fixture (`speech_test_hi_001.wav`, 9.120 s) and physical device (Lenovo TB-X6C6F):

| Metric / Property | Whisper Tiny INT8 First Run (Case A: `hi`) | Vosk Hindi First Run (`vosk-model-small-hi-0.22`) | Evidence Category |
| :--- | :--- | :--- | :--- |
| **RAW HYPOTHESIS** | `"ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrakoeta ndrako"` | `"कुछ अरु में आज तक केंद्रक होता है जिसका मतलब यह कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"` | **MEASURED RESULT** |
| **RECOGNIZED SCRIPT** | Non-Devanagari Latin token loop | Native Devanagari Hindi text | **MEASURED RESULT** |
| **WER** | 100.00% (24 / 24 errors) | 16.67% (4 / 24 errors) | **MEASURED RESULT** |
| **CER** | 103.23% (96 / 93 errors) | 11.83% (11 / 93 errors) | **MEASURED RESULT** |
| **EXACT MATCH** | `false` | `false` | **MEASURED RESULT** |
| **ALIGNMENT (S/D/I/H)** | S=11, D=13, I=0, H=0 | S=2, D=1, I=1, H=21 | **MEASURED RESULT** |
| **AUDIO DURATION** | 9,120 ms | 9,120 ms | **MEASURED RESULT** |
| **INFERENCE DURATION** | 9,728 ms | 9,678 ms | **MEASURED RESULT** |
| **MODEL-LOAD DURATION** | Embedded asset load | 3,350 ms | **MEASURED RESULT** |
| **REAL-TIME FACTOR (RTF)** | 1.0667 | 1.0612 | **MEASURED RESULT** |
| **MODEL DISK SIZE** | 103,609,903 bytes (~98.81 MiB total: encoder 12.94 MB, decoder 89.86 MB, tokens 0.82 MB) | 82,084,166 bytes (~78.28 MiB uncompressed directory) | **FACT** |
| **RUNTIME STABILITY** | Clean (0 crashes) | Clean (0 crashes) | **MEASURED RESULT** |

---

## 24. Limitations

1. **Single Audio Fixture:** Evaluated exclusively against 1 frozen Hindi utterance (`speech_test_hi_001.wav`, 9.120 s). These results do not imply generalized accuracy across varied acoustic environments, regional Hindi accents, or noisy tactical conditions.
2. **Single Execution Run:** Exactly one deterministic physical run was recorded.
3. **No Microphone / VAD Integration:** Audio was injected directly from memory; live acoustic capture dynamics and Silero VAD segmentation were not exercised.
4. **Model Storage Footprint:** The 82 MB model directory was stored on external app-private storage (`/sdcard/Android/data/org.itantra.app/files/models/`), which requires unpacking from assets or external staging.

---

## 25. Next Experiment

- **Scope:** Retain Vosk as an experimental offline ASR evaluation candidate.
- **Potential Next Phase:** If authorized, evaluate multi-case Indic datasets (e.g. 10-case FLEURS Hindi suite) and investigate memory/CPU profiling and cold-start model unpacking performance without altering production speech pipeline architecture.
