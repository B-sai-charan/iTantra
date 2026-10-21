import unicodedata
import re
import hashlib
import wave
import os
import sys

sys.stdout.reconfigure(encoding='utf-8')

# Implementation of IndicTextNormalizer in Python
def indic_normalize(text):
    if not text:
        return ""
    # 1. NFC
    nfc = unicodedata.normalize('NFC', text)
    # 2. Lowercase (casefold / lower)
    lowered = nfc.lower()
    # 3. Strip chars that are not Letter (\p{L}), Mark (\p{M}), Number (\p{N}), or Whitespace (\s)
    out = []
    for ch in lowered:
        cat = unicodedata.category(ch)
        # Letter: Lu, Ll, Lt, Lm, Lo
        # Mark: Mn, Mc, Me
        # Number: Nd, Nl, No
        # Space: Zs, Zl, Zp or isspace
        if cat.startswith('L') or cat.startswith('M') or cat.startswith('N') or ch.isspace():
            out.append(ch)
        # otherwise strip
    stripped = "".join(out)
    # 4. Collapse whitespace
    return re.sub(r'\s+', ' ', stripped).strip()

corpus = [
    {
        "id": "HI-001",
        "asset": "speech_test_hi_001.wav",
        "raw_ref": "कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,",
        "expected_duration_s": 9.120,
        "expected_samples": 145920,
        "expected_sha256": "3614bd9301b65e0fc8a12c43cdab7a403b834c7948337352f97a22e26475df43",
        "source_dataset": "Google FLEURS (google/fleurs, hi_in, test split)",
        "source_row_id": "1766 (10011266027513218401.wav)"
    },
    {
        "id": "HI-002",
        "asset": "speech_test_hi_002.wav",
        "raw_ref": "मनोविज्ञान सहित विज्ञान के सभी मामलों पर अरस्तू के विचारों को स्वीकार किया जाता है।",
        "expected_duration_s": 6.660,
        "expected_samples": 106560,
        "expected_sha256": "059942f06d572e46ccec1eb005522c118fc2a78b0612b160e84288315d04883d",
        "source_dataset": "Google FLEURS (google/fleurs, hi_in, test split)",
        "source_row_id": "1926 (14585676639063535081.wav)"
    },
    {
        "id": "HI-003",
        "asset": "speech_test_hi_003.wav",
        "raw_ref": "लूना को साथी पहलवानों ने भी श्रद्धांजलि दी.",
        "expected_duration_s": 3.840,
        "expected_samples": 61440,
        "expected_sha256": "1481c27cf6651bff893b7ba8977103e01a7fb82b9e79b8e7c0931a90b648f191",
        "source_dataset": "Google FLEURS (google/fleurs, hi_in, test split)",
        "source_row_id": "1747 (16976841982852749134.wav)"
    },
    {
        "id": "HI-004",
        "asset": "speech_test_hi_004.wav",
        "raw_ref": "रोम में अलग-अलग जगहों पर कई बड़े टेलीविज़न स्क्रीन लगाए गए ताकि लोग समारोह देख सकें.",
        "expected_duration_s": 8.940,
        "expected_samples": 143040,
        "expected_sha256": "4c8bbc7b39e3b0a0d052c03ce22f06e84210d4cd1c799669faecbdb90b5d2883",
        "source_dataset": "Google FLEURS (google/fleurs, hi_in, test split)",
        "source_row_id": "1917 (2129043417079181978.wav)"
    },
    {
        "id": "HI-005",
        "asset": "speech_test_hi_005.wav",
        "raw_ref": "यह शहर देश के बाकी शहरों से अलग है क्योंकि यह किसी अफ्रीकी शहर की बजाय अरब शहर लगता है.",
        "expected_duration_s": 8.820,
        "expected_samples": 141120,
        "expected_sha256": "f7cd61da6030853b5da777619c1ac00227debc17294039a882bbc27784aa5519",
        "source_dataset": "Google FLEURS (google/fleurs, hi_in, test split)",
        "source_row_id": "1958 (1137772989789278397.wav)"
    }
]

print("=== TASK 1: RECOMPUTE REFERENCE WORD COUNTS ===")
total_words = 0
for case in corpus:
    norm = indic_normalize(case["raw_ref"])
    words = norm.split(" ") if norm else []
    count = len(words)
    total_words += count
    print(f"  {case['id']} | word count: {count} | words: {words}")
print(f"Total Reference Words: {total_words}")
print(f"Matches documented (76): {total_words == 76}\n")

print("=== TASK 2: RECOMPUTE REFERENCE CHARACTER COUNTS ===")
total_chars = 0
for case in corpus:
    norm = indic_normalize(case["raw_ref"])
    # Unicode code points excluding whitespace
    chars = [ch for ch in norm if not ch.isspace()]
    count = len(chars)
    total_chars += count
    print(f"  {case['id']} | char count: {count} | normalized: \"{norm}\"")
print(f"Total Reference Characters: {total_chars}")
print(f"Matches documented (341): {total_chars == 341}\n")

print("=== TASK 3 & 5: VERIFY AUDIO METADATA & HASHES ===")
total_samples = 0
total_duration = 0.0
all_audio_match = True

for case in corpus:
    asset_path = os.path.join("app/src/main/assets", case["asset"])
    with open(asset_path, "rb") as f:
        data = f.read()
    sha = hashlib.sha256(data).hexdigest()
    
    with wave.open(asset_path, "rb") as w:
        channels = w.getnchannels()
        sampwidth = w.getsampwidth()
        framerate = w.getframerate()
        nframes = w.getnframes()
        duration = nframes / float(framerate)
    
    total_samples += nframes
    total_duration += duration
    
    sha_ok = (sha == case["expected_sha256"])
    sr_ok = (framerate == 16000)
    ch_ok = (channels == 1)
    fmt_ok = (sampwidth == 2)
    dur_ok = (abs(duration - case["expected_duration_s"]) < 1e-4)
    samp_ok = (nframes == case["expected_samples"])
    
    print(f"  {case['id']} ({case['asset']}):")
    print(f"    SHA256: {sha} (Expected: {case['expected_sha256']}) -> {sha_ok}")
    print(f"    Audio: {framerate} Hz, {channels} ch, {sampwidth*8}-bit PCM, {nframes} samples, {duration:.3f} s -> {sr_ok and ch_ok and fmt_ok and dur_ok and samp_ok}")
    if not (sha_ok and sr_ok and ch_ok and fmt_ok and dur_ok and samp_ok):
        all_audio_match = False

print(f"Total Duration: {total_duration:.3f} s (Expected: 37.380 s)")
print(f"Total Samples: {total_samples} (Expected: 598080)")
print(f"All Audio Metadata and Hashes Verified: {all_audio_match}\n")

print("=== TASK 4: VERIFY REFERENCE PAIRING ===")
for case in corpus:
    print(f"  {case['id']} -> {case['asset']}")
    print(f"    Dataset: {case['source_dataset']}")
    print(f"    Row ID: {case['source_row_id']}")
    print(f"    Raw Ref: \"{case['raw_ref']}\"")
