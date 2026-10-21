import subprocess
import time
import os
import xml.etree.ElementTree as ET

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"
PKG = "org.itantra.app"
COMPONENT = f"{PKG}/org.itantra.MainActivity"
OUT_DIR = r"c:\Users\saich\Downloads\SIH\PS26173\scratch\uiux_phase2"

def run_adb(device_id, args):
    cmd = [ADB, "-s", device_id] + args
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout

def get_node_info(device_id, resource_id):
    run_adb(device_id, ["shell", "uiautomator", "dump", "/sdcard/dump.xml"])
    xml_data = run_adb(device_id, ["shell", "cat", "/sdcard/dump.xml"])
    if not xml_data.strip():
        return None
    try:
        root = ET.fromstring(xml_data)
        for node in root.iter("node"):
            if node.attrib.get("resource-id") == resource_id:
                text = node.attrib.get("text", "")
                bounds = node.attrib.get("bounds", "")
                b = bounds.replace("][", ",").replace("[", "").replace("]", "").split(",")
                x1, y1, x2, y2 = int(b[0]), int(b[1]), int(b[2]), int(b[3])
                return {"text": text, "x": (x1 + x2) // 2, "y": (y1 + y2) // 2}
    except Exception as e:
        print(f"Error parsing xml: {e}")
    return None

def tap_node(device_id, resource_id):
    info = get_node_info(device_id, resource_id)
    if info:
        run_adb(device_id, ["shell", "input", "tap", str(info["x"]), str(info["y"])])
        time.sleep(1)
        return True
    return False

def capture(device_id, filename):
    remote = f"/sdcard/{filename}"
    local = os.path.join(OUT_DIR, filename)
    run_adb(device_id, ["shell", "screencap", "-p", remote])
    subprocess.run([ADB, "-s", device_id, "pull", remote, local], capture_output=True)
    run_adb(device_id, ["shell", "rm", remote])
    print(f"Saved {local}")

# 1. Start Phone Receiver and wait for initialization
print("Starting Phone Receiver...")
run_adb(PHONE, ["shell", "am", "force-stop", PKG])
run_adb(PHONE, ["shell", "am", "start", "-n", COMPONENT])
time.sleep(6) # allow full neural model initialization

# 2. Start Tablet Transmitter, wait for initialization
print("Starting Tablet Transmitter...")
run_adb(TABLET, ["shell", "am", "force-stop", PKG])
run_adb(TABLET, ["shell", "am", "start", "-n", COMPONENT])
time.sleep(6) # allow full neural model initialization

tap_node(TABLET, "org.itantra.app:id/btn_mode_client")
time.sleep(1)

ip_info = get_node_info(TABLET, "org.itantra.app:id/et_peer_ip")
if ip_info:
    run_adb(TABLET, ["shell", "input", "tap", str(ip_info["x"]), str(ip_info["y"])])
    time.sleep(0.3)
    for _ in range(25):
        run_adb(TABLET, ["shell", "input", "keyevent", "67"])
    run_adb(TABLET, ["shell", "input", "text", "172.20.105.179"])
    time.sleep(0.5)

tap_node(TABLET, "org.itantra.app:id/btn_connect_peer")
time.sleep(6) # allow composition connect & ready

capture(TABLET, "05_connected.png")

# Start PTT
print("Tapping PTT on Tablet...")
tap_node(TABLET, "org.itantra.app:id/btn_ptt")
time.sleep(2)
capture(TABLET, "07_ptt_active.png")

# Stop PTT
tap_node(TABLET, "org.itantra.app:id/btn_ptt")
time.sleep(1)

print("Done capturing 05_connected.png and 07_ptt_active.png")
