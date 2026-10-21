import os, hashlib, subprocess, time, zipfile, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
apk_path = 'app/build/outputs/apk/debug/app-debug.apk'

print('=== 1. CURRENT APK IDENTITY ===')
if os.path.exists(apk_path):
    size = os.path.getsize(apk_path)
    with open(apk_path, 'rb') as f:
        sha256 = hashlib.sha256(f.read()).hexdigest()
    print(f'APK Path: {apk_path}')
    print(f'APK Size: {size} bytes')
    print(f'APK SHA-256: {sha256}')
    expected_sha = '80e0c2dbe3b30306f62fb623f76630286079ee05d213761b42d3072cc2ca274a'
    print(f'Matches baseline size (315427692): {size == 315427692}')
    print(f'Matches baseline SHA: {sha256 == expected_sha}')
else:
    print('APK NOT FOUND!')

print('\n=== 3. CONNECTED DEVICES ===')
dev_out = subprocess.run([adb, 'devices'], capture_output=True, text=True).stdout
print(dev_out.strip())
devices = [line.split()[0] for line in dev_out.strip().split('\n')[1:] if line.strip() and not line.startswith('*')]

for d in devices:
    mfr = subprocess.run([adb, '-s', d, 'shell', 'getprop', 'ro.product.manufacturer'], capture_output=True, text=True).stdout.strip()
    model = subprocess.run([adb, '-s', d, 'shell', 'getprop', 'ro.product.model'], capture_output=True, text=True).stdout.strip()
    rel = subprocess.run([adb, '-s', d, 'shell', 'getprop', 'ro.build.version.release'], capture_output=True, text=True).stdout.strip()
    abi = subprocess.run([adb, '-s', d, 'shell', 'getprop', 'ro.product.cpu.abi'], capture_output=True, text=True).stdout.strip()
    ip_out = subprocess.run([adb, '-s', d, 'shell', 'ip', 'addr', 'show', 'wlan0'], capture_output=True, text=True).stdout
    ip = [l.strip().split()[1].split('/')[0] for l in ip_out.split('\n') if 'inet ' in l]
    print(f'Device: {d} | Mfr: {mfr} | Model: {model} | Android: {rel} | ABI: {abi} | IP: {ip}')

print('\n=== 4. PACKAGE STATE ON DEVICES ===')
for d in devices:
    pm_path = subprocess.run([adb, '-s', d, 'shell', 'pm', 'path', 'org.itantra.app'], capture_output=True, text=True).stdout.strip()
    pid = subprocess.run([adb, '-s', d, 'shell', 'pidof', 'org.itantra.app'], capture_output=True, text=True).stdout.strip()
    print(f'[{d}] Package Path: {pm_path} | Running PID: {pid}')

print('\n=== 5. LAUNCH TEST & PERMISSION CHECK ===')
for d in devices:
    # Check RECORD_AUDIO permission
    dumpsys = subprocess.run([adb, '-s', d, 'shell', 'dumpsys', 'package', 'org.itantra.app'], capture_output=True, text=True).stdout
    perm_granted = 'android.permission.RECORD_AUDIO: granted=true' in dumpsys or 'RECORD_AUDIO: granted=true' in dumpsys or 'android.permission.RECORD_AUDIO' in dumpsys
    print(f'[{d}] RECORD_AUDIO Permission Info in dumpsys: granted_indicator={perm_granted}')
    
    # Launch app
    start = subprocess.run([adb, '-s', d, 'shell', 'am', 'start', '-n', 'org.itantra.app/org.itantra.MainActivity'], capture_output=True, text=True).stdout.strip()
    print(f'[{d}] Launch Command Output: {start}')

time.sleep(3)

print('\n=== 6. RUNNING PID & LOGCAT CRASH CHECK ===')
for d in devices:
    pid = subprocess.run([adb, '-s', d, 'shell', 'pidof', 'org.itantra.app'], capture_output=True, text=True).stdout.strip()
    print(f'[{d}] Process Alive PID: {pid}')
    
    # Check logcat for FATAL / crashes
    log = subprocess.run([adb, '-s', d, 'logcat', '-d', '-t', '100'], capture_output=True, text=True).stdout
    fatals = [l for l in log.split('\n') if any(k in l for k in ['FATAL', 'AndroidRuntime', 'ANR', 'UnsatisfiedLinkError', 'OutOfMemory', 'Exception'])]
    print(f'[{d}] Recent Exceptions/Fatal Lines in logcat ({len(fatals)} found):')
    for f in fatals[:5]:
        print('   ', f)
