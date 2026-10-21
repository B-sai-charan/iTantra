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
    print("STEP 4: TABLET LISTENER")
    print("==================================================")
    tab_netstat, _, _ = adb_shell(TABLET, f"netstat -tlpn 2>/dev/null | grep {PORT}")
    tab_proc, _, _ = adb_shell(TABLET, "grep -i A4BD /proc/net/tcp /proc/net/tcp6")
    print(f"Tablet Listening Socket: {tab_netstat}")
    print(f"Tablet /proc/net/tcp6: {tab_proc}")

    print("\n==================================================")
    print("STEP 5: PHONE -> TABLET RAW TCP TEST")
    print("==================================================")
    out_phone_to_tab, err_phone_to_tab, code_phone_to_tab = adb_shell(PHONE, f"toybox nc -w 3 -z {TABLET_IP} {PORT} 2>&1")
    print(f"Raw TCP Phone -> Tablet ({TABLET_IP}:{PORT}):\nExit code: {code_phone_to_tab}, Output: '{out_phone_to_tab}'")
    if code_phone_to_tab == 0:
        phone_to_tab_res = "SUCCESS"
    else:
        phone_to_tab_res = f"FAILED: {out_phone_to_tab}"
    print(f"Result: {phone_to_tab_res}")

    print("\n==================================================")
    print("STEP 6: REVERSE RAW TCP TEST TABLET -> PHONE")
    print("==================================================")
    # Check if Phone is listening on 42173
    phone_listen, _, _ = adb_shell(PHONE, "grep -i A4BD /proc/net/tcp /proc/net/tcp6")
    print(f"Is Phone listening on 42173: {bool(phone_listen)}")
    out_tab_to_phone, err_tab_to_phone, code_tab_to_phone = adb_shell(TABLET, f"echo test | nc -w 3 -q 1 {PHONE_HOTSPOT_IP} {PORT} 2>&1")
    print(f"Raw TCP Tablet -> Phone ({PHONE_HOTSPOT_IP}:{PORT}):\nExit code: {code_tab_to_phone}, Output: '{out_tab_to_phone}'")
    if code_tab_to_phone == 0:
        tab_to_phone_res = "SUCCESS"
    else:
        tab_to_phone_res = f"FAILED / {out_tab_to_phone}"
    print(f"Result: {tab_to_phone_res}")

    print("\n==================================================")
    print("STEP 7: iTantra APPLICATION TEST (PHONE TX -> TABLET RX)")
    print("==================================================")
    if phone_to_tab_res == "SUCCESS":
        # Clear logcats
        adb_shell(PHONE, "logcat -c")
        adb_shell(TABLET, "logcat -c")

        # Bring Phone app to foreground
        adb_shell(PHONE, "am start -n org.itantra.app/.MainActivity")
        time.sleep(1)

        # Select Transmitter (btn_mode_client) at [609,198][1158,258] (or search UI)
        # Type IP into et_peer_ip and tap btn_connect_peer
        adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
        subprocess.run([ADB, "-s", PHONE, "pull", "/sdcard/dump.xml", "scratch/phone_ui.xml"], capture_output=True)
        with open("scratch/phone_ui.xml", "r", encoding="utf-8", errors="ignore") as f:
            pxml = f.read()

        m_client = re.search(r'resource-id="org.itantra.app:id/btn_mode_client"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', pxml)
        m_ip = re.search(r'resource-id="org.itantra.app:id/et_peer_ip"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', pxml)
        m_conn = re.search(r'resource-id="org.itantra.app:id/btn_connect_peer"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', pxml)

        if m_client:
            cx = (int(m_client.group(1)) + int(m_client.group(3))) // 2
            cy = (int(m_client.group(2)) + int(m_client.group(4))) // 2
            adb_shell(PHONE, f"input tap {cx} {cy}")
            print(f"Tapped Transmitter (Client) at ({cx}, {cy})")
            time.sleep(0.5)

        if m_ip:
            ix = (int(m_ip.group(1)) + int(m_ip.group(3))) // 2
            iy = (int(m_ip.group(2)) + int(m_ip.group(4))) // 2
            adb_shell(PHONE, f"input tap {ix} {iy}")
            time.sleep(0.3)
            # Clear text
            adb_shell(PHONE, "input keyevent KEYCODE_MOVE_END")
            for _ in range(25):
                adb_shell(PHONE, "input keyevent KEYCODE_DEL")
            time.sleep(0.3)
            adb_shell(PHONE, f"input text {TABLET_IP}")
            time.sleep(0.5)
            print(f"Entered Tablet IP {TABLET_IP} into Phone")

        if m_conn:
            bx = (int(m_conn.group(1)) + int(m_conn.group(3))) // 2
            by = (int(m_conn.group(2)) + int(m_conn.group(4))) // 2
            adb_shell(PHONE, f"input tap {bx} {by}")
            print(f"Tapped Connect button at ({bx}, {by})")

        # Wait 3 seconds for connection handshake
        time.sleep(3)

        # Inspect Phone and Tablet UI & logs
        p_logs, _, _ = adb_shell(PHONE, "logcat -d | grep -E 'PHYSICAL_E2E|TcpTransport|MainActivity|ConnectionState' | tail -n 30")
        t_logs, _, _ = adb_shell(TABLET, "logcat -d | grep -E 'PHYSICAL_E2E|TcpTransport|MainActivity|ConnectionState' | tail -n 30")

        adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
        subprocess.run([ADB, "-s", PHONE, "pull", "/sdcard/dump.xml", "scratch/phone_ui_after.xml"], capture_output=True)
        with open("scratch/phone_ui_after.xml", "r", encoding="utf-8", errors="ignore") as f:
            pxml_after = f.read()

        p_state = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', pxml_after)
        p_err = re.search(r'resource-id="org.itantra.app:id/tv_last_error"[^>]*text="([^"]+)"', pxml_after)

        print("\n--- PHONE LOGS ---")
        print(p_logs)
        print("\n--- TABLET LOGS ---")
        print(t_logs)
        print(f"\nPhone Final UI State: {p_state.group(1) if p_state else 'Unknown'}")
        print(f"Phone Final UI Error: {p_err.group(1) if p_err else 'None'}")
    else:
        print("Skipping iTantra application test because raw TCP failed.")

if __name__ == "__main__":
    main()
