import wave
import struct
import math
import hashlib
import os

def sha256_file(filepath):
    h = hashlib.sha256()
    with open(filepath, 'rb') as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def read_wav_pcm16(filepath):
    with wave.open(filepath, 'rb') as wf:
        nchannels = wf.getnchannels()
        sampwidth = wf.getsampwidth()
        framerate = wf.getframerate()
        nframes = wf.getnframes()
        raw_bytes = wf.readframes(nframes)
    assert nchannels == 1, f"Expected mono, got {nchannels}"
    assert sampwidth == 2, f"Expected 16-bit, got {sampwidth}"
    assert framerate == 16000, f"Expected 16kHz, got {framerate}"
    samples = list(struct.unpack(f"<{nframes}h", raw_bytes))
    return samples, framerate

def write_wav_pcm16(filepath, samples, framerate=16000):
    nframes = len(samples)
    clamped = []
    clip_count = 0
    for s in samples:
        val = int(round(s))
        if val > 32767:
            val = 32767
            clip_count += 1
        elif val < -32768:
            val = -32768
            clip_count += 1
        clamped.append(val)
    raw_bytes = struct.pack(f"<{nframes}h", *clamped)
    with wave.open(filepath, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(framerate)
        wf.writeframes(raw_bytes)
    return clip_count

def calc_signal_metrics(samples):
    n = len(samples)
    if n == 0:
        return 0, -100, -100, 0
    sum_sq = sum(s * s for s in samples)
    rms = math.sqrt(sum_sq / n)
    peak = max(abs(s) for s in samples)
    rms_dbfs = 20 * math.log10(rms / 32768.0) if rms > 0 else -100.0
    peak_dbfs = 20 * math.log10(peak / 32768.0) if peak > 0 else -100.0
    return rms, peak, rms_dbfs, peak_dbfs

class BiquadFilter:
    def __init__(self, b0, b1, b2, a0, a1, a2):
        # Normalize by a0
        self.b0 = b0 / a0
        self.b1 = b1 / a0
        self.b2 = b2 / a0
        self.a1 = a1 / a0
        self.a2 = a2 / a0
        self.x1 = 0.0
        self.x2 = 0.0
        self.y1 = 0.0
        self.y2 = 0.0

    def process(self, x):
        y = self.b0 * x + self.b1 * self.x1 + self.b2 * self.x2 - self.a1 * self.y1 - self.a2 * self.y2
        self.x2 = self.x1
        self.x1 = x
        self.y2 = self.y1
        self.y1 = y
        return y

def make_low_shelf(fc, gain_db, fs=16000.0, Q=0.7071):
    A = 10.0 ** (gain_db / 40.0)
    w0 = 2.0 * math.pi * fc / fs
    cos_w0 = math.cos(w0)
    sin_w0 = math.sin(w0)
    alpha = sin_w0 / (2.0 * Q)
    two_sqrt_A_alpha = 2.0 * math.sqrt(A) * alpha

    b0 =    A * ( (A + 1.0) - (A - 1.0) * cos_w0 + two_sqrt_A_alpha )
    b1 =  2.0 * A * ( (A - 1.0) - (A + 1.0) * cos_w0 )
    b2 =    A * ( (A + 1.0) - (A - 1.0) * cos_w0 - two_sqrt_A_alpha )
    a0 =          (A + 1.0) + (A - 1.0) * cos_w0 + two_sqrt_A_alpha
    a1 = -2.0 * ( (A - 1.0) + (A + 1.0) * cos_w0 )
    a2 =          (A + 1.0) + (A - 1.0) * cos_w0 - two_sqrt_A_alpha

    return BiquadFilter(b0, b1, b2, a0, a1, a2)

def make_peaking(fc, gain_db, fs=16000.0, Q=1.0):
    A = 10.0 ** (gain_db / 40.0)
    w0 = 2.0 * math.pi * fc / fs
    cos_w0 = math.cos(w0)
    sin_w0 = math.sin(w0)
    alpha = sin_w0 / (2.0 * Q)

    b0 =   1.0 + alpha * A
    b1 =  -2.0 * cos_w0
    b2 =   1.0 - alpha * A
    a0 =   1.0 + alpha / A
    a1 =  -2.0 * cos_w0
    a2 =   1.0 - alpha / A

    return BiquadFilter(b0, b1, b2, a0, a1, a2)

def filter_samples(samples, filters):
    out = []
    for s in samples:
        v = float(s)
        for f in filters:
            v = f.process(v)
        out.append(v)
    return out

def main():
    src_path = "scratch/same_sentence_reference_vs_mic.wav"
    samples, fs = read_wav_pcm16(src_path)
    print(f"Loaded {len(samples)} samples from {src_path} (fs={fs})")

    variants = [
        ("A", "scratch/spectral_ab_original.wav", []),
        ("B", "scratch/spectral_ab_low3.wav", [make_low_shelf(300.0, 3.0)]),
        ("C", "scratch/spectral_ab_low6.wav", [make_low_shelf(300.0, 6.0)]),
        ("D", "scratch/spectral_ab_low9.wav", [make_low_shelf(300.0, 9.0)]),
        ("E", "scratch/spectral_ab_low12.wav", [make_low_shelf(300.0, 12.0)]),
        ("F", "scratch/spectral_ab_low6_midminus3.wav", [make_low_shelf(300.0, 6.0), make_peaking(750.0, -3.0)]),
        ("G", "scratch/spectral_ab_low9_midminus3.wav", [make_low_shelf(300.0, 9.0), make_peaking(750.0, -3.0)]),
    ]

    results = []
    for code, out_path, flist in variants:
        if not flist:
            filtered = [float(s) for s in samples]
        else:
            filtered = filter_samples(samples, flist)

        # Check peak before saving
        max_val = max(abs(v) for v in filtered)
        attenuation_db = 0.0
        if max_val > 32767.0:
            scale = 32767.0 / max_val
            attenuation_db = 20 * math.log10(scale)
            print(f"Variant {code} ({out_path}) would clip (peak={max_val:.1f}). Applying attenuation {attenuation_db:.2f} dB")
            filtered = [v * scale for v in filtered]

        clip_count = write_wav_pcm16(out_path, filtered, fs)
        saved_samples, _ = read_wav_pcm16(out_path)
        rms, peak, rms_dbfs, peak_dbfs = calc_signal_metrics(saved_samples)
        file_sha = sha256_file(out_path)
        duration = len(saved_samples) / fs

        results.append({
            "code": code,
            "path": out_path,
            "duration": duration,
            "samples": len(saved_samples),
            "rms_dbfs": rms_dbfs,
            "peak_dbfs": peak_dbfs,
            "peak_raw": peak,
            "clips": clip_count,
            "sha256": file_sha,
            "attenuation_db": attenuation_db
        })

    print("\n--- Summary of Generated Variants ---")
    for r in results:
        print(f"Variant {r['code']} | {r['path']} | Dur: {r['duration']:.3f}s | RMS: {r['rms_dbfs']:.2f} dBFS | Peak: {r['peak_dbfs']:.2f} dBFS ({r['peak_raw']}) | Clips: {r['clips']} | Atten: {r['attenuation_db']:.2f} dB | SHA: {r['sha256']}")

if __name__ == "__main__":
    main()
