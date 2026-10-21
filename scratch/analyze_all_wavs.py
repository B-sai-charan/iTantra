import subprocess
import base64
import os
import wave
import struct
import math
import hashlib

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "HNP097HQ"

def pull_b64(remote_name, local_path):
    cmd = [ADB, "-s", DEVICE, "shell", f"run-as org.itantra.app base64 files/{remote_name}"]
    p = subprocess.run(cmd, capture_output=True, text=True)
    if p.returncode == 0 and len(p.stdout.strip()) > 0:
        data = base64.b64decode(p.stdout.strip())
        with open(local_path, "wb") as f:
            f.write(data)
        print(f"Pulled {remote_name} -> {local_path} ({len(data)} bytes)")
    else:
        print(f"Failed to pull {remote_name}: {p.stderr}")

os.makedirs("scratch", exist_ok=True)
pull_b64("raw_mic_source_control_en.wav", "scratch/raw_mic_source_control_en.wav")
pull_b64("raw_mic_source_control_hi.wav", "scratch/raw_mic_source_control_hi.wav")
pull_b64("raw_mic_ab_vr.wav", "scratch/raw_mic_ab_vr.wav")
pull_b64("raw_mic_ab_mic.wav", "scratch/raw_mic_ab_mic.wav")
pull_b64("same_sentence_mic_test.wav", "scratch/same_sentence_mic_test.wav")

files = [
    ("same_sentence_mic_test.wav", "scratch/same_sentence_mic_test.wav"),
    ("raw_mic_en_control.wav", "scratch/raw_mic_en_control.wav"),
    ("raw_mic_hi_control.wav", "scratch/raw_mic_hi_control.wav"),
    ("raw_mic_source_control_en.wav", "scratch/raw_mic_source_control_en.wav"),
    ("raw_mic_source_control_hi.wav", "scratch/raw_mic_source_control_hi.wav"),
    ("raw_mic_ab_vr.wav", "scratch/raw_mic_ab_vr.wav"),
    ("raw_mic_ab_mic.wav", "scratch/raw_mic_ab_mic.wav")
]

print("\n" + "="*50)
print("AUDIO METRICS TABLE")
print("="*50)

for name, path in files:
    if not os.path.exists(path):
        print(f"MISSING: {name}")
        continue
    with open(path, "rb") as f:
        data = f.read()
        sha = hashlib.sha256(data).hexdigest()
    try:
        with wave.open(path, "rb") as w:
            nchannels = w.getnchannels()
            sampwidth = w.getsampwidth()
            framerate = w.getframerate()
            nframes = w.getnframes()
            raw_frames = w.readframes(nframes)
        
        samples = struct.unpack(f"<{nframes}h", raw_frames)
        sum_sq = sum(s*s for s in samples)
        rms = math.sqrt(sum_sq / len(samples)) if samples else 0
        rms_db = 20 * math.log10(rms / 32768.0) if rms > 0 else -100.0
        peak = max(abs(s) for s in samples) if samples else 0
        peak_db = 20 * math.log10(peak / 32768.0) if peak > 0 else -100.0
        crest_factor = peak / rms if rms > 0 else 0
        crest_db = 20 * math.log10(crest_factor) if crest_factor > 0 else 0
        zero_cnt = sum(1 for s in samples if s == 0)
        zero_pct = (zero_cnt / len(samples)) * 100.0 if samples else 0
        clip_cnt = sum(1 for s in samples if abs(s) >= 32767)
        clip_pct = (clip_cnt / len(samples)) * 100.0 if samples else 0
        min_s = min(samples) if samples else 0
        max_s = max(samples) if samples else 0
        duration = nframes / framerate

        print(f"=== {name} ===")
        print(f"SHA-256: {sha}")
        print(f"Format: {framerate} Hz, {nchannels} ch, {sampwidth*8}-bit PCM")
        print(f"Duration: {duration:.3f} s ({nframes} samples)")
        print(f"RMS: {rms:.2f} ({rms_db:.2f} dBFS)")
        print(f"Peak: {peak} ({peak_db:.2f} dBFS)")
        print(f"Crest Factor: {crest_factor:.2f} ({crest_db:.2f} dB)")
        print(f"Zero %: {zero_pct:.2f}% ({zero_cnt} samples)")
        print(f"Clipping: {clip_cnt} ({clip_pct:.4f}%)")
        print(f"Min/Max: [{min_s}, {max_s}]")
        print()
    except Exception as e:
        print(f"Error reading {name}: {e}")
