import zipfile
import io
import hashlib

ARCHIVE_PATH = "M3/Dr. Vaseegaran.zip"

def inspect_embedded_apk():
    zf = zipfile.ZipFile(ARCHIVE_PATH, 'r')
    apk_data = zf.read("Dr. Vaseegaran/app/build/outputs/apk/debug/app-debug.apk")
    apk_io = io.BytesIO(apk_data)
    apk_zf = zipfile.ZipFile(apk_io, 'r')

    print("=== EMBEDDED APK IN M3 ARCHIVE AUDIT ===")
    print(f"Total entries in embedded APK: {len(apk_zf.namelist())}")
    
    print("\n--- Native Libraries in Embedded APK ---")
    for name in apk_zf.namelist():
        if name.startswith("lib/"):
            info = apk_zf.getinfo(name)
            print(f"  {name:50s} ({info.file_size} B)")

    print("\n--- Model Assets in Embedded APK ---")
    for name in sorted(apk_zf.namelist()):
        if name.startswith("assets/models/") or name.startswith("assets/vits-piper-"):
            if name.endswith(".onnx"):
                data = apk_zf.read(name)
                h = hashlib.sha256(data).hexdigest()
                info = apk_zf.getinfo(name)
                print(f"  {name:60s} ({info.file_size} B) SHA-256: {h}")

if __name__ == "__main__":
    inspect_embedded_apk()
