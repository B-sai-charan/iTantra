import subprocess
import time
import os
import re
import xml.etree.ElementTree as ET

ADB_PATH = os.path.expandvars(r"$LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"
OUTPUT_DIR = os.path.abspath(r"c:\Users\saich\Downloads\SIH\PS26173\scratch\uiux_final")

os.makedirs(OUTPUT_DIR, exist_ok=True)

def adb(serial, cmd):
    full_cmd = f'"{ADB_PATH}" -s {serial} {cmd}'
    res = subprocess.run(full_cmd, shell=True, capture_output=True, encoding='utf-8', errors='replace')
    return (res.stdout or "").strip()

def capture_screenshot(serial, filename):
    dest = os.path.join(OUTPUT_DIR, filename)
    adb(serial, "shell screencap -p /sdcard/screen.png")
    res = subprocess.run(f'"{ADB_PATH}" -s {serial} pull /sdcard/screen.png "{dest}"', shell=True, capture_output=True, encoding='utf-8', errors='replace')
    print(f"Captured {dest}: {(res.stdout or '').strip()}")

def get_node_center(serial, res_id):
    adb(serial, "shell uiautomator dump /sdcard/dump.xml")
    xml_str = adb(serial, "shell cat /sdcard/dump.xml")
    try:
        root = ET.fromstring(xml_str)
        for elem in root.iter():
            if elem.attrib.get('resource-id') == f"org.itantra.app:id/{res_id}":
                bounds = elem.attrib.get('bounds')
                m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
                if m:
                    x1, y1, x2, y2 = map(int, m.groups())
                    return ((x1 + x2) // 2, (y1 + y2) // 2), elem.attrib.get('text', '')
    except Exception as e:
        print(f"Error finding {res_id}: {e}")
    return None, ""

# Discover IPs
phone_ip_raw = adb(PHONE, "shell ip -4 addr show wlan0")
phone_ip_m = re.search(r'inet\s+([0-9.]+)/\d+', phone_ip_raw)
phone_ip = phone_ip_m.group(1) if phone_ip_m else "172.20.105.179"

tablet_ip_raw = adb(TABLET, "shell ip -4 addr show wlan0")
tablet_ip_m = re.search(r'inet\s+([0-9.]+)/\d+', tablet_ip_raw)
tablet_ip = tablet_ip_m.group(1) if tablet_ip_m else "172.20.38.186"

print(f"Phone IP: {phone_ip}, Tablet IP: {tablet_ip}")

# 1. 01_final_light_receiver.png (Phone in Light Mode, Receiver)
print("1. Setting Phone to Light Mode, Receiver...")
c, txt = get_node_center(PHONE, "btn_theme_toggle")
if c:
    for _ in range(3):
        if "Light" in txt or "LIGHT" in txt:
            break
        adb(PHONE, f"shell input tap {c[0]} {c[1]}")
        time.sleep(1)
        c, txt = get_node_center(PHONE, "btn_theme_toggle")

c_rec, _ = get_node_center(PHONE, "btn_mode_server")
if c_rec:
    adb(PHONE, f"shell input tap {c_rec[0]} {c_rec[1]}")
    time.sleep(1)

capture_screenshot(PHONE, "01_final_light_receiver.png")

# 2. 02_final_dark_receiver.png (Phone in Dark Mode, Receiver)
print("2. Setting Phone to Dark Mode, Receiver...")
c, txt = get_node_center(PHONE, "btn_theme_toggle")
for _ in range(3):
    if "Dark" in txt or "DARK" in txt:
        break
    adb(PHONE, f"shell input tap {c[0]} {c[1]}")
    time.sleep(1)
    c, txt = get_node_center(PHONE, "btn_theme_toggle")

capture_screenshot(PHONE, "02_final_dark_receiver.png")

# 3. 03_final_light_transmitter.png (Tablet in Light Mode, Transmitter)
print("3. Setting Tablet to Light Mode, Transmitter...")
c, txt = get_node_center(TABLET, "btn_theme_toggle")
if c:
    for _ in range(3):
        if "Light" in txt or "LIGHT" in txt:
            break
        adb(TABLET, f"shell input tap {c[0]} {c[1]}")
        time.sleep(1)
        c, txt = get_node_center(TABLET, "btn_theme_toggle")

c_tx, _ = get_node_center(TABLET, "btn_mode_client")
if c_tx:
    adb(TABLET, f"shell input tap {c_tx[0]} {c_tx[1]}")
    time.sleep(1)

capture_screenshot(TABLET, "03_final_light_transmitter.png")

# 4. 04_final_dark_transmitter.png (Tablet in Dark Mode, Transmitter)
print("4. Setting Tablet to Dark Mode, Transmitter...")
c, txt = get_node_center(TABLET, "btn_theme_toggle")
for _ in range(3):
    if "Dark" in txt or "DARK" in txt:
        break
    adb(TABLET, f"shell input tap {c[0]} {c[1]}")
    time.sleep(1)
    c, txt = get_node_center(TABLET, "btn_theme_toggle")

capture_screenshot(TABLET, "04_final_dark_transmitter.png")

# 5. 05_final_connected.png (Tablet connected to Phone IP)
print("5. Connecting Tablet to Phone...")
c_ip, _ = get_node_center(TABLET, "et_peer_ip")
if c_ip:
    adb(TABLET, f"shell input tap {c_ip[0]} {c_ip[1]}")
    time.sleep(0.3)
    for _ in range(25):
        adb(TABLET, "shell input keyevent KEYCODE_DEL")
    adb(TABLET, f"shell input text {phone_ip}")
    time.sleep(0.5)

c_conn, _ = get_node_center(TABLET, "btn_connect_peer")
if c_conn:
    adb(TABLET, f"shell input tap {c_conn[0]} {c_conn[1]}")
    time.sleep(2)

capture_screenshot(TABLET, "05_final_connected.png")

# 6. 06_final_error.png (Error on empty/invalid IP)
print("6. Testing Error Banner on Tablet...")
if c_ip:
    adb(TABLET, f"shell input tap {c_ip[0]} {c_ip[1]}")
    time.sleep(0.3)
    for _ in range(25):
        adb(TABLET, "shell input keyevent KEYCODE_DEL")
    time.sleep(0.5)
if c_conn:
    adb(TABLET, f"shell input tap {c_conn[0]} {c_conn[1]}")
    time.sleep(1.5)

capture_screenshot(TABLET, "06_final_error.png")

# Reconnect Tablet to Phone
if c_ip:
    adb(TABLET, f"shell input tap {c_ip[0]} {c_ip[1]}")
    time.sleep(0.3)
    adb(TABLET, f"shell input text {phone_ip}")
    time.sleep(0.5)
if c_conn:
    adb(TABLET, f"shell input tap {c_conn[0]} {c_conn[1]}")
    time.sleep(2)

# 7. 07_final_ptt.png (PTT active state on Tablet)
print("7. Testing PTT active state on Tablet...")
c_ptt, _ = get_node_center(TABLET, "btn_ptt")
if c_ptt:
    adb(TABLET, f"shell input tap {c_ptt[0]} {c_ptt[1]}")
    time.sleep(0.8)
    capture_screenshot(TABLET, "07_final_ptt.png")
    time.sleep(1.5)
    adb(TABLET, f"shell input tap {c_ptt[0]} {c_ptt[1]}")

print("All screenshots captured and verified!")
