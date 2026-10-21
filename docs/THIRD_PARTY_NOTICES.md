# Third-Party Notices & Open Source Attributions

**Project:** iTantra — Tactical Offline Speech Transceiver  
**Scope:** Smart India Hackathon (SIH 2026) Prototype / Non-Commercial Educational Evaluation  

This application incorporates pre-trained neural models, open-source runtimes, and acoustic data from third-party open-source projects. All components are utilized in accordance with their respective licensing terms for offline evaluation and research prototype demonstration.

---

## 1. Neural Text-to-Speech (TTS) Models

### A. Hindi Neural TTS Model (`vits-piper-hi_IN-priyamvada-medium`)
- **Model Name:** `vits-piper-hi_IN-priyamvada-medium`
- **Architecture:** VITS (`piper` ONNX computation graph)
- **Model License:** **CC-BY-NC-SA 4.0** (Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International)
- **Training Dataset:** AI4Bharat `indicnlp_corpus` (Indic TTS Hindi dataset)
- **Trainer / Contributor:** PravalX ([`https://github.com/PravalX`](https://github.com/PravalX))
- **Distribution Scope:** Used strictly for non-commercial SIH prototype evaluation and demonstration. Commercial redistribution or proprietary re-licensing is not claimed.

### B. English Neural TTS Model (`vits-piper-en_US-lessac-low`)
- **Model Name:** `vits-piper-en_US-lessac-low`
- **Architecture:** VITS (`piper` ONNX computation graph)
- **Model License:** **MIT License**
- **Training Dataset:** Blizzard Challenge 2013 Lessac Corpus (CSTR, University of Edinburgh)
- **Upstream Repository:** Rhasspy Piper Project ([`https://github.com/rhasspy/piper`](https://github.com/rhasspy/piper))

### C. Phonemization Dictionary Tables (`espeak-ng-data`)
- **Component:** eSpeak-NG phoneme and dictionary tables
- **License:** **GNU General Public License v3.0 (GPLv3)**
- **Upstream Source:** eSpeak-NG Project ([`https://github.com/espeak-ng/espeak-ng`](https://github.com/espeak-ng/espeak-ng))

---

## 2. On-Device Runtimes & Inference Engines

### A. Sherpa-ONNX Offline Engine
- **Component:** `sherpa-onnx` static Android AAR (C++/JNI bindings)
- **License:** **Apache License 2.0**
- **Copyright:** Next-gen Kaldi / k2-fsa Team ([`https://github.com/k2-fsa/sherpa-onnx`](https://github.com/k2-fsa/sherpa-onnx))

### B. ONNX Runtime
- **Component:** ONNX Runtime Embedded Inference Engine
- **License:** **MIT License**
- **Copyright:** Microsoft Corporation ([`https://github.com/microsoft/onnxruntime`](https://github.com/microsoft/onnxruntime))

### C. Silero Voice Activity Detection (VAD)
- **Component:** Silero VAD ONNX model
- **License:** **MIT License**
- **Copyright:** Silero Team ([`https://github.com/snakers4/silero-vad`](https://github.com/snakers4/silero-vad))

### D. OpenAI Whisper Speech Recognition (ASR)
- **Component:** Whisper tiny.en / multilingual acoustic model weights
- **License:** **MIT License**
- **Copyright:** OpenAI ([`https://github.com/openai/whisper`](https://github.com/openai/whisper))
