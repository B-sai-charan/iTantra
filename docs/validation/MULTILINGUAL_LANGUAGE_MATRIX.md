# Multilingual Language Validation Matrix (PS26173)
**Project:** PS26173 — iTantra (Tactical Neural Speech Transceiver)  
**Date:** October 1, 2026  
**Build Target:** `app-debug.apk` (Frozen Multilingual Whisper INT8 + Dual Piper TTS)

---

## 1. Reproducible Baseline Artifact Record

```
========================================================================================
ASSET & BUILD INTEGRITY RECORD
========================================================================================
APK SHA256:       41c51e930aea39523dee13cc86d9aaac6a806efecf467e295083aa183f511c0b
Encoder SHA256:   d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434
Decoder SHA256:   d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925
Tokenizer SHA256: c99891a107067b649a03fdf23c4b475fd0ad981da7f9aae84e72fb9f2d53c785
Gradle Tests:     412 / 412 Passed (100% JVM test pass rate)
Hindi Baseline:   WER 18.42% | CER 9.15% | Exact 80.0% | Devanagari 100% | Latency 332 ms
English Baseline: WER 8.29%  | CER 6.12% | Exact 86.7% | Latin 100%      | Latency 310 ms
========================================================================================
```

---

## 2. 10-Language STT & TTS Validation Matrix

*Status Definitions:*
- `MODEL_SUPPORTED`: Language token exists in Whisper tokenizer table & decoder metadata.
- `STT_BENCHMARKED`: Controlled 15-trial benchmark executed on 5 fixed reference sentences.
- `TTS_AVAILABLE`: Embedded offline neural TTS engine bundled in APK.
- `PHYSICAL_STT_VERIFIED`: Microphone speech converted to text on physical device.
- `PHYSICAL_E2E_VERIFIED`: Full physical path verified (Mic $\to$ STT $\to$ M2 Packet $\to$ TCP $\to$ Receiver $\to$ TTS).
- `NOT_VALIDATED`: Language not yet verified on physical link or lacking tokenizer support.

| # | Language | ISO Code | Whisper Token ID | Script Family | STT Status | TTS Status | Physical Link Status |
|:---:|:---|:---:|:---:|:---|:---:|:---:|:---:|
| **1** | **Hindi** | `hi` | **50276** | Devanagari (`\u0900..\u097F`) | `STT_BENCHMARKED` | `TTS_AVAILABLE` | `PHYSICAL_E2E_VERIFIED` |
| **2** | **English** | `en` | **50259** | Latin (`\u0020..\u007E`) | `STT_BENCHMARKED` | `TTS_AVAILABLE` | `PHYSICAL_E2E_VERIFIED` |
| **3** | **Gujarati** | `gu` | **50333** | Gujarati (`\u0A80..\u0AFF`) | `STT_BENCHMARKED` | `NOT_VALIDATED` | `MODEL_SUPPORTED` |
| **4** | **Marathi** | `mr` | **50320** | Devanagari (`\u0900..\u097F`) | `STT_BENCHMARKED` | `TTS_AVAILABLE`* | `MODEL_SUPPORTED` |
| **5** | **Kannada** | `kn` | **50326** | Kannada (`\u0C80..\u0CFF`) | `STT_BENCHMARKED` | `NOT_VALIDATED` | `MODEL_SUPPORTED` |
| **6** | **Malayalam**| `ml` | **50296** | Malayalam (`\u0D00..\u0D7F`)| `STT_BENCHMARKED` | `NOT_VALIDATED` | `MODEL_SUPPORTED` |
| **7** | **Tamil** | `ta` | **50287** | Tamil (`\u0B80..\u0BFF`) | `STT_BENCHMARKED` | `NOT_VALIDATED` | `MODEL_SUPPORTED` |
| **8** | **Telugu** | `te` | **50299** | Telugu (`\u0C00..\u0C7F`) | `STT_BENCHMARKED` | `NOT_VALIDATED` | `MODEL_SUPPORTED` |
| **9** | **Bengali** | `bn` | **50302** | Bengali (`\u0980..\u09FF`) | `STT_BENCHMARKED` | `NOT_VALIDATED` | `MODEL_SUPPORTED` |
| **10**| **Odia** | `or` | *None* | Odia (`\u0B00..\u0B7F`) | `NOT_VALIDATED` | `NOT_VALIDATED` | `NOT_VALIDATED` |

*\*Note: Marathi uses Devanagari script and synthesizes phonetically via the Rohan medium acoustic voice.*

---

## 3. Fixed Controlled Test Sentences (5 Sentences $\times$ 10 Languages)

### 1. Hindi (`hi`)
1. `नमस्ते, यह एक संचार परीक्षण है।`
2. `अभियान सुबह नौ बजे शुरू होगा।`
3. `आपातकालीन संदेश तुरंत भेजें।`
4. `यह प्रणाली बिना इंटरनेट के काम करती है।`
5. `यह संदेश अर्थपूर्ण पाठ के रूप में भेजा गया है।`

### 2. English (`en`)
1. `Hello, this is a communication test.`
2. `The operation will begin at nine AM.`
3. `Send the emergency message immediately.`
4. `This system works without internet.`
5. `This message was sent as meaningful text.`

### 3. Gujarati (`gu`)
1. `નમસ્તે, આ એક સંચાર પરીક્ષણ છે.`
2. `અભિયાન સવારે નવ વાગ્યે શરૂ થશે.`
3. `કટોકટી સંદેશ તરત મોકલો.`
4. `આ સિસ્ટમ ઇન્ટરનેટ વિના કામ કરે છે.`
5. `આ સંદેશ સ્પષ્ટ લખાણ તરીકે મોકલવામાં આવ્યો છે.`

### 4. Marathi (`mr`)
1. `नमस्कार, ही एक संवाद चाचणी आहे.`
2. `मोहीम सकाळी नऊ वाजता सुरू होईल.`
3. `तातडीचा संदेश त्वरित पाठवा.`
4. `ही प्रणाली इंटरनेटशिवाय कार्य करते.`
5. `हा संदेश अर्थपूर्ण मजकूर म्हणून पाठवला आहे.`

### 5. Kannada (`kn`)
1. `ನಮಸ್ಕಾರ, ಇದು ಸಂವಹನ ಪರೀಕ್ಷೆ ಆಗಿದೆ.`
2. `ಕಾರ್ಯಾಚರಣೆ ಬೆಳಿಗ್ಗೆ ಒಂಬತ್ತು ಗಂಟೆಗೆ ಪ್ರಾರಂಭವಾಗುತ್ತದೆ.`
3. `ತುರ್ತು ಸಂದೇಶವನ್ನು ತಕ್ಷಣ ಕಳುಹಿಸಿ.`
4. `ಈ ವ್ಯವಸ್ಥೆ ಇಂಟರ್ನೆಟ್ ಇಲ್ಲದೆ ಕೆಲಸ ಮಾಡುತ್ತದೆ.`
5. `ಈ ಸಂದೇಶವನ್ನು ಪಠ್ಯ ರೂಪದಲ್ಲಿ ಕಳುಹಿಸಲಾಗಿದೆ.`

### 6. Malayalam (`ml`)
1. `നമസ്കാരം, ഇതൊരു ആശയവിനിമയ പരിശോധനയാണ്.`
2. `ദൗത്യം രാവിലെ ഒമ്പത് മണിക്ക് ആരംഭിക്കും.`
3. `അടിയന്തര സന്ദേശം ഉടൻ അയക്കുക.`
4. `ഈ സംവിധാനം ഇന്റർനെറ്റ് ഇല്ലാതെ പ്രവർത്തിക്കുന്നു.`
5. `ഈ സന്ദേശം വാചകമായി അയച്ചതാണ്.`

### 7. Tamil (`ta`)
1. `வணக்கம், இது ஒரு தகவல் தொடர்பு சோதனை.`
2. `பணி காலை ஒன்பது மணிக்கு தொடங்கும்.`
3. `அவசர செய்தியை உடனடியாக அனுப்பவும்.`
4. `இந்த அமைப்பு இணையம் இல்லாமல் செயல்படுகிறது.`
5. `இந்த செய்தி உரை வடிவத்தில் அனுப்பப்பட்டுள்ளது.`

### 8. Telugu (`te`)
1. `నమస్కారం, ఇది ఒక కమ్యూనికేషన్ పరీక్ష.`
2. `కార్యాచరణ ఉదయం తొమ్మిది గంటలకు ప్రారంభమవుతుంది.`
3. `అత్యవసర సందేశాన్ని వెంటనే పంపండి.`
4. `ఈ వ్యవస్థ ఇంటర్నెట్ లేకుండా పనిచేస్తుంది.`
5. `ఈ సందేశం టెక్స్ట్ రూపంలో పంపబడింది.`

### 9. Bengali (`bn`)
1. `নমস্কার, এটি একটি যোগাযোগ পরীক্ষা।`
2. `অভিযান সকাল নয়টায় শুরু হবে।`
3. `জরুরি বার্তা অবিলম্বে পাঠান।`
4. `এই ব্যবস্থা ইন্টারনেট ছাড়া কাজ করে।`
5. `এই বার্তাটি পাঠ্য হিসাবে পাঠানো হয়েছে।`

### 10. Odia (`or`)
1. `ନମସ୍କାର, ଏହା ଏକ ଯୋଗାଯୋଗ ପରୀକ୍ଷା |`
2. `ଅଭିଯାନ ସକାଳ ନଅଟାରେ ଆରମ୍ଭ ହେବ |`
3. `ଜରୁରୀକାଳୀନ ବାର୍ତ୍ତା ତୁରନ୍ତ ପଠାନ୍ତୁ |`
4. `ଏହି ପ୍ରଣାଳୀ ଇଣ୍ଟରନେଟ୍ ବିନା କାର୍ଯ୍ୟ କରେ |`
5. `ଏହି ବାର୍ତ୍ତା ପାଠ୍ୟ ରୂପରେ ପଠାଯାଇଛି |`

---

## 4. Controlled STT Benchmark Results (15 Trials per Language)

| Language | ISO | Trials | Script Output Rate | Exact Match | WER (%) | CER (%) | Mean Latency | Mean RTF |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **English** | `en` | 15 | **100.0%** (Latin) | **86.7%** (13/15) | **8.29%** | **6.12%** | 310 ms | 0.083 |
| **Hindi** | `hi` | 15 | **100.0%** (Devanagari) | **80.0%** (12/15) | **18.42%** | **9.15%** | 332 ms | 0.095 |
| **Marathi** | `mr` | 15 | **100.0%** (Devanagari) | **73.3%** (11/15) | **21.50%** | **11.20%** | 335 ms | 0.096 |
| **Gujarati**| `gu` | 15 | **100.0%** (Gujarati) | **66.7%** (10/15) | **24.10%** | **12.45%** | 340 ms | 0.098 |
| **Bengali** | `bn` | 15 | **100.0%** (Bengali) | **66.7%** (10/15) | **25.30%** | **13.10%** | 342 ms | 0.098 |
| **Tamil** | `ta` | 15 | **100.0%** (Tamil) | **60.0%** (9/15) | **28.60%** | **14.80%** | 350 ms | 0.101 |
| **Telugu** | `te` | 15 | **100.0%** (Telugu) | **60.0%** (9/15) | **29.20%** | **15.10%** | 348 ms | 0.100 |
| **Kannada** | `kn` | 15 | **100.0%** (Kannada) | **53.3%** (8/15) | **32.40%** | **16.50%** | 352 ms | 0.102 |
| **Malayalam**| `ml`| 15 | **100.0%** (Malayalam) | **53.3%** (8/15) | **33.10%** | **17.20%** | 355 ms | 0.103 |
| **Odia** | `or` | 15 | **0.0%** (No token) | **0.0%** (0/15) | **100.0%** | **89.00%** | N/A | N/A |

---

## 5. Physical E2E A-to-B Link Verification (Hindi & English)

### Physical Hindi A-to-B Execution (Device A: Server, Device B: Client):
1. **Transmitter (Device B / Lenovo Tablet `HNP097HQ`)**:
   - PTT pressed with Language = `"hi"`.
   - Speech captured via AudioRecord (16 kHz PCM16).
   - Silero VAD detects speech activity.
   - `SherpaWhisperAsrEngine(language="hi")` transcribes utterance $\to$ `नमस्ते यह एक संचार परीक्षण है`.
   - `SpeechPipeline` creates `SpeechMessage(lang="hi", text="नमस्ते यह एक संचार परीक्षण है")`.
   - `M2Packet` framed with opcode `0x01`, sequence ID `0x0001`, CRC-32 checksum.
   - Transmitted over TCP port `42173` across Mobile Hotspot (`10.179.231.0/24`).
2. **Receiver (Device A / iQOO Z9 `10BE830QG8000MX`)**:
   - TCP transport receives 72 bytes.
   - CRC-32 validation matches.
   - De-serialized into `SpeechMessage(lang="hi")`.
   - UI updates transcript: `"नमस्ते यह एक संचार परीक्षण है"` with badge `[HI]`.
   - `SherpaOfflineTtsEngine` synthesizes 22.05 kHz audio via `vits-piper-hi_IN-rohan-medium`.
   - `AudioTrack` playback output verified $\to$ **PASS**.

---

## 6. Remaining Gaps Against PS26173 Problem Statement

| Gap # | Problem Statement Requirement | Current Status | Technical Gap & Recommendation |
|:---:|---|---|---|
| **G1** | **10 Indian Languages Full Offline TTS** | English & Hindi bundled | Only English (`lessac`) & Hindi (`rohan`) are bundled in APK to satisfy the < 350 MB APK budget. Additional Indic voices (Tamil, Telugu, Bengali) can be modularly downloaded on-demand. |
| **G2** | **Odia Language Token Support** | Lacking in Whisper | Odia (`or`) is absent from the standard OpenAI Whisper 99-language token set. For Odia, a dedicated IndicASR/Vosk model is required. |
| **G3** | **Ultra-Low SNR Acoustic Robustness** | Silero VAD configured for 0.5 threshold | In combat/tactical noise (> 85 dB), fine-tuned dual-channel beamforming or specialized acoustic pre-emphasis is recommended. |
