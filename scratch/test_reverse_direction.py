import subprocess, os, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX' # IP: 172.20.29.81
dev_b = 'HNP097HQ'       # IP: 172.20.161.69

print('=== PHASE 12: REVERSE DIRECTION TEST (A -> B) ===')

# Set Dev B as Server (Receiver)
print('Setting Device B as Server...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '300', '228'])
time.sleep(1.5)

# Set Dev A as Client (Transmitter)
print('Setting Device A as Client...')
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '912', '489'])
time.sleep(1.0)

# On Dev A, enter Dev B IP: 172.20.161.69
# Let's find bounds of et_peer_ip and btn_connect_peer on Dev A
subprocess.run([adb, '-s', dev_a, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
xml = subprocess.run([adb, '-s', dev_a, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
root = ET.fromstring(xml)

et_a = None
btn_a = None
for n in root.iter('node'):
    if 'et_peer_ip' in n.attrib.get('resource-id', ''):
        b = n.attrib.get('bounds')
        et_a = b
    if 'btn_connect_peer' in n.attrib.get('resource-id', ''):
        b = n.attrib.get('bounds')
        btn_a = b

print(f'Dev A et_peer_ip bounds: {et_a}, btn_connect bounds: {btn_a}')

# On Dev A, tap IP field (center of [84,573][900,693] -> 492, 633)
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '492', '633'])
time.sleep(0.5)
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'keyevent', 'KEYCODE_MOVE_END'])
for _ in range(25):
    subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'keyevent', '67'])
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'text', '172.20.161.69'])
time.sleep(1.0)

# Tap Connect on Dev A (center of [924,573][1176,693] -> 1050, 633)
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '1050', '633'])
time.sleep(2.5)

# Check state
subprocess.run([adb, '-s', dev_a, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
xml_a = subprocess.run([adb, '-s', dev_a, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
root_a = ET.fromstring(xml_a)
for n in root_a.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        print('Dev A (Transmitter) Connection State:', n.attrib.get('text'))

subprocess.run([adb, '-s', dev_b, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
xml_b = subprocess.run([adb, '-s', dev_b, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
root_b = ET.fromstring(xml_b)
for n in root_b.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        print('Dev B (Receiver) Connection State:', n.attrib.get('text'))
