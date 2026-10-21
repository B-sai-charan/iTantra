import subprocess
import time
import re

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def check_roles():
    p_pid = subprocess.run([ADB, "-s", PHONE, "shell", "pidof org.itantra.app"], capture_output=True, text=True).stdout.strip()
    t_pid = subprocess.run([ADB, "-s", TABLET, "shell", "pidof org.itantra.app"], capture_output=True, text=True).stdout.strip()
    
    p_logs = subprocess.run([ADB, "-s", PHONE, "shell", f"logcat -d --pid={p_pid}"], capture_output=True, text=True).stdout
    t_logs = subprocess.run([ADB, "-s", TABLET, "shell", f"logcat -d --pid={t_pid}"], capture_output=True, text=True).stdout
    
    print("Phone recent role/transport lines:")
    for l in p_logs.splitlines()[-15:]:
        print("  [PHONE]", l)
        
    print("\nTablet recent role/transport lines:")
    for l in t_logs.splitlines()[-15:]:
        print("  [TABLET]", l)

if __name__ == "__main__":
    check_roles()
