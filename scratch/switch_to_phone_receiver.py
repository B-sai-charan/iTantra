import subprocess
import time
import os
import re
import xml.etree.ElementTree as ET

ADB_PATH = os.path.expandvars(r"$LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def adb(serial, cmd):
    full_cmd = f'"{ADB_PATH}" -s {serial} {cmd}'
    res = subprocess.run(full_cmd, shell=True, capture_output=True, encoding='utf-8', errors='replace')
    return (res.stdout or "").strip()

def get_node_info(serial, res_id):
    adb(serial, "shell uiautomator dump /sdcard/dump.xml")
    xml_str = adb(serial, "shell cat /sdcard/dump.xml")
    try:
        root = ET.fromstring(xml_str)
        for elem in root.iter():
            if elem.attrib.get('resource-id') == f"org.itantra.app:id/{res_id}":
                bounds = elem.attrib.get('bounds')
                m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
                center = None
                if m:
                    x1, y1, x2, y2 = map(int, m.groups())
                    center = ((x1 + x2) // 2, (y1 + y2) // 2)
                return center, elem.attrib.get('text', ''), elem.attrib.get('displayed', 'true')
    except Exception as e:
        print(f"Error finding {res_id} on {serial}: {e}")
    return None, "", "false"

# 1. Discover active IPs
tab_ip_raw = adb(TABLET, "shell ip -4 addr show wlan0")
m_tab = re.search(r'inet\s+([0-9.]+)/\d+', tab_ip_raw)
tablet_ip = m_tab.group(1) if m_tab else "172.20.38.186"

phone_ip_raw = adb(PHONE, "shell ip -4 addr show wlan0")
m_phone = re.search(r'inet\s+([0-9.]+)/\d+', phone_ip_raw)
phone_ip = m_phone.group(1) if m_phone else "172.20.105.179"

print(f"Discovered Phone IP: {phone_ip}")
print(f"Discovered Tablet IP: {tablet_ip}")

# 2. Set Phone as Receiver
print("Setting Phone (10BE830QG8000MX) as Receiver...")
c_phone_rec, _, _ = get_node_info(PHONE, "btn_mode_server")
if c_phone_rec:
    adb(PHONE, f"shell input tap {c_phone_rec[0]} {c_phone_rec[1]}")
    time.sleep(1.5)

_, phone_role_hint, _ = get_node_info(PHONE, "tv_server_role_hint")
_, phone_conn_state, _ = get_node_info(PHONE, "tv_connection_state")
print(f"Phone Role Hint: {phone_role_hint}")
print(f"Phone Connection State: {phone_conn_state}")

# 3. Set Tablet as Transmitter and connect to Phone IP
print(f"Setting Tablet (HNP097HQ) as Transmitter pointing to {phone_ip}...")
c_tab_tx, _, _ = get_node_info(TABLET, "btn_mode_client")
if c_tab_tx:
    adb(TABLET, f"shell input tap {c_tab_tx[0]} {c_tab_tx[1]}")
    time.sleep(1)

c_tab_ip, _, _ = get_node_info(TABLET, "et_peer_ip")
if c_tab_ip:
    adb(TABLET, f"shell input tap {c_tab_ip[0]} {c_tab_ip[1]}")
    time.sleep(0.3)
    for _ in range(25):
        adb(TABLET, "shell input keyevent KEYCODE_DEL")
    adb(TABLET, f"shell input text {phone_ip}")
    time.sleep(0.5)

c_tab_conn, _, _ = get_node_info(TABLET, "btn_connect_peer")
if c_tab_conn:
    adb(TABLET, f"shell input tap {c_tab_conn[0]} {c_tab_conn[1]}")
    time.sleep(2.5)

# 4. Check Connection States
_, tab_conn_state, _ = get_node_info(TABLET, "tv_connection_state")
_, tab_err, _ = get_node_info(TABLET, "tv_last_error")
_, phone_conn_state_after, _ = get_node_info(PHONE, "tv_connection_state")

print(f"Tablet Connection State: {tab_conn_state}")
print(f"Tablet Error Banner: {tab_err if tab_err else 'None (Clean)'}")
print(f"Phone Connection State: {phone_conn_state_after}")

# 5. Live Speech Test: Tablet -> Phone
print("Testing live speech from Tablet to Phone...")
c_tab_ptt, _, _ = get_node_info(TABLET, "btn_ptt")
if c_tab_ptt:
    adb(TABLET, f"shell input tap {c_tab_ptt[0]} {c_tab_ptt[1]}")
    time.sleep(3.5)
    adb(TABLET, f"shell input tap {c_tab_ptt[0]} {c_tab_ptt[1]}")
    time.sleep(2.5)

_, tab_you, _ = get_node_info(TABLET, "tv_you_transcript")
_, phone_remote, _ = get_node_info(PHONE, "tv_remote_transcript")
_, phone_tts_status, _ = get_node_info(PHONE, "tv_remote_playback_status")

print(f"Tablet Transmitted (YOU): {tab_you}")
print(f"Phone Received (REMOTE): {phone_remote}")
print(f"Phone TTS Playback: {repr(phone_tts_status)}")
print("Role switch and E2E verification complete!")
