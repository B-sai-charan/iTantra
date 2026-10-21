import subprocess
import time
import json
import re
import math

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
DEVICE = "HNP097HQ"

REFERENCE_12 = "After early nightfall, the yellow lamps would light up here and there."
REFERENCE_18 = "After early nightfall, the yellow lamps would light up here and there the squalid quarter of the brothels."

VARIANTS = [
    ("A", "ORIGINAL", "spectral_ab_original.wav", 0.0, 0.0),
    ("B", "LOW_BOOST_+3dB", "spectral_ab_low3.wav", 3.0, 0.0),
    ("C", "LOW_BOOST_+6dB", "spectral_ab_low6.wav", 6.0, 0.0),
    ("D", "LOW_BOOST_+9dB", "spectral_ab_low9.wav", 9.0, 0.0),
    ("E", "LOW_BOOST_+12dB", "spectral_ab_low12.wav", 12.0, 0.0),
    ("F", "LOW_BOOST_+6dB_MID_-3dB", "spectral_ab_low6_midminus3.wav", 6.0, -3.0),
    ("G", "LOW_BOOST_+9dB_MID_-3dB", "spectral_ab_low9_midminus3.wav", 9.0, -3.0),
]

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
    
    # WER
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
    
    # CER
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
    
    return wer, cer, ref_norm, hyp_norm

def run_adb_eval(wav_filename):
    # Clear logcat
    subprocess.run([ADB, "-s", DEVICE, "logcat", "-c"], check=True)
    time.sleep(0.5)
    
    # Launch intent
    cmd = [
        ADB, "-s", DEVICE, "shell", "am", "start",
        "-n", "org.itantra.app/org.itantra.MainActivity",
        "-a", "org.itantra.action.RUN_ACOUSTIC_PLAYBACK_LOOPBACK",
        "--es", "evalWav", wav_filename,
        "--ei", "repIndex", "1"
    ]
    subprocess.run(cmd, check=True)
    
    # Wait for completion in logcat
    raw_hyp = ""
    inf_time_ms = 0
    rtf = 0.0
    detected_lang = "en"
    
    max_wait = 15
    start_time = time.time()
    
    pattern = re.compile(r'\[Loopback Rep 1 ASR\] Raw:\s*"(.*?)"\s*\|\s*WER=.*?Time=(\d+)ms,\s*RTF=([0-9.]+)')
    
    while time.time() - start_time < max_wait:
        time.sleep(1.0)
        logs = subprocess.run([ADB, "-s", DEVICE, "logcat", "-d", "-s", "ACOUSTIC_LOOPBACK_DIAG:I"], capture_output=True, text=True).stdout
        match = pattern.search(logs)
        if match:
            raw_hyp = match.group(1)
            inf_time_ms = int(match.group(2))
            rtf = float(match.group(3))
            break
            
    return raw_hyp, inf_time_ms, rtf

def main():
    results = []
    
    # Load signal stats from generated variants
    with open("scratch/controlled_same_sentence_07_data.json", "r") as f:
        pass # just checking
        
    print("=================================================================")
    print("STARTING ON-DEVICE WHISPER EVALUATION FOR 7 FILTERED VARIANTS")
    print("=================================================================")
    
    for code, desc, fname, low_b, mid_c in VARIANTS:
        print(f"\nEvaluating Variant {code}: {desc} ({fname})...")
        raw_hyp, inf_ms, rtf = run_adb_eval(fname)
        
        # Calculate WER / CER against target 12-word reference
        wer12, cer12, norm_ref12, norm_hyp = calculate_wer_cer(REFERENCE_12, raw_hyp)
        wer18, cer18, norm_ref18, _ = calculate_wer_cer(REFERENCE_18, raw_hyp)
        
        print(f"  Result: Raw = \"{raw_hyp}\"")
        print(f"  Normalized = \"{norm_hyp}\"")
        print(f"  Latency = {inf_ms} ms | RTF = {rtf:.4f}")
        print(f"  12-word Target: WER = {wer12*100:.2f}% | CER = {cer12*100:.2f}%")
        print(f"  18-word Target: WER = {wer18*100:.2f}% | CER = {cer18*100:.2f}%")
        
        results.append({
            "variant_code": code,
            "description": desc,
            "file_name": fname,
            "low_boost_db": low_b,
            "mid_corr_db": mid_c,
            "raw_hypothesis": raw_hyp,
            "normalized_hypothesis": norm_hyp,
            "latency_ms": inf_ms,
            "rtf": rtf,
            "wer_12_pct": round(wer12 * 100.0, 2),
            "cer_12_pct": round(cer12 * 100.0, 2),
            "wer_18_pct": round(wer18 * 100.0, 2),
            "cer_18_pct": round(cer18 * 100.0, 2)
        })
        
    with open("scratch/spectral_ab_10_results.json", "w") as f:
        json.dump(results, f, indent=2)
        
    print("\nSaved all results to scratch/spectral_ab_10_results.json")

if __name__ == "__main__":
    main()
