import subprocess
import wave
import struct
import math
import hashlib
import json

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "HNP097HQ"

REFERENCE_TEXT = "After early nightfall, the yellow lamps would light up here and there."

def pull_file(remote_name, local_path):
    cmd = [ADB, "-s", DEVICE, "exec-out", "run-as", "org.itantra.app", "cat", f"/data/user/0/org.itantra.app/files/{remote_name}"]
    with open(local_path, "wb") as f:
        subprocess.run(cmd, stdout=f, check=True)

def ensure_exact_8s(path):
    with wave.open(path, 'rb') as wf:
        nch = wf.getnchannels()
        sw = wf.getsampwidth()
        fs = wf.getframerate()
        nf = wf.getnframes()
        raw = wf.readframes(nf)
        samples = list(struct.unpack(f"<{nf * nch}h", raw))
    
    target_samples = 128000
    if len(samples) < target_samples:
        samples = samples + [0] * (target_samples - len(samples))
    elif len(samples) > target_samples:
        samples = samples[:target_samples]
        
    raw_out = struct.pack(f"<{target_samples}h", *samples)
    with wave.open(path, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(16000)
        wf.writeframes(raw_out)

pull_file("raw_mic_ab_vr.wav", "scratch/audio_source_voice_recognition.wav")
pull_file("raw_mic_ab_mic.wav", "scratch/audio_source_mic.wav")
pull_file("raw_mic_source_control_en.wav", "scratch/audio_source_default.wav")

ensure_exact_8s("scratch/audio_source_voice_recognition.wav")
ensure_exact_8s("scratch/audio_source_mic.wav")
ensure_exact_8s("scratch/audio_source_default.wav")

for p in ["scratch/audio_source_voice_recognition.wav", "scratch/audio_source_mic.wav", "scratch/audio_source_default.wav"]:
    with wave.open(p, 'rb') as wf:
        print(f"Verified {p}: {wf.getnchannels()} ch, {wf.getframerate()} Hz, {wf.getnframes()} samples ({wf.getnframes()/wf.getframerate():.3f}s)")
