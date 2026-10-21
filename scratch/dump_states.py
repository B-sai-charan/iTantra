import subprocess
import os
import xml.etree.ElementTree as ET

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")

for dev, name in [("10BE830QG8000MX", "Phone (Server)"), ("HNP097HQ", "Tablet (Client)")]:
    subprocess.run([ADB, "-s", dev, "shell", "uiautomator", "dump", "/sdcard/st_dump.xml"], capture_output=True)
    subprocess.run([ADB, "-s", dev, "pull", "/sdcard/st_dump.xml", f"scratch/{dev}_st.xml"], capture_output=True)
    with open(f"scratch/{dev}_st.xml", "r", encoding="utf-8", errors="replace") as f:
        tree = ET.fromstring(f.read())
    print(f"=== {name} ({dev}) ===")
    for n in tree.iter("node"):
        rid = n.attrib.get("resource-id", "")
        if any(k in rid for k in ["tv_connection_state", "tv_status", "tv_server_role_hint", "et_peer_ip", "btn_connect_peer", "tv_you_transcript", "tv_remote_transcript"]):
            print(f"  {rid.split('/')[-1]:25s} -> \"{n.attrib.get('text')}\"")
