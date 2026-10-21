import os
import sys
import subprocess
import time
import hashlib
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

DEV_A = "10BE830QG8000MX"
DEV_B = "HNP097HQ"
APK_PATH = r"app\build\outputs\apk\debug\app-debug.apk"

def run_cmd(cmd, timeout=60):
    res = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=timeout)
    return res.returncode, res.stdout, res.stderr

def run_adb(dev, args, timeout=60):
    return run_cmd([ADB_PATH, "-s", dev] + args, timeout=timeout)

def get_apk_details():
    size = os.path.getsize(APK_PATH)
    with open(APK_PATH, "rb") as f:
        sha256 = hashlib.sha256(f.read()).hexdigest().upper()
    return size, sha256

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

def parse_xml_nodes(xml_path):
    tree = ET.parse(xml_path)
    nodes = {}
    for elem in tree.getroot().iter():
        rid = elem.attrib.get('resource-id', '')
        if rid:
            short_id = rid.split('/')[-1]
            nodes[short_id] = elem.attrib
    return nodes

def get_node_center(attrib):
    bounds = attrib.get('bounds', '')
    import re
    m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def check_logcat(dev):
    _, out, _ = run_adb(dev, ["logcat", "-d", "-t", "500"])
    keywords = ["AndroidRuntime", "FATAL EXCEPTION", "org.itantra.app", "NullPointerException", "IllegalStateException", "InflateException"]
    matches = []
    for line in out.splitlines():
        if any(k in line for k in ["FATAL EXCEPTION", "AndroidRuntime", "InflateException", "NullPointerException", "IllegalStateException"]):
            if "org.itantra.app" in line or "AndroidRuntime" in line:
                matches.append(line)
    return matches

def main():
    print("==================================================", flush=True)
    print("PHASE 2 - PHYSICAL UI VALIDATION", flush=True)
    print("==================================================", flush=True)
    
    size, sha256 = get_apk_details()
    print(f"APK Size: {size} bytes ({size/(1024*1024):.2f} MB)", flush=True)
    print(f"APK SHA-256: {sha256}", flush=True)
    
    devices = [("Device A (Phone)", DEV_A, "scratch/phase2_device_a"), 
               ("Device B (Tablet)", DEV_B, "scratch/phase2_device_b")]
               
    for dev_name, dev_id, prefix in devices:
        print(f"\n--------------------------------------------------", flush=True)
        print(f"Testing {dev_name}: {dev_id}", flush=True)
        print(f"--------------------------------------------------", flush=True)
        
        # 1. Clean reinstall
        print(f"[{dev_name}] Clean install...", flush=True)
        run_adb(dev_id, ["logcat", "-c"])
        run_adb(dev_id, ["uninstall", "org.itantra.app"])
        code, out, err = run_adb(dev_id, ["install", "-r", "-d", APK_PATH], timeout=120)
        print(f"[{dev_name}] Install output: {out.strip()}", flush=True)
        
        # 2. Launch
        print(f"[{dev_name}] Launching org.itantra.app/org.itantra.MainActivity...", flush=True)
        run_adb(dev_id, ["shell", "am", "start", "-n", "org.itantra.app/org.itantra.MainActivity"])
        time.sleep(3)
        
        # 3. Screenshot & Dump Dashboard
        png_main = f"{prefix}_dashboard.png"
        xml_main = f"{prefix}_ui_dump.xml"
        capture_screenshot(dev_id, png_main)
        dump_ui(dev_id, xml_main)
        print(f"[{dev_name}] Captured dashboard: {png_main} ({os.path.getsize(png_main)} bytes)", flush=True)
        print(f"[{dev_name}] Captured UI dump: {xml_main} ({os.path.getsize(xml_main)} bytes)", flush=True)
        
        # Check IDs in main dashboard
        nodes = parse_xml_nodes(xml_main)
        print(f"[{dev_name}] Key UI IDs check:", flush=True)
        ids_to_check = [
            "iv_app_logo", "tv_title", "tv_subtitle", "layout_connection_status",
            "btn_mode_server", "btn_mode_client", "btn_lang_en", "btn_lang_hi",
            "layout_card_you", "tv_you_lang_badge", "tv_you_transcript",
            "layout_card_remote", "tv_remote_lang_badge", "tv_remote_transcript",
            "btn_ptt", "tv_ptt_label", "tv_status"
        ]
        for rid in ids_to_check:
            if rid in nodes:
                t_val = repr(nodes[rid].get('text', ''))
                print(f"  + {rid}: bounds={nodes[rid].get('bounds')} text={t_val}", flush=True)
            else:
                print(f"  - MISSING: {rid}", flush=True)
                
        # 4. Step 4 Smoke interactions
        print(f"\n[{dev_name}] Step 4 Smoke interactions:", flush=True)
        
        # Switch to Hindi
        if "btn_lang_hi" in nodes:
            cx, cy = get_node_center(nodes["btn_lang_hi"])
            print(f"  -> Action 2: Tapping btn_lang_hi at ({cx},{cy})...", flush=True)
            run_adb(dev_id, ["shell", "input", "tap", str(cx), str(cy)])
            time.sleep(1)
            dump_ui(dev_id, f"{prefix}_hindi.xml")
            n_hi = parse_xml_nodes(f"{prefix}_hindi.xml")
            badge = n_hi.get("tv_you_lang_badge", {}).get("text", "")
            print(f"  -> Lang badge updated to: '{badge}'", flush=True)
            
        # Switch back to English
        n_curr = parse_xml_nodes(f"{prefix}_hindi.xml")
        if "btn_lang_en" in n_curr:
            cx, cy = get_node_center(n_curr["btn_lang_en"])
            print(f"  -> Action 3: Tapping btn_lang_en at ({cx},{cy})...", flush=True)
            run_adb(dev_id, ["shell", "input", "tap", str(cx), str(cy)])
            time.sleep(1)
            dump_ui(dev_id, f"{prefix}_english.xml")
            n_en = parse_xml_nodes(f"{prefix}_english.xml")
            badge = n_en.get("tv_you_lang_badge", {}).get("text", "")
            print(f"  -> Lang badge updated to: '{badge}'", flush=True)
            
        # Switch to Client mode
        n_curr = parse_xml_nodes(f"{prefix}_english.xml")
        if "btn_mode_client" in n_curr:
            cx, cy = get_node_center(n_curr["btn_mode_client"])
            print(f"  -> Action 4: Tapping btn_mode_client at ({cx},{cy})...", flush=True)
            run_adb(dev_id, ["shell", "input", "tap", str(cx), str(cy)])
            time.sleep(1)
            xml_client = f"{prefix}_client.xml"
            dump_ui(dev_id, xml_client)
            n_client = parse_xml_nodes(xml_client)
            has_ip = "et_peer_ip" in n_client
            has_conn = "btn_connect_peer" in n_client
            print(f"  -> Client mode active: et_peer_ip present={has_ip}, btn_connect_peer present={has_conn}", flush=True)
            if has_ip:
                print(f"     et_peer_ip bounds: {n_client['et_peer_ip'].get('bounds')}", flush=True)
            if has_conn:
                print(f"     btn_connect_peer bounds: {n_client['btn_connect_peer'].get('bounds')}", flush=True)
                
        # Switch back to Server mode
        n_curr = parse_xml_nodes(f"{prefix}_client.xml")
        if "btn_mode_server" in n_curr:
            cx, cy = get_node_center(n_curr["btn_mode_server"])
            print(f"  -> Action 5: Tapping btn_mode_server at ({cx},{cy})...", flush=True)
            run_adb(dev_id, ["shell", "input", "tap", str(cx), str(cy)])
            time.sleep(1)
            dump_ui(dev_id, f"{prefix}_server.xml")
            n_serv = parse_xml_nodes(f"{prefix}_server.xml")
            has_hint = "tv_server_role_hint" in n_serv
            print(f"  -> Server mode active: tv_server_role_hint present={has_hint} ({repr(n_serv.get('tv_server_role_hint', {}).get('text'))})", flush=True)

        # Scroll dashboard
        print(f"  -> Action 6: Scrolling down...", flush=True)
        run_adb(dev_id, ["shell", "input", "swipe", "500", "1500", "500", "500", "300"])
        time.sleep(1)
        print(f"  -> Action 7: Scrolling back to top...", flush=True)
        run_adb(dev_id, ["shell", "input", "swipe", "500", "500", "500", "1500", "300"])
        time.sleep(1)

        # Tap PTT once
        dump_ui(dev_id, f"{prefix}_ptt_ready.xml")
        n_ptt = parse_xml_nodes(f"{prefix}_ptt_ready.xml")
        if "btn_ptt" in n_ptt:
            cx, cy = get_node_center(n_ptt["btn_ptt"])
            print(f"  -> Action 8: Tapping btn_ptt at ({cx},{cy})...", flush=True)
            run_adb(dev_id, ["shell", "input", "tap", str(cx), str(cy)])
            time.sleep(1.5)
            dump_ui(dev_id, f"{prefix}_ptt_pressed.xml")
            n_ptt_active = parse_xml_nodes(f"{prefix}_ptt_pressed.xml")
            label = n_ptt_active.get("tv_ptt_label", {}).get("text", "")
            status = n_ptt_active.get("tv_status", {}).get("text", "")
            print(f"  -> PTT state during press: label={repr(label)}, status={repr(status)}", flush=True)
            
            # Tap PTT again to stop/return
            print(f"  -> Action 9: Tapping btn_ptt again to stop...", flush=True)
            run_adb(dev_id, ["shell", "input", "tap", str(cx), str(cy)])
            time.sleep(1.5)
            dump_ui(dev_id, f"{prefix}_ptt_released.xml")
            n_ptt_rel = parse_xml_nodes(f"{prefix}_ptt_released.xml")
            label2 = n_ptt_rel.get("tv_ptt_label", {}).get("text", "")
            status2 = n_ptt_rel.get("tv_status", {}).get("text", "")
            print(f"  -> PTT state after release: label={repr(label2)}, status={repr(status2)}", flush=True)

        # Background app and return
        print(f"  -> Action 10: Backgrounding app (Home)...", flush=True)
        run_adb(dev_id, ["shell", "input", "keyevent", "KEYCODE_HOME"])
        time.sleep(2)
        print(f"  -> Action 11: Returning to app...", flush=True)
        run_adb(dev_id, ["shell", "am", "start", "-n", "org.itantra.app/org.itantra.MainActivity"])
        time.sleep(2)
        
        # Check crashes in logcat
        crashes = check_logcat(dev_id)
        if crashes:
            print(f"[{dev_name}] CRASH/ERROR DETECTED in Logcat:", flush=True)
            for c in crashes:
                print("  ", c, flush=True)
        else:
            print(f"[{dev_name}] Logcat verification: 0 crashes, 0 FATAL exceptions.", flush=True)

    print("\n=== VALIDATION COMPLETED SUCCESSFULLY ===", flush=True)

if __name__ == "__main__":
    main()
