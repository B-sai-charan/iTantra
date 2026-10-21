import subprocess
import time
import re

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"
TABLET_IP = "172.20.161.69"

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip()

print("1. Clearing Logcats...")
adb_shell(PHONE, "logcat -c")
adb_shell(TABLET, "logcat -c")

print("2. Launching Phone App and Entering Tablet IP...")
adb_shell(PHONE, "am start -n org.itantra.app/.MainActivity")
time.sleep(1)

# Select Transmitter
adb_shell(PHONE, "input tap 912 489")
time.sleep(1)

# Enter Tablet IP
adb_shell(PHONE, "input tap 486 639")
time.sleep(0.3)
adb_shell(PHONE, "input keyevent KEYCODE_MOVE_END")
for _ in range(35):
    adb_shell(PHONE, "input keyevent KEYCODE_DEL")
time.sleep(0.3)
adb_shell(PHONE, f"input text {TABLET_IP}")
time.sleep(0.5)

# Tap Connect
print("3. Tapping Connect...")
adb_shell(PHONE, "input tap 1044 639")
time.sleep(4)

# Check Phone logs
p_pid = adb_shell(PHONE, "pidof org.itantra.app")
p_logs = adb_shell(PHONE, f"logcat -d --pid={p_pid}")
print("\n--- Phone Logcat ---")
for l in p_logs.splitlines():
    if any(k in l for k in ["PHYSICAL_E2E", "MainActivity", "SOCKET_CONNECTED", "TRANSPORT", "CONNECT_FAILED", "ConnectionState"]):
        print(" ", l)

# Check Phone UI
adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
p_xml = subprocess.run([ADB, "-s", PHONE, "shell", "cat /sdcard/dump.xml"], capture_output=True, text=True).stdout
m_pstate = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', p_xml)
m_perr = re.search(r'resource-id="org.itantra.app:id/tv_last_error"[^>]*text="([^"]+)"', p_xml)

print(f"\nPhone UI Connection State: {m_pstate.group(1) if m_pstate else 'Unknown'}")
print(f"Phone UI Error: {m_perr.group(1) if m_perr else 'None'}")
