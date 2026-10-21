import urllib.request
import tarfile
import hashlib
import struct
import os
import sys

sys.stdout.reconfigure(encoding='utf-8')

target_files = {
    "10011266027513218401.wav": ("HI-001", "speech_test_hi_001.wav"),
    "14585676639063535081.wav": ("HI-002", "speech_test_hi_002.wav"),
    "16976841982852749134.wav": ("HI-003", "speech_test_hi_003.wav"),
    "2129043417079181978.wav":  ("HI-004", "speech_test_hi_004.wav"),
    "1137772989789278397.wav":  ("HI-005", "speech_test_hi_005.wav")
}

os.makedirs("scratch/fleurs_audio_raw", exist_ok=True)
os.makedirs("scratch/fleurs_audio_pcm16", exist_ok=True)

url = "https://huggingface.co/datasets/google/fleurs/resolve/main/data/hi_in/audio/test.tar.gz"
print(f"Opening stream from {url}...")
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
resp = urllib.request.urlopen(req)

found_count = 0
with tarfile.open(fileobj=resp, mode='r|gz') as tar:
    for member in tar:
        base_name = os.path.basename(member.name)
        if base_name in target_files:
            case_id, dest_name = target_files[base_name]
            raw_path = os.path.join("scratch/fleurs_audio_raw", base_name)
            f = tar.extractfile(member)
            raw_data = f.read()
            with open(raw_path, "wb") as out_f:
                out_f.write(raw_data)
            
            raw_sha256 = hashlib.sha256(raw_data).hexdigest()
            print(f"--- {case_id} ({base_name}) ---")
            print(f"  Raw downloaded size: {len(raw_data)} bytes")
            print(f"  Raw SHA256: {raw_sha256}")
            
            # Parse RIFF WAV header
            riff, size, wave_id = struct.unpack("<4sI4s", raw_data[:12])
            print(f"  RIFF header: {riff.decode('ascii')}, {wave_id.decode('ascii')}")
            
            # Find fmt and data chunks
            offset = 12
            fmt_tag = None
            channels = None
            sample_rate = None
            bits_per_sample = None
            audio_samples_float = []
            
            while offset < len(raw_data):
                chunk_id, chunk_size = struct.unpack("<4sI", raw_data[offset:offset+8])
                chunk_data = raw_data[offset+8:offset+8+chunk_size]
                if chunk_id == b'fmt ':
                    fmt_tag, channels, sample_rate, byte_rate, block_align, bits_per_sample = struct.unpack("<HHIIHH", chunk_data[:16])
                    print(f"  fmt chunk: format_tag={fmt_tag} (1=PCM, 3=IEEE_FLOAT), channels={channels}, sample_rate={sample_rate}, bits={bits_per_sample}")
                elif chunk_id == b'data':
                    print(f"  data chunk size: {chunk_size} bytes")
                    if fmt_tag == 3 and bits_per_sample == 32:
                        num_samples = chunk_size // 4
                        floats = struct.unpack(f"<{num_samples}f", chunk_data)
                        audio_samples_float = list(floats)
                    elif fmt_tag == 1 and bits_per_sample == 16:
                        num_samples = chunk_size // 2
                        shorts = struct.unpack(f"<{num_samples}h", chunk_data)
                        audio_samples_float = [s / 32768.0 for s in shorts]
                offset += 8 + chunk_size
                if chunk_size % 2 != 0:
                    offset += 1 # pad byte
            
            # Create standard deterministic PCM16 WAV
            pcm16_path = os.path.join("scratch/fleurs_audio_pcm16", dest_name)
            int16_samples = []
            for s in audio_samples_float:
                # Clamp to [-1.0, 1.0] and scale to int16
                clamped = max(-1.0, min(1.0, s))
                int16_val = int(round(clamped * 32767.0))
                int16_val = max(-32768, min(32767, int16_val))
                int16_samples.append(int16_val)
            
            pcm16_data = struct.pack(f"<{len(int16_samples)}h", *int16_samples)
            
            # Write 44-byte standard PCM16 WAV header
            num_channels = channels if channels else 1
            sr = sample_rate if sample_rate else 16000
            byte_rate = sr * num_channels * 2
            block_align = num_channels * 2
            data_size = len(pcm16_data)
            riff_size = 36 + data_size
            
            wav_header = struct.pack(
                "<4sI4s4sIHHIIHH4sI",
                b'RIFF', riff_size, b'WAVE',
                b'fmt ', 16, 1, num_channels, sr, byte_rate, block_align, 16,
                b'data', data_size
            )
            
            final_wav_bytes = wav_header + pcm16_data
            with open(pcm16_path, "wb") as out_f:
                out_f.write(final_wav_bytes)
            
            pcm16_sha256 = hashlib.sha256(final_wav_bytes).hexdigest()
            print(f"  PCM16 converted file: {pcm16_path}")
            print(f"  PCM16 size: {len(final_wav_bytes)} bytes ({len(int16_samples)} samples, {len(int16_samples)/sr:.3f} s)")
            print(f"  PCM16 SHA256: {pcm16_sha256}")
            
            # If HI-001, compare with app/src/main/assets/speech_test_hi_001.wav
            if case_id == "HI-001":
                with open("app/src/main/assets/speech_test_hi_001.wav", "rb") as existing_f:
                    existing_bytes = existing_f.read()
                    existing_sha = hashlib.sha256(existing_bytes).hexdigest()
                    print(f"  HI-001 existing asset SHA256: {existing_sha}")
                    print(f"  HI-001 Match: {existing_sha == pcm16_sha256}")
            
            found_count += 1
            if found_count == len(target_files):
                print("\nAll target files processed successfully!")
                break
