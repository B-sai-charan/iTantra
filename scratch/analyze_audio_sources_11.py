import wave
import struct
import math
import hashlib
import json
import subprocess
import time
import re

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "HNP097HQ"

REFERENCE_12 = "After early nightfall, the yellow lamps would light up here and there."
REFERENCE_18 = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."

FILES = [
    ("Profile A", "MediaRecorder.AudioSource.VOICE_RECOGNITION", 6, "scratch/audio_source_voice_recognition.wav", "audio_source_voice_recognition.wav"),
    ("Profile B", "MediaRecorder.AudioSource.MIC", 1, "scratch/audio_source_mic.wav", "audio_source_mic.wav"),
    ("Profile C", "MediaRecorder.AudioSource.DEFAULT", 0, "scratch/audio_source_default.wav", "audio_source_default.wav")
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
    print("STARTING M1-AUDIO-SOURCE-RECORDER-PROFILE-11 DIAGNOSTIC ANALYSIS")
    print("=================================================================")
    
    # Push the 3 WAVs to /data/user/0/org.itantra.app/files/
    for prof, sname, sid, local_p, remote_name in FILES:
        print(f"Pushing {local_p} to device as {remote_name}...")
        cmd_push = [ADB, "-s", DEVICE, "push", local_p, f"/data/local/tmp/{remote_name}"]
        subprocess.run(cmd_push, check=True)
        cmd_cp = [ADB, "-s", DEVICE, "shell", f"run-as org.itantra.app cp /data/local/tmp/{remote_name} /data/user/0/org.itantra.app/files/{remote_name}"]
        subprocess.run(cmd_cp, check=True)
        
    results = []
    
    for prof, sname, sid, local_p, remote_name in FILES:
        print(f"\n--- Analyzing {prof}: {sname} ({sid}) ---")
        samples, fs, nch = read_wav_pcm16(local_p)
        file_sha = sha256_file(local_p)
        sig = compute_signal_metrics(samples, fs)
        vad = compute_vad_boundaries(samples, fs)
        
        speech_samples = samples[vad["start_sample"] : vad["end_sample"]]
        spec = compute_spectral_features(speech_samples, fs)
        
        raw_hyp, inf_ms, rtf = run_on_device_whisper(remote_name)
        wer12, cer12, norm_ref12, norm_hyp = calculate_wer_cer(REFERENCE_12, raw_hyp)
        wer18, cer18, norm_ref18, _ = calculate_wer_cer(REFERENCE_18, raw_hyp)
        
        print(f"  SHA-256: {file_sha}")
        print(f"  RMS: {sig['rms_dbfs']:.2f} dBFS | Peak: {sig['peak_dbfs']:.2f} dBFS (Peak raw: {sig['peak_abs']})")
        print(f"  VAD Speech: {vad['start_ms']}ms -> {vad['end_ms']}ms ({vad['duration_ms']}ms)")
        print(f"  Spectral Centroid: {spec['centroid']:.2f} Hz | Bandwidth: {spec['bandwidth']:.2f} Hz | Rolloff: {spec['rolloff']:.2f} Hz | Flatness: {spec['flatness']:.4f}")
        print(f"  Bands (%): 0-250: {spec['band_pcts']['b0_250_pct']:.2f}%, 250-500: {spec['band_pcts']['b250_500_pct']:.2f}%, 500-1k: {spec['band_pcts']['b500_1000_pct']:.2f}%, 1-2k: {spec['band_pcts']['b1000_2000_pct']:.2f}%, 2-4k: {spec['band_pcts']['b2000_4000_pct']:.2f}%, 4-8k: {spec['band_pcts']['b4000_8000_pct']:.2f}%")
        print(f"  Whisper Transcript: \"{raw_hyp}\"")
        print(f"  Latency: {inf_ms} ms | RTF: {rtf:.4f} | 12-word WER: {wer12*100:.2f}% | CER: {cer12*100:.2f}%")
        
        results.append({
            "profile": prof,
            "source_name": sname,
            "source_id": sid,
            "file_path": local_p,
            "sha256": file_sha,
            "signal": sig,
            "vad": vad,
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
        
    with open("scratch/audio_source_profile_11_results.json", "w") as f:
        json.dump(results, f, indent=2)
        
    print("\nCompleted analysis. Saved to scratch/audio_source_profile_11_results.json")

if __name__ == "__main__":
    main()
