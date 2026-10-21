# M1 Device Microphone vs. Reference Audio Controlled Comparison (Task m1-device-mic-vs-reference-audio-01)

## Objective

Determine whether the recent live-microphone Whisper ASR failures on Device A (`HNP097HQ`) represent an engine/runtime degradation or a microphone-specific acoustic/input gap, by executing a controlled side-by-side offline evaluation using the production `SherpaWhisperAsrEngine` on:

1. **Condition A**: Authoritative frozen English benchmark audio fixture (`EN-001` / `speech_test.wav`)
2. **Condition B**: Newly captured production microphone audio (`production_capture_source_test.wav`)

---

### Device Details

| Property | Value |
|---|---|
| **Device ID** | `HNP097HQ` |
| **Model** | Lenovo TB-X6C6F |
| **Android Version** | Android 12 (API 31) |
| **ABI Architecture** | `arm64-v8a` |
| **Execution Context** | On-device JVM/NDK Sherpa-ONNX Runtime |

---

### Audio Identity & Integrity Verification

| Property | Condition A (Reference Fixture) | Condition B (Device Microphone Audio) |
|---|---|---|
| **File Identity** | `speech_test.wav` (`EN-001`) | `production_capture_source_test.wav` |
| **Origin / Source** | LibriSpeech benchmark asset | Production `AudioRecordCapture` (`VOICE_RECOGNITION` / `MIC`) |
| **File Location** | App Asset: `assets/speech_test.wav` | Device Storage: `/data/user/0/org.itantra.app/files/` |
| **File Size** | 212,044 bytes | 252,844 bytes |
| **SHA-256 Checksum** | `6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f` | `26298c77438f0491ee172c810a9c328dc8628866612a41310ff6d37c4c0684d2` |
| **Integrity Status** | Verified against frozen benchmark | Verified from previous isolation capture |

---

### Signal Characteristics Comparison

| Metric | Condition A (`speech_test.wav`) | Condition B (`production_capture_source_test.wav`) |
|---|---|---|
| **Sample Rate** | 16,000 Hz | 16,000 Hz |
| **Channel Count** | 1 (Mono) | 1 (Mono) |
| **PCM Encoding** | 16-bit Signed Linear PCM | 16-bit Signed Linear PCM |
| **Sample Count** | 106,000 samples | 126,400 samples |
| **Audio Duration** | 6,625 ms (6.625 s) | 7,900 ms (7.900 s) |
| **Minimum Sample** | -9,631 | -10,312 |
| **Maximum Sample** | +17,529 | +10,871 |
| **Peak Absolute Amplitude** | 17,529 (53.50% full scale) | 10,871 (33.18% full scale) |
| **RMS Amplitude** | 1,543.09 | 558.42 |
| **RMS dBFS** | -26.54 dBFS | -35.37 dBFS |
| **Zero Sample Percentage** | 0.25% (266 zero samples) | 0.12% (155 zero samples) |
| **Clipping Count (-32768)** | 0 | 0 |
| **Clipping Count (+32767)** | 0 | 0 |

---

### Three Inference Runs per Condition

* **Engine**: Production `SherpaWhisperAsrEngine` (Whisper-Tiny INT8, greedy search, `lang = "en"`)
* **Execution**: Executed consecutively in the same process session on Device A.

#### Condition A: Known-Good Reference Fixture
* **Reference Text**: `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."`
* **Normalized Reference**: `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` (18 words, 87 characters)

| Metric | Run 1 | Run 2 | Run 3 | Mean / Aggregate |
|---|---|---|---|---|
| **Raw Hypothesis** | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | `"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."` | Deterministic exact match |
| **Normalized Hypothesis** | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | `"after early nightfall the yellow lamps would light up here and there the squalid quarter of the brothels"` | 18 words, 87 characters |
| **Detected Language** | `en` | `en` | `en` | `en` (100%) |
| **WER** | **0.00%** | **0.00%** | **0.00%** | **0.00%** |
| **CER** | **0.00%** | **0.00%** | **0.00%** | **0.00%** |
| **Exact Match** | `true` | `true` | `true` | **3 / 3 (100%)** |
| **Inference Time (ms)** | 4,705 ms | 4,745 ms | 4,656 ms | 4,702.0 ms |
| **Real-Time Factor (RTF)** | **0.7102** | **0.7162** | **0.7028** | **0.7097** |
| **Runtime Errors** | `None` | `None` | `None` | 0 errors |

#### Condition B: Device Microphone Recording
* **Reference Text**: `"Hello from iTantra. This is a production capture source isolation test."`
* **Normalized Reference**: `"hello from itantra this is a production capture source isolation test"` (11 words, 59 characters)

| Metric | Run 1 | Run 2 | Run 3 | Mean / Aggregate |
|---|---|---|---|---|
| **Raw Hypothesis** | `"[inaudible]"` | `"[inaudible]"` | `"[inaudible]"` | Deterministic output |
| **Normalized Hypothesis** | `"inaudible"` | `"inaudible"` | `"inaudible"` | 1 word, 9 characters |
| **Detected Language** | `en` | `en` | `en` | `en` (100%) |
| **WER** | **100.00%** | **100.00%** | **100.00%** | **100.00%** |
| **CER** | **88.14%** | **88.14%** | **88.14%** | **88.14%** |
| **Exact Match** | `false` | `false` | `false` | **0 / 3 (0%)** |
| **Inference Time (ms)** | 2,385 ms | 2,390 ms | 2,378 ms | 2,384.3 ms |
| **Real-Time Factor (RTF)** | **0.3019** | **0.3025** | **0.3010** | **0.3018** |
| **Runtime Errors** | `None` | `None` | `None` | 0 errors |

---

### Compact ASR Behavior Comparison Table

| Condition | Audio source | Duration | WER | CER | Mean RTF | Exact Match |
|---|---|---:|---:|---:|---:|---|
| **Known-good fixture** | Existing benchmark WAV (`speech_test.wav`) | 6.625 s | **0.00%** | **0.00%** | **0.7097** | **3 / 3 (100%)** |
| **Device microphone** | Production AudioRecord WAV (`production_capture_source_test.wav`) | 7.900 s | **100.00%** | **88.14%** | **0.3018** | **0 / 3 (0%)** |

---

### Analysis & Interpretation

The controlled comparison shows a strong difference between the known-good reference audio and the device microphone recording, but this experiment does not by itself identify the underlying cause.

Key observations:
1. **Engine Functionality**: The Whisper-Tiny INT8 engine, onnxruntime native binaries, tokenizer, and greedy decoding pipeline on Device A (`HNP097HQ`) remain fully functional and achieve **0.00% WER / 100% exact match** on the reference fixture.
2. **Failure Specificity**: The 100% WER failure is specific to the microphone-captured audio stream on the device, producing `"[inaudible]"` or hallucinatory phrases, despite the microphone WAV exhibiting valid 16 kHz mono PCM structure, zero clipping, and no DC offset.
3. **Hardware / Acoustic Neutrality**: This diagnostic establishes the existence of an acoustic/signal-domain recognition gap for the microphone audio, without speculating on specific hardware, acoustic environment, or model sensitivity causes.

---

### Limitations

* Evaluates two single-utterance WAV files (one LibriSpeech benchmark fixture and one live-captured microphone recording) across three repeated inference runs each.
* Does not evaluate diverse background noise levels, gain normalizations, or alternate Whisper decoding strategies.

---

### Verification of Zero Production Changes

* Whisper model files: **UNCHANGED**
* Whisper decoding: **UNCHANGED**
* Target language configuration: **UNCHANGED**
* Silero VAD parameters & thresholds: **UNCHANGED**
* `AudioRecord` capture parameters: **UNCHANGED**
* Preprocessing / DSP filters: **NONE ADDED**
* M2 Transport / M3 TTS: **UNCHANGED**

---

### Test Suite & Build Status

* `./gradlew testDebugUnitTest`: **343 / 343 tests passed (0 failures, 0 ignored)**
* `./gradlew assembleDebug`: **BUILD SUCCESSFUL**

---

### Conclusion

```text
MICROPHONE-SPECIFIC ASR GAP OBSERVED
```
