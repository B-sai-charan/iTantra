import os
import sys
import subprocess
import time

ADB_PATH = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
if not os.path.exists(ADB_PATH):
    ADB_PATH = "adb"

DEV_A = "10BE830QG8000MX" # Server IP: 172.20.29.81
DEV_B = "HNP097HQ"         # Client IP: 172.20.84.74

def run_adb(dev, args):
    cmd = [ADB_PATH, "-s", dev] + args
    res = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
    return res.returncode, res.stdout, res.stderr

def get_node_center(attrib):
    bounds = attrib.get('bounds', '')
    import re
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def parse_xml(xml_path):
    import xml.etree.ElementTree as ET
    tree = ET.parse(xml_path)
    nodes = {}
    for elem in tree.getroot().iter():
        rid = elem.attrib.get('resource-id', '')
        if rid:
            short_id = rid.split('/')[-1]
            nodes[short_id] = elem.attrib
    return nodes

def dump_ui(dev, out_path):
    run_adb(dev, ["shell", "rm", "-f", "/sdcard/window_dump.xml"])
    run_adb(dev, ["shell", "uiautomator", "dump", "/sdcard/window_dump.xml"])
    run_adb(dev, ["pull", "/sdcard/window_dump.xml", out_path])

def main():
    print("==========================================")
    print("STEP 4 CONTROLLED LOGCAT TEST")
    print("==========================================")
    
    # 1. Clear logcat on both devices
    run_adb(DEV_A, ["logcat", "-c"])
    run_adb(DEV_B, ["logcat", "-c"])
    
    # DEVICE A: Launch & ensure Server mode
    print("\n--- Device A: Setup Server ---")
    run_adb(DEV_A, ["shell", "am", "start", "-n", "org.itantra.app/org.itantra.MainActivity"])
    time.sleep(2)
    dump_ui(DEV_A, "scratch/dev_a_step4.xml")
    nodes_a = parse_xml("scratch/dev_a_step4.xml")
    if "btn_mode_server" in nodes_a:
        cx, cy = get_node_center(nodes_a["btn_mode_server"])
        run_adb(DEV_A, ["shell", "input", "tap", str(cx), str(cy)])
        print(f"[Device A] Tapped btn_mode_server at ({cx}, {cy})")
    time.sleep(2)
    
    # Check Device A connection status pill
    dump_ui(DEV_A, "scratch/dev_a_server_ready.xml")
    nodes_a_ready = parse_xml("scratch/dev_a_server_ready.xml")
    pill_text = nodes_a_ready.get("tv_connection_state", {}).get("text", "")
    print(f"[Device A] Connection state pill: '{pill_text}'")
    
    # DEVICE B: Launch, select Client, input 172.20.29.81, tap Connect
    print("\n--- Device B: Setup Client & Connect to Device A (172.20.29.81) ---")
    run_adb(DEV_B, ["shell", "am", "start", "-n", "org.itantra.app/org.itantra.MainActivity"])
    time.sleep(2)
    dump_ui(DEV_B, "scratch/dev_b_step4_init.xml")
    nodes_b = parse_xml("scratch/dev_b_step4_init.xml")
    
    # Select Client
    if "btn_mode_client" in nodes_b:
        cx, cy = get_node_center(nodes_b["btn_mode_client"])
        run_adb(DEV_B, ["shell", "input", "tap", str(cx), str(cy)])
        print(f"[Device B] Tapped btn_mode_client at ({cx}, {cy})")
    time.sleep(1)
    
    # Dump again to get et_peer_ip and btn_connect_peer
    dump_ui(DEV_B, "scratch/dev_b_client_mode.xml")
    nodes_b_client = parse_xml("scratch/dev_b_client_mode.xml")
    
    if "et_peer_ip" in nodes_b_client:
        cx, cy = get_node_center(nodes_b_client["et_peer_ip"])
        run_adb(DEV_B, ["shell", "input", "tap", str(cx), str(cy)])
        time.sleep(0.5)
        # Clear existing text with backspaces
        for _ in range(25):
            run_adb(DEV_B, ["shell", "input", "keyevent", "KEYCODE_DEL"])
        # Type IP
        run_adb(DEV_B, ["shell", "input", "text", "172.20.29.81"])
        print("[Device B] Entered IP '172.20.29.81' into et_peer_ip")
    time.sleep(1)
    
    # Tap btn_connect_peer
    dump_ui(DEV_B, "scratch/dev_b_ip_entered.xml")
    nodes_b_ip = parse_xml("scratch/dev_b_ip_entered.xml")
    if "btn_connect_peer" in nodes_b_ip:
        cx, cy = get_node_center(nodes_b_ip["btn_connect_peer"])
        run_adb(DEV_B, ["shell", "input", "tap", str(cx), str(cy)])
        print(f"[Device B] Tapped btn_connect_peer at ({cx}, {cy})")
    time.sleep(3)
    
    # Check Device B state after connect
    dump_ui(DEV_B, "scratch/dev_b_connected.xml")
    nodes_b_conn = parse_xml("scratch/dev_b_connected.xml")
    pill_b = nodes_b_conn.get("tv_connection_state", {}).get("text", "")
    status_b = nodes_b_conn.get("tv_status", {}).get("text", "")
    err_b = nodes_b_conn.get("tv_last_error", {}).get("text", "")
    print(f"[Device B] After Connect: pill='{pill_b}', status='{status_b}', error='{err_b}'")
    
    # Collect logcat from both devices
    print("\n==========================================")
    print("DEVICE A (SERVER) LOGCAT:")
    print("==========================================")
    _, log_a, _ = run_adb(DEV_A, ["logcat", "-d", "-t", "300"])
    for l in log_a.splitlines():
        if any(k in l for k in ["PHYSICAL_E2E", "TcpConnection", "Socket", "ServerSocket", "42173", "Transceiver", "bind", "accept", "connect", "CONNECT"]):
            print("  [A]", l)
            
    print("\n==========================================")
    print("DEVICE B (CLIENT) LOGCAT:")
    print("==========================================")
    _, log_b, _ = run_adb(DEV_B, ["logcat", "-d", "-t", "300"])
    for l in log_b.splitlines():
        if any(k in l for k in ["PHYSICAL_E2E", "TcpConnection", "Socket", "ServerSocket", "42173", "Transceiver", "bind", "accept", "connect", "CONNECT"]):
            print("  [B]", l)

if __name__ == "__main__":
    main()
