import subprocess
import time
import re
import socket
import sys

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip()

def get_device_info(dev_id):
    model = adb_shell(dev_id, "getprop ro.product.model")
    brand = adb_shell(dev_id, "getprop ro.product.brand")
    os_ver = adb_shell(dev_id, "getprop ro.build.version.release")
    sdk = adb_shell(dev_id, "getprop ro.build.version.sdk")
    return f"{brand} {model} (Android {os_ver}, API {sdk}, Serial: {dev_id})"

def get_wifi_info(dev_id):
    status = adb_shell(dev_id, "cmd wifi status")
    ssid_match = re.search(r'SSID:\s*"([^"]+)"', status)
    bssid_match = re.search(r'BSSID:\s*([0-9a-fA-F:]+)', status)
    ssid = ssid_match.group(1) if ssid_match else "Unknown"
    bssid = bssid_match.group(1) if bssid_match else "Unknown"
    return ssid, bssid, status

def get_ip_info(dev_id):
    ip_out = adb_shell(dev_id, "ip -4 addr show")
    interfaces = {}
    for line in ip_out.splitlines():
        line = line.strip()
        m_iface = re.match(r'^\d+:\s+([a-zA-Z0-9_]+):', line)
        if m_iface:
            cur_iface = m_iface.group(1)
        m_inet = re.search(r'inet\s+([0-9\.]+)/\d+', line)
        if m_inet:
            ip = m_inet.group(1)
            if not ip.startswith("127."):
                interfaces[cur_iface] = ip
    return interfaces

def get_routes(dev_id):
    return adb_shell(dev_id, "ip route show")

def get_listening_sockets(dev_id, port=42173):
    hex_port = f"{port:04X}"
    tcp = adb_shell(dev_id, f"grep -i ':{hex_port}' /proc/net/tcp /proc/net/tcp6")
    netstat = adb_shell(dev_id, f"netstat -tlpn 2>/dev/null | grep {port}")
    return hex_port in tcp.upper() or str(port) in netstat, netstat, tcp

def test_raw_tcp_from_phone_to_tablet(tablet_ip, port=42173, timeout=4):
    cmd = f"toybox nc -w {timeout} -z {tablet_ip} {port} 2>&1"
    res = adb_shell(PHONE, cmd)
    return res

def test_raw_tcp_from_tablet_to_phone(phone_ip, port=42173, timeout=4):
    cmd = f"toybox nc -w {timeout} -z {phone_ip} {port} 2>&1"
    res = adb_shell(TABLET, cmd)
    return res

def main():
    print("==================================================")
    print("STEP 1: PRE-CHECK CURRENT DEVICE & APP STATE")
    print("==================================================")
    phone_info = get_device_info(PHONE)
    tablet_info = get_device_info(TABLET)
    print(f"Phone: {phone_info}")
    print(f"Tablet: {tablet_info}")

    phone_ssid, phone_bssid, _ = get_wifi_info(PHONE)
    tab_ssid, tab_bssid, _ = get_wifi_info(TABLET)
    
    phone_ips = get_ip_info(PHONE)
    tab_ips = get_ip_info(TABLET)
    
    tab_routes = get_routes(TABLET)
    phone_routes = get_routes(PHONE)

    print(f"Phone SSID: {phone_ssid} (BSSID: {phone_bssid})")
    print(f"Phone IPs: {phone_ips}")
    print(f"Tablet SSID: {tab_ssid} (BSSID: {tab_bssid})")
    print(f"Tablet IPs: {tab_ips}")
    print(f"Tablet Route: {tab_routes}")

    tab_listen, tab_netstat, tab_tcp_raw = get_listening_sockets(TABLET, 42173)
    print(f"\nTablet listening on 42173: {tab_listen}")
    print(f"Tablet netstat output:\n{tab_netstat}")
    print(f"Tablet /proc/net/tcp6:\n{tab_tcp_raw}")

    # Check SoftAP / Tethering on Phone
    phone_tether_raw = adb_shell(PHONE, "ip addr show")
    tether_ifaces = [line for line in phone_tether_raw.splitlines() if any(k in line for k in ['swlan', 'softap', 'ap0', 'wlan1', 'rndis'])]
    print(f"\nPhone Tether interfaces: {tether_ifaces}")

    tablet_target_ip = tab_ips.get("wlan0", list(tab_ips.values())[0] if tab_ips else "172.20.161.69")
    print(f"\nTarget Tablet IP for raw TCP test: {tablet_target_ip}")

    print("\n==================================================")
    print("STEP 4: RAW TCP TEST (Phone -> Tablet :42173)")
    print("==================================================")
    raw_res = test_raw_tcp_from_phone_to_tablet(tablet_target_ip, 42173)
    print(f"Phone -> Tablet ({tablet_target_ip}:42173) Result:\n'{raw_res}'")

    # Let's also do a socket test from Python if needed or via adb shell python/nc
    # In android toybox nc: if success, exit code is 0 and no output or 'open'
    code_res = subprocess.run([ADB, "-s", PHONE, "shell", f"toybox nc -w 3 -z {tablet_target_ip} 42173"], capture_output=True, text=True)
    print(f"Exit code: {code_res.returncode}, stdout: '{code_res.stdout.strip()}', stderr: '{code_res.stderr.strip()}'")

if __name__ == "__main__":
    main()
