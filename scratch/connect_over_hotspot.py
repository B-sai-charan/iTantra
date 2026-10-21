import subprocess
import time
import re

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"
TABLET_IP = "10.179.231.149"

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip()

def main():
    print("=== CONNECTING PHONE (TRANSMITTER) -> TABLET (RECEIVER) OVER HOTSPOT ===")
    # Clear logcats
    adb_shell(PHONE, "logcat -c")
    adb_shell(TABLET, "logcat -c")
    
    # Bring phone app to front
    adb_shell(PHONE, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    
    # Dump UI hierarchy to find et_peer_ip and btn_connect_peer
    adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
    subprocess.run([ADB, "-s", PHONE, "pull", "/sdcard/dump.xml", "scratch/p_ui.xml"], capture_output=True)
    with open("scratch/p_ui.xml", "r", encoding="utf-8", errors="ignore") as f:
        xml = f.read()

    m_ip = re.search(r'resource-id="org.itantra.app:id/et_peer_ip"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    m_conn = re.search(r'resource-id="org.itantra.app:id/btn_connect_peer"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    
    if m_ip:
        ix = (int(m_ip.group(1)) + int(m_ip.group(3))) // 2
        iy = (int(m_ip.group(2)) + int(m_ip.group(4))) // 2
        print(f"Clicking et_peer_ip at ({ix}, {iy})...")
        adb_shell(PHONE, f"input tap {ix} {iy}")
        time.sleep(0.3)
        adb_shell(PHONE, "input keyevent KEYCODE_MOVE_END")
        for _ in range(30):
            adb_shell(PHONE, "input keyevent KEYCODE_DEL")
        time.sleep(0.3)
        adb_shell(PHONE, f"input text {TABLET_IP}")
        time.sleep(0.5)
        print(f"Entered Tablet IP: {TABLET_IP}")

    if m_conn:
        bx = (int(m_conn.group(1)) + int(m_conn.group(3))) // 2
        by = (int(m_conn.group(2)) + int(m_conn.group(4))) // 2
        print(f"Clicking btn_connect_peer at ({bx}, {by})...")
        adb_shell(PHONE, f"input tap {bx} {by}")

    # Wait 3 seconds
    time.sleep(3)
    
    print("\n--- PHONE LOGCAT ---")
    p_logs = subprocess.run([ADB, "-s", PHONE, "logcat", "-d"], capture_output=True, text=True).stdout
    for line in p_logs.splitlines():
        if any(k in line for k in ["PHYSICAL_E2E", "TcpTransport", "MainActivity", "SOCKET_CONNECTED", "CONNECTED"]):
            print(line)

    print("\n--- TABLET LOGCAT ---")
    t_logs = subprocess.run([ADB, "-s", TABLET, "logcat", "-d"], capture_output=True, text=True).stdout
    for line in t_logs.splitlines():
        if any(k in line for k in ["PHYSICAL_E2E", "TcpTransport", "MainActivity", "SOCKET_ACCEPTED", "CONNECTED"]):
            print(line)

    # UI Verification
    adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
    subprocess.run([ADB, "-s", PHONE, "pull", "/sdcard/dump.xml", "scratch/p_ui_final.xml"], capture_output=True)
    with open("scratch/p_ui_final.xml", "r", encoding="utf-8", errors="ignore") as f:
        xml_p = f.read()

    adb_shell(TABLET, "uiautomator dump /sdcard/dump.xml")
    subprocess.run([ADB, "-s", TABLET, "pull", "/sdcard/dump.xml", "scratch/t_ui_final.xml"], capture_output=True)
    with open("scratch/t_ui_final.xml", "r", encoding="utf-8", errors="ignore") as f:
        xml_t = f.read()

    m_pstate = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', xml_p)
    m_perr = re.search(r'resource-id="org.itantra.app:id/tv_last_error"[^>]*text="([^"]+)"', xml_p)
    m_tstate = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', xml_t)

    print(f"\nPhone UI State: {m_pstate.group(1) if m_pstate else 'Unknown'}")
    print(f"Phone UI Error: {m_perr.group(1) if m_perr else 'None'}")
    print(f"Tablet UI State: {m_tstate.group(1) if m_tstate else 'Unknown'}")

if __name__ == "__main__":
    main()
