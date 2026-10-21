import os
import glob
import shutil

def perform_cleanup():
    deleted_files = []
    total_deleted_bytes = 0

    # 1. Delete matching scratch WAV files
    scratch_patterns = [
        "scratch/vad_*.wav",
        "scratch/audio_path_control_*.wav",
        "scratch/same_sentence_*.wav",
        "scratch/raw_mic_*.wav"
    ]
    
    for pat in scratch_patterns:
        for f in glob.glob(pat):
            sz = os.path.getsize(f)
            total_deleted_bytes += sz
            os.remove(f)
            deleted_files.append((f, sz))

    # 2. Delete app/build
    app_build = os.path.abspath("app/build")
    build_files_count = 0
    build_bytes = 0
    if os.path.exists(app_build):
        for root, dirs, files in os.walk(app_build):
            for file in files:
                fp = os.path.join(root, file)
                try:
                    build_bytes += os.path.getsize(fp)
                    build_files_count += 1
                except Exception:
                    pass
        total_deleted_bytes += build_bytes
        shutil.rmtree(app_build)

    print(f"CLEANUP COMPLETED SUCCESSFULLY:")
    print(f"- Deleted app/build: {build_files_count} files ({build_bytes / (1024*1024):.2f} MB)")
    print(f"- Deleted scratch WAVs: {len(deleted_files)} files ({sum(s for _, s in deleted_files) / (1024*1024):.2f} MB)")
    print(f"- Total deleted: {build_files_count + len(deleted_files)} files, {total_deleted_bytes} bytes ({total_deleted_bytes / (1024*1024):.2f} MB / {total_deleted_bytes / (1024*1024*1024):.4f} GB)")

if __name__ == "__main__":
    perform_cleanup()
