import subprocess
import time
import re
import sys

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"
PORT = 42173

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip(), res.stderr.strip(), res.returncode

def main():
    print("==================================================")
    print("STEP 1 & 2: VERIFY NETWORK STATE ON BOYS AP-2")
    print("==================================================")
    p_wifi, _, _ = adb_shell(PHONE, "cmd wifi status")
    t_wifi, _, _ = adb_shell(TABLET, "cmd wifi status")

    p_ssid_m = re.search(r'SSID:\s*"([^"]+)"', p_wifi)
    p_bssid_m = re.search(r'BSSID:\s*([0-9a-fA-F:]+)', p_wifi)
    t_ssid_m = re.search(r'SSID:\s*"([^"]+)"', t_wifi)
    t_bssid_m = re.search(r'BSSID:\s*([0-9a-fA-F:]+)', t_wifi)

    p_ssid = p_ssid_m.group(1) if p_ssid_m else "Unknown"
    p_bssid = p_bssid_m.group(1) if p_bssid_m else "Unknown"
    t_ssid = t_ssid_m.group(1) if t_ssid_m else "Unknown"
    t_bssid = t_bssid_m.group(1) if t_bssid_m else "Unknown"

    p_ip_m = re.search(r'inet\s+([0-9\.]+)/\d+', adb_shell(PHONE, "ip -4 addr show wlan0")[0])
    t_ip_m = re.search(r'inet\s+([0-9\.]+)/\d+', adb_shell(TABLET, "ip -4 addr show wlan0")[0])

    p_ip = p_ip_m.group(1) if p_ip_m else "Unknown"
    t_ip = t_ip_m.group(1) if t_ip_m else "Unknown"

    print(f"Phone: SSID='{p_ssid}', BSSID='{p_bssid}', IP='{p_ip}'")
    print(f"Tablet: SSID='{t_ssid}', BSSID='{t_bssid}', IP='{t_ip}'")

    print("\n==================================================")
    print("STEP 3: SETUP TABLET RECEIVER & VERIFY LISTENER")
    print("==================================================")
    adb_shell(TABLET, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    adb_shell(TABLET, "input tap 316 228") # Receiver (Server)
    
    print("Polling Tablet listening socket...")
    is_tab_listening = False
    tab_listen_str = ""
    for i in range(15):
        time.sleep(1)
        netstat, _, _ = adb_shell(TABLET, f"netstat -tlpn 2>/dev/null | grep {PORT} || grep -i A4BD /proc/net/tcp6")
        if str(PORT) in netstat or "A4BD" in netstat.upper():
            is_tab_listening = True
            tab_listen_str = netstat
            print(f"  [T+{i+1}s] Tablet IS LISTENING:\n{netstat}")
            break
        else:
            print(f"  [T+{i+1}s] Waiting for Tablet listener...")

    print("\n==================================================")
    print(f"STEP 4: FRESH RAW TCP TEST: PHONE -> TABLET ({t_ip}:{PORT})")
    print("==================================================")
    p_nc_out, p_nc_err, p_nc_code = adb_shell(PHONE, f"toybox nc -w 4 -z {t_ip} {PORT} 2>&1")
    print(f"Phone -> Tablet Raw TCP: code={p_nc_code}, out='{p_nc_out}', err='{p_nc_err}'")
    
    raw_tcp_success = (p_nc_code == 0 and ("open" in p_nc_out.lower() or p_nc_out == ""))
    raw_result_str = "SUCCESS" if raw_tcp_success else f"FAIL ({p_nc_out})"
    print(f"Raw TCP Result: {raw_result_str}")

    print("\n==================================================")
    print("STEP 5 & 6: iTantra APP CONNECT ON PHONE & 15s LOGCAT CAPTURE")
    print("==================================================")
    # Get PIDs before connecting
    p_pid = adb_shell(PHONE, "pidof org.itantra.app")[0]
    t_pid = adb_shell(TABLET, "pidof org.itantra.app")[0]

    p_log_start = len(adb_shell(PHONE, f"logcat -d --pid={p_pid}")[0].splitlines())
    t_log_start = len(adb_shell(TABLET, f"logcat -d --pid={t_pid}")[0].splitlines())

    print("Configuring Phone as Transmitter...")
    adb_shell(PHONE, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    adb_shell(PHONE, "input tap 912 489") # Transmitter (Client)
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

    print(f"Tapping Connect on Phone to {t_ip}:{PORT} at timestamp {time.strftime('%H:%M:%S')}...")
    t_connect_start = time.time()
    adb_shell(PHONE, "input tap 1044 639")

    print("Capturing logs for 15 seconds continuously...")
    for sec in range(1, 16):
        time.sleep(1)
        if sec % 5 == 0:
            print(f"  [T+{sec:02d}s] Monitoring...")

    # Extract all new log lines
    p_new = adb_shell(PHONE, f"logcat -d --pid={p_pid}")[0].splitlines()[p_log_start:]
    t_new = adb_shell(TABLET, f"logcat -d --pid={t_pid}")[0].splitlines()[t_log_start:]

    print("\n==================================================")
    print("PHONE LOGCAT (15s Window)")
    print("==================================================")
    for l in p_new:
        if any(k in l for k in ["PHYSICAL_E2E", "MainActivity", "SOCKET", "TRANSPORT", "CONNECT", "ConnectionState", "Exception", "failed"]):
            print(" ", l)

    print("\n==================================================")
    print("TABLET LOGCAT (15s Window)")
    print("==================================================")
    for l in t_new:
        if any(k in l for k in ["PHYSICAL_E2E", "MainActivity", "SOCKET", "TRANSPORT", "CONNECT", "ConnectionState", "ACCEPTED"]):
            print(" ", l)

    # Check Phone UI state
    adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
    p_xml, _, _ = adb_shell(PHONE, "cat /sdcard/dump.xml")
    m_pstate = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', p_xml)
    m_perr = re.search(r'resource-id="org.itantra.app:id/tv_last_error"[^>]*text="([^"]+)"', p_xml)
    
    phone_ui_state = m_pstate.group(1) if m_pstate else "Unknown"
    phone_ui_err = m_perr.group(1) if m_perr else "None"

    print(f"\nFinal Phone UI State: {phone_ui_state}")
    print(f"Final Phone UI Error: {phone_ui_err}")

if __name__ == "__main__":
    main()
