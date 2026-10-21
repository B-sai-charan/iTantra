import subprocess
import time
import re

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip()

def check_status():
    p_conn = adb_shell(PHONE, "dumpsys activity top | grep -E 'tv_connection_state' || echo ''")
    # Let's check logcat for transport state
    p_logs = adb_shell(PHONE, "logcat -d | grep -E 'PHYSICAL_E2E.*(CONNECTED|DISCONNECTED)' | tail -n 5")
    t_logs = adb_shell(TABLET, "logcat -d | grep -E 'PHYSICAL_E2E.*(CONNECTED|DISCONNECTED|LISTEN)' | tail -n 5")
    print("Phone state logs:\n", p_logs)
    print("\nTablet state logs:\n", t_logs)

if __name__ == "__main__":
    check_status()
