import os
import zipfile
import hashlib
import json

ARCHIVE_PATH = "M3/Dr. Vaseegaran.zip"

def sha256_stream(zf, name):
    h = hashlib.sha256()
    with zf.open(name) as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest()

def sha256_file(filepath):
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(1024 * 1024):
            h.update(chunk)
    return h.hexdigest()

def audit_archive():
    if not os.path.exists(ARCHIVE_PATH):
        print(f"Archive not found: {ARCHIVE_PATH}")
        return

    arch_sz = os.path.getsize(ARCHIVE_PATH)
    arch_hash = sha256_file(ARCHIVE_PATH)

    print("=== 1. ARCHIVE FORENSIC INVENTORY ===")
    print(f"Archive Path     : {ARCHIVE_PATH}")
    print(f"Exact Size       : {arch_sz} bytes ({arch_sz / (1024*1024):.2f} MB / {arch_sz / (1024*1024*1024):.4f} GB)")
    print(f"SHA-256 Checksum : {arch_hash}")

    zf = zipfile.ZipFile(ARCHIVE_PATH, 'r')
    infolist = zf.infolist()
    total_entries = len(infolist)
    dir_entries = sum(1 for i in infolist if i.is_dir() or i.filename.endswith('/'))
    file_entries = total_entries - dir_entries
    uncompressed_size = sum(i.file_size for i in infolist)
    compressed_size = sum(i.compress_size for i in infolist)
    compression_ratio = (compressed_size / uncompressed_size) if uncompressed_size > 0 else 0

    print(f"Total Entries    : {total_entries}")
    print(f"Directory Entries: {dir_entries}")
    print(f"File Entries     : {file_entries}")
    print(f"Uncompressed Size: {uncompressed_size} bytes ({uncompressed_size / (1024*1024):.2f} MB / {uncompressed_size / (1024*1024*1024):.4f} GB)")
    print(f"Compressed Size  : {compressed_size} bytes ({compressed_size / (1024*1024):.2f} MB / {compressed_size / (1024*1024*1024):.4f} GB)")
    print(f"Compression Ratio: {compression_ratio:.4f} ({compression_ratio*100:.2f}%)")

    # Top-level directory breakdown
    top_dirs = {}
    for i in infolist:
        parts = i.filename.replace('\\', '/').split('/')
        top_name = parts[0] if len(parts) > 1 else "(root)"
        if top_name not in top_dirs:
            top_dirs[top_name] = {"files": 0, "compressed": 0, "uncompressed": 0}
        if not (i.is_dir() or i.filename.endswith('/')):
            top_dirs[top_name]["files"] += 1
            top_dirs[top_name]["compressed"] += i.compress_size
            top_dirs[top_name]["uncompressed"] += i.file_size

    print("\n--- Breakdown by Top-Level Structure ---")
    for d, st in sorted(top_dirs.items(), key=lambda x: x[1]["uncompressed"], reverse=True):
        print(f"Directory: {d:30s} | Files: {st['files']:5d} | Compressed: {st['compressed']/(1024*1024):8.2f} MB | Uncompressed: {st['uncompressed']/(1024*1024):8.2f} MB")

    # Large members >= 10MB
    print("\n=== 2. LARGE ARTIFACT INVENTORY (>= 10 MB) ===")
    large_members = [i for i in infolist if i.file_size >= 10 * 1024 * 1024 and not (i.is_dir() or i.filename.endswith('/'))]
    print(f"Total Large Members (>= 10 MB): {len(large_members)}")
    for lm in sorted(large_members, key=lambda x: x.file_size, reverse=True):
        ext = os.path.splitext(lm.filename)[1].lower()
        lm_hash = sha256_stream(zf, lm.filename)
        print(f"\nPath         : {lm.filename}")
        print(f"Uncompressed : {lm.file_size} bytes ({lm.file_size / (1024*1024):.2f} MB)")
        print(f"Compressed   : {lm.compress_size} bytes ({lm.compress_size / (1024*1024):.2f} MB)")
        print(f"Extension    : {ext}")
        print(f"SHA-256      : {lm_hash}")

    # Inspect all files by type/category
    all_files = [i for i in infolist if not (i.is_dir() or i.filename.endswith('/'))]
    apk_files = [i for i in all_files if i.filename.endswith('.apk')]
    onnx_files = [i for i in all_files if i.filename.endswith('.onnx')]
    aar_files = [i for i in all_files if i.filename.endswith('.aar')]
    source_files = [i for i in all_files if i.filename.endswith(('.kt', '.java', '.gradle', '.gradle.kts'))]
    doc_files = [i for i in all_files if i.filename.endswith(('.md', '.txt', '.json', '.csv', '.tsv', '.pdf', '.docx', '.html'))]
    
    print("\n=== 3. CATEGORY COUNTS ===")
    print(f"APKs        : {len(apk_files)}")
    print(f"ONNX models : {len(onnx_files)}")
    print(f"AAR libs    : {len(aar_files)}")
    print(f"Source files: {len(source_files)}")
    print(f"Docs/Reports: {len(doc_files)}")

if __name__ == "__main__":
    audit_archive()
