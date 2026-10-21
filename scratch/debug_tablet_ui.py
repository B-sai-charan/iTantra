import subprocess

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
TABLET = "HNP097HQ"
PHONE = "10BE830QG8000MX"

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip()

print("Tablet Activity:")
print(adb_shell(TABLET, "dumpsys activity activities | grep -E 'ResumedActivity|topResumedActivity'"))

# Pull UI hierarchy
adb_shell(TABLET, "uiautomator dump /sdcard/dump.xml")
subprocess.run([ADB, "-s", TABLET, "pull", "/sdcard/dump.xml", "scratch/tablet_ui.xml"], capture_output=True)
with open("scratch/tablet_ui.xml", "r", encoding="utf-8", errors="ignore") as f:
    content = f.read()
print("\nTablet UI Hierarchy length:", len(content))
print("First 500 chars:", content[:500])
