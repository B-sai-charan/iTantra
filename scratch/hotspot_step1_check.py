import os, subprocess, time, json, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')

dev_a = '10BE830QG8000MX' # Phone
dev_b = 'HNP097HQ'       # Tablet

print('=== STEP 1: PRE-CHECK CURRENT APP & INTERFACE STATE ===')
for d, name in [(dev_a, 'PHONE (Dev A)'), (dev_b, 'TABLET (Dev B)')]:
    mfr = subprocess.run([adb, '-s', d, 'shell', 'getprop', 'ro.product.manufacturer'], capture_output=True, text=True).stdout.strip()
    model = subprocess.run([adb, '-s', d, 'shell', 'getprop', 'ro.product.model'], capture_output=True, text=True).stdout.strip()
    rel = subprocess.run([adb, '-s', d, 'shell', 'getprop', 'ro.build.version.release'], capture_output=True, text=True).stdout.strip()
    abi = subprocess.run([adb, '-s', d, 'shell', 'getprop', 'ro.product.cpu.abi'], capture_output=True, text=True).stdout.strip()
    
    ip_out = subprocess.run([adb, '-s', d, 'shell', 'ip', 'addr'], capture_output=True, text=True).stdout
    route_out = subprocess.run([adb, '-s', d, 'shell', 'ip', 'route', 'show'], capture_output=True, text=True).stdout
    wifi_dumpsys = subprocess.run([adb, '-s', d, 'shell', 'dumpsys', 'wifi'], capture_output=True, text=True).stdout
    
    print(f'[{name} - {d}]')
    print(f'  Model: {mfr} {model} (Android {rel}, {abi})')
    print('  Interfaces & IPs:')
    for line in ip_out.split('\n'):
        if any(k in line for k in ['wlan0', 'ap0', 'swlan0', 'tether', 'inet ']):
            print('    ', line.strip())
    print('  Routes:')
    for line in route_out.split('\n'):
        if line.strip():
            print('    ', line.strip())
    
    # Check SSID
    for l in wifi_dumpsys.split('\n'):
        if 'mWifiInfo' in l or 'SSID:' in l:
            if 'SSID' in l and 'null' not in l:
                print('   Wi-Fi SSID snippet:', l.strip()[:100])
                break

# Check Tablet listening socket
print('\n=== TABLET SOCKET CHECK (PORT 42173) ===')
netstat_b = subprocess.run([adb, '-s', dev_b, 'shell', 'netstat', '-tlpn'], capture_output=True, text=True).stdout
for l in netstat_b.split('\n'):
    if '42173' in l:
        print('   ', l)
