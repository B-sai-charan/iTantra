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

def run_suite():
    print("==================================================")
    print("iTantra SIH 2026 — Forensic Network Test")
    print("PHONE -> TABLET HOTSPOT VALIDATION")
    print("==================================================")
    
    # 1. Device Info
    phone_model = adb_shell(PHONE, "getprop ro.product.model")
    phone_brand = adb_shell(PHONE, "getprop ro.product.brand")
    phone_os = adb_shell(PHONE, "getprop ro.build.version.release")
    phone_sdk = adb_shell(PHONE, "getprop ro.build.version.sdk")
    
    tab_model = adb_shell(TABLET, "getprop ro.product.model")
    tab_brand = adb_shell(TABLET, "getprop ro.product.brand")
    tab_os = adb_shell(TABLET, "getprop ro.build.version.release")
    tab_sdk = adb_shell(TABLET, "getprop ro.build.version.sdk")

    # 2. Network Info
    tab_wifi_status = adb_shell(TABLET, "cmd wifi status")
    tab_ssid_match = re.search(r'SSID:\s*"([^"]+)"', tab_wifi_status)
    tab_ssid = tab_ssid_match.group(1) if tab_ssid_match else "Unknown"

    tab_ip_raw = adb_shell(TABLET, "ip -4 addr show")
    tab_ip = "Unknown"
    tab_iface = "wlan0"
    for line in tab_ip_raw.splitlines():
        if "inet " in line and "127.0.0.1" not in line:
            m = re.search(r'inet\s+([0-9\.]+)/\d+.*(wlan\d+)', line)
            if m:
                tab_ip = m.group(1)
                tab_iface = m.group(2)
            else:
                m2 = re.search(r'inet\s+([0-9\.]+)/\d+', line)
                if m2:
                    tab_ip = m2.group(1)

    tab_route = adb_shell(TABLET, "ip route show")
    tab_gw = "Unknown"
    for line in tab_route.splitlines():
        if "default via" in line:
            tab_gw = line.split()[2]
        elif "scope link" in line and tab_gw == "Unknown":
            # e.g. 192.168.43.0/24 dev wlan0 proto kernel scope link src 192.168.43.100
            m_gw = re.search(r'src\s+([0-9\.]+)', line)
            if m_gw:
                # Often gateway is .1
                prefix = '.'.join(m_gw.group(1).split('.')[:3])
                tab_gw = f"{prefix}.1 (inferred)"

    # Phone Hotspot IP
    phone_ip_raw = adb_shell(PHONE, "ip -4 addr show")
    phone_hotspot_ip = tab_gw.replace(" (inferred)", "")
    for line in phone_ip_raw.splitlines():
        if any(iface_name in line for iface_name in ['swlan', 'softap', 'ap0', 'wlan1', 'rndis']):
            m_pip = re.search(r'inet\s+([0-9\.]+)/\d+', line)
            if m_pip:
                phone_hotspot_ip = m_pip.group(1)

    # 3. Tablet Listener
    tab_listen_raw = adb_shell(TABLET, "netstat -tlpn 2>/dev/null | grep 42173 || grep -i A4BD /proc/net/tcp6")
    tab_is_listening = "42173" in tab_listen_raw or "A4BD" in tab_listen_raw.upper()
    tab_bind = "[::]:42173" if tab_is_listening else "NOT LISTENING"

    # 4. Raw TCP Test Phone -> Tablet :42173
    cmd_raw = f"toybox nc -w 3 -z {tab_ip} 42173 2>&1"
    raw_res = adb_shell(PHONE, cmd_raw)
    raw_status = "SUCCESS (Connected)" if raw_res == "" or "open" in raw_res.lower() else raw_res

    # Reverse Raw TCP Tablet -> Phone :42173
    rev_raw = adb_shell(TABLET, f"toybox nc -w 3 -z {phone_hotspot_ip} 42173 2>&1")
    rev_status = "SUCCESS (Connected)" if rev_raw == "" or "open" in rev_raw.lower() else rev_raw

    # 5. App Test if Raw TCP is success
    app_res = "NOT RUN (Raw TCP failed/pending)"
    if "SUCCESS" in raw_status:
        # Clear logcat on phone
        adb_shell(PHONE, "logcat -c")
        
        # Bring iTantra to foreground on Phone, enter IP, click connect
        adb_shell(PHONE, "am start -n org.itantra.app/.MainActivity")
        time.sleep(1)
        # Select Transmitter (rb_tx)
        # Find ID for rb_tx, et_peer_ip, btn_connect
        # We can use adb shell input tap or UI automate
        pass

    print(f"\n--- COLLECTED DATA ---")
    print(f"Phone Model: {phone_brand} {phone_model} (Android {phone_os}, API {phone_sdk})")
    print(f"Tablet Model: {tab_brand} {tab_model} (Android {tab_os}, API {tab_sdk})")
    print(f"SSID: {tab_ssid}")
    print(f"Phone Hotspot IP: {phone_hotspot_ip}")
    print(f"Tablet Hotspot IP: {tab_ip}")
    print(f"Tablet Gateway: {tab_gw}")
    print(f"Tablet Interface: {tab_iface}")
    print(f"Tablet Listening: {'YES' if tab_is_listening else 'NO'} ({tab_bind})")
    print(f"Raw TCP Phone -> Tablet: {raw_status}")
    print(f"Raw TCP Tablet -> Phone: {rev_status}")

if __name__ == "__main__":
    run_suite()
