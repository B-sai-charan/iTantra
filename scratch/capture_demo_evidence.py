import subprocess, os, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX'
dev_b = 'HNP097HQ'

# 1. UI dumps
print('=== UI STATE ON BOTH DEVICES ===')
for d, name in [(dev_a, 'DEV_A (Receiver)'), (dev_b, 'DEV_B (Transmitter)')]:
    subprocess.run([adb, '-s', d, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
    res = subprocess.run([adb, '-s', d, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True)
    xml = res.stdout.decode('utf-8', errors='ignore')
    root = ET.fromstring(xml)
    print(f'=== {name} ===')
    for n in root.iter('node'):
        rid = n.attrib.get('resource-id', '')
        t = n.attrib.get('text', '')
        if any(k in rid for k in ['tv_connection_state', 'tv_you_transcript', 'tv_remote_transcript', 'tv_status', 'tv_ptt_label']):
            print(f'   {rid} -> "{t}"')

# 2. Logcat on Dev A
print('\n=== DEV A RECEIVER LOGS ===')
res_a = subprocess.run([adb, '-s', dev_a, 'logcat', '-d'], capture_output=True)
log_a = res_a.stdout.decode('utf-8', errors='ignore')
for l in log_a.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'M2_RECEIVE', 'M2_DECODE', 'E2E-REMOTE', 'E2E-B-PLAYBACK', 'SpeechMessage']):
        print('   ', l)

# 3. Logcat on Dev B
print('\n=== DEV B TRANSMITTER LOGS ===')
res_b = subprocess.run([adb, '-s', dev_b, 'logcat', '-d'], capture_output=True)
log_b = res_b.stdout.decode('utf-8', errors='ignore')
for l in log_b.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'M2_SEND', 'M2_ENCODE', 'SpeechMessage']):
        print('   ', l)
