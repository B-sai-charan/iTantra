import urllib.request

urls = [
    "https://huggingface.co/datasets/google/fleurs/raw/main/data/hi_in/test.tsv",
    "https://huggingface.co/datasets/google/fleurs/raw/main/data/hi_in/train.tsv",
]

for url in urls:
    try:
        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(req) as resp:
            content = resp.read(2048).decode('utf-8')
            print(f"Success for {url}:")
            print(content[:500])
    except Exception as e:
        print(f"Failed {url}: {e}")
