# Whisper Model Asset Audit: Old vs New
**Project:** PS26173 — iTantra  
**Date:** October 1, 2026  
**Status:** AUDIT COMPLETED

---

## 1. Checkpoint & Asset Identification

### Root Cause Audit
Investigation of `app/src/main/assets/models/whisper-tiny/tiny-encoder.int8.onnx` revealed embedded ONNX ModelMetadata:
- `n_vocab`: `51865`
- `sot_sequence`: `50258,50259,50359`
- `all_language_tokens`: `50317,50296,50261,50276 (hi),...`
- `all_language_codes`: `sq,ml,de,hi,uz,zh,nl,he,...`
- `transcribe`: `50359`
- `translate`: `50358`
- `no_timestamps`: `50363`

This confirms that **`tiny-encoder.int8.onnx` and `tiny-decoder.int8.onnx` are the genuine Multilingual Whisper-Tiny INT8 models**.

However, the previous `tiny-tokens.txt` file contained only **50,257 tokens** (the English-only GPT-2 vocabulary), truncated right before `<|endoftext|>` (50257) and all 99 multilingual language tokens (`<|en|>`: 50259 .. `<|hi|>`: 50276).

When Sherpa-ONNX loaded this truncated 50,257-line token file:
1. It initialized with an encoder/decoder expecting vocabulary size 51,865.
2. The internal `id2token_` map only held entries for token IDs 0 through 50256.
3. Language conditioning on `<|hi|>` (50276) and transcription on `<|transcribe|>` (50359) attempted to index past the token table, causing fallback and outputting Latin phonetic syllables.

---

## 2. Tokenizer & Model Comparison Table

| Property | Old Model Asset (`whisper-tiny`) | Replacement Multilingual Asset | Verification Status |
|---|---|---|---|
| **Tokenizer File** | `tiny-tokens.txt` (50,257 tokens) | `tiny-tokens.txt` (51,865 tokens) | **VERIFIED** |
| **Vocab Size (`n_vocab`)** | 50,257 (English GPT-2) | 51,865 (Multilingual Standard) | **VERIFIED** |
| **Token ID Range** | `0 .. 50256` | `0 .. 51864` | **VERIFIED** |
| **`<|hi|>` Token (Hindi)** | ❌ Missing | ✅ Present (ID 50276) | **VERIFIED** |
| **`<|en|>` Token (English)** | ❌ Missing | ✅ Present (ID 50259) | **VERIFIED** |
| **`<|transcribe|>` Token** | ❌ Missing | ✅ Present (ID 50359) | **VERIFIED** |
| **`<|translate|>` Token** | ❌ Missing | ✅ Present (ID 50358) | **VERIFIED** |
| **`<|notimestamps|>` Token** | ❌ Missing | ✅ Present (ID 50363) | **VERIFIED** |
| **Timestamp Tokens** | ❌ 0 / 1501 | ✅ 1501 / 1501 (`<|0.00|>` .. `<|30.00|>`) | **VERIFIED** |
| **Encoder Compatibility** | `tiny-encoder.int8.onnx` (51865 metadata) | `tiny-encoder.int8.onnx` (100% matched) | **VERIFIED** |
| **Decoder Compatibility** | `tiny-decoder.int8.onnx` (51865 output) | `tiny-decoder.int8.onnx` (100% matched) | **VERIFIED** |
| **Quantization** | INT8 Quantized (12.9 MB + 89.8 MB) | INT8 Quantized (12.9 MB + 89.8 MB) | **VERIFIED** |

---

## 3. Compatibility & Validation Summary

- **Encoder/Decoder Architecture**: 4-layer 6-head 384-state Transformer.
- **Sample Rate / Features**: 16 kHz mono PCM16 $\to$ 80-dim log-mel spectrogram.
- **Language Flow**:
  - `requestedLanguage ("hi" / "en")` $\to$ `SherpaOfflineWhisperModel(language)` $\to$ `sot_sequence [50258, 50276/50259, 50359]`.
  - Recognizer decodes into valid tokens $0 .. 51864$.
  - Preserves UTF-8 Unicode bytes for Devanagari text.
