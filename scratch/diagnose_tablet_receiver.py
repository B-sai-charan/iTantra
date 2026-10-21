import os, subprocess, time, json, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')

dev_a = '10BE830QG8000MX' # Mobile Phone (vivo I2304)
dev_b = 'HNP097HQ'       # Tablet (Lenovo TB-X6C6F)

print('=== PHASE 1 & 2: DEVICE IDENTIFICATION & NETWORK INSPECTION ===')
for d, role in [(dev_a, 'MOBILE PHONE (Dev A)'), (dev_b, 'TABLET (Dev B)')]:
    mfr = subprocess.run([adb, '-s', d, 'shell', 'getprop', 'ro.product.manufacturer'], capture_output=True, text=True).stdout.strip()
    model = subprocess.run([adb, '-s', d, 'shell', 'getprop', 'ro.product.model'], capture_output=True, text=True).stdout.strip()
    rel = subprocess.run([adb, '-s', d, 'shell', 'getprop', 'ro.build.version.release'], capture_output=True, text=True).stdout.strip()
    abi = subprocess.run([adb, '-s', d, 'shell', 'getprop', 'ro.product.cpu.abi'], capture_output=True, text=True).stdout.strip()
    
    ip_out = subprocess.run([adb, '-s', d, 'shell', 'ip', 'addr', 'show', 'wlan0'], capture_output=True, text=True).stdout
    route_out = subprocess.run([adb, '-s', d, 'shell', 'ip', 'route', 'show'], capture_output=True, text=True).stdout
    
    ip = 'UNKNOWN'
    prefix = 'UNKNOWN'
    for line in ip_out.split('\n'):
        if 'inet ' in line:
            parts = line.strip().split()[1].split('/')
            ip = parts[0]
            prefix = parts[1] if len(parts) > 1 else '32'
            
    gateway = 'UNKNOWN'
    for line in route_out.split('\n'):
        if 'default via' in line:
            gateway = line.strip().split()[2]
            
    # Wi-Fi SSID
    wifi_info = subprocess.run([adb, '-s', d, 'shell', 'dumpsys', 'wifi'], capture_output=True, text=True).stdout
    ssid = 'UNKNOWN'
    for l in wifi_info.split('\n'):
        if 'mWifiInfo' in l or 'SSID:' in l or 'SSID' in l:
            if 'SSID' in l and ssid == 'UNKNOWN' and 'null' not in l:
                ssid = l.strip()
                break
                
    print(f'[{role} - {d}]')
    print(f'  Model: {mfr} {model} (Android {rel}, {abi})')
    print(f'  IP: {ip}/{prefix}')
    print(f'  Gateway: {gateway}')
    print(f'  Wi-Fi Info snippet: {ssid[:100]}')

print('\n=== PHASE 3 & 4: RECEIVER ON TABLET (DEV B) & SOCKET BIND AUDIT ===')
# Fresh start on both
for d in [dev_a, dev_b]:
    subprocess.run([adb, '-s', d, 'shell', 'am', 'force-stop', 'org.itantra.app'])
time.sleep(1)

for d in [dev_a, dev_b]:
    subprocess.run([adb, '-s', d, 'shell', 'am', 'start', '-n', 'org.itantra.app/org.itantra.MainActivity'])
time.sleep(3)

# On Tablet (Dev B): Select Receiver (Server) mode (center 300, 228)
print('Setting Tablet (Dev B) as Receiver (Server)...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '300', '228'])
time.sleep(2.5)

# Check Tablet UI state
subprocess.run([adb, '-s', dev_b, 'shell', 'uiautomator', 'dump', '/sdcard/dump.xml'])
res_b = subprocess.run([adb, '-s', dev_b, 'shell', 'cat', '/sdcard/dump.xml'], capture_output=True)
xml_b = res_b.stdout.decode('utf-8', errors='ignore')
root_b = ET.fromstring(xml_b)
tablet_state = 'UNKNOWN'
for n in root_b.iter('node'):
    if 'tv_connection_state' in n.attrib.get('resource-id', ''):
        tablet_state = n.attrib.get('text', '')
print(f'Tablet (Dev B) UI Connection State: {tablet_state}')

# Check Tablet socket state
netstat_b = subprocess.run([adb, '-s', dev_b, 'shell', 'netstat', '-tlpn'], capture_output=True, text=True).stdout
ss_b = subprocess.run([adb, '-s', dev_b, 'shell', 'ss', '-tlpn'], capture_output=True, text=True).stdout
print('Tablet (Dev B) Socket Listen Output for 42173:')
found_b_listen = False
for line in (netstat_b + ss_b).split('\n'):
    if '42173' in line:
        print('   ', line)
        found_b_listen = True
if not found_b_listen:
    print('   NONE found in netstat/ss on Tablet!')

# Check Tablet Logcat
log_b_init = subprocess.run([adb, '-s', dev_b, 'logcat', '-d', '-t', '40'], capture_output=True, text=True).stdout
print('Tablet Recent Logcat lines:')
for l in log_b_init.split('\n'):
    if any(k in l for k in ['SERVER_START', 'TcpServerTransport', 'MainActivity', 'TransceiverCoordinator']):
        print('   ', l)
