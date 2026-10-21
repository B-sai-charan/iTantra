import wave
import struct
import math
import zlib
import os
import json

def load_wav(path):
    with wave.open(path, 'rb') as wf:
        n_frames = wf.getnframes()
        fs = wf.getframerate()
        ch = wf.getnchannels()
        raw = wf.readframes(n_frames)
        samples = list(struct.unpack(f"<{n_frames * ch}h", raw))
        return samples, fs, ch

def compute_basic_signal(samples, fs):
    n = len(samples)
    if n == 0:
        return {}
    sum_s = sum(float(s) for s in samples)
    mean = sum_s / n
    sum_sq = sum(float(s) * float(s) for s in samples)
    variance = (sum_sq / n) - (mean * mean)
    std_dev = math.sqrt(max(0.0, variance))
    rms = math.sqrt(sum_sq / n)
    rms_dbfs = 20.0 * math.log10(rms / 32768.0) if rms > 0 else -96.0
    max_abs = max(abs(s) for s in samples)
    peak_dbfs = 20.0 * math.log10(max_abs / 32768.0) if max_abs > 0 else -96.0
    crest_factor = (max_abs / rms) if rms > 0 else 0.0
    clips = sum(1 for s in samples if abs(s) >= 32767)
    zeros = sum(1 for s in samples if s == 0)
    zero_pct = (zeros / float(n)) * 100.0
    
    return {
        "sample_rate": fs,
        "channels": 1,
        "sample_count": n,
        "duration_s": n / float(fs),
        "rms": rms,
        "rms_dbfs": rms_dbfs,
        "peak_abs": max_abs,
        "peak_dbfs": peak_dbfs,
        "clipping_count": clips,
        "zero_pct": zero_pct,
        "mean": mean,
        "std_dev": std_dev,
        "crest_factor": crest_factor
    }

def fft_mag_spectrum(frame):
    n = len(frame) # 512
    # Apply Hanning window
    w_frame = [frame[i] * 0.5 * (1.0 - math.cos(2.0 * math.pi * i / (n - 1))) for i in range(n)]
    
    # Radix-2 FFT
    real = [float(x) / 32768.0 for x in w_frame]
    imag = [0.0] * n
    
    j = 0
    for i in range(n - 1):
        if i < j:
            real[i], real[j] = real[j], real[i]
            imag[i], imag[j] = imag[j], imag[i]
        k = n >> 1
        while k <= j:
            j -= k
            k >>= 1
        j += k
        
    length = 2
    while length <= n:
        half = length >> 1
        angle = -2.0 * math.pi / length
        wstep_r = math.cos(angle)
        wstep_i = math.sin(angle)
        i = 0
        while i < n:
            wr = 1.0
            wi = 0.0
            for k in range(half):
                ur = real[i + k]
                ui = imag[i + k]
                vr = real[i + k + half] * wr - imag[i + k + half] * wi
                vi = real[i + k + half] * wi + imag[i + k + half] * wr
                real[i + k] = ur + vr
                imag[i + k] = ui + vi
                real[i + k + half] = ur - vr
                imag[i + k + half] = ui - vi
                next_wr = wr * wstep_r - wi * wstep_i
                wi = wr * wstep_i + wi * wstep_r
                wr = next_wr
            i += length
        length <<= 1
        
    mag = [math.sqrt(real[k]*real[k] + imag[k]*imag[k]) for k in range(n // 2)]
    return mag

def compute_spectral_features(samples, fs=16000, frame_size=512, hop_size=256):
    num_frames = (len(samples) - frame_size) // hop_size + 1
    if num_frames <= 0:
        return {}
    
    centroids = []
    bandwidths = []
    rolloffs = []
    flatnesses = []
    zcrs = []
    
    num_bins = frame_size // 2
    bin_width_hz = (fs / 2.0) / float(num_bins)
    
    for i in range(num_frames):
        frame = samples[i * hop_size : i * hop_size + frame_size]
        
        # Zero Crossing Rate
        zero_cross = 0
        for j in range(1, frame_size):
            if (frame[j] >= 0 and frame[j-1] < 0) or (frame[j] < 0 and frame[j-1] >= 0):
                zero_cross += 1
        zcr = zero_cross / float(frame_size - 1)
        zcrs.append(zcr)
        
        # Spectrum
        mag = fft_mag_spectrum(frame)
        total_mag = sum(mag)
        
        if total_mag > 1e-12:
            # Centroid
            centroid = sum(k * bin_width_hz * mag[k] for k in range(num_bins)) / total_mag
            centroids.append(centroid)
            
            # Bandwidth
            spread = sum(((k * bin_width_hz - centroid) ** 2) * mag[k] for k in range(num_bins)) / total_mag
            bandwidths.append(math.sqrt(max(0.0, spread)))
            
            # Rolloff 85%
            cum = 0.0
            ro_thresh = total_mag * 0.85
            rolloff = (num_bins - 1) * bin_width_hz
            for k in range(num_bins):
                cum += mag[k]
                if cum >= ro_thresh:
                    rolloff = k * bin_width_hz
                    break
            rolloffs.append(rolloff)
            
            # Spectral Flatness
            eps = 1e-10
            log_sum = sum(math.log(max(eps, mag[k])) for k in range(num_bins))
            geo_mean = math.exp(log_sum / num_bins)
            arith_mean = total_mag / num_bins
            flatness = (geo_mean / arith_mean) if arith_mean > 0 else 0.0
            flatnesses.append(flatness)
        else:
            centroids.append(0.0)
            bandwidths.append(0.0)
            rolloffs.append(0.0)
            flatnesses.append(0.0)
            
    return {
        "spectral_centroid_hz": sum(centroids) / len(centroids),
        "spectral_bandwidth_hz": sum(bandwidths) / len(bandwidths),
        "spectral_rolloff_hz": sum(rolloffs) / len(rolloffs),
        "spectral_flatness": sum(flatnesses) / len(flatnesses),
        "zero_crossing_rate": sum(zcrs) / len(zcrs)
    }

def compute_frequency_bands(samples, fs=16000, frame_size=512, hop_size=256):
    bands = [
        ("0–250 Hz", 0.0, 250.0),
        ("250–500 Hz", 250.0, 500.0),
        ("500–1000 Hz", 500.0, 1000.0),
        ("1000–2000 Hz", 1000.0, 2000.0),
        ("2000–4000 Hz", 2000.0, 4000.0),
        ("4000–8000 Hz", 4000.0, 8000.0)
    ]
    num_frames = (len(samples) - frame_size) // hop_size + 1
    band_energies = [0.0] * len(bands)
    total_energy = 0.0
    num_bins = frame_size // 2
    bin_width_hz = (fs / 2.0) / float(num_bins)
    
    for i in range(num_frames):
        frame = samples[i * hop_size : i * hop_size + frame_size]
        mag = fft_mag_spectrum(frame)
        for k in range(num_bins):
            f = k * bin_width_hz
            e = mag[k] * mag[k]
            total_energy += e
            for b, (_, low, high) in enumerate(bands):
                if low <= f < high:
                    band_energies[b] += e
                    break
                    
    results = {}
    for b, (name, low, high) in enumerate(bands):
        pct = (band_energies[b] / total_energy * 100.0) if total_energy > 0 else 0.0
        results[name] = {
            "low_hz": low, "high_hz": high, "abs_energy": band_energies[b], "percentage": pct
        }
    return results

def detect_speech_region(samples, fs=16000, frame_size=512, threshold_dbfs=-42.0):
    num_frames = len(samples) // frame_size
    speech_frames = []
    for i in range(num_frames):
        f = samples[i * frame_size : (i + 1) * frame_size]
        rms = math.sqrt(sum(float(x)*float(x) for x in f) / frame_size)
        db = 20.0 * math.log10(rms / 32768.0) if rms > 0 else -96.0
        if db > threshold_dbfs:
            speech_frames.append(i)
            
    if speech_frames:
        start_frame = max(0, speech_frames[0] - 2)
        end_frame = min(num_frames, speech_frames[-1] + 3)
        start_sample = start_frame * frame_size
        end_sample = end_frame * frame_size
        start_ms = (start_sample * 1000) // fs
        end_ms = (end_sample * 1000) // fs
    else:
        start_sample = 0
        end_sample = len(samples)
        start_ms = 0
        end_ms = (len(samples) * 1000) // fs
        
    return {
        "start_sample": start_sample,
        "end_sample": end_sample,
        "start_ms": start_ms,
        "end_ms": end_ms,
        "duration_ms": end_ms - start_ms
    }

def hz_to_mel(hz):
    return 2595.0 * math.log10(1.0 + hz / 700.0)

def mel_to_hz(mel):
    return 700.0 * (10.0 ** (mel / 2595.0) - 1.0)

def build_mel_filterbank(n_mels=80, num_bins=256, fs=16000):
    min_mel = hz_to_mel(0)
    max_mel = hz_to_mel(fs / 2.0)
    mel_points = [min_mel + (max_mel - min_mel) * i / (n_mels + 1) for i in range(n_mels + 2)]
    hz_points = [mel_to_hz(m) for m in mel_points]
    bin_points = [int(math.floor((num_bins * 2) * h / fs)) for h in hz_points]
    
    weights = [[0.0] * num_bins for _ in range(n_mels)]
    for m in range(1, n_mels + 1):
        f_m_minus = bin_points[m - 1]
        f_m = bin_points[m]
        f_m_plus = bin_points[m + 1]
        for k in range(f_m_minus, f_m):
            if f_m > f_m_minus and k < num_bins:
                weights[m - 1][k] = (k - f_m_minus) / float(f_m - f_m_minus)
        for k in range(f_m, f_m_plus):
            if f_m_plus > f_m and k < num_bins:
                weights[m - 1][k] = (f_m_plus - k) / float(f_m_plus - f_m)
    return weights

def compute_whisper_log_mel(samples, n_mels=80, frame_size=512, hop_length=160, fs=16000):
    num_bins = frame_size // 2 # 256
    mel_fb = build_mel_filterbank(n_mels, num_bins, fs)
    num_frames = (len(samples) - frame_size) // hop_length + 1
    log_mels = []
    
    for i in range(num_frames):
        frame = samples[i * hop_length : i * hop_length + frame_size]
        mag = fft_mag_spectrum(frame) # length 256
        
        mel_energies = []
        for m in range(n_mels):
            m_e = sum(mel_fb[m][k] * (mag[k] * mag[k]) for k in range(num_bins))
            log_m_e = math.log10(max(1e-10, m_e))
            mel_energies.append(log_m_e)
        log_mels.append(mel_energies)
        
    return log_mels

# Pure Python PNG Writer
def save_png(path, matrix, width, height):
    all_vals = [val for row in matrix for val in row]
    min_v = min(all_vals)
    max_v = max(all_vals)
    v_range = max(1e-6, max_v - min_v)
    
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0) # Filter byte: None
        for x in range(width):
            val = matrix[y][x]
            norm_val = int(255 * (val - min_v) / v_range)
            # Colormap
            r = int(min(255, max(0, 255 * math.sin(math.pi * norm_val / 255.0))))
            g = int(min(255, max(0, 255 * (norm_val / 255.0) ** 0.7)))
            b = int(min(255, max(0, 255 * math.cos(math.pi * norm_val / 510.0))))
            raw_data.extend([r, g, b])
            
    compressed = zlib.compress(bytes(raw_data))
    
    def make_chunk(chunk_type, data):
        c_type = chunk_type.encode('ascii')
        crc = zlib.crc32(c_type + data)
        return struct.pack('>I', len(data)) + c_type + data + struct.pack('>I', crc)
        
    png_bytes = bytearray(b'\x89PNG\r\n\x1a\n')
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)
    png_bytes.extend(make_chunk('IHDR', ihdr))
    png_bytes.extend(make_chunk('IDAT', compressed))
    png_bytes.extend(make_chunk('IEND', b''))
    
    with open(path, 'wb') as f:
        f.write(png_bytes)

def compare_spectrograms(mel1, mel2):
    t1 = len(mel1)
    t2 = len(mel2)
    
    resampled_mel2 = []
    for i in range(t1):
        idx2 = min(t2 - 1, int(i * t2 / float(t1)))
        resampled_mel2.append(mel2[idx2])
        
    vec1 = [val for row in mel1 for val in row]
    vec2 = [val for row in resampled_mel2 for val in row]
    
    dot = sum(a * b for a, b in zip(vec1, vec2))
    norm1 = math.sqrt(sum(a * a for a in vec1))
    norm2 = math.sqrt(sum(b * b for b in vec2))
    cos_sim = dot / (norm1 * norm2) if norm1 > 0 and norm2 > 0 else 0.0
    cos_dist = 1.0 - cos_sim
    
    mad = sum(abs(a - b) for a, b in zip(vec1, vec2)) / float(len(vec1))
    
    mean1 = sum(vec1) / len(vec1)
    mean2 = sum(vec2) / len(vec2)
    num = sum((a - mean1) * (b - mean2) for a, b in zip(vec1, vec2))
    den = math.sqrt(sum((a - mean1)**2 for a in vec1) * sum((b - mean2)**2 for b in vec2))
    corr = num / den if den > 0 else 0.0
    
    return {
        "cosine_distance": cos_dist,
        "mean_absolute_diff": mad,
        "correlation": corr
    }

def main():
    paths = {
        "reference": "app/src/main/assets/speech_test.wav",
        "live_mic": "scratch/same_sentence_reference_vs_mic.wav",
        "level_matched": "scratch/same_sentence_level_matched.wav"
    }
    
    output = {}
    
    for key, path in paths.items():
        print(f"Analyzing {key} from {path}...")
        samples, fs, ch = load_wav(path)
        
        # 1. Basic Signal
        sig = compute_basic_signal(samples, fs)
        
        # 2. VAD Detection
        vad = detect_speech_region(samples, fs)
        speech_samples = samples[vad["start_sample"] : vad["end_sample"]]
        
        # 3. Spectral Features (Full & Speech)
        spec_full = compute_spectral_features(samples, fs)
        spec_speech = compute_spectral_features(speech_samples, fs)
        
        # 4. Frequency Bands
        bands = compute_frequency_bands(speech_samples, fs)
        
        # 5. Whisper Log-Mel Spectrogram
        log_mel = compute_whisper_log_mel(speech_samples)
        
        # Flatten Log-Mel for visualization (Transpose: 80 rows x T columns)
        t_len = len(log_mel)
        mel_img = [[log_mel[t][m] for t in range(t_len)] for m in range(80)]
        mel_img.reverse()
        
        png_path = f"scratch/spectral_{key}.png"
        save_png(png_path, mel_img, t_len, 80)
        print(f"Saved visualization to {png_path} ({t_len}x80)")
        
        # Log-Mel stats
        flat_mel = [val for row in log_mel for val in row]
        mel_mean = sum(flat_mel) / len(flat_mel)
        mel_std = math.sqrt(sum((x - mel_mean)**2 for x in flat_mel) / len(flat_mel))
        
        output[key] = {
            "path": path,
            "signal": sig,
            "vad": vad,
            "spectral_full": spec_full,
            "spectral_speech": spec_speech,
            "bands_speech": bands,
            "whisper_log_mel_stats": {
                "frames": t_len,
                "n_mels": 80,
                "mean": mel_mean,
                "std_dev": mel_std,
                "min": min(flat_mel),
                "max": max(flat_mel)
            },
            "_raw_mel": log_mel
        }
        
    comp_ref_live = compare_spectrograms(output["reference"]["_raw_mel"], output["live_mic"]["_raw_mel"])
    comp_ref_lvl = compare_spectrograms(output["reference"]["_raw_mel"], output["level_matched"]["_raw_mel"])
    comp_live_lvl = compare_spectrograms(output["live_mic"]["_raw_mel"], output["level_matched"]["_raw_mel"])
    
    for k in output:
        del output[k]["_raw_mel"]
        
    comparison_summary = {
        "ref_vs_live_mic": comp_ref_live,
        "ref_vs_level_matched": comp_ref_lvl,
        "live_mic_vs_level_matched": comp_live_lvl
    }
    
    final_data = {
        "profiles": output,
        "spectrogram_comparisons": comparison_summary
    }
    
    with open("scratch/spectral_comparison_09_data.json", "w") as f:
        json.dump(final_data, f, indent=2)
        
    print("\nSpectral analysis complete. Saved data to scratch/spectral_comparison_09_data.json")

if __name__ == "__main__":
    main()
