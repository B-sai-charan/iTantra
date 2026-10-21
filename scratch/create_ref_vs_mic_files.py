import wave
import struct
import math
import hashlib
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

def sha256_file(path):
    with open(path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()

def compute_metrics(samples):
    n = len(samples)
    max_abs = max(abs(s) for s in samples)
    peak_dbfs = 20 * math.log10(max_abs / 32768.0) if max_abs > 0 else -96.0
    sum_sq = sum(float(s)*float(s) for s in samples)
    rms = math.sqrt(sum_sq / n)
    rms_dbfs = 20 * math.log10(rms / 32768.0) if rms > 0 else -96.0
    clips = sum(1 for s in samples if abs(s) >= 32767)
    zeros = sum(1 for s in samples if s == 0)
    return {
        'sample_count': n,
        'duration_s': n / 16000.0,
        'rms': rms,
        'rms_dbfs': rms_dbfs,
        'peak_abs': max_abs,
        'peak_dbfs': peak_dbfs,
        'clipping_count': clips,
        'zero_pct': (zeros / float(n)) * 100.0
    }

def main():
    target_samples = 128000
    
    # 1. Reference Audio
    ref_samples = load_wav("app/src/main/assets/speech_test.wav")
    ref_metrics = compute_metrics(ref_samples)
    ref_sha = sha256_file("app/src/main/assets/speech_test.wav")
    print(f"Reference Audio: {len(ref_samples)} samples, RMS={ref_metrics['rms_dbfs']:.2f} dBFS, Peak={ref_metrics['peak_dbfs']:.2f} dBFS, SHA={ref_sha}")
    
    # 2. Live Mic Audio (8.000s exact, 128,000 samples)
    raw_samples = load_wav("same_sentence_mic_test.wav")
    if len(raw_samples) < target_samples:
        mic_8s = raw_samples + [0] * (target_samples - len(raw_samples))
    else:
        mic_8s = raw_samples[:target_samples]
        
    mic_path = "scratch/same_sentence_reference_vs_mic.wav"
    save_wav(mic_path, mic_8s)
    mic_metrics = compute_metrics(mic_8s)
    mic_sha = sha256_file(mic_path)
    print(f"Live Mic Audio: {len(mic_8s)} samples, RMS={mic_metrics['rms_dbfs']:.2f} dBFS, Peak={mic_metrics['peak_dbfs']:.2f} dBFS, SHA={mic_sha}")
    
    # 3. Level-Matched Variant
    # Match Live Mic RMS to Reference RMS (1543.0855 / 463.8388 = 3.32677)
    scale = ref_metrics['rms'] / mic_metrics['rms']
    print(f"Scale Factor: {scale:.5f} (+{20 * math.log10(scale):.2f} dB)")
    
    predicted_peak = mic_metrics['peak_abs'] * scale
    print(f"Predicted Peak: {predicted_peak:.1f} / 32767 (Will clip: {predicted_peak >= 32767})")
    
    lvl_samples = [s * scale for s in mic_8s]
    lvl_path = "scratch/same_sentence_level_matched.wav"
    save_wav(lvl_path, lvl_samples)
    lvl_metrics = compute_metrics(lvl_samples)
    lvl_sha = sha256_file(lvl_path)
    print(f"Level-Matched Audio: {len(lvl_samples)} samples, RMS={lvl_metrics['rms_dbfs']:.2f} dBFS, Peak={lvl_metrics['peak_dbfs']:.2f} dBFS, Clips={lvl_metrics['clipping_count']}, SHA={lvl_sha}")

if __name__ == "__main__":
    main()
