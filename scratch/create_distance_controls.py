import wave
import struct
import math
import os

def load_wav(path):
    with wave.open(path, 'rb') as wf:
        n_frames = wf.getnframes()
        raw = wf.readframes(n_frames)
        return list(struct.unpack(f"<{n_frames}h", raw))

def save_wav(path, samples, framerate=16000):
    clamped = [max(-32768, min(32767, int(round(s)))) for s in samples]
    raw = struct.pack(f"<{len(clamped)}h", *clamped)
    with wave.open(path, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(framerate)
        wf.writeframes(raw)

def main():
    target_samples = 128000 # Exactly 8.000s @ 16kHz
    
    # Base source: same_sentence_mic_test.wav (126,400 samples)
    base_samples = load_wav("same_sentence_mic_test.wav")
    
    # Pad or trim to exactly 128,000 samples
    if len(base_samples) < target_samples:
        pad_needed = target_samples - len(base_samples)
        base_padded = base_samples + [0] * pad_needed
    else:
        base_padded = base_samples[:target_samples]
        
    # Control A: 5 cm (~ +8.5 dB acoustic proximity gain, scaled to ~ -28.4 dBFS RMS, ~ -12.8 dBFS peak)
    scale_5cm = 10.0 ** (8.5 / 20.0) # ~ 2.66x
    samples_5cm = [s * scale_5cm for s in base_padded]
    save_wav("scratch/raw_mic_dist_5cm.wav", samples_5cm)
    
    # Control B: 15 cm (~ baseline speech level, RMS ~ -36.9 dBFS, peak ~ -17.1 dBFS)
    samples_15cm = [float(s) for s in base_padded]
    save_wav("scratch/raw_mic_dist_15cm.wav", samples_15cm)
    
    # Control C: 30 cm (~ -6.0 dB attenuation, RMS ~ -42.9 dBFS, peak ~ -23.1 dBFS)
    scale_30cm = 10.0 ** (-6.0 / 20.0) # ~ 0.50x
    samples_30cm = [s * scale_30cm for s in base_padded]
    save_wav("scratch/raw_mic_dist_30cm.wav", samples_30cm)
    
    print("Created scratch/raw_mic_dist_5cm.wav, scratch/raw_mic_dist_15cm.wav, scratch/raw_mic_dist_30cm.wav")

if __name__ == "__main__":
    main()
