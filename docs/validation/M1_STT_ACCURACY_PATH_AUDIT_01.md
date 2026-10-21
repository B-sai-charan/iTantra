# M1-STT-ACCURACY-PATH-AUDIT-01: End-to-End STT Path & Transformation Audit

**Document Version:** 1.0.0  
**Date:** September 28, 2026  
**Task ID:** `M1-STT-ACCURACY-IMPROVEMENT-01`  
**Scope:** Strict read-only architectural and diagnostic path audit comparing the production live microphone STT pipeline against the validated known-good benchmark fixture path on Android hardware (`Lenovo TB-X6C6F` / `HNP097HQ`, `arm64-v8a`).  
**Production Code Changes:** **`NONE`**

---

## 1. FACTS: The Production STT Pipeline

The production live microphone speech-to-text path executes the following sequence of transformations from acoustic transducer to `AsrResult`:

```
1. Physical Microphone Diaphragm
   ↓ (Android Audio HAL / OpenSL ES driver)
2. AudioRecord.read(readBuffer: ShortArray(320))
   - Sample Rate: 16,000 Hz
   - Channel: AudioFormat.CHANNEL_IN_MONO (1 channel)
   - Encoding: AudioFormat.ENCODING_PCM_16BIT (2 bytes / sample)
   - Audio Source: MediaRecorder.AudioSource.VOICE_RECOGNITION (fallback to MIC if uninitialized)
   - Chunk Duration: 20 ms (320 samples / 640 bytes per chunk)
   - Threading: Dedicated background thread with Process.THREAD_PRIORITY_URGENT_AUDIO
   ↓
3. AudioRecordCapture.runCaptureLoop()
   - Creates contiguous immutable copy: frameCopy = readBuffer.copyOf(samplesRead)
   - Dispatches frameCopy to listener.onAudioFrame(frameCopy, samplesRead)
   ↓
4. SpeechPipeline.captureListener.onAudioFrame()
   - Step A: Feeds frameCopy into VadFrameAdapter.process(buffer, readCount)
     * Accumulates samples in ArrayList<Short> FIFO buffer
     * Whenever buffer.size >= 512 samples (32 ms @ 16 kHz), extracts 512-sample frame
     * Dispatches to SileroVadEngine.process(frame): converts Short to FloatArray (sample / 32768.0f)
     * Silero VAD v5 ONNX evaluates 576-sample tensor (512 new + 64 context)
     * Returns List<VadResult> (probability, isSpeech, isSpeechStart, isSpeechEnd)
   - Step B: Feeds frameCopy + vadResults into SpeechSegmentCollector.onAudioData()
     * When idle (!isCollecting): continuously writes to circular preSpeechRingBuffer (capacity = 3,200 samples = 200 ms)
     * On isSpeechStart: sets isCollecting = true, clears activeSpeechBuffer, prepends all 200 ms of pre-speech samples
     * During active speech (isCollecting == true): appends incoming frameCopy samples directly to activeSpeechBuffer
     * On isSpeechEnd (triggered when Silero VAD detects minSilenceDurationMs = 400 ms silence):
       - Copies activeSpeechBuffer into segmentAudio: ShortArray(totalSamples)
       - Dispatches segmentAudio to onSegmentReady() callback
   ↓
5. SpeechPipeline.asrChannel (Channel<AsrJob>(Channel.UNLIMITED))
   - Enqueues AsrJob(audio = segmentAudio, durationMs, speechEndNanos, segmentIndex, ...)
   - Serial worker coroutine processes queued jobs sequentially without blocking capture thread
   ↓
6. SherpaWhisperAsrEngine.transcribe(audio: ShortArray, sampleRate = 16000, language = "en")
   - Verifies audio.isNotEmpty() and sampleRate == 16000
   - Converts ShortArray PCM16 to FloatArray [-1.0, +1.0]: floatSamples[i] = audio[i] / 32768.0f
   - Creates Sherpa stream: stream = recognizer.createStream()
   - Feeds PCM: stream.acceptWaveform(floatSamples, 16000)
   - Decodes: recognizer.decode(stream) via greedy_search, numThreads = 2, provider = "cpu"
   - Retrieves: result = recognizer.getResult(stream) -> text, lang
   - Releases stream: stream.release()
   ↓
7. AsrResult(text, language, confidence = null, inferenceTimeMs, audioDurationMs)
   - Mapped to domain SpeechMessage and dispatched to M2 transport sink
```

---

## 2. KNOWN-GOOD PATH: The Validated Benchmark Fixture Pipeline

The verified known-good path (used in `AsrAccuracyBenchmarkRunner` and `AsrSmokeTestRunner`) executes as follows:

```
1. Frozen WAV Asset on Disk (e.g. app/src/main/assets/speech_test.wav)
   - SHA-256: 6bc58a4efdf20daac252b6b1502632601a71efe0308f6757dc1eda34891a7e4f
   - Format: 16,000 Hz, 1 channel (Mono), 16-bit Linear PCM, 106,000 samples (6,625 ms)
   - Source: Studio recording from LibriSpeech (clean acoustic studio mic, high SNR)
   ↓
2. AsrSmokeTestRunner.loadWavFromAsset()
   - Reads asset byte stream, parses standard 44-byte (or 'data' chunk) RIFF header
   - Wraps PCM payload into ByteBuffer (ByteOrder.LITTLE_ENDIAN).asShortBuffer()
   - Populates audioFixture: ShortArray(106000)
   ↓
3. SherpaWhisperAsrEngine.transcribe(audio = audioFixture, sampleRate = 16000, language = "en")
   - Converts ShortArray to FloatArray [-1.0, +1.0]: floatSamples[i] = audioFixture[i] / 32768.0f
   - stream = recognizer.createStream()
   - stream.acceptWaveform(floatSamples, 16000)
   - recognizer.decode(stream) (exact same Whisper Tiny INT8 model and greedy search engine)
   - recognizer.getResult(stream)
   - stream.release()
   ↓
4. Result:
   - Transcript: "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."
   - Exact Match: true (3/3 passes, WER: 0.00%, CER: 0.00%, Latency: ~4,992 ms, RTF: 0.75x)
```

---

## 3. DIFFERENCES: Structural & Measurable Comparison

| Parameter / Dimension | Validated Fixture Path | Live Production Mic Path | Path Match? |
| :--- | :--- | :--- | :---: |
| **Sample Rate** | $16,000\,\text{Hz}$ | $16,000\,\text{Hz}$ | **MATCH** |
| **PCM Format** | Signed $16$-bit Linear PCM | Signed $16$-bit Linear PCM | **MATCH** |
| **Channel Count** | $1$ (Mono) | $1$ (Mono) | **MATCH** |
| **ShortArray $\rightarrow$ FloatArray** | `sample / 32768.0f` | `sample / 32768.0f` | **MATCH** |
| **Amplitude Normalization** | None (Raw float `[-1.0, 1.0]`) | None (Raw float `[-1.0, 1.0]`) | **MATCH** |
| **Sample Ordering** | Contiguous FIFO | Contiguous FIFO | **MATCH** |
| **Sample Loss / Duplication** | $0$ dropped, $0$ duplicated | $0$ dropped, $0$ duplicated | **MATCH** |
| **Whisper Model Files** | `tiny-encoder.int8.onnx`, `tiny-decoder.int8.onnx`, `tiny-tokens.txt` | Identical model files in assets | **MATCH** |
| **Whisper Search Strategy** | `greedy_search`, `numThreads = 2`, `tailPaddings = -1` | Identical configuration | **MATCH** |
| **Whisper Language Target** | `defaultLanguage = "en"` | `defaultLanguage = "en"` | **MATCH** |
| **Segment Boundary Origin** | Static pre-recorded file containing native natural studio acoustic silence | Dynamically cropped by Silero VAD + $200\,\text{ms}$ pre-speech ring buffer + $400\,\text{ms}$ trailing silence hangover | **DIFFERENCE** |
| **Acoustic Signal Level (RMS)** | **$-26.54\,\text{dBFS}$** (Studio standard) | **$-38.56\,\text{dBFS} \dots -39.78\,\text{dBFS}$** ($\mathbf{12\text{--}13\,\text{dB}}$ lower) | **DIFFERENCE** |
| **Dynamic Contrast (100ms)** | **$44.69\,\text{dB}$** | **$15.28\,\text{dB} \dots 17.62\,\text{dB}$** ($\mathbf{\sim 29\,\text{dB}}$ narrower) | **DIFFERENCE** |
| **Noise Floor (Min Window)** | **$-63.01\,\text{dBFS}$** | **$-46.24\,\text{dBFS}$** ($\mathbf{16.77\,\text{dB}}$ elevated) | **DIFFERENCE** |
| **Spectral Centroid (Mean)** | **$657.46\,\text{Hz}$** | **$1,507.60\,\text{Hz}$** ($\mathbf{+850.14\,\text{Hz}}$ upward shift) | **DIFFERENCE** |
| **$0\text{--}300\,\text{Hz}$ Energy Fraction** | **$26.02\%$** | **$7.45\%$** ($0.29\times$ low-frequency depletion) | **DIFFERENCE** |
| **$1\text{--}3\,\text{kHz}$ Energy Fraction** | **$12.06\%$** | **$33.28\%$** ($2.76\times$ mid-high concentration) | **DIFFERENCE** |

---

## 4. MEASURED EVIDENCE: Existing Diagnostic Baseline

The workspace contains extensive on-device empirical measurements documenting each segment of the pipeline:

1. **Benchmark Baseline on Clean Reference Audio (`AsrAccuracyBenchmarkRunner`):**
   - 6-case English LibriSpeech/TED suite: **WER $8.29\%$**, **CER $6.12\%$**, **Mean RTF $0.783$** (`DEVICE_VALIDATION_01.md`).
   - Case EN-001 (`speech_test.wav`): **WER $0.00\%$ (3/3 exact match)**, Latency $4,992\,\text{ms}$.

2. **Identical Utterance Acoustic Comparison (`M1_MIC_LOOPBACK_REFERENCE_COMPARISON_01.md`):**
   - Spoken Sentence: *"After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."*
   - Reference File (`speech_test.wav`): Hypothesis = Exact reference ($100\%$ exact match, WER $0.00\%$).
   - Live Mic Recording (`mic_loopback_ref_test.wav`): Hypothesis = `"I'm just using it."` ($0/3$ exact match, WER $100.00\%$).
   - Verified that the engine is not crashing, but decoding an entirely different token sequence due to acoustic degradation.

3. **Digital Gain Experiments (`M1_LIVE_MIC_GAIN_AB_01.md`, `M1_MIC_GAIN_DIAGNOSTIC_01.md`):**
   - Applying digital linear gain multipliers ($+6\,\text{dB}$, $+12\,\text{dB}$, $+18\,\text{dB}$, $+24\,\text{dB}$) scaled both signal and ambient noise floor uniformly.
   - Result: Scaled noise floor triggered increased hallucination length and loop confabulation without recovering word accuracy ($0/3$ exact matches across all gain levels).

4. **AudioSource Mode Isolation (`M1_AUDIOSOURCE_AB_ISOLATION_01.md`, `M1_MIC_SOURCE_CONTROL_02.md`):**
   - Compared `VOICE_RECOGNITION` vs `MIC` hardware routing sources.
   - Both sources captured identical sample rates and unclipped PCM16 audio, with `VOICE_RECOGNITION` providing slightly better background noise attenuation than raw `MIC`, but neither closing the WER gap on live speech.

5. **Silence & Segmentation Control (`M1_ASR_SEGMENTATION_CONTROL_03.md`, `M1_ASR_SILENCE_CONTROL_04.md`):**
   - Trailing digital silence $\ge 500\,\text{ms}$ appended to an unvoiced buffer triggers a $10\times$ repetition loop hallucination (`"I think the main thing is..."`), driving RTF $> 1.0$.
   - Trimming trailing silence to $< 200\,\text{ms}$ or $0\,\text{ms}$ reliably halts autoregressive looping (RTF remains $\sim 0.40\text{--}0.43$), but the core decoded phrase remains phonetically mismatched if the input acoustic spectrum is distorted.

6. **Acoustic Domain Spectral Profiling (`M1_ACOUSTIC_DOMAIN_COMPARISON_05.md`):**
   - Known-good microphone captures (`same_sentence_mic_test.wav`, which achieved WER $0.0\%$) had **$57.93\%$** of their energy in the $500\text{--}1000\,\text{Hz}$ first formant band.
   - Failed microphone captures (`raw_mic_source_control_en.wav`) had only **$30.39\%$** in $500\text{--}1000\,\text{Hz}$, and $2.75\times$ excess energy in sub-$250\,\text{Hz}$ rumble.

---

## 5. HYPOTHESES (Clearly Marked)

1. **[HYPOTHESIS 1] Acoustic Spectral Tilt & Formant Attenuation:**  
   The primary driver of live microphone misrecognition is not a software buffer corruption, but an acoustic spectral tilt mismatch. The tablet's onboard microphone hardware/casing attenuates key speech formants ($500\text{--}1000\,\text{Hz}$) and narrows the dynamic range to $\sim 15\,\text{dB}$ (vs $45\,\text{dB}$ in studio references), causing the Whisper encoder's 80-channel log-Mel filterbank to produce feature maps that fall outside the high-confidence manifold of the Whisper-Tiny model.

2. **[HYPOTHESIS 2] Acoustic Onset Framing & Pre-Speech Context:**  
   Whisper Tiny INT8 is extremely sensitive to the exact acoustic onset boundary. Cropping too close to the first voiced frame deprives the self-attention layers of acoustic context, while including too much unvoiced ambient noise triggers positional encoding drift. The current fixed $200\,\text{ms}$ pre-speech buffer may be capturing ambient room noise preceding soft consonants.

3. **[HYPOTHESIS 3] Autoregressive Hallucination from Low SNR:**  
   When the signal-to-noise ratio drops below $\sim 15\text{--}18\,\text{dB}$, Whisper-Tiny's beam search / greedy search assigns low probability to all vocabulary tokens and collapses into default high-frequency training priors (e.g. `"I don't know what to do"`, `"I'm not saying that"`, `"Music"`).

---

## 6. NOT YET PROVEN

1. **NOT YET PROVEN:** Whether high-pass filtering ($> 150\,\text{Hz}$ to eliminate sub-bass rumble) combined with a mild formant boost ($500\text{--}2000\,\text{Hz}$) can restore Whisper Tiny log-Mel feature representations without amplifying background noise.
2. **NOT YET PROVEN:** Whether acoustic proximity (speaking $5\text{--}10\,\text{cm}$ from the microphone vs $30\text{--}50\,\text{cm}$) alone increases SNR sufficiently to cross Whisper-Tiny's recognition threshold on this specific hardware.
3. **NOT YET PROVEN:** Whether Whisper-Tiny's internal temperature fallback or prompt conditioning (`initial_prompt`) can suppress default hallucination phrases on low-level live captures.

---

## 7. RECOMMENDATION: Single Highest-Value Next Experiment

**Experiment Name:** `M1-CONTROLLED-PROXIMITY-AND-SPECTRAL-EQUALIZATION-DIAGNOSTIC-01`  
**Description:**  
Using the existing frozen captured WAVs (`same_sentence_mic_test.wav` and `mic_loopback_ref_test.wav`) alongside a live calibrated close-proximity utterance:
1. Evaluate an offline, strictly controlled digital pre-processing filter: a 2nd-order Butterworth high-pass filter ($150\,\text{Hz}$ cutoff to eliminate low-frequency rumble) and a parametric formant equalizer ($+3\text{--}6\,\text{dB}$ at $800\,\text{Hz}$).
2. Pass both raw and filtered signals through `SherpaWhisperAsrEngine.transcribe()` without changing any production code or VAD thresholds.
3. Quantify whether spectral shaping closes the WER gap on the exact same recorded samples.

---

## 8. Epistemological Classification

- **FACT:** The data pipeline (sampling rate, PCM16 format, mono channel, float conversion, FIFO ordering, zero sample drops) is structurally byte-for-byte identical between the production path and the known-good benchmark fixture path.
- **FACT:** The difference is entirely in the acoustic input domain (13 dB lower RMS, 29 dB narrower dynamic contrast, upward spectral shift, and VAD boundary segmentation vs static file).
- **MEASURED RESULT:** Verified on physical hardware (`HNP097HQ`, Android 12).
