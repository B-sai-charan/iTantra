import os, subprocess, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX'
dev_b = 'HNP097HQ'

print('==============================================')
print('STEP 1 — DISCOVER CURRENT IPs')
print('==============================================')
def get_ip(device):
    out = subprocess.run([adb, '-s', device, 'shell', 'ip', 'addr', 'show', 'wlan0'], capture_output=True, text=True).stdout
    for line in out.split('\n'):
        if 'inet ' in line:
            return line.strip().split()[1].split('/')[0]
    return 'UNKNOWN'

ip_a = get_ip(dev_a)
ip_b = get_ip(dev_b)
print(f'Device A current IP = {ip_a}')
print(f'Device B current IP = {ip_b}')

print('\n==============================================')
print('STEP 2 — FRESH APP START')
print('==============================================')
for d in [dev_a, dev_b]:
    subprocess.run([adb, '-s', d, 'shell', 'am', 'force-stop', 'org.itantra.app'])
time.sleep(1)

for d in [dev_a, dev_b]:
    subprocess.run([adb, '-s', d, 'shell', 'am', 'start', '-n', 'org.itantra.app/org.itantra.MainActivity'])
time.sleep(3.5)

pid_a = subprocess.run([adb, '-s', dev_a, 'shell', 'pidof', 'org.itantra.app'], capture_output=True, text=True).stdout.strip()
pid_b = subprocess.run([adb, '-s', dev_b, 'shell', 'pidof', 'org.itantra.app'], capture_output=True, text=True).stdout.strip()
print(f'Device A Process Alive: PID {pid_a}')
print(f'Device B Process Alive: PID {pid_b}')

print('\n==============================================')
print('STEP 3 — RECEIVER (DEVICE A)')
print('==============================================')
# Device A defaults to Receiver (Server), but let's tap it explicitly to be certain
# btn_mode_server on Dev A: [84,429][612,549] -> center (348, 489)
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '348', '489'])
time.sleep(2.5)

netstat = subprocess.run([adb, '-s', dev_a, 'shell', 'netstat', '-tlpn'], capture_output=True, text=True).stdout
ss_out = subprocess.run([adb, '-s', dev_a, 'shell', 'ss', '-tlpn'], capture_output=True, text=True).stdout
is_listening = ('42173' in netstat or '42173' in ss_out)
print(f'TCP port 42173 = {"LISTENING" if is_listening else "NOT LISTENING"}')

print('\n==============================================')
print('STEP 4 — TRANSMITTER (DEVICE B)')
print('==============================================')
# Select Transmitter (Client) on Dev B: center (880, 228)
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '880', '228'])
time.sleep(1.0)

# Enter current Device A IP into et_peer_ip (center 500, 303)
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '500', '303'])
time.sleep(0.5)
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'keyevent', 'KEYCODE_MOVE_END'])
for _ in range(25):
    subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'keyevent', '67'])
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'text', ip_a])
time.sleep(1.0)

# Clear logcats
subprocess.run([adb, '-s', dev_a, 'logcat', '-c'])
subprocess.run([adb, '-s', dev_b, 'logcat', '-c'])

# Tap Connect on Dev B: center (1092, 303)
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '1092', '303'])
time.sleep(2.5)

# Verify CONNECTED
subprocess.run([adb, '-s', dev_b, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
xml_b = subprocess.run([adb, '-s', dev_b, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
root_b = ET.fromstring(xml_b)
dev_b_conn = 'UNKNOWN'
for n in root_b.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        dev_b_conn = n.attrib.get('text', '')
print(f'Device B Connection Status in UI: {dev_b_conn}')

log_b = subprocess.run([adb, '-s', dev_b, 'logcat', '-d'], capture_output=True, text=True).stdout
for l in log_b.split('\n'):
    if any(k in l for k in ['SOCKET_CONNECTED', 'CLIENT_CONNECT', 'TRANSPORT_STATE']):
        print('   ', l)

print('\n==============================================')
print('STEP 5 — ENGLISH LIVE TEST (MESSAGE 1)')
print('==============================================')
# Ensure English is selected on Dev B: center (300, 408)
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '300', '408'])
time.sleep(0.5)

# Clear logcats
subprocess.run([adb, '-s', dev_a, 'logcat', '-c'])
subprocess.run([adb, '-s', dev_b, 'logcat', '-c'])

# Tap PTT on Dev B (center 600, 854)
print('Pressing PTT on Device B for Message 1...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
time.sleep(2.0)
print('Releasing PTT on Device B...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
time.sleep(3.5)

# Read Dev A logs for Message 1
res_a = subprocess.run([adb, '-s', dev_a, 'logcat', '-d'], capture_output=True)
log_a1 = res_a.stdout.decode('utf-8', errors='ignore')
print('--- Dev A Logs for Message 1 ---')
for l in log_a1.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'M2_RECEIVE', 'M2_DECODE', 'E2E-REMOTE', 'E2E-B-PLAYBACK', 'SpeechMessage']):
        print('   ', l)

print('\n==============================================')
print('STEP 6 — SECOND MESSAGE (MESSAGE 2)')
print('==============================================')
# Clear logcats
subprocess.run([adb, '-s', dev_a, 'logcat', '-c'])
subprocess.run([adb, '-s', dev_b, 'logcat', '-c'])

# Tap PTT on Dev B
print('Pressing PTT on Device B for Message 2...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
time.sleep(2.0)
print('Releasing PTT on Device B...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
time.sleep(3.5)

# Read Dev A logs for Message 2
res_a2 = subprocess.run([adb, '-s', dev_a, 'logcat', '-d'], capture_output=True)
log_a2 = res_a2.stdout.decode('utf-8', errors='ignore')
print('--- Dev A Logs for Message 2 ---')
for l in log_a2.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'M2_RECEIVE', 'M2_DECODE', 'E2E-REMOTE', 'E2E-B-PLAYBACK', 'SpeechMessage']):
        print('   ', l)

print('\n==============================================')
print('STEP 7 — FINAL UI CHECK ON BOTH DEVICES')
print('==============================================')
for d, name in [(dev_a, 'DEVICE A (Receiver)'), (dev_b, 'DEVICE B (Transmitter)')]:
    subprocess.run([adb, '-s', d, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
    xml = subprocess.run([adb, '-s', d, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True, text=True).stdout
    root = ET.fromstring(xml)
    print(f'=== UI STATE FOR {name} ===')
    for n in root.iter('node'):
        rid = n.attrib.get('resource-id', '')
        t = n.attrib.get('text', '')
        if any(k in rid for k in ['tv_connection_state', 'tv_you_transcript', 'tv_remote_transcript', 'tv_status', 'tv_ptt_label']):
            print(f'   {rid} -> "{t}"')
