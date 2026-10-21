import wave
import struct
import math
import os

def load_wav(file_path):
    with wave.open(file_path, 'rb') as wf:
        n_channels = wf.getnchannels()
        sampwidth = wf.getsampwidth()
        framerate = wf.getframerate()
        n_frames = wf.getnframes()
        raw_bytes = wf.readframes(n_frames)
        samples = list(struct.unpack(f"<{n_frames * n_channels}h", raw_bytes))
        return samples, framerate, n_channels

def save_wav(file_path, samples, framerate=16000):
    clamped = [max(-32768, min(32767, int(round(s)))) for s in samples]
    raw_bytes = struct.pack(f"<{len(clamped)}h", *clamped)
    with wave.open(file_path, 'wb') as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(framerate)
        wf.writeframes(raw_bytes)

def compute_stats(samples):
    max_amp = max(abs(s) for s in samples) if samples else 0
    peak_dbfs = 20 * math.log10(max_amp / 32768.0) if max_amp > 0 else -96.0
    sum_sq = sum(float(s) * float(s) for s in samples)
    mean_sq = sum_sq / len(samples) if samples else 0.0
    rms = math.sqrt(mean_sq)
    rms_dbfs = 20 * math.log10(rms / 32768.0) if rms > 0 else -96.0
    clips = sum(1 for s in samples if abs(s) >= 32767)
    return {
        "sample_count": len(samples),
        "duration_sec": len(samples) / 16000.0,
        "peak_abs": int(max_amp),
        "peak_dbfs": peak_dbfs,
        "rms_dbfs": rms_dbfs,
        "clipping_count": int(clips)
    }

class BiquadFilter:
    def __init__(self, b0, b1, b2, a0, a1, a2):
        self.b0 = b0 / a0
        self.b1 = b1 / a0
        self.b2 = b2 / a0
        self.a1 = a1 / a0
        self.a2 = a2 / a0
        self.x1 = 0.0
        self.x2 = 0.0
        self.y1 = 0.0
        self.y2 = 0.0

    def process_sample(self, x):
        y = self.b0 * x + self.b1 * self.x1 + self.b2 * self.x2 - self.a1 * self.y1 - self.a2 * self.y2
        self.x2 = self.x1
        self.x1 = x
        self.y2 = self.y1
        self.y1 = y
        return y

    def process(self, samples):
        out = []
        for s in samples:
            out.append(self.process_sample(float(s)))
        return out

def make_highpass_150hz(fs=16000.0, fc=150.0):
    # 2nd-order Butterworth High-Pass via Bilinear Transform
    # Q = 1/sqrt(2) = 0.70710678
    w0 = 2.0 * math.pi * fc / fs
    cos_w0 = math.cos(w0)
    sin_w0 = math.sin(w0)
    alpha = sin_w0 / (2.0 * 0.70710678)

    b0 = (1.0 + cos_w0) / 2.0
    b1 = -(1.0 + cos_w0)
    b2 = (1.0 + cos_w0) / 2.0
    a0 = 1.0 + alpha
    a1 = -2.0 * cos_w0
    a2 = 1.0 - alpha
    return BiquadFilter(b0, b1, b2, a0, a1, a2)

def make_peaking_eq(fs=16000.0, f0=800.0, gain_db=6.0, Q=1.0):
    # Audio EQ Cookbook Peaking Filter
    A = 10.0 ** (gain_db / 40.0)
    w0 = 2.0 * math.pi * f0 / fs
    cos_w0 = math.cos(w0)
    sin_w0 = math.sin(w0)
    alpha = sin_w0 / (2.0 * Q)

    b0 = 1.0 + alpha * A
    b1 = -2.0 * cos_w0
    b2 = 1.0 - alpha * A
    a0 = 1.0 + alpha / A
    a1 = -2.0 * cos_w0
    a2 = 1.0 - alpha / A
    return BiquadFilter(b0, b1, b2, a0, a1, a2)

def main():
    scratch_dir = "scratch"
    src1_path = "same_sentence_mic_test.wav"
    src2_path = "mic_loopback_ref_test.wav"

    results = []

    for label, src_path, prefix in [
        ("Source 1 (same_sentence_mic_test)", src1_path, "same_sentence"),
        ("Source 2 (mic_loopback_ref_test)", src2_path, "mic_loopback")
    ]:
        print(f"=== Processing {label} from {src_path} ===")
        samples, fs, ch = load_wav(src_path)
        print(f"Loaded {len(samples)} samples, {fs}Hz, {ch} channel(s)")

        # Variant A: Original
        out_a = os.path.join(scratch_dir, f"{prefix}_var_a_orig.wav")
        save_wav(out_a, samples, fs)
        stats_a = compute_stats(samples)
        print(f"  Variant A (Orig): RMS={stats_a['rms_dbfs']:.2f}dBFS, Peak={stats_a['peak_dbfs']:.2f}dBFS, Clips={stats_a['clipping_count']}")

        # Variant B: 150 Hz 2nd-order High-Pass
        hp_filter = make_highpass_150hz(fs=fs, fc=150.0)
        samples_b = hp_filter.process(samples)
        out_b = os.path.join(scratch_dir, f"{prefix}_var_b_hp150.wav")
        save_wav(out_b, samples_b, fs)
        stats_b = compute_stats(samples_b)
        print(f"  Variant B (HP 150Hz): RMS={stats_b['rms_dbfs']:.2f}dBFS, Peak={stats_b['peak_dbfs']:.2f}dBFS, Clips={stats_b['clipping_count']}")

        # Variant C: 150 Hz HP + 800 Hz Formant Boost (+6dB, Q=1.0)
        boost_filter = make_peaking_eq(fs=fs, f0=800.0, gain_db=6.0, Q=1.0)
        samples_c = boost_filter.process(samples_b)
        out_c = os.path.join(scratch_dir, f"{prefix}_var_c_hp150_boost800.wav")
        save_wav(out_c, samples_c, fs)
        stats_c = compute_stats(samples_c)
        print(f"  Variant C (HP 150Hz + Boost 800Hz): RMS={stats_c['rms_dbfs']:.2f}dBFS, Peak={stats_c['peak_dbfs']:.2f}dBFS, Clips={stats_c['clipping_count']}")

        results.append((prefix, "A", stats_a, out_a))
        results.append((prefix, "B", stats_b, out_b))
        results.append((prefix, "C", stats_c, out_c))

    print("\nSummary of Generated Files:")
    for prefix, var, st, path in results:
        print(f"{path} -> RMS: {st['rms_dbfs']:.2f} dBFS | Peak: {st['peak_dbfs']:.2f} dBFS | Clips: {st['clipping_count']}")

if __name__ == "__main__":
    main()
