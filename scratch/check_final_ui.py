import subprocess
import os
import sys
import xml.etree.ElementTree as ET

if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
DEV_A = "10BE830QG8000MX"
DEV_B = "HNP097HQ"

def check_ui(dev, label):
    subprocess.run([ADB, "-s", dev, "shell", "rm", "-f", "/sdcard/dump.xml"])
    subprocess.run([ADB, "-s", dev, "shell", "uiautomator", "dump", "/sdcard/dump.xml"])
    subprocess.run([ADB, "-s", dev, "pull", "/sdcard/dump.xml", f"scratch/{label}_final.xml"])
    subprocess.run([ADB, "-s", dev, "shell", "screencap", "-p", "/sdcard/screen.png"])
    subprocess.run([ADB, "-s", dev, "pull", "/sdcard/screen.png", f"scratch/{label}_final.png"])
    
    t = ET.parse(f"scratch/{label}_final.xml")
    print(f"=== {label} ({dev}) UI STATE ===")
    for e in t.getroot().iter():
        rid = e.attrib.get('resource-id', '')
        if rid:
            sid = rid.split('/')[-1]
            if sid in ['tv_title', 'tv_connection_state', 'tv_you_transcript', 'tv_you_lang_badge', 
                       'tv_remote_transcript', 'tv_remote_lang_badge', 'tv_remote_playback_status', 
                       'tv_status', 'tv_last_error']:
                print(f"  {sid}: text={repr(e.attrib.get('text', ''))} bounds={e.attrib.get('bounds')}")

check_ui(DEV_A, "dev_a_e2e")
check_ui(DEV_B, "dev_b_e2e")
