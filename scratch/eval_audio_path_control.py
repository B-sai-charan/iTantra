import sys
import subprocess
import time
import re
import math
import hashlib
import wave
import struct
import os

sys.stdout.reconfigure(encoding='utf-8')

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "HNP097HQ"

REFERENCE_12 = "After early nightfall, the yellow lamps would light up here and there."
REFERENCE_18 = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."

FILES = [
    ("scratch/audio_path_control_voice_recognition.wav", "audio_source_voice_recognition.wav", "Test A: VOICE_RECOGNITION"),
    ("scratch/audio_path_control_mic.wav", "audio_source_mic.wav", "Test B: MIC"),
    ("scratch/audio_path_control_default.wav", "audio_source_default.wav", "Test C: DEFAULT"),
    ("scratch/speech_test.wav", "speech_test.wav", "Clean Reference Fixture")
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
    
    return wer, cer, ref_norm, hyp_norm

def compute_metrics(wav_path):
    with wave.open(wav_path, 'rb') as wf:
        nchannels = wf.getnchannels()
        sampwidth = wf.getsampwidth()
        framerate = wf.getframerate()
        nframes = wf.getnframes()
        raw_bytes = wf.readframes(nframes)
    
    samples = list(struct.unpack(f"<{nframes * nchannels}h", raw_bytes))
    duration_s = nframes / float(framerate)
    sha256 = hashlib.sha256(open(wav_path, 'rb').read()).hexdigest()
    
    n = len(samples)
    min_val = min(samples)
    max_val = max(samples)
    max_abs = max(abs(s) for s in samples)
    clipping = sum(1 for s in samples if abs(s) >= 32767)
    zeros = sum(1 for s in samples if s == 0)
    zero_pct = (zeros / float(n)) * 100.0 if n > 0 else 0.0
    
    sum_sq = sum(float(s)**2 for s in samples)
    rms = math.sqrt(sum_sq / n) if n > 0 else 0.0
    rms_dbfs = 20.0 * math.log10(rms / 32768.0) if rms > 0 else -100.0
    peak_dbfs = 20.0 * math.log10(max_abs / 32768.0) if max_abs > 0 else -100.0
    crest_factor = max_abs / rms if rms > 0 else 0.0
    
    # Zero crossing rate
    zc = 0
    for i in range(1, n):
        if (samples[i-1] >= 0 and samples[i] < 0) or (samples[i-1] < 0 and samples[i] >= 0):
            zc += 1
    zcr = zc / float(n - 1) if n > 1 else 0.0
    zcr_per_sec = zcr * framerate
    
    # Short-time frame analysis (25ms window = 400 samples, 10ms hop = 160 samples)
    frame_len = int(0.025 * framerate) # 400
    hop_len = int(0.010 * framerate)   # 160
    num_frames = (n - frame_len) // hop_len + 1
    hann = [0.5 * (1.0 - math.cos(2.0 * math.pi * i / (frame_len - 1))) for i in range(frame_len)]
    
    frame_energies_db = []
    spectral_centroids = []
    spectral_flatnesses = []
    
    # 6 Frequency band accumulators (0-250, 250-500, 500-1000, 1000-2000, 2000-4000, 4000-8000)
    band_powers = [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
    total_power = 0.0
    
    num_bins = frame_len // 2 + 1
    bin_freqs = [k * framerate / float(frame_len) for k in range(num_bins)]
    
    step = max(1, num_frames // 100)
    
    for fi in range(0, num_frames, step):
        start = fi * hop_len
        frame = samples[start : start + frame_len]
        
        f_sum_sq = sum(float(x)**2 for x in frame)
        f_rms = math.sqrt(f_sum_sq / frame_len)
        f_dbfs = 20.0 * math.log10(f_rms / 32768.0) if f_rms > 0 else -100.0
        frame_energies_db.append(f_dbfs)
        
        w_frame = [frame[i] * hann[i] for i in range(frame_len)]
        mag_spec = []
        for k in range(num_bins):
            re_val = 0.0
            im_val = 0.0
            for t in range(frame_len):
                angle = -2.0 * math.pi * k * t / frame_len
                re_val += w_frame[t] * math.cos(angle)
                im_val += w_frame[t] * math.sin(angle)
            mag = math.sqrt(re_val*re_val + im_val*im_val)
            mag_spec.append(mag)
            
            pwr = mag * mag
            freq = bin_freqs[k]
            total_power += pwr
            if freq < 250.0:
                band_powers[0] += pwr
            elif freq < 500.0:
                band_powers[1] += pwr
            elif freq < 1000.0:
                band_powers[2] += pwr
            elif freq < 2000.0:
                band_powers[3] += pwr
            elif freq < 4000.0:
                band_powers[4] += pwr
            else:
                band_powers[5] += pwr
                
        spec_sum = sum(mag_spec)
        centroid = sum(bin_freqs[k] * mag_spec[k] for k in range(num_bins)) / spec_sum if spec_sum > 1e-12 else 0.0
        spectral_centroids.append(centroid)
        
        power_spec = [m*m + 1e-12 for m in mag_spec]
        log_sum = sum(math.log(p) for p in power_spec)
        geom_mean = math.exp(log_sum / num_bins)
        arith_mean = sum(power_spec) / num_bins
        flatness = geom_mean / arith_mean if arith_mean > 0 else 0.0
        spectral_flatnesses.append(flatness)
        
    sorted_fe = sorted(frame_energies_db)
    p10 = sorted_fe[int(len(sorted_fe) * 0.10)]
    p90 = sorted_fe[int(len(sorted_fe) * 0.90)]
    dyn_range = p90 - p10
    
    mean_centroid = sum(spectral_centroids) / len(spectral_centroids)
    mean_flatness = sum(spectral_flatnesses) / len(spectral_flatnesses)
    
    band_pcts = [b / total_power * 100.0 if total_power > 0 else 0.0 for b in band_powers]
    
    return {
        "duration_s": duration_s,
        "samples": n,
        "sha256": sha256,
        "clipping": clipping,
        "zero_pct": zero_pct,
        "rms_dbfs": rms_dbfs,
        "peak_dbfs": peak_dbfs,
        "crest_factor": crest_factor,
        "zcr_per_sec": zcr_per_sec,
        "centroid": mean_centroid,
        "flatness": mean_flatness,
        "dyn_range": dyn_range,
        "band_pcts": band_pcts
    }

def run_on_device_asr(eval_wav_name):
    subprocess.run([ADB, "-s", DEVICE, "logcat", "-c"], check=True)
    time.sleep(0.5)
    
    cmd = [
        ADB, "-s", DEVICE, "shell", "am", "start",
        "-n", "org.itantra.app/org.itantra.MainActivity",
        "-a", "org.itantra.action.RUN_ACOUSTIC_PLAYBACK_LOOPBACK",
        "--es", "evalWav", eval_wav_name,
        "--ei", "repIndex", "1"
    ]
    subprocess.run(cmd, check=True)
    
    pattern = re.compile(r'\[Loopback Rep 1 ASR\] Raw:\s*\"(.*?)\"\s*\|\s*WER=.*?Time=(\d+)ms,\s*RTF=([0-9.]+)')
    start_time = time.time()
    raw_hyp = ""
    inf_ms = 0
    rtf = 0.0
    
    while time.time() - start_time < 15:
        time.sleep(1.0)
        logs = subprocess.run([ADB, "-s", DEVICE, "logcat", "-d", "-s", "ACOUSTIC_LOOPBACK_DIAG:I"], capture_output=True).stdout.decode('utf-8', errors='replace')
        m = pattern.search(logs)
        if m:
            raw_hyp = m.group(1)
            inf_ms = int(m.group(2))
            rtf = float(m.group(3))
            break
            
    return raw_hyp, inf_ms, rtf

def main():
    print("=================================================================")
    print("STARTING M1-ANDROID-AUDIO-PATH-CONTROL-16 DIAGNOSTIC")
    print("=================================================================")
    
    results = []
    
    for local_path, device_name, label in FILES:
        print(f"\n--- Processing: {label} ({local_path}) ---")
        metrics = compute_metrics(local_path)
        raw_hyp, inf_ms, rtf = run_on_device_asr(device_name)
        
        target_ref = REFERENCE_18 if "speech_test" in local_path else REFERENCE_12
        wer, cer, norm_ref, norm_hyp = calculate_wer_cer(target_ref, raw_hyp)
        
        item = {
            "label": label,
            "local_path": local_path,
            "device_name": device_name,
            "target_ref": target_ref,
            "metrics": metrics,
            "raw_hyp": raw_hyp,
            "norm_hyp": norm_hyp,
            "wer": wer,
            "cer": cer,
            "inf_ms": inf_ms,
            "rtf": rtf
        }
        results.append(item)
        
        print(f"SHA256: {metrics['sha256']}")
        print(f"RMS: {metrics['rms_dbfs']:.2f} dBFS | Peak: {metrics['peak_dbfs']:.2f} dBFS | Dyn Range: {metrics['dyn_range']:.2f} dB")
        print(f"Centroid: {metrics['centroid']:.1f} Hz | Flatness: {metrics['flatness']:.4f} | ZCR: {metrics['zcr_per_sec']:.1f}/s")
        print(f"Band Energies: 0-250: {metrics['band_pcts'][0]:.1f}%, 250-500: {metrics['band_pcts'][1]:.1f}%, 500-1k: {metrics['band_pcts'][2]:.1f}%, 1k-2k: {metrics['band_pcts'][3]:.1f}%, 2k-4k: {metrics['band_pcts'][4]:.1f}%, 4k-8k: {metrics['band_pcts'][5]:.1f}%")
        print(f"ASR Raw: \"{raw_hyp}\"")
        print(f"WER: {wer*100:.2f}% | CER: {cer*100:.2f}% | Latency: {inf_ms}ms | RTF: {rtf:.4f}")
        
    print("\n=================================================================")
    print("ALL RUNS COMPLETE")
    print("=================================================================")

if __name__ == "__main__":
    main()
