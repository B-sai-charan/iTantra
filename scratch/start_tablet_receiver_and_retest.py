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
    return res.stdout.strip()

def inspect_and_start_tablet():
    print("=== INSPECTING TABLET UI ===")
    adb_shell(TABLET, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    
    adb_shell(TABLET, "uiautomator dump /sdcard/dump.xml")
    xml = adb_shell(TABLET, "cat /sdcard/dump.xml")
    
    rx_match = re.search(r'resource-id="org.itantra.app:id/rb_rx"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    btn_listen_match = re.search(r'resource-id="org.itantra.app:id/btn_listen"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml)
    state_match = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', xml)
    
    print(f"Current Tablet UI Connection State: {state_match.group(1) if state_match else 'Unknown'}")
    
    # Tap Receiver radio button (rb_rx)
    if rx_match:
        x = (int(rx_match.group(1)) + int(rx_match.group(3))) // 2
        y = (int(rx_match.group(2)) + int(rx_match.group(4))) // 2
        adb_shell(TABLET, f"input tap {x} {y}")
        print(f"Tapped Receiver (rb_rx) at ({x}, {y})")
        time.sleep(0.5)

    # Tap Start Listening button (btn_listen) if visible
    if btn_listen_match:
        x = (int(btn_listen_match.group(1)) + int(btn_listen_match.group(3))) // 2
        y = (int(btn_listen_match.group(2)) + int(btn_listen_match.group(4))) // 2
        adb_shell(TABLET, f"input tap {x} {y}")
        print(f"Tapped Start Listening (btn_listen) at ({x}, {y})")
        time.sleep(1)

    # Check listening socket on Tablet
    tab_netstat = adb_shell(TABLET, f"netstat -tlpn 2>/dev/null | grep {PORT}")
    tab_proc = adb_shell(TABLET, "grep -i A4BD /proc/net/tcp /proc/net/tcp6")
    print(f"\nTablet Netstat 42173: {tab_netstat}")
    print(f"Tablet /proc/net/tcp6: {tab_proc}")

    # Re-check UI state
    adb_shell(TABLET, "uiautomator dump /sdcard/dump.xml")
    xml2 = adb_shell(TABLET, "cat /sdcard/dump.xml")
    state2 = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', xml2)
    print(f"Tablet UI State after Start Listening: {state2.group(1) if state2 else 'Unknown'}")

if __name__ == "__main__":
    inspect_and_start_tablet()
