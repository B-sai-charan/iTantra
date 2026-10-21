import urllib.request
import sys

sys.stdout.reconfigure(encoding='utf-8')

url = "https://huggingface.co/datasets/google/fleurs/raw/main/data/hi_in/test.tsv"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
try:
    with urllib.request.urlopen(req) as resp:
        lines = resp.read().decode('utf-8').splitlines()
    print(f"Successfully loaded test.tsv ({len(lines)} lines)")
    
    target_ids = {"1766": "HI-001", "1926": "HI-002", "1747": "HI-003", "1917": "HI-004", "1958": "HI-005"}
    
    for line in lines:
        parts = line.split('\t')
        if parts[0] in target_ids:
            cid = target_ids[parts[0]]
            row_id = parts[0]
            fname = parts[1]
            raw_text = parts[2]
            norm_text = parts[3]
            samples = parts[5] if len(parts) > 5 else 'N/A'
            gender = parts[6] if len(parts) > 6 else 'N/A'
            print(f"Case {cid} -> Row ID {row_id}:")
            print(f"  Filename: {fname}")
            print(f"  Raw Text: \"{raw_text}\"")
            print(f"  Norm Text: \"{norm_text}\"")
            print(f"  Samples: {samples} | Gender: {gender}")
            print()
except Exception as e:
    print(f"Error fetching test.tsv: {e}")
