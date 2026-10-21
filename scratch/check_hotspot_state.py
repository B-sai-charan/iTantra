import os, subprocess, time

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX' # Phone
dev_b = 'HNP097HQ'       # Tablet

print('=== INSPECTING PHONE TETHERING & TABLET CONNECTION ===')

# Check Phone interfaces for AP / hotspot interface
phone_ip_out = subprocess.run([adb, '-s', dev_a, 'shell', 'ip', 'addr'], capture_output=True, text=True).stdout
print('[PHONE INTERFACES]')
for l in phone_ip_out.split('\n'):
    if any(k in l for k in ['wlan', 'ap', 'swlan', 'softap', 'tether', 'inet ']):
        print('  ', l.strip())

# Check Phone tethering dumpsys
tether_dumpsys = subprocess.run([adb, '-s', dev_a, 'shell', 'dumpsys', 'tethering'], capture_output=True, text=True).stdout
print('[PHONE TETHERING STATE]')
for l in tether_dumpsys.split('\n'):
    if any(k in l for k in ['Tethered:', 'Active:', 'Serving:', 'IPv4:', 'wlan', 'ap', 'softap', 'swlan', 'Clients']):
        print('  ', l.strip())

# Check Tablet Wi-Fi connection
tablet_wifi = subprocess.run([adb, '-s', dev_b, 'shell', 'dumpsys', 'wifi'], capture_output=True, text=True).stdout
print('\n[TABLET CURRENT WI-FI CONNECTION]')
for l in tablet_wifi.split('\n'):
    if 'mWifiInfo' in l or 'SSID:' in l:
        if 'SSID' in l and 'null' not in l:
            print('  ', l.strip()[:100])
            break

tablet_ip_out = subprocess.run([adb, '-s', dev_b, 'shell', 'ip', 'addr', 'show', 'wlan0'], capture_output=True, text=True).stdout
print('[TABLET wlan0 IP]')
for l in tablet_ip_out.split('\n'):
    if 'inet ' in l:
        print('  ', l.strip())
