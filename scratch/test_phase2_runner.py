import os
import sys
import subprocess
import time
import xml.etree.ElementTree as ET

ADB_PATH = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe")
if not os.path.exists(ADB_PATH):
    ADB_PATH = "adb"

DEV_A = "10BE830QG8000MX"
DEV_B = "HNP097HQ"
APK_PATH = r"app\build\outputs\apk\debug\app-debug.apk"

def run_adb(device_id, cmd_args):
    cmd = [ADB_PATH, "-s", device_id] + cmd_args
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.returncode, res.stdout, res.stderr

def ensure_dir(d):
    if not os.path.exists(d):
        os.makedirs(d, exist_ok=True)

def dump_ui(device_id, out_xml_path):
    # clear old dump on device
    run_adb(device_id, ["shell", "rm", "-f", "/sdcard/window_dump.xml"])
    code, out, err = run_adb(device_id, ["shell", "uiautomator", "dump", "/sdcard/window_dump.xml"])
    if code != 0 or "dumped to" not in out:
        print(f"[{device_id}] uiautomator dump issue: {out} {err}")
    # pull
    code, out, err = run_adb(device_id, ["pull", "/sdcard/window_dump.xml", out_xml_path])
    if code == 0 and os.path.exists(out_xml_path):
        print(f"[{device_id}] UI hierarchy saved to {out_xml_path} ({os.path.getsize(out_xml_path)} bytes)")
        return True
    else:
        print(f"[{device_id}] Failed to pull UI hierarchy: {err}")
        return False

def take_screenshot(device_id, out_png_path):
    run_adb(device_id, ["shell", "rm", "-f", "/sdcard/screen.png"])
    code, out, err = run_adb(device_id, ["shell", "screencap", "-p", "/sdcard/screen.png"])
    code, out, err = run_adb(device_id, ["pull", "/sdcard/screen.png", out_png_path])
    if code == 0 and os.path.exists(out_png_path):
        print(f"[{device_id}] Screenshot saved to {out_png_path} ({os.path.getsize(out_png_path)} bytes)")
        return True
    else:
        print(f"[{device_id}] Failed to pull screenshot: {err}")
        return False

def parse_ui_ids(xml_path):
    tree = ET.parse(xml_path)
    root = tree.getroot()
    elements = []
    for elem in root.iter():
        res_id = elem.attrib.get('resource-id', '')
        text = elem.attrib.get('text', '')
        bounds = elem.attrib.get('bounds', '')
        visible = elem.attrib.get('visible-to-user', '')
        cls = elem.attrib.get('class', '')
        if res_id:
            # extract short id
            short_id = res_id.split('/')[-1]
            elements.append({
                'full_id': res_id,
                'id': short_id,
                'text': text,
                'bounds': bounds,
                'visible': visible,
                'class': cls
            })
    return elements

def find_element_bounds(xml_path, short_id):
    tree = ET.parse(xml_path)
    root = tree.getroot()
    for elem in root.iter():
        res_id = elem.attrib.get('resource-id', '')
        if res_id.endswith(f"/{short_id}"):
            bounds = elem.attrib.get('bounds', '')
            # parse bounds [x1,y1][x2,y2]
            import re
            m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', bounds)
            if m:
                x1, y1, x2, y2 = map(int, m.groups())
                return (x1 + x2) // 2, (y1 + y2) // 2, (x1, y1, x2, y2), elem.attrib
    return None

def tap_id(device_id, xml_path, short_id):
    info = find_element_bounds(xml_path, short_id)
    if info:
        cx, cy, bounds, attrib = info
        print(f"[{device_id}] Tapping {short_id} at ({cx}, {cy}) bounds={bounds} text='{attrib.get('text')}'")
        run_adb(device_id, ["shell", "input", "tap", str(cx), str(cy)])
        return True
    else:
        print(f"[{device_id}] Element {short_id} not found in {xml_path}")
        return False

def check_logcat_crashes(device_id):
    code, out, err = run_adb(device_id, ["logcat", "-d", "-t", "500"])
    crash_keywords = ["AndroidRuntime", "FATAL EXCEPTION", "org.itantra.app", "NullPointerException", "IllegalStateException", "InflateException"]
    found_issues = []
    for line in out.splitlines():
        if any(kw in line for kw in crash_keywords):
            if "org.itantra" in line or "AndroidRuntime" in line or "FATAL" in line or "FATAL EXCEPTION" in line:
                found_issues.append(line)
    return found_issues

def main():
    ensure_dir("scratch")
    print("=== STARTING PHASE 2 PHYSICAL UI VALIDATION ===")
    
    # 1. Check APK
    print(f"Checking APK: {APK_PATH}")
    if not os.path.exists(APK_PATH):
        print(f"ERROR: APK not found at {APK_PATH}")
        return
    
    apk_size = os.path.getsize(APK_PATH)
    import hashlib
    with open(APK_PATH, "rb") as f:
        apk_sha256 = hashlib.sha256(f.read()).hexdigest().upper()
    print(f"APK Size: {apk_size} bytes ({apk_size/(1024*1024):.2f} MB)")
    print(f"APK SHA-256: {apk_sha256}")

    devices = [("Device A (Phone)", DEV_A), ("Device B (Tablet)", DEV_B)]
    
    for label, dev_id in devices:
        print(f"\n==========================================")
        print(f"PROCESSING {label} - {dev_id}")
        print(f"==========================================")
        
        # Clear logcat
        run_adb(dev_id, ["logcat", "-c"])
        
        # Uninstall cleanly
        print(f"[{dev_id}] Uninstalling previous build...")
        run_adb(dev_id, ["uninstall", "org.itantra.app"])
        
        # Install
        print(f"[{dev_id}] Installing {APK_PATH}...")
        code, out, err = run_adb(dev_id, ["install", "-r", "-d", APK_PATH])
        print(f"[{dev_id}] Install result: {out.strip()} {err.strip()}")
        if code != 0:
            print(f"[{dev_id}] INSTALL FAILED!")
            continue
            
        # Launch
        print(f"[{dev_id}] Launching org.itantra.app/org.itantra.MainActivity...")
        code, out, err = run_adb(dev_id, ["shell", "am", "start", "-n", "org.itantra.app/org.itantra.MainActivity"])
        print(f"[{dev_id}] Launch result: {out.strip()} {err.strip()}")
        time.sleep(3)
        
        # Check initial crashes
        crashes = check_logcat_crashes(dev_id)
        if any("FATAL EXCEPTION" in c or "AndroidRuntime" in c for c in crashes):
            print(f"[{dev_id}] CRASH DETECTED ON LAUNCH:")
            for c in crashes:
                print("  ", c)
        else:
            print(f"[{dev_id}] Launch verified - No crashes detected.")
            
        # Initial UI Dump and Screenshot
        prefix = "phase2_device_a" if dev_id == DEV_A else "phase2_device_b"
        xml_file = f"scratch/{prefix}_ui_dump.xml"
        png_file = f"scratch/{prefix}_dashboard.png"
        
        dump_ui(dev_id, xml_file)
        take_screenshot(dev_id, png_file)
        
        # Verify required IDs
        required_ids = [
            "iv_app_logo", "tv_title", "tv_subtitle", "layout_connection_status",
            "btn_mode_server", "btn_mode_client", "et_peer_ip", "btn_connect_peer",
            "btn_lang_en", "btn_lang_hi", "layout_card_you", "tv_you_lang_badge",
            "tv_you_transcript", "layout_card_remote", "tv_remote_lang_badge",
            "tv_remote_transcript", "tv_remote_playback_status", "btn_ptt",
            "tv_ptt_label", "tv_status", "tv_last_error"
        ]
        
        found_elements = parse_ui_ids(xml_file)
        found_id_set = {e['id']: e for e in found_elements}
        
        print(f"\n[{dev_id}] ID VERIFICATION REPORT:")
        missing_ids = []
        for rid in required_ids:
            if rid in found_id_set:
                el = found_id_set[rid]
                print(f"  [PRESENT] {rid}: bounds={el['bounds']} text='{el['text']}' class='{el['class']}'")
            else:
                print(f"  [MISSING/HIDDEN] {rid}")
                missing_ids.append(rid)
                
        # Perform Step 4 Interaction Smoke Test
        print(f"\n[{dev_id}] PERFORMING INTERACTION SMOKE TEST...")
        # 2. Switch English -> Hindi
        print(f"[{dev_id}] Action 2: Switch English -> Hindi")
        tap_id(dev_id, xml_file, "btn_lang_hi")
        time.sleep(1)
        dump_ui(dev_id, f"scratch/{prefix}_after_lang_hi.xml")
        
        # 3. Switch Hindi -> English
        print(f"[{dev_id}] Action 3: Switch Hindi -> English")
        tap_id(dev_id, f"scratch/{prefix}_after_lang_hi.xml", "btn_lang_en")
        time.sleep(1)
        dump_ui(dev_id, f"scratch/{prefix}_after_lang_en.xml")
        
        # 4. Switch Server -> Client
        print(f"[{dev_id}] Action 4: Switch Server -> Client")
        tap_id(dev_id, f"scratch/{prefix}_after_lang_en.xml", "btn_mode_client")
        time.sleep(1)
        dump_ui(dev_id, f"scratch/{prefix}_after_client_mode.xml")
        take_screenshot(dev_id, f"scratch/{prefix}_client_mode.png")
        
        # 5. Switch Client -> Server
        print(f"[{dev_id}] Action 5: Switch Client -> Server")
        tap_id(dev_id, f"scratch/{prefix}_after_client_mode.xml", "btn_mode_server")
        time.sleep(1)
        dump_ui(dev_id, f"scratch/{prefix}_after_server_mode.xml")
        
        # 6. Scroll dashboard
        print(f"[{dev_id}] Action 6: Scroll down")
        # Swipe up to scroll down
        run_adb(dev_id, ["shell", "input", "swipe", "500", "1500", "500", "500", "300"])
        time.sleep(1)
        dump_ui(dev_id, f"scratch/{prefix}_scrolled_down.xml")
        take_screenshot(dev_id, f"scratch/{prefix}_scrolled_down.png")
        
        # 7. Return to top
        print(f"[{dev_id}] Action 7: Return to top")
        run_adb(dev_id, ["shell", "input", "swipe", "500", "500", "500", "1500", "300"])
        time.sleep(1)
        dump_ui(dev_id, f"scratch/{prefix}_scrolled_top.xml")
        
        # 8. Tap PTT once and observe real existing state transition
        print(f"[{dev_id}] Action 8: Tap PTT button once")
        tap_id(dev_id, f"scratch/{prefix}_scrolled_top.xml", "btn_ptt")
        time.sleep(2)
        dump_ui(dev_id, f"scratch/{prefix}_after_ptt_press.xml")
        take_screenshot(dev_id, f"scratch/{prefix}_ptt_pressed.png")
        
        # 9. Stop/return according to existing UI behavior (tap PTT again if toggle or wait)
        print(f"[{dev_id}] Action 9: Tap PTT to return/stop")
        tap_id(dev_id, f"scratch/{prefix}_after_ptt_press.xml", "btn_ptt")
        time.sleep(2)
        dump_ui(dev_id, f"scratch/{prefix}_after_ptt_release.xml")
        
        # 10. Background app
        print(f"[{dev_id}] Action 10: Background the app (Home button)")
        run_adb(dev_id, ["shell", "input", "keyevent", "KEYCODE_HOME"])
        time.sleep(2)
        
        # 11. Return to app
        print(f"[{dev_id}] Action 11: Return to app")
        run_adb(dev_id, ["shell", "am", "start", "-n", "org.itantra.app/org.itantra.MainActivity"])
        time.sleep(2)
        take_screenshot(dev_id, f"scratch/{prefix}_returned.png")
        
        # Final logcat crash check
        print(f"\n[{dev_id}] Checking Logcat for any runtime errors...")
        crashes = check_logcat_crashes(dev_id)
        if crashes:
            print(f"[{dev_id}] Found logcat lines of interest:")
            for c in crashes[-20:]:
                print("  ", c)
        else:
            print(f"[{dev_id}] Logcat clean - Zero crashes or runtime exceptions.")

if __name__ == "__main__":
    main()
