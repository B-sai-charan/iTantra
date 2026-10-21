import subprocess
import time
import re

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip()

print("1. Checking Phone UI before PTT...")
adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
subprocess.run([ADB, "-s", PHONE, "pull", "/sdcard/dump.xml", "scratch/p_ui_before.xml"], capture_output=True)
with open("scratch/p_ui_before.xml", "r", encoding="utf-8", errors="ignore") as f:
    xml = f.read()

m_status = re.search(r'resource-id="org.itantra.app:id/tv_status"[^>]*text="([^"]+)"', xml)
m_state = re.search(r'resource-id="org.itantra.app:id/tv_connection_state"[^>]*text="([^"]+)"', xml)
print(f"Phone UI Status: {m_status.group(1) if m_status else 'Unknown'}")
print(f"Phone UI Connection State: {m_state.group(1) if m_state else 'Unknown'}")

# Clear logcats
adb_shell(PHONE, "logcat -c")
adb_shell(TABLET, "logcat -c")

# Tap PTT
print("\n2. Tapping PTT at (630, 1803)...")
adb_shell(PHONE, "input tap 630 1803")
time.sleep(1)

# Check UI during recording
adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
subprocess.run([ADB, "-s", PHONE, "pull", "/sdcard/dump.xml", "scratch/p_ui_rec.xml"], capture_output=True)
with open("scratch/p_ui_rec.xml", "r", encoding="utf-8", errors="ignore") as f:
    xml_rec = f.read()

m_status_rec = re.search(r'resource-id="org.itantra.app:id/tv_status"[^>]*text="([^"]+)"', xml_rec)
print(f"Phone UI Status while Recording: {m_status_rec.group(1) if m_status_rec else 'Unknown'}")

# Let user speak for 5 seconds
print("\n>>> RECORDING LIVE SPEECH FOR 5 SECONDS <<<")
for i in range(5, 0, -1):
    print(f"Recording... {i}s")
    time.sleep(1)

# Tap PTT again to stop
print("\n3. Tapping PTT to stop recording at (630, 1803)...")
adb_shell(PHONE, "input tap 630 1803")

print("\n4. Waiting 6 seconds for inference & transmission...")
time.sleep(6)

print("\n==================================================")
print("PHONE LOGCAT EVENTS")
print("==================================================")
p_logs = subprocess.run([ADB, "-s", PHONE, "logcat", "-d"], capture_output=True, text=True).stdout
for line in p_logs.splitlines():
    if any(k in line for k in ["PHYSICAL_E2E", "SpeechPipeline", "Whisper", "Sherpa", "M2_SEND", "E2E-LOCAL", "E2E-A"]):
        print(line)

print("\n==================================================")
print("TABLET LOGCAT EVENTS")
print("==================================================")
t_logs = subprocess.run([ADB, "-s", TABLET, "logcat", "-d"], capture_output=True, text=True).stdout
for line in t_logs.splitlines():
    if any(k in line for k in ["PHYSICAL_E2E", "M2_RECEIVE", "M2_DECODE", "M2_DELIVERED", "E2E-REMOTE", "E2E-B", "Piper", "Tts"]):
        print(line)

# Final UI check
adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
subprocess.run([ADB, "-s", PHONE, "pull", "/sdcard/dump.xml", "scratch/p_ui_end.xml"], capture_output=True)
with open("scratch/p_ui_end.xml", "r", encoding="utf-8", errors="ignore") as f:
    xml_end_p = f.read()

adb_shell(TABLET, "uiautomator dump /sdcard/dump.xml")
subprocess.run([ADB, "-s", TABLET, "pull", "/sdcard/dump.xml", "scratch/t_ui_end.xml"], capture_output=True)
with open("scratch/t_ui_end.xml", "r", encoding="utf-8", errors="ignore") as f:
    xml_end_t = f.read()

p_you = re.search(r'resource-id="org.itantra.app:id/tv_you_transcript"[^>]*text="([^"]+)"', xml_end_p)
t_remote = re.search(r'resource-id="org.itantra.app:id/tv_remote_transcript"[^>]*text="([^"]+)"', xml_end_t)

print("\n==================================================")
print("FINAL UI TRANSCRIPTS")
print("==================================================")
print(f"Phone 'YOU' Card: {p_you.group(1) if p_you else 'None'}")
print(f"Tablet 'REMOTE' Card: {t_remote.group(1) if t_remote else 'None'}")
