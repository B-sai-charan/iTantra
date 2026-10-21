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
    print("STEP 1 — VERIFY NETWORK ON BOYS AP-2")
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

    p_routes, _, _ = adb_shell(PHONE, "ip route show")
    t_routes, _, _ = adb_shell(TABLET, "ip route show")

    print(f"Phone: SSID={p_ssid}, BSSID={p_bssid}, IP={p_ip}")
    print(f"Tablet: SSID={t_ssid}, BSSID={t_bssid}, IP={t_ip}")
    print(f"Phone Routes: {p_routes}")
    print(f"Tablet Routes: {t_routes}")

    print("\n==================================================")
    print("STEP 2 — PHONE RECEIVER SETUP & LISTENER VERIFICATION")
    print("==================================================")
    # Clear logcats
    adb_shell(PHONE, "logcat -c")
    adb_shell(TABLET, "logcat -c")

    # Launch Phone App and select Receiver
    adb_shell(PHONE, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    # Tap Receiver (Server) on Phone at (348, 489)
    adb_shell(PHONE, "input tap 348 489")
    
    # Wait for Phone TCP server to bind
    print("Waiting for Phone TCP 42173 socket to bind...")
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
            print(f"  [T+{i+1}s] Waiting for Phone server socket...")

    print("\n==================================================")
    print(f"STEP 3 — RAW TCP TEST: TABLET -> PHONE ({p_ip}:{PORT})")
    print("==================================================")
    t_raw_out, t_raw_err, t_raw_code = adb_shell(TABLET, f"echo test | nc -w 4 -q 1 {p_ip} {PORT} 2>&1")
    print(f"Tablet -> Phone Raw TCP: code={t_raw_code}, out='{t_raw_out}', err='{t_raw_err}'")

    if t_raw_code == 0:
        raw_t_to_p = "SUCCESS"
    elif "Connection refused" in t_raw_out:
        raw_t_to_p = "CONNECTION REFUSED"
    elif "No route" in t_raw_out:
        raw_t_to_p = "EHOSTUNREACH"
    elif "Timeout" in t_raw_out or t_raw_code != 0:
        raw_t_to_p = "TIMEOUT"
    else:
        raw_t_to_p = f"OTHER ({t_raw_out})"

    print(f"Tablet -> Phone Raw TCP Result: {raw_t_to_p}")

    print("\n==================================================")
    print(f"STEP 4 — REVERSE TCP TEST: PHONE -> TABLET ({t_ip}:{PORT})")
    print("==================================================")
    p_raw_out, p_raw_err, p_raw_code = adb_shell(PHONE, f"toybox nc -w 4 -z {t_ip} {PORT} 2>&1")
    print(f"Phone -> Tablet Raw TCP: code={p_raw_code}, out='{p_raw_out}'")

    if p_raw_code == 0 and ("open" in p_raw_out.lower() or p_raw_out == ""):
        raw_p_to_t = "SUCCESS"
    elif "Connection refused" in p_raw_out:
        raw_p_to_t = "CONNECTION REFUSED"
    elif "No route" in p_raw_out:
        raw_p_to_t = "EHOSTUNREACH"
    elif "Timeout" in p_raw_out or p_raw_code != 0:
        raw_p_to_t = "TIMEOUT"
    else:
        raw_p_to_t = f"OTHER ({p_raw_out})"

    print(f"Phone -> Tablet Raw TCP Result: {raw_p_to_t}")

    print("\n==================================================")
    print("STEP 5 — iTantra CONNECTION: TABLET (TX) -> PHONE (RX)")
    print("==================================================")
    app_conn_status = "NOT RUN"
    exact_error = "None"
    
    if raw_t_to_p == "SUCCESS":
        print("Raw TCP succeeded! Connecting Tablet Transmitter to Phone Receiver...")
        # Clear logcats
        adb_shell(PHONE, "logcat -c")
        adb_shell(TABLET, "logcat -c")

        # Launch Tablet App
        adb_shell(TABLET, "am start -n org.itantra.app/.MainActivity")
        time.sleep(1)

        # Tap Transmitter (Client) on Tablet at (880, 228)
        adb_shell(TABLET, "input tap 880 228")
        time.sleep(1)

        # Enter Phone IP into Tablet et_peer_ip (center 500, 303)
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

        # Check Tablet UI and Logs
        t_pid = adb_shell(TABLET, "pidof org.itantra.app")[0]
        p_pid = adb_shell(PHONE, "pidof org.itantra.app")[0]

        t_logs, _, _ = adb_shell(TABLET, f"logcat -d --pid={t_pid}")
        p_logs, _, _ = adb_shell(PHONE, f"logcat -d --pid={p_pid}")

        print("\n--- Tablet (Transmitter) Logcat ---")
        for l in t_logs.splitlines():
            if any(k in l for k in ["PHYSICAL_E2E", "MainActivity", "SOCKET_CONNECTED", "TRANSPORT", "CONNECT_FAILED", "ConnectionState"]):
                print(" ", l)

        print("\n--- Phone (Receiver) Logcat ---")
        for l in p_logs.splitlines():
            if any(k in l for k in ["PHYSICAL_E2E", "MainActivity", "SOCKET_ACCEPTED", "SOCKET_CONNECTED", "TRANSPORT", "ConnectionState"]):
                print(" ", l)

        # Check Tablet UI state
        adb_shell(TABLET, "uiautomator dump /sdcard/dump.xml")
        t_xml, _, _ = adb_shell(TABLET, "cat /sdcard/dump.xml")
        m_tstate = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', t_xml)
        m_terr = re.search(r'resource-id="org.itantra.app:id/tv_last_error"[^>]*text="([^"]+)"', t_xml)
        
        t_ui_state = m_tstate.group(1) if m_tstate else "Unknown"
        app_conn_status = "CONNECTED" if t_ui_state == "Connected" else f"FAILED ({t_ui_state})"
        exact_error = m_terr.group(1) if m_terr else "None"
        print(f"\nTablet UI Connection State: {t_ui_state}")
        print(f"Tablet UI Error: {exact_error}")
    else:
        app_conn_status = "FAILED"
        exact_error = f"Raw TCP from Tablet to Phone {p_ip}:{PORT} returned {raw_t_to_p}"
        print(f"Skipping iTantra Connect because Raw TCP returned {raw_t_to_p}")

    print("\n==================================================")
    print("STEP 6 — SPEECH E2E (TABLET TX -> PHONE RX)")
    print("==================================================")
    stt_transcript = "None"
    msg_sent = False
    msg_received = False
    tts_played = False
    speech_pass = "FAIL"

    if app_conn_status == "CONNECTED":
        print("Connection is CONNECTED! Running Live Speech Test: Tablet -> Phone...")
        t_pid = adb_shell(TABLET, "pidof org.itantra.app")[0]
        p_pid = adb_shell(PHONE, "pidof org.itantra.app")[0]

        t_log_start = len(adb_shell(TABLET, f"logcat -d --pid={t_pid}")[0].splitlines())
        p_log_start = len(adb_shell(PHONE, f"logcat -d --pid={p_pid}")[0].splitlines())

        # Select English on Tablet (center 316, 450)
        adb_shell(TABLET, "input tap 316 450")
        time.sleep(0.5)

        # Tap PTT on Tablet at (600, 850)
        print("Tapping PTT on Tablet to start recording...")
        adb_shell(TABLET, "input tap 600 850")
        print(">>> SPEAK NOW INTO TABLET MICROPHONE (5s) <<<")
        for i in range(5, 0, -1):
            print(f"Recording... {i}s")
            time.sleep(1)

        print("Tapping PTT on Tablet to finalize speech...")
        adb_shell(TABLET, "input tap 600 850")
        
        print("Waiting 6 seconds for STT, TCP transmission, and Phone TTS playback...")
        time.sleep(6)

        t_new = adb_shell(TABLET, f"logcat -d --pid={t_pid}")[0].splitlines()[t_log_start:]
        p_new = adb_shell(PHONE, f"logcat -d --pid={p_pid}")[0].splitlines()[p_log_start:]

        print("\n--- TABLET LOGS (M1 STT -> M2 SEND) ---")
        for l in t_new:
            if "Transcript:" in l or "text=" in l:
                print(" ", l)
                m_tr = re.search(r'Transcript:\s*\"([^\"]+)\"', l) or re.search(r'text=\'([^\']+)\'', l)
                if m_tr and stt_transcript == "None":
                    stt_transcript = m_tr.group(1)
            if "M2_SEND" in l or "Outbound SpeechMessage" in l:
                print(" ", l)
                msg_sent = True

        print("\n--- PHONE LOGS (M2 RECV -> M3 TTS) ---")
        for l in p_new:
            if any(k in l for k in ["M2_RECEIVE", "M2_DECODE", "M2_DELIVERED", "E2E-REMOTE", "M3-TTS", "M3-PLAYBACK", "E2E-B-PLAYBACK"]):
                print(" ", l)
                if "M2_DECODE" in l or "E2E-REMOTE" in l or "M2_RECEIVE" in l:
                    msg_received = True
                if "PLAYBACK" in l or "M3-PLAYBACK-COMPLETE" in l or "E2E-B-PLAYBACK" in l:
                    tts_played = True

        speech_pass = "PASS" if (msg_sent and msg_received and tts_played) else "FAIL"

    print("\n==================================================")
    print("FINAL SUMMARY REPORT")
    print("==================================================")
    print(f"Phone IP: {p_ip}")
    print(f"Tablet IP: {t_ip}")
    print(f"Phone BSSID: {p_bssid}")
    print(f"Tablet BSSID: {t_bssid}")
    print(f"Phone Listener: {'LISTENING' if p_listening else 'NOT LISTENING'}")
    print(f"Raw TCP Tablet -> Phone: {raw_t_to_p}")
    print(f"Raw TCP Phone -> Tablet: {raw_p_to_t}")
    print(f"iTantra Tablet -> Phone: {app_conn_status}")
    print(f"Exact Error: {exact_error}")
    print(f"STT Transcript: {stt_transcript}")
    print(f"SpeechMessage Sent: {'YES' if msg_sent else 'NO'}")
    print(f"SpeechMessage Received: {'YES' if msg_received else 'NO'}")
    print(f"Phone TTS: {'YES' if tts_played else 'NO'}")
    print(f"Speech E2E Result: {speech_pass}")

if __name__ == "__main__":
    main()
