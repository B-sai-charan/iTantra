import struct
import hashlib

with open("app/src/main/assets/speech_test_hi_001.wav", "rb") as f:
    existing_bytes = f.read()

with open("scratch/fleurs_audio_raw/10011266027513218401.wav", "rb") as f:
    raw_bytes = f.read()

raw_floats = struct.unpack("<145920f", raw_bytes[58:])

shorts = []
for f in raw_floats:
    if f >= 0:
        val = int(round(f * 32767.0))
    else:
        val = int(round(f * 32768.0))
    val = max(-32768, min(32767, val))
    shorts.append(val)

data = struct.pack("<145920h", *shorts)
hdr = struct.pack("<4sI4s4sIHHIIHH4sI", b'RIFF', 36+len(data), b'WAVE', b'fmt ', 16, 1, 1, 16000, 32000, 2, 16, b'data', len(data))
final_wav = hdr + data

sha = hashlib.sha256(final_wav).hexdigest()
target_sha = hashlib.sha256(existing_bytes).hexdigest()

print(f"Computed SHA256: {sha}")
print(f"Target   SHA256: {target_sha}")
print(f"Match: {sha == target_sha}")
