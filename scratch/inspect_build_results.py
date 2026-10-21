import os
import glob
import zipfile
import hashlib
import xml.etree.ElementTree as ET

def sha256_bytes(b):
    return hashlib.sha256(b).hexdigest()

def sha256_file(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest()

def inspect_results():
    print("=== 1. UNIT TEST SUMMARY ===")
    test_xmls = glob.glob("app/build/test-results/testDebugUnitTest/*.xml")
    total_tests = 0
    total_failures = 0
    total_errors = 0
    total_skipped = 0

    for xml_file in test_xmls:
        try:
            tree = ET.parse(xml_file)
            root = tree.getroot()
            total_tests += int(root.attrib.get("tests", 0))
            total_failures += int(root.attrib.get("failures", 0))
            total_errors += int(root.attrib.get("errors", 0))
            total_skipped += int(root.attrib.get("skipped", 0))
        except Exception as e:
            print(f"Error reading {xml_file}: {e}")

    passed_tests = total_tests - (total_failures + total_errors + total_skipped)
    print(f"Total Tests : {total_tests}")
    print(f"Passed      : {passed_tests}")
    print(f"Failed      : {total_failures}")
    print(f"Errors      : {total_errors}")
    print(f"Skipped     : {total_skipped}")

    apk_path = "app/build/outputs/apk/debug/app-debug.apk"
    if not os.path.exists(apk_path):
        print(f"\nAPK not yet present at {apk_path}")
        return

    print("\n=== 2. FRESH APK AUDIT ===")
    apk_sz = os.path.getsize(apk_path)
    apk_hash = sha256_file(apk_path)
    print(f"APK Path   : {apk_path}")
    print(f"APK Size   : {apk_sz} bytes ({apk_sz / (1024*1024):.2f} MB)")
    print(f"APK SHA-256: {apk_hash}")

    print("\n--- APK Native Libraries ---")
    with zipfile.ZipFile(apk_path, 'r') as z:
        for name in z.namelist():
            if name.startswith("lib/"):
                info = z.getinfo(name)
                print(f"   {name} ({info.file_size} bytes)")

    print("\n--- APK Model Assets & SHA-256 Verification ---")
    canonical_hashes = {
        "assets/models/whisper-tiny/tiny-decoder.int8.onnx": "d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925",
        "assets/models/whisper-tiny/tiny-encoder.int8.onnx": "d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434",
        "assets/vits-piper-en_US-lessac-low/en_US-lessac-low.onnx": "8177731223c47c72764e7021866f549dc72b894abc6b252c7f1ca0f8f65ad659",
        "assets/models/hi/vits-piper-hi_IN-rohan-medium/hi_IN-rohan-medium.onnx": "353d2a20bdb6dd03b74e1aff82b7a10a1646f677f8474c6c81fd606705536224"
    }

    with zipfile.ZipFile(apk_path, 'r') as z:
        for name in sorted(z.namelist()):
            if name.startswith("assets/models/") or name.startswith("assets/vits-piper-"):
                info = z.getinfo(name)
                if name.endswith(".onnx"):
                    data = z.read(name)
                    h = sha256_bytes(data)
                    expected = canonical_hashes.get(name)
                    match = (h == expected) if expected else "N/A"
                    print(f"   {name} ({info.file_size} bytes) -> SHA-256: {h} [MATCH CANONICAL: {match}]")
                elif not name.endswith("/"):
                    print(f"   {name} ({info.file_size} bytes)")

if __name__ == "__main__":
    inspect_results()
