import subprocess
import time
import re
import sys

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip()

def check_hotspot():
    t_wifi = adb_shell(TABLET, "cmd wifi status")
    m = re.search(r'SSID:\s*"([^"]+)"', t_wifi)
    ssid = m.group(1) if m else "None"
    
    t_ips = [line.strip() for line in adb_shell(TABLET, "ip -4 addr show").splitlines() if "inet " in line and "127.0.0.1" not in line]
    p_ips = [line.strip() for line in adb_shell(PHONE, "ip -4 addr show").splitlines() if "inet " in line and "127.0.0.1" not in line]
    
    return ssid, t_ips, p_ips

def main():
    ssid, t_ips, p_ips = check_hotspot()
    print(f"Tablet SSID: {ssid}")
    print(f"Tablet IPs: {t_ips}")
    print(f"Phone IPs: {p_ips}")
    
    if "BOYS AP" in ssid:
        print("\n[STATUS] Devices are currently on campus AP ('BOYS AP-2').")
        print("Please enable Phone Personal Hotspot and connect Tablet to it.")
    else:
        print(f"\n[STATUS] Devices connected to Hotspot: '{ssid}'!")

if __name__ == "__main__":
    main()
