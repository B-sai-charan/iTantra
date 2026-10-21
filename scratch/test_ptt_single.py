import subprocess, os, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX'
dev_b = 'HNP097HQ'

# Clear logcats
subprocess.run([adb, '-s', dev_a, 'logcat', '-c'])
subprocess.run([adb, '-s', dev_b, 'logcat', '-c'])

# Tap PTT on Dev B (center 600, 854)
print('Tapping PTT on Device B to start recording...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
time.sleep(2.0)
print('Tapping PTT on Device B to stop recording and trigger STT...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
time.sleep(3.5)

# Check UI on both devices
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

# Check logcat from Dev A
log_a = subprocess.run([adb, '-s', dev_a, 'logcat', '-d'], capture_output=True, text=True).stdout
print('=== DEV A RECEIVER LOGS ===')
for l in log_a.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'M2_RECEIVE', 'M2_DECODE', 'E2E-REMOTE', 'E2E-B-PLAYBACK', 'SpeechMessage']):
        print(l)
