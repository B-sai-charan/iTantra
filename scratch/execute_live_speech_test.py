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

def run_live_test(record_duration=5):
    print("==================================================")
    print("LIVE PHONE -> TABLET SPEECH E2E VALIDATION")
    print("==================================================")
    
    # 1. Clear logcats
    adb_shell(PHONE, "logcat -c")
    adb_shell(TABLET, "logcat -c")

    # 2. Select English on Phone
    print("Selecting English on Phone (btn_lang_en at 348, 924)...")
    adb_shell(PHONE, "input tap 348 924")
    time.sleep(0.5)

    # 3. Start speech capture (Press PTT)
    print("\n[STARTING CAPTURE] Tapping PTT button (630, 1803)...")
    adb_shell(PHONE, "input tap 630 1803")
    
    print(f">>> SPEAK NOW INTO PHONE MICROPHONE ({record_duration} seconds) <<<")
    for i in range(record_duration, 0, -1):
        print(f"Recording... {i}s remaining")
        time.sleep(1)

    # 4. Stop speech capture (Release PTT)
    print("\n[STOPPING CAPTURE] Tapping PTT button (630, 1803)...")
    adb_shell(PHONE, "input tap 630 1803")

    print("\nWaiting 6 seconds for Whisper STT -> SpeechMessage -> TCP Send -> Tablet Receive -> TTS Synthesis -> Audio Playback...")
    time.sleep(6)

    # 5. Extract Phone Logs
    print("\n==================================================")
    print("PHONE CAPTURED EVENTS (STT -> SpeechMessage -> TCP Send)")
    print("==================================================")
    phone_logs = subprocess.run([ADB, "-s", PHONE, "logcat", "-d"], capture_output=True, text=True).stdout
    phone_relevant = []
    for line in phone_logs.splitlines():
        if any(k in line for k in ["PHYSICAL_E2E", "SpeechPipeline", "SherpaWhisper", "Whisper", "M2_SEND", "E2E-LOCAL-OUTBOUND", "E2E-A-OUTBOUND", "SOCKET_SEND"]):
            phone_relevant.append(line)
            print(line)

    # 6. Extract Tablet Logs
    print("\n==================================================")
    print("TABLET CAPTURED EVENTS (TCP Receive -> Decode -> TTS Playback)")
    print("==================================================")
    tablet_logs = subprocess.run([ADB, "-s", TABLET, "logcat", "-d"], capture_output=True, text=True).stdout
    tablet_relevant = []
    for line in tablet_logs.splitlines():
        if any(k in line for k in ["PHYSICAL_E2E", "M2_RECEIVE", "M2_DECODE", "M2_DELIVERED", "E2E-REMOTE-INBOUND", "E2E-B-INBOUND", "E2E-B-PLAYBACK", "PiperTtsEngine", "Tts"]):
            tablet_relevant.append(line)
            print(line)

    # 7. Dump Phone & Tablet UI State
    adb_shell(PHONE, "uiautomator dump /sdcard/dump.xml")
    subprocess.run([ADB, "-s", PHONE, "pull", "/sdcard/dump.xml", "scratch/phone_speech_ui.xml"], capture_output=True)
    with open("scratch/phone_speech_ui.xml", "r", encoding="utf-8", errors="ignore") as f:
        p_xml = f.read()

    adb_shell(TABLET, "uiautomator dump /sdcard/dump.xml")
    subprocess.run([ADB, "-s", TABLET, "pull", "/sdcard/dump.xml", "scratch/tablet_speech_ui.xml"], capture_output=True)
    with open("scratch/tablet_speech_ui.xml", "r", encoding="utf-8", errors="ignore") as f:
        t_xml = f.read()

    p_you_tx = re.search(r'resource-id="org.itantra.app:id/tv_you_transcript"[^>]*text="([^"]+)"', p_xml)
    p_status = re.search(r'resource-id="org.itantra.app:id/tv_status"[^>]*text="([^"]+)"', p_xml)
    
    t_remote_tx = re.search(r'resource-id="org.itantra.app:id/tv_remote_transcript"[^>]*text="([^"]+)"', t_xml)
    t_status = re.search(r'resource-id="org.itantra.app:id/tv_status"[^>]*text="([^"]+)"', t_xml)

    print("\n==================================================")
    print("UI TRANSCRIPTS")
    print("==================================================")
    print(f"Phone 'YOU' Card Transcript: {p_you_tx.group(1) if p_you_tx else 'None'}")
    print(f"Phone Status: {p_status.group(1) if p_status else 'None'}")
    print(f"Tablet 'REMOTE' Card Transcript: {t_remote_tx.group(1) if t_remote_tx else 'None'}")
    print(f"Tablet Status: {t_status.group(1) if t_status else 'None'}")

if __name__ == "__main__":
    dur = int(sys.argv[1]) if len(sys.argv) > 1 else 5
    run_live_test(dur)
