import os
import sys
import subprocess
import re

ADB_PATH = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
if not os.path.exists(ADB_PATH):
    ADB_PATH = "adb"

DEV_A = "10BE830QG8000MX"
DEV_B = "HNP097HQ"

def run_adb(dev, args):
    cmd = [ADB_PATH, "-s", dev] + args
    res = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
    return res.returncode, res.stdout, res.stderr

def get_net_info(dev, label):
    print(f"\n==========================================")
    print(f"NETWORK FORENSICS FOR {label} ({dev})")
    print(f"==========================================")
    
    # Wi-Fi State
    _, out, _ = run_adb(dev, ["shell", "dumpsys", "wifi"])
    cur_wifi = [line for line in out.splitlines() if "mWifiInfo" in line or "mNetworkInfo" in line or "Wi-Fi is" in line or "SSID" in line][:5]
    print(f"[{label}] Wi-Fi State snippets:")
    for l in cur_wifi:
        print("  ", l.strip())
        
    # IP Addr
    _, ip_out, _ = run_adb(dev, ["shell", "ip", "addr", "show", "wlan0"])
    if not ip_out.strip():
        _, ip_out, _ = run_adb(dev, ["shell", "ip", "addr"])
    print(f"[{label}] IP Addr (wlan0 / all):")
    for l in ip_out.splitlines():
        if "inet " in l or "wlan0" in l or "state UP" in l:
            print("  ", l.strip())
            
    # IP Route
    _, route_out, _ = run_adb(dev, ["shell", "ip", "route"])
    print(f"[{label}] IP Route:")
    for l in route_out.splitlines():
        print("  ", l.strip())
        
    # Netstat / ss for port 42173
    _, netstat_out, _ = run_adb(dev, ["shell", "netstat", "-tlpn"])
    if not netstat_out.strip():
        _, netstat_out, _ = run_adb(dev, ["shell", "cat", "/proc/net/tcp"])
    print(f"[{label}] Port 42173 / TCP Listen check:")
    found_42173 = False
    # 42173 in hex is A4BD
    for l in netstat_out.splitlines():
        if "42173" in l or ":A4BD" in l or "a4bd" in l.lower():
            print("  [PORT 42173 FOUND!]", l.strip())
            found_42173 = True
    if not found_42173:
        print("  Port 42173 NOT currently listening or visible in raw netstat.")
        
    # App PID
    _, pid_out, _ = run_adb(dev, ["shell", "pidof", "org.itantra.app"])
    print(f"[{label}] org.itantra.app PID: {pid_out.strip()}")

def main():
    get_net_info(DEV_A, "Device A (Phone)")
    get_net_info(DEV_B, "Device B (Tablet)")

if __name__ == "__main__":
    main()
