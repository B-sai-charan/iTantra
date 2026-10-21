import os
import glob
import hashlib

def sha256_file(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest()

def verify_post_cleanup():
    print("=== POST-CLEANUP VERIFICATION ===")
    
    # 1. app/build/
    app_build_exists = os.path.exists("app/build")
    print(f"1. app/build/ exists: {app_build_exists} (Expected: False)")

    # 2. scratch WAVs
    patterns = [
        "scratch/vad_*.wav",
        "scratch/audio_path_control_*.wav",
        "scratch/same_sentence_*.wav",
        "scratch/raw_mic_*.wav"
    ]
    matching = []
    for pat in patterns:
        matching.extend(glob.glob(pat))
    print(f"2. Remaining matching scratch WAVs: {len(matching)} (Expected: 0)")
    if matching:
        print("   Found:", matching)

    # 3. Critical files
    critical_files = [
        "app/libs/sherpa-onnx-static-link-onnxruntime-1.13.7-arm64-sanitized.aar",
        "app/libs/vosk-android-0.3.75.aar",
        "app/src/main/assets/models/whisper-tiny/tiny-encoder.int8.onnx",
        "app/src/main/assets/models/whisper-tiny/tiny-decoder.int8.onnx",
        "app/src/main/assets/models/silero_vad.onnx",
        "app/src/main/assets/vits-piper-en_US-lessac-low/en_US-lessac-low.onnx",
        "app/src/main/assets/models/hi/vits-piper-hi_IN-rohan-medium/hi_IN-rohan-medium.onnx"
    ]
    print("\n3. Critical binary / model assets verification:")
    for cf in critical_files:
        exists = os.path.exists(cf)
        sz = os.path.getsize(cf) if exists else 0
        sha = sha256_file(cf) if exists else "N/A"
        print(f"   [{'EXISTS' if exists else 'MISSING'}] {cf} ({sz} bytes, SHA-256: {sha})")

    # 4. Critical directories and files
    dirs_and_archives = [
        ("M2/", os.path.isdir("M2")),
        ("M3/Dr. Vaseegaran.zip", os.path.isfile("M3/Dr. Vaseegaran.zip")),
        ("scratch/vosk-model-small-hi-0.22.zip", os.path.isfile("scratch/vosk-model-small-hi-0.22.zip")),
        ("docs/validation/", os.path.isdir("docs/validation")),
        ("docs/asr/", os.path.isdir("docs/asr"))
    ]
    print("\n4. Workspace directories and key archives:")
    for name, exists in dirs_and_archives:
        print(f"   [{'EXISTS' if exists else 'MISSING'}] {name}")

    py_files = glob.glob("scratch/*.py")
    print(f"   [{'EXISTS' if py_files else 'MISSING'}] scratch/*.py ({len(py_files)} python scripts preserved)")

if __name__ == "__main__":
    verify_post_cleanup()
