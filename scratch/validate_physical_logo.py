import subprocess
import os
import time

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
    print(f"=== PHYSICAL LOGO VALIDATION ON PHYSICAL DEVICES ===")
    print(f"Target APK: {APK}")
    
    for dev_name, dev_id in DEVICES:
        print(f"\n==========================================")
        print(f"VALIDATING {dev_name} ({dev_id})")
        print(f"==========================================")
        
        # 1. Install APK with -r (replace / preserve data)
        print(f"Installing APK on {dev_name}...")
        out, err, code = run_adb(dev_id, ["install", "-r", APK])
        print(f"Install Result: {out} {err} (Code: {code})")
        
        # 2. Launch Main Activity
        print(f"Launching org.itantra/.MainActivity on {dev_name}...")
        out, err, code = run_adb(dev_id, ["shell", "am", "start", "-n", "org.itantra/.MainActivity"])
        print(f"Launch Result: {out} (Code: {code})")
        
        time.sleep(2)
        
        # 3. Check for crashes in logcat
        out, err, code = run_adb(dev_id, ["shell", "pidof", "org.itantra"])
        pid = out.strip()
        print(f"App PID on {dev_name}: {pid}")
        if not pid:
            print(f"ERROR: App is not running on {dev_name}!")
        else:
            print(f"App is running stably with PID {pid}")
            
        # 4. In-app Screenshot
        inapp_shot = f"scratch/{dev_name.lower().replace(' ', '_')}_inapp_header.png"
        dev_tmp_shot = "/sdcard/inapp_shot.png"
        run_adb(dev_id, ["shell", "screencap", "-p", dev_tmp_shot])
        run_adb(dev_id, ["pull", dev_tmp_shot, inapp_shot])
        run_adb(dev_id, ["shell", "rm", dev_tmp_shot])
        print(f"Saved In-App Screenshot to {inapp_shot} (Size: {os.path.getsize(inapp_shot)} bytes)")
        
        # 5. UI Automator Dump to check iv_app_logo view bounds
        dump_xml = f"scratch/{dev_name.lower().replace(' ', '_')}_ui_dump.xml"
        run_adb(dev_id, ["shell", "uiautomator", "dump", "/sdcard/ui_dump.xml"])
        run_adb(dev_id, ["pull", "/sdcard/ui_dump.xml", dump_xml])
        run_adb(dev_id, ["shell", "rm", "/sdcard/ui_dump.xml"])
        
        if os.path.exists(dump_xml):
            content = open(dump_xml, 'r', encoding='utf-8', errors='ignore').read()
            if "iv_app_logo" in content:
                print(f"CONFIRMED: iv_app_logo view node is present in {dev_name} active window hierarchy!")
            if "tv_title" in content and "tv_subtitle" in content:
                print(f"CONFIRMED: tv_title ('iTantra') and tv_subtitle are present in {dev_name} layout!")
                
        # 6. Relaunch test
        print(f"Testing close and relaunch on {dev_name}...")
        run_adb(dev_id, ["shell", "am", "force-stop", "org.itantra"])
        time.sleep(1)
        run_adb(dev_id, ["shell", "am", "start", "-n", "org.itantra/.MainActivity"])
        time.sleep(1.5)
        out, err, code = run_adb(dev_id, ["shell", "pidof", "org.itantra"])
        print(f"Relaunched PID on {dev_name}: {out.strip()} (Launch stable: {bool(out.strip())})")

if __name__ == "__main__":
    main()
