import subprocess, os, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX' # Mobile Phone (Transmitter)
dev_b = 'HNP097HQ'       # Tablet (Receiver: 172.20.161.69)

tablet_ip = '172.20.161.69'

print('=== 1. FRESH START BOTH APPS ===')
for d in [dev_a, dev_b]:
    subprocess.run([adb, '-s', d, 'shell', 'am', 'force-stop', 'org.itantra.app'])
time.sleep(1.0)

for d in [dev_a, dev_b]:
    subprocess.run([adb, '-s', d, 'shell', 'am', 'start', '-n', 'org.itantra.app/org.itantra.MainActivity'])
time.sleep(3.0)

print('=== 2. CONFIGURE TABLET AS RECEIVER (SERVER) ===')
# Tablet defaults to Server mode on launch, let's tap btn_mode_server: center (300, 228)
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '300', '228'])
time.sleep(3.0)

# Check Tablet netstat
netstat_b = subprocess.run([adb, '-s', dev_b, 'shell', 'netstat', '-tlpn'], capture_output=True, text=True).stdout
print('Tablet listening sockets:')
for l in netstat_b.split('\n'):
    if '42173' in l:
        print('   ', l)

print(f'=== 3. CONFIGURE PHONE AS TRANSMITTER & CONNECT TO {tablet_ip} ===')
# On Phone (Dev A), tap btn_mode_client: [648,429][1176,549] -> center (912, 489)
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '912', '489'])
time.sleep(1.5)

# On Phone (Dev A), tap et_peer_ip: [84,579][888,699] -> center (486, 639)
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '486', '639'])
time.sleep(0.5)
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'keyevent', 'KEYCODE_MOVE_END'])
for _ in range(30):
    subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'keyevent', '67'])
time.sleep(0.5)

for char in tablet_ip:
    subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'text', char])
    time.sleep(0.08)
time.sleep(1.0)

# Clear logcats
subprocess.run([adb, '-s', dev_a, 'logcat', '-c'])
subprocess.run([adb, '-s', dev_b, 'logcat', '-c'])

# On Phone (Dev A), tap btn_connect_peer: [912,579][1176,699] -> center (1044, 639)
print('Tapping Connect on Mobile Phone...')
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '1044', '639'])
time.sleep(4.0)

# 4. Check UI on Phone
subprocess.run([adb, '-s', dev_a, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
res_a = subprocess.run([adb, '-s', dev_a, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True)
xml_a = res_a.stdout.decode('utf-8', errors='ignore')
root_a = ET.fromstring(xml_a)
phone_conn = 'UNKNOWN'
for n in root_a.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        phone_conn = n.attrib.get('text', '')
print(f'Mobile Phone UI Connection State: "{phone_conn}"')

# Read Phone logcat
res_log_a = subprocess.run([adb, '-s', dev_a, 'logcat', '-d'], capture_output=True)
log_a = res_log_a.stdout.decode('utf-8', errors='ignore')
print('=== PHONE LOGCAT ===')
for l in log_a.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'SOCKET_', 'CLIENT_CONNECT', 'CONNECT_FAILED', 'TRANSPORT_FAILED', 'MainActivity']):
        print('   ', l)

# Read Tablet logcat
res_log_b = subprocess.run([adb, '-s', dev_b, 'logcat', '-d'], capture_output=True)
log_b = res_log_b.stdout.decode('utf-8', errors='ignore')
print('=== TABLET LOGCAT ===')
for l in log_b.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'TcpServerTransport', 'Socket', 'accept']):
        print('   ', l)
