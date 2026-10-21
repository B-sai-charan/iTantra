import subprocess, os, time, xml.etree.ElementTree as ET

adb = os.path.expandvars(r'%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe')
dev_a = '10BE830QG8000MX' # IP: 172.20.29.81
dev_b = 'HNP097HQ'       # IP: 172.20.161.69

print('=== PHASE 11: LIVE HINDI PATH VALIDATION ===')

# Set Dev A as Server (Receiver)
print('Setting Device A as Receiver (Server)...')
subprocess.run([adb, '-s', dev_a, 'shell', 'input', 'tap', '348', '489'])
time.sleep(1.5)

# Set Dev B as Client (Transmitter)
print('Setting Device B as Transmitter (Client)...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '880', '228'])
time.sleep(1.0)

# Connect Dev B to Dev A
print('Connecting Device B to Device A...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '1092', '303'])
time.sleep(2.0)

# Select Hindi on Dev B (bounds: [609,380][1158,437] -> center 880, 408)
print('Selecting Hindi on Device B...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '880', '408'])
time.sleep(1.0)

# Clear logcats
subprocess.run([adb, '-s', dev_a, 'logcat', '-c'])
subprocess.run([adb, '-s', dev_b, 'logcat', '-c'])

# Tap PTT on Dev B (center 600, 854)
print('Triggering PTT cycle on Device B...')
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
time.sleep(2.0)
subprocess.run([adb, '-s', dev_b, 'shell', 'input', 'tap', '600', '854'])
time.sleep(3.0)

# Check logcats
log_a = subprocess.run([adb, '-s', dev_a, 'logcat', '-d'], capture_output=True, text=True).stdout
log_b = subprocess.run([adb, '-s', dev_b, 'logcat', '-d'], capture_output=True, text=True).stdout

print('=== DEV A (RECEIVER HINDI LOGS) ===')
for l in log_a.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'SpeechMessage', 'SherpaOfflineTts', 'Rohan', 'vits-piper-hi']):
        print(l)

print('=== DEV B (TRANSMITTER HINDI LOGS) ===')
for l in log_b.split('\n'):
    if any(k in l for k in ['PHYSICAL_E2E', 'SpeechMessage', 'SpeechPipeline']):
        print(l)
