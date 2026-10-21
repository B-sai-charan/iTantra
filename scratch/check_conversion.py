import struct
import hashlib

with open("app/src/main/assets/speech_test_hi_001.wav", "rb") as f:
    existing_bytes = f.read()

with open("scratch/fleurs_audio_raw/10011266027513218401.wav", "rb") as f:
    raw_bytes = f.read()

print(f"Existing size: {len(existing_bytes)}, SHA256: {hashlib.sha256(existing_bytes).hexdigest()}")
print(f"Raw size: {len(raw_bytes)}, SHA256: {hashlib.sha256(raw_bytes).hexdigest()}")

# Check existing header
riff, size, wave_id = struct.unpack("<4sI4s", existing_bytes[:12])
print(f"Existing header: {riff}, size={size}, wave={wave_id}")
offset = 12
while offset < len(existing_bytes):
    chunk_id, chunk_size = struct.unpack("<4sI", existing_bytes[offset:offset+8])
    print(f"Existing chunk: {chunk_id}, size={chunk_size}")
    if chunk_id == b'fmt ':
        fmt_tag, channels, sample_rate, byte_rate, block_align, bits_per_sample = struct.unpack("<HHIIHH", existing_bytes[offset+8:offset+8+chunk_size][:16])
        print(f"Existing fmt: tag={fmt_tag}, ch={channels}, sr={sample_rate}, bits={bits_per_sample}")
    offset += 8 + chunk_size
    if chunk_size % 2 != 0:
        offset += 1

# Let's check how raw float32 samples convert to match existing bytes
# Raw fleurs wav header has 58 bytes
raw_floats = struct.unpack("<145920f", raw_bytes[58:])
existing_shorts = struct.unpack("<145920h", existing_bytes[44:])

print(f"First 10 raw floats: {raw_floats[:10]}")
print(f"First 10 existing shorts: {existing_shorts[:10]}")

for name, func in [
    ("round*32767", lambda flt: int(round(flt * 32767.0))),
    ("round*32768", lambda flt: max(-32768, min(32767, int(round(flt * 32768.0))))),
    ("int*32767", lambda flt: int(flt * 32767.0)),
    ("int*32768", lambda flt: max(-32768, min(32767, int(flt * 32768.0)))),
]:
    shorts = [func(flt) for flt in raw_floats]
    data = struct.pack("<145920h", *shorts)
    hdr = struct.pack("<4sI4s4sIHHIIHH4sI", b'RIFF', 36+len(data), b'WAVE', b'fmt ', 16, 1, 1, 16000, 32000, 2, 16, b'data', len(data))
    sha = hashlib.sha256(hdr + data).hexdigest()
    print(f"Rule '{name}': SHA256={sha} | Match={sha == hashlib.sha256(existing_bytes).hexdigest()}")
