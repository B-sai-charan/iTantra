import subprocess, os, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX' # IP: 172.20.227.73
dev_b = 'HNP097HQ'       # IP: 172.20.84.74

# Clear logcats
subprocess.run([adb, '-s', dev_a, 'logcat', '-c'])
subprocess.run([adb, '-s', dev_b, 'logcat', '-c'])

print('Connecting Dev B to Dev A at 172.20.227.73...')
# btn_connect_peer on Dev B: center 1092, 303
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '1092', '303'])
time.sleep(2.5)

# Check state on Dev B
subprocess.run([adb, '-s', dev_b, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
xml_b = subprocess.run([adb, '-s', dev_b, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
root_b = ET.fromstring(xml_b)
for n in root_b.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        print('Dev B State:', n.attrib.get('text'))

# Trigger PTT on Dev B
print('Triggering PTT on Dev B...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
time.sleep(2.0)
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
time.sleep(3.5)

# Check UI state on both
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

# Read logs on Dev A
log_a = subprocess.run([adb, '-s', dev_a, 'logcat', '-d'], capture_output=True, text=True).stdout
print('=== DEV A RECEIVER LOGS ===')
for l in log_a.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'M2_RECEIVE', 'M2_DECODE', 'E2E-REMOTE', 'E2E-B-PLAYBACK', 'SpeechMessage']):
        print(l)
