import wave
import struct
import math
import hashlib
import json
import subprocess
import time
import re
import os

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "HNP097HQ"

REFERENCE_12 = "After early nightfall, the yellow lamps would light up here and there."
REFERENCE_18 = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."

SOURCE_RUNS = [
    (1, "scratch/same_sentence_reference_vs_mic.wav", "scratch/vad_streaming_run1.wav"),
    (2, "scratch/same_sentence_15cm.wav", "scratch/vad_streaming_run2.wav"),
    (3, "scratch/same_sentence_mic_test.wav", "scratch/vad_streaming_run3.wav"),
]

def sha256_file(filepath):
    h = hashlib.sha256()
    with open(filepath, 'rb') as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

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
    
    return wer, cer, ref_norm, hyp_norm

def compute_signal_metrics(samples, framerate=16000):
    n = len(samples)
    if n == 0:
        return {}
    sum_s = sum(float(s) for s in samples)
    mean = sum_s / n
    sum_sq = sum(float(s) * float(s) for s in samples)
    variance = (sum_sq / n) - (mean * mean)
    std_dev = math.sqrt(max(0.0, variance))
    
    rms = math.sqrt(sum_sq / n)
    rms_dbfs = 20.0 * math.log10(rms / 32768.0) if rms > 0 else -100.0
    
    max_abs = max(abs(s) for s in samples)
    peak_dbfs = 20.0 * math.log10(max_abs / 32768.0) if max_abs > 0 else -100.0
    crest_factor = (max_abs / rms) if rms > 0 else 0.0
    
    clips = sum(1 for s in samples if abs(s) >= 32767)
    zeros = sum(1 for s in samples if s == 0)
    zero_pct = (zeros / n) * 100.0
    
    return {
        "sample_count": n,
        "duration_sec": n / float(framerate),
        "mean": mean,
        "std_dev": std_dev,
        "rms": rms,
        "rms_dbfs": rms_dbfs,
        "peak_abs": max_abs,
        "peak_dbfs": peak_dbfs,
        "crest_factor": crest_factor,
        "clipping_count": clips,
        "zero_pct": zero_pct
    }

def compute_vad_boundaries(samples, framerate=16000):
    frame_size = 512
    num_frames = len(samples) // frame_size
    frame_energies = []
    
    for i in range(num_frames):
        f = samples[i * frame_size : (i + 1) * frame_size]
        f_rms = math.sqrt(sum(float(x) * float(x) for x in f) / frame_size)
        f_dbfs = 20.0 * math.log10(f_rms / 32768.0) if f_rms > 0 else -96.0
        frame_energies.append(f_dbfs)
        
    speech_frames = [i for i, e in enumerate(frame_energies) if e > -42.0]
    
    if speech_frames:
        start_frame = max(0, speech_frames[0] - 2)
        end_frame = min(num_frames, speech_frames[-1] + 3)
        start_sample = start_frame * frame_size
        end_sample = end_frame * frame_size
        start_ms = int((start_sample * 1000) / framerate)
        end_ms = int((end_sample * 1000) / framerate)
        duration_ms = end_ms - start_ms
    else:
        start_sample = 0
        end_sample = len(samples)
        start_ms = 0
        end_ms = int((len(samples) * 1000) / framerate)
        duration_ms = end_ms
        
    return {
        "start_sample": start_sample,
        "end_sample": end_sample,
        "start_ms": start_ms,
        "end_ms": end_ms,
        "duration_ms": duration_ms
    }

def run_on_device_whisper(remote_wav_name):
    subprocess.run([ADB, "-s", DEVICE, "logcat", "-c"], check=True)
    time.sleep(0.5)
    cmd = [
        ADB, "-s", DEVICE, "shell", "am", "start",
        "-n", "org.itantra.app/org.itantra.MainActivity",
        "-a", "org.itantra.action.RUN_ACOUSTIC_PLAYBACK_LOOPBACK",
        "--es", "evalWav", remote_wav_name,
        "--ei", "repIndex", "1"
    ]
    subprocess.run(cmd, check=True)
    
    raw_hyp = ""
    inf_time_ms = 0
    rtf = 0.0
    pattern = re.compile(r'\[Loopback Rep 1 ASR\] Raw:\s*"(.*?)"\s*\|\s*WER=.*?Time=(\d+)ms,\s*RTF=([0-9.]+)')
    start_time = time.time()
    while time.time() - start_time < 15:
        time.sleep(1.0)
        logs = subprocess.run([ADB, "-s", DEVICE, "logcat", "-d", "-s", "ACOUSTIC_LOOPBACK_DIAG:I"], capture_output=True, text=True).stdout
        match = pattern.search(logs)
        if match:
            raw_hyp = match.group(1)
            inf_time_ms = int(match.group(2))
            rtf = float(match.group(3))
            break
    return raw_hyp, inf_time_ms, rtf

def main():
    print("=================================================================")
    print("STARTING M1-VAD-TRIGGERED-STREAMING-INGESTION-12 EVALUATION")
    print("=================================================================")
    
    # 1. Prepare 3 raw captures
    eval_items = []
    
    for run_idx, src_path, target_raw_path in SOURCE_RUNS:
        samples, fs, _ = read_wav_pcm16(src_path)
        target_len = 128000
        if len(samples) < target_len:
            samples = samples + [0] * (target_len - len(samples))
        elif len(samples) > target_len:
            samples = samples[:target_len]
        write_wav_pcm16(target_raw_path, samples, fs)
        
        vad = compute_vad_boundaries(samples, fs)
        
        # Condition A: Full buffer
        full_name = f"vad_run{run_idx}_full.wav"
        full_path = f"scratch/{full_name}"
        write_wav_pcm16(full_path, samples, fs)
        
        # Condition B: VAD crop
        crop_samples = samples[vad["start_sample"] : vad["end_sample"]]
        crop_name = f"vad_run{run_idx}_crop.wav"
        crop_path = f"scratch/{crop_name}"
        write_wav_pcm16(crop_path, crop_samples, fs)
        
        # Condition C: VAD + 200ms pre / 500ms post padding
        pad_pre_samples = int(0.200 * fs) # 3200 samples
        pad_post_samples = int(0.500 * fs) # 8000 samples
        
        pad_start = max(0, vad["start_sample"] - pad_pre_samples)
        pad_end = min(len(samples), vad["end_sample"] + pad_post_samples)
        padded_samples = samples[pad_start : pad_end]
        padded_name = f"vad_run{run_idx}_padded.wav"
        padded_path = f"scratch/{padded_name}"
        write_wav_pcm16(padded_path, padded_samples, fs)
        
        eval_items.append({
            "run": run_idx,
            "condition": "Condition A (Full Buffer)",
            "cond_key": "FULL",
            "file_path": full_path,
            "remote_name": full_name,
            "samples": samples,
            "start_ms": 0,
            "end_ms": int((len(samples)*1000)/fs),
            "dur_ms": int((len(samples)*1000)/fs),
            "vad": vad
        })
        eval_items.append({
            "run": run_idx,
            "condition": "Condition B (VAD Crop)",
            "cond_key": "VAD_CROP",
            "file_path": crop_path,
            "remote_name": crop_name,
            "samples": crop_samples,
            "start_ms": vad["start_ms"],
            "end_ms": vad["end_ms"],
            "dur_ms": vad["duration_ms"],
            "vad": vad
        })
        eval_items.append({
            "run": run_idx,
            "condition": "Condition C (VAD + 200/500ms Pad)",
            "cond_key": "VAD_PADDED",
            "file_path": padded_path,
            "remote_name": padded_name,
            "samples": padded_samples,
            "start_ms": int((pad_start*1000)/fs),
            "end_ms": int((pad_end*1000)/fs),
            "dur_ms": int((len(padded_samples)*1000)/fs),
            "vad": vad
        })

    # Push all 9 files to device
    for item in eval_items:
        lp = item["file_path"]
        rn = item["remote_name"]
        print(f"Pushing {lp} to device as {rn}...")
        subprocess.run([ADB, "-s", DEVICE, "push", lp, f"/data/local/tmp/{rn}"], check=True)
        subprocess.run([ADB, "-s", DEVICE, "shell", f"run-as org.itantra.app cp /data/local/tmp/{rn} /data/user/0/org.itantra.app/files/{rn}"], check=True)

    results = []
    print("\n--- Running On-Device Whisper Inference for 9 Variants ---")
    
    for item in eval_items:
        run_num = item["run"]
        cond = item["condition"]
        rn = item["remote_name"]
        lp = item["file_path"]
        smp = item["samples"]
        
        sig = compute_signal_metrics(smp)
        file_sha = sha256_file(lp)
        
        raw_hyp, inf_ms, rtf = run_on_device_whisper(rn)
        wer12, cer12, norm_ref12, norm_hyp = calculate_wer_cer(REFERENCE_12, raw_hyp)
        wer18, cer18, norm_ref18, _ = calculate_wer_cer(REFERENCE_18, raw_hyp)
        
        print(f"Run {run_num} | {cond} ({rn}):")
        print(f"  Speech Bounds: {item['start_ms']}ms -> {item['end_ms']}ms (Dur: {item['dur_ms']}ms)")
        print(f"  RMS: {sig['rms_dbfs']:.2f} dBFS | Peak: {sig['peak_dbfs']:.2f} dBFS | SHA: {file_sha[:12]}...")
        print(f"  Transcript: \"{raw_hyp}\"")
        print(f"  Latency: {inf_ms} ms | RTF: {rtf:.4f} | 12-word WER: {wer12*100:.2f}% | CER: {cer12*100:.2f}%")
        
        results.append({
            "run": run_num,
            "condition": cond,
            "cond_key": item["cond_key"],
            "remote_name": rn,
            "file_path": lp,
            "sha256": file_sha,
            "speech_start_ms": item["start_ms"],
            "speech_end_ms": item["end_ms"],
            "duration_ms": item["dur_ms"],
            "signal": sig,
            "whisper": {
                "raw_hypothesis": raw_hyp,
                "normalized_hypothesis": norm_hyp,
                "latency_ms": inf_ms,
                "rtf": rtf,
                "wer_12_pct": round(wer12 * 100.0, 2),
                "cer_12_pct": round(cer12 * 100.0, 2),
                "wer_18_pct": round(wer18 * 100.0, 2),
                "cer_18_pct": round(cer18 * 100.0, 2)
            }
        })

    with open("scratch/vad_streaming_ingestion_12_results.json", "w") as f:
        json.dump(results, f, indent=2)
        
    print("\nCompleted all 9 evaluations. Saved to scratch/vad_streaming_ingestion_12_results.json")

if __name__ == "__main__":
    main()
