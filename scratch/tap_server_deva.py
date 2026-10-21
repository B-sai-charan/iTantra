import subprocess, os, time

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX'

subprocess.run([adb, '-s', dev_a, 'logcat', '-c'])

print('Tapping Receiver (Server) on Dev A...')
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '348', '489'])
time.sleep(2.5)

log_a = subprocess.run([adb, '-s', dev_a, 'logcat', '-d'], capture_output=True, text=True).stdout
print('=== DEV A LOGCAT ===')
for l in log_a.split('\n'):
    if any(k in l for k in ['MainActivity', 'ROLE_SELECTED', 'TcpServerTransport', 'TransceiverCoordinator', 'PHYSICAL_E2E']):
        print(l)
