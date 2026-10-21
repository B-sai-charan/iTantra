import wave, array, math, json

def fft(x):
    N = len(x)
    if N <= 1:
        return x
    even = fft(x[0::2])
    odd = fft(x[1::2])
    T = [math.e ** (-2j * math.pi * k / N) * odd[k] for k in range(N // 2)]
    return [even[k] + T[k] for k in range(N // 2)] + [even[k] - T[k] for k in range(N // 2)]

def analyze_audio(path):
    f = wave.open(path, 'rb')
    n_channels = f.getnchannels()
    sampwidth = f.getsampwidth()
    framerate = f.getframerate()
    n_frames = f.getnframes()
    raw = f.readframes(n_frames)
    samples = array.array('h', raw)
    n = len(samples)
    
    # 1. Basic Stats
    min_s = min(samples)
    max_s = max(samples)
    peak = max(abs(s) for s in samples)
    sum_s = sum(samples)
    mean_s = sum_s / n
    sum_sq = sum(s*s for s in samples)
    rms = math.sqrt(sum_sq / n)
    dbfs = 20 * math.log10(rms / 32768.0) if rms > 0 else -96.0
    zeros = sum(1 for s in samples if s == 0)
    clip_neg = sum(1 for s in samples if s == -32768)
    clip_pos = sum(1 for s in samples if s == 32767)
    
    # 2. Time-Domain 100ms Windows (1600 samples)
    win_size = int(framerate * 0.1) # 1600 samples
    n_windows = n // win_size
    win_rms_list = []
    win_zcr_list = []
    
    for w in range(n_windows):
        win_samples = samples[w * win_size : (w + 1) * win_size]
        w_sum_sq = sum(s*s for s in win_samples)
        w_rms = math.sqrt(w_sum_sq / win_size)
        w_dbfs = 20 * math.log10(w_rms / 32768.0) if w_rms > 0 else -96.0
        win_rms_list.append((w_rms, w_dbfs))
        
        # ZCR in this window
        zc = 0
        for i in range(1, len(win_samples)):
            if (win_samples[i-1] >= 0 and win_samples[i] < 0) or (win_samples[i-1] < 0 and win_samples[i] >= 0):
                zc += 1
        zcr_rate = zc / (win_size - 1)
        win_zcr_list.append(zcr_rate)
        
    rms_vals = sorted([r[0] for r in win_rms_list])
    dbfs_vals = sorted([r[1] for r in win_rms_list])
    zcr_vals = sorted(win_zcr_list)
    
    def percentile(lst, p):
        idx = int(len(lst) * p)
        return lst[min(idx, len(lst)-1)]
        
    p10_rms = percentile(rms_vals, 0.10)
    p10_dbfs = percentile(dbfs_vals, 0.10)
    p50_rms = percentile(rms_vals, 0.50)
    p50_dbfs = percentile(dbfs_vals, 0.50)
    p90_rms = percentile(rms_vals, 0.90)
    p90_dbfs = percentile(dbfs_vals, 0.90)
    
    min_win_rms = min(rms_vals)
    max_win_rms = max(rms_vals)
    mean_win_rms = sum(rms_vals) / len(rms_vals)
    
    min_win_dbfs = min(dbfs_vals)
    max_win_dbfs = max(dbfs_vals)
    mean_win_dbfs = sum(dbfs_vals) / len(dbfs_vals)
    
    below_50 = sum(1 for r in win_rms_list if r[1] < -50.0) / len(win_rms_list) * 100.0
    below_45 = sum(1 for r in win_rms_list if r[1] < -45.0) / len(win_rms_list) * 100.0
    below_40 = sum(1 for r in win_rms_list if r[1] < -40.0) / len(win_rms_list) * 100.0
    below_35 = sum(1 for r in win_rms_list if r[1] < -35.0) / len(win_rms_list) * 100.0
    
    mean_zcr = sum(zcr_vals) / len(zcr_vals)
    median_zcr = percentile(zcr_vals, 0.50)
    min_zcr = min(zcr_vals)
    max_zcr = max(zcr_vals)
    
    # 4. Spectral analysis via FFT (Frame size 512, hop 256)
    fft_size = 512
    hop_size = 256
    n_frames_fft = (n - fft_size) // hop_size
    
    band_energies = [0.0, 0.0, 0.0, 0.0, 0.0] # 0-300, 300-1000, 1000-3000, 3000-6000, 6000-8000
    total_energy = 0.0
    
    centroids = []
    bandwidths = []
    rolloffs = []
    
    hann = [0.5 * (1 - math.cos(2 * math.pi * i / (fft_size - 1))) for i in range(fft_size)]
    freq_bins = [i * framerate / fft_size for i in range(fft_size // 2 + 1)]
    
    for f_idx in range(n_frames_fft):
        chunk = [complex(samples[f_idx * hop_size + i] * hann[i], 0.0) for i in range(fft_size)]
        spectrum = fft(chunk)
        half_spec = spectrum[:fft_size // 2 + 1]
        mags = [abs(c)**2 for c in half_spec]
            
        f_tot = sum(mags)
        if f_tot > 0:
            cent = sum(freq_bins[k] * mags[k] for k in range(len(mags))) / f_tot
            centroids.append(cent)
            
            bw = math.sqrt(sum(((freq_bins[k] - cent)**2) * mags[k] for k in range(len(mags))) / f_tot)
            bandwidths.append(bw)
            
            cum = 0.0
            ro = freq_bins[-1]
            for k in range(len(mags)):
                cum += mags[k]
                if cum >= 0.85 * f_tot:
                    ro = freq_bins[k]
                    break
            rolloffs.append(ro)
            
            for k in range(len(mags)):
                freq = freq_bins[k]
                e = mags[k]
                total_energy += e
                if freq < 300:
                    band_energies[0] += e
                elif freq < 1000:
                    band_energies[1] += e
                elif freq < 3000:
                    band_energies[2] += e
                elif freq < 6000:
                    band_energies[3] += e
                else:
                    band_energies[4] += e
                    
    band_pct = [100.0 * e / total_energy if total_energy > 0 else 0.0 for e in band_energies]
    mean_cent = sum(centroids) / len(centroids) if centroids else 0.0
    mean_bw = sum(bandwidths) / len(bandwidths) if bandwidths else 0.0
    mean_ro = sum(rolloffs) / len(rolloffs) if rolloffs else 0.0
    
    return {
        'channels': n_channels,
        'sampwidth': sampwidth,
        'framerate': framerate,
        'sample_count': n,
        'duration_s': n / framerate,
        'min_s': min_s,
        'max_s': max_s,
        'peak': peak,
        'mean_dc': mean_s,
        'rms': rms,
        'dbfs': dbfs,
        'zeros_pct': 100.0 * zeros / n,
        'clip_neg': clip_neg,
        'clip_pos': clip_pos,
        'min_win_rms': min_win_rms,
        'max_win_rms': max_win_rms,
        'mean_win_rms': mean_win_rms,
        'median_win_rms': p50_rms,
        'p10_win_rms': p10_rms,
        'p90_win_rms': p90_rms,
        'min_win_dbfs': min_win_dbfs,
        'max_win_dbfs': max_win_dbfs,
        'mean_win_dbfs': mean_win_dbfs,
        'median_win_dbfs': p50_dbfs,
        'p10_win_dbfs': p10_dbfs,
        'p90_win_dbfs': p90_dbfs,
        'below_50': below_50,
        'below_45': below_45,
        'below_40': below_40,
        'below_35': below_35,
        'mean_zcr': mean_zcr,
        'median_zcr': median_zcr,
        'min_zcr': min_zcr,
        'max_zcr': max_zcr,
        'band_pct': band_pct,
        'spectral_centroid': mean_cent,
        'spectral_bandwidth': mean_bw,
        'spectral_rolloff_85': mean_ro
    }

if __name__ == '__main__':
    print('=== REFERENCE FIXTURE ===')
    res_ref = analyze_audio('app/src/main/assets/speech_test.wav')
    print(json.dumps(res_ref, indent=2))

    print('=== DEVICE MICROPHONE ===')
    res_mic = analyze_audio('production_capture_source_test.wav')
    print(json.dumps(res_mic, indent=2))
