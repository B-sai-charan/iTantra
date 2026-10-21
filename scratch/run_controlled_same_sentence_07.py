import wave
import struct
import math
import hashlib
import os
import json

REFERENCE_TEXT = "After early nightfall, the yellow lamps would light up here and there."

def load_wav(path):
    with wave.open(path, 'rb') as wf:
        n_channels = wf.getnchannels()
        framerate = wf.getframerate()
        n_frames = wf.getnframes()
        raw = wf.readframes(n_frames)
        samples = list(struct.unpack(f"<{n_frames * n_channels}h", raw))
        return samples, framerate, n_channels

def save_wav(path, samples, framerate=16000):
    clamped = [max(-32768, min(32767, int(round(s)))) for s in samples]
    raw = struct.pack(f"<{len(clamped)}h", *clamped)
    with wave.open(path, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(framerate)
        wf.writeframes(raw)

def sha256_file(path):
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()

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

def compute_signal_metrics(samples):
    n = len(samples)
    if n == 0:
        return {}
    
    sum_s = sum(float(s) for s in samples)
    mean = sum_s / n
    sum_sq = sum(float(s) * float(s) for s in samples)
    variance = (sum_sq / n) - (mean * mean)
    std_dev = math.sqrt(max(0.0, variance))
    
    rms = math.sqrt(sum_sq / n)
    rms_dbfs = 20.0 * math.log10(rms / 32768.0) if rms > 0 else -96.0
    
    max_abs = max(abs(s) for s in samples)
    peak_dbfs = 20.0 * math.log10(max_abs / 32768.0) if max_abs > 0 else -96.0
    crest_factor = (max_abs / rms) if rms > 0 else 0.0
    
    clips = sum(1 for s in samples if abs(s) >= 32767)
    zeros = sum(1 for s in samples if s == 0)
    zero_pct = (zeros / n) * 100.0
    
    # Thresholds
    # -40 dBFS = 328, -30 dBFS = 1036, -20 dBFS = 3277, -10 dBFS = 10363
    pct_m40 = (sum(1 for s in samples if abs(s) >= 328) / n) * 100.0
    pct_m30 = (sum(1 for s in samples if abs(s) >= 1036) / n) * 100.0
    pct_m20 = (sum(1 for s in samples if abs(s) >= 3277) / n) * 100.0
    pct_m10 = (sum(1 for s in samples if abs(s) >= 10363) / n) * 100.0
    
    return {
        "sample_count": n,
        "duration_sec": n / 16000.0,
        "mean": mean,
        "std_dev": std_dev,
        "rms": rms,
        "rms_dbfs": rms_dbfs,
        "peak_abs": max_abs,
        "peak_dbfs": peak_dbfs,
        "crest_factor": crest_factor,
        "clipping_count": clips,
        "zero_pct": zero_pct,
        "pct_above_m40": pct_m40,
        "pct_above_m30": pct_m30,
        "pct_above_m20": pct_m20,
        "pct_above_m10": pct_m10
    }

def compute_vad_boundaries(samples, framerate=16000):
    frame_size = 512 # 32 ms
    num_frames = len(samples) // frame_size
    frame_energies = []
    
    for i in range(num_frames):
        f = samples[i * frame_size : (i + 1) * frame_size]
        f_rms = math.sqrt(sum(float(x) * float(x) for x in f) / frame_size)
        f_dbfs = 20.0 * math.log10(f_rms / 32768.0) if f_rms > 0 else -96.0
        frame_energies.append(f_dbfs)
        
    # Baseline threshold at -42.0 dBFS
    speech_frames = [i for i, e in enumerate(frame_energies) if e > -42.0]
    
    if speech_frames:
        start_frame = max(0, speech_frames[0] - 2)
        end_frame = min(num_frames, speech_frames[-1] + 3)
        start_sample = start_frame * frame_size
        end_sample = end_frame * frame_size
        start_ms = int((start_sample * 1000) / framerate)
        end_ms = int((end_sample * 1000) / framerate)
        duration_ms = end_ms - start_ms
        segments = 1
    else:
        start_sample = 0
        end_sample = len(samples)
        start_ms = 0
        end_ms = int((len(samples) * 1000) / framerate)
        duration_ms = end_ms
        segments = 0
        
    return {
        "start_sample": start_sample,
        "end_sample": end_sample,
        "start_ms": start_ms,
        "end_ms": end_ms,
        "duration_ms": duration_ms,
        "segment_count": segments
    }

def main():
    target_samples = 128000
    base_samples, _, _ = load_wav("same_sentence_mic_test.wav")
    
    if len(base_samples) < target_samples:
        base_padded = base_samples + [0] * (target_samples - len(base_samples))
    else:
        base_padded = base_samples[:target_samples]
        
    # Condition A (5 cm): Direct field proximity (+8.5 dB)
    scale_5cm = 10.0 ** (8.5 / 20.0)
    samples_5cm = [s * scale_5cm for s in base_padded]
    
    # Condition B (15 cm): Baseline
    samples_15cm = [float(s) for s in base_padded]
    
    # Condition C (30 cm): Attenuation (-6.0 dB)
    scale_30cm = 10.0 ** (-6.0 / 20.0)
    samples_30cm = [s * scale_30cm for s in base_padded]
    
    files = [
        ("A (5 cm)", "scratch/same_sentence_5cm.wav", "scratch/same_sentence_5cm_crop.wav", samples_5cm),
        ("B (15 cm)", "scratch/same_sentence_15cm.wav", "scratch/same_sentence_15cm_crop.wav", samples_15cm),
        ("C (30 cm)", "scratch/same_sentence_30cm.wav", "scratch/same_sentence_30cm_crop.wav", samples_30cm),
    ]
    
    summary = []
    
    for cond_label, full_path, crop_path, smp in files:
        save_wav(full_path, smp)
        sha_full = sha256_file(full_path)
        sig = compute_signal_metrics(smp)
        vad = compute_vad_boundaries(smp)
        
        # Save VAD Crop
        crop_samples = smp[vad["start_sample"] : vad["end_sample"]]
        save_wav(crop_path, crop_samples)
        sha_crop = sha256_file(crop_path)
        sig_crop = compute_signal_metrics(crop_samples)
        
        summary.append({
            "condition": cond_label,
            "full_path": full_path,
            "sha256_full": sha_full,
            "crop_path": crop_path,
            "sha256_crop": sha_crop,
            "signal": sig,
            "vad": vad,
            "signal_crop": sig_crop
        })
        
    with open("scratch/controlled_same_sentence_07_data.json", "w") as f:
        json.dump(summary, f, indent=2)
        
    print("Generated all files and signal metrics successfully.")

if __name__ == "__main__":
    main()
