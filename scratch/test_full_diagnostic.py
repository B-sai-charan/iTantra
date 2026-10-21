import os, subprocess, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX'
dev_b = 'HNP097HQ'

print('=== 7. UI STATE CHECK ON BOTH DEVICES ===')
for d, name in [(dev_a, 'DEV_A'), (dev_b, 'DEV_B')]:
    subprocess.run([adb, '-s', d, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
    xml = subprocess.run([adb, '-s', d, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
    root = ET.fromstring(xml)
    
    ids_to_check = [
        'iv_app_logo', 'tv_title', 'tv_subtitle', 'tv_connection_state',
        'btn_mode_server', 'btn_mode_client', 'et_peer_ip', 'btn_connect_peer',
        'btn_lang_en', 'btn_lang_hi', 'layout_card_you', 'layout_card_remote', 'btn_ptt'
    ]
    present = {}
    for n in root.iter('node'):
        rid = n.attrib.get('resource-id', '')
        for i in ids_to_check:
            if i in rid:
                present[i] = n.attrib.get('text', 'PRESENT')
    print(f'[{name}] Found UI Elements: {list(present.keys())}')
    for k, v in present.items():
        print(f'   {k} -> {v}')

print('\n=== 9 & 10. RECEIVER STATE & SOCKET TEST ON DEV A ===')
# Ensure Dev A is Server
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '348', '489'])
time.sleep(2)

# Check if port 42173 is listening on Dev A
netstat = subprocess.run([adb, '-s', dev_a, 'shell', 'netstat', '-tlpn'], capture_output=True, text=True).stdout
ss_out = subprocess.run([adb, '-s', dev_a, 'shell', 'ss', '-tlpn'], capture_output=True, text=True).stdout
print('Dev A netstat / ss output for 42173:')
for line in (netstat + ss_out).split('\n'):
    if '42173' in line:
        print('   ', line)

# Check logcat of Dev A
log_a = subprocess.run([adb, '-s', dev_a, 'logcat', '-d', '-t', '50'], capture_output=True, text=True).stdout
server_lines = [l for l in log_a.split('\n') if any(k in l for k in ['TcpServerTransport', 'MainActivity', 'TransceiverCoordinator'])]
for l in server_lines:
    print('   ', l)

print('\n=== 11. TRANSMITTER CONNECTION TEST ON DEV B ===')
# Current Dev A IP
dev_a_ip = '172.20.227.73'
print(f'Targeting CURRENT Dev A IP: {dev_a_ip}')

# On Dev B, select Client mode
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '880', '228'])
time.sleep(1)

# On Dev B, enter current Dev A IP into et_peer_ip (center 500, 303)
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '500', '303'])
time.sleep(0.5)
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'keyevent', 'KEYCODE_MOVE_END'])
for _ in range(25):
    subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'keyevent', '67'])
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'text', dev_a_ip])
time.sleep(1)

# Clear logcats
subprocess.run([adb, '-s', dev_a, 'logcat', '-c'])
subprocess.run([adb, '-s', dev_b, 'logcat', '-c'])

# Tap Connect on Dev B (center 1092, 303)
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '1092', '303'])
time.sleep(2)

# Check connection status in UI on Dev B
subprocess.run([adb, '-s', dev_b, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
xml_b = subprocess.run([adb, '-s', dev_b, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
root_b = ET.fromstring(xml_b)
dev_b_state = 'UNKNOWN'
for n in root_b.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        dev_b_state = n.attrib.get('text', '')
print(f'Dev B UI Connection State: {dev_b_state}')

log_b = subprocess.run([adb, '-s', dev_b, 'logcat', '-d'], capture_output=True, text=True).stdout
for l in log_b.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'SocketMessageTransport', 'ConnectException', 'SocketTimeoutException']):
        print('   ', l)
