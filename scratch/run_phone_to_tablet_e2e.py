import subprocess
import time
import re
import sys

ADB = r"C:\Users\saich\AppData\Local\Android\Sdk\platform-tools\adb.exe"
PHONE = "10BE830QG8000MX"
TABLET = "HNP097HQ"
TABLET_IP = "10.179.231.149"

def adb_shell(device, cmd):
    res = subprocess.run([ADB, "-s", device, "shell", cmd], capture_output=True, text=True)
    return res.stdout.strip()

def run_test():
    print("==================================================")
    print("SETTING UP TOPOLOGY: PHONE (TX) -> TABLET (RX)")
    print("==================================================")
    
    # 1. Setup Tablet as Receiver (Server)
    print("Ensuring Tablet is Receiver (Server) listening on 42173...")
    adb_shell(TABLET, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    # Tap btn_mode_server on Tablet (center 316, 228)
    adb_shell(TABLET, "input tap 316 228")
    time.sleep(2)

    # 2. Setup Phone as Transmitter (Client)
    print("Ensuring Phone is Transmitter (Client) connected to Tablet...")
    adb_shell(PHONE, "am start -n org.itantra.app/.MainActivity")
    time.sleep(1)
    # Tap btn_mode_client on Phone (center 912, 489)
    adb_shell(PHONE, "input tap 912 489")
    time.sleep(1)
    
    # Enter Tablet IP on Phone and Connect
    adb_shell(PHONE, "input tap 486 639")
    time.sleep(0.3)
    adb_shell(PHONE, "input keyevent KEYCODE_MOVE_END")
    for _ in range(35):
        adb_shell(PHONE, "input keyevent KEYCODE_DEL")
    time.sleep(0.3)
    adb_shell(PHONE, f"input text {TABLET_IP}")
    time.sleep(0.5)
    # Tap Connect (center 1044, 639)
    adb_shell(PHONE, "input tap 1044 639")
    time.sleep(3)

    # 3. Select English on Phone (center 348, 924)
    print("Selecting English on Phone...")
    adb_shell(PHONE, "input tap 348 924")
    time.sleep(0.5)

    # Clear logcat buffers by recording start timestamps or logcat marks
    p_pid = adb_shell(PHONE, "pidof org.itantra.app")
    t_pid = adb_shell(TABLET, "pidof org.itantra.app")
    
    p_log_start = len(adb_shell(PHONE, f"logcat -d --pid={p_pid}").splitlines())
    t_log_start = len(adb_shell(TABLET, f"logcat -d --pid={t_pid}").splitlines())

    print("\n==================================================")
    print("STARTING LIVE SPEECH TEST: PHONE -> TABLET")
    print("==================================================")
    print("Starting speech capture on Phone (tap PTT at 630, 1803)...")
    adb_shell(PHONE, "input tap 630 1803")
    
    print("\n>>> RECORDING IN PROGRESS — SPEAK INTO PHONE MICROPHONE (6 seconds) <<<")
    for i in range(6, 0, -1):
        print(f"Recording... {i}s remaining")
        time.sleep(1)

    print("\nStopping capture on Phone (tap PTT at 630, 1803)...")
    adb_shell(PHONE, "input tap 630 1803")

    print("\nWaiting 6 seconds for inference, transmission, TTS synthesis, and playback...")
    time.sleep(6)

    # Collect new logs from Phone
    p_full = adb_shell(PHONE, f"logcat -d --pid={p_pid}")
    p_new_lines = p_full.splitlines()[p_log_start:]

    # Collect new logs from Tablet
    t_full = adb_shell(TABLET, f"logcat -d --pid={t_pid}")
    t_new_lines = t_full.splitlines()[t_log_start:]

    print("\n==================================================")
    print("PHONE LOGCAT (M1 STT -> M2 TCP SEND)")
    print("==================================================")
    for line in p_new_lines:
        if any(k in line for k in ["PHYSICAL_E2E", "SpeechPipeline", "ASR", "Whisper", "M2_SEND", "E2E-LOCAL", "E2E-A", "SOCKET_SEND"]):
            print(line)

    print("\n==================================================")
    print("TABLET LOGCAT (M2 TCP RECV -> M3 TTS SYNTHESIS & PLAYBACK)")
    print("==================================================")
    for line in t_new_lines:
        if any(k in line for k in ["PHYSICAL_E2E", "M2_RECEIVE", "M2_DECODE", "M2_DELIVERED", "E2E-REMOTE", "E2E-B", "Piper", "Tts", "PLAYBACK", "M2_M3_TEST"]):
            print(line)

if __name__ == "__main__":
    run_test()
