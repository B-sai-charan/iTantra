import subprocess
import time
import re
import sys

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"

def main():
    print("==================================================")
    print("MONITORING LIVE SPEECH E2E SESSION")
    print("Phone (Transmitter) -> Tablet (Receiver)")
    print("==================================================")
    
    # Check if phone transceiver is running
    p_log = subprocess.run([ADB, "-s", PHONE, "logcat", "-d", "-t", "20"], capture_output=True, text=True).stdout
    is_capturing = "AUDIO_CAPTURE" in p_log or "VAD_SAMPLE" in p_log
    
    if not is_capturing:
        print("Starting transceiver capture on Phone (tap PTT)...")
        subprocess.run([ADB, "-s", PHONE, "shell", "input tap 630 1803"])
        time.sleep(1)

    print("\n>>> PHONE IS ACTIVELY LISTENING TO MICROPHONE <<<")
    print("Please speak a short sentence into the PHONE microphone (e.g. 'Hello, this is a live test').")
    print("Monitoring events on both devices for 15 seconds...\n")
    
    start_time = time.time()
    captured_speech = False
    
    while time.time() - start_time < 15:
        time.sleep(1)
        # Check Phone logs for ASR / Outbound SpeechMessage
        p_out = subprocess.run([ADB, "-s", PHONE, "logcat", "-d", "-t", "20"], capture_output=True, text=True).stdout
        if any(k in p_out for k in ["E2E-LOCAL-OUTBOUND", "E2E-A-OUTBOUND", "M2_SEND", "ASR_RESULT"]):
            captured_speech = True
            break
            
    # If not triggered automatically by VAD silence after speech, tap PTT to finalize
    if not captured_speech:
        print("Finishing capture (tapping PTT to finalize utterance)...")
        subprocess.run([ADB, "-s", PHONE, "shell", "input tap 630 1803"])
        time.sleep(4)

    # Collect full logcats from both devices
    print("\n==================================================")
    print("PHONE LOGCAT (M1 STT -> M2 Transport)")
    print("==================================================")
    p_full = subprocess.run([ADB, "-s", PHONE, "logcat", "-d", "-t", "200"], capture_output=True, text=True).stdout
    phone_lines = []
    for line in p_full.splitlines():
        if any(k in line for k in ["PHYSICAL_E2E", "SpeechPipeline", "ASR", "Whisper", "M2_SEND", "E2E-LOCAL", "E2E-A", "SOCKET_SEND"]):
            phone_lines.append(line)
            print(line)

    print("\n==================================================")
    print("TABLET LOGCAT (M2 Transport -> M3 TTS)")
    print("==================================================")
    t_full = subprocess.run([ADB, "-s", TABLET, "logcat", "-d", "-t", "200"], capture_output=True, text=True).stdout
    tablet_lines = []
    for line in t_full.splitlines():
        if any(k in line for k in ["PHYSICAL_E2E", "M2_RECEIVE", "M2_DECODE", "M2_DELIVERED", "E2E-REMOTE", "E2E-B", "Piper", "Tts", "PLAYBACK"]):
            tablet_lines.append(line)
            print(line)

    # Pull UI state
    subprocess.run([ADB, "-s", PHONE, "shell", "uiautomator dump /sdcard/dump.xml"])
    subprocess.run([ADB, "-s", PHONE, "pull", "/sdcard/dump.xml", "scratch/p_speech_ui.xml"], capture_output=True)
    with open("scratch/p_speech_ui.xml", "r", encoding="utf-8", errors="ignore") as f:
        pxml = f.read()

    subprocess.run([ADB, "-s", TABLET, "shell", "uiautomator dump /sdcard/dump.xml"])
    subprocess.run([ADB, "-s", TABLET, "pull", "/sdcard/dump.xml", "scratch/t_speech_ui.xml"], capture_output=True)
    with open("scratch/t_speech_ui.xml", "r", encoding="utf-8", errors="ignore") as f:
        txml = f.read()

    p_you = re.search(r'resource-id="org.itantra.app:id/tv_you_transcript"[^>]*text="([^"]+)"', pxml)
    t_remote = re.search(r'resource-id="org.itantra.app:id/tv_remote_transcript"[^>]*text="([^"]+)"', txml)

    print("\n==================================================")
    print("FINAL UI STATE")
    print("==================================================")
    print(f"Phone 'YOU' Card: {p_you.group(1) if p_you else 'None'}")
    print(f"Tablet 'REMOTE' Card: {t_remote.group(1) if t_remote else 'None'}")

if __name__ == "__main__":
    main()
