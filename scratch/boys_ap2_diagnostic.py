import subprocess
import time
import re

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"
PORT = 42173

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip(), res.stderr.strip(), res.returncode

def main():
    print("==================================================")
    print("BOYS AP-2 ONLY — PHONE -> TABLET CONNECTIVITY TEST")
    print("==================================================")

    # 1. Device and WiFi Info
    p_wifi_out, _, _ = adb_shell(PHONE, "cmd wifi status")
    t_wifi_out, _, _ = adb_shell(TABLET, "cmd wifi status")

    p_ssid_match = re.search(r'SSID:\s*"([^"]+)"', p_wifi_out)
    p_bssid_match = re.search(r'BSSID:\s*([0-9a-fA-F:]+)', p_wifi_out)
    p_ssid = p_ssid_match.group(1) if p_ssid_match else "Unknown"
    p_bssid = p_bssid_match.group(1) if p_bssid_match else "Unknown"

    t_ssid_match = re.search(r'SSID:\s*"([^"]+)"', t_wifi_out)
    t_bssid_match = re.search(r'BSSID:\s*([0-9a-fA-F:]+)', t_wifi_out)
    t_ssid = t_ssid_match.group(1) if t_ssid_match else "Unknown"
    t_bssid = t_bssid_match.group(1) if t_bssid_match else "Unknown"

    # 2. IP and Route Info
    p_ip_out, _, _ = adb_shell(PHONE, "ip -4 addr show wlan0")
    t_ip_out, _, _ = adb_shell(TABLET, "ip -4 addr show wlan0")

    p_ip_match = re.search(r'inet\s+([0-9\.]+)/(\d+)', p_ip_out)
    p_ip = p_ip_match.group(1) if p_ip_match else "Unknown"
    p_cidr = p_ip_match.group(2) if p_ip_match else "16"

    t_ip_match = re.search(r'inet\s+([0-9\.]+)/(\d+)', t_ip_out)
    t_ip = t_ip_match.group(1) if t_ip_match else "Unknown"
    t_cidr = t_ip_match.group(2) if t_ip_match else "16"

    p_route, _, _ = adb_shell(PHONE, "ip route show")
    t_route, _, _ = adb_shell(TABLET, "ip route show")

    print(f"Phone SSID: {p_ssid} | BSSID: {p_bssid} | IP: {p_ip}/{p_cidr}")
    print(f"Tablet SSID: {t_ssid} | BSSID: {t_bssid} | IP: {t_ip}/{t_cidr}")
    print(f"Same BSSID: {'YES' if p_bssid.lower() == t_bssid.lower() else 'NO'}")
    print(f"Phone Routes:\n{p_route}")
    print(f"Tablet Routes:\n{t_route}")

    # 3. Setup Tablet Receiver
    print("\n==================================================")
    print("STEP 3: TABLET RECEIVER & LISTENING SOCKET")
    print("==================================================")
    adb_shell(TABLET, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    adb_shell(TABLET, "input tap 316 228") # Tap Receiver (Server)
    time.sleep(2)

    t_listen, _, _ = adb_shell(TABLET, f"netstat -tlpn 2>/dev/null | grep {PORT} || grep -i A4BD /proc/net/tcp6")
    is_tab_listening = str(PORT) in t_listen or "A4BD" in t_listen.upper()
    print(f"Tablet listening on TCP {PORT}: {'LISTENING' if is_tab_listening else 'NOT LISTENING'} ({t_listen})")

    # 4. Raw TCP Test: Phone -> Tablet
    print("\n==================================================")
    print(f"STEP 4: RAW TCP TEST: PHONE -> TABLET ({t_ip}:{PORT})")
    print("==================================================")
    p_nc_out, p_nc_err, p_nc_code = adb_shell(PHONE, f"toybox nc -w 4 -z {t_ip} {PORT} 2>&1")
    print(f"Phone -> Tablet Raw TCP output: '{p_nc_out}' (code={p_nc_code})")

    if p_nc_code == 0 and ("open" in p_nc_out.lower() or p_nc_out == ""):
        raw_result = "SUCCESS"
    elif "No route" in p_nc_out or "EHOSTUNREACH" in p_nc_out:
        raw_result = "EHOSTUNREACH"
    elif "Connection refused" in p_nc_out:
        raw_result = "CONNECTION REFUSED"
    elif "Timeout" in p_nc_out or "timed out" in p_nc_out or p_nc_code != 0:
        raw_result = "TIMEOUT"
    else:
        raw_result = f"OTHER ({p_nc_out})"

    print(f"Phone -> Tablet Raw TCP Result: {raw_result}")

    # 5. Reverse Raw TCP Test: Tablet -> Phone
    print("\n==================================================")
    print(f"STEP 5: REVERSE RAW TCP TEST: TABLET -> PHONE ({p_ip}:{PORT})")
    print("==================================================")
    # Ensure Phone is listening on 42173 for reverse test if needed or check existing state
    # Let's check if Phone has an open port or start server temporarily on Phone
    # If phone is not listening, testing connection will show CONNECTION REFUSED (which proves route reachable) or TIMEOUT/EHOSTUNREACH (route blocked)
    t_nc_out, t_nc_err, t_nc_code = adb_shell(TABLET, f"echo test | nc -w 4 -q 1 {p_ip} {PORT} 2>&1")
    print(f"Tablet -> Phone Raw TCP output: '{t_nc_out}' (code={t_nc_code})")

    if t_nc_code == 0:
        rev_result = "SUCCESS (Connected)"
    elif "Connection refused" in t_nc_out:
        rev_result = "CONNECTION REFUSED (Route exists, but no listener on Phone)"
    elif "No route" in t_nc_out:
        rev_result = "EHOSTUNREACH"
    elif "Timeout" in t_nc_out or t_nc_code != 0:
        rev_result = "TIMEOUT"
    else:
        rev_result = f"OTHER ({t_nc_out})"

    print(f"Tablet -> Phone Raw TCP Result: {rev_result}")

    # 6. iTantra Application Test if raw TCP succeeds
    print("\n==================================================")
    print("STEP 6: iTantra APPLICATION TEST")
    print("==================================================")
    if raw_result == "SUCCESS":
        print("Raw TCP succeeded, testing iTantra Connect...")
        adb_shell(PHONE, "am start -n org.itantra.app/.MainActivity")
        time.sleep(1)
        adb_shell(PHONE, "input tap 912 489") # Transmitter
        time.sleep(1)
        # Enter Tablet IP
        adb_shell(PHONE, "input tap 486 639")
        time.sleep(0.3)
        adb_shell(PHONE, "input keyevent KEYCODE_MOVE_END")
        for _ in range(35):
            adb_shell(PHONE, "input keyevent KEYCODE_DEL")
        time.sleep(0.3)
        adb_shell(PHONE, f"input text {t_ip}")
        time.sleep(0.5)
        adb_shell(PHONE, "input tap 1044 639") # Connect
        time.sleep(4)

        # Check UI State
        adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
        p_xml, _, _ = adb_shell(PHONE, "cat /sdcard/dump.xml")
        m_state = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', p_xml)
        app_result = m_state.group(1) if m_state else "Unknown"
    else:
        print(f"Skipping iTantra Connect because Raw TCP returned {raw_result}.")
        # Let's inspect Phone's last error on UI
        adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
        p_xml, _, _ = adb_shell(PHONE, "cat /sdcard/dump.xml")
        m_state = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', p_xml)
        m_err = re.search(r'resource-id="org.itantra.app:id/tv_last_error"[^>]*text="([^"]+)"', p_xml)
        app_result = "FAILED"
        exact_err = m_err.group(1) if m_err else f"Raw TCP {raw_result} from {p_ip} to {t_ip}:{PORT}"

    print(f"iTantra Connection Result: {app_result}")

if __name__ == "__main__":
    main()
