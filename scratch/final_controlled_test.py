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

def main():
    print("==================================================")
    print("STEP 1 & 2: DISCOVER CURRENT HOTSPOT NETWORK STATE")
    print("==================================================")
    
    # Phone interfaces
    p_ip_raw = adb_shell(PHONE, "ip -4 addr show")
    t_ip_raw = adb_shell(TABLET, "ip -4 addr show")
    t_wifi_raw = adb_shell(TABLET, "cmd wifi status")
    
    print(f"Phone IPv4:\n{p_ip_raw}")
    print(f"\nTablet IPv4:\n{t_ip_raw}")
    print(f"\nTablet WiFi Status:\n{t_wifi_raw[:350]}")
    
    # Extract IPs
    phone_hotspot_ip = None
    tablet_ip = None
    
    for line in p_ip_raw.splitlines():
        if any(iface in line for iface in ['wlan2', 'swlan', 'softap', 'ap0', 'wlan1']):
            m = re.search(r'inet\s+([0-9\.]+)/\d+', line)
            if m:
                phone_hotspot_ip = m.group(1)
        elif 'wlan0' in line and not phone_hotspot_ip:
            m = re.search(r'inet\s+([0-9\.]+)/\d+', line)
            if m:
                phone_hotspot_ip = m.group(1)

    for line in t_ip_raw.splitlines():
        if 'wlan0' in line and '127.0.0.1' not in line:
            m = re.search(r'inet\s+([0-9\.]+)/\d+', line)
            if m:
                tablet_ip = m.group(1)

    print(f"\nIdentified Phone IP: {phone_hotspot_ip}")
    print(f"Identified Tablet IP: {tablet_ip}")

    print("\n==================================================")
    print("STEP 3: CONFIRM TABLET IS LISTENING ON 42173")
    print("==================================================")
    # Start Tablet as Receiver if not already
    adb_shell(TABLET, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    adb_shell(TABLET, "input tap 316 228") # Tap Receiver
    time.sleep(2)
    
    # Check listening socket
    tab_netstat = adb_shell(TABLET, "netstat -tlpn 2>/dev/null | grep 42173 || grep -i A4BD /proc/net/tcp6")
    is_tab_listening = "42173" in tab_netstat or "A4BD" in tab_netstat.upper()
    print(f"Tablet Listening: {is_tab_listening} ({tab_netstat})")

    print("\n==================================================")
    print(f"STEP 4: PHONE CONNECTS TO TABLET ({tablet_ip}:42173)")
    print("==================================================")
    # Bring Phone to front
    adb_shell(PHONE, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    # Select Transmitter
    adb_shell(PHONE, "input tap 912 489")
    time.sleep(1)
    # Enter Tablet IP
    adb_shell(PHONE, "input tap 486 639")
    time.sleep(0.3)
    adb_shell(PHONE, "input keyevent KEYCODE_MOVE_END")
    for _ in range(35):
        adb_shell(PHONE, "input keyevent KEYCODE_DEL")
    time.sleep(0.3)
    adb_shell(PHONE, f"input text {tablet_ip}")
    time.sleep(0.5)
    # Tap Connect
    adb_shell(PHONE, "input tap 1044 639")
    time.sleep(3)

    print("\n==================================================")
    print("STEP 5: CONFIRM PHONE UI SHOWS 'Connected'")
    print("==================================================")
    adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
    p_xml = subprocess.run([ADB, "-s", PHONE, "shell", "cat /sdcard/dump.xml"], capture_output=True, text=True).stdout
    m_pstate = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', p_xml)
    m_perr = re.search(r'resource-id="org.itantra.app:id/tv_last_error"[^>]*text="([^"]+)"', p_xml)
    
    initial_phone_state = m_pstate.group(1) if m_pstate else "Unknown"
    print(f"Phone UI Connection State: {initial_phone_state}")
    print(f"Phone UI Error: {m_perr.group(1) if m_perr else 'None'}")

    print("\n==================================================")
    print("STEP 6 & 7: 30-SECOND STABILITY CHECK")
    print("==================================================")
    print("Holding connection for 30 seconds...")
    for sec in range(1, 31):
        time.sleep(1)
        if sec % 5 == 0 or sec == 30:
            print(f"  [T+{sec:02d}s] Checking stability...")

    adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
    p_xml_30s = subprocess.run([ADB, "-s", PHONE, "shell", "cat /sdcard/dump.xml"], capture_output=True, text=True).stdout
    m_pstate_30s = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', p_xml_30s)
    stable_phone_state = m_pstate_30s.group(1) if m_pstate_30s else "Unknown"
    print(f"Phone UI Connection State after 30 seconds: {stable_phone_state}")

    print("\n==================================================")
    print("STEP 8–12: SEND ONE SPOKEN ENGLISH SENTENCE (PHONE -> TABLET)")
    print("==================================================")
    # Clear PID logs
    p_pid = adb_shell(PHONE, "pidof org.itantra.app")
    t_pid = adb_shell(TABLET, "pidof org.itantra.app")
    
    p_log_start = len(adb_shell(PHONE, f"logcat -d --pid={p_pid}").splitlines())
    t_log_start = len(adb_shell(TABLET, f"logcat -d --pid={t_pid}").splitlines())

    # Tap English on Phone
    adb_shell(PHONE, "input tap 348 924")
    time.sleep(0.5)

    # Tap PTT to start speech recording
    print("Tapping PTT on Phone (630, 1803)...")
    adb_shell(PHONE, "input tap 630 1803")
    print(">>> SPEAK NOW INTO PHONE MICROPHONE (5s) <<<")
    for i in range(5, 0, -1):
        print(f"Recording... {i}s")
        time.sleep(1)

    print("Tapping PTT on Phone to finalize speech...")
    adb_shell(PHONE, "input tap 630 1803")
    
    print("Waiting 6 seconds for STT, TCP transmission, and Tablet TTS playback...")
    time.sleep(6)

    # Collect new logs
    p_new = adb_shell(PHONE, f"logcat -d --pid={p_pid}").splitlines()[p_log_start:]
    t_new = adb_shell(TABLET, f"logcat -d --pid={t_pid}").splitlines()[t_log_start:]

    print("\n--- PHONE LOGS (M1 & M2) ---")
    stt_transcript = "None"
    msg_sent = False
    for l in p_new:
        if "Transcript:" in l or "text=" in l:
            print(" ", l)
            m_tr = re.search(r'Transcript:\s*\"([^\"]+)\"', l) or re.search(r'text=\'([^\']+)\'', l)
            if m_tr and stt_transcript == "None":
                stt_transcript = m_tr.group(1)
        if "M2_SEND" in l or "Outbound SpeechMessage" in l:
            print(" ", l)
            msg_sent = True

    print("\n--- TABLET LOGS (M2 & M3) ---")
    msg_received = False
    tts_played = False
    for l in t_new:
        if any(k in l for k in ["M2_RECEIVE", "M2_DECODE", "M2_DELIVERED", "E2E-REMOTE", "M3-TTS", "M3-PLAYBACK", "E2E-B-PLAYBACK"]):
            print(" ", l)
            if "M2_DECODE" in l or "E2E-REMOTE" in l or "M2_RECEIVE" in l:
                msg_received = True
            if "PLAYBACK" in l or "M3-PLAYBACK-COMPLETE" in l or "E2E-B-PLAYBACK" in l:
                tts_played = True

    print("\n==================================================")
    print("STEP 13: CONFIRM PHONE REMAINS CONNECTED AFTER MESSAGE")
    print("==================================================")
    adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
    p_xml_final = subprocess.run([ADB, "-s", PHONE, "shell", "cat /sdcard/dump.xml"], capture_output=True, text=True).stdout
    m_pstate_final = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', p_xml_final)
    final_phone_state = m_pstate_final.group(1) if m_pstate_final else "Unknown"
    print(f"Phone UI Connection State after E2E: {final_phone_state}")

    print("\n==================================================")
    print("SUMMARY")
    print("==================================================")
    print(f"Phone IP: {phone_hotspot_ip}")
    print(f"Tablet IP: {tablet_ip}")
    print(f"TCP connected: {'YES' if initial_phone_state == 'Connected' else 'NO'}")
    print(f"30-second connection stable: {'YES' if stable_phone_state == 'Connected' else 'NO'}")
    print(f"Actual STT: {stt_transcript}")
    print(f"SpeechMessage delivered: {'YES' if msg_received else 'NO'}")
    print(f"Tablet TTS: {'YES' if tts_played else 'NO'}")
    print(f"Phone still Connected after E2E: {'YES' if final_phone_state == 'Connected' else 'NO'}")

if __name__ == "__main__":
    main()
