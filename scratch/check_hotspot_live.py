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
    phone_ip = adb_shell(PHONE, "ip -4 addr show")
    tab_ip = adb_shell(TABLET, "ip -4 addr show")
    tab_ssid = adb_shell(TABLET, "dumpsys wifi | grep -E 'mWifiInfo|SSID:' | head -n 3")
    print(f"[{time.strftime('%H:%M:%S')}]")
    print(f"Phone IPv4:\n{phone_ip}")
    print(f"Tablet IPv4:\n{tab_ip}")
    print(f"Tablet SSID info:\n{tab_ssid}")

if __name__ == "__main__":
    check_status()
