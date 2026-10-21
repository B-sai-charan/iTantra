import subprocess
import time
import re
import os
import xml.etree.ElementTree as ET

ADB_PATH = os.path.expandvars(r"$LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def adb(serial, cmd):
    full_cmd = f'"{ADB_PATH}" -s {serial} {cmd}'
    res = subprocess.run(full_cmd, shell=True, capture_output=True, text=True)
    return res.stdout.strip()

def dump_ui(serial):
    adb(serial, "shell uiautomator dump /sdcard/dump.xml")
    xml_str = adb(serial, "shell cat /sdcard/dump.xml")
    try:
        return ET.fromstring(xml_str)
    except Exception as e:
        print(f"Error parsing XML from {serial}: {e}")
        return None

def find_node_by_id(root, res_id):
    if root is None:
        return None
    for elem in root.iter():
        if elem.attrib.get('resource-id') == f"org.itantra.app:id/{res_id}":
            return elem
    return None

def get_bounds_center(node):
    if node is None:
        return None
    bounds = node.attrib.get('bounds')
    if not bounds:
        return None
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return ((x1 + x2) // 2, (y1 + y2) // 2)
    return None

def click_id(serial, res_id):
    root = dump_ui(serial)
    node = find_node_by_id(root, res_id)
    if node is not None:
        center = get_bounds_center(node)
        if center:
            adb(serial, f"shell input tap {center[0]} {center[1]}")
            time.sleep(0.8)
            return True
    return False

def get_text_by_id(serial, res_id):
    root = dump_ui(serial)
    node = find_node_by_id(root, res_id)
    if node is not None:
        return node.attrib.get('text', '')
    return None

def clear_input(serial, res_id):
    click_id(serial, res_id)
    time.sleep(0.3)
    # send keyevent backspace multiple times or select all
    for _ in range(25):
        adb(serial, "shell input keyevent KEYCODE_DEL")

def set_input_text(serial, res_id, text):
    click_id(serial, res_id)
    time.sleep(0.3)
    for _ in range(25):
        adb(serial, "shell input keyevent KEYCODE_DEL")
    adb(serial, f"shell input text {text}")
    time.sleep(0.5)

print("=== Starting Post-UI/UX Functional Regression Audit ===")

# Discover IPs
phone_ip_raw = adb(PHONE, "shell ip -4 addr show wlan0")
phone_ip_m = re.search(r'inet\s+([0-9.]+)/\d+', phone_ip_raw)
phone_ip = phone_ip_m.group(1) if phone_ip_m else "Unknown"

tablet_ip_raw = adb(TABLET, "shell ip -4 addr show wlan0")
tablet_ip_m = re.search(r'inet\s+([0-9.]+)/\d+', tablet_ip_raw)
tablet_ip = tablet_ip_m.group(1) if tablet_ip_m else "Unknown"

print(f"Discovered Phone IP: {phone_ip}")
print(f"Discovered Tablet IP: {tablet_ip}")

# Test 1: Role Switching on Phone
print("\n--- Testing Role Switching on Phone ---")
click_id(PHONE, "btn_role_receiver")
time.sleep(1)
phone_status_rec = get_text_by_id(PHONE, "tv_connection_status")
print(f"Phone as Receiver status: {phone_status_rec}")

click_id(PHONE, "btn_role_transmitter")
time.sleep(1)
phone_status_tx = get_text_by_id(PHONE, "tv_connection_status")
print(f"Phone as Transmitter status: {phone_status_tx}")

# Test 2: Role Switching on Tablet
print("\n--- Testing Role Switching on Tablet ---")
click_id(TABLET, "btn_role_receiver")
time.sleep(1)
tab_status_rec = get_text_by_id(TABLET, "tv_connection_status")
print(f"Tablet as Receiver status: {tab_status_rec}")

click_id(TABLET, "btn_role_transmitter")
time.sleep(1)
tab_status_tx = get_text_by_id(TABLET, "tv_connection_status")
print(f"Tablet as Transmitter status: {tab_status_tx}")

# Test 3: Theme Toggle & State Persistence on Phone
print("\n--- Testing Theme Toggle on Phone ---")
t1 = get_text_by_id(PHONE, "btn_theme_toggle")
print(f"Initial theme toggle text: {t1}")
click_id(PHONE, "btn_theme_toggle")
time.sleep(1)
t2 = get_text_by_id(PHONE, "btn_theme_toggle")
print(f"After 1st toggle: {t2}")
click_id(PHONE, "btn_theme_toggle")
time.sleep(1)
t3 = get_text_by_id(PHONE, "btn_theme_toggle")
print(f"After 2nd toggle: {t3}")

# Test 4: Error Recovery Test on Tablet
print("\n--- Testing Error Recovery on Tablet (Transmitter) ---")
click_id(TABLET, "btn_role_transmitter")
set_input_text(TABLET, "et_peer_ip", "192.0.2.1")
click_id(TABLET, "btn_connect")
time.sleep(2)
err_text = get_text_by_id(TABLET, "tv_error_banner")
conn_status = get_text_by_id(TABLET, "tv_connection_status")
print(f"Error banner text: {err_text}")
print(f"Connection status: {conn_status}")

# Test 5: Topology 1 (Tablet = Receiver, Phone = Transmitter)
print("\n--- Testing Topology 1: Tablet = Receiver, Phone = Transmitter ---")
click_id(TABLET, "btn_role_receiver")
time.sleep(1)
tab_rec_status = get_text_by_id(TABLET, "tv_connection_status")
print(f"Tablet Receiver Status: {tab_rec_status}")

click_id(PHONE, "btn_role_transmitter")
time.sleep(0.5)
set_input_text(PHONE, "et_peer_ip", tablet_ip)
click_id(PHONE, "btn_connect")
time.sleep(2)
phone_tx_status = get_text_by_id(PHONE, "tv_connection_status")
tab_rec_status2 = get_text_by_id(TABLET, "tv_connection_status")
print(f"Phone Transmitter Status: {phone_tx_status}")
print(f"Tablet Receiver Status: {tab_rec_status2}")

# Test PTT on Phone
print("\n--- Testing PTT on Phone ---")
click_id(PHONE, "btn_ptt")
time.sleep(1)
phone_ptt_label = get_text_by_id(PHONE, "tv_ptt_label")
print(f"Phone PTT label during click: {phone_ptt_label}")
click_id(PHONE, "btn_ptt")
time.sleep(2)
phone_ptt_ready = get_text_by_id(PHONE, "tv_status")
print(f"Phone status after PTT stop: {phone_ptt_ready}")

# Test 6: Topology 2 (Phone = Receiver, Tablet = Transmitter)
print("\n--- Testing Topology 2: Phone = Receiver, Tablet = Transmitter ---")
click_id(PHONE, "btn_role_receiver")
time.sleep(1)
phone_rec_status = get_text_by_id(PHONE, "tv_connection_status")
print(f"Phone Receiver Status: {phone_rec_status}")

click_id(TABLET, "btn_role_transmitter")
time.sleep(0.5)
set_input_text(TABLET, "et_peer_ip", phone_ip)
click_id(TABLET, "btn_connect")
time.sleep(2)
tab_tx_status = get_text_by_id(TABLET, "tv_connection_status")
phone_rec_status2 = get_text_by_id(PHONE, "tv_connection_status")
print(f"Tablet Transmitter Status: {tab_tx_status}")
print(f"Phone Receiver Status: {phone_rec_status2}")

# Test PTT on Tablet
print("\n--- Testing PTT on Tablet ---")
click_id(TABLET, "btn_ptt")
time.sleep(1)
tab_ptt_label = get_text_by_id(TABLET, "tv_ptt_label")
print(f"Tablet PTT label during click: {tab_ptt_label}")
click_id(TABLET, "btn_ptt")
time.sleep(2)
tab_ptt_ready = get_text_by_id(TABLET, "tv_status")
print(f"Tablet status after PTT stop: {tab_ptt_ready}")

print("\n=== Check Logcat for Crashes / ANRs ===")
phone_crashes = adb(PHONE, "logcat -d -s AndroidRuntime:E FATAL:E ActivityManager:E | grep -iE 'crash|fatal|anr' | tail -n 10")
tablet_crashes = adb(TABLET, "logcat -d -s AndroidRuntime:E FATAL:E ActivityManager:E | grep -iE 'crash|fatal|anr' | tail -n 10")
print(f"Phone crash check: {phone_crashes if phone_crashes else 'None (0 crashes)'}")
print(f"Tablet crash check: {tablet_crashes if tablet_crashes else 'None (0 crashes)'}")
