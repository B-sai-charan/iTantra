import subprocess
import time

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
TABLET = "HNP097HQ"

def run(cmd):
    return subprocess.run([ADB, "-s", TABLET, "shell", cmd], capture_output=True, text=True).stdout.strip()

# Clear logcat
subprocess.run([ADB, "-s", TABLET, "logcat", "-c"])

# Bring MainActivity to front
run("am start -n org.itantra.app/.MainActivity")
time.sleep(1)

# Click btn_mode_server at [42,198][591,258] -> center (316, 228)
print("Clicking btn_mode_server at (316, 228)...")
run("input tap 316 228")
time.sleep(2)

print("\n--- TABLET LOGCAT ---")
logs = subprocess.run([ADB, "-s", TABLET, "logcat", "-d"], capture_output=True, text=True).stdout
for line in logs.splitlines():
    if any(k in line for k in ["PHYSICAL_E2E", "TcpTransport", "MainActivity", "AndroidTransceiver", "reconnectWithConfig", "Server"]):
        print(line)

print("\n--- TABLET LISTENING SOCKET ---")
netstat = run("netstat -tlpn 2>/dev/null | grep 42173 || grep -i A4BD /proc/net/tcp6")
print(netstat)
