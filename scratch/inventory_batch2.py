import os
import glob
import hashlib

def get_dir_size_and_files(dir_path):
    total_size = 0
    file_list = []
    if not os.path.exists(dir_path):
        return 0, []
    for root, dirs, files in os.walk(dir_path):
        for f in files:
            fp = os.path.join(root, f)
            try:
                sz = os.path.getsize(fp)
                total_size += sz
                file_list.append((fp, sz))
            except Exception as e:
                pass
    return total_size, file_list

def run_inventory():
    print("=== PRE-DELETION INVENTORY ===")
    # 1. app/build/
    app_build_path = os.path.abspath("app/build")
    build_size, build_files = get_dir_size_and_files(app_build_path)
    print(f"1. app/build/ directory:")
    print(f"   Files count: {len(build_files)}")
    print(f"   Total size: {build_size} bytes ({build_size / (1024*1024):.2f} MB)")

    # 2-5. scratch WAV patterns
    scratch_patterns = [
        "scratch/vad_*.wav",
        "scratch/audio_path_control_*.wav",
        "scratch/same_sentence_*.wav",
        "scratch/raw_mic_*.wav"
    ]
    
    scratch_files_to_delete = []
    for pat in scratch_patterns:
        matches = glob.glob(pat)
        for m in sorted(matches):
            sz = os.path.getsize(m)
            scratch_files_to_delete.append((m, sz))
            
    scratch_size = sum(sz for _, sz in scratch_files_to_delete)
    print(f"\n2. scratch/*.wav target files:")
    print(f"   Files count: {len(scratch_files_to_delete)}")
    print(f"   Total size: {scratch_size} bytes ({scratch_size / (1024*1024):.2f} MB)")
    print("\n--- Listing matching scratch WAV files ---")
    for fpath, sz in scratch_files_to_delete:
        norm_path = fpath.replace('\\', '/')
        print(f"   {norm_path} ({sz} bytes, {sz/(1024*1024):.4f} MB)")
        
    grand_total_files = len(build_files) + len(scratch_files_to_delete)
    grand_total_size = build_size + scratch_size
    print("\n=== SUMMARY OF DELETION TARGETS ===")
    print(f"Total files to delete: {grand_total_files} (app/build tree + {len(scratch_files_to_delete)} scratch WAVs)")
    print(f"Total size to delete: {grand_total_size} bytes ({grand_total_size / (1024*1024):.2f} MB / {grand_total_size / (1024*1024*1024):.4f} GB)")

if __name__ == "__main__":
    run_inventory()
