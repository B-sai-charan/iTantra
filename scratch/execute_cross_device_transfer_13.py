import wave
import struct
import math
import hashlib
import json
import subprocess
import time
import re
import shutil
import os

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE_A = "HNP097HQ"
DEVICE_B = "10BE830QG8000MX"

REFERENCE_12 = "After early nightfall, the yellow lamps would light up here and there."
REFERENCE_18 = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."

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

def fft_complex(x):
    n = len(x)
    if n <= 1:
        return x
    even = fft_complex(x[0::2])
    odd = fft_complex(x[1::2])
    t = [math.e ** (-2j * math.pi * k / n) * odd[k] for k in range(n // 2)]
    return [even[k] + t[k] for k in range(n // 2)] + [even[k] - t[k] for k in range(n // 2)]

def compute_spectral_features(samples, fs=16000, n_fft=512, hop_size=256):
    num_frames = (len(samples) - n_fft) // hop_size
    if num_frames <= 0:
        return {}
        
    window = [0.5 * (1.0 - math.cos(2.0 * math.pi * n / (n_fft - 1))) for n in range(n_fft)]
    freq_bins = [k * fs / n_fft for k in range(n_fft // 2 + 1)]
    
    centroids = []
    bandwidths = []
    rolloffs = []
    flatnesses = []
    zcrs = []
    
    band_energies = {
        "0-250": 0.0,
        "250-500": 0.0,
        "500-1000": 0.0,
        "1000-2000": 0.0,
        "2000-4000": 0.0,
        "4000-8000": 0.0,
        "total": 0.0
    }
    
    for i in range(num_frames):
        frame = [complex(samples[i * hop_size + n] * window[n], 0.0) for n in range(n_fft)]
        
        raw_frame = samples[i * hop_size : i * hop_size + n_fft]
        zcr = sum(1 for n in range(1, len(raw_frame)) if (raw_frame[n] >= 0 and raw_frame[n-1] < 0) or (raw_frame[n] < 0 and raw_frame[n-1] >= 0)) / float(n_fft)
        zcrs.append(zcr)
        
        fft_res = fft_complex(frame)
        mags = [abs(fft_res[k]) for k in range(n_fft // 2 + 1)]
        power = [m * m for m in mags]
        total_p = sum(power)
        
        if total_p > 1e-12:
            centroid = sum(f * p for f, p in zip(freq_bins, power)) / total_p
            centroids.append(centroid)
            
            bw = math.sqrt(sum(((f - centroid) ** 2) * p for f, p in zip(freq_bins, power)) / total_p)
            bandwidths.append(bw)
            
            cum_p = 0.0
            rolloff_p = 0.85 * total_p
            rf = freq_bins[-1]
            for f, p in zip(freq_bins, power):
                cum_p += p
                if cum_p >= rolloff_p:
                    rf = f
                    break
            rolloffs.append(rf)
            
            n_bins = len(power)
            log_sum = sum(math.log(max(1e-12, p)) for p in power)
            geom_mean = math.exp(log_sum / n_bins)
            arith_mean = total_p / n_bins
            flatness = geom_mean / arith_mean if arith_mean > 0 else 0.0
            flatnesses.append(flatness)
            
            for f, p in zip(freq_bins, power):
                band_energies["total"] += p
                if f < 250.0:
                    band_energies["0-250"] += p
                elif f < 500.0:
                    band_energies["250-500"] += p
                elif f < 1000.0:
                    band_energies["500-1000"] += p
                elif f < 2000.0:
                    band_energies["1000-2000"] += p
                elif f < 4000.0:
                    band_energies["2000-4000"] += p
                else:
                    band_energies["4000-8000"] += p
                    
    tot = band_energies["total"] if band_energies["total"] > 0 else 1.0
    band_pcts = {
        "b0_250_pct": (band_energies["0-250"] / tot) * 100.0,
        "b250_500_pct": (band_energies["250-500"] / tot) * 100.0,
        "b500_1000_pct": (band_energies["500-1000"] / tot) * 100.0,
        "b1000_2000_pct": (band_energies["1000-2000"] / tot) * 100.0,
        "b2000_4000_pct": (band_energies["2000-4000"] / tot) * 100.0,
        "b4000_8000_pct": (band_energies["4000-8000"] / tot) * 100.0,
    }
    
    return {
        "centroid": sum(centroids) / len(centroids) if centroids else 0.0,
        "bandwidth": sum(bandwidths) / len(bandwidths) if bandwidths else 0.0,
        "rolloff": sum(rolloffs) / len(rolloffs) if rolloffs else 0.0,
        "flatness": sum(flatnesses) / len(flatnesses) if flatnesses else 0.0,
        "zcr": sum(zcrs) / len(zcrs) if zcrs else 0.0,
        "band_pcts": band_pcts
    }

def run_on_device_whisper(device_id, remote_wav_name):
    subprocess.run([ADB, "-s", device_id, "logcat", "-c"], check=True)
    time.sleep(0.5)
    cmd = [
        ADB, "-s", device_id, "shell", "am", "start",
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
        logs = subprocess.run([ADB, "-s", device_id, "logcat", "-d", "-s", "ACOUSTIC_LOOPBACK_DIAG:I"], capture_output=True, text=True).stdout
        match = pattern.search(logs)
        if match:
            raw_hyp = match.group(1)
            inf_time_ms = int(match.group(2))
            rtf = float(match.group(3))
            break
    return raw_hyp, inf_time_ms, rtf

def trigger_device_capture(device_id, label):
    print(f"Triggering 8.000s capture on {device_id} ({label})...")
    subprocess.run([ADB, "-s", device_id, "logcat", "-c"], check=True)
    cmd = [
        ADB, "-s", device_id, "shell", "am", "start",
        "-n", "org.itantra.app/org.itantra.MainActivity",
        "-a", "org.itantra.action.RUN_MIC_ROUTING_CONTROL",
        "--es", "testType", "EN",
        "--es", "audioSource", "VOICE_RECOGNITION"
    ]
    subprocess.run(cmd, check=True)
    
    # Wait for capture to complete (8s recording + 2s processing)
    time.sleep(11.0)

def pull_wav_from_app(device_id, remote_name, local_path):
    cmd = [ADB, "-s", device_id, "exec-out", "run-as", "org.itantra.app", "cat", f"/data/user/0/org.itantra.app/files/{remote_name}"]
    with open(local_path, "wb") as f:
        subprocess.run(cmd, stdout=f, check=True)

def push_wav_to_app(device_id, local_path, remote_name):
    cmd_push = [ADB, "-s", device_id, "push", local_path, f"/data/local/tmp/{remote_name}"]
    subprocess.run(cmd_push, check=True)
    cmd_cp = [ADB, "-s", device_id, "shell", f"run-as org.itantra.app cp /data/local/tmp/{remote_name} /data/user/0/org.itantra.app/files/{remote_name}"]
    subprocess.run(cmd_cp, check=True)

def main():
    print("=================================================================")
    print("STARTING M1-CROSS-DEVICE-ACOUSTIC-TRANSFER-13")
    print("=================================================================")
    
    # Copy speech_test.wav to scratch
    ref_asset_src = "app/src/main/assets/speech_test.wav"
    ref_scratch_path = "scratch/speech_test.wav"
    shutil.copyfile(ref_asset_src, ref_scratch_path)
    
    # Capture Device A
    trigger_device_capture(DEVICE_A, "Device A — Lenovo TB-X6C6F")
    dev_a_path = "scratch/cross_device_acoustic_transfer_A.wav"
    pull_wav_from_app(DEVICE_A, "raw_mic_source_control_en.wav", dev_a_path)
    
    # Ensure exact 128,000 samples
    samples_a, fs_a, _ = read_wav_pcm16(dev_a_path)
    if len(samples_a) != 128000:
        if len(samples_a) < 128000:
            samples_a = samples_a + [0] * (128000 - len(samples_a))
        else:
            samples_a = samples_a[:128000]
        write_wav_pcm16(dev_a_path, samples_a, 16000)
        
    # Capture Device B
    trigger_device_capture(DEVICE_B, "Device B — vivo I2304")
    dev_b_path = "scratch/cross_device_acoustic_transfer_B.wav"
    pull_wav_from_app(DEVICE_B, "raw_mic_source_control_en.wav", dev_b_path)
    
    samples_b, fs_b, _ = read_wav_pcm16(dev_b_path)
    if len(samples_b) != 128000:
        if len(samples_b) < 128000:
            samples_b = samples_b + [0] * (128000 - len(samples_b))
        else:
            samples_b = samples_b[:128000]
        write_wav_pcm16(dev_b_path, samples_b, 16000)
        
    # Test matrix items
    matrix = [
        ("Reference Clean WAV", "Digital LibriSpeech Asset", ref_scratch_path, "speech_test.wav"),
        ("Device A Raw Microphone", "Lenovo TB-X6C6F (HNP097HQ)", dev_a_path, "cross_device_acoustic_transfer_A.wav"),
        ("Device B Raw Microphone", "vivo I2304 (10BE830QG8000MX)", dev_b_path, "cross_device_acoustic_transfer_B.wav"),
        ("Optional Same-Device Control", "Device A (same_sentence_mic_test.wav)", "scratch/same_sentence_mic_test.wav", "same_sentence_mic_test.wav")
    ]
    
    results = []
    
    # Push all test files to Device A for standardized inference
    for label, dev_name, local_p, remote_n in matrix:
        push_wav_to_app(DEVICE_A, local_p, remote_n)
        
    print("\n--- Running Standardized Whisper Evaluation on Device A ---")
    for label, dev_name, local_p, remote_n in matrix:
        samples, fs, _ = read_wav_pcm16(local_p)
        file_sha = sha256_file(local_p)
        sig = compute_signal_metrics(samples, fs)
        spec = compute_spectral_features(samples, fs)
        
        raw_hyp, inf_ms, rtf = run_on_device_whisper(DEVICE_A, remote_n)
        wer12, cer12, norm_ref12, norm_hyp = calculate_wer_cer(REFERENCE_12, raw_hyp)
        wer18, cer18, norm_ref18, _ = calculate_wer_cer(REFERENCE_18, raw_hyp)
        
        print(f"\nSource: {label} [{dev_name}]:")
        print(f"  SHA-256: {file_sha}")
        print(f"  Duration: {sig['duration_sec']:.3f} s ({sig['sample_count']} samples)")
        print(f"  RMS: {sig['rms_dbfs']:.2f} dBFS | Peak: {sig['peak_dbfs']:.2f} dBFS | Crest: {sig['crest_factor']:.2f} | Clips: {sig['clipping_count']}")
        print(f"  Centroid: {spec['centroid']:.2f} Hz | Rolloff: {spec['rolloff']:.2f} Hz | Bandwidth: {spec['bandwidth']:.2f} Hz | Flatness: {spec['flatness']:.4f}")
        print(f"  Bands (%): 0-250: {spec['band_pcts']['b0_250_pct']:.2f}%, 250-500: {spec['band_pcts']['b250_500_pct']:.2f}%, 500-1k: {spec['band_pcts']['b500_1000_pct']:.2f}%, 1-2k: {spec['band_pcts']['b1000_2000_pct']:.2f}%, 2-4k: {spec['band_pcts']['b2000_4000_pct']:.2f}%, 4-8k: {spec['band_pcts']['b4000_8000_pct']:.2f}%")
        print(f"  Transcript: \"{raw_hyp}\"")
        print(f"  Latency: {inf_ms} ms | RTF: {rtf:.4f}")
        print(f"  12-word WER: {wer12*100:.2f}% | CER: {cer12*100:.2f}%")
        print(f"  18-word WER: {wer18*100:.2f}% | CER: {cer18*100:.2f}%")
        
        results.append({
            "source_label": label,
            "device_name": dev_name,
            "file_path": local_p,
            "sha256": file_sha,
            "signal": sig,
            "spectral": spec,
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
        
    with open("scratch/cross_device_transfer_13_results.json", "w") as f:
        json.dump(results, f, indent=2)
        
    print("\nCompleted M1-13 diagnostic. Results saved to scratch/cross_device_transfer_13_results.json")

if __name__ == "__main__":
    main()
