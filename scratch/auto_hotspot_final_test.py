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

def get_network():
    t_wifi = adb_shell(TABLET, "cmd wifi status")
    m_ssid = re.search(r'SSID:\s*"([^"]+)"', t_wifi)
    ssid = m_ssid.group(1) if m_ssid else "None"

    p_ip_raw = adb_shell(PHONE, "ip -4 addr show")
    t_ip_raw = adb_shell(TABLET, "ip -4 addr show")

    p_ip = None
    t_ip = None

    for line in p_ip_raw.splitlines():
        if any(iface in line for iface in ['wlan2', 'swlan', 'softap', 'ap0', 'wlan1']):
            m = re.search(r'inet\s+([0-9\.]+)/\d+', line)
            if m:
                p_ip = m.group(1)
        elif 'wlan0' in line and not p_ip:
            m = re.search(r'inet\s+([0-9\.]+)/\d+', line)
            if m:
                p_ip = m.group(1)

    for line in t_ip_raw.splitlines():
        if 'wlan0' in line and '127.0.0.1' not in line:
            m = re.search(r'inet\s+([0-9\.]+)/\d+', line)
            if m:
                t_ip = m.group(1)

    return ssid, p_ip, t_ip

def run_suite(ssid, p_ip, t_ip):
    print("==================================================")
    print("FINAL CONTROLLED PHONE -> TABLET CONNECTION TEST")
    print("==================================================")
    print(f"SSID: {ssid}")
    print(f"Phone IP: {p_ip}")
    print(f"Tablet IP: {t_ip}")

    # 1. Confirm Tablet listening on 42173
    print("\n[STEP 1] Setting up Tablet Receiver...")
    adb_shell(TABLET, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    adb_shell(TABLET, "input tap 316 228") # Tap Receiver
    time.sleep(2)
    
    tab_netstat = adb_shell(TABLET, "netstat -tlpn 2>/dev/null | grep 42173 || grep -i A4BD /proc/net/tcp6")
    is_listening = "42173" in tab_netstat or "A4BD" in tab_netstat.upper()
    print(f"Tablet listening on 42173: {'YES' if is_listening else 'NO'} ({tab_netstat})")

    # 2. Connect Phone to Tablet
    print(f"\n[STEP 2] Phone Connecting to {t_ip}:42173...")
    adb_shell(PHONE, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    adb_shell(PHONE, "input tap 912 489") # Tap Transmitter
    time.sleep(1)
    
    # Enter IP
    adb_shell(PHONE, "input tap 486 639")
    time.sleep(0.3)
    adb_shell(PHONE, "input keyevent KEYCODE_MOVE_END")
    for _ in range(35):
        adb_shell(PHONE, "input keyevent KEYCODE_DEL")
    time.sleep(0.3)
    adb_shell(PHONE, f"input text {t_ip}")
    time.sleep(0.5)
    # Tap Connect
    adb_shell(PHONE, "input tap 1044 639")
    time.sleep(3)

    # 3. Check Phone UI
    adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
    p_xml = subprocess.run([ADB, "-s", PHONE, "shell", "cat /sdcard/dump.xml"], capture_output=True, text=True).stdout
    m_pstate = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', p_xml)
    initial_state = m_pstate.group(1) if m_pstate else "Unknown"
    print(f"Phone UI initial state: {initial_state}")

    # 4. 30-Second Continuous Stability Check
    print("\n[STEP 3] 30-Second Continuous Stability Check...")
    stable = True
    for sec in range(1, 31):
        time.sleep(1)
        if sec % 10 == 0:
            adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
            p_xml_curr = subprocess.run([ADB, "-s", PHONE, "shell", "cat /sdcard/dump.xml"], capture_output=True, text=True).stdout
            m_curr = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', p_xml_curr)
            curr_st = m_curr.group(1) if m_curr else "Unknown"
            print(f"  [T+{sec:02d}s] Phone UI: {curr_st}")
            if curr_st != "Connected":
                stable = False

    adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
    p_xml_30s = subprocess.run([ADB, "-s", PHONE, "shell", "cat /sdcard/dump.xml"], capture_output=True, text=True).stdout
    m_30s = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', p_xml_30s)
    state_30s = m_30s.group(1) if m_30s else "Unknown"
    print(f"Phone UI state at 30s: {state_30s}")

    # 5. Send ONE spoken English sentence from Phone
    print("\n[STEP 4] Live Speech Transmission (Phone -> Tablet)...")
    p_pid = adb_shell(PHONE, "pidof org.itantra.app")
    t_pid = adb_shell(TABLET, "pidof org.itantra.app")
    
    p_log_start = len(adb_shell(PHONE, f"logcat -d --pid={p_pid}").splitlines())
    t_log_start = len(adb_shell(TABLET, f"logcat -d --pid={t_pid}").splitlines())

    # Select English on Phone
    adb_shell(PHONE, "input tap 348 924")
    time.sleep(0.5)

    # Tap PTT to start capture
    print("Tapping PTT to start speech recording (630, 1803)...")
    adb_shell(PHONE, "input tap 630 1803")
    print(">>> SPEAK NOW INTO PHONE MICROPHONE (5 seconds) <<<")
    for i in range(5, 0, -1):
        print(f"Recording... {i}s")
        time.sleep(1)

    print("Tapping PTT to stop capture...")
    adb_shell(PHONE, "input tap 630 1803")
    
    print("Waiting 6 seconds for inference, transmission, and TTS synthesis...")
    time.sleep(6)

    # 6. Collect Logs
    p_new = adb_shell(PHONE, f"logcat -d --pid={p_pid}").splitlines()[p_log_start:]
    t_new = adb_shell(TABLET, f"logcat -d --pid={t_pid}").splitlines()[t_log_start:]

    actual_stt = "None"
    msg_sent = False
    for l in p_new:
        if "Transcript:" in l or "text=" in l:
            m_tr = re.search(r'Transcript:\s*\"([^\"]+)\"', l) or re.search(r'text=\'([^\']+)\'', l)
            if m_tr and actual_stt == "None":
                actual_stt = m_tr.group(1)
        if "M2_SEND" in l or "Outbound SpeechMessage" in l:
            msg_sent = True

    msg_delivered = False
    tts_played = False
    for l in t_new:
        if any(k in l for k in ["M2_DECODE", "E2E-REMOTE", "M2_RECEIVE"]):
            msg_delivered = True
        if any(k in l for k in ["PLAYBACK", "M3-PLAYBACK-COMPLETE", "E2E-B-PLAYBACK"]):
            tts_played = True

    # 7. Final UI Check
    adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
    p_xml_final = subprocess.run([ADB, "-s", PHONE, "shell", "cat /sdcard/dump.xml"], capture_output=True, text=True).stdout
    m_final = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', p_xml_final)
    final_state = m_final.group(1) if m_final else "Unknown"

    print("\n==================================================")
    print("FINAL TEST REPORT")
    print("==================================================")
    print(f"Phone IP: {p_ip}")
    print(f"Tablet IP: {t_ip}")
    print(f"TCP connected: {'YES' if initial_state == 'Connected' else 'NO'}")
    print(f"30-second connection stable: {'YES' if state_30s == 'Connected' and stable else 'NO'}")
    print(f"Actual STT: {actual_stt}")
    print(f"SpeechMessage delivered: {'YES' if msg_delivered else 'NO'}")
    print(f"Tablet TTS: {'YES' if tts_played else 'NO'}")
    print(f"Phone still Connected after E2E: {'YES' if final_state == 'Connected' else 'NO'}")
    print(f"\nFINAL VERDICT: {'PASS' if (state_30s == 'Connected' and msg_delivered and tts_played and final_state == 'Connected') else 'FAIL'}")

if __name__ == "__main__":
    ssid, p_ip, t_ip = get_network()
    run_suite(ssid, p_ip, t_ip)
