import zipfile
import hashlib
import os

ARCHIVE_PATH = "M3/Dr. Vaseegaran.zip"

def sha256_bytes(b):
    return hashlib.sha256(b).hexdigest()

def sha256_file(filepath):
    if not os.path.exists(filepath):
        return None
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest()

def compare_sources():
    zf = zipfile.ZipFile(ARCHIVE_PATH, 'r')
    infolist = zf.infolist()

    print("=== SOURCE & ASSET COMPARISON: ARCHIVE vs ACTIVE REPOSITORY ===")
    
    # 1. Source files (.kt, .java)
    src_entries = [i for i in infolist if i.filename.endswith(('.kt', '.java')) and not i.filename.startswith('Dr. Vaseegaran/.gradle')]
    print(f"\n--- Kotlin / Java Sources ({len(src_entries)} files) ---")
    for s in sorted(src_entries, key=lambda x: x.filename):
        content = zf.read(s.filename)
        z_hash = sha256_bytes(content)
        # map relative path
        # Dr. Vaseegaran/app/src/main/java/org/itantra/... -> app/src/main/java/org/itantra/...
        rel_path = s.filename.replace("Dr. Vaseegaran/", "")
        local_hash = sha256_file(rel_path)
        status = "EXACT MATCH" if z_hash == local_hash else ("DIFFERENT" if local_hash else "MISSING IN REPO")
        print(f"{rel_path:70s} | {status} (Archive: {z_hash[:8]} | Local: {local_hash[:8] if local_hash else 'NONE'})")

    # 2. Benchmark & normalizer files
    print("\n--- Key M3 Modules Check in Repo ---")
    key_modules = [
        "app/src/main/java/org/itantra/benchmark/HindiBenchmarkRunner.kt",
        "app/src/main/java/org/itantra/benchmark/HindiBenchmarkDataset.kt",
        "app/src/main/java/org/itantra/tts/SherpaOnnxTtsEngine.kt",
        "app/src/main/java/org/itantra/tts/TtsManager.kt",
        "app/src/main/java/org/itantra/ui/HindiBenchmarkScreen.kt",
        "app/src/main/java/org/itantra/ui/TtsLabScreen.kt"
    ]
    for km in key_modules:
        ex = os.path.exists(km)
        print(f"  {km:65s} -> Exists locally: {ex}")

    # 3. Model assets check
    print("\n--- Model Assets Check ---")
    model_entries = [i for i in infolist if "assets/models" in i.filename and not i.filename.endswith('/') and not i.filename.startswith('Dr. Vaseegaran/app/build')]
    for m in model_entries:
        content = zf.read(m.filename)
        z_hash = sha256_bytes(content)
        rel_path = m.filename.replace("Dr. Vaseegaran/", "")
        # Note: in Dr. Vaseegaran it was app/src/main/assets/models/en/vits-piper-en_US-lessac-low/...
        # and in our repo it is app/src/main/assets/vits-piper-en_US-lessac-low/...
        # Let's check both paths
        local_path = rel_path
        if not os.path.exists(local_path):
            alt_path = rel_path.replace("assets/models/en/", "assets/").replace("assets/models/hi/", "assets/models/hi/")
            if os.path.exists(alt_path):
                local_path = alt_path
        local_hash = sha256_file(local_path)
        status = "EXACT MATCH" if z_hash == local_hash else ("DIFFERENT" if local_hash else "MISSING")
        print(f"{rel_path:60s} -> {local_path:50s} | {status}")

if __name__ == "__main__":
    compare_sources()
