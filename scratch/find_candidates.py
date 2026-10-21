import urllib.request
import sys

sys.stdout.reconfigure(encoding='utf-8')

url = "https://huggingface.co/datasets/google/fleurs/raw/main/data/hi_in/test.tsv"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req) as resp:
    lines = resp.read().decode('utf-8').splitlines()

print(f"Total lines: {len(lines)}")
for i, line in enumerate(lines[:30]):
    parts = line.split('\t')
    if len(parts) >= 7:
        row_id, fname, raw_text, norm_text, phonemes, num_samples, gender = parts[:7]
        duration_s = int(num_samples) / 16000.0
        print(f"[{i}] ID: {row_id} | File: {fname} | Duration: {duration_s:.2f}s ({num_samples} samples) | Gender: {gender}")
        print(f"    Raw: {raw_text}")
        print(f"    Norm: {norm_text}")
        print()
