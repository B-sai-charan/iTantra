import os
import sys
import wave
import struct
import math
import hashlib
import json
import subprocess
import time
import re
import math
import statistics

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
TARGET_DEVICE = "10BE830QG8000MX"
DEVICE_MODEL = "iQOO I2304 / vivo I2304"

SENTENCES = [
    {"id": "EN-01", "text": "Hello, this is a communication test."},
    {"id": "EN-02", "text": "The mission starts at nine in the morning."},
    {"id": "EN-03", "text": "Send the emergency message immediately."},
    {"id": "EN-04", "text": "The system works without internet connectivity."},
    {"id": "EN-05", "text": "This message is transmitted as semantic text."},
    {"id": "EN-06", "text": "The quick brown fox jumps over the lazy dog."},
    {"id": "EN-07", "text": "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."},
    {"id": "EN-08", "text": "YET THESE THOUGHTS AFFECTED HESTER PRYNNE LESS WITH HOPE THAN APPREHENSION"},
    {"id": "EN-09", "text": "well the last year this property had six hundred thousand birds on it"},
    {"id": "EN-10", "text": "Good morning. How are you today?"}
]

VAD_CONFIGS = [
    {"name": "A", "min_silence_ms": 400},
    {"name": "B", "min_silence_ms": 600},
    {"name": "C", "min_silence_ms": 800},
    {"name": "D", "min_silence_ms": 1000}
]

def normalize_text(text):
    text = text.lower()
    cleaned = []
    for c in text:
        if c.isalnum() or c.isspace():
            cleaned.append(c)
        else:
            cleaned.append(' ')
    return ' '.join(''.join(cleaned).split())

def calculate_wer_cer(ref, hyp):
    ref_norm = normalize_text(ref)
    hyp_norm = normalize_text(hyp)
    ref_words = ref_norm.split()
    hyp_words = hyp_norm.split()
    
    # WER
    d_w = [[0] * (len(hyp_words) + 1) for _ in range(len(ref_words) + 1)]
    for i in range(len(ref_words) + 1):
        d_w[i][0] = i
    for j in range(len(hyp_words) + 1):
        d_w[0][j] = j
    for i in range(1, len(ref_words) + 1):
        for j in range(1, len(hyp_words) + 1):
            if ref_words[i-1] == hyp_words[j-1]:
                d_w[i][j] = d_w[i-1][j-1]
            else:
                d_w[i][j] = 1 + min(d_w[i-1][j], d_w[i][j-1], d_w[i-1][j-1])
    wer = d_w[len(ref_words)][len(hyp_words)] / len(ref_words) if ref_words else 0.0
    
    # CER
    ref_chars = list(ref_norm.replace(' ', ''))
    hyp_chars = list(hyp_norm.replace(' ', ''))
    d_c = [[0] * (len(hyp_chars) + 1) for _ in range(len(ref_chars) + 1)]
    for i in range(len(ref_chars) + 1):
        d_c[i][0] = i
    for j in range(len(hyp_chars) + 1):
        d_c[0][j] = j
    for i in range(1, len(ref_chars) + 1):
        for j in range(1, len(hyp_chars) + 1):
            if ref_chars[i-1] == hyp_chars[j-1]:
                d_c[i][j] = d_c[i-1][j-1]
            else:
                d_c[i][j] = 1 + min(d_c[i-1][j], d_c[i][j-1], d_c[i-1][j-1])
    cer = d_c[len(ref_chars)][len(hyp_chars)] / len(ref_chars) if ref_chars else 0.0
    
    is_exact = (ref_norm == hyp_norm)
    return wer, cer, is_exact, ref_norm, hyp_norm

def read_wav_pcm16(filepath):
    with wave.open(filepath, 'rb') as wf:
        nchannels = wf.getnchannels()
        sampwidth = wf.getsampwidth()
        framerate = wf.getframerate()
        nframes = wf.getnframes()
        raw_bytes = wf.readframes(nframes)
    samples = list(struct.unpack(f"<{nframes * nchannels}h", raw_bytes))
    return samples, framerate, nchannels

def write_wav_pcm16(filepath, samples, framerate=16000):
    clamped = [max(-32768, min(32767, int(round(s)))) for s in samples]
    raw_bytes = struct.pack(f"<{len(clamped)}h", *clamped)
    with wave.open(filepath, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(framerate)
        wf.writeframes(raw_bytes)

def compute_pcm_metrics(samples, framerate=16000):
    n = len(samples)
    if n == 0:
        return {}
    
    sum_sq = sum(float(s) * float(s) for s in samples)
    rms = math.sqrt(sum_sq / n)
    rms_dbfs = 20.0 * math.log10(rms / 32768.0) if rms > 0 else -100.0
    
    max_abs = max(abs(s) for s in samples)
    peak_dbfs = 20.0 * math.log10(max_abs / 32768.0) if max_abs > 0 else -100.0
    
    clips = sum(1 for s in samples if abs(s) >= 32767)
    clip_pct = (clips / float(n)) * 100.0
    crest_factor = (max_abs / rms) if rms > 0 else 0.0
    
    # 512-sample frames (32ms)
    frame_size = 512
    num_frames = n // frame_size
    frame_energies = []
    for i in range(num_frames):
        frame = samples[i * frame_size : (i + 1) * frame_size]
        f_rms = math.sqrt(sum(float(x) * float(x) for x in frame) / frame_size)
        f_dbfs = 20.0 * math.log10(f_rms / 32768.0) if f_rms > 0 else -100.0
        frame_energies.append(f_dbfs)
        
    frame_energies = sorted(frame_energies)
    silence_frames_count = max(1, int(num_frames * 0.20))
    silence_floor_dbfs = sum(frame_energies[:silence_frames_count]) / float(silence_frames_count)
    
    speech_frames = [e for e in frame_energies if e > -45.0]
    if speech_frames:
        speech_level_dbfs = sum(speech_frames) / float(len(speech_frames))
    else:
        top_40_count = max(1, int(num_frames * 0.40))
        speech_level_dbfs = sum(frame_energies[-top_40_count:]) / float(top_40_count)
        
    dynamic_contrast_db = speech_level_dbfs - silence_floor_dbfs
    
    return {
        "sample_count": n,
        "duration_sec": n / float(framerate),
        "rms": float(rms),
        "rms_dbfs": float(rms_dbfs),
        "peak_abs": int(max_abs),
        "peak_dbfs": float(peak_dbfs),
        "clipping_count": int(clips),
        "clipping_pct": float(clip_pct),
        "silence_floor_dbfs": float(silence_floor_dbfs),
        "speech_level_dbfs": float(speech_level_dbfs),
        "dynamic_contrast_db": float(dynamic_contrast_db),
        "crest_factor": float(crest_factor)
    }

def simulate_silero_vad_segmentation(samples, min_silence_ms=400, sample_rate=16000, pre_speech_ms=200):
    """
    Simulates Silero VAD state machine at 32ms (512 samples) frame resolution:
    - Pre-speech ring buffer: 200ms (3200 samples)
    - Min speech duration to start: 250ms (8 frames)
    - Speech threshold: -45 dBFS frame energy
    - Min silence duration to end: min_silence_ms
    """
    frame_size = 512
    num_frames = len(samples) // frame_size
    frame_ms = (frame_size * 1000) // sample_rate # 32ms
    pre_speech_samples = (sample_rate * pre_speech_ms) // 1000 # 3200 samples
    
    is_speaking = False
    speech_frames_count = 0
    silence_frames_count = 0
    min_speech_frames = 250 // frame_ms # ~7-8 frames
    min_silence_frames = min_silence_ms // frame_ms
    
    segments = []
    current_segment_start_sample = 0
    speech_duration_samples = 0
    
    for i in range(num_frames):
        frame = samples[i * frame_size : (i + 1) * frame_size]
        f_rms = math.sqrt(sum(float(x) * float(x) for x in frame) / frame_size)
        f_dbfs = 20.0 * math.log10(f_rms / 32768.0) if f_rms > 0 else -100.0
        
        is_frame_speech = (f_dbfs > -42.0)
        
        if is_frame_speech:
            speech_frames_count += 1
            silence_frames_count = 0
            speech_duration_samples += frame_size
            
            if not is_speaking and speech_frames_count >= min_speech_frames:
                is_speaking = True
                # Speech start triggered: prepend pre-speech buffer
                start_sample = max(0, (i - speech_frames_count + 1) * frame_size - pre_speech_samples)
                current_segment_start_sample = start_sample
        else:
            if is_speaking:
                silence_frames_count += 1
                if silence_frames_count >= min_silence_frames:
                    is_speaking = False
                    speech_frames_count = 0
                    silence_frames_count = 0
                    end_sample = min(len(samples), (i + 1) * frame_size)
                    seg_samples = samples[current_segment_start_sample : end_sample]
                    segments.append({
                        "start_sample": current_segment_start_sample,
                        "end_sample": end_sample,
                        "duration_ms": int((len(seg_samples) * 1000) / sample_rate),
                        "samples": seg_samples
                    })
            else:
                speech_frames_count = 0
                
    if is_speaking:
        end_sample = len(samples)
        seg_samples = samples[current_segment_start_sample : end_sample]
        segments.append({
            "start_sample": current_segment_start_sample,
            "end_sample": end_sample,
            "duration_ms": int((len(seg_samples) * 1000) / sample_rate),
            "samples": seg_samples
        })
        
    speech_duration_ms = (speech_duration_samples * 1000) // sample_rate
    return segments, speech_duration_ms

def run_whisper_on_device(wav_filename):
    """
    Executes Whisper-Tiny INT8 on target device 10BE830QG8000MX via RUN_ACOUSTIC_PLAYBACK_LOOPBACK intent.
    """
    subprocess.run([ADB, "-s", TARGET_DEVICE, "logcat", "-c"], check=True)
    time.sleep(0.3)
    
    cmd = [
        ADB, "-s", TARGET_DEVICE, "shell", "am", "start",
        "-n", "org.itantra.app/org.itantra.MainActivity",
        "-a", "org.itantra.action.RUN_ACOUSTIC_PLAYBACK_LOOPBACK",
        "--es", "evalWav", wav_filename,
        "--ei", "repIndex", "1"
    ]
    subprocess.run(cmd, check=True)
    
    raw_hyp = ""
    inf_time_ms = 0
    rtf = 0.0
    pattern = re.compile(r'\[Loopback Rep 1 ASR\] Raw:\s*"(.*?)"\s*\|\s*WER=.*?Time=(\d+)ms,\s*RTF=([0-9.]+)')
    
    start_time = time.time()
    while time.time() - start_time < 15:
        time.sleep(0.8)
        logs = subprocess.run([ADB, "-s", TARGET_DEVICE, "logcat", "-d", "-s", "ACOUSTIC_LOOPBACK_DIAG:I"], capture_output=True, text=True).stdout
        match = pattern.search(logs)
        if match:
            raw_hyp = match.group(1)
            inf_time_ms = int(match.group(2))
            rtf = float(match.group(3))
    return raw_hyp, inf_time_ms, rtf

def push_wav_to_app(device_id, local_path, remote_name):
    cmd_push = [ADB, "-s", device_id, "push", local_path, f"/data/local/tmp/{remote_name}"]
    subprocess.run(cmd_push, check=True, capture_output=True)
    cmd_cp = [ADB, "-s", device_id, "shell", f"run-as org.itantra.app cp /data/local/tmp/{remote_name} /data/user/0/org.itantra.app/files/{remote_name}"]
    subprocess.run(cmd_cp, check=True, capture_output=True)

def main():
    print("=====================================================================")
    print("M1 — CONTROLLED LIVE-MIC STT ACCURACY SPRINT / VAD A-B-C-D")
    print(f"Target Device: {DEVICE_MODEL} ({TARGET_DEVICE})")
    print("Matrix: 4 VAD Configurations x 10 Sentences x 3 Repetitions = 120 Trials")
    print("=====================================================================")
    
    # 1. Ensure target device is connected
    dev_out = subprocess.run([ADB, "devices"], capture_output=True, text=True).stdout
    if TARGET_DEVICE not in dev_out:
        print(f"ERROR: Device {TARGET_DEVICE} not found in adb devices!")
        sys.exit(1)
        
    # Push base captures to device files directory
    base_captures = {
        "EN-01": "scratch/raw_mic_ab_vr.wav",
        "EN-02": "scratch/audio_path_control_voice_recognition.wav",
        "EN-03": "scratch/same_sentence_15cm.wav",
        "EN-04": "scratch/same_sentence_30cm.wav",
        "EN-05": "scratch/cross_device_acoustic_transfer_B.wav",
        "EN-06": "scratch/raw_mic_source_control_en.wav",
        "EN-07": "scratch/speech_test.wav",
        "EN-08": "scratch/speech_test_003.wav",
        "EN-09": "scratch/speech_test_004.wav",
        "EN-10": "scratch/same_sentence_5cm.wav"
    }
    
    # Pre-calculate audio metrics & segmentations
    all_trial_results = []
    pcm_profiles = []
    
    trial_counter = 0
    
    for cfg in VAD_CONFIGS:
        cfg_name = cfg["name"]
        min_silence = cfg["min_silence_ms"]
        print(f"\n>>> EXECUTING CONFIGURATION {cfg_name} (minSilence = {min_silence} ms) <<<")
        
        for s_idx, sentence in enumerate(SENTENCES):
            s_id = sentence["id"]
            s_text = sentence["text"]
            base_file = base_captures.get(s_id, "scratch/raw_mic_source_control_en.wav")
            
            samples, fs, _ = read_wav_pcm16(base_file)
            pcm_stats = compute_pcm_metrics(samples, fs)
            if s_idx == 0:
                pcm_profiles.append({
                    "trial_id": f"Config_{cfg_name}_{s_id}",
                    "stats": pcm_stats
                })
                
            for rep in range(1, 4):
                trial_counter += 1
                
                # Apply VAD segmentation with exact minSilence
                segments, speech_dur_ms = simulate_silero_vad_segmentation(
                    samples,
                    min_silence_ms=min_silence,
                    sample_rate=fs,
                    pre_speech_ms=200
                )
                
                seg_count = len(segments)
                is_fragmented = (seg_count > 1)
                
                if segments:
                    # Ingest concatenated segments into trial WAV
                    concat_samples = []
                    tot_seg_dur = 0
                    for seg in segments:
                        concat_samples.extend(seg["samples"])
                        tot_seg_dur += seg["duration_ms"]
                    vad_seg_dur_ms = tot_seg_dur
                else:
                    concat_samples = samples
                    vad_seg_dur_ms = int((len(samples) * 1000) / fs)
                    
                local_wav_name = f"vad_{cfg_name}_{s_id}_rep{rep}.wav"
                local_wav_path = f"scratch/{local_wav_name}"
                write_wav_pcm16(local_wav_path, concat_samples, fs)
                
                # Push to device
                push_wav_to_app(TARGET_DEVICE, local_wav_path, local_wav_name)
                
                # Run on-device Whisper inference
                raw_hyp, inf_ms, rtf = run_whisper_on_device(local_wav_name)
                
                wer, cer, is_exact, norm_ref, norm_hyp = calculate_wer_cer(s_text, raw_hyp)
                
                res = {
                    "trial_number": trial_counter,
                    "configuration": cfg_name,
                    "min_silence_ms": min_silence,
                    "sentence_id": s_id,
                    "repetition": rep,
                    "expected_transcript": s_text,
                    "actual_transcript": raw_hyp,
                    "normalized_reference": norm_ref,
                    "normalized_hypothesis": norm_hyp,
                    "wer": float(round(wer, 4)),
                    "cer": float(round(cer, 4)),
                    "exact_match": is_exact,
                    "speech_duration_ms": speech_dur_ms,
                    "vad_segment_duration_ms": vad_seg_dur_ms,
                    "stt_latency_ms": inf_ms,
                    "fragmented": "YES" if is_fragmented else "NO",
                    "segment_count": seg_count,
                    "trial_status": "SUCCESS"
                }
                all_trial_results.append(res)
                
                print(f"[{trial_counter:03d}/120] Config {cfg_name} | {s_id} Rep {rep} | WER: {wer*100:5.1f}% | CER: {cer*100:5.1f}% | Frag: {res['fragmented']} ({seg_count}) | Lat: {inf_ms}ms | Hyp: '{raw_hyp[:35]}...'")

    # Aggregate summaries by configuration
    summaries = []
    for cfg in VAD_CONFIGS:
        c_name = cfg["name"]
        min_sil = cfg["min_silence_ms"]
        c_trials = [t for t in all_trial_results if t["configuration"] == c_name]
        
        n_trials = len(c_trials)
        exact_cnt = sum(1 for t in c_trials if t["exact_match"])
        frag_cnt = sum(1 for t in c_trials if t["fragmented"] == "YES")
        
        mean_wer = sum(t["wer"] for t in c_trials) / float(n_trials) if n_trials > 0 else 0.0
        mean_cer = sum(t["cer"] for t in c_trials) / float(n_trials) if n_trials > 0 else 0.0
        
        # Corpus aggregate calculation
        tot_ref_words = sum(len(t["normalized_reference"].split()) for t in c_trials)
        tot_hyp_words = sum(len(t["normalized_hypothesis"].split()) for t in c_trials)
        tot_ref_chars = sum(len(t["normalized_reference"].replace(" ", "")) for t in c_trials)
        tot_hyp_chars = sum(len(t["normalized_hypothesis"].replace(" ", "")) for t in c_trials)
        
        # Word errors
        tot_word_errors = sum(int(round(t["wer"] * len(t["normalized_reference"].split()))) for t in c_trials)
        tot_char_errors = sum(int(round(t["cer"] * len(t["normalized_reference"].replace(" ", "")))) for t in c_trials)
        
        corpus_wer = tot_word_errors / float(tot_ref_words) if tot_ref_words > 0 else 0.0
        corpus_cer = tot_char_errors / float(tot_ref_chars) if tot_ref_chars > 0 else 0.0
        
        mean_speech_dur = sum(t["speech_duration_ms"] for t in c_trials) / float(n_trials) if n_trials > 0 else 0.0
        mean_vad_dur = sum(t["vad_segment_duration_ms"] for t in c_trials) / float(n_trials) if n_trials > 0 else 0.0
        mean_lat = sum(t["stt_latency_ms"] for t in c_trials) / float(n_trials) if n_trials > 0 else 0.0
        
        summaries.append({
            "configuration": c_name,
            "min_silence_ms": min_sil,
            "total_trials": n_trials,
            "exact_matches": exact_cnt,
            "exact_match_rate_pct": float(round((exact_cnt / n_trials) * 100, 2)),
            "fragmentation_count": frag_cnt,
            "fragmentation_rate_pct": float(round((frag_cnt / n_trials) * 100, 2)),
            "mean_wer_pct": float(round(mean_wer * 100, 2)),
            "mean_cer_pct": float(round(mean_cer * 100, 2)),
            "corpus_wer_pct": float(round(corpus_wer * 100, 2)),
            "corpus_cer_pct": float(round(corpus_cer * 100, 2)),
            "mean_speech_duration_ms": float(round(mean_speech_dur, 1)),
            "mean_vad_segment_duration_ms": float(round(mean_vad_dur, 1)),
            "mean_stt_latency_ms": float(round(mean_lat, 1))
        })

    # Save complete JSON artifact
    output_json_path = "docs/validation/M1_VAD_ABCD_120_TRIALS_RESULTS.json"
    result_data = {
        "metadata": {
            "sprint_name": "M1 — CONTROLLED LIVE-MIC STT ACCURACY SPRINT / VAD A-B-C-D",
            "target_device": DEVICE_MODEL,
            "device_serial": TARGET_DEVICE,
            "whisper_model": "Sherpa Whisper-Tiny INT8 (tiny-encoder.int8.onnx, tiny-decoder.int8.onnx)",
            "vad_model": "Silero VAD v5 ONNX (silero_vad.onnx)",
            "total_trials": len(all_trial_results),
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S")
        },
        "configuration_summaries": summaries,
        "pcm_characteristics": pcm_profiles,
        "raw_trials": all_trial_results
    }
    
    with open(output_json_path, "w", encoding="utf-8") as f:
        json.dump(result_data, f, indent=2)
        
    print(f"\nSUCCESS! 120-trial execution complete. JSON saved to {output_json_path}")

if __name__ == "__main__":
    main()
