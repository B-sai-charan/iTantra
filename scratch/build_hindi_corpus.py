import struct
import hashlib
import os
import sys

sys.stdout.reconfigure(encoding='utf-8')

corpus_defs = [
    {
        "case_id": "HI-001",
        "row_id": "1766",
        "source_file": "10011266027513218401.wav",
        "target_file": "speech_test_hi_001.wav",
        "gender": "MALE",
        "raw_reference": "कुछ अणुओं में अस्थिर केंद्रक होता है, जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है,",
        "norm_reference": "कुछ अणुओं में अस्थिर केंद्रक होता है जिसका मतलब यह है कि उनमें थोड़े या बिना किसी झटके से टूटने की प्रवृत्ति होती है"
    },
    {
        "case_id": "HI-002",
        "row_id": "1926",
        "source_file": "14585676639063535081.wav",
        "target_file": "speech_test_hi_002.wav",
        "gender": "FEMALE",
        "raw_reference": "मनोविज्ञान सहित विज्ञान के सभी मामलों पर अरस्तू के विचारों को स्वीकार किया जाता है।",
        "norm_reference": "मनोविज्ञान सहित विज्ञान के सभी मामलों पर अरस्तू के विचारों को स्वीकार किया जाता है"
    },
    {
        "case_id": "HI-003",
        "row_id": "1747",
        "source_file": "16976841982852749134.wav",
        "target_file": "speech_test_hi_003.wav",
        "gender": "MALE",
        "raw_reference": "लूना को साथी पहलवानों ने भी श्रद्धांजलि दी.",
        "norm_reference": "लूना को साथी पहलवानों ने भी श्रद्धांजलि दी"
    },
    {
        "case_id": "HI-004",
        "row_id": "1917",
        "source_file": "2129043417079181978.wav",
        "target_file": "speech_test_hi_004.wav",
        "gender": "MALE",
        "raw_reference": "रोम में अलग-अलग जगहों पर कई बड़े टेलीविज़न स्क्रीन लगाए गए ताकि लोग समारोह देख सकें.",
        "norm_reference": "रोम में अलग-अलग जगहों पर कई बड़े टेलीविज़न स्क्रीन लगाए गए ताकि लोग समारोह देख सकें"
    },
    {
        "case_id": "HI-005",
        "row_id": "1958",
        "source_file": "1137772989789278397.wav",
        "target_file": "speech_test_hi_005.wav",
        "gender": "FEMALE",
        "raw_reference": "यह शहर देश के बाकी शहरों से अलग है क्योंकि यह किसी अफ्रीकी शहर की बजाय अरब शहर लगता है.",
        "norm_reference": "यह शहर देश के बाकी शहरों से अलग है क्योंकि यह किसी अफ्रीकी शहर की बजाय अरब शहर लगता है"
    }
]

os.makedirs("app/src/main/assets", exist_ok=True)

manifest = []

for case in corpus_defs:
    raw_path = os.path.join("scratch/fleurs_audio_raw", case["source_file"])
    with open(raw_path, "rb") as f:
        raw_bytes = f.read()
    
    raw_sha = hashlib.sha256(raw_bytes).hexdigest()
    raw_size = len(raw_bytes)
    
    # 58 bytes header in FLEURS IEEE Float WAV
    num_samples = (raw_size - 58) // 4
    raw_floats = struct.unpack(f"<{num_samples}f", raw_bytes[58:58 + num_samples*4])
    
    shorts = []
    for flt in raw_floats:
        if flt >= 0:
            val = int(round(flt * 32767.0))
        else:
            val = int(round(flt * 32768.0))
        val = max(-32768, min(32767, val))
        shorts.append(val)
    
    pcm16_data = struct.pack(f"<{len(shorts)}h", *shorts)
    data_size = len(pcm16_data)
    riff_size = 36 + data_size
    
    wav_header = struct.pack(
        "<4sI4s4sIHHIIHH4sI",
        b'RIFF', riff_size, b'WAVE',
        b'fmt ', 16, 1, 1, 16000, 32000, 2, 16,
        b'data', data_size
    )
    final_wav_bytes = wav_header + pcm16_data
    
    target_path = os.path.join("app/src/main/assets", case["target_file"])
    
    # Only write if not already present or verify if HI-001
    if case["case_id"] == "HI-001":
        with open(target_path, "rb") as existing_f:
            existing_bytes = existing_f.read()
            assert hashlib.sha256(existing_bytes).hexdigest() == "3614bd9301b65e0fc8a12c43cdab7a403b834c7948337352f97a22e26475df43", "HI-001 hash mismatch!"
            assert final_wav_bytes == existing_bytes, "HI-001 byte mismatch!"
    else:
        with open(target_path, "wb") as out_f:
            out_f.write(final_wav_bytes)
    
    final_sha = hashlib.sha256(final_wav_bytes).hexdigest()
    duration_s = num_samples / 16000.0
    duration_ms = (num_samples * 1000) // 16000
    
    item = {
        "case_id": case["case_id"],
        "row_id": case["row_id"],
        "source_file": case["source_file"],
        "target_file": case["target_file"],
        "gender": case["gender"],
        "num_samples": num_samples,
        "duration_s": duration_s,
        "duration_ms": duration_ms,
        "raw_sha256": raw_sha,
        "local_sha256": final_sha,
        "raw_reference": case["raw_reference"],
        "norm_reference": case["norm_reference"]
    }
    manifest.append(item)
    
    print(f"Case {case['case_id']}:")
    print(f"  Asset: {case['target_file']}")
    print(f"  Source: FLEURS hi_in test ID {case['row_id']} ({case['source_file']})")
    print(f"  Gender: {case['gender']}")
    print(f"  Samples: {num_samples} ({duration_s:.3f} s / {duration_ms} ms)")
    print(f"  Raw Source SHA256: {raw_sha}")
    print(f"  Local Asset SHA256: {final_sha}")
    print(f"  Reference: \"{case['raw_reference']}\"")
    print()
