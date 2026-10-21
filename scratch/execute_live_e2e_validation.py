import os
import sys
import subprocess
import time
import winsound
import xml.etree.ElementTree as ET

# Configure UTF-8 stdout
if sys.stdout.encoding != 'utf-8':
    try:
        sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    except Exception:
        pass

ADB_PATH = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
if not os.path.exists(ADB_PATH):
    ADB_PATH = "adb"

DEV_A = "10BE830QG8000MX"  # Server / Receiver (IP: 172.20.29.81)
DEV_B = "HNP097HQ"          # Client / Transmitter (IP: 172.20.84.74)
WAV_FILE = r"scratch\hello_from_itantra.wav"

def run_adb(dev, args, timeout=30):
    cmd = [ADB_PATH, "-s", dev] + args
    res = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=timeout)
    return res.returncode, res.stdout, res.stderr

def get_node_center(attrib):
    bounds = attrib.get('bounds', '')
    import re
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def parse_xml(xml_path):
    tree = ET.parse(xml_path)
    nodes = {}
    for elem in tree.getroot().iter():
        rid = elem.attrib.get('resource-id', '')
        if rid:
            short_id = rid.split('/')[-1]
            nodes[short_id] = elem.attrib
    return nodes

def dump_ui(dev, out_path):
    run_adb(dev, ["shell", "rm", "-f", "/sdcard/window_dump.xml"])
    run_adb(dev, ["shell", "uiautomator", "dump", "/sdcard/window_dump.xml"])
    run_adb(dev, ["pull", "/sdcard/window_dump.xml", out_path])
    return os.path.exists(out_path)

def capture_screenshot(dev, out_path):
    run_adb(dev, ["shell", "rm", "-f", "/sdcard/screen.png"])
    run_adb(dev, ["shell", "screencap", "-p", "/sdcard/screen.png"])
    run_adb(dev, ["pull", "/sdcard/screen.png", out_path])
    return os.path.exists(out_path)

def main():
    print("==================================================")
    print("PHASE M2 — CONTROLLED LIVE PHONE-TO-PHONE E2E TEST")
    print("==================================================")
    
    # 0. Clear logcats
    print("\n[SETUP] Clearing logcat buffers on Device A and Device B...")
    run_adb(DEV_A, ["logcat", "-c"])
    run_adb(DEV_B, ["logcat", "-c"])
    
    # STEP 1 — DEVICE A SETUP (SERVER / RECEIVER)
    print("\n--------------------------------------------------")
    print("STEP 1: CONFIGURING DEVICE A (RECEIVER / SERVER)")
    print("--------------------------------------------------")
    run_adb(DEV_A, ["shell", "am", "start", "-n", "org.itantra.app/org.itantra.MainActivity"])
    time.sleep(2)
    
    dump_ui(DEV_A, "scratch/live_e2e_dev_a_init.xml")
    nodes_a = parse_xml("scratch/live_e2e_dev_a_init.xml")
    if "btn_mode_server" in nodes_a:
        cx, cy = get_node_center(nodes_a["btn_mode_server"])
        run_adb(DEV_A, ["shell", "input", "tap", str(cx), str(cy)])
        print(f"[Device A] Selected Receiver (Server) mode at ({cx}, {cy})")
    time.sleep(2)
    
    dump_ui(DEV_A, "scratch/live_e2e_dev_a_server_ready.xml")
    nodes_a_ready = parse_xml("scratch/live_e2e_dev_a_server_ready.xml")
    pill_a = nodes_a_ready.get("tv_connection_state", {}).get("text", "")
    status_a = nodes_a_ready.get("tv_status", {}).get("text", "")
    print(f"[Device A] Connection Pill: '{pill_a}', Status: '{status_a}'")
    
    # Check Device A listening port
    _, netstat_a, _ = run_adb(DEV_A, ["shell", "netstat", "-tlpn"])
    print("[Device A] Netstat 42173 check:")
    for l in netstat_a.splitlines():
        if "42173" in l:
            print("  ", l.strip())
            
    # STEP 2 — DEVICE B SETUP (TRANSMITTER / CLIENT)
    print("\n--------------------------------------------------")
    print("STEP 2: CONFIGURING DEVICE B (TRANSMITTER / CLIENT)")
    print("--------------------------------------------------")
    run_adb(DEV_B, ["shell", "am", "start", "-n", "org.itantra.app/org.itantra.MainActivity"])
    time.sleep(2)
    
    dump_ui(DEV_B, "scratch/live_e2e_dev_b_init.xml")
    nodes_b = parse_xml("scratch/live_e2e_dev_b_init.xml")
    if "btn_mode_client" in nodes_b:
        cx, cy = get_node_center(nodes_b["btn_mode_client"])
        run_adb(DEV_B, ["shell", "input", "tap", str(cx), str(cy)])
        print(f"[Device B] Selected Transmitter (Client) mode at ({cx}, {cy})")
    time.sleep(1)
    
    dump_ui(DEV_B, "scratch/live_e2e_dev_b_client.xml")
    nodes_b_client = parse_xml("scratch/live_e2e_dev_b_client.xml")
    
    # Enter Server IP 172.20.29.81
    if "et_peer_ip" in nodes_b_client:
        cx, cy = get_node_center(nodes_b_client["et_peer_ip"])
        run_adb(DEV_B, ["shell", "input", "tap", str(cx), str(cy)])
        time.sleep(0.5)
        for _ in range(25):
            run_adb(DEV_B, ["shell", "input", "keyevent", "KEYCODE_DEL"])
        run_adb(DEV_B, ["shell", "input", "text", "172.20.29.81"])
        print("[Device B] Entered Server IP '172.20.29.81'")
    time.sleep(1)
    
    # Tap Connect
    dump_ui(DEV_B, "scratch/live_e2e_dev_b_ip_ready.xml")
    nodes_b_ip = parse_xml("scratch/live_e2e_dev_b_ip_ready.xml")
    if "btn_connect_peer" in nodes_b_ip:
        cx, cy = get_node_center(nodes_b_ip["btn_connect_peer"])
        run_adb(DEV_B, ["shell", "input", "tap", str(cx), str(cy)])
        print(f"[Device B] Tapped Connect button at ({cx}, {cy})")
    time.sleep(3)
    
    dump_ui(DEV_B, "scratch/live_e2e_dev_b_connected.xml")
    nodes_b_conn = parse_xml("scratch/live_e2e_dev_b_connected.xml")
    pill_b = nodes_b_conn.get("tv_connection_state", {}).get("text", "")
    status_b = nodes_b_conn.get("tv_status", {}).get("text", "")
    print(f"[Device B] Connection Pill: '{pill_b}', Status: '{status_b}'")
    
    # STEP 3 — LIVE SPEECH TRANSMISSION
    print("\n--------------------------------------------------")
    print("STEP 3: LIVE SPEECH TRANSMISSION ON DEVICE B")
    print("--------------------------------------------------")
    print("Sentence to speak: 'Hello from iTantra'")
    
    if "btn_ptt" in nodes_b_conn:
        cx, cy = get_node_center(nodes_b_conn["btn_ptt"])
        print(f"[Device B] Pressing PTT button at ({cx}, {cy})...")
        run_adb(DEV_B, ["shell", "input", "tap", str(cx), str(cy)])
        time.sleep(0.8)
        
        # Play acoustic speech from speaker into Device B physical mic
        print("[Device B] Playing acoustic speech 'Hello from iTantra' to physical microphone...")
        try:
            winsound.PlaySound(WAV_FILE, winsound.SND_FILENAME)
        except Exception as e:
            print("Winsound play exception:", e)
        time.sleep(1.5)
        
        # Release / Stop PTT
        print(f"[Device B] Stopping PTT at ({cx}, {cy})...")
        run_adb(DEV_B, ["shell", "input", "tap", str(cx), str(cy)])
        time.sleep(3.5) # Wait for Whisper STT and network dispatch
    
    # Capture Device B post-speech state
    dump_ui(DEV_B, "scratch/live_e2e_dev_b_post_speech.xml")
    capture_screenshot(DEV_B, "scratch/live_e2e_dev_b_post_speech.png")
    nodes_b_post = parse_xml("scratch/live_e2e_dev_b_post_speech.xml")
    you_transcript_b = nodes_b_post.get("tv_you_transcript", {}).get("text", "")
    you_badge_b = nodes_b_post.get("tv_you_lang_badge", {}).get("text", "")
    status_b_post = nodes_b_post.get("tv_status", {}).get("text", "")
    print(f"\n[Device B] YOU Transcript: '{you_transcript_b}' (Badge: {you_badge_b})")
    print(f"[Device B] Status Label: '{status_b_post}'")
    
    # STEP 4 — RECEIVER INSPECTION ON DEVICE A
    print("\n--------------------------------------------------")
    print("STEP 4: INSPECTING RECEIVER ON DEVICE A")
    print("--------------------------------------------------")
    time.sleep(3) # Wait for TTS synthesis and playback
    dump_ui(DEV_A, "scratch/live_e2e_dev_a_post_receive.xml")
    capture_screenshot(DEV_A, "scratch/live_e2e_dev_a_post_receive.png")
    nodes_a_post = parse_xml("scratch/live_e2e_dev_a_post_receive.xml")
    remote_transcript_a = nodes_a_post.get("tv_remote_transcript", {}).get("text", "")
    remote_badge_a = nodes_a_post.get("tv_remote_lang_badge", {}).get("text", "")
    remote_playback_a = nodes_a_post.get("tv_remote_playback_status", {}).get("text", "")
    status_a_post = nodes_a_post.get("tv_status", {}).get("text", "")
    
    print(f"[Device A] REMOTE Transcript: '{remote_transcript_a}' (Badge: {remote_badge_a})")
    print(f"[Device A] Remote Playback Badge: '{remote_playback_a}'")
    print(f"[Device A] Status Label: '{status_a_post}'")
    
    # Collect relevant logcats
    print("\n==================================================")
    print("DEVICE B (TRANSMITTER) RELEVANT LOGS:")
    print("==================================================")
    _, log_b, _ = run_adb(DEV_B, ["logcat", "-d", "-t", "400"])
    keywords = ["PHYSICAL_E2E", "SpeechMessage", "SpeechMessageTransportAdapter", "PacketEncoder", 
                "M2_SEND", "CLIENT_CONNECT", "SOCKET_CONNECTED", "Speech recognized", "Outbound", "Whisper"]
    for l in log_b.splitlines():
        if any(k in l for k in keywords):
            print("  [Dev B]", l.strip())
            
    print("\n==================================================")
    print("DEVICE A (RECEIVER) RELEVANT LOGS:")
    print("==================================================")
    _, log_a, _ = run_adb(DEV_A, ["logcat", "-d", "-t", "400"])
    keywords_a = ["PHYSICAL_E2E", "SpeechMessage", "SpeechMessageTransportAdapter", "PacketDecoder", 
                  "M2_RECEIVE", "M2_DECODE", "M2_DELIVERED", "E2E-REMOTE-INBOUND", "E2E-B-PLAYBACK", "TTS", "AudioTrack", "playback"]
    for l in log_a.splitlines():
        if any(k in l for k in keywords_a):
            print("  [Dev A]", l.strip())

if __name__ == "__main__":
    main()
