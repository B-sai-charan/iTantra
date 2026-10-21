import subprocess
import time
import re

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"
TABLET_IP = "10.179.231.149"
PHONE_HOTSPOT_IP = "10.179.231.166"
PORT = 42173

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip(), res.stderr.strip(), res.returncode

def main():
    print("==================================================")
    print("STEP 3 & 4: TABLET LISTENER VERIFICATION")
    print("==================================================")
    
    # Check netstat / proc / tcp6 on Tablet
    tab_netstat, _, _ = adb_shell(TABLET, f"netstat -tlpn 2>/dev/null | grep {PORT}")
    tab_proc, _, _ = adb_shell(TABLET, "grep -i A4BD /proc/net/tcp /proc/net/tcp6")
    
    print(f"Tablet netstat 42173:\n{tab_netstat}")
    print(f"Tablet /proc/net/tcp6 (port A4BD = 42173):\n{tab_proc}")
    
    is_listening = bool(tab_netstat or tab_proc)
    print(f"Tablet listening on 42173: {'YES' if is_listening else 'NO'}")

    print("\n==================================================")
    print(f"STEP 5 & 6: RAW TCP TEST PHONE -> TABLET ({TABLET_IP}:{PORT})")
    print("==================================================")
    
    # Run toybox nc on Phone
    out, err, code = adb_shell(PHONE, f"toybox nc -w 3 -z {TABLET_IP} {PORT} 2>&1")
    print(f"Phone -> Tablet ({TABLET_IP}:{PORT}) Output: '{out}' (code={code})")
    
    if code == 0 and ("open" in out.lower() or out == ""):
        raw_phone_to_tab = "SUCCESS"
    elif "No route" in out or "EHOSTUNREACH" in out:
        raw_phone_to_tab = "EHOSTUNREACH"
    elif "Connection refused" in out:
        raw_phone_to_tab = "CONNECTION REFUSED"
    elif "Timeout" in out or "timed out" in out:
        raw_phone_to_tab = "TIMEOUT"
    else:
        raw_phone_to_tab = f"OTHER: {out}"

    print(f"Classification Phone -> Tablet Raw TCP: {raw_phone_to_tab}")

    print("\n==================================================")
    print(f"STEP 7: REVERSE RAW TCP TEST TABLET -> PHONE ({PHONE_HOTSPOT_IP}:{PORT})")
    print("==================================================")
    # Check if Phone is listening on 42173 first
    phone_listen, _, _ = adb_shell(PHONE, "grep -i A4BD /proc/net/tcp /proc/net/tcp6")
    print(f"Is Phone listening on 42173? {bool(phone_listen)}")
    
    # Test from tablet to phone
    out_rev, err_rev, code_rev = adb_shell(TABLET, f"echo test | nc -w 3 -q 1 {PHONE_HOTSPOT_IP} {PORT} 2>&1")
    print(f"Tablet -> Phone ({PHONE_HOTSPOT_IP}:{PORT}) Output: '{out_rev}' (code={code_rev})")
    
    if code_rev == 0:
        raw_tab_to_phone = "SUCCESS"
    elif "Connection refused" in out_rev:
        raw_tab_to_phone = "CONNECTION REFUSED (No server listening on Phone :42173)"
    elif "No route" in out_rev:
        raw_tab_to_phone = "EHOSTUNREACH"
    elif "Timeout" in out_rev:
        raw_tab_to_phone = "TIMEOUT"
    else:
        raw_tab_to_phone = f"Result code={code_rev}, out='{out_rev}'"
        
    print(f"Classification Tablet -> Phone Raw TCP: {raw_tab_to_phone}")

    print("\n==================================================")
    print("STEP 8: iTantra APPLICATION TEST (PHONE TX -> TABLET RX)")
    print("==================================================")
    
    if raw_phone_to_tab == "SUCCESS":
        print("Raw TCP Succeeded! Executing iTantra connection test on Phone...")
        # Clear logcat
        adb_shell(PHONE, "logcat -c")
        adb_shell(TABLET, "logcat -c")
        
        # Bring app to foreground
        adb_shell(PHONE, "am start -n org.itantra.app/.MainActivity")
        time.sleep(1)
        
        # Update IP input field on Phone
        # Focus on et_peer_ip and input TABLET_IP
        # Let's inspect UI hierarchy to get bounds or use input tap
        # Dump UI hierarchy to find bounds of rb_tx, et_peer_ip, btn_connect
        adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
        adb_shell(PHONE, "cat /sdcard/dump.xml > /sdcard/ui.xml")
        xml_out, _, _ = adb_shell(PHONE, "cat /sdcard/ui.xml")
        
        print("Searching UI elements on Phone...")
        tx_match = re.search(r'resource-id="org.itantra.app:id/rb_tx"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml_out)
        ip_match = re.search(r'resource-id="org.itantra.app:id/et_peer_ip"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml_out)
        btn_match = re.search(r'resource-id="org.itantra.app:id/btn_connect"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml_out)
        
        if tx_match:
            x = (int(tx_match.group(1)) + int(tx_match.group(3))) // 2
            y = (int(tx_match.group(2)) + int(tx_match.group(4))) // 2
            adb_shell(PHONE, f"input tap {x} {y}")
            time.sleep(0.5)

        if ip_match:
            x = (int(ip_match.group(1)) + int(ip_match.group(3))) // 2
            y = (int(ip_match.group(2)) + int(ip_match.group(4))) // 2
            adb_shell(PHONE, f"input tap {x} {y}")
            time.sleep(0.3)
            # Select all and delete
            adb_shell(PHONE, "input keyevent KEYCODE_MOVE_END")
            for _ in range(25):
                adb_shell(PHONE, "input keyevent KEYCODE_DEL")
            time.sleep(0.3)
            adb_shell(PHONE, f"input text {TABLET_IP}")
            time.sleep(0.5)

        if btn_match:
            x = (int(btn_match.group(1)) + int(btn_match.group(3))) // 2
            y = (int(btn_match.group(2)) + int(btn_match.group(4))) // 2
            adb_shell(PHONE, f"input tap {x} {y}")
            print(f"Tapped Connect button at ({x}, {y})")

        # Wait 3 seconds for connection
        time.sleep(3)
        
        # Read Phone logcat
        logcat_phone, _, _ = adb_shell(PHONE, "logcat -d | grep -E 'TcpTransport|MainActivity|SOCKET_CONNECTED|CONNECTION' | tail -n 25")
        print("\n--- Phone Logcat Messages ---")
        print(logcat_phone)
        
        # Read Tablet logcat
        logcat_tab, _, _ = adb_shell(TABLET, "logcat -d | grep -E 'TcpTransport|MainActivity|SOCKET_ACCEPTED|CONNECTION' | tail -n 25")
        print("\n--- Tablet Logcat Messages ---")
        print(logcat_tab)
        
        # Check UI connection state on Phone
        adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
        xml_after, _, _ = adb_shell(PHONE, "cat /sdcard/dump.xml")
        state_match = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', xml_after)
        err_match = re.search(r'resource-id="org.itantra.app:id/tv_last_error"[^>]*text="([^"]+)"', xml_after)
        
        ui_state = state_match.group(1) if state_match else "Unknown"
        ui_err = err_match.group(1) if err_match else "None"
        
        print(f"\nPhone Final UI State: '{ui_state}'")
        print(f"Phone Final UI Error: '{ui_err}'")
    else:
        print("Skipping iTantra application test because Raw TCP did not succeed.")

if __name__ == "__main__":
    main()
