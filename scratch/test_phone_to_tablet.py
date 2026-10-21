import subprocess, os, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX' # Mobile Phone (Client / Transmitter)
dev_b = 'HNP097HQ'       # Tablet (Server / Receiver: 172.20.38.186)

tablet_ip = '172.20.38.186'
phone_ip = '172.20.105.179'

print(f'=== PHASE 6 & 7: CONNECT PHONE ({phone_ip}) TO TABLET ({tablet_ip}:42173) ===')

# On Phone (Dev A), select Transmitter mode: center (912, 489)
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '912', '489'])
time.sleep(1.0)

# Enter Tablet IP on Phone into et_peer_ip (center 492, 633)
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '492', '633'])
time.sleep(0.5)
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'keyevent', 'KEYCODE_MOVE_END'])
for _ in range(25):
    subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'keyevent', '67'])
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'text', tablet_ip])
time.sleep(1.0)

# Clear logcats
subprocess.run([adb, '-s', dev_a, 'logcat', '-c'])
subprocess.run([adb, '-s', dev_b, 'logcat', '-c'])

# Tap Connect on Phone (center 1050, 633)
print('Tapping Connect on Phone...')
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '1050', '633'])
time.sleep(10.0)

# Dump UI on Phone
subprocess.run([adb, '-s', dev_a, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
res_a = subprocess.run([adb, '-s', dev_a, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True)
xml_a = res_a.stdout.decode('utf-8', errors='ignore')
root_a = ET.fromstring(xml_a)
phone_conn = 'UNKNOWN'
for n in root_a.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        phone_conn = n.attrib.get('text', '')
print(f'Phone Connection UI State: {phone_conn}')

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
