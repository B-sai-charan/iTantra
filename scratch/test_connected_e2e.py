import subprocess, os, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX' # IP: 172.20.227.73
dev_b = 'HNP097HQ'       # IP: 172.20.84.74

# 1. Start Server on Dev A
print('Starting Server on Device A...')
# btn_mode_server bounds on Dev A: [84,429][612,549] -> center 348, 489
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '348', '489'])
time.sleep(2.0)

# Check state on Dev A
subprocess.run([adb, '-s', dev_a, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
xml_a = subprocess.run([adb, '-s', dev_a, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
root_a = ET.fromstring(xml_a)
for n in root_a.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        print('Dev A State:', n.attrib.get('text'))

# 2. Connect from Dev B
print('Connecting from Device B to 172.20.227.73...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '1092', '303'])
time.sleep(2.0)

# Check state on Dev B
subprocess.run([adb, '-s', dev_b, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
xml_b = subprocess.run([adb, '-s', dev_b, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
root_b = ET.fromstring(xml_b)
for n in root_b.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        print('Dev B State:', n.attrib.get('text'))

# 3. Trigger Speech on Dev B
print('Triggering PTT on Device B...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
time.sleep(2.0)
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
time.sleep(3.5)

# 4. Final state check
for d, name in [(dev_a, 'DEV_A'), (dev_b, 'DEV_B')]:
    subprocess.run([adb, '-s', d, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
    xml = subprocess.run([adb, '-s', d, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
    root = ET.fromstring(xml)
    print(f'=== UI STATE FOR {name} ===')
    for n in root.iter('node'):
        rid = n.attrib.get('resource-id', '')
        t = n.attrib.get('text', '')
        if any(k in rid for k in ['tv_connection_state', 'tv_you_transcript', 'tv_remote_transcript', 'tv_status', 'tv_ptt_label']):
            print(f'{rid} -> "{t}"')
