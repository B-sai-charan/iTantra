# M1 Captured Audio ASR Isolation Validation (Task m1-captured-audio-asr-isolation-01)

## Objective

Determine whether the exact WAV file captured by `AudioRecord` on physical Device A (`HNP097HQ`) is itself responsible for the recent live-microphone Whisper ASR failures by executing repeated isolated offline transcriptions on the raw captured file using the production Whisper-Tiny INT8 engine.

---

### Device Details

| Property | Value |
|---|---|
| **Device ID** | `HNP097HQ` |
| **Model** | Lenovo TB-X6C6F |
| **Android Version** | Android 12 (API 31) |
| **ABI Architecture** | `arm64-v8a` |
| **Connection** | ADB Physical Device |

---

### Exact WAV Verification & SHA-256

The exact WAV file was pulled directly from Device A application storage:

| Property | Value | Verification Status |
|---|---|---|
| **Device Storage Path** | `/data/user/0/org.itantra.app/files/mic_integrity_test.wav` | Verified |
| **File Size** | 253,484 bytes | Verified |
| **Expected SHA-256** | `5246badc37514c353521a2b83369bc1256a17aa74443472d103fe9eb8c9343ba` | Expected |
| **Measured SHA-256** | `5246badc37514c353521a2b83369bc1256a17aa74443472d103fe9eb8c9343ba` | **EXACT MATCH** |

---

### WAV Metadata & Audio Signal Statistics

| Parameter | Measured Value | Standard / Health Metric |
|---|---|---|
| **RIFF Format** | Canonical `RIFF` / `WAVE` / `fmt ` / `data` | Uncompressed PCM |
| **Audio Format** | 1 (Linear PCM) | 16-bit Little-Endian |
| **Channels** | 1 (Mono) | Exact mono match |
| **Sample Rate** | 16,000 Hz | Exact match |
| **Byte Rate** | 32,000 bytes/sec | $16,000 \times 1 \times 2$ |
| **Block Align** | 2 bytes | 16-bit alignment |
| **Sample Count** | 126,720 samples | Exact sample count |
| **Audio Duration** | 7,920 ms (7.920 seconds) | Exact timebase alignment |
| **Min Sample** | -5,895 | In $[-32768, 32767]$ |
| **Max Sample** | +8,809 | In $[-32768, 32767]$ |
| **Peak Absolute Amplitude** | 8,809 | 26.88% full-scale headroom |
| **RMS Amplitude** | 492.94 | Normal acoustic energy |
| **RMS dBFS** | -36.45 dBFS | Healthy conversational level |
| **Zero Sample Percentage** | 0.12% (155 zero samples) | Normal zero-crossings |
| **Clipping Count (-32768)** | 0 | No negative clipping |
| **Clipping Count (+32767)** | 0 | No positive clipping |

---

### Three Isolated ASR Inference Runs

* **ASR Engine**: Production `SherpaWhisperAsrEngine` (Whisper-Tiny INT8, greedy search, `defaultLanguage = "en"`)
* **Reference Text**: `"Hello from iTantra. This is a microphone recording integrity test."`
* **Normalized Reference**: `"hello from itantra this is a microphone recording integrity test"` (10 words, 55 characters)

| Metric | Run 1 | Run 2 | Run 3 | Aggregate / Mean |
|---|---|---|---|---|
| **Raw Hypothesis** | `"I'm going to start with the other one."` | `"I'm going to start with the other one."` | `"I'm going to start with the other one."` | Deterministic identical output |
| **Normalized Hypothesis** | `"im going to start with the other one"` | `"im going to start with the other one"` | `"im going to start with the other one"` | 8 words, 30 characters |
| **Detected Language** | `en` | `en` | `en` | `en` (100%) |
| **Substitutions (Words)** | 8 | 8 | 8 | 8 |
| **Deletions (Words)** | 2 | 2 | 2 | 2 |
| **Insertions (Words)** | 0 | 0 | 0 | 0 |
| **Word Error Rate (WER)** | **100.00%** | **100.00%** | **100.00%** | **100.00%** |
| **Character Error Rate (CER)**| **78.18%** | **78.18%** | **78.18%** | **78.18%** |
| **Exact Match** | `false` | `false` | `false` | 0/3 exact match |
| **Inference Time (ms)** | 3,260 ms | 3,144 ms | 3,146 ms | 3,183.3 ms |
| **Audio Duration (ms)** | 7,920 ms | 7,920 ms | 7,920 ms | 7,920 ms |
| **Real-Time Factor (RTF)** | **0.4116** | **0.3970** | **0.3972** | **0.4019** |
| **Runtime Errors** | `None` | `None` | `None` | 0 errors |

---

### Live Pipeline vs. Isolated File Comparison

| Pipeline Path | Input Architecture | Audio Processing | ASR Hypothesis | WER | CER |
|---|---|---|---|---|---|
| **Path A: Live Microphone**<br>*(Task `m1-live-repeatability-01`)* | Real-time `AudioRecord` streaming | Silero VAD segmentation $\to$ `SpeechSegmentCollector` $\to$ Whisper | Run 1: `"I'm gonna be a little bit more careful."`<br>Run 2: `"I'm gonna blow it."`<br>Run 3: `"I'm going to be in the middle."` | **100.0%** | **79.75%** (mean) |
| **Path B: Exact Captured WAV**<br>*(Task `m1-captured-audio-asr-isolation-01`)* | Static WAV file loaded directly from disk | Whole-utterance buffer $\to$ Float conversion $\to$ Whisper | Runs 1–3 (Deterministic):<br>`"I'm going to start with the other one."` | **100.0%** | **78.18%** (mean) |

#### Analysis of Comparison
1. **Audio Integrity**: As confirmed in `m1-mic-audio-integrity-01`, the captured WAV has healthy signal amplitude (-36.45 dBFS), valid 16 kHz mono PCM encoding, zero clipping, and no DC offset.
2. **Deterministic Hallucination**: When the exact captured audio is fed directly into Whisper-Tiny INT8 without VAD or streaming segmentation, Whisper deterministically outputs `"I'm going to start with the other one."` across all 3 repetitions.
3. **Localization**: The 100% WER live failure reproduced directly in isolated static WAV transcription, demonstrating that the failure is reproducible in Whisper ASR on the captured microphone signal independently of real-time streaming transport or segmentation.

---

### Limitations

* This diagnostic evaluates one physical microphone recording of a single prescribed sentence across three repeated offline inference runs.
* It does not evaluate other acoustic environments, noise conditions, or alternative Whisper decoding configurations.

---

### Test Results

- **Unit Test Suite**:
  - Command: `./gradlew testDebugUnitTest`
  - Total Tests: **343**
  - Passed: **343**
  - Failed: **0**
  - Ignored: **0**
- **Debug Build**:
  - Command: `./gradlew assembleDebug`
  - Result: **BUILD SUCCESSFUL**

---

### Conclusion

```text
CAPTURED AUDIO ASR RECOGNITION ISSUE DETECTED
```
