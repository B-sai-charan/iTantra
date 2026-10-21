import subprocess
import re
import time

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip()

def main():
    print("=========================================")
    print("1. DEVICE MODEL & ANDROID VERSION")
    print("=========================================")
    phone_model = adb_shell(PHONE, "getprop ro.product.model")
    phone_brand = adb_shell(PHONE, "getprop ro.product.brand")
    phone_os = adb_shell(PHONE, "getprop ro.build.version.release")
    phone_sdk = adb_shell(PHONE, "getprop ro.build.version.sdk")
    
    tab_model = adb_shell(TABLET, "getprop ro.product.model")
    tab_brand = adb_shell(TABLET, "getprop ro.product.brand")
    tab_os = adb_shell(TABLET, "getprop ro.build.version.release")
    tab_sdk = adb_shell(TABLET, "getprop ro.build.version.sdk")
    
    print(f"Phone: {phone_brand} {phone_model} (Android {phone_os}, SDK {phone_sdk})")
    print(f"Tablet: {tab_brand} {tab_model} (Android {tab_os}, SDK {tab_sdk})")
    
    print("\n=========================================")
    print("2. PHONE NETWORK INTERFACES & TETHERING")
    print("=========================================")
    phone_ip_raw = adb_shell(PHONE, "ip addr show")
    print(phone_ip_raw)
    
    print("\n=========================================")
    print("3. TABLET NETWORK INTERFACES & WIFI")
    print("=========================================")
    tab_ip_raw = adb_shell(TABLET, "ip addr show")
    print(tab_ip_raw)
    
    tab_route = adb_shell(TABLET, "ip route show")
    print("\nTablet Routes:\n", tab_route)
    
    tab_wifi = adb_shell(TABLET, "cmd wifi status")
    print("\nTablet WiFi Status:\n", tab_wifi)

    print("\n=========================================")
    print("4. ITANTRA APP RUNNING STATE & SOCKETS")
    print("=========================================")
    print("Phone netstat :42173:")
    print(adb_shell(PHONE, "netstat -tlpn 2>/dev/null || ss -tlpn 2>/dev/null || grep -i 42173 /proc/net/tcp /proc/net/tcp6"))
    
    print("\nTablet netstat :42173:")
    print(adb_shell(TABLET, "netstat -tlpn 2>/dev/null || ss -tlpn 2>/dev/null || grep -i 42173 /proc/net/tcp /proc/net/tcp6"))

    print("\nTablet Listening /proc/net/tcp6 port A4BD (42173 hex):")
    print(adb_shell(TABLET, "grep -i A4BD /proc/net/tcp /proc/net/tcp6"))

if __name__ == "__main__":
    main()
