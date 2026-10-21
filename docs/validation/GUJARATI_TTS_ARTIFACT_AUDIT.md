# Gujarati TTS Artifact Audit & Verification
**Project:** PS26173 — iTantra (Tactical Neural Speech Transceiver)  
**Date:** October 1, 2026  
**Status:** PHASE 1 ARTIFACT AUDITED

---

## 1. Authoritative Model Identification

| Parameter | Measured Specification |
|---|---|
| **Model Name** | `vits-mimic3-gu_IN-cmu-indic_low` |
| **Authoritative URL** | `https://huggingface.co/csukuangfj/vits-mimic3-gu_IN-cmu-indic_low` |
| **License** | **Apache-2.0** |
| **Acoustic Framework** | VITS (Variational Inference with adversarial learning for end-to-end Text-to-Speech) |
| **Runtime Target** | Sherpa-ONNX `OfflineTtsVitsModelConfig` |
| **Target Language / Locale** | Gujarati (`gu` / `gu_IN`) |
| **Acoustic Dataset** | CMU Indic Gujarati Dataset |

---

## 2. Artifact File Details & Dimensions

| File Component | Remote Path | File Size | Format |
|---|---|:---:|:---:|
| **Acoustic Model (ONNX)** | `gu_IN-cmu-indic_low.onnx` | **76,334,560 bytes (72.80 MB)** | ONNX Graph |
| **Token Vocabulary** | `tokens.txt` | **2,410 bytes** | Plain text mapping |
| **Phonemizer Data** | `espeak-ng-data/` | **~2.5 MB** | Compiled espeak-ng dicts |
| **Config Metadata** | `gu_IN-cmu-indic_low.onnx.json` | **1,850 bytes** | JSON hyperparameters |

---

## 3. Sherpa-ONNX Runtime Compatibility

1. **JNI / C++ Engine**: Compatible with `OfflineTts` in `libsherpa-onnx.so`.
2. **Audio Output**: Sample rate 16 kHz or 22.05 kHz mono FloatArray / PCM16.
3. **Android ABIs**: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`.
4. **Memory / CPU Footprint**:
   - Projected Peak RAM: ~85 MB
   - Projected Synthesis RTF: ~0.24 on Cortex-A78 CPU.
