import wave
import struct
import math
import os
import json

REFERENCE_TEXT = "After early nightfall, the yellow lamps would light up here and there."
NORMALIZED_REF = "after early nightfall the yellow lamps would light up here and there"

def normalize_text(text):
    text = text.lower()
    cleaned = []
    for c in text:
        if c.isalnum() or c.isspace():
            cleaned.append(c)
        else:
            cleaned.append(' ')
    return ' '.join(''.join(cleaned).split())

def calculate_wer_cer(ref, hyp):
    ref_norm = normalize_text(ref)
    hyp_norm = normalize_text(hyp)
    
    ref_words = ref_norm.split()
    hyp_words = hyp_norm.split()
    
    # Word Error Rate (Levenshtein distance on words)
    d_w = [[0] * (len(hyp_words) + 1) for _ in range(len(ref_words) + 1)]
    for i in range(len(ref_words) + 1):
        d_w[i][0] = i
    for j in range(len(hyp_words) + 1):
        d_w[0][j] = j
    for i in range(1, len(ref_words) + 1):
        for j in range(1, len(hyp_words) + 1):
            if ref_words[i-1] == hyp_words[j-1]:
                d_w[i][j] = d_w[i-1][j-1]
            else:
                d_w[i][j] = 1 + min(d_w[i-1][j], d_w[i][j-1], d_w[i-1][j-1])
    
    wer = d_w[len(ref_words)][len(hyp_words)] / len(ref_words) if ref_words else 0.0
    
    # Character Error Rate
    ref_chars = list(ref_norm.replace(' ', ''))
    hyp_chars = list(hyp_norm.replace(' ', ''))
    d_c = [[0] * (len(hyp_chars) + 1) for _ in range(len(ref_chars) + 1)]
    for i in range(len(ref_chars) + 1):
        d_c[i][0] = i
    for j in range(len(hyp_chars) + 1):
        d_c[0][j] = j
    for i in range(1, len(ref_chars) + 1):
        for j in range(1, len(hyp_chars) + 1):
            if ref_chars[i-1] == hyp_chars[j-1]:
                d_c[i][j] = d_c[i-1][j-1]
            else:
                d_c[i][j] = 1 + min(d_c[i-1][j], d_c[i][j-1], d_c[i-1][j-1])
                
    cer = d_c[len(ref_chars)][len(hyp_chars)] / len(ref_chars) if ref_chars else 0.0
    
    return {
        "wer": wer,
        "cer": cer,
        "ref_norm": ref_norm,
        "hyp_norm": hyp_norm,
        "ref_words": len(ref_words),
        "hyp_words": len(hyp_words),
        "word_errors": d_w[len(ref_words)][len(hyp_words)],
        "ref_chars": len(ref_chars),
        "hyp_chars": len(hyp_chars),
        "char_errors": d_c[len(ref_chars)][len(hyp_chars)]
    }

def analyze_wav(file_path):
    with wave.open(file_path, 'rb') as wf:
        n_channels = wf.getnchannels()
        sampwidth = wf.getsampwidth()
        framerate = wf.getframerate()
        n_frames = wf.getnframes()
        raw_bytes = wf.readframes(n_frames)
        samples = list(struct.unpack(f"<{n_frames * n_channels}h", raw_bytes))
        
    duration = n_frames / float(framerate)
    max_amp = max(abs(s) for s in samples) if samples else 0
    peak_dbfs = 20 * math.log10(max_amp / 32768.0) if max_amp > 0 else -96.0
    sum_sq = sum(float(s) * float(s) for s in samples)
    mean_sq = sum_sq / len(samples) if samples else 0.0
    rms = math.sqrt(mean_sq)
    rms_dbfs = 20 * math.log10(rms / 32768.0) if rms > 0 else -96.0
    clips = sum(1 for s in samples if abs(s) >= 32767)
    
    # Frame-by-frame energy VAD boundary detection (32ms frames = 512 samples)
    frame_size = 512
    num_frames = len(samples) // frame_size
    frame_energies = []
    for i in range(num_frames):
        f = samples[i*frame_size:(i+1)*frame_size]
        f_rms = math.sqrt(sum(float(x)*float(x) for x in f) / frame_size)
        f_dbfs = 20 * math.log10(f_rms / 32768.0) if f_rms > 0 else -96.0
        frame_energies.append(f_dbfs)
        
    # Active speech threshold at -40 dBFS or baseline + 6 dB
    speech_frames = [i for i, e in enumerate(frame_energies) if e > -42.0]
    if speech_frames:
        start_frame = max(0, speech_frames[0] - 2)
        end_frame = min(num_frames, speech_frames[-1] + 3)
        speech_start_ms = int((start_frame * frame_size * 1000) / framerate)
        speech_end_ms = int((end_frame * frame_size * 1000) / framerate)
        speech_dur_ms = speech_end_ms - speech_start_ms
    else:
        speech_start_ms = 0
        speech_end_ms = int(duration * 1000)
        speech_dur_ms = speech_end_ms
        
    return {
        "file_path": file_path,
        "sample_count": len(samples),
        "framerate": framerate,
        "duration_sec": duration,
        "peak_amp": max_amp,
        "peak_dbfs": peak_dbfs,
        "rms_dbfs": rms_dbfs,
        "clipping_count": clips,
        "vad_start_ms": speech_start_ms,
        "vad_end_ms": speech_end_ms,
        "vad_duration_ms": speech_dur_ms
    }

print("Loaded analysis script successfully.")
