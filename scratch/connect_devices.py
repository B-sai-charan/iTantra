import subprocess
import os
import time
import re
import xml.etree.ElementTree as ET

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
DEV_PHONE = "10BE830QG8000MX"  # Phone (Server)
DEV_TABLET = "HNP097HQ"         # Tablet (Client)

def run_adb(dev, args, timeout=30):
    cmd = [ADB, "-s", dev] + args
    res = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=timeout)
    return res.stdout.strip(), res.stderr.strip(), res.returncode

def dump_ui(dev):
    run_adb(dev, ["shell", "rm", "-f", "/sdcard/connect_dump.xml"])
    run_adb(dev, ["shell", "uiautomator", "dump", "/sdcard/connect_dump.xml"])
    out, _, _ = run_adb(dev, ["shell", "cat", "/sdcard/connect_dump.xml"])
    try:
        return ET.fromstring(out)
    except Exception as e:
        print(f"Error parsing XML for {dev}: {e}")
        return None

def get_node(root, res_id):
    if root is None:
        return None
    for n in root.iter("node"):
        if res_id in n.attrib.get("resource-id", ""):
            return n.attrib
    return None

def get_center(attrib):
    if not attrib:
        return None
    bounds = attrib.get("bounds", "")
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def tap_node(dev, root, res_id):
    node = get_node(root, res_id)
    if node:
        c = get_center(node)
        if c:
            print(f"[{dev}] Tapping {res_id} at {c}")
            run_adb(dev, ["shell", "input", "tap", str(c[0]), str(c[1])])
            time.sleep(1.0)
            return True
    print(f"[{dev}] Node {res_id} not found to tap!")
    return False

def get_ip(dev):
    out, _, _ = run_adb(dev, ["shell", "ip -4 addr show wlan0"])
    m = re.search(r'inet\s+(\d+\.\d+\.\d+\.\d+)', out)
    if m:
        return m.group(1)
    out, _, _ = run_adb(dev, ["shell", "ip route"])
    m = re.search(r'src\s+(\d+\.\d+\.\d+\.\d+)', out)
    return m.group(1) if m else None

def main():
    print("==================================================")
    print("CONNECTING PHONE AND TABLET")
    print("==================================================")

    # Bring both to foreground
    print("1. Launching/Foregorunding MainActivity on both devices...")
    for d in [DEV_PHONE, DEV_TABLET]:
        run_adb(d, ["shell", "am", "start", "-n", "org.itantra.app/org.itantra.MainActivity"])
    time.sleep(2)

    phone_ip = get_ip(DEV_PHONE)
    tablet_ip = get_ip(DEV_TABLET)
    print(f"Phone  (Server target) IP: {phone_ip}")
    print(f"Tablet (Client source) IP: {tablet_ip}")

    # Set Phone as Server (Receiver)
    print("\n2. Setting Phone as Server (Receiver)...")
    root_phone = dump_ui(DEV_PHONE)
    tap_node(DEV_PHONE, root_phone, "btn_mode_server")
    time.sleep(1)

    # Set Tablet as Client (Transmitter)
    print("\n3. Setting Tablet as Client (Transmitter)...")
    root_tab = dump_ui(DEV_TABLET)
    tap_node(DEV_TABLET, root_tab, "btn_mode_client")
    time.sleep(1)

    # Input Phone IP on Tablet and connect
    print(f"\n4. Entering Server IP ({phone_ip}) on Tablet and connecting...")
    root_tab = dump_ui(DEV_TABLET)
    ip_node = get_node(root_tab, "et_peer_ip")
    if ip_node:
        c = get_center(ip_node)
        print(f"[Tablet] Tapping et_peer_ip at {c}...")
        run_adb(DEV_TABLET, ["shell", "input", "tap", str(c[0]), str(c[1])])
        time.sleep(0.5)
        # Select all and delete
        run_adb(DEV_TABLET, ["shell", "input", "keyevent", "--longpress", "KEYCODE_CLEAR"])
        # Clear field via backspaces
        for _ in range(25):
            run_adb(DEV_TABLET, ["shell", "input", "keyevent", "KEYCODE_DEL"])
        # Type IP
        run_adb(DEV_TABLET, ["shell", "input", "text", phone_ip])
        time.sleep(0.5)
    
    # Tap Connect on Tablet
    root_tab = dump_ui(DEV_TABLET)
    tap_node(DEV_TABLET, root_tab, "btn_connect_peer")
    print("Connection command issued. Waiting for socket handshake...")
    time.sleep(3.0)

    # Check connection states
    print("\n5. Checking Connection State on both devices...")
    root_phone_after = dump_ui(DEV_PHONE)
    root_tab_after = dump_ui(DEV_TABLET)

    phone_state = get_node(root_phone_after, "tv_connection_state")
    tablet_state = get_node(root_tab_after, "tv_connection_state")

    phone_text = phone_state.get("text", "") if phone_state else "UNKNOWN"
    tablet_text = tablet_state.get("text", "") if tablet_state else "UNKNOWN"

    print(f"Phone  Connection State: '{phone_text}'")
    print(f"Tablet Connection State: '{tablet_text}'")

    # Capture screenshots
    run_adb(DEV_PHONE, ["shell", "screencap", "-p", "/sdcard/connected_phone.png"])
    run_adb(DEV_PHONE, ["pull", "/sdcard/connected_phone.png", "scratch/connected_phone.png"])
    run_adb(DEV_PHONE, ["shell", "rm", "/sdcard/connected_phone.png"])

    run_adb(DEV_TABLET, ["shell", "screencap", "-p", "/sdcard/connected_tablet.png"])
    run_adb(DEV_TABLET, ["pull", "/sdcard/connected_tablet.png", "scratch/connected_tablet.png"])
    run_adb(DEV_TABLET, ["shell", "rm", "/sdcard/connected_tablet.png"])

    print("\nSaved screenshots to scratch/connected_phone.png and scratch/connected_tablet.png")

if __name__ == "__main__":
    main()
