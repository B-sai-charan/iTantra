import subprocess
import time
import re

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip()

print("1. Starting Tablet Receiver...")
adb_shell(TABLET, "am start -n org.itantra.app/.MainActivity")
time.sleep(1)
adb_shell(TABLET, "input tap 316 228") # Receiver (Server)

# Wait up to 15s for port 42173 to be in LISTEN state
print("2. Polling Tablet listening socket on port 42173...")
is_listening = False
listening_socket = ""
for i in range(15):
    time.sleep(1)
    netstat = adb_shell(TABLET, "netstat -tlpn 2>/dev/null | grep 42173 || grep -i A4BD /proc/net/tcp6")
    if "42173" in netstat or "A4BD" in netstat.upper():
        is_listening = True
        listening_socket = netstat
        print(f"  [T+{i+1}s] Tablet IS LISTENING:\n{listening_socket}")
        break
    else:
        print(f"  [T+{i+1}s] Waiting for server socket...")

# Read exact current IPs and BSSIDs
p_wifi = adb_shell(PHONE, "cmd wifi status")
t_wifi = adb_shell(TABLET, "cmd wifi status")

p_bssid = re.search(r'BSSID:\s*([0-9a-fA-F:]+)', p_wifi).group(1)
t_bssid = re.search(r'BSSID:\s*([0-9a-fA-F:]+)', t_wifi).group(1)

p_ip = re.search(r'inet\s+([0-9\.]+)/\d+', adb_shell(PHONE, "ip -4 addr show wlan0")).group(1)
t_ip = re.search(r'inet\s+([0-9\.]+)/\d+', adb_shell(TABLET, "ip -4 addr show wlan0")).group(1)

print(f"\nPhone BSSID: {p_bssid} | IP: {p_ip}")
print(f"Tablet BSSID: {t_bssid} | IP: {t_ip}")
print(f"Same BSSID: {'YES' if p_bssid.lower() == t_bssid.lower() else 'NO'}")

# 3. Raw TCP Test Phone -> Tablet :42173
print(f"\n3. Testing Raw TCP: Phone -> Tablet ({t_ip}:42173)...")
p_nc = subprocess.run([ADB, "-s", PHONE, "shell", f"toybox nc -w 4 -z {t_ip} 42173"], capture_output=True, text=True)
print(f"Phone -> Tablet exit code: {p_nc.returncode}")
print(f"Phone -> Tablet stdout: '{p_nc.stdout.strip()}'")
print(f"Phone -> Tablet stderr: '{p_nc.stderr.strip()}'")

# 4. Reverse TCP Test Tablet -> Phone :42173
print(f"\n4. Testing Reverse Raw TCP: Tablet -> Phone ({p_ip}:42173)...")
t_nc = subprocess.run([ADB, "-s", TABLET, "shell", f"echo test | nc -w 4 -q 1 {p_ip} 42173"], capture_output=True, text=True)
print(f"Tablet -> Phone exit code: {t_nc.returncode}")
print(f"Tablet -> Phone stdout: '{t_nc.stdout.strip()}'")
print(f"Tablet -> Phone stderr: '{t_nc.stderr.strip()}'")
