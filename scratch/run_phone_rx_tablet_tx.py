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
    print("STEP 1: VERIFY CURRENT NETWORK ON BOYS AP-2")
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

    print(f"Network SSID: Phone='{p_ssid}', Tablet='{t_ssid}'")
    print(f"Phone IP: {p_ip} (BSSID: {p_bssid})")
    print(f"Tablet IP: {t_ip} (BSSID: {t_bssid})")

    print("\n==================================================")
    print("STEP 2: CONFIGURE PHONE AS RECEIVER / SERVER")
    print("==================================================")
    adb_shell(PHONE, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    # Tap Receiver (Server) on Phone at (348, 489)
    adb_shell(PHONE, "input tap 348 489")

    print("Polling Phone TCP 42173 listening socket...")
    p_listening = False
    p_listen_str = ""
    for i in range(15):
        time.sleep(1)
        netstat, _, _ = adb_shell(PHONE, f"netstat -tlpn 2>/dev/null | grep {PORT} || grep -i A4BD /proc/net/tcp6")
        if str(PORT) in netstat or "A4BD" in netstat.upper():
            p_listening = True
            p_listen_str = netstat
            print(f"  [T+{i+1}s] Phone IS LISTENING on {PORT}:\n{netstat}")
            break
        else:
            print(f"  [T+{i+1}s] Waiting for Phone listener...")

    # Check Phone UI State
    adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
    p_xml, _, _ = adb_shell(PHONE, "cat /sdcard/dump.xml")
    p_ui_state_m = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', p_xml)
    p_ui_state = p_ui_state_m.group(1) if p_ui_state_m else "Unknown"
    print(f"Phone UI State: '{p_ui_state}'")

    print("\n==================================================")
    print(f"STEP 4: RAW TCP VALIDATION (TABLET -> PHONE: {p_ip}:{PORT})")
    print("==================================================")
    t_raw_out, t_raw_err, t_raw_code = adb_shell(TABLET, f"echo test | nc -w 4 -q 1 {p_ip} {PORT} 2>&1")
    print(f"Raw TCP Tablet -> Phone: code={t_raw_code}, out='{t_raw_out}', err='{t_raw_err}'")

    if t_raw_code == 0:
        raw_result = "SUCCESS"
    elif "Connection refused" in t_raw_out:
        raw_result = "CONNECTION REFUSED"
    elif "No route" in t_raw_out:
        raw_result = "EHOSTUNREACH"
    elif "Timeout" in t_raw_out or t_raw_code != 0:
        raw_result = "TIMEOUT"
    else:
        raw_result = f"OTHER ({t_raw_out})"

    print(f"Raw TCP Result: {raw_result}")

    print("\n==================================================")
    print("STEP 3 & 5: TABLET TRANSMITTER CONNECTION")
    print("==================================================")
    t_pid = adb_shell(TABLET, "pidof org.itantra.app")[0]
    p_pid = adb_shell(PHONE, "pidof org.itantra.app")[0]

    t_log_start = len(adb_shell(TABLET, f"logcat -d --pid={t_pid}")[0].splitlines())
    p_log_start = len(adb_shell(PHONE, f"logcat -d --pid={p_pid}")[0].splitlines())

    adb_shell(TABLET, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)

    # Tap Transmitter (Client) on Tablet at (880, 228)
    adb_shell(TABLET, "input tap 880 228")
    time.sleep(1)

    # Enter Phone IP
    adb_shell(TABLET, "input tap 500 303")
    time.sleep(0.3)
    adb_shell(TABLET, "input keyevent KEYCODE_MOVE_END")
    for _ in range(35):
        adb_shell(TABLET, "input keyevent KEYCODE_DEL")
    time.sleep(0.3)
    adb_shell(TABLET, f"input text {p_ip}")
    time.sleep(0.5)

    # Tap Connect on Tablet at (1092, 303)
    print(f"Tapping Connect on Tablet to Phone IP {p_ip}...")
    adb_shell(TABLET, "input tap 1092 303")
    time.sleep(4)

    # Check Tablet UI & logs
    adb_shell(TABLET, "uiautomator dump /sdcard/dump.xml")
    t_xml, _, _ = adb_shell(TABLET, "cat /sdcard/dump.xml")
    t_state_m = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', t_xml)
    t_err_m = re.search(r'resource-id="org.itantra.app:id/tv_last_error"[^>]*text="([^"]+)"', t_xml)

    t_ui_state = t_state_m.group(1) if t_state_m else "Unknown"
    t_ui_err = t_err_m.group(1) if t_err_m else "None"

    print(f"Tablet UI State: '{t_ui_state}'")
    print(f"Tablet UI Error: '{t_ui_err}'")

    # Check stability if connected
    is_connected = (t_ui_state == "Connected")
    stability_str = "DISCONNECTED"
    if is_connected:
        print("Connection established! Waiting 15 seconds to verify stability...")
        stable_count = 0
        for sec in range(1, 16):
            time.sleep(1)
            if sec % 5 == 0:
                adb_shell(TABLET, "uiautomator dump /sdcard/dump.xml")
                t_xml_cur, _, _ = adb_shell(TABLET, "cat /sdcard/dump.xml")
                st_cur = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', t_xml_cur)
                val = st_cur.group(1) if st_cur else "Unknown"
                print(f"  [T+{sec:02d}s] Tablet UI: {val}")
                if val == "Connected":
                    stable_count += 1
        stability_str = "STABLE" if stable_count == 3 else "DISCONNECTED"

    # Step 6: Speech Test if connected
    stt_transcript = "None"
    msg_sent = "NOT SENT"
    msg_received = "NO"
    tts_played = "NOT PLAYED"

    if is_connected:
        print("\n==================================================")
        print("STEP 6: LIVE SPEECH TEST: TABLET (TX) -> PHONE (RX)")
        print("==================================================")
        t_spk_start = len(adb_shell(TABLET, f"logcat -d --pid={t_pid}")[0].splitlines())
        p_spk_start = len(adb_shell(PHONE, f"logcat -d --pid={p_pid}")[0].splitlines())

        # Select English on Tablet (center 316, 450)
        adb_shell(TABLET, "input tap 316 450")
        time.sleep(0.5)

        # Tap PTT on Tablet at (600, 850)
        print("Tapping PTT on Tablet...")
        adb_shell(TABLET, "input tap 600 850")
        print(">>> SPEAK NOW INTO TABLET MICROPHONE (5s) <<<")
        for i in range(5, 0, -1):
            print(f"Recording... {i}s")
            time.sleep(1)

        print("Tapping PTT to stop capture...")
        adb_shell(TABLET, "input tap 600 850")
        print("Waiting 6 seconds for STT, TCP transmission, and Phone TTS playback...")
        time.sleep(6)

        t_new = adb_shell(TABLET, f"logcat -d --pid={t_pid}")[0].splitlines()[t_spk_start:]
        p_new = adb_shell(PHONE, f"logcat -d --pid={p_pid}")[0].splitlines()[p_spk_start:]

        for l in t_new:
            if "Transcript:" in l or "text=" in l:
                m_tr = re.search(r'Transcript:\s*\"([^\"]+)\"', l) or re.search(r'text=\'([^\']+)\'', l)
                if m_tr and stt_transcript == "None":
                    stt_transcript = m_tr.group(1)
            if "M2_SEND" in l or "Outbound SpeechMessage" in l:
                msg_sent = "SENT"

        for l in p_new:
            if any(k in l for k in ["M2_DECODE", "E2E-REMOTE", "M2_RECEIVE"]):
                msg_received = "YES"
            if any(k in l for k in ["PLAYBACK", "M3-PLAYBACK-COMPLETE", "E2E-B-PLAYBACK"]):
                tts_played = "PLAYED"

    print("\n==================================================")
    print("FINAL SUMMARY REPORT")
    print("==================================================")
    print(f"Network: BOYS AP-2")
    print(f"SSID: {p_ssid}")
    print(f"Phone IP: {p_ip}")
    print(f"Tablet IP: {t_ip}")
    print(f"Phone BSSID: {p_bssid}")
    print(f"Tablet BSSID: {t_bssid}")
    print(f"Phone listener: {'YES' if p_listening else 'NO'}")
    print(f"Raw TCP Tablet -> Phone: {raw_result}")
    print(f"iTantra Tablet -> Phone: {'CONNECTED' if is_connected else 'FAILED'}")
    print(f"Connection stability: {stability_str}")
    print(f"SpeechMessage: {msg_sent}")
    print(f"Received SpeechMessage: {msg_received}")
    print(f"Offline TTS: {tts_played}")
    print(f"Exact error: {t_ui_err if not is_connected else 'None'}")
    print(f"STT Transcript: {stt_transcript}")
    print(f"\nFINAL VERDICT: {'PASS' if (is_connected and msg_sent == 'SENT' and msg_received == 'YES' and tts_played == 'PLAYED') else 'FAIL'}")

if __name__ == "__main__":
    main()
