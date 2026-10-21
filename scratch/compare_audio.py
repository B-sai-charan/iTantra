import struct
import hashlib

with open("app/src/main/assets/speech_test_hi_001.wav", "rb") as f:
    existing_bytes = f.read()

with open("scratch/fleurs_audio_raw/10011266027513218401.wav", "rb") as f:
    raw_bytes = f.read()

raw_floats = struct.unpack("<145920f", raw_bytes[58:])
existing_shorts = struct.unpack("<145920h", existing_bytes[44:])

# Compare where the difference comes from
diffs = []
for i, (f, s) in enumerate(zip(raw_floats, existing_shorts)):
    r32767 = int(round(f * 32767.0))
    r32768 = int(round(f * 32768.0))
    if s not in (r32767, r32768):
        diffs.append((i, f, s, r32767, r32768, s / f if f != 0 else 0))

print(f"Total diff samples (neither round*32767 nor round*32768): {len(diffs)}")
if diffs:
    for d in diffs[:10]:
        print(f"Index {d[0]}: float={d[1]}, existing={d[2]}, r32767={d[3]}, r32768={d[4]}, ratio={d[5]}")

try:
    import soundfile as sf
    data, sr = sf.read("scratch/fleurs_audio_raw/10011266027513218401.wav")
    sf.write("scratch/test_sf.wav", data, sr, subtype='PCM_16')
    import hashlib
    with open("scratch/test_sf.wav", "rb") as f:
        print(f"SoundFile PCM_16 SHA256: {hashlib.sha256(f.read()).hexdigest()}")
except Exception as e:
    print(f"SoundFile exception: {e}")
