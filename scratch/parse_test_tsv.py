import urllib.request
import sys

sys.stdout.reconfigure(encoding='utf-8')

url = "https://huggingface.co/datasets/google/fleurs/raw/main/data/hi_in/test.tsv"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req) as resp:
    lines = resp.read().decode('utf-8').splitlines()

print(f"Total lines in test.tsv: {len(lines)}")
for i, line in enumerate(lines[:10]):
    parts = line.split('\t')
    print(f"Line {i}: {len(parts)} parts")
    if len(parts) >= 4:
        print(f"  ID: {parts[0]}")
        print(f"  Filename: {parts[1]}")
        print(f"  Raw Transcription: {parts[2]}")
        print(f"  Normalized: {parts[3]}")
