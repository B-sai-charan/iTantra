import subprocess
import os
import time
import hashlib
import xml.etree.ElementTree as ET

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
APK = os.path.abspath("app/build/outputs/apk/debug/app-debug.apk")
DEVICES = [
    ("Device A", "10BE830QG8000MX"),
    ("Device B", "HNP097HQ")
]

def run_adb(dev_id, args):
    cmd = [ADB, "-s", dev_id] + args
    res = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    return res.stdout.strip(), res.stderr.strip(), res.returncode

def main():
    print("=== PHASE 2 PHYSICAL UI VALIDATION ===")
    
    # 1. APK Info
    apk_sz = os.path.getsize(APK)
    apk_h = hashlib.sha256(open(APK, "rb").read()).hexdigest()
    print(f"Target APK: {APK}")
    print(f"APK Size  : {apk_sz} bytes ({apk_sz/(1024*1024):.2f} MB)")
    print(f"APK SHA256: {apk_h}")
    
    results = {}

    for dev_name, dev_id in DEVICES:
        print(f"\n==========================================")
        print(f"TESTING {dev_name} ({dev_id})")
        print(f"==========================================")
        dev_res = {}
        
        # Clear logcat buffer
        run_adb(dev_id, ["logcat", "-c"])
        
        # Step 1: Install cleanly
        print("1. Installing APK...")
        out, err, code = run_adb(dev_id, ["install", "-r", APK])
        print(f"   Install: {out} (Code: {code})")
        dev_res["install"] = (code == 0)
        
        # Step 2: Launch
        print("2. Launching MainActivity...")
        out, err, code = run_adb(dev_id, ["shell", "am", "start", "-n", "org.itantra.app/org.itantra.MainActivity"])
        print(f"   Launch: {out}")
        time.sleep(2.5)
        
        # Check PID
        pid_out, _, _ = run_adb(dev_id, ["shell", "pidof org.itantra.app"])
        pid = pid_out.strip()
        print(f"   App PID: {pid}")
        dev_res["launched"] = bool(pid)
        
        # Step 3: Capture primary dashboard screenshot
        shot_path = f"scratch/phase2_{dev_name.lower().replace(' ', '_')}_dashboard.png"
        dev_tmp_shot = "/sdcard/phase2_dash.png"
        run_adb(dev_id, ["shell", "screencap", "-p", dev_tmp_shot])
        run_adb(dev_id, ["pull", dev_tmp_shot, shot_path])
        run_adb(dev_id, ["shell", "rm", dev_tmp_shot])
        print(f"   Saved Dashboard Screenshot: {shot_path} ({os.path.getsize(shot_path)} bytes)")
        
        # Step 4: UI Hierarchy Dump
        dump_path = f"scratch/phase2_{dev_name.lower().replace(' ', '_')}_ui_dump.xml"
        run_adb(dev_id, ["shell", "uiautomator", "dump", "/sdcard/ui_dump.xml"])
        run_adb(dev_id, ["pull", "/sdcard/ui_dump.xml", dump_path])
        run_adb(dev_id, ["shell", "rm", "/sdcard/ui_dump.xml"])
        print(f"   Saved UI Dump: {dump_path}")
        
        # Parse view positions/bounds
        tree = ET.parse(dump_path)
        print("   Key Visible View Bounds:")
        for node in tree.getroot().iter("node"):
            r = node.attrib.get("resource-id", "")
            b = node.attrib.get("bounds", "")
            t = node.attrib.get("text", "").encode("ascii", "replace").decode("ascii")
            for target in ["iv_app_logo", "tv_title", "tv_subtitle", "layout_connection_status", "btn_mode_server", "btn_mode_client", "btn_lang_en", "btn_lang_hi", "layout_card_you", "tv_you_transcript", "layout_card_remote", "tv_remote_transcript", "btn_ptt", "tv_ptt_label", "tv_status"]:
                if target in r:
                    print(f"     - {r.split('/')[-1]:26s} | {b:20s} | text='{t}'")
                    
        # Step 5: Interaction Smoke Test
        print("\n3. Performing Safe Interaction Smoke Sequence:")
        
        def get_bounds_center(target_id):
            for n in tree.getroot().iter("node"):
                if target_id in n.attrib.get("resource-id", ""):
                    b = n.attrib.get("bounds", "")
                    parts = b.replace("][", ",").replace("[", "").replace("]", "").split(",")
                    if len(parts) == 4:
                        x = (int(parts[0]) + int(parts[2])) // 2
                        y = (int(parts[1]) + int(parts[3])) // 2
                        return x, y
            return None
            
        # A. Switch Language: English -> Hindi -> English
        hi_center = get_bounds_center("btn_lang_hi")
        en_center = get_bounds_center("btn_lang_en")
        if hi_center and en_center:
            print(f"   Tapping Hindi Button at {hi_center}...")
            run_adb(dev_id, ["shell", "input", "tap", str(hi_center[0]), str(hi_center[1])])
            time.sleep(1)
            print(f"   Tapping English Button at {en_center}...")
            run_adb(dev_id, ["shell", "input", "tap", str(en_center[0]), str(en_center[1])])
            time.sleep(1)
            
        # B. Switch Role: Server -> Client -> Server
        client_center = get_bounds_center("btn_mode_client")
        server_center = get_bounds_center("btn_mode_server")
        if client_center and server_center:
            print(f"   Tapping Transmitter (Client) at {client_center}...")
            run_adb(dev_id, ["shell", "input", "tap", str(client_center[0]), str(client_center[1])])
            time.sleep(1)
            
            # Verify client IP row appeared
            run_adb(dev_id, ["shell", "uiautomator", "dump", "/sdcard/client_dump.xml"])
            c_dump = f"scratch/{dev_name.lower().replace(' ', '_')}_client_dump.xml"
            run_adb(dev_id, ["pull", "/sdcard/client_dump.xml", c_dump])
            run_adb(dev_id, ["shell", "rm", "/sdcard/client_dump.xml"])
            if os.path.exists(c_dump):
                c_content = open(c_dump, "r", encoding="utf-8", errors="ignore").read()
                print(f"   et_peer_ip visible in Client mode: {'et_peer_ip' in c_content}")
                print(f"   btn_connect_peer visible in Client mode: {'btn_connect_peer' in c_content}")
            
            print(f"   Tapping Receiver (Server) at {server_center}...")
            run_adb(dev_id, ["shell", "input", "tap", str(server_center[0]), str(server_center[1])])
            time.sleep(1)
            
        # C. Scroll down and up
        print("   Scrolling dashboard down and up...")
        run_adb(dev_id, ["shell", "input", "swipe", "500", "1200", "500", "400", "300"])
        time.sleep(1)
        run_adb(dev_id, ["shell", "input", "swipe", "500", "400", "500", "1200", "300"])
        time.sleep(1)
        
        # D. PTT Tap & Stop
        ptt_center = get_bounds_center("btn_ptt")
        if ptt_center:
            print(f"   Tapping PTT at {ptt_center} to start capture...")
            run_adb(dev_id, ["shell", "input", "tap", str(ptt_center[0]), str(ptt_center[1])])
            time.sleep(1.5)
            print(f"   Tapping PTT at {ptt_center} to stop capture...")
            run_adb(dev_id, ["shell", "input", "tap", str(ptt_center[0]), str(ptt_center[1])])
            time.sleep(1)
            
        # E. Background & Return
        print("   Backgrounding app via HOME key...")
        run_adb(dev_id, ["shell", "input", "keyevent", "KEYCODE_HOME"])
        time.sleep(1.5)
        print("   Returning to app...")
        run_adb(dev_id, ["shell", "am", "start", "-n", "org.itantra.app/org.itantra.MainActivity"])
        time.sleep(1.5)
        
        pid_after, _, _ = run_adb(dev_id, ["shell", "pidof org.itantra.app"])
        print(f"   App PID after interactions: {pid_after.strip()} (Stable: {bool(pid_after.strip())})")
        dev_res["interaction_stable"] = bool(pid_after.strip())
        
        # Step 6: Crash check in logcat
        log, _, _ = run_adb(dev_id, ["logcat", "-d", "-s", "AndroidRuntime:E", "FATAL:E"])
        has_fatal = "FATAL EXCEPTION" in log or "AndroidRuntime" in log
        print(f"   Fatal crashes in logcat: {has_fatal}")
        dev_res["has_fatal"] = has_fatal
        
        results[dev_name] = dev_res

    print("\n=== FINAL RESULTS SUMMARY ===")
    for dev, r in results.items():
        print(f"{dev}: Launch={r['launched']}, InteractionsStable={r['interaction_stable']}, Crashes={r['has_fatal']}")

if __name__ == "__main__":
    main()
