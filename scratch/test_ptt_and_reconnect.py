import subprocess, os, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_b = 'HNP097HQ'

# PTT bounds on Dev B: [534,788][666,920] (center 600, 854)
print('=== PHASE 14: 3 REPEATED PTT CYCLES ===')
for i in range(1, 4):
    print(f'Cycle {i}: Pressing PTT...')
    subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
    time.sleep(1.5)
    subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
    time.sleep(1.0)
    print(f'Cycle {i} completed.')

print('\n=== PHASE 13: DISCONNECT / RECONNECT TEST ===')
# Tap Server mode to disconnect client
print('Switching mode on Device B to Server...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '300', '228'])
time.sleep(1.5)

# Switch back to Client mode
print('Switching mode on Device B back to Client...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '880', '228'])
time.sleep(1.0)

# Connect again
print('Connecting again...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '1092', '303'])
time.sleep(2.0)

# Dump UI
subprocess.run([adb, '-s', dev_b, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
xml = subprocess.run([adb, '-s', dev_b, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
root = ET.fromstring(xml)
for n in root.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        print('Post-Reconnect State on Dev B:', n.attrib.get('text'))
