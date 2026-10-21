import subprocess
import os
import time
import re
import xml.etree.ElementTree as ET

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
DEV_PHONE = "10BE830QG8000MX"  # Phone
DEV_TABLET = "HNP097HQ"         # Tablet

def run_adb(dev, args, timeout=30):
    cmd = [ADB, "-s", dev] + args
    res = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=timeout)
    return res.stdout.strip(), res.stderr.strip(), res.returncode

def dump_ui(dev, filename):
    run_adb(dev, ["shell", "rm", "-f", "/sdcard/dump.xml"])
    run_adb(dev, ["shell", "uiautomator", "dump", "/sdcard/dump.xml"])
    run_adb(dev, ["pull", "/sdcard/dump.xml", filename])
    try:
        with open(filename, "r", encoding="utf-8", errors="replace") as f:
            content = f.read()
        return ET.fromstring(content)
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
            print(f"[{dev}] Tapping {res_id} at {c}", flush=True)
            run_adb(dev, ["shell", "input", "tap", str(c[0]), str(c[1])])
            time.sleep(1.0)
            return True
    print(f"[{dev}] Node {res_id} not found!", flush=True)
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
    print("==================================================", flush=True)
    print("PAIRING AND CONNECTING THE TWO DEVICES", flush=True)
    print("==================================================", flush=True)

    # 1. Bring MainActivity to foreground on both devices
    for d in [DEV_PHONE, DEV_TABLET]:
        run_adb(d, ["shell", "am", "start", "-n", "org.itantra.app/org.itantra.MainActivity"])
    time.sleep(1.5)

    phone_ip = get_ip(DEV_PHONE)
    tablet_ip = get_ip(DEV_TABLET)
    print(f"Phone  IP (Server Receiver): {phone_ip}", flush=True)
    print(f"Tablet IP (Client Transmitter): {tablet_ip}", flush=True)

    # 2. Set Phone as Server (Receiver)
    root_phone = dump_ui(DEV_PHONE, "scratch/phone_state.xml")
    tap_node(DEV_PHONE, root_phone, "btn_mode_server")
    time.sleep(1)

    # 3. Set Tablet as Client (Transmitter)
    root_tab = dump_ui(DEV_TABLET, "scratch/tab_state.xml")
    tap_node(DEV_TABLET, root_tab, "btn_mode_client")
    time.sleep(1)

    # 4. On Tablet, clear text in et_peer_ip and input Server IP
    root_tab = dump_ui(DEV_TABLET, "scratch/tab_state.xml")
    ip_node = get_node(root_tab, "et_peer_ip")
    if ip_node:
        c = get_center(ip_node)
        print(f"[Tablet] Tapping IP input field at {c}...", flush=True)
        run_adb(DEV_TABLET, ["shell", "input", "tap", str(c[0]), str(c[1])])
        time.sleep(0.5)
        # Move cursor to end and delete all characters
        run_adb(DEV_TABLET, ["shell", "input", "keyevent", "123"]) # KEYCODE_MOVE_END
        for _ in range(30):
            run_adb(DEV_TABLET, ["shell", "input", "keyevent", "67"]) # KEYCODE_DEL
        print(f"[Tablet] Typing server IP {phone_ip}...", flush=True)
        run_adb(DEV_TABLET, ["shell", "input", "text", phone_ip])
        time.sleep(0.5)

    # 5. Tap Connect on Tablet
    root_tab = dump_ui(DEV_TABLET, "scratch/tab_state.xml")
    tap_node(DEV_TABLET, root_tab, "btn_connect_peer")
    print("Initiated peer connection...", flush=True)

    # 6. Wait for connection handshake and verify
    time.sleep(3.0)
    root_p_connected = dump_ui(DEV_PHONE, "scratch/phone_connected.xml")
    root_t_connected = dump_ui(DEV_TABLET, "scratch/tablet_connected.xml")

    p_conn = get_node(root_p_connected, "tv_connection_state")
    t_conn = get_node(root_t_connected, "tv_connection_state")

    p_text = p_conn.get("text", "") if p_conn else ""
    t_text = t_conn.get("text", "") if t_conn else ""

    print(f"\nDevice A (Phone)  Connection State: '{p_text}'", flush=True)
    print(f"Device B (Tablet) Connection State: '{t_text}'", flush=True)

    # Save final screenshots
    run_adb(DEV_PHONE, ["shell", "screencap", "-p", "/sdcard/connected_phone.png"])
    run_adb(DEV_PHONE, ["pull", "/sdcard/connected_phone.png", "scratch/connected_phone.png"])
    run_adb(DEV_TABLET, ["shell", "screencap", "-p", "/sdcard/connected_tablet.png"])
    run_adb(DEV_TABLET, ["pull", "/sdcard/connected_tablet.png", "scratch/connected_tablet.png"])
    print("Saved connected screenshots to scratch/connected_phone.png and scratch/connected_tablet.png", flush=True)

if __name__ == "__main__":
    main()
