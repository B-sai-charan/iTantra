import subprocess
import time
import re

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip()

def main():
    print("==================================================")
    print("STEP 1: IDENTIFY NEW NETWORK STATE")
    print("==================================================")
    
    # Phone IP interfaces
    phone_ip_raw = adb_shell(PHONE, "ip -4 addr show")
    print("Phone IPv4 Interfaces:\n", phone_ip_raw)
    
    # Tablet IP interfaces & WiFi
    tab_ip_raw = adb_shell(TABLET, "ip -4 addr show")
    print("\nTablet IPv4 Interfaces:\n", tab_ip_raw)
    
    tab_wifi_status = adb_shell(TABLET, "cmd wifi status")
    print("\nTablet WiFi Status:\n", tab_wifi_status)
    
    tab_routes = adb_shell(TABLET, "ip route show")
    print("\nTablet Routes:\n", tab_routes)

if __name__ == "__main__":
    main()
