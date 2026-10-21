import subprocess
import re

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def check_ui(dev_id, name):
    subprocess.run([ADB, "-s", dev_id, "shell", "uiautomator dump /sdcard/dump.xml"])
    subprocess.run([ADB, "-s", dev_id, "pull", "/sdcard/dump.xml", f"scratch/{name}_dump.xml"], capture_output=True)
    with open(f"scratch/{name}_dump.xml", "r", encoding="utf-8", errors="ignore") as f:
        xml = f.read()
    
    m_ip = re.search(r'resource-id="org.itantra.app:id/et_peer_ip"[^>]*text="([^"]*)"', xml)
    m_state = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]*)"', xml)
    m_err = re.search(r'resource-id="org.itantra.app:id/tv_last_error"[^>]*text="([^"]*)"', xml)
    
    print(f"=== {name} ===")
    print("et_peer_ip:", repr(m_ip.group(1) if m_ip else "NOT FOUND"))
    print("tv_connection_state:", repr(m_state.group(1) if m_state else "NOT FOUND"))
    print("tv_last_error:", repr(m_err.group(1) if m_err else "None"))

check_ui(PHONE, "phone")
check_ui(TABLET, "tablet")
