import subprocess, os, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX' # Mobile Phone (vivo I2304)
dev_b = 'HNP097HQ'       # Tablet (Lenovo: 172.20.38.186)

tablet_ip = '172.20.38.186'
phone_ip = '172.20.105.179'

print(f'=== ACCURATE INPUT TEST: Entering {tablet_ip} on Mobile Phone ===')

# On Mobile Phone, tap et_peer_ip field: center (492, 633)
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '492', '633'])
time.sleep(0.5)

# Select all and delete
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'keyevent', 'KEYCODE_MOVE_END'])
for _ in range(30):
    subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'keyevent', '67']) # backspace
time.sleep(0.5)

# Type IP character by character
for char in tablet_ip:
    subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'text', char])
    time.sleep(0.1)
time.sleep(1.0)

# Check text entered
subprocess.run([adb, '-s', dev_a, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
res = subprocess.run([adb, '-s', dev_a, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True)
xml = res.stdout.decode('utf-8', errors='ignore')
root = ET.fromstring(xml)
entered_text = 'UNKNOWN'
for n in root.iter('node'):
    if 'et_peer_ip' in n.attrib.get('resource-id', ''):
        entered_text = n.attrib.get('text', '')
print(f'Verified et_peer_ip text on Mobile Phone: "{entered_text}"')

# Clear logcats
subprocess.run([adb, '-s', dev_a, 'logcat', '-c'])
subprocess.run([adb, '-s', dev_b, 'logcat', '-c'])

# Tap Connect on Mobile Phone (center 1050, 633)
print('Tapping Connect on Mobile Phone...')
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '1050', '633'])
time.sleep(10.0)

# Dump UI on Mobile Phone
subprocess.run([adb, '-s', dev_a, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
res_a = subprocess.run([adb, '-s', dev_a, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True)
xml_a = res_a.stdout.decode('utf-8', errors='ignore')
root_a = ET.fromstring(xml_a)
phone_conn = 'UNKNOWN'
for n in root_a.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        phone_conn = n.attrib.get('text', '')
print(f'Phone UI Connection State: {phone_conn}')

# Read Phone logcat
res_log_a = subprocess.run([adb, '-s', dev_a, 'logcat', '-d'], capture_output=True)
log_a = res_log_a.stdout.decode('utf-8', errors='ignore')
print('=== PHONE LOGCAT SEQUENCE ===')
for l in log_a.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'SOCKET_', 'CLIENT_CONNECT', 'CONNECT_FAILED', 'TRANSPORT_FAILED', 'Exception', 'Timeout', 'refused']):
        print('   ', l)

# Read Tablet logcat
res_log_b = subprocess.run([adb, '-s', dev_b, 'logcat', '-d'], capture_output=True)
log_b = res_log_b.stdout.decode('utf-8', errors='ignore')
print('=== TABLET LOGCAT SEQUENCE ===')
for l in log_b.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'TcpServerTransport', 'Socket', 'accept']):
        print('   ', l)
