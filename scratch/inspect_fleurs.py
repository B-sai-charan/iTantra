import urllib.request
import json

url = "https://datasets-server.huggingface.co/rows?dataset=google%2Ffleurs&config=hi_in&split=test&offset=0&limit=15"
req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
resp = urllib.request.urlopen(req)
data = json.loads(resp.read().decode('utf-8'))

for i, r in enumerate(data['rows']):
    row = r['row']
    print(f"Index: {i}")
    print(f"  ID: {row.get('id')}")
    print(f"  Path: {row.get('path')}")
    print(f"  Raw Transcription: {row.get('raw_transcription')}")
    print(f"  Transcription: {row.get('transcription')}")
    print(f"  Audio Source: {row.get('audio')[0].get('src')}")
    print("---")
