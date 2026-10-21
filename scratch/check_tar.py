import urllib.request
import tarfile
import io

url = "https://huggingface.co/datasets/google/fleurs/resolve/main/data/hi_in/audio/test.tar.gz"
print(f"Connecting to {url}...")
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
resp = urllib.request.urlopen(req)
print(f"Response code: {resp.getcode()}, length: {resp.headers.get('Content-Length')}")
