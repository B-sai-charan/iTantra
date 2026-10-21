import zipfile
import os

ARCHIVE_PATH = "M3/Dr. Vaseegaran.zip"

def breakdown_sizes():
    zf = zipfile.ZipFile(ARCHIVE_PATH, 'r')
    categories = {}
    for item in zf.infolist():
        fn = item.filename.replace('\\', '/')
        parts = fn.split('/')
        if len(parts) > 1:
            cat = f"{parts[0]}/{parts[1]}"
            if len(parts) > 2 and parts[1] in ('.tools', 'app', '.gradle'):
                cat = f"{parts[0]}/{parts[1]}/{parts[2]}"
        else:
            cat = parts[0]
            
        if cat not in categories:
            categories[cat] = {"count": 0, "comp": 0, "uncomp": 0}
        categories[cat]["count"] += 1
        categories[cat]["comp"] += item.compress_size
        categories[cat]["uncomp"] += item.file_size

    print("=== DIRECTORY SIZES BREAKDOWN IN Dr. Vaseegaran.zip ===")
    for c, data in sorted(categories.items(), key=lambda x: x[1]["uncomp"], reverse=True):
        print(f"{c:40s} | {data['count']:5d} files | Comp: {data['comp']/(1024*1024):8.2f} MB | Uncomp: {data['uncomp']/(1024*1024):8.2f} MB")

if __name__ == "__main__":
    breakdown_sizes()
