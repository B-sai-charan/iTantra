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

def capture_screenshot(serial, filepath):
    adb(serial, "shell screencap -p /sdcard/screen.png")
    subprocess.run(f'"{ADB_PATH}" -s {serial} pull /sdcard/screen.png "{filepath}"', shell=True, capture_output=True, encoding='utf-8', errors='replace')
    print(f"Captured {filepath}")

# 1. Detect IPs
tablet_ip_raw = adb(TABLET, "shell ip -4 addr show wlan0")
m_tab = re.search(r'inet\s+([0-9.]+)/\d+', tablet_ip_raw)
tablet_ip = m_tab.group(1) if m_tab else "Unknown"

phone_ip_raw = adb(PHONE, "shell ip -4 addr show wlan0")
m_phone = re.search(r'inet\s+([0-9.]+)/\d+', phone_ip_raw)
phone_ip = m_phone.group(1) if m_phone else "Unknown"

print(f"=== STEP 1: DETECTED IPs ===")
print(f"Tablet IP (HNP097HQ): {tablet_ip}")
print(f"Phone IP (10BE830QG8000MX): {phone_ip}")

# 2. Configure TABLET as Receiver
print(f"\n=== STEP 2: CONFIGURE TABLET AS RECEIVER ===")
c_tab_rec, txt, _ = get_node_info(TABLET, "btn_mode_server")
if c_tab_rec:
    adb(TABLET, f"shell input tap {c_tab_rec[0]} {c_tab_rec[1]}")
    time.sleep(1.5)

_, tab_conn_state, _ = get_node_info(TABLET, "tv_connection_state")
_, tab_role_hint, _ = get_node_info(TABLET, "tv_server_role_hint")
_, tab_err, _ = get_node_info(TABLET, "tv_last_error")

print(f"Tablet Connection State: {tab_conn_state}")
print(f"Tablet Server Role Hint: {tab_role_hint}")
print(f"Tablet Error Banner: {tab_err if tab_err else 'None (Clean)'}")

# 3. Configure PHONE as Transmitter
print(f"\n=== STEP 3: CONFIGURE PHONE AS TRANSMITTER ===")
c_phone_tx, _, _ = get_node_info(PHONE, "btn_mode_client")
if c_phone_tx:
    adb(PHONE, f"shell input tap {c_phone_tx[0]} {c_phone_tx[1]}")
    time.sleep(1)

# Enter Tablet IP on Phone
c_phone_ip, _, _ = get_node_info(PHONE, "et_peer_ip")
if c_phone_ip:
    adb(PHONE, f"shell input tap {c_phone_ip[0]} {c_phone_ip[1]}")
    time.sleep(0.3)
    for _ in range(25):
        adb(PHONE, "shell input keyevent KEYCODE_DEL")
    adb(PHONE, f"shell input text {tablet_ip}")
    time.sleep(0.5)

c_phone_conn, _, _ = get_node_info(PHONE, "btn_connect_peer")
if c_phone_conn:
    adb(PHONE, f"shell input tap {c_phone_conn[0]} {c_phone_conn[1]}")
    time.sleep(2.5)

# 4. Verify Connection States on Both
print(f"\n=== STEP 4: VERIFY CONNECTION ON BOTH DEVICES ===")
_, phone_conn_state, _ = get_node_info(PHONE, "tv_connection_state")
_, phone_err, _ = get_node_info(PHONE, "tv_last_error")
_, tab_conn_state_after, _ = get_node_info(TABLET, "tv_connection_state")

print(f"Phone Connection State: {phone_conn_state}")
print(f"Phone Error Banner: {phone_err if phone_err else 'None (Clean)'}")
print(f"Tablet Connection State: {tab_conn_state_after}")

# 5. Capture Screenshots
print(f"\n=== STEP 5: CAPTURE SCREENSHOTS ===")
capture_screenshot(TABLET, os.path.abspath(r"scratch\tablet_receiver_connected.png"))
capture_screenshot(PHONE, os.path.abspath(r"scratch\phone_transmitter_connected.png"))

# 6. Physical Speech Test: Phone -> Tablet
print(f"\n=== STEP 6: PHYSICAL SPEECH TEST (PHONE -> TABLET) ===")
c_phone_ptt, _, _ = get_node_info(PHONE, "btn_ptt")
if c_phone_ptt:
    print("Tapping PTT on Phone...")
    adb(PHONE, f"shell input tap {c_phone_ptt[0]} {c_phone_ptt[1]}")
    time.sleep(3.5) # Recording window
    print("Stopping PTT on Phone...")
    adb(PHONE, f"shell input tap {c_phone_ptt[0]} {c_phone_ptt[1]}")
    time.sleep(2.5) # Processing & TTS playback window

# 7. Dump UI state after speech
print(f"\n=== STEP 7: POST-SPEECH UI STATE DUMP ===")
_, phone_you, _ = get_node_info(PHONE, "tv_you_transcript")
_, phone_status, _ = get_node_info(PHONE, "tv_status")
_, tab_remote, _ = get_node_info(TABLET, "tv_remote_transcript")
_, tab_playback, _ = get_node_info(TABLET, "tv_remote_playback_status")
_, tab_status, _ = get_node_info(TABLET, "tv_status")

print(f"Phone YOU Transcript: {phone_you}")
print(f"Phone Status: {phone_status}")
print(f"Tablet REMOTE Transcript: {tab_remote}")
print(f"Tablet TTS Playback Status: {tab_playback}")
print(f"Tablet Status: {tab_status}")

# 8. Check Logcat
print(f"\n=== STEP 8: LOGCAT FILTERED CHECK ===")
phone_log = adb(PHONE, "logcat -d -s MainActivity:E TransceiverCoordinator:E AndroidTransceiverComposition:E Transceiver:E AndroidRuntime:E | tail -n 15")
tab_log = adb(TABLET, "logcat -d -s MainActivity:E TransceiverCoordinator:E AndroidTransceiverComposition:E Transceiver:E AndroidRuntime:E | tail -n 15")
print(f"Phone Errors: {phone_log if phone_log else 'None (0 errors)'}")
print(f"Tablet Errors: {tab_log if tab_log else 'None (0 errors)'}")

print("\n=== REVERSE TOPOLOGY VALIDATION COMPLETE ===")
