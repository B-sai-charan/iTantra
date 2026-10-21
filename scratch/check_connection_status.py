import subprocess
import os
import xml.etree.ElementTree as ET

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")

def check(dev, label):
    subprocess.run([ADB, "-s", dev, "shell", "uiautomator", "dump", "/sdcard/dump.xml"])
    out = subprocess.run([ADB, "-s", dev, "shell", "cat", "/sdcard/dump.xml"], capture_output=True, text=True).stdout
    try:
        root = ET.fromstring(out)
        print(f"=== {label} ({dev}) ===")
        for n in root.iter("node"):
            rid = n.attrib.get("resource-id", "")
            if any(k in rid for k in ["et_peer_ip", "tv_connection_state", "tv_status", "btn_connect_peer", "tv_server_role_hint"]):
                print(f"  {rid.split('/')[-1]:25s} -> '{n.attrib.get('text')}'")
    except Exception as e:
        print(f"Failed to parse: {e}")

check("10BE830QG8000MX", "Phone")
check("HNP097HQ", "Tablet")
