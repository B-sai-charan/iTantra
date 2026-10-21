import subprocess, os, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX' # Mobile Phone (Transmitter)
dev_b = 'HNP097HQ'       # Tablet (Receiver: 172.20.161.69)

# 1. Verify Tablet is listening
subprocess.run([adb, '-s', dev_b, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
res_b = subprocess.run([adb, '-s', dev_b, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True)
xml_b = res_b.stdout.decode('utf-8', errors='ignore')
root_b = ET.fromstring(xml_b)
tablet_conn = 'UNKNOWN'
for n in root_b.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        tablet_conn = n.attrib.get('text', '')
print(f'Tablet (Receiver) UI Status: "{tablet_conn}"')

# 2. On Mobile Phone (Dev A), tap Connect (center 1050, 633)
print('Tapping Connect on Mobile Phone...')
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '1050', '633'])
time.sleep(3.0)

# 3. Check Phone UI status
subprocess.run([adb, '-s', dev_a, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
res_a = subprocess.run([adb, '-s', dev_a, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True)
xml_a = res_a.stdout.decode('utf-8', errors='ignore')
root_a = ET.fromstring(xml_a)
phone_conn = 'UNKNOWN'
for n in root_a.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        phone_conn = n.attrib.get('text', '')
print(f'Phone (Transmitter) UI Status: "{phone_conn}"')

# 4. Check Logcat on Phone
res_log_a = subprocess.run([adb, '-s', dev_a, 'logcat', '-d'], capture_output=True)
log_a = res_log_a.stdout.decode('utf-8', errors='ignore')
print('=== PHONE LOGCAT RECENT LINES ===')
for l in log_a.split('\n')[-20:]:
    if any(k in l for k in ['PHYSICAL_E2E', 'SOCKET_', 'CLIENT_CONNECT', 'TRANSPORT_STATE']):
        print('   ', l)
