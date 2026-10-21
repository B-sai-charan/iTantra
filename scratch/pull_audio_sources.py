import subprocess
import wave
import struct
import math
import hashlib

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "HNP097HQ"

def pull_file(remote_name, local_path):
    cmd = [ADB, "-s", DEVICE, "exec-out", "run-as", "org.itantra.app", "cat", f"/data/user/0/org.itantra.app/files/{remote_name}"]
    with open(local_path, "wb") as f:
        subprocess.run(cmd, stdout=f, check=True)

pull_file("scratch_audio_source_voice_recognition.wav", "scratch/audio_source_voice_recognition.wav")
pull_file("scratch_audio_source_mic.wav", "scratch/audio_source_mic.wav")

# Also pull raw_mic_source_control_en.wav if needed
pull_file("raw_mic_source_control_en.wav", "scratch/audio_source_default.wav")

for name in ["scratch/audio_source_voice_recognition.wav", "scratch/audio_source_mic.wav", "scratch/audio_source_default.wav"]:
    with wave.open(name, "rb") as wf:
        nch = wf.getnchannels()
        fs = wf.getframerate()
        nf = wf.getnframes()
        dur = nf / fs
        print(f"Verified {name}: {nch} ch, {fs} Hz, {nf} frames ({dur:.3f} s)")
