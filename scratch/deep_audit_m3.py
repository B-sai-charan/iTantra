import zipfile
import hashlib
import os

ARCHIVE_PATH = "M3/Dr. Vaseegaran.zip"

def sha256_stream(zf, name):
    h = hashlib.sha256()
    with zf.open(name) as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest()

def deep_audit():
    zf = zipfile.ZipFile(ARCHIVE_PATH, 'r')
    infolist = zf.infolist()

    print("=== ONNX MODELS IN ARCHIVE ===")
    onnx_entries = [i for i in infolist if i.filename.endswith('.onnx')]
    for o in onnx_entries:
        h = sha256_stream(zf, o.filename)
        print(f"Path: {o.filename}\n  Size: {o.file_size} B ({o.file_size/(1024*1024):.2f} MB), SHA-256: {h}")

    print("\n=== AAR FILES IN ARCHIVE ===")
    aar_entries = [i for i in infolist if i.filename.endswith('.aar')]
    for a in aar_entries:
        h = sha256_stream(zf, a.filename)
        print(f"Path: {a.filename}\n  Size: {a.file_size} B ({a.file_size/(1024*1024):.2f} MB), SHA-256: {h}")

    print("\n=== APK FILES IN ARCHIVE ===")
    apk_entries = [i for i in infolist if i.filename.endswith('.apk')]
    for apk in apk_entries:
        h = sha256_stream(zf, apk.filename)
        print(f"Path: {apk.filename}\n  Size: {apk.file_size} B ({apk.file_size/(1024*1024):.2f} MB), SHA-256: {h}")

    print("\n=== SOURCE FILES IN ARCHIVE (.kt, .java) ===")
    src_entries = [i for i in infolist if i.filename.endswith(('.kt', '.java'))]
    for s in sorted(src_entries, key=lambda x: x.filename):
        print(f"  {s.filename} ({s.file_size} B)")

    print("\n=== DOCUMENTATION / REPORTS / BENCHMARKS ===")
    doc_entries = [i for i in infolist if i.filename.endswith(('.md', '.json', '.txt', '.pdf', '.docx', '.csv', '.tsv', '.html')) and not i.filename.startswith(('Dr. Vaseegaran/.tools', 'Dr. Vaseegaran/app/build'))]
    for d in sorted(doc_entries, key=lambda x: x.filename):
        print(f"  {d.filename} ({d.file_size} B)")

    print("\n=== AUDIO FILES (.wav, .mp3, .pcm) ===")
    audio_entries = [i for i in infolist if i.filename.endswith(('.wav', '.mp3', '.pcm', '.ogg'))]
    for au in sorted(audio_entries, key=lambda x: x.filename):
        print(f"  {au.filename} ({au.file_size} B)")

    print("\n=== TOP LEVEL DIRECTORIES IN ARCHIVE ===")
    top_dirs = set()
    second_dirs = set()
    for i in infolist:
        parts = i.filename.replace('\\', '/').split('/')
        if len(parts) > 1:
            top_dirs.add(parts[0])
            second_dirs.add(f"{parts[0]}/{parts[1]}")
    print("Top dirs:", sorted(list(top_dirs)))
    print("Second dirs:", sorted(list(second_dirs)))

if __name__ == "__main__":
    deep_audit()
