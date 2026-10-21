# Official Frozen Multi-Case Hindi STT Benchmark Corpus Specification

> **BENCHMARK INFRASTRUCTURE SPECIFICATION — NO ASR EXECUTION RECORDED**
>
> This document defines the frozen five-case Hindi Speech-to-Text (STT) benchmark evaluation corpus (`HI-001` through `HI-005`) for offline neural transceiver speech evaluation on the iTantra platform.
> **All audio fixtures are sourced deterministically from the public open evaluation dataset Google FLEURS under the CC BY 4.0 license.**

---

## 1. Corpus Architecture & Provenance

| Parameter | Specification | Evidence Category |
| :--- | :--- | :--- |
| **CORPUS NAME** | iTantra Indic Hindi Frozen Benchmark Suite | **FACT** |
| **CASE COUNT** | 5 deterministic cases (`HI-001` &ndash; `HI-005`) | **FACT** |
| **SOURCE DATASET** | Google FLEURS (`google/fleurs`, subset `hi_in`, split `test`) | **FACT** |
| **DATASET CITATION** | Conneau et al., *FLEURS: Few-shot Learning Evaluation of Universal Representations of Speech*, IEEE SLT 2022 | **FACT** |
| **SOURCE LICENSE** | Creative Commons Attribution 4.0 International (CC BY 4.0) | **FACT** |
| **AUDIO SAMPLING RATE** | 16,000 Hz (16 kHz) | **FACT** |
| **CHANNEL CONFIGURATION** | 1 Channel (Mono) | **FACT** |
| **ENCODING FORMAT** | Linear PCM 16-bit signed integer little-endian (PCM16) | **FACT** |
| **TARGET LANGUAGE** | Hindi (`hi` / Indic Devanagari Unicode script) | **FACT** |

---

## 2. Audio Format Conversion Specification

In the official upstream Google FLEURS archive (`audio/test.tar.gz`), audio records are packaged as 32-bit IEEE Float WAV files (`wFormatTag = 3`).

To achieve direct compatibility with mobile edge ASR decoders (Sherpa-ONNX Whisper and Vosk Kaldi) without runtime audio manipulation, fixtures were deterministically formatted into standard 16-bit linear PCM (`wFormatTag = 1`) using the canonical sample mapping:

$$\text{PCM16}(s) = \begin{cases} \operatorname{round}(s \times 32767.0) & \text{if } s \ge 0 \\ \operatorname{round}(s \times 32768.0) & \text{if } s < 0 \end{cases}$$

clamped to the signed 16-bit integer boundary $[-32768, 32767]$ with a standard 44-byte RIFF/WAVE header.

- **Denoising / Filtering Applied:** None (**FACT**).
- **Dynamic Normalization Applied:** None (**FACT**).
- **Resampling Applied:** None (native sample rate is 16 kHz in both source and output) (**FACT**).

---

## 3. Case-by-Case Corpus Manifest

---

### Case HI-001 (Existing Frozen Baseline Fixture)

- **Case ID:** `HI-001`
- **Asset Filename:** `speech_test_hi_001.wav`
- **Source Dataset:** Google FLEURS (`google/fleurs`, `hi_in`, split: `test`)
- **FLEURS Row ID:** `1766`
- **FLEURS Source Filename:** `10011266027513218401.wav`
- **Speaker Gender:** Male
- **Audio Properties:** 16,000 Hz, 1 channel (Mono), PCM16
- **Sample Count:** 145,920 samples
- **Audio Duration:** 9.120 s (9,120 ms)
- **Source IEEE-Float32 File SHA256:** `217f3abbd8e9cd3767d6e8b87d49fbf82421fc920a103de026b4586d7e644dc5`
- **Local Asset PCM16 File SHA256:** `3614bd9301b65e0fc8a12c43cdab7a403b834c7948337352f97a22e26475df43`
- **Verbatim Reference Transcript:**
  > `"कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,"`
- **Normalized Reference Transcript (IndicTextNormalizer):**
  > `"कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"`
- **Reference Word Count ($N_{\text{words}}$):** 24 words
- **Reference Character Count ($N_{\text{chars}}$):** 93 characters

---

### Case HI-002

- **Case ID:** `HI-002`
- **Asset Filename:** `speech_test_hi_002.wav`
- **Source Dataset:** Google FLEURS (`google/fleurs`, `hi_in`, split: `test`)
- **FLEURS Row ID:** `1926`
- **FLEURS Source Filename:** `14585676639063535081.wav`
- **Speaker Gender:** Female
- **Audio Properties:** 16,000 Hz, 1 channel (Mono), PCM16
- **Sample Count:** 106,560 samples
- **Audio Duration:** 6.660 s (6,660 ms)
- **Source IEEE-Float32 File SHA256:** `eee9638f1b46055558ce51dcca82dd9eb97dfa57788c387d81b3e7e75c2f803d`
- **Local Asset PCM16 File SHA256:** `059942f06d572e46ccec1eb005522c118fc2a78b0612b160e84288315d04883d`
- **Verbatim Reference Transcript:**
  > `"मनोविज्ञान सहित विज्ञान के सभी मामलों पर अरस्तू के विचारों को स्वीकार किया जाता है।"`
- **Normalized Reference Transcript (IndicTextNormalizer):**
  > `"मनोविज्ञान सहित विज्ञान के सभी मामलों पर अरस्तू के विचारों को स्वीकार किया जाता है"`
- **Reference Word Count ($N_{\text{words}}$):** 15 words
- **Reference Character Count ($N_{\text{chars}}$):** 68 characters

---

### Case HI-003

- **Case ID:** `HI-003`
- **Asset Filename:** `speech_test_hi_003.wav`
- **Source Dataset:** Google FLEURS (`google/fleurs`, `hi_in`, split: `test`)
- **FLEURS Row ID:** `1747`
- **FLEURS Source Filename:** `16976841982852749134.wav`
- **Speaker Gender:** Male
- **Audio Properties:** 16,000 Hz, 1 channel (Mono), PCM16
- **Sample Count:** 61,440 samples
- **Audio Duration:** 3.840 s (3,840 ms)
- **Source IEEE-Float32 File SHA256:** `7d290602e0f8f4ba88b589d3dfacb076f38afedcf6442daa649d5ae2febce048`
- **Local Asset PCM16 File SHA256:** `1481c27cf6651bff893b7ba8977103e01a7fb82b9e79b8e7c0931a90b648f191`
- **Verbatim Reference Transcript:**
  > `"लूना को साथी पहलवानों ने भी श्रद्धांजलि दी."`
- **Normalized Reference Transcript (IndicTextNormalizer):**
  > `"लूना को साथी पहलवानों ने भी श्रद्धांजलि दी"`
- **Reference Word Count ($N_{\text{words}}$):** 8 words
- **Reference Character Count ($N_{\text{chars}}$):** 35 characters

---

### Case HI-004

- **Case ID:** `HI-004`
- **Asset Filename:** `speech_test_hi_004.wav`
- **Source Dataset:** Google FLEURS (`google/fleurs`, `hi_in`, split: `test`)
- **FLEURS Row ID:** `1917`
- **FLEURS Source Filename:** `2129043417079181978.wav`
- **Speaker Gender:** Male
- **Audio Properties:** 16,000 Hz, 1 channel (Mono), PCM16
- **Sample Count:** 143,040 samples
- **Audio Duration:** 8.940 s (8,940 ms)
- **Source IEEE-Float32 File SHA256:** `0b103ece4f02b538e25d930bc9294682c8a82bffd68d25456e99bac1dacb5c7d`
- **Local Asset PCM16 File SHA256:** `4c8bbc7b39e3b0a0d052c03ce22f06e84210d4cd1c799669faecbdb90b5d2883`
- **Verbatim Reference Transcript:**
  > `"रोम में अलग-अलग जगहों पर कई बड़े टेलीविज़न स्क्रीन लगाए गए ताकि लोग समारोह देख सकें."`
- **Normalized Reference Transcript (IndicTextNormalizer):**
  > `"रोम में अलग-अलग जगहों पर कई बड़े टेलीविज़न स्क्रीन लगाए गए ताकि लोग समारोह देख सकें"`
- **Reference Word Count ($N_{\text{words}}$):** 16 words
- **Reference Character Count ($N_{\text{chars}}$):** 67 characters

---

### Case HI-005

- **Case ID:** `HI-005`
- **Asset Filename:** `speech_test_hi_005.wav`
- **Source Dataset:** Google FLEURS (`google/fleurs`, `hi_in`, split: `test`)
- **FLEURS Row ID:** `1958`
- **FLEURS Source Filename:** `1137772989789278397.wav`
- **Speaker Gender:** Female
- **Audio Properties:** 16,000 Hz, 1 channel (Mono), PCM16
- **Sample Count:** 141,120 samples
- **Audio Duration:** 8.820 s (8,820 ms)
- **Source IEEE-Float32 File SHA256:** `caf79f49726eb01e9c65917d551a2a48e45c299f057b463d7daf801b33fb90ec`
- **Local Asset PCM16 File SHA256:** `f7cd61da6030853b5da777619c1ac00227debc17294039a882bbc27784aa5519`
- **Verbatim Reference Transcript:**
  > `"यह शहर देश के बाकी शहरों से अलग है क्योंकि यह किसी अफ्रीकी शहर की बजाय अरब शहर लगता है."`
- **Normalized Reference Transcript (IndicTextNormalizer):**
  > `"यह शहर देश के बाकी शहरों से अलग है क्योंकि यह किसी अफ्रीकी शहर की बजाय अरब शहर लगता है"`
- **Reference Word Count ($N_{\text{words}}$):** 20 words
- **Reference Character Count ($N_{\text{chars}}$):** 67 characters

---

## 4. Multi-Case Corpus Summary

| Case ID | Source Identifier | Asset Filename | Gender | Audio Duration | Sample Count | Reference Words | Reference Characters | Local Asset SHA256 |
| :--- | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **HI-001** | FLEURS `hi_in` test `1766` | `speech_test_hi_001.wav` | Male | 9.120 s | 145,920 | 24 | 93 | `3614bd9301b65e0fc8a12c43cdab7a403b834c7948337352f97a22e26475df43` |
| **HI-002** | FLEURS `hi_in` test `1926` | `speech_test_hi_002.wav` | Female | 6.660 s | 106,560 | 15 | 68 | `059942f06d572e46ccec1eb005522c118fc2a78b0612b160e84288315d04883d` |
| **HI-003** | FLEURS `hi_in` test `1747` | `speech_test_hi_003.wav` | Male | 3.840 s | 61,440 | 8 | 35 | `1481c27cf6651bff893b7ba8977103e01a7fb82b9e79b8e7c0931a90b648f191` |
| **HI-004** | FLEURS `hi_in` test `1917` | `speech_test_hi_004.wav` | Male | 8.940 s | 143,040 | 16 | 67 | `4c8bbc7b39e3b0a0d052c03ce22f06e84210d4cd1c799669faecbdb90b5d2883` |
| **HI-005** | FLEURS `hi_in` test `1958` | `speech_test_hi_005.wav` | Female | 8.820 s | 141,120 | 20 | 67 | `f7cd61da6030853b5da777619c1ac00227debc17294039a882bbc27784aa5519` |
| **TOTAL** | &mdash; | &mdash; | **3M / 2F** | **37.380 s** | **598,080** | **83** | **330** | &mdash; |

---

## 5. Scoring & Normalization Rules

- **Normalizer:** [`IndicTextNormalizer`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/stt/benchmark/IndicTextNormalizer.kt) (Unicode NFC normalization, Devanagari character preservation, Latin lowercasing, punctuation stripping).
- **Scorer:** [`IndicWerCalculator`](file:///c:/Users/saich/Downloads/SIH/PS26173/app/src/main/java/org/itantra/core/stt/benchmark/IndicWerCalculator.kt) (Strict word Levenshtein distance for WER; Unicode code-point edit distance for CER).
- **Suite Aggregation Formula:**
  - $\text{Corpus WER} = \frac{\sum (S + D + I)}{\sum N_{\text{words}}}$
  - $\text{Corpus CER} = \frac{\sum \text{Char Errors}}{\sum N_{\text{chars}}}$
  - $\text{Mean RTF} = \frac{1}{K}\sum_{i=1}^K \text{RTF}_i$

---

## 6. Project & Baseline Integrity

- **Official English Benchmark:** Retains 6 verified cases (EN-001 to EN-006, 181 words, 784 chars, official 8.29% WER / 6.12% CER).
- **Existing HI-001 Asset:** Retains identical SHA256 checksum (`3614bd9301b65e0fc8a12c43cdab7a403b834c7948337352f97a22e26475df43`).
- **Production ASR Code:** Untouched and isolated from benchmark data additions.
